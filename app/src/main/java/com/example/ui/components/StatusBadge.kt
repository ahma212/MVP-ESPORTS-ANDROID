package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.ui.theme.HighDensityBorder
import com.example.ui.theme.HighDensityDanger
import com.example.ui.theme.HighDensityDangerDim
import com.example.ui.theme.HighDensityInfo
import com.example.ui.theme.HighDensityInfoDim
import com.example.ui.theme.HighDensitySuccess
import com.example.ui.theme.HighDensitySuccessDim
import com.example.ui.theme.HighDensitySurface
import com.example.ui.theme.HighDensityTextMuted
import com.example.ui.theme.HighDensityTextPrimary
import com.example.ui.theme.HighDensityWarning
import com.example.ui.theme.HighDensityWarningDim

enum class StatusIndicatorType {
    ACTIVE,
    WARNING,
    INACTIVE,
    INFO
}

@Composable
fun StatusBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    indicatorType: StatusIndicatorType = when {
        value.equals("ACTIVE", ignoreCase = true) || value.equals("Connected", ignoreCase = true) -> StatusIndicatorType.ACTIVE
        value.equals("OFF", ignoreCase = true) || value.equals("Not connected", ignoreCase = true) -> StatusIndicatorType.INACTIVE
        value.contains("Starting", ignoreCase = true) || value.contains("Init", ignoreCase = true) -> StatusIndicatorType.WARNING
        else -> StatusIndicatorType.INFO
    }
) {
    val (badgeTextColor, badgeBgColor, badgeBorderColor) = when (indicatorType) {
        StatusIndicatorType.ACTIVE -> Triple(
            HighDensitySuccess,
            HighDensitySuccessDim,
            HighDensitySuccess.copy(alpha = 0.3f)
        )
        StatusIndicatorType.WARNING -> Triple(
            HighDensityWarning,
            HighDensityWarningDim,
            HighDensityWarning.copy(alpha = 0.3f)
        )
        StatusIndicatorType.INACTIVE -> {
            if (value.equals("OFF", ignoreCase = true)) {
                Triple(
                    HighDensityDanger,
                    HighDensityDangerDim,
                    HighDensityDanger.copy(alpha = 0.3f)
                )
            } else {
                Triple(
                    HighDensityTextMuted,
                    Color(0xFF161618),
                    HighDensityBorder
                )
            }
        }
        StatusIndicatorType.INFO -> Triple(
            HighDensityInfo,
            HighDensityInfoDim,
            HighDensityInfo.copy(alpha = 0.3f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0D0D0F))
            .border(1.dp, HighDensityBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = HighDensityTextPrimary,
                fontFamily = FontFamily.SansSerif
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(badgeBgColor)
                    .border(1.dp, badgeBorderColor, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = value.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = badgeTextColor,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

