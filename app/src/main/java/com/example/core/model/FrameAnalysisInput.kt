package com.example.core.model

import com.example.services.video.VideoSourceType

/**
 * Platform-independent representation of a frame prepared for computer vision / OCR analysis.
 * Contains all necessary metadata and byte references without Android-specific UI classes.
 */
data class FrameAnalysisInput(
    val sequenceNumber: Long,
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val format: String = "RGBA_8888",
    val buffer: ByteArray? = null,
    val sourceType: VideoSourceType = VideoSourceType.ANDROID_MEDIA_PROJECTION,
    val appliedRoi: RoiRegion? = null,
    val isCropped: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FrameAnalysisInput

        if (sequenceNumber != other.sequenceNumber) return false
        if (timestampMs != other.timestampMs) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (format != other.format) return false
        if (sourceType != other.sourceType) return false
        if (appliedRoi != other.appliedRoi) return false
        if (isCropped != other.isCropped) return false
        if (metadata != other.metadata) return false
        if (buffer != null) {
            if (other.buffer == null) return false
            if (!buffer.contentEquals(other.buffer)) return false
        } else if (other.buffer != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + format.hashCode()
        result = 31 * result + (buffer?.contentHashCode() ?: 0)
        result = 31 * result + sourceType.hashCode()
        result = 31 * result + (appliedRoi?.hashCode() ?: 0)
        result = 31 * result + isCropped.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }

    companion object {
        fun fromDetectionFrame(
            frame: DetectionFrame,
            sourceType: VideoSourceType = VideoSourceType.ANDROID_MEDIA_PROJECTION,
            roi: RoiRegion? = null
        ): FrameAnalysisInput {
            return FrameAnalysisInput(
                sequenceNumber = frame.frameId,
                timestampMs = frame.timestampMs,
                width = frame.width,
                height = frame.height,
                format = frame.format,
                buffer = frame.buffer,
                sourceType = sourceType,
                appliedRoi = roi,
                isCropped = false
            )
        }
    }
}
