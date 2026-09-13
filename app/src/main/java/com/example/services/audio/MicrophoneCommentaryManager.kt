package com.example.services.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MicrophoneState(
    val isRecording: Boolean = false,
    val hasPermission: Boolean = false,
    val statusMessage: String = "Microphone ready. Permission required.",
    val peakVolume: Float = 0f
)

/**
 * Microphone Commentary Manager handling runtime permission, start/stop recording,
 * volume, mute, and live metering for commentary input.
 * Thread-safe, single-instance AudioRecord lifecycle manager.
 */
object MicrophoneCommentaryManager {
    private val _micState = MutableStateFlow(MicrophoneState())
    val micState: StateFlow<MicrophoneState> = _micState.asStateFlow()

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Synchronized
    fun checkPermission(context: Context): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        _micState.update {
            it.copy(
                hasPermission = granted,
                statusMessage = if (granted) "Microphone permission granted." else "Microphone permission denied."
            )
        }
        return granted
    }

    @Synchronized
    fun startMicrophone(context: Context) {
        val hasPerm = checkPermission(context)
        if (!hasPerm) {
            _micState.update { it.copy(isRecording = false, statusMessage = "Cannot start: RECORD_AUDIO permission missing.") }
            return
        }

        if (_micState.value.isRecording && audioRecord != null && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            return
        }

        // Release any existing stale instance first
        stopMicrophoneInternal()

        try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (minBufferSize <= 0 || minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                _micState.update { it.copy(isRecording = false, statusMessage = "AudioRecord invalid min buffer size ($minBufferSize).") }
                return
            }

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize.coerceAtLeast(4096)
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                _micState.update { it.copy(isRecording = false, statusMessage = "AudioRecord failed to initialize.") }
                return
            }

            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                recorder.release()
                _micState.update { it.copy(isRecording = false, statusMessage = "AudioRecord failed to enter recording state.") }
                return
            }

            audioRecord = recorder
            _micState.update { it.copy(isRecording = true, statusMessage = "Commentary microphone live.") }

        } catch (e: Exception) {
            stopMicrophoneInternal()
            _micState.update { it.copy(isRecording = false, statusMessage = "Error starting mic: ${e.localizedMessage ?: e.message}") }
        }
    }

    @Synchronized
    fun read(buffer: ByteArray, offset: Int, size: Int): Int {
        val recorder = audioRecord ?: return 0
        return try {
            if (recorder.state == AudioRecord.STATE_INITIALIZED && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = recorder.read(buffer, offset, size)
                if (bytesRead > 0) {
                    calculatePeakVolume(buffer, offset, bytesRead)
                    bytesRead
                } else {
                    0
                }
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    @Synchronized
    fun stopMicrophone() {
        stopMicrophoneInternal()
        _micState.update { it.copy(isRecording = false, peakVolume = 0f, statusMessage = "Microphone stopped.") }
    }

    private fun stopMicrophoneInternal() {
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

    private fun calculatePeakVolume(buffer: ByteArray, offset: Int, bytesRead: Int) {
        var maxSample = 0
        var i = offset
        val end = offset + bytesRead - 1
        while (i < end) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val absSample = Math.abs(sample.toShort().toInt())
            if (absSample > maxSample) maxSample = absSample
            i += 2
        }
        val peak = (maxSample.toFloat() / 32767f).coerceIn(0f, 1f)
        _micState.update { it.copy(peakVolume = peak) }
    }
}
