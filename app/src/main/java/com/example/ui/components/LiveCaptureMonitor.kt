package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.HighDensityBorder
import com.example.ui.theme.HighDensityDanger
import com.example.ui.theme.HighDensityDangerDim
import com.example.ui.theme.HighDensityOrange
import com.example.ui.theme.HighDensityOrangeLight
import com.example.ui.theme.HighDensitySuccess
import com.example.ui.theme.HighDensitySurface
import com.example.ui.theme.HighDensityTextMuted
import com.example.ui.theme.HighDensityTextPrimary
import com.example.ui.theme.HighDensityTextSecondary

@Composable
fun LiveCaptureMonitor(
    isCapturing: Boolean,
    sourceName: String,
    framesCaptured: Long,
    fps: Int,
    resolution: String,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isCapturing) HighDensityOrange else HighDensityBorder

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(HighDensitySurface)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
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
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = if (isCapturing) HighDensityOrange else HighDensityTextMuted,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "PREVIEW_FEED_01",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            if (isCapturing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(HighDensityDanger)
                    )
                    Text(
                        text = "LIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityDanger,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF1A1A1C))
                        .border(1.dp, HighDensityBorder, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "NO SIGNAL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighDensityDanger,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Monitor viewport box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black)
                .border(1.dp, HighDensityBorder, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isCapturing) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF140F0A), Color(0xFF0A0A0B))
                            )
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SRC: $sourceName",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = HighDensityOrangeLight
                        )
                        Text(
                            text = "$fps FPS",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = HighDensitySuccess
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "CAPTURE STREAM ACTIVE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = HighDensityTextPrimary,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "AI / OCR: STANDBY",
                            fontSize = 10.sp,
                            color = HighDensityTextSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RES: $resolution",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = HighDensityTextMuted
                        )
                        Text(
                            text = "FRAMES: $framesCaptured",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = HighDensityTextMuted
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, HighDensityBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(HighDensityDanger)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "WAIT FOR SIGNAL INPUT...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextMuted,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

