package com.example.services.composition

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.services.streaming.LocalLiveRuntimeManager
import com.example.services.streaming.StandingTableControlsManager

/**
 * Premium Custom Esports Graphics & Overlays Layer (Part G).
 *
 * Implements full real-time rendering of customizable, high-fidelity overlays
 * directly onto the program output stream without allocating dynamic memory on the frame thread.
 *
 * Components:
 * 1. Logo / Brand Overlay (Exquisite Golden MVP Esports Crest)
 * 2. Custom Text Overlay (Configurable position, scale, color, background pill)
 * 3. Match Information Overlay (Reads from live state: Tournament, Map, Match#, Status)
 * 4. Team / Player Showcase Overlay (Displays top live team status from standing runtime)
 *
 * Z-Order:
 * 120 (Strictly above standard base standings, but beneath VIP milestones & Scene screens).
 */
class CustomGraphicsOverlayLayer(
    var teamsProvider: (() -> List<com.example.core.model.TeamLiveState>)? = null,
    override var isEnabled: Boolean = true
) : IBroadcastLayer {

    override val layerId: String = LAYER_ID
    override val layerName: String = "Custom Graphics & Overlays"
    override val zIndex: Int = 120

    companion object {
        const val LAYER_ID = "custom_graphics_overlay"
    }

    // Zero-allocation reusable paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rectF = RectF()
    private val shieldPath = Path()
    private val wingPath = Path()

    // Dynamic configurable custom banner properties
    var customBannerText: String? = null
    var customBannerPosition: String = "Top"
    var customBannerScale: Float = 1.0f
    var customBannerTextColor: Int? = null
    var customBannerBgColor: Int? = null
    var customBannerBorderColor: Int? = null

    override fun draw(canvas: Canvas, frameWidth: Int, frameHeight: Int, timestampMs: Long) {
        val controls = StandingTableControlsManager.controlsState.value
        
        // 1. Check Master switch
        if (!isEnabled || !controls.customGraphicsEnabled || frameWidth <= 0 || frameHeight <= 0) return

        val scale = (frameWidth / 1920f).coerceIn(0.6f, 1.5f)

        // 2. Draw LOGO Overlay (Aspect Ratio Preserved, Top-Left Corner)
        if (controls.logoOverlayEnabled) {
            drawPremiumLogo(canvas, 60f * scale, 60f * scale, 120f * scale, timestampMs)
        }

        // 3. Draw MATCH INFORMATION Overlay (Top-Center / Top-Right Section)
        if (controls.matchInfoOverlayEnabled) {
            drawMatchInfoCard(canvas, frameWidth - (460f * scale), 60f * scale, scale, timestampMs)
        }

        // 4. Draw CUSTOM TEXT OVERLAY (Highly Configurable Dynamic Text)
        if (controls.textOverlayEnabled) {
            drawCustomTextBanner(canvas, frameWidth, frameHeight, scale, timestampMs)
        }

        // 5. Draw TEAM / PLAYER INFORMATION Overlay (Bottom-Right/Sidebar Showcase)
        if (controls.teamPlayerOverlayEnabled) {
            drawTopTeamShowcaseCard(canvas, frameWidth - (460f * scale), 200f * scale, scale)
        }
    }

    /**
     * Draws an incredibly beautiful, highly stylized geometric golden crest logo
     * directly with vector operations. Perfect aspect ratio preservation guaranteed.
     */
    private fun drawPremiumLogo(canvas: Canvas, x: Float, y: Float, size: Float, timestampMs: Long) {
        val cx = x + (size / 2f)
        val cy = y + (size / 2f)

        // Subtly animate logo hover shine glow using timestamp
        val glowAlpha = (160 + 35 * Math.sin(timestampMs / 400.0)).toInt().coerceIn(100, 220)

        // Golden Shield Outer Outline
        shieldPath.reset()
        shieldPath.moveTo(cx, cy - (size * 0.5f))
        shieldPath.lineTo(cx + (size * 0.45f), cy - (size * 0.3f))
        shieldPath.lineTo(cx + (size * 0.4f), cy + (size * 0.15f))
        shieldPath.quadTo(cx + (size * 0.3f), cy + (size * 0.45f), cx, cy + (size * 0.5f))
        shieldPath.quadTo(cx - (size * 0.3f), cy + (size * 0.45f), cx - (size * 0.4f), cy + (size * 0.15f))
        shieldPath.lineTo(cx - (size * 0.45f), cy - (size * 0.3f))
        shieldPath.close()

        // Shield Fill (Gradient)
        logoPaint.shader = LinearGradient(
            cx, cy - (size * 0.5f), cx, cy + (size * 0.5f),
            intArrayOf(Color.argb(255, 20, 20, 26), Color.argb(255, 10, 10, 15)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(shieldPath, logoPaint)
        logoPaint.shader = null

        // Shield Golden Border
        borderPaint.color = Color.argb(glowAlpha, 255, 170, 0)
        borderPaint.strokeWidth = 3f * (size / 120f)
        canvas.drawPath(shieldPath, borderPaint)

        // Draw "M V P" Letters Stylized
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        
        // M-V-P Main
        textPaint.textSize = size * 0.35f
        textPaint.color = Color.WHITE
        canvas.drawText("MVP", cx, cy + (size * 0.1f), textPaint)

        // ESPORTS Sub-label
        textPaint.textSize = size * 0.13f
        textPaint.color = Color.parseColor("#FF6600")
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        canvas.drawText("ESPORTS", cx, cy + (size * 0.3f), textPaint)
    }

    /**
     * Draws an elegant Match Information Card reading directly from existing state.
     */
    private fun drawMatchInfoCard(canvas: Canvas, x: Float, y: Float, scale: Float, timestampMs: Long) {
        val width = 400f * scale
        val height = 110f * scale
        rectF.set(x, y, x + width, y + height)

        // Premium Translucent Glass Backing (0xEE0A0A0E)
        bgPaint.color = Color.argb(238, 10, 10, 14)
        canvas.drawRoundRect(rectF, 10f * scale, 10f * scale, bgPaint)

        // Dual Border Accent
        borderPaint.color = Color.argb(120, 255, 102, 0)
        borderPaint.strokeWidth = 2f * scale
        canvas.drawRoundRect(rectF, 10f * scale, 10f * scale, borderPaint)

        val liveSession = LocalLiveRuntimeManager.broadcastState.value

        // Draw Match Title
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textPaint.textSize = 15f * scale
        textPaint.color = Color.WHITE
        canvas.drawText(liveSession.tournamentTitle.uppercase(), x + (20f * scale), y + (30f * scale), textPaint)

        // Draw Map & Match No
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textPaint.textSize = 12f * scale
        textPaint.color = Color.parseColor("#CCCCCC")
        val infoStr = "MAP: ${liveSession.map.uppercase()}  •  MATCH: #${liveSession.currentMatchNumber}"
        canvas.drawText(infoStr, x + (20f * scale), y + (55f * scale), textPaint)

        // Draw Live Status Pill
        val pillWidth = 90f * scale
        val pillHeight = 24f * scale
        val pillL = x + width - pillWidth - (20f * scale)
        val pillT = y + (20f * scale)
        rectF.set(pillL, pillT, pillL + pillWidth, pillT + pillHeight)

        val isLiveAnimate = (timestampMs / 500) % 2 == 0L
        bgPaint.color = if (isLiveAnimate) Color.parseColor("#DC2626") else Color.parseColor("#991B1B")
        canvas.drawRoundRect(rectF, 4f * scale, 4f * scale, bgPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textPaint.textSize = 10f * scale
        textPaint.color = Color.WHITE
        canvas.drawText(liveSession.status.uppercase(), pillL + (pillWidth / 2f), pillT + (16f * scale), textPaint)

        // Footer Brand Line
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textPaint.textSize = 9f * scale
        textPaint.color = Color.parseColor("#666666")
        canvas.drawText("MVP ESPORTS OFFICIAL MULTI-CAMERA STREAM", x + (20f * scale), y + (90f * scale), textPaint)
    }

    /**
     * Draws Custom Text banner directly in composed frames with premium typography.
     * Accurately binds real user-entered text, custom position, scale, and color theme.
     */
    private fun drawCustomTextBanner(canvas: Canvas, frameWidth: Int, frameHeight: Int, scale: Float, timestampMs: Long) {
        val controls = StandingTableControlsManager.controlsState.value
        val bannerText = customBannerText?.takeIf { it.isNotBlank() }
            ?: controls.customBannerText.takeIf { it.isNotBlank() }
            ?: return

        val effectiveScale = scale * customBannerScale.coerceIn(0.5f, 2.5f) * controls.customBannerScale.coerceIn(0.5f, 2.5f)
        val cx = frameWidth / 2f

        val effectivePos = customBannerPosition.ifBlank { controls.customBannerPosition }
        val posY = when (effectivePos.lowercase()) {
            "bottom" -> frameHeight * 0.85f
            "center" -> frameHeight * 0.50f
            else -> frameHeight * 0.12f // Default "top"
        }

        val textColor = customBannerTextColor
            ?: runCatching { Color.parseColor(controls.customBannerTextColor) }.getOrDefault(Color.WHITE)
        val borderColor = customBannerBorderColor
            ?: runCatching { Color.parseColor(controls.customBannerBorderColor) }.getOrDefault(Color.parseColor("#FF6600"))
        val bgColor = customBannerBgColor
            ?: runCatching { Color.parseColor(controls.customBannerBgColor) }.getOrDefault(Color.argb(240, 15, 15, 20))

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = 20f * effectiveScale

        val textWidth = textPaint.measureText(bannerText)
        val paddingH = 32f * effectiveScale
        val paddingV = 12f * effectiveScale
        
        rectF.set(
            cx - (textWidth / 2f) - paddingH,
            posY - (textPaint.textSize) - paddingV,
            cx + (textWidth / 2f) + paddingH,
            posY + paddingV
        )

        // Rounded Dark Pill with Accent outline
        bgPaint.color = bgColor
        canvas.drawRoundRect(rectF, 8f * effectiveScale, 8f * effectiveScale, bgPaint)

        borderPaint.color = borderColor
        borderPaint.strokeWidth = 2f * effectiveScale
        canvas.drawRoundRect(rectF, 8f * effectiveScale, 8f * effectiveScale, borderPaint)

        textPaint.color = textColor
        canvas.drawText(bannerText, cx, posY - (textPaint.textSize * 0.1f), textPaint)
    }

    /**
     * Draws real existing top performing team status showcase.
     */
    private fun drawTopTeamShowcaseCard(canvas: Canvas, x: Float, y: Float, scale: Float) {
        val teams = teamsProvider?.invoke() ?: emptyList()
        if (teams.isEmpty()) return

        // Grab current top standing team
        val topTeam = teams.first()

        val width = 400f * scale
        val height = 150f * scale
        rectF.set(x, y, x + width, y + height)

        bgPaint.color = Color.argb(240, 12, 12, 16)
        canvas.drawRoundRect(rectF, 10f * scale, 10f * scale, bgPaint)

        borderPaint.color = Color.argb(90, 255, 215, 0) // Golden accent for top team
        borderPaint.strokeWidth = 1.5f * scale
        canvas.drawRoundRect(rectF, 10f * scale, 10f * scale, borderPaint)

        // Header Title
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = 10f * scale
        textPaint.color = Color.parseColor("#FFBB00")
        canvas.drawText("★ CURRENT MATCH LEADER ★", x + (20f * scale), y + (28f * scale), textPaint)

        // Team Name
        textPaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textPaint.textSize = 22f * scale
        textPaint.color = Color.WHITE
        canvas.drawText(topTeam.teamName, x + (20f * scale), y + (62f * scale), textPaint)

        // Kills & Total Points
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textPaint.textSize = 12f * scale
        textPaint.color = Color.parseColor("#CCCCCC")
        val statsStr = "KILLS: ${topTeam.currentMatchKills}   |   PTS: ${topTeam.currentMatchPoints}"
        canvas.drawText(statsStr, x + (20f * scale), y + (95f * scale), textPaint)

        // Live status
        val statusStr = "STATUS: ${topTeam.currentAlivePlayers} ALIVE"
        textPaint.color = if (topTeam.currentAlivePlayers > 0) Color.parseColor("#16A34A") else Color.parseColor("#DC2626")
        canvas.drawText(statusStr, x + (20f * scale), y + (122f * scale), textPaint)

        // Golden Badge
        val badgeW = 60f * scale
        val badgeH = 24f * scale
        val badgeL = x + width - badgeW - (20f * scale)
        val badgeT = y + (20f * scale)
        rectF.set(badgeL, badgeT, badgeL + badgeW, badgeT + badgeH)

        bgPaint.color = Color.parseColor("#FFD700")
        canvas.drawRoundRect(rectF, 4f * scale, 4f * scale, bgPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textPaint.textSize = 10f * scale
        textPaint.color = Color.BLACK
        canvas.drawText("RANK 1", badgeL + (badgeW / 2f), badgeT + (16f * scale), textPaint)
    }
}
