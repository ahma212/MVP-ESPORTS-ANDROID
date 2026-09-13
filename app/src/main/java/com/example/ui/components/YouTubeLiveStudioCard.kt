package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.services.overlay.FloatingPointerOverlay
import com.example.services.streaming.LiveSessionState
import com.example.services.streaming.YouTubeChannelInfo
import com.example.services.streaming.YouTubeLiveBroadcastInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeLiveStudioCard(
    sessionState: LiveSessionState,
    channelInfo: YouTubeChannelInfo?,
    broadcastInfo: YouTubeLiveBroadcastInfo?,
    errorMessage: String?,
    isAuthorized: Boolean,
    onAuthorize: () -> Unit,
    onStartLive: (title: String, description: String, privacy: String) -> Unit,
    onStopLive: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var broadcastTitle by remember { mutableStateOf("MVP ESPORTS PK LIVE TOURNAMENT") }
    var broadcastDesc by remember { mutableStateOf("Live PUBG Mobile esports action analyzed by MVP ESPORTS.") }
    var privacyStatus by remember { mutableStateOf("public") }

    var hasOverlayPermission by remember { mutableStateOf(FloatingPointerOverlay.checkOverlayPermission(context)) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = "YouTube Live Studio",
                        tint = Color(0xFFFF0000),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "YOUTUBE LIVE STUDIO",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status Badge
                val (badgeBg, badgeText, badgeColor) = when (sessionState) {
                    LiveSessionState.IDLE -> Triple(Color(0xFF334155), "NOT CONNECTED", Color.LightGray)
                    LiveSessionState.AUTHORIZING -> Triple(Color(0xFF0284C7), "AUTHORIZING", Color.White)
                    LiveSessionState.READY -> Triple(Color(0xFF166534), "CONNECTED", Color(0xFF4ADE80))
                    LiveSessionState.STARTING -> Triple(Color(0xFFCA8A04), "STARTING...", Color.White)
                    LiveSessionState.LIVE -> Triple(Color(0xFFDC2626), "● LIVE", Color.White)
                    LiveSessionState.RECONNECTING -> Triple(Color(0xFFEA580C), "RECONNECTING", Color.White)
                    LiveSessionState.STOPPING -> Triple(Color(0xFF475569), "STOPPING", Color.White)
                    LiveSessionState.STOPPED -> Triple(Color(0xFF334155), "STOPPED", Color.LightGray)
                    LiveSessionState.ERROR -> Triple(Color(0xFF991B1B), "ERROR", Color(0xFFFCA5A5))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Error display if any
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF451A1A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(18.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Channel Info when connected
            if (isAuthorized && channelInfo != null) {
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Connected Channel",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                            Text(
                                text = channelInfo.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "ID: ${channelInfo.channelId}",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }

                        TextButton(onClick = onDisconnect) {
                            Text("DISCONNECT", color = Color(0xFFEF4444), fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Broadcast Controls
                OutlinedTextField(
                    value = broadcastTitle,
                    onValueChange = { broadcastTitle = it },
                    label = { Text("Live Broadcast Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedLabelColor = Color(0xFF3B82F6),
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sessionState != LiveSessionState.LIVE) {
                        Button(
                            onClick = {
                                onStartLive(broadcastTitle, broadcastDesc, privacyStatus)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("START LIVE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onStopLive,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("STOP LIVE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (broadcastInfo != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF0284C7).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Broadcast ID: ${broadcastInfo.broadcastId}", color = Color.White, fontSize = 11.sp)
                            if (!broadcastInfo.rtmpIngestUrl.isNullOrBlank()) {
                                Text("RTMP Stream: ${broadcastInfo.rtmpIngestUrl}", color = Color(0xFF38BDF8), fontSize = 10.sp)
                            }
                            Text("Lifecycle State: ${broadcastInfo.lifeCycleStatus}", color = Color.LightGray, fontSize = 10.sp)
                        }
                    }
                }

            } else {
                // Connect Button
                Button(
                    onClick = onAuthorize,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CastConnected, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CONNECT YOUTUBE ACCOUNT", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
