package com.example.services.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class InternalAudioState(
    val isCapturing: Boolean = false,
    val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    val limitationMessage: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        "Internal audio capture (AudioPlaybackCapture) supported."
    else
        "Internal audio capture requires Android 10 (API 29) or higher."
)

/**
 * Manager for capturing internal game audio on Android 10+ via AudioPlaybackCapture.
 * Thread-safe and safe against cleanup leaks.
 */
object InternalAudioCaptureManager {
    private val _internalAudioState = MutableStateFlow(InternalAudioState())
    val internalAudioState: StateFlow<InternalAudioState> = _internalAudioState.asStateFlow()

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Synchronized
    @SuppressLint("MissingPermission")
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    fun startCapture(mediaProjection: MediaProjection) {
        if (!_internalAudioState.value.isSupported) return

        stopCaptureInternal()

        try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            val config = android.media.AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()

            val recorder = AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setAudioPlaybackCaptureConfig(config)
                .setBufferSizeInBytes(minBufSize.coerceAtLeast(4096))
                .build()

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                _internalAudioState.update { it.copy(isCapturing = false, limitationMessage = "Internal audio record failed to initialize.") }
                return
            }

            recorder.startRecording()
            audioRecord = recorder
            _internalAudioState.update { it.copy(isCapturing = true, limitationMessage = "Internal game audio capture active.") }
        } catch (e: Exception) {
            stopCaptureInternal()
            _internalAudioState.update { it.copy(isCapturing = false, limitationMessage = "Failed internal capture: ${e.localizedMessage ?: e.message}") }
        }
    }

    @Synchronized
    fun read(buffer: ByteArray, offset: Int, size: Int): Int {
        val recorder = audioRecord ?: return 0
        return try {
            if (recorder.state == AudioRecord.STATE_INITIALIZED && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = recorder.read(buffer, offset, size)
                if (bytesRead > 0) bytesRead else 0
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    @Synchronized
    fun stopCapture() {
        stopCaptureInternal()
        _internalAudioState.update { it.copy(isCapturing = false, limitationMessage = "Internal audio capture stopped.") }
    }

    private fun stopCaptureInternal() {
        val recorder = audioRecord
        audioRecord = null
        if (recorder != null) {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: Exception) {}
            try {
                recorder.release()
            } catch (_: Exception) {}
        }
    }
}
