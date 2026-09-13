package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HighDensityBorder
import com.example.ui.theme.HighDensityOrange
import com.example.ui.theme.HighDensityOrangeLight
import com.example.ui.theme.HighDensitySuccess
import com.example.ui.theme.HighDensitySurface
import com.example.ui.theme.HighDensityTextMuted
import com.example.ui.theme.HighDensityTextPrimary
import com.example.ui.theme.HighDensityTextSecondary
import com.example.ui.theme.HighDensityWarning

@Composable
fun ArchitecturePipelineViewer(
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        "PUBG MOBILE / LIVE VIDEO" to "Abstract Input (Android, Windows, Video File)",
        "VIDEO FRAME CAPTURE" to "IVideoInputService (DisplayMedia / VirtualDisplay)",
        "AI / COMPUTER VISION ANALYSIS" to "IDetectionService (Ready for Phase 4 CV Model)",
        "EVENT DETECTION" to "KNOCK, KILL, ELIMINATION, REVIVE",
        "PLAYER / TEAM IDENTIFICATION" to "OCR / In-game tagging & slot recognition",
        "CONFIDENCE SCORE" to "ConfidenceRule (>= 50% Auto | < 50% Admin Review)",
        "AUTOMATIC PROCESSING OR ADMIN REVIEW" to "High-confidence auto-processed immediately",
        "SUPABASE LIVE EVENTS" to "ISupabaseService (Single Source of Truth)",
        "LIVE LEADERBOARD" to "Real-time match scoring & placement matrix",
        "VIP STREAM OVERLAY" to "IOverlayService (Custom esports graphics)",
        "YOUTUBE LIVE" to "IStreamingService (Broadcast stream engine)"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(HighDensitySurface)
            .border(1.dp, HighDensityBorder, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = HighDensityOrange,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "END-TO-END PIPELINE ARCHITECTURE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = HighDensityTextPrimary,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        steps.forEachIndexed { index, (title, subtitle) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(22.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                when (index) {
                                    0, 1 -> HighDensitySuccess
                                    5, 6 -> HighDensityWarning
                                    7 -> HighDensityOrange
                                    else -> Color(0xFF2E2E32)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                    if (index < steps.size - 1) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(18.dp)
                                .background(HighDensityBorder)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(
                        text = title,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityTextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = subtitle,
                        fontSize = 9.sp,
                        color = HighDensityTextMuted
                    )
                }
            }
        }
    }
}

