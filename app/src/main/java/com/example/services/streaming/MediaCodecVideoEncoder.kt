package com.example.services.streaming

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Production Hardware Video Encoder implementation behind [IVideoEncoder] abstraction.
 * Uses Android [MediaCodec] for H.264 (AVC) encoding with configurable resolution, bitrate, FPS, and keyframe interval.
 * Includes proper start, input frame handling (YUV conversion & monotonic PTS), output packet draining (SPS/PPS & IDR keyframes),
 * stop, release, and error recovery.
 */
class MediaCodecVideoEncoder : IVideoEncoder {

    companion object {
        private const val TAG = "MediaCodecVideoEncoder"
        private const val TIMEOUT_US = 10000L
    }

    private var mediaCodec: MediaCodec? = null

private var inputSurface: Surface? = null

private val surfacePaint =
    Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    override var isRunning: Boolean = false
        private set

    var isHardwareCodec: Boolean = false
        private set

    var currentWidth: Int = 1280
        private set

    var currentHeight: Int = 720
        private set

    var currentBitrate: Int = 2500000
        private set

    var currentFps: Int = 30
        private set

    var frameCount: Long = 0L
        private set

    var totalBytesEncoded: Long = 0L
        private set

    var lastPresentationTimeUs: Long = -1L
        private set

    private var lastBufferInfo = MediaCodec.BufferInfo()
    private var lastOutputBuffer: ByteBuffer? = null
    private var lastOutputFormat: MediaFormat? = null
    var spsPpsHeader: ByteArray? = null
        private set

    private var cachedArgbBuffer: IntArray? = null
    private var cachedYuvBuffer: ByteArray? = null

    private val _encodedFrames = MutableSharedFlow<EncodedVideoFrame>(extraBufferCapacity = 64)
    fun getOutputBufferInfo(): MediaCodec.BufferInfo? = lastBufferInfo
    fun getOutputBuffer(): ByteBuffer? = lastOutputBuffer
    fun getOutputFormat(): MediaFormat? = lastOutputFormat

    fun releaseOutputBuffer(index: Int) {
        mediaCodec?.releaseOutputBuffer(index, false)
    }
    override val encodedFrames: SharedFlow<EncodedVideoFrame> = _encodedFrames.asSharedFlow()

    var selectedColorFormat: Int = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        private set

    var colorFormatOverride: Int? = null

    override fun configureEncoder(width: Int, height: Int, bitrate: Int, fps: Int): Result<Unit> {
        if (width <= 0 || height <= 0 || bitrate <= 0 || fps <= 0) {
            return Result.failure(IllegalArgumentException("Invalid encoder configuration parameters: ${width}x${height}, ${bitrate}bps, ${fps}fps"))
        }

        return try {
            currentWidth = width
            currentHeight = height
            currentBitrate = bitrate
            currentFps = fps

            // Release any existing encoder instance first
            stopEncoder()

// HARDWARE SURFACE INPUT
            // Removes the expensive CPU Bitmap -> YUV420 conversion on every frame.
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                width,
                height
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe = sharper

                // High Profile for better quality at same bitrate
                try {
                    setInteger(
                        MediaFormat.KEY_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
                    )
                    setInteger(
                        MediaFormat.KEY_LEVEL,
                        MediaCodecInfo.CodecProfileLevel.AVCLevel41
                    )
                } catch (_: Exception) {
                }

                try {
                    setInteger(
                        MediaFormat.KEY_BITRATE_MODE,
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                    )
                } catch (_: Exception) {
                }

                // Some devices support this for better quality
                try {
                    setInteger(MediaFormat.KEY_COMPLEXITY, 8)
                } catch (_: Exception) {
                }
            }

            try {
                val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)

                codec.configure(
                    format,
                    null,
                    null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE
                )

                // Hardware encoder Surface.
                val surface = codec.createInputSurface()

                codec.start()

                mediaCodec = codec
                inputSurface = surface
                isHardwareCodec = true

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Hardware Surface MediaCodec initialization failed: ${e.message}",
                    e
                )

                try {
                    mediaCodec?.release()
                } catch (_: Exception) {
                }

                mediaCodec = null

                try {
                    inputSurface?.release()
                } catch (_: Exception) {
                }

                inputSurface = null
                isHardwareCodec = false
                isRunning = false

                return Result.failure(e)
            }
