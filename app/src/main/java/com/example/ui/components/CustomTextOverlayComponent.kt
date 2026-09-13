package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.core.model.CustomTextOverlayItem

/**
 * Custom Text Overlay component managed by runtime state.
 * Supports configurable position, visibility animation, duration, and styling.
 */
@Composable
fun CustomTextOverlayComponent(
    item: CustomTextOverlayItem,
    modifier: Modifier = Modifier
) {
    if (!item.enabled) return

    val alignment = when {
        item.yPosition < 0.3f -> Alignment.TopCenter
        item.yPosition > 0.7f -> Alignment.BottomCenter
        else -> Alignment.Center
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = alignment
    ) {
        AnimatedVisibility(
            visible = item.isVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xD909090B))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = item.text,
                    color = Color(0xFFFF6600),
                    fontSize = item.fontSizeSp.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
