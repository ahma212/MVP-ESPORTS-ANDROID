package com.example.core.model

/**
 * Platform-independent Region of Interest (ROI) with normalized coordinates [0.0 .. 1.0].
 * Automatically scales across varying resolutions, aspect ratios, and platforms.
 */
data class RoiRegion(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val enabled: Boolean = true,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    init {
        require(x in 0.0f..1.0f) { "ROI x must be in range [0.0, 1.0], was $x" }
        require(y in 0.0f..1.0f) { "ROI y must be in range [0.0, 1.0], was $y" }
        require(width in 0.0f..1.0f) { "ROI width must be in range [0.0, 1.0], was $width" }
        require(height in 0.0f..1.0f) { "ROI height must be in range [0.0, 1.0], was $height" }
    }

    /**
     * Calculates absolute pixel boundaries for a given frame dimension.
     */
    fun toPixelRect(frameWidth: Int, frameHeight: Int): PixelRect {
        val px = (x * frameWidth).toInt().coerceIn(0, frameWidth - 1)
        val py = (y * frameHeight).toInt().coerceIn(0, frameHeight - 1)
        val pw = (width * frameWidth).toInt().coerceIn(1, frameWidth - px)
        val ph = (height * frameHeight).toInt().coerceIn(1, frameHeight - py)
        return PixelRect(left = px, top = py, width = pw, height = ph)
    }

    /**
     * Clamps and creates a safe normalized copy.
     */
    fun clamped(
        newX: Float = x,
        newY: Float = y,
        newW: Float = width,
        newH: Float = height
    ): RoiRegion {
        val safeW = newW.coerceIn(0.01f, 1.0f)
        val safeH = newH.coerceIn(0.01f, 1.0f)
        val safeX = newX.coerceIn(0.0f, 1.0f - safeW)
        val safeY = newY.coerceIn(0.0f, 1.0f - safeH)
        return copy(
            x = safeX,
            y = safeY,
            width = safeW,
            height = safeH,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    companion object {
        val DEFAULT_KILL_FEED = RoiRegion(
            id = "roi_kill_feed",
            name = "KILL_FEED",
            x = 0.60f,
            y = 0.04f,
            width = 0.38f,
            height = 0.22f,
            enabled = true
        )

        val DEFAULT_PLAYER_ID = RoiRegion(
            id = "roi_player_id",
            name = "PLAYER_ID",
            x = 0.02f,
            y = 0.85f,
            width = 0.25f,
            height = 0.12f,
            enabled = false
        )

        val DEFAULT_TEAM_NUMBER = RoiRegion(
            id = "roi_team_number",
            name = "TEAM_NUMBER",
            x = 0.02f,
            y = 0.02f,
            width = 0.18f,
            height = 0.10f,
            enabled = false
        )
    }
}

/**
 * Absolute pixel boundary representation.
 */
data class PixelRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}
