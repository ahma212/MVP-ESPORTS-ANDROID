package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.RoiRegion
import com.example.ui.theme.HighDensityBorder
import com.example.ui.theme.HighDensityDanger
import com.example.ui.theme.HighDensityOrange
import com.example.ui.theme.HighDensityOrangeLight
import com.example.ui.theme.HighDensitySuccess
import com.example.ui.theme.HighDensitySurface
import com.example.ui.theme.HighDensitySurfaceVariant
import com.example.ui.theme.HighDensityTextMuted
import com.example.ui.theme.HighDensityTextPrimary
import com.example.ui.theme.HighDensityTextSecondary
import java.util.Locale

/**
 * Inspection state for Phase 4C.3 Real Detection Inspector.
 */
data class RealDetectionInspectionState(
    val activeRoi: RoiRegion = RoiRegion.DEFAULT_KILL_FEED,
    val ocrLeftText: String = "-",
    val ocrRightText: String = "-",
    val detectedIcons: String = "-",
    val ignoredFlags: String = "-",
    val detectedEventType: String = "-",
    val killerName: String = "-",
    val victimName: String = "-",
    val confidence: Float = 0.0f,
    val decisionStatus: String = "NO_DECISION_WAIT", // "NO_DECISION_WAIT", "AUTO_PROCESS", "ADMIN_REVIEW"
    val decisionReason: String = "Waiting for clear visual kill-feed evidence",
    val uniqueEventId: String = "-",
    val frameTimestampMs: Long = 0L,
    // Performance Metrics
    val capturedFps: Int = 0,
    val sampledFps: Int = 0,
    val analyzedFps: Double = 0.0,
    val avgAnalysisTimeMs: Long = 0L,
    val droppedFrames: Long = 0L,
    val detectorLatencyMs: Long = 0L
)

@Composable
fun RealDetectionInspectorCard(
    inspectionState: RealDetectionInspectionState,
    fullFrameBitmap: ImageBitmap?,
    croppedRoiBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HighDensitySurface)
            .border(1.dp, HighDensityBorder, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = HighDensityOrange,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "REAL DETECTION INSPECTOR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            // Status Badge (NO_DECISION_WAIT / AUTO_PROCESS / ADMIN_REVIEW)
            val badgeColor = when (inspectionState.decisionStatus) {
                "AUTO_PROCESS" -> HighDensitySuccess
                "ADMIN_REVIEW" -> HighDensityOrange
                else -> Color(0xFF90A4AE) // NO_DECISION_WAIT
            }
            val badgeIcon = when (inspectionState.decisionStatus) {
                "AUTO_PROCESS" -> Icons.Default.CheckCircle
                "ADMIN_REVIEW" -> Icons.Default.Warning
                else -> Icons.Default.HourglassEmpty
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = inspectionState.decisionStatus,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = badgeColor
                )
            }
        }

        // Cropped Active ROI Viewport (Cropped Kill-Feed Only)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "CROPPED ROI (${inspectionState.activeRoi.name})",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = HighDensityOrange
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0F0B08))
                    .border(1.dp, HighDensityOrange.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (croppedRoiBitmap != null) {
                    Image(
                        bitmap = croppedRoiBitmap,
                        contentDescription = "Cropped ROI Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "NO ROI CROP",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextMuted
                    )
                }
            }
        }

        // Live Intermediate Parsing Readout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(HighDensitySurfaceVariant)
                .border(1.dp, HighDensityBorder, RoundedCornerShape(4.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "INTERMEDIATE PARSING DATA",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = HighDensityOrangeLight
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("OCR LEFT_TEXT:", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text(
                        text = inspectionState.ocrLeftText.ifBlank { "-" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("OCR RIGHT_TEXT:", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text(
                        text = inspectionState.ocrRightText.ifBlank { "-" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextPrimary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DETECTED ICONS:", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text(
                        text = inspectionState.detectedIcons.ifBlank { "-" },
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityOrange
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("IGNORED FLAGS / BADGES:", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text(
                        text = inspectionState.ignoredFlags.ifBlank { "None" },
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("KILLER / ATTACKER:", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text(
                        text = inspectionState.killerName.ifBlank { "-" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("VICTIM / TARGET:", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text(
                        text = inspectionState.victimName.ifBlank { "-" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityDanger
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("EVENT TYPE:", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text(
                        text = inspectionState.detectedEventType.ifBlank { "-" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextPrimary
                    )
                }
                Column {
                    Text("CONFIDENCE:", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text(
                        text = String.format(Locale.US, "%.1f%% (%.2f)", inspectionState.confidence * 100f, inspectionState.confidence),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (inspectionState.confidence >= 0.50f) HighDensitySuccess else HighDensityOrange
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "EVENT ID: ${inspectionState.uniqueEventId}",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextMuted
                )
                Text(
                    text = "FRAME TS: ${inspectionState.frameTimestampMs}ms",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextMuted
                )
            }
        }

        // Real Performance & Pipeline Latency Numbers
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0D1117))
                .border(1.dp, HighDensityBorder, RoundedCornerShape(4.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = HighDensityOrange,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "PERFORMANCE & PIPELINE LATENCY",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityOrange
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("CAPTURED FPS", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text("${inspectionState.capturedFps}", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = HighDensityTextPrimary)
                }
                Column {
                    Text("SAMPLED FPS", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text("${inspectionState.sampledFps}", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = HighDensityTextPrimary)
                }
                Column {
                    Text("ANALYZED FPS", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text(String.format(Locale.US, "%.1f", inspectionState.analyzedFps), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = HighDensitySuccess)
                }
                Column {
                    Text("AVG ANALYSIS", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text("${inspectionState.avgAnalysisTimeMs} ms", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = HighDensityOrangeLight)
                }
                Column {
                    Text("DROPPED", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text("${inspectionState.droppedFrames}", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = HighDensityTextSecondary)
                }
                Column {
                    Text("DETECTOR LATENCY", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = HighDensityTextMuted)
                    Text("${inspectionState.detectorLatencyMs} ms", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = HighDensitySuccess)
                }
            }
        }
    }
}
