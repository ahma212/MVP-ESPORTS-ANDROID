package com.example.services.composition

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.core.model.TeamLiveState

/**
 * Real Broadcast Composition Overlay for the Overall Standing Table.
 *
 * Reuses live tournament standings from [teamsProvider] and renders
 * a premium MVP ESPORTS VIP broadcast panel on the right side.
 *
 * Columns:
 * # | TEAM | ALIVE | KILLS | PTS
 *
 * Visual:
 * - Deep navy glass
 * - Sky blue / cyan border
 * - Red live accent
 * - Gold premium title and points
 * - Top 1/2/3 premium row accents
 * - Eliminated rows grey
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

    // Reusable paints for good frame performance.
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        typeface = Typeface.create(
            Typeface.SANS_SERIF,
            Typeface.BOLD
        )
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = Typeface.create(
            Typeface.SANS_SERIF,
            Typeface.NORMAL
        )
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun draw(
        canvas: Canvas,
        frameWidth: Int,
        frameHeight: Int,
        timestampMs: Long
    ) {
        if (!isEnabled || frameWidth <= 0 || frameHeight <= 0) {
            return
        }

        val rawTeams =
            teamsProvider
                ?.invoke()
                ?.sortedBy { it.rank }
                ?.take(
                    maxTeams
                        .coerceAtLeast(1)
                        .coerceAtMost(16)
                )
                ?: emptyList()

        if (rawTeams.isEmpty()) {
            return
        }

        val s =
            ((frameWidth / 1920f) * scale)
                .coerceIn(0.70f, 1.20f)

        val panelWidth = 360f * s
        val headerHeight = 48f * s
        val rowHeight = 29f * s
        val panelHeight =
            (headerHeight + 30f + rawTeams.size * rowHeight + 10f) * s

        val posX =
            frameWidth - panelWidth - 24f * s

        val posY =
            frameHeight * yOffsetPercent

        val rect =
            RectF(
                posX,
                posY,
                posX + panelWidth,
                posY + panelHeight
            )

        /*
         * 1. VIP RED OUTER ACCENT
         */
        borderPaint.color =
            Color.argb(
                (55 * alpha).toInt().coerceIn(0, 255),
                255,
                23,
                68
            )

        borderPaint.strokeWidth =
            5f * s

        canvas.drawRoundRect(
            rect,
            14f * s,
            14f * s,
            borderPaint
        )

        /*
         * 2. SKY BLUE / CYAN BORDER
         */
        borderPaint.color =
            Color.argb(
                (190 * alpha).toInt().coerceIn(0, 255),
                0,
                229,
                255
            )

        borderPaint.strokeWidth =
            1.5f * s

        canvas.drawRoundRect(
            rect,
            14f * s,
            14f * s,
            borderPaint
        )

        /*
         * 3. DARK NAVY GLASS PANEL
         */
        bgPaint.color =
            Color.argb(
                (238 * alpha).toInt().coerceIn(0, 255),
                5,
                10,
                22
            )

        canvas.drawRoundRect(
            rect,
            14f * s,
            14f * s,
            bgPaint
        )

        /*
         * 4. HEADER BAR
         */
        val headerRect =
            RectF(
                posX,
                posY,
                posX + panelWidth,
                posY + headerHeight
            )

        bgPaint.color =
            Color.argb(
                235,
                12,
                29,
                54
            )

        canvas.drawRoundRect(
            headerRect,
            14f * s,
            14f * s,
            bgPaint
        )

        /*
         * 5. RED LIVE INDICATOR
         */
        bgPaint.color =
            Color.rgb(
                255,
                23,
                68
            )

        canvas.drawRoundRect(
            RectF(
                posX + 10f * s,
                posY + 12f * s,
                posX + 17f * s,
                posY + 32f * s
            ),
            4f * s,
            4f * s,
            bgPaint
        )

        /*
         * 6. GOLD MVP ESPORTS TITLE
         */
        headerPaint.color =
            Color.rgb(
                255,
                213,
                74
            )

        headerPaint.textSize =
            13f * s

        headerPaint.typeface =
            Typeface.create(
                Typeface.SANS_SERIF,
                Typeface.BOLD
            )

        canvas.drawText(
            "MVP ESPORTS",
            posX + 25f * s,
            posY + 23f * s,
            headerPaint
        )

        /*
         * 7. SKY BLUE SUBTITLE
         */
        headerPaint.color =
            Color.rgb(
                103,
                232,
                249
            )

        headerPaint.textSize =
            8.5f * s

        canvas.drawText(
            "VIP • LIVE STANDINGS",
            posX + 25f * s,
            posY + 38f * s,
            headerPaint
        )

        /*
         * 8. COLUMN HEADER
         */
        val columnTop =
            posY + headerHeight

        bgPaint.color =
            Color.argb(
                105,
                16,
                42,
                72
            )

        canvas.drawRect(
            posX,
            columnTop,
            posX + panelWidth,
            columnTop + 22f * s,
            bgPaint
        )

        textPaint.color =
            Color.rgb(
                125,
                211,
                252
            )

        textPaint.textSize =
            8.5f * s

        textPaint.typeface =
            Typeface.create(
                Typeface.SANS_SERIF,
                Typeface.BOLD
            )

        val columnY =
            columnTop + 15f * s

        canvas.drawText(
            "#",
            posX + 12f * s,
            columnY,
            textPaint
        )

        canvas.drawText(
            "TEAM",
            posX + 43f * s,
            columnY,
            textPaint
        )

        canvas.drawText(
            "ALIVE",
            posX + 202f * s,
            columnY,
            textPaint
        )

        canvas.drawText(
            "KILLS",
            posX + 260f * s,
            columnY,
            textPaint
        )

        canvas.drawText(
            "PTS",
            posX + 320f * s,
            columnY,
            textPaint
        )

        /*
         * 9. TEAM ROWS
         */
        rawTeams.forEachIndexed { index, team ->

            val rowY =
                columnTop +
                    28f * s +
                    index * rowHeight

            val isEliminated =
                team.isEliminated ||
                    team.currentAlivePlayers <= 0

            /*
             * Row background.
             */
            val rowRect =
                RectF(
                    posX + 6f * s,
                    rowY - 18f * s,
                    posX + panelWidth - 6f * s,
                    rowY + 6f * s
                )

            when {
                isEliminated -> {
                    bgPaint.color =
                        Color.argb(
                            48,
                            71,
                            85,
                            105
                        )

                    canvas.drawRoundRect(
                        rowRect,
                        4f * s,
                        4f * s,
                        bgPaint
                    )
                }

                team.rank == 1 -> {
                    bgPaint.color =
                        Color.argb(
                            45,
                            255,
                            215,
                            0
                        )

                    canvas.drawRoundRect(
                        rowRect,
                        4f * s,
                        4f * s,
                        bgPaint
                    )
                }

                team.rank == 2 -> {
                    bgPaint.color =
                        Color.argb(
                            38,
                            125,
                            211,
                            252
                        )

                    canvas.drawRoundRect(
                        rowRect,
                        4f * s,
                        4f * s,
                        bgPaint
                    )
                }

                team.rank == 3 -> {
                    bgPaint.color =
                        Color.argb(
                            35,
                            56,
                            189,
                            248
                        )

                    canvas.drawRoundRect(
                        rowRect,
                        4f * s,
                        4f * s,
                        bgPaint
                    )
                }

                else -> {
                    bgPaint.color =
                        Color.argb(
                            22,
                            19,
                            42,
                            70
                        )

                    canvas.drawRoundRect(
                        rowRect,
                        4f * s,
                        4f * s,
                        bgPaint
                    )
                }
            }

            /*
             * Rank color.
             */
            val rankColor =
                when {
                    isEliminated ->
                        Color.parseColor("#475569")

                    team.rank == 1 ->
                        Color.parseColor("#FFD54A")

                    team.rank == 2 ->
                        Color.parseColor("#7DD3FC")

                    team.rank == 3 ->
                        Color.parseColor("#38BDF8")

                    else ->
                        Color.parseColor("#CBD5E1")
                }

            textPaint.typeface =
                Typeface.create(
                    Typeface.SANS_SERIF,
                    Typeface.BOLD
                )

            textPaint.textSize =
                10.5f * s

            textPaint.color =
                rankColor

            canvas.drawText(
                "#${team.rank}",
                posX + 12f * s,
                rowY,
                textPaint
            )

            /*
             * Team name.
             */
            textPaint.color =
                if (isEliminated) {
                    Color.parseColor("#475569")
                } else {
                    Color.WHITE
                }

            val teamLabel =
                "T%02d %s".format(
                    team.teamNumber,
                    truncateText(
                        team.teamName,
                        145f * s,
                        textPaint
                    )
                )

            canvas.drawText(
                teamLabel,
                posX + 43f * s,
                rowY,
                textPaint
            )

            /*
             * Alive.
             */
            val aliveColor =
                when {
                    isEliminated ->
                        Color.parseColor("#475569")

                    team.currentAlivePlayers >= 3 ->
                        Color.parseColor("#4ADE80")

                    team.currentAlivePlayers == 2 ->
                        Color.parseColor("#FACC15")

                    else ->
                        Color.parseColor("#FF4D6D")
                }

            textPaint.color =
                aliveColor

            canvas.drawText(
                if (isEliminated) {
                    "DEAD"
                } else {
                    "${team.currentAlivePlayers}"
                },
                posX + 202f * s,
                rowY,
                textPaint
            )

            /*
             * Kills.
             */
            textPaint.color =
                if (isEliminated) {
                    Color.parseColor("#475569")
                } else {
                    Color.parseColor("#E2E8F0")
                }

            canvas.drawText(
                "${team.currentMatchKills}",
                posX + 264f * s,
                rowY,
                textPaint
            )

            /*
             * Points — GOLD.
             */
            textPaint.color =
                if (isEliminated) {
                    Color.parseColor("#475569")
                } else {
                    Color.parseColor("#FFD54A")
                }

            textPaint.typeface =
                Typeface.create(
                    Typeface.SANS_SERIF,
                    Typeface.BOLD
                )

            canvas.drawText(
                "${team.currentMatchPoints}",
                posX + 322f * s,
                rowY,
                textPaint
            )
        }

        /*
         * 10. PREMIUM RED + SKY-BLUE BOTTOM ACCENT
         */
        val accentY =
            posY +
                panelHeight -
                5f * s

        bgPaint.color =
            Color.rgb(
                255,
                23,
                68
            )

        canvas.drawRect(
            posX + 10f * s,
            accentY,
            posX + panelWidth * 0.42f,
            accentY + 2f * s,
            bgPaint
        )

        bgPaint.color =
            Color.rgb(
                0,
                229,
                255
            )

        canvas.drawRect(
            posX + panelWidth * 0.42f,
            accentY,
            posX + panelWidth - 10f * s,
            accentY + 2f * s,
            bgPaint
        )
    }

    private fun truncateText(
        text: String,
        maxWidth: Float,
        paint: Paint
    ): String {
        if (
            maxWidth <= 20f ||
            text.isBlank()
        ) {
            return ""
        }

        if (
            paint.measureText(text) <= maxWidth
        ) {
            return text
        }

        var truncated = text

        while (
            truncated.isNotEmpty() &&
            paint.measureText("$truncated…") > maxWidth
        ) {
            truncated =
                truncated.dropLast(1)
        }

        return if (
            truncated.isEmpty()
        ) {
            ""
        } else {
            "$truncated…"
        }
    }
}