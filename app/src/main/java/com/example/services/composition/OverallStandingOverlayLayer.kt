package com.example.services.composition

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.core.model.TeamLiveState
import com.example.services.streaming.LocalLiveRuntimeManager

/**
 * Real Broadcast Composition Overlay for the Overall Standing Table.
 *
 * Reuses live tournament standings from [LocalLiveRuntimeManager] and renders
 * a crisp VIP esports broadcast panel on the right side of the frame.
 *
 * Requirements fulfilled:
 * - VIP esports panel on the right side
 * - Columns: # | TEAM | ALIVE | KILLS | PTS
 * - Top 1/2/3 gold/silver/bronze row accents
 * - Eliminated row grey
 * - No player names on standing
 * - Dark navy glass (#070B14) + thin cyan border (#00E5FF)
 */
class OverallStandingOverlayLayer(
    override var isEnabled: Boolean = true,
    var xOffsetPercent: Float = 0.68f,
    var yOffsetPercent: Float = 0.05f,
    var scale: Float = 1.0f,
    var alpha: Float = 0.92f,
    var top3Mode: Boolean = false,
    var maxTeams: Int = 16,
    var teamsProvider: (() -> List<TeamLiveState>)? = null,
    var matchFormatProvider: (() -> String)? = null
) : IBroadcastLayer {

    override val layerId: String = LAYER_ID
    override val layerName: String = "Overall Standing Overlay"
    override val zIndex: Int = 100

    companion object {
        const val LAYER_ID = "overall_standing_overlay"
    }

    // Reusable Paints for performance
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        textSize = 14f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) 
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
        textSize = 12f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun draw(canvas: Canvas, frameWidth: Int, frameHeight: Int, timestampMs: Long) {
        if (!isEnabled || frameWidth <= 0 || frameHeight <= 0) return

        val rawTeams = teamsProvider?.invoke()?.sortedBy { it.rank }?.take(16) ?: emptyList()
        if (rawTeams.isEmpty()) return

        val s = (frameWidth / 1920f).coerceIn(0.7f, 1.2f)
        val panelWidth = 340f * s
        val panelHeight = (54f + rawTeams.size * 32f) * s
        val posX = frameWidth - panelWidth - 24f * s
        val posY = frameHeight * 0.15f
        val rect = RectF(posX, posY, posX + panelWidth, posY + panelHeight)

        // 1. Draw Panel Glass (Navy background #070B14 with high alpha)
        bgPaint.color = Color.argb(230, 7, 11, 20)
        canvas.drawRoundRect(rect, 12f * s, 12f * s, bgPaint)
        
        // Thin cyan border (#00E5FF)
        borderPaint.color = Color.argb(180, 0, 229, 255)
        borderPaint.strokeWidth = 1.5f * s
        canvas.drawRoundRect(rect, 12f * s, 12f * s, borderPaint)

        // 2. Header Bar
        bgPaint.color = Color.argb(120, 13, 21, 38)
        val headerRect = RectF(posX, posY, posX + panelWidth, posY + 40f * s)
        canvas.drawRoundRect(headerRect, 12f * s, 12f * s, bgPaint)

        headerPaint.color = Color.WHITE
        headerPaint.textSize = 13f * s
        headerPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("MVP ESPORTS • VIP STANDINGS", posX + 14f * s, posY + 24f * s, headerPaint)

        // Column Labels Header
        textPaint.color = Color.parseColor("#94A3B8")
        textPaint.textSize = 10f * s
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val colHeaderY = posY + 36f * s
        canvas.drawText("#", posX + 14f * s, colHeaderY, textPaint)
        canvas.drawText("TEAM", posX + 46f * s, colHeaderY, textPaint)
        canvas.drawText("ALIVE", posX + 175f * s, colHeaderY, textPaint)
        canvas.drawText("KILLS", posX + 235f * s, colHeaderY, textPaint)
        canvas.drawText("PTS", posX + 290f * s, colHeaderY, textPaint)
        
        // 3. Draw Rows (# TEAM ALIVE KILLS PTS)
        rawTeams.forEachIndexed { index, team ->
            val rowY = posY + 62f * s + index * 30f * s
            val isEliminated = team.isEliminated || team.currentAlivePlayers <= 0

            // Row background accent for top 3 or eliminated
            when {
                isEliminated -> {
                    bgPaint.color = Color.argb(60, 15, 23, 42)
                    canvas.drawRoundRect(RectF(posX + 6f * s, rowY - 16f * s, posX + panelWidth - 6f * s, rowY + 8f * s), 4f * s, 4f * s, bgPaint)
                }
                team.rank == 1 -> {
                    bgPaint.color = Color.argb(35, 255, 215, 0)
                    canvas.drawRoundRect(RectF(posX + 6f * s, rowY - 16f * s, posX + panelWidth - 6f * s, rowY + 8f * s), 4f * s, 4f * s, bgPaint)
                }
                team.rank == 2 -> {
                    bgPaint.color = Color.argb(35, 226, 232, 240)
                    canvas.drawRoundRect(RectF(posX + 6f * s, rowY - 16f * s, posX + panelWidth - 6f * s, rowY + 8f * s), 4f * s, 4f * s, bgPaint)
                }
                team.rank == 3 -> {
                    bgPaint.color = Color.argb(35, 205, 127, 50)
                    canvas.drawRoundRect(RectF(posX + 6f * s, rowY - 16f * s, posX + panelWidth - 6f * s, rowY + 8f * s), 4f * s, 4f * s, bgPaint)
                }
            }

            // Rank Badge Color
            val rankColor = when {
                isEliminated -> Color.parseColor("#475569")
                team.rank == 1 -> Color.parseColor("#FFD700") // Gold
                team.rank == 2 -> Color.parseColor("#E2E8F0") // Silver
                team.rank == 3 -> Color.parseColor("#CD7F32") // Bronze
                else -> Color.parseColor("#94A3B8")
            }

            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textPaint.textSize = 11f * s
            textPaint.color = rankColor
            canvas.drawText("#${team.rank}", posX + 14f * s, rowY, textPaint)

            // Team Name
            textPaint.color = if (isEliminated) Color.parseColor("#475569") else Color.WHITE
            val teamLabel = "T%02d %s".format(team.teamNumber, truncateText(team.teamName, 110f * s, textPaint))
            canvas.drawText(teamLabel, posX + 46f * s, rowY, textPaint)

            // Alive Count
            val aliveColor = when {
                isEliminated -> Color.parseColor("#475569")
                team.currentAlivePlayers >= 3 -> Color.parseColor("#10B981")
                team.currentAlivePlayers == 2 -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#EF4444")
            }
            textPaint.color = aliveColor
            canvas.drawText(if (isEliminated) "DEAD" else "${team.currentAlivePlayers}", posX + 175f * s, rowY, textPaint)

            // Kills
            textPaint.color = if (isEliminated) Color.parseColor("#475569") else Color.WHITE
            canvas.drawText("${team.currentMatchKills}", posX + 240f * s, rowY, textPaint)

            // Points
            textPaint.color = if (isEliminated) Color.parseColor("#475569") else Color.parseColor("#00E5FF")
            canvas.drawText("${team.currentMatchPoints}", posX + 295f * s, rowY, textPaint)
        }
    }

    private fun truncateText(text: String, maxWidth: Float, paint: Paint): String {
        if (maxWidth <= 20f || text.isBlank()) return ""
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return if (truncated.isEmpty()) "" else "$truncated…"
    }
}
