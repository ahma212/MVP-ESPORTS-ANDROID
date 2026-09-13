package com.example.services.streaming

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.services.audio.AudioMixerManager
import com.example.services.audio.InternalAudioCaptureManager
import com.example.services.audio.MicrophoneCommentaryManager
import com.example.services.audio.PcmMixer
import com.example.services.composition.BroadcastVideoCompositor
import com.example.services.composition.ComposedBroadcastFrame
import com.example.services.composition.IBroadcastCompositor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Real-time operational metrics for the active video broadcast pipeline.
 */
data class BroadcastPipelineMetrics(
    val isActive: Boolean = false,
    val rtmpUrl: String? = null,
    val framesComposed: Long = 0L,
    val framesEncoded: Long = 0L,
    val bytesEncoded: Long = 0L,
    val packetsTransmitted: Long = 0L,
    val bytesTransmitted: Long = 0L,
    val currentFps: Double = 0.0,
    val currentBitrateKbps: Int = 0,
    val isEncoderRunning: Boolean = false,
    val isRtmpConnected: Boolean = false,
    val uptimeSeconds: Long = 0L,
    val lastError: String? = null,
    val targetFps: Int = 30,
    val audioBitrateKbps: Int = 128,
    val isAudioActive: Boolean = false,
    val lastEncodedFrameTimestamp: Long = 0L,
    val lastRtmpSendTimestamp: Long = 0L,
    val healthStatus: String = "DISCONNECTED"
)

/**
 * Production Real-Time Video Encoder & Streaming Pipeline.
 *
 * Implements the Part B architecture:
 * ComposedBroadcastFrame → Hardware H.264 Encoder → Encoded Video Frames → Existing Streaming Pipeline
 *
 * Key features:
 * - Direct ingestion of [ComposedBroadcastFrame] from [BroadcastVideoCompositor]
 * - Hardware H.264 encoding via [MediaCodecVideoEncoder] ([IVideoEncoder])
 * - Downstream packet transmission to [RtmpIngestGateway] ([IYouTubeIngest])
 * - Full lifecycle orchestration (start, pause, stop, release)
 * - Safe error handling and non-blocking asynchronous coroutine execution
 * - Real-time diagnostic metrics tracking (FPS, Bitrate, Bytes, Uptime)
 */
