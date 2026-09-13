package com.example.core.model

/**
 * Representation of a captured video frame passed to the AI / Computer Vision layer.
 * Platform-independent abstraction that works on Android, Windows desktop, or Web feeds.
 */
data class DetectionFrame(
    val frameId: Long,
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val format: String = "RGBA_8888",
    val buffer: ByteArray? = null,
    val sourceIdentifier: String = "screen_capture"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DetectionFrame

        if (frameId != other.frameId) return false
        if (timestampMs != other.timestampMs) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (format != other.format) return false
        if (sourceIdentifier != other.sourceIdentifier) return false
        if (buffer != null) {
            if (other.buffer == null) return false
            if (!buffer.contentEquals(other.buffer)) return false
        } else if (other.buffer != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = frameId.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + format.hashCode()
        result = 31 * result + (buffer?.contentHashCode() ?: 0)
        result = 31 * result + sourceIdentifier.hashCode()
        return result
    }
}
