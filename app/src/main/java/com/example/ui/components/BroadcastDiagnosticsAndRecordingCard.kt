package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Save
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
import com.example.services.streaming.BroadcastStreamingPipeline
import com.example.services.streaming.BroadcastRecordingManager
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun BroadcastDiagnosticsAndRecordingCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pipeline = remember { BroadcastStreamingPipeline.getInstance(context) }
    val recordingManager = remember { BroadcastRecordingManager.getInstance() }

    val metrics by pipeline.pipelineMetrics.collectAsState()
    val isRecording by recordingManager.isRecording.collectAsState()
    val recordingDuration by recordingManager.recordingDurationSeconds.collectAsState()
    val stationState by com.example.services.station.StationDeskManager.stationState.collectAsState()
    
    val storagePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->

    val granted = permissions.values.all { it }

    if (granted) {
        val result = com.example.services.station.StationDeskManager.startRecordingFromStation(context)

        if (result.isFailure) {
            android.util.Log.e(
                "MVP_RECORDING",
                "Recording start failed",
                result.exceptionOrNull()
            )
        }
    } else {
        android.util.Log.w(
            "MVP_RECORDING",
            "Required permission was not granted"
        )
    }
}

    // Periodically force UI recomposition for relative timestamp updates
    var ticks by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            ticks++
        }
    }

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
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Diagnostics icon",
                        tint = HighDensityOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BROADCAST DIAGNOSTICS & RECORDING (${stationState.resolution.label}${stationState.fps.fps})",
                        color = HighDensityTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "${stationState.currentFps} FPS | ${if (stationState.isHeavy) "HEAVY" else "SMOOTH"}",
                    color = if (stationState.isHeavy) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                // Health Status Badge
                val (healthBg, healthText, healthColor) = when (metrics.healthStatus) {
                    "HEALTHY" -> Triple(Color(0xFF166534), "HEALTHY", Color(0xFF4ADE80))
                    "WARNING" -> Triple(Color(0xFF854D0E), "WARNING", Color(0xFFFACC15))
                    "ERROR" -> Triple(Color(0xFF991B1B), "ERROR", Color(0xFFF87171))
                    else -> Triple(Color(0xFF374151), "DISCONNECTED", Color(0xFF9CA3AF))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(healthBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = healthText,
                        color = healthColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Real-Time Health & Pipeline Metrics Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(HighDensitySurfaceVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: FPS and Bitrates
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("VIDEO FPS / TARGET", color = HighDensityTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${String.format("%.1f", metrics.currentFps)} / ${metrics.targetFps} FPS",
                            color = HighDensityTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("VIDEO BITRATE", color = HighDensityTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${metrics.currentBitrateKbps} Kbps",
                            color = HighDensityTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Row 2: Dropped frames and Audio bitrate
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        val dropped = (metrics.framesComposed - metrics.framesEncoded).coerceAtLeast(0L)
                        Text("DROPPED / ENCODED FRAMES", color = HighDensityTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "$dropped / ${metrics.framesEncoded}",
                            color = if (dropped > 0L) HighDensityDanger else HighDensityTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AUDIO BITRATE", color = HighDensityTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${metrics.audioBitrateKbps} Kbps",
                            color = HighDensityTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Row 3: Live Pipeline Engines Status
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("VIDEO H.264 ENCODER", color = HighDensityTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (metrics.isEncoderRunning) "RUNNING" else "STOPPED",
                            color = if (metrics.isEncoderRunning) Color(0xFF4ADE80) else HighDensityTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AUDIO AAC ENGINE", color = HighDensityTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (metrics.isAudioActive) "RUNNING" else "STOPPED",
                            color = if (metrics.isAudioActive) Color(0xFF4ADE80) else HighDensityTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Row 4: RTMP status & duration
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("RTMP PIPELINE", color = HighDensityTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (metrics.isRtmpConnected) "CONNECTED" else "DISCONNECTED",
                            color = if (metrics.isRtmpConnected) Color(0xFF4ADE80) else HighDensityTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LIVE UPTIME", color = HighDensityTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatDuration(metrics.uptimeSeconds),
                            color = HighDensityTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Relative packet times to satisfy real monitoring requirements
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LAST ENCODED FRAME", color = HighDensityTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatRelativeTime(metrics.lastEncodedFrameTimestamp, ticks),
                            color = HighDensityTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LAST RTMP PACKET SENT", color = HighDensityTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatRelativeTime(metrics.lastRtmpSendTimestamp, ticks),
                            color = HighDensityTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Display active errors
                metrics.lastError?.let { err ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF451A1A))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "ERROR: $err",
                            color = Color(0xFFFCA5A5),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Divider
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HighDensityBorder)
            )

            // Local Recording Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save icon",
                            tint = HighDensityOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LOCAL MP4 BROADCAST RECORDING",
                            color = HighDensityTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Recording Status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isRecording) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = "recording",
                                tint = HighDensityDanger,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "REC // ${formatDuration(recordingDuration)}",
                                color = HighDensityDanger,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                text = "OFFLINE",
                                color = HighDensityTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
   onClick = {
    try {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            storagePermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        } else {
            val result =
                com.example.services.station.StationDeskManager
                    .startRecordingFromStation(context)

            if (result.isFailure) {
                android.util.Log.e(
                    "MVP_RECORDING",
                    "Recording start failed",
                    result.exceptionOrNull()
                )
            }
        }
    } catch (e: Exception) {
        android.util.Log.e(
            "MVP_RECORDING",
            "Recording button crash",
            e
        )
    }
},
                        enabled = !isRecording,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HighDensityOrange,
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF1E1610),
                            disabledContentColor = Color(0xFF523B2A)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "START REC",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = {
                            com.example.services.station.StationDeskManager.stopRecordingFromStation()
                        },
                        enabled = isRecording,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HighDensityDanger,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF241515),
                            disabledContentColor = Color(0xFF5A3535)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "STOP REC",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Display file save status or recording errors
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

private fun formatRelativeTime(timestampMs: Long, trigger: Long): String {
    if (timestampMs == 0L) return "Never"
    val diff = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    if (diff < 1000) return "Just now"
    val secs = diff / 1000
    if (secs < 60) return "${secs}s ago"
    return "${secs / 60}m ago"
}
