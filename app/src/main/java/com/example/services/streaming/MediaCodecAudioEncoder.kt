package com.example.services.streaming

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log

/**
 * Production AAC Audio Encoder using Android MediaCodec (AAC-LC).
 * Encodes PCM audio buffers into AAC bitstream packets for RTMP transmission.
 */
class MediaCodecAudioEncoder {
    companion object {
        private const val TAG = "MediaCodecAudioEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val SAMPLE_RATE = 44100
        private const val BIT_RATE = 64000
        private const val CHANNEL_COUNT = 2
    }

    private var mediaCodec: MediaCodec? = null
    var isRunning: Boolean = false
        private set

    fun configureEncoder(): Result<Unit> {
        return try {
            val format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            isRunning = true
            Result.success(Unit)
        } catch (e: Exception) {
            isRunning = false
            Log.e(TAG, "Failed to configure AAC encoder: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun encodePcmData(pcmBytes: ByteArray, presentationTimeUs: Long, onEncodedPacket: (ByteArray, Long, Boolean) -> Unit) {
        val codec = mediaCodec ?: return
        if (!isRunning) return

        try {
            val inputBufferIndex = codec.dequeueInputBuffer(10000L)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(pcmBytes)
                codec.queueInputBuffer(inputBufferIndex, 0, pcmBytes.size, presentationTimeUs, 0)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            while (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val outData = ByteArray(bufferInfo.size)
                    outputBuffer.get(outData)

                    val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    onEncodedPacket(outData, bufferInfo.presentationTimeUs / 1000L, isConfig)
                }
                codec.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AAC encoding error: ${e.message}")
        }
    }

    fun stopEncoder() {
        try {
            isRunning = false
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaCodec = null
    }
}
