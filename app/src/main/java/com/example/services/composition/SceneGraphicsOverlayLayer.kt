package com.example.services.composition

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.core.model.EsportsScene
import com.example.services.scene.EsportsSceneEngine

/**
 * Real Broadcast Scene Graphics Overlay Layer.
 * Renders dedicated broadcast scene graphics, countdown timers, break screens,
 * and ending banners directly onto the program output when the broadcast is in
 * STARTING_COUNTDOWN, BREAK, MATCH_ENDED, NEXT_MATCH_COUNTDOWN, or ENDING scenes.
 * During LIVE_MATCH, it yields to pure PUBG gameplay with standard overlays.
 */
class SceneGraphicsOverlayLayer(
    override var isEnabled: Boolean = false
) : IBroadcastLayer {

    override val layerId: String = LAYER_ID
    override val layerName: String = "Broadcast Scene Graphics"
    override val zIndex: Int = 150

    companion object {
        const val LAYER_ID = "scene_graphics_overlay"
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        color = Color.parseColor("#FFFF6600")
        textAlign = Paint.Align.CENTER
    }

    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        color = Color.parseColor("#E0E0E0")
        textAlign = Paint.Align.CENTER
    }

    private val timerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = Color.parseColor("#FFFFFF")
        textAlign = Paint.Align.CENTER
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val rectF = RectF()

    override fun draw(canvas: Canvas, frameWidth: Int, frameHeight: Int, timestampMs: Long) {
        if (!isEnabled || frameWidth <= 0 || frameHeight <= 0) return

        val sceneState = EsportsSceneEngine.sceneState.value
        val currentScene = sceneState.currentScene

        // Only draw dedicated scene graphics for non-live match states (Countdown, Break, Match Ended, Ending, Idle)
        if (
    currentScene == EsportsScene.IDLE ||
    currentScene == EsportsScene.LIVE_MATCH ||
    currentScene == EsportsScene.STOPPED
) {
    return
}

        // Semi-transparent professional studio backdrop overlay
        bgPaint.color = Color.argb(220, 10, 10, 15)
        rectF.set(0f, 0f, frameWidth.toFloat(), frameHeight.toFloat())
        canvas.drawRect(rectF, bgPaint)

        val cx = frameWidth / 2f
        val cy = frameHeight / 2f
        val scale = (frameWidth / 1920f).coerceIn(0.6f, 1.5f)

        // Draw Studio Graphic Card Frame
        val cardWidth = 800f * scale
        val cardHeight = 420f * scale
        val cardLeft = cx - (cardWidth / 2f)
        val cardTop = cy - (cardHeight / 2f)

        bgPaint.color = Color.argb(240, 20, 20, 26)
        rectF.set(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
        canvas.drawRoundRect(rectF, 16f * scale, 16f * scale, bgPaint)

        borderPaint.color = Color.parseColor("#FF6600")
        borderPaint.strokeWidth = 2f * scale
        canvas.drawRoundRect(rectF, 16f * scale, 16f * scale, borderPaint)

        // Accent Header Bar
        val headerHeight = 64f * scale
        bgPaint.shader = LinearGradient(
            cardLeft, cardTop, cardLeft + cardWidth, cardTop,
            intArrayOf(Color.parseColor("#CC5500"), Color.parseColor("#FF8800"), Color.parseColor("#CC5500")),
            null, Shader.TileMode.CLAMP
        )
        rectF.set(cardLeft, cardTop, cardLeft + cardWidth, cardTop + headerHeight)
        canvas.drawRoundRect(rectF, 16f * scale, 16f * scale, bgPaint)
        bgPaint.shader = null

        // Header Title
        titlePaint.textSize = 22f * scale
        titlePaint.color = Color.WHITE
        val headerTitle = when (currentScene) {
            EsportsScene.STARTING_COUNTDOWN, EsportsScene.NEXT_MATCH_COUNTDOWN -> "TOURNAMENT MATCH STARTING SOON"
            EsportsScene.BREAK -> "TOURNAMENT BREAK & TACTICAL PAUSE"
            EsportsScene.MATCH_ENDED -> "MATCH CONCLUDED • STANDINGS FINALIZING"
            EsportsScene.ENDING -> "BROADCAST CONCLUDING"
            else -> "MVP ESPORTS OFFICIAL BROADCAST"
        }
        canvas.drawText(headerTitle, cx, cardTop + (headerHeight / 2f) + (titlePaint.textSize / 3f), titlePaint)

        // Subtitle / Custom Message
        subtitlePaint.textSize = 16f * scale
        subtitlePaint.color = Color.parseColor("#CCCCCC")
        canvas.drawText(sceneState.customMessage, cx, cardTop + headerHeight + (50f * scale), subtitlePaint)

        // Main Timer / Status Display
        val remainingSecs = when (currentScene) {
            EsportsScene.STARTING_COUNTDOWN, EsportsScene.NEXT_MATCH_COUNTDOWN -> sceneState.remainingCountdownSeconds
            EsportsScene.BREAK -> sceneState.remainingBreakSeconds
            EsportsScene.ENDING -> sceneState.remainingEndingSeconds
            else -> 0
        }

        if (sceneState.isRunning && remainingSecs > 0) {
            val mins = remainingSecs / 60
            val secs = remainingSecs % 60
            val timeStr = String.format("%02d:%02d", mins, secs)

            timerPaint.textSize = 64f * scale
            timerPaint.color = Color.parseColor("#FF8800")
            canvas.drawText(timeStr, cx, cardTop + headerHeight + (180f * scale), timerPaint)

            subtitlePaint.textSize = 14f * scale
            subtitlePaint.color = Color.parseColor("#888888")
            canvas.drawText("MATCH STARTING AUTOMATICALLY UPON COUNTDOWN ZERO", cx, cardTop + headerHeight + (230f * scale), subtitlePaint)
        } else {
            timerPaint.textSize = 36f * scale
            timerPaint.color = Color.parseColor("#FFFFFF")
            val statusStr = when (currentScene) {
    EsportsScene.MATCH_ENDED ->
        "STANDINGS LOCKED • PREPARING NEXT ROUND"

    EsportsScene.ENDING ->
        "THANK YOU FOR WATCHING MVP ESPORTS"

    EsportsScene.STARTING_COUNTDOWN,
    EsportsScene.NEXT_MATCH_COUNTDOWN,
    EsportsScene.BREAK ->
        ""

    else ->
        ""
}
            if (statusStr.isNotBlank()) {
    canvas.drawText(
        statusStr,
        cx,
        cardTop + headerHeight + (160f * scale),
        timerPaint
    )
}
        }

        // Footer Branding
        subtitlePaint.textSize = 13f * scale
        subtitlePaint.color = Color.parseColor("#666666")
        canvas.drawText("MVP ESPORTS OFFICIAL TOURNAMENT BROADCAST ENGINE • SECURE RTMP STREAM", cx, cardTop + cardHeight - (30f * scale), subtitlePaint)
    }
}
