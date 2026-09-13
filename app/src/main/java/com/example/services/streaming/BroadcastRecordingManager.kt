package com.example.services.streaming

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import com.example.services.composition.BroadcastVideoCompositor
import com.example.services.composition.ComposedBroadcastFrame
import com.example.services.station.StationDeskManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.ByteBuffer

enum class RecordingProfile(val label: String, val width: Int, val height: Int, val bitrate: Int) {
    HIGH("High (1080p)", 1920, 1080, 8000000),
    MEDIUM("Medium (720p)", 1280, 720, 4000000),
    LOW("Low (480p)", 854, 480, 1500000)
}

/**
 * Production local video recording pipeline (Part H).
 *
 * Captures real composed frames from [BroadcastVideoCompositor] and encodes them
 * in real-time into a playable local MP4 video file using MediaMuxer.
 *
 * Guarantees:
 * - Writes to Movies/MVP-Esports and invokes MediaScanner on completion.
 * - Queue max 2 frames with DROP_OLDEST; never blocks screen capture or UI.
 * - Default PERFORMANCE: 1280x720 @ 30fps, 4.0 Mbps.
 */
class BroadcastRecordingManager private constructor() {

    companion object {
        private const val TAG = "BroadcastRecording"

        @Volatile
        private var instance: BroadcastRecordingManager? = null

        fun getInstance(): BroadcastRecordingManager {
            return instance ?: synchronized(this) {
                instance ?: BroadcastRecordingManager().also { instance = it }
            }
        }
    }

    private val recordingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var recordingJob: Job? = null
    private var frameCollectionJob: Job? = null
    private var encoderDrainJob: Job? = null
    private var durationJob: Job? = null
    private val recordingEncoder = MediaCodecVideoEncoder()
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var muxerStarted = false

    // Bounded queue: max 2 frames, drops oldest if encoder is busy
    private var frameChannel: Channel<ComposedBroadcastFrame>? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds: StateFlow<Long> = _recordingDurationSeconds.asStateFlow()

    private val _recordingError = MutableStateFlow<String?>(null)
    val recordingError: StateFlow<String?> = _recordingError.asStateFlow()

    private val _fileCreationStatus = MutableStateFlow<String?>("IDLE")
    val fileCreationStatus: StateFlow<String?> = _fileCreationStatus.asStateFlow()

    private val _selectedProfile = MutableStateFlow(RecordingProfile.MEDIUM)
    val selectedProfile: StateFlow<RecordingProfile> = _selectedProfile.asStateFlow()

    private val _selectedFps = MutableStateFlow(30)
    val selectedFps: StateFlow<Int> = _selectedFps.asStateFlow()

    private val _supportedResolutions = MutableStateFlow<List<Pair<Int, Int>>>(listOf(Pair(1920, 1080), Pair(1280, 720), Pair(854, 480)))
    val supportedResolutions: StateFlow<List<Pair<Int, Int>>> = _supportedResolutions.asStateFlow()

    private val _supportedFpsList = MutableStateFlow<List<Int>>(listOf(30, 60))
    val supportedFpsList: StateFlow<List<Int>> = _supportedFpsList.asStateFlow()

    private var currentOutputFile: File? = null
    private var recordingStartTimeMs = 0L
    private var appContext: Context? = null

    private var averageProcessTimeMs = 0.0
    private val frameProcessTimes = mutableListOf<Long>()
    private var isThrottled = false

    init {
        // Populates hardware encoder capabilities safely
        val candidates = listOf(Pair(1920, 1080), Pair(1280, 720), Pair(854, 480))
        val supportedRes = candidates.filter { isResolutionSupported(it.first, it.second) }
        _supportedResolutions.value = if (supportedRes.isNotEmpty()) supportedRes else listOf(Pair(1280, 720))

        val fpsCandidates = listOf(30, 60)
        val supportedFps = fpsCandidates.filter { isFpsSupported(it) }
        _supportedFpsList.value = if (supportedFps.isNotEmpty()) supportedFps else listOf(30)
    }

    fun setSelectedProfile(profile: RecordingProfile) {
        _selectedProfile.value = profile
    }

    fun setSelectedFps(fps: Int) {
        _selectedFps.value = fps
    }

