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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
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
import com.example.ui.theme.HighDensityDanger
import com.example.ui.theme.HighDensityDangerDim
import com.example.ui.theme.HighDensityOrange
import com.example.ui.theme.HighDensitySuccess
import com.example.ui.theme.HighDensitySuccessDim
import com.example.ui.theme.HighDensitySurface
import com.example.ui.theme.HighDensityTextMuted
import com.example.ui.theme.HighDensityTextPrimary
import com.example.ui.theme.HighDensityTextSecondary
import com.example.ui.theme.HighDensityWarning
import com.example.ui.theme.HighDensityWarningDim

@Composable
fun ConfidenceRuleViewer(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(HighDensitySurface)
            .border(1.dp, HighDensityBorder, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = HighDensityOrange,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "CONFIDENCE & ADMIN REVIEW RULE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = HighDensityTextPrimary,
                letterSpacing = 0.5.sp
            )
        }

        // High confidence card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0C1611))
                .border(1.dp, HighDensitySuccess.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONFIDENCE >= 50%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensitySuccess
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(HighDensitySuccessDim)
                            .border(1.dp, HighDensitySuccess.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "AUTO-PROCESSED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = HighDensitySuccess,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    text = "Event immediately updates leaderboard & stream overlay without waiting for admin approval.",
                    fontSize = 10.sp,
                    color = HighDensityTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Low confidence card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1A140B))
                .border(1.dp, HighDensityWarning.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONFIDENCE < 50%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityWarning
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(HighDensityWarningDim)
                            .border(1.dp, HighDensityWarning.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ADMIN REVIEW QUEUE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = HighDensityWarning,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    text = "Routed to low-confidence queue for referee verification ([CONFIRM] / [REJECT]).",
                    fontSize = 10.sp,
                    color = HighDensityTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Future Admin Review UI Blueprint
        Text(
            text = "ADMIN REVIEW SCHEMA & ACTIONS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = HighDensityTextMuted,
            letterSpacing = 0.5.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0A0A0B))
                .border(1.dp, HighDensityBorder, RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Payload: EventType | Killer | Victim | Weapon | Time | Evidence ROI Frame",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(HighDensitySuccessDim)
                            .border(1.dp, HighDensitySuccess.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(text = "[ CONFIRM ]", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = HighDensitySuccess, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(HighDensityDangerDim)
                            .border(1.dp, HighDensityDanger.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(text = "[ REJECT ]", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = HighDensityDanger, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