// DO NOT use the requested input format for MediaMuxer.
// We will replace this only after MediaCodec reports
// INFO_OUTPUT_FORMAT_CHANGED.
lastOutputFormat = null
            isRunning = true
            frameCount = 0L
            totalBytesEncoded = 0L
            lastPresentationTimeUs = -1L
            spsPpsHeader = null

            Result.success(Unit)
        } catch (e: Exception) {
            isRunning = false
            Result.failure(e)
        }
    }

    override fun encodeFrame(bitmap: Bitmap, presentationTimeUs: Long): Result<ByteArray> {
        if (!isRunning) {
            return Result.failure(IllegalStateException("Encoder is not running or configured"))
        }

        if (bitmap.isRecycled) {
            return Result.failure(IllegalArgumentException("Cannot encode a recycled bitmap"))
        }

        return try {
            // Guarantee strictly monotonic presentation timestamps (PTS)
            val frameIntervalUs = 1_000_000L / currentFps.coerceAtLeast(1)
            val effectivePtsUs = if (presentationTimeUs > lastPresentationTimeUs) {
                presentationTimeUs
            } else {
                (lastPresentationTimeUs + frameIntervalUs).coerceAtLeast(0L)
            }
            lastPresentationTimeUs = effectivePtsUs

            val encodedBytes: ByteArray = if (isHardwareCodec && mediaCodec != null) {
                encodeHardwareFrame(bitmap, effectivePtsUs)
            } else {
                throw IllegalStateException("No valid hardware codec available or configured")
            }

            Result.success(encodedBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding video frame: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun encodeHardwareFrame(bitmap: Bitmap, presentationTimeUs: Long): ByteArray {
        val codec = mediaCodec ?: return ByteArray(0)

        // HARDWARE SURFACE INPUT
// No CPU ARGB -> YUV420 conversion.
val surface = inputSurface
    ?: return ByteArray(0)

val canvas: Canvas = surface.lockHardwareCanvas()

try {
    canvas.drawColor(android.graphics.Color.BLACK)

    val sourceRect = Rect(
        0,
        0,
        bitmap.width,
        bitmap.height
    )

    val destinationRect = Rect(
        0,
        0,
        currentWidth,
        currentHeight
    )

    canvas.drawBitmap(
        bitmap,
        sourceRect,
        destinationRect,
        surfacePaint
    )

} finally {
    surface.unlockCanvasAndPost(canvas)
}

        // 2. Output Packet Handling: Drain all available encoded output buffers
        val bufferInfo = MediaCodec.BufferInfo()
        val outputStream = ByteArrayOutputStream()
        var isKeyFrame = false

        var outputBufferIndex =
    codec.dequeueOutputBuffer(bufferInfo, 0L)
        while (outputBufferIndex >= 0 || outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                lastOutputFormat = newFormat
                val csd0 = newFormat.getByteBuffer("csd-0")
                val csd1 = newFormat.getByteBuffer("csd-1")
                if (csd0 != null && csd1 != null) {
                    val sps = ByteArray(csd0.remaining()).also { csd0.get(it); csd0.rewind() }
                    val pps = ByteArray(csd1.remaining()).also { csd1.get(it); csd1.rewind() }

                    val spsNalus = parseAnnexBNalus(sps)
                    val ppsNalus = parseAnnexBNalus(pps)
                    val spsNalu = spsNalus.firstOrNull { (it[0].toInt() and 0x1F) == 7 } ?: (if (spsNalus.isNotEmpty()) spsNalus[0] else sps)
                    val ppsNalu = ppsNalus.firstOrNull { (it[0].toInt() and 0x1F) == 8 } ?: (if (ppsNalus.isNotEmpty()) ppsNalus[0] else pps)

                    val configRecord = buildAvcDecoderConfigurationRecord(spsNalu, ppsNalu)
                    if (configRecord != null) {
                        spsPpsHeader = configRecord
                        _encodedFrames.tryEmit(
                            EncodedVideoFrame(
                                data = configRecord,
                                presentationTimeUs = bufferInfo.presentationTimeUs,
                                isKeyFrame = true,
                                isCodecConfig = true,
                                width = currentWidth,
                                height = currentHeight
                            )
                        )
                    }
                }
            } else {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(chunk)

                    val nalus = parseAnnexBNalus(chunk)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        val spsNalu = nalus.firstOrNull { (it[0].toInt() and 0x1F) == 7 }
                        val ppsNalu = nalus.firstOrNull { (it[0].toInt() and 0x1F) == 8 }
                        if (spsNalu != null && ppsNalu != null) {
                            val configRecord = buildAvcDecoderConfigurationRecord(spsNalu, ppsNalu)
                            if (configRecord != null) {
                                spsPpsHeader = configRecord
                                _encodedFrames.tryEmit(
                                    EncodedVideoFrame(
                                        data = configRecord,
                                        presentationTimeUs = 0L,
                                        isKeyFrame = true,
                                        isCodecConfig = true,
                                        width = currentWidth,
                                        height = currentHeight
                                    )
                                )
                            }
                        }
                    } else {
                        val configNalus = nalus.filter { (it[0].toInt() and 0x1F) == 7 || (it[0].toInt() and 0x1F) == 8 }
                        val videoNalus = nalus.filter { (it[0].toInt() and 0x1F) != 7 && (it[0].toInt() and 0x1F) != 8 }

                        if (configNalus.isNotEmpty()) {
                            val spsNalu = configNalus.firstOrNull { (it[0].toInt() and 0x1F) == 7 }
                            val ppsNalu = configNalus.firstOrNull { (it[0].toInt() and 0x1F) == 8 }
                            if (spsNalu != null && ppsNalu != null) {
                                val configRecord = buildAvcDecoderConfigurationRecord(spsNalu, ppsNalu)
                                if (configRecord != null) {
                                    spsPpsHeader = configRecord
                                    _encodedFrames.tryEmit(
                                        EncodedVideoFrame(
                                            data = configRecord,
                                            presentationTimeUs = 0L,
                                            isKeyFrame = true,
                                            isCodecConfig = true,
                                            width = currentWidth,
                                            height = currentHeight
                                        )
                                    )
                                }
                            }
                        }

                        if (videoNalus.isNotEmpty()) {
                            val hasIdr = videoNalus.any { (it[0].toInt() and 0x1F) == 5 }
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 || hasIdr) {
                                isKeyFrame = true
                            }

                            val avccOut = java.io.ByteArrayOutputStream()
                            for (nalu in videoNalus) {
                                val len = nalu.size
                                avccOut.write((len shr 24) and 0xFF)
                                avccOut.write((len shr 16) and 0xFF)
                                avccOut.write((len shr 8) and 0xFF)
                                avccOut.write(len and 0xFF)
                                avccOut.write(nalu)
                            }
                            outputStream.write(avccOut.toByteArray())
                        }
                    }
                }
                codec.releaseOutputBuffer(outputBufferIndex, false)
            }
            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
        }

        val resultBytes = outputStream.toByteArray()
        if (resultBytes.isNotEmpty()) {
            frameCount++
            totalBytesEncoded += resultBytes.size
            _encodedFrames.tryEmit(
                EncodedVideoFrame(
                    data = resultBytes,
                    presentationTimeUs = presentationTimeUs,
                    isKeyFrame = isKeyFrame,
                    isCodecConfig = false,
                    width = currentWidth,
                    height = currentHeight
                )
            )
        }
        return resultBytes
    }

    override fun stopEncoder() {
        try {
            isRunning = false
            mediaCodec?.let { codec ->
              try {
    codec.signalEndOfInputStream()
} catch (_: Exception) {
}
                try {
                    codec.stop()
                } catch (_: Exception) {}

                try {
                    codec.release()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaCodec: ${e.message}")
        } finally {
            mediaCodec = null

try {
    inputSurface?.release()
} catch (_: Exception) {
}

inputSurface = null
isHardwareCodec = false
        }
    }

    private fun parseAnnexBNalus(data: ByteArray): List<ByteArray> {
        val nalus = mutableListOf<ByteArray>()
        var i = 0
        var start = -1
        while (i <= data.size - 3) {
            val is4Byte = (i <= data.size - 4 && data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte())
            val is3Byte = (data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte())

            if (is4Byte || is3Byte) {
                val prefixLen = if (is4Byte) 4 else 3
                if (start != -1) {
                    val naluLen = i - start
                    if (naluLen > 0) {
                        val nalu = ByteArray(naluLen)
                        System.arraycopy(data, start, nalu, 0, naluLen)
                        nalus.add(nalu)
                    }
                }
                start = i + prefixLen
                i += prefixLen
            } else {
                i++
            }
        }
        if (start != -1 && start < data.size) {
            val naluLen = data.size - start
            if (naluLen > 0) {
                val nalu = ByteArray(naluLen)
                System.arraycopy(data, start, nalu, 0, naluLen)
                nalus.add(nalu)
            }
        }
        return nalus
    }

    private fun buildAvcDecoderConfigurationRecord(spsNalu: ByteArray, ppsNalu: ByteArray): ByteArray? {
        if (spsNalu.size < 4 || ppsNalu.isEmpty()) return null
        return try {
            val record = java.io.ByteArrayOutputStream()
            record.write(1) // configurationVersion = 1
            record.write(spsNalu[1].toInt() and 0xFF) // profile
            record.write(spsNalu[2].toInt() and 0xFF) // profile_compatibility
            record.write(spsNalu[3].toInt() and 0xFF) // level
            record.write(0xFF) // lengthSizeMinusOne = 3 (4 bytes)
            record.write(0xE1) // numOfSequenceParameterSets = 1
            record.write((spsNalu.size shr 8) and 0xFF)
            record.write(spsNalu.size and 0xFF)
            record.write(spsNalu)
            record.write(1) // numOfPictureParameterSets = 1
            record.write((ppsNalu.size shr 8) and 0xFF)
            record.write(ppsNalu.size and 0xFF)
            record.write(ppsNalu)
            record.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
