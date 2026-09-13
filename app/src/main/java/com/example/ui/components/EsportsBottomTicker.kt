package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable Bottom Live Ticker Component for Esports Broadcast.
 * Features continuous smooth scrolling motion, broadcast appearance, and speed configuration.
 */
@Composable
fun EsportsBottomTicker(
    tickerText: String,
    speed: Float = 1.0f,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!enabled || tickerText.isBlank()) return

    val infiniteTransition = rememberInfiniteTransition(label = "ticker_transition")
    // Adjust animation duration inverse to speed
    val durationMillis = (10000 / speed.coerceIn(0.2f, 5.0f)).toInt()
    
    val offsetX by infiniteTransition.animateFloat(
        initialValue = 1000f,
        targetValue = -1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ticker_offset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xE60A0A0C))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.offset { IntOffset(offsetX.toInt(), 0) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "  $tickerText  •  $tickerText  ",
                color = Color(0xFFFF8800),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}
