package com.example.services.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer

class MusicPcmDecoder(private val context: Context, private val uri: Uri) {
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var bufferInfo = MediaCodec.BufferInfo()

    fun start() {
        var ext: MediaExtractor? = null
        var dec: MediaCodec? = null
        try {
            ext = MediaExtractor().apply {
                setDataSource(context, uri, null)
            }
            val trackCount = ext.trackCount
            if (trackCount <= 0) {
                ext.release()
                throw IllegalStateException("Media file has 0 tracks: $uri")
            }

            var audioTrackIndex = -1
            var defaultAudioIndex = -1

            for (i in 0 until trackCount) {
                val format = ext.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    if (audioTrackIndex == -1) {
                        audioTrackIndex = i
                    }
                    try {
                        if (format.containsKey("is-default") && format.getInteger("is-default") != 0) {
                            defaultAudioIndex = i
                        }
                    } catch (_: Exception) {}
                }
            }

            val targetTrack = if (defaultAudioIndex != -1) defaultAudioIndex else audioTrackIndex
            if (targetTrack == -1) {
                ext.release()
                throw IllegalStateException("No valid audio track found in media source: $uri")
            }

            ext.selectTrack(targetTrack)
            val format = ext.getTrackFormat(targetTrack)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IllegalStateException("Selected audio track has no MIME type")

            dec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            extractor = ext
            codec = dec
        } catch (e: Exception) {
            try { dec?.stop() } catch (_: Exception) {}
            try { dec?.release() } catch (_: Exception) {}
            try { ext?.release() } catch (_: Exception) {}
            extractor = null
            codec = null
            throw e
        }
    }

    fun read(buffer: ByteArray): Int {
        val codec = codec ?: return 0
        val extractor = extractor ?: return 0

        try {
            // Feed input
            val inputIndex = codec.dequeueInputBuffer(1000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize > 0) {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    } else {
                        if (LocalMusicPlayerManager.musicState.value.isLooping) {
                            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                            val loopSampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (loopSampleSize > 0) {
                                codec.queueInputBuffer(inputIndex, 0, loopSampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }
            }

            // Get output
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 1000)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                val size = minOf(bufferInfo.size, buffer.size)
                outputBuffer?.get(buffer, 0, size)
                codec.releaseOutputBuffer(outputIndex, false)
                return size
            }
        } catch (e: Exception) {
            // Handle decode failure gracefully
        }
        return 0
    }

    fun stop() {
        try {
            codec?.stop()
        } catch (_: Exception) {}
        try {
            codec?.release()
        } catch (_: Exception) {}
        try {
            extractor?.release()
        } catch (_: Exception) {}
        extractor = null
        codec = null
    }
}

