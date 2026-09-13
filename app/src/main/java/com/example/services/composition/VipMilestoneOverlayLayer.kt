package com.example.services.composition

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import com.example.core.model.TeamLiveState
import com.example.services.streaming.StandingTableControlsManager
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Premium VIP Kill & Point Milestone Broadcast Card Layer (Part F).
 *
 * This layer renders team-specific milestone achievement cards in the center of the
 * broadcast output with high-fidelity glow, scaling entrance/exit, and diagonal shine sweeps.
 *
 * Guarantees:
 * 1. Tracks and displays achievements independently for kills and points.
 * 2. Each milestone per team triggers exactly once.
 * 3. Supports an active queue to show multiple achievements sequentially.
 * 4. Safe and lightweight, utilizing zero-allocation reusable paint elements.
 */
class VipMilestoneOverlayLayer(
    var teamsProvider: (() -> List<com.example.core.model.TeamLiveState>)? = null,
    override var isEnabled: Boolean = true
) : IBroadcastLayer {

    override val layerId: String = LAYER_ID
    override val layerName: String = "VIP Milestone Card"
    override val zIndex: Int = 300 // Milestone cards in front (zIndex = 300)

    companion object {
        const val LAYER_ID = "vip_milestone_card"
        private const val TAG = "VipMilestoneOverlay"

        // Milestone threshold configurations
        private val POINT_MILESTONES = setOf(20, 50, 80, 100, 120, 150, 200)
    }

    // Active milestone data structures
    data class ActiveMilestone(
        val teamNumber: Int,
        val teamName: String,
        val type: String, // "KILLS" or "POINTS"
        val value: Int,
        var triggeredTimeMs: Long = 0L
    )

    private val milestoneQueue = ConcurrentLinkedQueue<ActiveMilestone>()
    private var currentActive: ActiveMilestone? = null

    // Set of triggered milestones to prevent duplicate alerts (Format: "teamNum_type_value")
    private val triggeredKeys = HashSet<String>()

    // Reusable Paints & Shaders
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rectF = RectF()
    private val badgeRect = RectF()
    private val crownPath = Path()

    /**
     * Inspects team state changes and queues any newly reached milestones.
     * Runs lightweight comparisons on the actual live broadcast state.
     */
    fun detectMilestones(previousTeams: List<TeamLiveState>, currentTeams: List<TeamLiveState>) {
        val controls = StandingTableControlsManager.controlsState.value
        val killEnabled = controls.killCardEnabled
        val pointEnabled = controls.milestoneCardEnabled

        if (!killEnabled && !pointEnabled) return

        val prevMap = previousTeams.associateBy { it.teamNumber }

        for (curr in currentTeams) {
            val prev = prevMap[curr.teamNumber] ?: continue

            // 1. Detect Kill Milestones (Multiples of 5)
            if (killEnabled) {
                val currKills = curr.currentMatchKills
                val prevKills = prev.currentMatchKills
                if (currKills > prevKills) {
                    // Find all multiples of 5 crossed
                    for (milestone in 5..100 step 5) {
                        if (prevKills < milestone && currKills >= milestone) {
                            queueMilestone(curr.teamNumber, curr.teamName, "KILLS", milestone)
                        }
                    }
                }
            }

            // 2. Detect Point Milestones
            if (pointEnabled) {
                val currPoints = curr.currentMatchPoints
                val prevPoints = prev.currentMatchPoints
                if (currPoints > prevPoints) {
                    for (milestone in POINT_MILESTONES) {
                        if (prevPoints < milestone && currPoints >= milestone) {
                            queueMilestone(curr.teamNumber, curr.teamName, "POINTS", milestone)
                        }
                    }
                }
            }
        }
    }

    private fun queueMilestone(teamNumber: Int, teamName: String, type: String, value: Int) {
        val key = "${teamNumber}_${type}_$value"
        synchronized(triggeredKeys) {
            if (triggeredKeys.contains(key)) return
            triggeredKeys.add(key)
        }

        Log.d(TAG, "New VIP Milestone Detected: $teamName reached $value $type")
        milestoneQueue.offer(ActiveMilestone(teamNumber, teamName, type, value))
    }

    override fun draw(canvas: Canvas, frameWidth: Int, frameHeight: Int, timestampMs: Long) {
        // Obey master ON/OFF controls
        val controls = StandingTableControlsManager.controlsState.value
        val killEnabled = controls.killCardEnabled
        val pointEnabled = controls.milestoneCardEnabled
        val isControlOn = killEnabled || pointEnabled

        if (!isEnabled || !isControlOn || frameWidth <= 0 || frameHeight <= 0) {
            // Keep queue ticking even if hidden so it doesn't build backlog
            if (!isControlOn) {
                currentActive = null
                milestoneQueue.clear()
            }
            return
        }

        val active = currentActive
        if (active == null) {
            val next = milestoneQueue.poll()
            if (next != null) {
                val isNextEnabled = if (next.type == "KILLS") killEnabled else pointEnabled
                if (isNextEnabled) {
                    next.triggeredTimeMs = timestampMs
                    currentActive = next
                }
            }
            return
        }

        val isActiveEnabled = if (active.type == "KILLS") killEnabled else pointEnabled
        if (!isActiveEnabled) {
            currentActive = null
            return
        }

        val elapsedMs = timestampMs - active.triggeredTimeMs
        val entranceDurationMs = 500L
        val displayDurationMs = 3000L
        val exitDurationMs = 500L
        val totalDurationMs = entranceDurationMs + displayDurationMs + exitDurationMs

        if (elapsedMs >= totalDurationMs) {
            // Transition to next queued milestone
            currentActive = null
            return
        }

        // Calculate Scale and Alpha Animations
        val scale: Float
        val alpha: Int

        if (elapsedMs < entranceDurationMs) {
            // Entrance Transition
            val p = elapsedMs.toFloat() / entranceDurationMs
            scale = 0.7f + 0.3f * p
            alpha = (p * 255).toInt().coerceIn(0, 255)
        } else if (elapsedMs < entranceDurationMs + displayDurationMs) {
            // Static Display Phase
            scale = 1.0f
            alpha = 255
        } else {
            // Exit Transition
            val p = (elapsedMs - entranceDurationMs - displayDurationMs).toFloat() / exitDurationMs
            scale = 1.0f - 0.2f * p
            alpha = ((1f - p) * 255).toInt().coerceIn(0, 255)
        }

        // Renders in the absolute CENTER of the program frame
        val cx = frameWidth / 2f
        val cy = frameHeight / 2f
        val baseScale = (frameWidth / 1920f).coerceIn(0.6f, 1.5f)

        val cardWidth = 840f * baseScale * scale
        val cardHeight = 440f * baseScale * scale
        val cardLeft = cx - (cardWidth / 2f)
        val cardTop = cy - (cardHeight / 2f)
        val cardRight = cardLeft + cardWidth
        val cardBottom = cardTop + cardHeight

        // 1. Draw Radial Glow Shadow (Esports Luxury Look - simplified to 1 pass if heavy)
        val isHeavy = com.example.services.station.StationDeskManager.stationState.value.isHeavy
        val glowPasses = if (isHeavy) 1 else 4
        for (i in 1..glowPasses) {
            glowPaint.color = Color.argb((25 / i) * alpha / 255, 255, 170, 0)
            glowPaint.strokeWidth = i * 8f * baseScale
            rectF.set(cardLeft, cardTop, cardRight, cardBottom)
            canvas.drawRoundRect(rectF, 16f * baseScale, 16f * baseScale, glowPaint)
        }

        // 2. Draw Premium Glassmorphism Card Body
        bgPaint.color = Color.argb((235 * alpha) / 255, 10, 10, 15)
        rectF.set(cardLeft, cardTop, cardRight, cardBottom)
        canvas.drawRoundRect(rectF, 16f * baseScale, 16f * baseScale, bgPaint)

        // 3. Draw Dual Metallic Borders
        borderPaint.color = Color.argb((200 * alpha) / 255, 255, 185, 0)
        borderPaint.strokeWidth = 3f * baseScale
        canvas.drawRoundRect(rectF, 16f * baseScale, 16f * baseScale, borderPaint)

        val innerOffset = 8f * baseScale
        rectF.set(cardLeft + innerOffset, cardTop + innerOffset, cardRight - innerOffset, cardBottom - innerOffset)
        borderPaint.color = Color.argb((80 * alpha) / 255, 255, 220, 100)
        borderPaint.strokeWidth = 1f * baseScale
        canvas.drawRoundRect(rectF, 12f * baseScale, 12f * baseScale, borderPaint)

        // 4. Draw Royal Crown Decorative Element
        drawCrown(canvas, cx, cardTop + (45f * baseScale * scale), 45f * baseScale * scale, alpha)

        // 5. Title Text: VIP MILESTONE UNLOCKED
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = 15f * baseScale * scale
        textPaint.color = Color.argb((180 * alpha) / 255, 255, 215, 0)
        canvas.drawText("★ VIP BROADCAST MILESTONE ★", cx, cardTop + (115f * baseScale * scale), textPaint)

        // 6. Display Achieving Team Name
        textPaint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textPaint.textSize = 42f * baseScale * scale
        textPaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawText(active.teamName, cx, cardTop + (185f * baseScale * scale), textPaint)

        // 7. Subtitle / Label: REACHED THE MILESTONE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 14f * baseScale * scale
        textPaint.color = Color.argb((160 * alpha) / 255, 180, 180, 180)
        canvas.drawText("HAS OFFICIALLY ACHIEVED THE MILESTONE OF", cx, cardTop + (225f * baseScale * scale), textPaint)

        // 8. Large Elegant Value Badge (e.g. "10 KILLS" or "50 POINTS")
        val badgeW = 420f * baseScale * scale
        val badgeH = 80f * baseScale * scale
        val badgeL = cx - (badgeW / 2f)
        val badgeT = cardTop + (260f * baseScale * scale)
        badgeRect.set(badgeL, badgeT, badgeL + badgeW, badgeT + badgeH)

        // Golden gradient fill for the badge
        bgPaint.shader = LinearGradient(
            badgeL, badgeT, badgeL + badgeW, badgeT,
            intArrayOf(Color.parseColor("#FF9900"), Color.parseColor("#FFD700"), Color.parseColor("#FF9900")),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(badgeRect, 8f * baseScale * scale, 8f * baseScale * scale, bgPaint)
        bgPaint.shader = null

        // Badge Text
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = 28f * baseScale * scale
        textPaint.color = Color.argb(alpha, 15, 15, 20)
        val badgeText = "${active.value} ${active.type}"
        canvas.drawText(badgeText, cx, badgeT + (52f * baseScale * scale), textPaint)

        // 9. Premium Diagonal Shining Animation Sweep (Bypassed if phone is heavy)
        if (!isHeavy) {
            val shineDurationMs = 2000L
            val cycleTime = elapsedMs % shineDurationMs
            val shineProgress = cycleTime.toFloat() / shineDurationMs
            val sweepStart = cardLeft - 150f * baseScale
            val sweepEnd = cardRight + 150f * baseScale
            val shineX = sweepStart + (sweepEnd - sweepStart) * shineProgress

            shinePaint.shader = LinearGradient(
                shineX - (60f * baseScale), cardTop, shineX + (60f * baseScale), cardBottom,
                intArrayOf(Color.TRANSPARENT, Color.argb((120 * alpha) / 255, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rectF, 12f * baseScale, 12f * baseScale, shinePaint)
            shinePaint.shader = null
        }

        // Footer Brand Note
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textPaint.textSize = 11f * baseScale * scale
        textPaint.color = Color.argb((100 * alpha) / 255, 120, 120, 120)
        canvas.drawText("MVP ESPORTS OFFICIAL SYSTEM PERFORMANCE ALERT", cx, cardBottom - (35f * baseScale * scale), textPaint)
    }

    private fun drawCrown(canvas: Canvas, cx: Float, cy: Float, size: Float, alpha: Int) {
        crownPath.reset()
        val half = size / 2f
        val left = cx - half
        val top = cy - half

        // Construct elegant vector crown path
        crownPath.moveTo(left, cy + half)
        crownPath.lineTo(left, cy)
        crownPath.lineTo(cx - size * 0.25f, cy + size * 0.15f)
        crownPath.lineTo(cx, top)
        crownPath.lineTo(cx + size * 0.25f, cy + size * 0.15f)
        crownPath.lineTo(cx + half, cy)
        crownPath.lineTo(cx + half, cy + half)
        crownPath.close()

        bgPaint.color = Color.argb((200 * alpha) / 255, 255, 191, 0)
        canvas.drawPath(crownPath, bgPaint)

        // Outer jewel crown dots
        bgPaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawCircle(left, cy, 3f, bgPaint)
        canvas.drawCircle(cx, top, 4f, bgPaint)
        canvas.drawCircle(cx + half, cy, 3f, bgPaint)
    }
}
