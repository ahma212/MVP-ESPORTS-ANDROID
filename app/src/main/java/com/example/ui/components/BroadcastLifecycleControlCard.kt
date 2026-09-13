package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.services.streaming.BroadcastController
import com.example.services.streaming.BroadcastLifecycleState
import com.example.ui.theme.*

@Composable
fun BroadcastLifecycleControlCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val broadcastController = remember { BroadcastController.getInstance(context) }
    
    val state by broadcastController.broadcastState.collectAsState()
    val errorMsg by broadcastController.broadcastError.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HighDensitySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, HighDensityBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "Broadcast Icon",
                        tint = HighDensityOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BROADCAST LIFECYCLE CONTROLLER",
                        color = HighDensityTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Status Badge
                val (bg, txt, col) = when (state) {
                    BroadcastLifecycleState.IDLE -> Triple(Color(0xFF374151), "IDLE", Color(0xFF9CA3AF))
                    BroadcastLifecycleState.READY -> Triple(Color(0xFF1E3A8A), "READY", Color(0xFF60A5FA))
                    BroadcastLifecycleState.LIVE -> Triple(Color(0xFF991B1B), "LIVE", Color(0xFFFCA5A5))
                    BroadcastLifecycleState.PAUSED -> Triple(Color(0xFF854D0E), "PAUSED", Color(0xFFFACC15))
                    BroadcastLifecycleState.COMPLETED -> Triple(Color(0xFF166534), "COMPLETED", Color(0xFF4ADE80))
                    BroadcastLifecycleState.ERROR -> Triple(Color(0xFF991B1B), "ERROR", Color(0xFFF87171))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(bg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = txt,
                        color = col,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            errorMsg?.let {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF451A1A))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "ERROR: $it",
                        color = Color(0xFFFCA5A5),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Prepare/Start
                Button(
                    onClick = {
                        if (state == BroadcastLifecycleState.IDLE || state == BroadcastLifecycleState.COMPLETED) {
                            broadcastController.prepareBroadcast()
                        } else if (state == BroadcastLifecycleState.READY || state == BroadcastLifecycleState.ERROR) {
                            broadcastController.startBroadcast()
                        }
                    },
                    enabled = state == BroadcastLifecycleState.IDLE || state == BroadcastLifecycleState.READY || state == BroadcastLifecycleState.ERROR || state == BroadcastLifecycleState.COMPLETED,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HighDensityOrange, contentColor = Color.Black, disabledContainerColor = Color(0xFF2B2B2B), disabledContentColor = Color.Gray),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (state == BroadcastLifecycleState.IDLE || state == BroadcastLifecycleState.COMPLETED) "PREPARE" else "START",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                    )
                }

                // Pause / Resume
                if (state == BroadcastLifecycleState.PAUSED) {
                    Button(
                        onClick = { broadcastController.resumeBroadcast() },
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA), contentColor = Color.Black),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(text = "RESUME", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    Button(
                        onClick = { broadcastController.pauseBroadcast() },
                        enabled = state == BroadcastLifecycleState.LIVE,
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFACC15), contentColor = Color.Black, disabledContainerColor = Color(0xFF2B2B2B), disabledContentColor = Color.Gray),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(text = "PAUSE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                // Next Match
                Button(
                    onClick = { broadcastController.nextMatch() },
                    enabled = state == BroadcastLifecycleState.LIVE || state == BroadcastLifecycleState.PAUSED,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HighDensitySurfaceVariant, contentColor = Color.White, disabledContainerColor = Color(0xFF2B2B2B), disabledContentColor = Color.Gray),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(text = "NEXT MATCH", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                // End
                Button(
                    onClick = { broadcastController.endBroadcast() },
                    enabled = state != BroadcastLifecycleState.IDLE && state != BroadcastLifecycleState.COMPLETED,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HighDensityDanger, contentColor = Color.White, disabledContainerColor = Color(0xFF2B2B2B), disabledContentColor = Color.Gray),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(text = "END", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
