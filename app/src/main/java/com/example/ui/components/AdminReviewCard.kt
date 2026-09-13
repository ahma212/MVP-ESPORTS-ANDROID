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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.example.core.model.EsportsDetectedEvent
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.HighDensityBorder
import com.example.ui.theme.HighDensityBorderAccent
import com.example.ui.theme.HighDensityDanger
import com.example.ui.theme.HighDensityDangerDim
import com.example.ui.theme.HighDensityOrange
import com.example.ui.theme.HighDensityOrangeDim
import com.example.ui.theme.HighDensitySuccess
import com.example.ui.theme.HighDensitySuccessDim
import com.example.ui.theme.HighDensitySurface
import com.example.ui.theme.HighDensitySurfaceVariant
import com.example.ui.theme.HighDensityTextMuted
import com.example.ui.theme.HighDensityTextPrimary
import com.example.ui.theme.HighDensityTextSecondary
import com.example.ui.theme.HighDensityWarning
import com.example.ui.theme.HighDensityWarningDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-density Admin Review Foundation component for Phase 4C.
 *
 * Displays low-confidence events (<50%) awaiting administrator confirmation or rejection.
 * High-confidence detections (≥50%) bypass this queue and auto-process instantly.
 */
@Composable
fun AdminReviewCard(
    pendingEvents: List<EsportsDetectedEvent>,
    onConfirmEvent: (eventId: String) -> Unit,
    onRejectEvent: (eventId: String, reason: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HighDensitySurface)
            .border(1.dp, HighDensityBorder, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    tint = HighDensityOrange,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "ADMIN REVIEW QUEUE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextPrimary,
                    letterSpacing = 0.8.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (pendingEvents.isNotEmpty()) HighDensityWarningDim else HighDensitySuccessDim)
                    .border(
                        1.dp,
                        if (pendingEvents.isNotEmpty()) HighDensityWarning else HighDensitySuccess,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${pendingEvents.size} PENDING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (pendingEvents.isNotEmpty()) HighDensityWarning else HighDensitySuccess
                )
            }
        }

        // Confidence Gate Rule Note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(HighDensitySurfaceVariant)
                .border(1.dp, HighDensityBorderAccent, RoundedCornerShape(4.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = HighDensityOrange,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Rule: Events with confidence < 50% require Admin approval. Detections ≥ 50% auto-process without waiting.",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = HighDensityTextSecondary
            )
        }

        if (pendingEvents.isEmpty()) {
            // Clean Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(HighDensityBackground)
                    .border(1.dp, HighDensityBorder, RoundedCornerShape(6.dp))
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "NO EVENTS PENDING REVIEW",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextMuted
                    )
                    Text(
                        text = "The detection engine foundation is active. Incoming low-confidence detections will appear here for verification.",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextMuted.copy(alpha = 0.8f)
                    )
                }
            }
        } else {
            // Event List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pendingEvents.forEach { event ->
                    AdminReviewItemRow(
                        event = event,
                        timeFormatted = timeFormatter.format(Date(event.timestampMs)),
                        onConfirm = { onConfirmEvent(event.id) },
                        onReject = { onRejectEvent(event.id, "Admin manual rejection") }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminReviewItemRow(
    event: EsportsDetectedEvent,
    timeFormatted: String,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(HighDensityBackground)
            .border(1.dp, HighDensityBorderAccent, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Header: Event Type + Confidence + Timestamp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(HighDensityOrangeDim)
                        .border(1.dp, HighDensityOrange, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = event.eventType.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityOrange
                    )
                }

                Text(
                    text = "ID: ${event.id.take(8)}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextMuted
                )
            }

            // Confidence Score
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(HighDensityWarningDim)
                    .border(1.dp, HighDensityWarning, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                val pct = (event.confidence * 100).toInt()
                Text(
                    text = "CONF: $pct% (<50%)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityWarning
                )
            }
        }

        // Event Metadata Grid: Killer, Victim, Teams, Frame Evidence
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(HighDensitySurface)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Killer: ${event.killerPlayerName ?: "Unknown"} [${event.killerTeamTag ?: "No Team"}]",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextPrimary
                )
                Text(
                    text = "Victim: ${event.victimPlayerName ?: "Unknown"} [${event.victimTeamTag ?: "No Team"}]",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Evidence Frame: #${event.frameReferenceId ?: "N/A"}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextMuted
                )
                Text(
                    text = "Captured: $timeFormatted",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextMuted
                )
            }
        }

        // Action Buttons: Confirm / Reject
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HighDensitySuccess,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "CONFIRM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            OutlinedButton(
                onClick = onReject,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = HighDensityDangerDim,
                    contentColor = HighDensityDanger
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, HighDensityDanger),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "REJECT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
