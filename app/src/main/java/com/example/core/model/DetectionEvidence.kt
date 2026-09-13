package com.example.core.model

/**
 * Exact source frame and ROI crop evidence capturing the visual occurrence that produced a detection.
 * Platform-independent data contract that can run on Android, Windows desktop, or Cloud workers.
 *
 * Explicitly carries:
 * - Source frame buffer & dimensions (sourceBuffer, frameWidth, frameHeight)
 * - ROI crop buffer & dimensions (croppedBuffer / buffer, croppedWidth, croppedHeight)
 * - Frame timestamp (frameTimestamp)
 * - ROI identifier (roiId)
 * - Evidence reference ID (referenceId)
 * - Extensible metadata (metadata)
 */
data class DetectionEvidence(
    val frameTimestamp: Long,
    val roiId: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val croppedWidth: Int,
    val croppedHeight: Int,
    val referenceId: String? = null,
    val buffer: ByteArray? = null,
    val sourceBuffer: ByteArray? = null,
    val croppedBuffer: ByteArray? = buffer,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DetectionEvidence

        if (frameTimestamp != other.frameTimestamp) return false
        if (roiId != other.roiId) return false
        if (frameWidth != other.frameWidth) return false
        if (frameHeight != other.frameHeight) return false
        if (croppedWidth != other.croppedWidth) return false
        if (croppedHeight != other.croppedHeight) return false
        if (referenceId != other.referenceId) return false
        if (metadata != other.metadata) return false
        if (buffer != null) {
            if (other.buffer == null) return false
            if (!buffer.contentEquals(other.buffer)) return false
        } else if (other.buffer != null) return false
        if (sourceBuffer != null) {
            if (other.sourceBuffer == null) return false
            if (!sourceBuffer.contentEquals(other.sourceBuffer)) return false
        } else if (other.sourceBuffer != null) return false
        if (croppedBuffer != null) {
            if (other.croppedBuffer == null) return false
            if (!croppedBuffer.contentEquals(other.croppedBuffer)) return false
        } else if (other.croppedBuffer != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = frameTimestamp.hashCode()
        result = 31 * result + roiId.hashCode()
        result = 31 * result + frameWidth
        result = 31 * result + frameHeight
        result = 31 * result + croppedWidth
        result = 31 * result + croppedHeight
        result = 31 * result + (referenceId?.hashCode() ?: 0)
        result = 31 * result + (buffer?.contentHashCode() ?: 0)
        result = 31 * result + (sourceBuffer?.contentHashCode() ?: 0)
        result = 31 * result + (croppedBuffer?.contentHashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }
}
