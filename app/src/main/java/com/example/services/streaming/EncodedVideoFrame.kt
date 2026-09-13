package com.example.services.streaming

/**
 * Represents an H.264 encoded video frame packet produced by [IVideoEncoder].
 *
 * @param data The raw H.264 NAL unit(s) byte stream (Annex B formatted).
 * @param presentationTimeUs Presentation timestamp in microseconds (PTS).
 * @param isKeyFrame True if this packet contains an IDR keyframe.
 * @param isCodecConfig True if this packet contains codec configuration (SPS/PPS).
 * @param width Video width in pixels.
 * @param height Video height in pixels.
 * @param timestampMs Presentation timestamp converted to milliseconds.
 */
data class EncodedVideoFrame(
    val data: ByteArray,
    val presentationTimeUs: Long,
    val isKeyFrame: Boolean,
    val isCodecConfig: Boolean = false,
    val width: Int,
    val height: Int,
    val timestampMs: Long = presentationTimeUs / 1000L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncodedVideoFrame

        if (!data.contentEquals(other.data)) return false
        if (presentationTimeUs != other.presentationTimeUs) return false
        if (isKeyFrame != other.isKeyFrame) return false
        if (isCodecConfig != other.isCodecConfig) return false
        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + presentationTimeUs.hashCode()
        result = 31 * result + isKeyFrame.hashCode()
        result = 31 * result + isCodecConfig.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}
