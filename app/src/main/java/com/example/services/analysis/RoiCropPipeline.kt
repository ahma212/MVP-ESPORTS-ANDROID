package com.example.services.analysis

import com.example.core.model.FrameAnalysisInput
import com.example.core.model.PixelRect
import com.example.core.model.RoiRegion

/**
 * Platform-independent pipeline for cropping frame data using normalized ROI coordinates.
 * Handles bounds clamping, stride extraction, and safe buffer slicing.
 */
object RoiCropPipeline {

    /**
     * Crops a FrameAnalysisInput according to the specified normalized ROI region.
     * Returns a new FrameAnalysisInput containing strictly the sub-region pixels.
     */
    fun crop(input: FrameAnalysisInput, roi: RoiRegion): FrameAnalysisInput {
        if (!roi.enabled || input.buffer == null || input.width <= 0 || input.height <= 0) {
            return input.copy(appliedRoi = roi)
        }

        val pixelRect = roi.toPixelRect(input.width, input.height)
        val croppedBuffer = cropRgbaBuffer(
            sourceBuffer = input.buffer,
            srcWidth = input.width,
            srcHeight = input.height,
            rect = pixelRect
        )

        return FrameAnalysisInput(
            sequenceNumber = input.sequenceNumber,
            timestampMs = input.timestampMs,
            width = pixelRect.width,
            height = pixelRect.height,
            format = input.format,
            buffer = croppedBuffer,
            sourceType = input.sourceType,
            appliedRoi = roi,
            isCropped = true,
            metadata = input.metadata + mapOf(
                "crop_rect" to "${pixelRect.left},${pixelRect.top},${pixelRect.width},${pixelRect.height}",
                "original_resolution" to "${input.width}x${input.height}"
            )
        )
    }

    /**
     * Slices an RGBA_8888 4-bytes-per-pixel buffer into a rectangular sub-buffer.
     */
    fun cropRgbaBuffer(
        sourceBuffer: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        rect: PixelRect
    ): ByteArray? {
        val cropW = rect.width
        val cropH = rect.height
        if (cropW <= 0 || cropH <= 0 || rect.left >= srcWidth || rect.top >= srcHeight) {
            return null
        }

        val safeLeft = rect.left.coerceIn(0, srcWidth - 1)
        val safeTop = rect.top.coerceIn(0, srcHeight - 1)
        val safeWidth = cropW.coerceIn(1, srcWidth - safeLeft)
        val safeHeight = cropH.coerceIn(1, srcHeight - safeTop)

        val bytesPerPixel = 4 // RGBA_8888
        val srcRowStride = srcWidth * bytesPerPixel
        val dstRowStride = safeWidth * bytesPerPixel
        val outputSize = safeWidth * safeHeight * bytesPerPixel
        val dstBuffer = ByteArray(outputSize)

        for (row in 0 until safeHeight) {
            val srcOffset = ((safeTop + row) * srcRowStride) + (safeLeft * bytesPerPixel)
            val dstOffset = row * dstRowStride

            if (srcOffset + dstRowStride <= sourceBuffer.size && dstOffset + dstRowStride <= dstBuffer.size) {
                System.arraycopy(sourceBuffer, srcOffset, dstBuffer, dstOffset, dstRowStride)
            }
        }

        return dstBuffer
    }
}