    fun isResolutionSupported(width: Int, height: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val codecList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
                for (info in codecList.codecInfos) {
                    if (!info.isEncoder) continue
                    for (type in info.supportedTypes) {
                        if (type.equals("video/avc", ignoreCase = true)) {
                            val caps = info.getCapabilitiesForType(type)
                            val videoCaps = caps.videoCapabilities
                            if (videoCaps != null && videoCaps.isSizeSupported(width, height)) {
                                return true
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return (width == 1280 && height == 720) || (width == 854 && height == 480) || (width == 1920 && height == 1080)
    }

    fun isFpsSupported(fps: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val codecList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
                for (info in codecList.codecInfos) {
                    if (!info.isEncoder) continue
                    for (type in info.supportedTypes) {
                        if (type.equals("video/avc", ignoreCase = true)) {
                            val caps = info.getCapabilitiesForType(type)
                            val videoCaps = caps.videoCapabilities
                            if (videoCaps != null) {
                                val range = videoCaps.supportedFrameRates
                                if (range != null && range.contains(fps)) {
                                    return true
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return fps == 30 || fps == 60
    }

    fun startRecording(context: Context, width: Int = 1280, height: Int = 720, fps: Int = 30, bitrate: Int = 4000000): Result<Unit> {
        if (_isRecording.value) return Result.success(Unit)

        return try {
            appContext = context.applicationContext
            val moviesPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val esportsDir = File(moviesPublicDir, "MVP-Esports")
            if (!esportsDir.exists()) {
                esportsDir.mkdirs()
            }
            val targetDir = if (esportsDir.exists() && esportsDir.canWrite()) {
                esportsDir
            } else {
                val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "MVP-Esports")
                fallback.mkdirs()
                fallback
            }
            val file = File(targetDir, "MVP_STATION_${System.currentTimeMillis()}.mp4")
            currentOutputFile = file

            muxerStarted = false
videoTrackIndex = -1
mediaMuxer = null

val configRes = recordingEncoder.configureEncoder(width, height, bitrate, fps)

if (configRes.isFailure) {
    _recordingError.value =
        configRes.exceptionOrNull()?.message ?: "Encoder configuration failed"

    recordingEncoder.stopEncoder()

    return Result.failure(
        configRes.exceptionOrNull()
            ?: IllegalStateException("Encoder configuration failed")
    )
}

// Encoder successfully configured BEFORE creating MediaMuxer.
mediaMuxer = MediaMuxer(
    file.absolutePath,
    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
)

            recordingStartTimeMs = SystemClock.elapsedRealtime()
            _isRecording.value = true
            _recordingDurationSeconds.value = 0L
            frameProcessTimes.clear()
            averageProcessTimeMs = 0.0
            isThrottled = false

            val channel = Channel<ComposedBroadcastFrame>(capacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            frameChannel = channel

            StationDeskManager.updateRecordingState(
                isRecording = true,
                durationSeconds = 0L,
                fileName = file.name,
                filePath = file.absolutePath
            )

            // 1. Drain loop for encoded frames -> MediaMuxer
            encoderDrainJob = recordingScope.launch {
                recordingEncoder.encodedFrames.collect { encodedFrame ->
                    if (!_isRecording.value) return@collect
                    
                    if (encodedFrame.isCodecConfig && !muxerStarted) {
    val format = recordingEncoder.getOutputFormat()

    if (format != null && mediaMuxer != null) {
        try {
            videoTrackIndex = mediaMuxer!!.addTrack(format)
            mediaMuxer!!.start()
            muxerStarted = true

            Log.i(
                TAG,
                "MediaMuxer started successfully. Track=$videoTrackIndex"
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "MediaMuxer start failed",
                e
            )

            _recordingError.value =
                e.message ?: "MediaMuxer start failed"

            _isRecording.value = false
        }
    }
}
                    
                    if (muxerStarted && !encodedFrame.isCodecConfig) {
                        val bufferInfo = MediaCodec.BufferInfo()
                        bufferInfo.set(0, encodedFrame.data.size, encodedFrame.presentationTimeUs, 0)
                        val buffer = ByteBuffer.wrap(encodedFrame.data)
                        mediaMuxer!!.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                    }
                }
            }

            // 2. Composed frames collection -> bounded non-blocking queue
            val compositor = BroadcastVideoCompositor.getInstance()
            frameCollectionJob = recordingScope.launch {
                compositor.composedFrames.collect { composedFrame ->
                    if (!_isRecording.value) return@collect
                    channel.trySend(composedFrame)
                }
            }

            // 3. Background encoding worker consuming from bounded queue
            recordingJob = recordingScope.launch(Dispatchers.Default) {
                for (frame in channel) {
                    if (!_isRecording.value) break
                    encodeAndMuxFrame(frame)
                }
            }

            // 4. Duration Update Job
            durationJob = recordingScope.launch {
                while (isActive && _isRecording.value) {
                    delay(1000)
                    val dur = (SystemClock.elapsedRealtime() - recordingStartTimeMs) / 1000
                    _recordingDurationSeconds.value = dur
                    StationDeskManager.updateRecordingState(
                        isRecording = true,
                        durationSeconds = dur,
                        fileName = file.name,
                        filePath = file.absolutePath
                    )
                }
            }

            Log.i(TAG, "Local broadcast recording started: ${file.absolutePath}")
            _fileCreationStatus.value = "RECORDING_ACTIVE: ${file.name}"
            _recordingError.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            _recordingError.value = e.message ?: "Failed to start recording"
            cleanupRecordingResources()
            Result.failure(e)
        }
    }

    fun stopRecording(): Result<Unit> {
        if (!_isRecording.value) return Result.success(Unit)

        return try {
            val lastFile = currentOutputFile
            val lastDur = _recordingDurationSeconds.value
            _isRecording.value = false
            frameChannel?.close()
            frameChannel = null
            recordingJob?.cancel()
            recordingJob = null
            frameCollectionJob?.cancel()
            frameCollectionJob = null
            encoderDrainJob?.cancel()
            encoderDrainJob = null
            durationJob?.cancel()
            durationJob = null

            recordingEncoder.stopEncoder()
            
            if (muxerStarted) {
                mediaMuxer?.stop()
            }
            mediaMuxer?.release()
            mediaMuxer = null
            muxerStarted = false

            StationDeskManager.updateRecordingState(
                isRecording = false,
                durationSeconds = lastDur,
                fileName = lastFile?.name,
                filePath = lastFile?.absolutePath
            )

            // Scan into phone Gallery
            if (lastFile != null && lastFile.exists()) {
                appContext?.let { ctx ->
                    try {
                        MediaScannerConnection.scanFile(
                            ctx,
                            arrayOf(lastFile.absolutePath),
                            arrayOf("video/mp4")
                        ) { path, uri ->
                            Log.i(TAG, "MP4 scanned into Gallery: $path -> $uri")
                        }
                    } catch (ex: Exception) {
                        Log.w(TAG, "Failed to trigger media scan: ${ex.message}")
                    }
                }
            }

            _fileCreationStatus.value = "SAVED: ${lastFile?.name}"
            Log.i(TAG, "Local broadcast recording saved: ${lastFile?.absolutePath}")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun encodeAndMuxFrame(composedFrame: ComposedBroadcastFrame) {
        try {
            // Priority: PUBG performance. If load is too high, drop frames to maintain system responsiveness.
            if (isThrottled && composedFrame.timestampMs % 2 != 0L) {
                return
            }

            val start = SystemClock.elapsedRealtime()
            val presentationTimeUs = (SystemClock.elapsedRealtime() - recordingStartTimeMs) * 1000L
            recordingEncoder.encodeFrame(composedFrame.bitmap, presentationTimeUs)
            val duration = SystemClock.elapsedRealtime() - start

            synchronized(frameProcessTimes) {
                frameProcessTimes.add(duration)
                if (frameProcessTimes.size > 10) {
                    frameProcessTimes.removeAt(0)
                }
                averageProcessTimeMs = frameProcessTimes.average()
                
                if (averageProcessTimeMs > 25.0 && !isThrottled) {
                    isThrottled = true
                    Log.w(TAG, "High encoder load detected. Halving local recording rate to protect PUBG gameplay smoothness.")
                } else if (averageProcessTimeMs < 12.0 && isThrottled) {
                    isThrottled = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding recording frame: ${e.message}", e)
        }
    }

    private fun cleanupRecordingResources() {
        _isRecording.value = false
        frameChannel?.close()
        frameChannel = null
        recordingJob?.cancel()
        recordingJob = null
        frameCollectionJob?.cancel()
        frameCollectionJob = null
        encoderDrainJob?.cancel()
        encoderDrainJob = null
        durationJob?.cancel()
        durationJob = null
        recordingEncoder.stopEncoder()
        try { mediaMuxer?.release() } catch (_: Exception) {}
        mediaMuxer = null
        muxerStarted = false
    }
}
