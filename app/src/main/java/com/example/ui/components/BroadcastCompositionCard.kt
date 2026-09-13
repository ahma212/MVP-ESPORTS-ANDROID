package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.services.composition.ComposedBroadcastFrame

/**
 * Dedicated Operator Studio Card displaying the real-time Broadcast Video Compositor output.
 *
 * Distinct from Operator UI previews:
 * Shows the actual composed broadcast video frame (Real PUBG frame + Overall Standing + Bottom Ticker)
 * without any operator controls, floating overlays, or debugging panels.
 */
@Composable
fun BroadcastCompositionCard(
    composedFrame: ComposedBroadcastFrame?,
    standingOverlayEnabled: Boolean,
    tickerOverlayEnabled: Boolean,
    onToggleStanding: () -> Unit,
    onToggleTicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF101014))
            .border(1.dp, Color(0xFF27272A), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (composedFrame != null) Color(0xFF22C55E) else Color(0xFFEF4444))
                )
                Text(
                    text = "BROADCAST PROGRAM OUTPUT",
                    color = Color(0xFFFF8800),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "STAGE 2 COMPOSITOR",
                color = Color(0xFF71717A),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Layer Status Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val baseText = if (composedFrame != null) "${composedFrame.width}x${composedFrame.height}" else "WAITING"
            LayerStatusChip(label = "BASE PUBG", value = baseText, active = composedFrame != null, modifier = Modifier.weight(1f))
            LayerStatusChip(label = "STANDING", value = if (standingOverlayEnabled) "ON" else "OFF", active = standingOverlayEnabled, modifier = Modifier.weight(1f))
            LayerStatusChip(label = "TICKER", value = if (tickerOverlayEnabled) "ON" else "OFF", active = tickerOverlayEnabled, modifier = Modifier.weight(1f))
            val timeText = if (composedFrame != null) "${composedFrame.compositionTimeMs}ms" else "--"
            LayerStatusChip(label = "LATENCY", value = timeText, active = composedFrame != null, modifier = Modifier.weight(0.9f))
        }

        // Quick Broadcast Overlay Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onToggleStanding,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (standingOverlayEnabled) Color(0xFF27272A) else Color(0xFF18181B),
                    contentColor = if (standingOverlayEnabled) Color(0xFFFF8800) else Color(0xFF71717A)
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (standingOverlayEnabled) "HIDE STANDINGS" else "SHOW STANDINGS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Button(
                onClick = onToggleTicker,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tickerOverlayEnabled) Color(0xFF27272A) else Color(0xFF18181B),
                    contentColor = if (tickerOverlayEnabled) Color(0xFFFF8800) else Color(0xFF71717A)
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (tickerOverlayEnabled) "HIDE TICKER" else "SHOW TICKER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun LayerStatusChip(
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF18181C))
            .padding(vertical = 4.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color(0xFF71717A),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = if (active) Color(0xFFFF8800) else Color(0xFF52525B),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