class BroadcastStreamingPipeline(
    private val compositor: IBroadcastCompositor = BroadcastVideoCompositor.getInstance(),
    private val encoder: IVideoEncoder = MediaCodecVideoEncoder(),
    private val ingestGateway: IYouTubeIngest = RtmpIngestGateway(),
    private val youtubeLiveService: IYouTubeLiveService? = null
) : IStreamingService {

    companion object {
        private const val TAG = "BroadcastPipeline"

        @Volatile
        private var instance: BroadcastStreamingPipeline? = null

        fun getInstance(context: Context? = null): BroadcastStreamingPipeline {
            return instance ?: synchronized(this) {
                instance ?: BroadcastStreamingPipeline(
                    compositor = BroadcastVideoCompositor.getInstance(),
                    encoder = MediaCodecVideoEncoder(),
                    ingestGateway = RtmpIngestGateway(),
                    youtubeLiveService = if (context != null) YouTubeLiveService.getInstance(context.applicationContext) else null
                ).also { instance = it }
            }
        }
    }

    private val pipelineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pipelineJob: Job? = null
    private var encoderJob: Job? = null
    private var metricsJob: Job? = null
    private var audioCaptureJob: Job? = null
    private val audioEncoder = MediaCodecAudioEncoder()
    private var musicDecoder: com.example.services.audio.MusicPcmDecoder? = null
    private var streamingChannel: kotlinx.coroutines.channels.Channel<ComposedBroadcastFrame>? = null

    private val isPipelineActive = AtomicBoolean(false)
    private val framesComposedCount = AtomicLong(0L)
    private val framesEncodedCount = AtomicLong(0L)
    private val bytesEncodedCount = AtomicLong(0L)
    private val packetsTransmittedCount = AtomicLong(0L)
    private val bytesTransmittedCount = AtomicLong(0L)

    private var sessionStartTimeMs: Long = 0L
    private var activeRtmpUrl: String? = null

    private val lastEncodedFrameTimestamp = AtomicLong(0L)
    private val lastRtmpSendTimestamp = AtomicLong(0L)
    private val lastErrorState = MutableStateFlow<String?>(null)
    private var configuredTargetFps: Int = 30

    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.NotConnected)
    override val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    private val _pipelineMetrics = MutableStateFlow(BroadcastPipelineMetrics())
    val pipelineMetrics: StateFlow<BroadcastPipelineMetrics> = _pipelineMetrics.asStateFlow()

    private val _encodedFrames = MutableSharedFlow<EncodedVideoFrame>(extraBufferCapacity = 64)
    val encodedFrames: SharedFlow<EncodedVideoFrame> = _encodedFrames.asSharedFlow()

    /**
     * Starts the video encoding and streaming pipeline.
     *
     * @param rtmpUrl Optional RTMP ingest URL. If provided, connects [RtmpIngestGateway].
     * @param width Encoding resolution width (default 1280).
     * @param height Encoding resolution height (default 720).
     * @param bitrate Target encoding bitrate in bps (default 2,500,000).
     * @param fps Target frame rate (default 30).
     */
    private var activeContext: Context? = null

    fun startPipeline(
        context: Context? = null,
        rtmpUrl: String? = null,
        width: Int = 1280,
        height: Int = 720,
        bitrate: Int = 4000000,
        fps: Int = 30
    ): Result<Unit> {
        if (isPipelineActive.get()) {
            return Result.success(Unit) // Already active
        }

        return try {
            activeRtmpUrl = rtmpUrl
            activeContext = context

            // 1. Configure Hardware H.264 Encoder
            val configRes = encoder.configureEncoder(width, height, bitrate, fps)
            if (configRes.isFailure) {
                val error = configRes.exceptionOrNull()?.message ?: "Failed to configure hardware encoder"
                _streamingState.value = StreamingState.StreamError(error)
                return Result.failure(configRes.exceptionOrNull()!!)
            }

            // Configure AAC Audio Encoder
            val audioConfigRes = audioEncoder.configureEncoder()
            if (audioConfigRes.isFailure) {
                val error = audioConfigRes.exceptionOrNull()?.message ?: "Failed to configure AAC audio encoder"
                encoder.stopEncoder()
                _streamingState.value = StreamingState.StreamError(error)
                return Result.failure(audioConfigRes.exceptionOrNull()!!)
            }
            
            // Initialize music decoder if playing
            val currentMusic = com.example.services.audio.LocalMusicPlayerManager.musicState.value
            if (context != null) {
                currentMusic.mediaUri?.let { uri ->
                    try {
                        musicDecoder = com.example.services.audio.MusicPcmDecoder(
                            context = context,
                            uri = android.net.Uri.parse(uri)
                        ).apply { start() }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to initialize music decoder: ${e.message}")
                        musicDecoder = null
                    }
                }
            }

            // 2. Connect Streaming Transport (RTMP Gateway) if an RTMP URL is specified
            var rtmpConnected = false
            if (!rtmpUrl.isNullOrBlank()) {
                val connectRes = ingestGateway.connectIngest(rtmpUrl)
                if (connectRes.isSuccess) {
                    rtmpConnected = true
                } else {
                    val errorMsg = connectRes.exceptionOrNull()?.message ?: "RTMP connection failed"
                    Log.e(TAG, "RTMP connection failed: $errorMsg")
                    encoder.stopEncoder()
                    audioEncoder.stopEncoder()
                    try { musicDecoder?.stop() } catch (_: Exception) {}
                    musicDecoder = null
                    _streamingState.value = StreamingState.StreamError(errorMsg)
                    return Result.failure(connectRes.exceptionOrNull()!!)
                }
            }

            // Reset counters
            framesComposedCount.set(0L)
            framesEncodedCount.set(0L)
            bytesEncodedCount.set(0L)
            packetsTransmittedCount.set(0L)
            bytesTransmittedCount.set(0L)
            lastEncodedFrameTimestamp.set(0L)
            lastRtmpSendTimestamp.set(0L)
            lastErrorState.value = null
            configuredTargetFps = fps
            sessionStartTimeMs = System.currentTimeMillis()

            isPipelineActive.set(true)
            updateMetricsSnapshot(0.0, 0, 0L)

            val channel = kotlinx.coroutines.channels.Channel<ComposedBroadcastFrame>(
                capacity = 2,
                onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
            )
            streamingChannel = channel

            // 3. Connect ComposedBroadcastFrame stream to bounded channel
            pipelineJob = pipelineScope.launch {
                compositor.composedFrames.collect { composedFrame ->
                    if (!isPipelineActive.get()) return@collect
                    channel.trySend(composedFrame)
                }
            }

            // Background encoding worker from bounded queue
            encoderJob = pipelineScope.launch(Dispatchers.Default) {
                for (frame in channel) {
                    if (!isPipelineActive.get()) break
                    processComposedFrameInternal(frame)
                }
            }

            // Forward encoder packets to pipeline encoded flow if supported and transmit to RTMP
            var hasSentCodecConfigForSession = false

            encoder.encodedFrames?.let { encoderFlow ->
                pipelineScope.launch {
                    encoderFlow.collect { encodedFrame ->
                        _encodedFrames.tryEmit(encodedFrame)
                        
                        if (ingestGateway.isConnected) {
                            if (ingestGateway is RtmpIngestGateway) {
                                // Guarantee Codec Config (SPS/PPS) sequence header is sent BEFORE first video frame
                                if (!hasSentCodecConfigForSession) {
                                    if (encodedFrame.isCodecConfig) {
                                        ingestGateway.transmitVideoPacket(
                                            encodedFrame.data,
                                            encodedFrame.data.size,
                                            isKeyFrame = true,
                                            timestampMs = 0L,
                                            isCodecConfig = true
                                        )
                                        hasSentCodecConfigForSession = true
                                    } else if (encoder is MediaCodecVideoEncoder && encoder.spsPpsHeader != null) {
                                        val header = encoder.spsPpsHeader!!
                                        ingestGateway.transmitVideoPacket(
                                            header,
                                            header.size,
                                            isKeyFrame = true,
                                            timestampMs = 0L,
                                            isCodecConfig = true
                                        )
                                        hasSentCodecConfigForSession = true
                                    }
                                }

                                if (!encodedFrame.isCodecConfig) {
                                    val transmitRes = ingestGateway.transmitVideoPacket(
                                        encodedFrame.data,
                                        encodedFrame.data.size,
                                        isKeyFrame = encodedFrame.isKeyFrame,
                                        timestampMs = encodedFrame.presentationTimeUs / 1000L,
                                        isCodecConfig = false
                                    )

                                    if (transmitRes.isSuccess) {
                                        packetsTransmittedCount.incrementAndGet()
                                        bytesTransmittedCount.addAndGet(encodedFrame.data.size.toLong())
                                        lastRtmpSendTimestamp.set(System.currentTimeMillis())
                                    } else {
                                        lastErrorState.value = transmitRes.exceptionOrNull()?.message ?: "RTMP transmission failed"
                                    }
                                }
                            } else {
                                ingestGateway.transmitMuxedData(encodedFrame.data, encodedFrame.data.size)
                            }
                        } else {
                            hasSentCodecConfigForSession = false
                        }
                    }
                }
            }

            // Start Real-Time Audio Capture & AAC Encoding Loop
            audioCaptureJob = pipelineScope.launch(Dispatchers.IO) {
                runAudioCaptureLoop()
            }

            // Start metrics calculation loop
            startMetricsLoop(fps, bitrate)

            _streamingState.value = StreamingState.StreamingLive(
                broadcastId = activeRtmpUrl ?: "local_broadcast",
                bitrateKbps = bitrate / 1000,
                fps = fps,
                uptimeSeconds = 0L
            )

            Result.success(Unit)
        } catch (e: Exception) {
            isPipelineActive.set(false)
            _streamingState.value = StreamingState.StreamError(e.message ?: "Unknown pipeline start error")
            Result.failure(e)
        }
    }

    /**
     * Synchronously/directly processes a single composed frame through the entire pipeline:
     * ComposedBroadcastFrame → Hardware H.264 Encoder → Encoded Video Frames → Ingest Gateway.
     */
    suspend fun processFrame(composedFrame: ComposedBroadcastFrame): Result<ByteArray> {
        return processComposedFrameInternal(composedFrame)
    }

    private suspend fun processComposedFrameInternal(composedFrame: ComposedBroadcastFrame): Result<ByteArray> {
        framesComposedCount.incrementAndGet()

        // 1. Send Composed Frame to Hardware H.264 Encoder with microsecond PTS
        val presentationTimeUs = composedFrame.timestampMs * 1000L
        val encodeResult = encoder.encodeFrame(composedFrame.bitmap, presentationTimeUs)

        return if (encodeResult.isSuccess) {
            val encodedBytes = encodeResult.getOrNull() ?: ByteArray(0)
            if (encodedBytes.isNotEmpty()) {
                framesEncodedCount.incrementAndGet()
                bytesEncodedCount.addAndGet(encodedBytes.size.toLong())
                lastEncodedFrameTimestamp.set(System.currentTimeMillis())

                // The actual RTMP transmission is now handled asynchronously in the encodedFrames collector
                // to ensure we get proper isCodecConfig and isKeyFrame flags.
            }
            if (_pipelineMetrics.value.isActive) {
                _pipelineMetrics.value = _pipelineMetrics.value.copy(
                    framesComposed = framesComposedCount.get(),
                    framesEncoded = framesEncodedCount.get(),
                    bytesEncoded = bytesEncodedCount.get(),
                    packetsTransmitted = packetsTransmittedCount.get(),
                    bytesTransmitted = bytesTransmittedCount.get(),
                    isEncoderRunning = encoder.isRunning,
                    isRtmpConnected = ingestGateway.isConnected
                )
            }
            Result.success(encodedBytes)
        } else {
            val error = encodeResult.exceptionOrNull()
            lastErrorState.value = error?.message ?: "Frame encoding failed"
            Log.w(TAG, "Frame encoding dropped: ${error?.message}")
            Result.failure(error ?: Exception("Frame encoding failed"))
        }
    }

    /**
     * Halts frame consumption, releases the hardware encoder, stops audio streams, and disconnects the streaming transport.
     * Guaranteed to be idempotent and safe to call concurrently or repeatedly.
     */
    fun stopPipeline(): Result<Unit> {
        return try {
            isPipelineActive.set(false)
            streamingChannel?.close()
            streamingChannel = null
            pipelineJob?.cancel()
            pipelineJob = null
            encoderJob?.cancel()
            encoderJob = null
            metricsJob?.cancel()
            metricsJob = null
            audioCaptureJob?.cancel()
            audioCaptureJob = null

            try {
                musicDecoder?.stop()
            } catch (_: Exception) {}
            musicDecoder = null

            try {
                encoder.stopEncoder()
            } catch (_: Exception) {}

            try {
                audioEncoder.stopEncoder()
            } catch (_: Exception) {}

            try {
                ingestGateway.disconnectIngest()
            } catch (_: Exception) {}

            _streamingState.value = StreamingState.NotConnected
            updateMetricsSnapshot(0.0, 0)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping broadcast pipeline: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun startMetricsLoop(targetFps: Int, targetBitrate: Int) {
        metricsJob?.cancel()
        metricsJob = pipelineScope.launch {
            var lastEncodedCount = 0L
            var lastBytesCount = 0L
            var lastCheckTime = System.currentTimeMillis()

            while (isActive && isPipelineActive.get()) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val deltaSec = ((now - lastCheckTime).toDouble() / 1000.0).coerceAtLeast(0.1)

                val currentEncoded = framesEncodedCount.get()
                val currentBytes = bytesEncodedCount.get()

                val fps = (currentEncoded - lastEncodedCount) / deltaSec
                val bitrateKbps = (((currentBytes - lastBytesCount) * 8) / (deltaSec * 1000.0)).toInt()

                lastEncodedCount = currentEncoded
                lastBytesCount = currentBytes
                lastCheckTime = now

                val uptime = (now - sessionStartTimeMs) / 1000L

                updateMetricsSnapshot(fps, bitrateKbps, uptime)

                if (_streamingState.value is StreamingState.StreamingLive) {
                    _streamingState.value = StreamingState.StreamingLive(
                        broadcastId = activeRtmpUrl ?: "live_stream",
                        bitrateKbps = bitrateKbps,
                        fps = fps.toInt(),
                        uptimeSeconds = uptime
                    )
                }
            }
        }
    }

    private fun calculateHealthStatus(
        isActive: Boolean,
        isRtmpConnected: Boolean,
        currentFps: Double,
        targetFps: Int,
        lastError: String?
    ): String {
        if (!isActive) return "DISCONNECTED"
        if (lastError != null) return "ERROR"
        if (!isRtmpConnected) return "WARNING"
        if (currentFps < targetFps * 0.7) return "WARNING"
        return "HEALTHY"
    }

    private fun updateMetricsSnapshot(fps: Double, bitrateKbps: Int, uptimeSeconds: Long = 0L) {
        val active = isPipelineActive.get()
        val errorMsg = lastErrorState.value
        val rtmpConn = ingestGateway.isConnected
        val audioActive = active && audioCaptureJob?.isActive == true
        val health = calculateHealthStatus(active, rtmpConn, fps, configuredTargetFps, errorMsg)

        _pipelineMetrics.value = BroadcastPipelineMetrics(
            isActive = active,
            rtmpUrl = activeRtmpUrl,
            framesComposed = framesComposedCount.get(),
            framesEncoded = framesEncodedCount.get(),
            bytesEncoded = bytesEncodedCount.get(),
            packetsTransmitted = packetsTransmittedCount.get(),
            bytesTransmitted = bytesTransmittedCount.get(),
            currentFps = fps,
            currentBitrateKbps = bitrateKbps,
            isEncoderRunning = encoder.isRunning,
            isRtmpConnected = rtmpConn,
            uptimeSeconds = uptimeSeconds,
            lastError = errorMsg,
            targetFps = configuredTargetFps,
            audioBitrateKbps = 128,
            isAudioActive = audioActive,
            lastEncodedFrameTimestamp = lastEncodedFrameTimestamp.get(),
            lastRtmpSendTimestamp = lastRtmpSendTimestamp.get(),
            healthStatus = health
        )
    }

    // --- IStreamingService Implementation ---

    override suspend fun authenticateYouTube(authCredentials: String): Result<Unit> {
        val service = youtubeLiveService ?: return Result.failure(IllegalStateException("YouTube Live Service not initialized"))
        _streamingState.value = StreamingState.Authenticating
        val authRes = service.authorizeWithToken(authCredentials)
        return if (authRes.isSuccess) {
            val channel = authRes.getOrNull()!!
            _streamingState.value = StreamingState.Authenticated(channel.title, channel.channelId)
            Result.success(Unit)
        } else {
            val err = authRes.exceptionOrNull()?.message ?: "YouTube Authorization failed"
            _streamingState.value = StreamingState.StreamError(err)
            Result.failure(authRes.exceptionOrNull()!!)
        }
    }

    override suspend fun createLiveBroadcast(config: BroadcastConfig): Result<String> {
        val service = youtubeLiveService ?: return Result.failure(IllegalStateException("YouTube Live Service not initialized"))
        val bRes = service.createLiveBroadcast(config.title, config.description, config.privacyStatus)
        if (bRes.isFailure) {
            val err = bRes.exceptionOrNull()?.message ?: "Broadcast creation failed"
            _streamingState.value = StreamingState.StreamError(err)
            return Result.failure(bRes.exceptionOrNull()!!)
        }

        val bInfo = bRes.getOrNull()!!
        val sRes = service.createLiveStream(config.title, "${config.targetFps}fps", config.targetResolution)
        if (sRes.isFailure) {
            val err = sRes.exceptionOrNull()?.message ?: "Stream creation failed"
            service.stopBroadcast(bInfo.broadcastId)
            _streamingState.value = StreamingState.StreamError(err)
            return Result.failure(sRes.exceptionOrNull()!!)
        }

        val streamId = sRes.getOrNull()!!
        val bindRes = service.bindBroadcastToStream(bInfo.broadcastId, streamId)
        if (bindRes.isFailure) {
            val err = bindRes.exceptionOrNull()?.message ?: "Stream binding failed"
            service.stopBroadcast(bInfo.broadcastId)
            _streamingState.value = StreamingState.StreamError(err)
            return Result.failure(bindRes.exceptionOrNull()!!)
        }

        val boundInfo = bindRes.getOrNull()!!
        val rtmpUrl = boundInfo.rtmpIngestUrl ?: ""
        _streamingState.value = StreamingState.BroadcastConfigured(bInfo.broadcastId, bInfo.title, rtmpUrl)
        return Result.success(bInfo.broadcastId)
    }

    override suspend fun startLiveStream(context: Context, broadcastId: String): Result<Unit> {
        val service = youtubeLiveService
        val rtmpUrl = service?.currentBroadcast?.value?.rtmpIngestUrl ?: activeRtmpUrl

        if (rtmpUrl.isNullOrBlank()) {
            val err = "No RTMP ingest URL available for broadcast $broadcastId"
            _streamingState.value = StreamingState.StreamError(err)
            return Result.failure(IllegalStateException(err))
        }

        // 1. Start hardware encoder & RTMP pipeline
        val pipeRes = startPipeline(context = context, rtmpUrl = rtmpUrl)
        if (pipeRes.isFailure) {
            service?.stopBroadcast(broadcastId)
            return pipeRes
        }

        // 2. Transition YouTube broadcast to LIVE
        val startRes = service?.startBroadcast(broadcastId)
        if (startRes != null && startRes.isFailure) {
            stopPipeline()
            service.stopBroadcast(broadcastId)
            val err = startRes.exceptionOrNull()?.message ?: "Failed to transition YouTube broadcast to LIVE"
            _streamingState.value = StreamingState.StreamError(err)
            return startRes
        }

        return Result.success(Unit)
    }

    override suspend fun stopLiveStream(): Result<Unit> {
        stopPipeline()
        youtubeLiveService?.stopBroadcast()
        return Result.success(Unit)
    }

    override fun disconnect() {
        stopPipeline()
        pipelineScope.launch {
            youtubeLiveService?.disconnect()
        }
    }

    private suspend fun runAudioCaptureLoop() {
        try {
            val sampleRate = 44100
            val bufferSize = 4096
            val micBuffer = ByteArray(bufferSize)
            val gameBuffer = ByteArray(bufferSize)
            val musicBuffer = ByteArray(bufferSize)
            var audioPtsUs = 0L
            var activeMusicUri: String? = null

            while (isPipelineActive.get()) {
                val micRead = MicrophoneCommentaryManager.read(micBuffer, 0, bufferSize)
                val gameRead = InternalAudioCaptureManager.read(gameBuffer, 0, bufferSize)
                
                // Dynamic music decoder management
                val currentMusic = com.example.services.audio.LocalMusicPlayerManager.musicState.value
                val ctx = activeContext
                if (currentMusic.isPlaying && currentMusic.mediaUri != null && ctx != null) {
                    if (activeMusicUri != currentMusic.mediaUri) {
                        try {
                            musicDecoder?.stop()
                        } catch (_: Exception) {}
                        activeMusicUri = currentMusic.mediaUri
                        try {
                            musicDecoder = com.example.services.audio.MusicPcmDecoder(
                                context = ctx,
                                uri = android.net.Uri.parse(currentMusic.mediaUri)
                            ).apply { start() }
                        } catch (_: Exception) {
                            musicDecoder = null
                        }
                    }
                } else {
                    if (musicDecoder != null) {
                        try {
                            musicDecoder?.stop()
                        } catch (_: Exception) {}
                        musicDecoder = null
                        activeMusicUri = null
                    }
                }

                // Music decoding
                val musicRead = musicDecoder?.read(musicBuffer) ?: 0

                val mixerState = AudioMixerManager.mixerState.value
                
                val mixedPcm = PcmMixer.mix(
                    micBuffer.take(micRead).toByteArray(),
                    mixerState.micVolume,
                    mixerState.micMuted,
                    gameBuffer.take(gameRead).toByteArray(),
                    mixerState.gameVolume,
                    mixerState.gameMuted,
                    if (musicRead > 0) musicBuffer.take(musicRead).toByteArray() else null,
                    mixerState.musicVolume,
                    mixerState.musicMuted
                )

                if (mixedPcm.isNotEmpty()) {
                    audioEncoder.encodePcmData(mixedPcm, audioPtsUs) { aacPacket, ptsMs, isConfig ->
                        if (ingestGateway.isConnected && ingestGateway is RtmpIngestGateway) {
                            ingestGateway.transmitAudioPacket(aacPacket, aacPacket.size, ptsMs, isConfig)
                        }
                    }
                    audioPtsUs += (mixedPcm.size.toLong() * 1000000L) / (sampleRate * 2 * 2)
                } else {
                    delay(10L)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio capture loop error: ${e.message}", e)
        }
    }
}
