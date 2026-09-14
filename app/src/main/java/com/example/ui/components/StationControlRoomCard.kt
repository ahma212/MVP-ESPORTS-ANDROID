package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.services.audio.AudioMixerManager
import com.example.core.model.TeamLiveState
import com.example.services.audio.LocalMusicPlayerManager
import com.example.services.composition.ComposedBroadcastFrame
import com.example.services.station.*
import com.example.services.streaming.LiveSessionState
import com.example.services.supabase.SupabaseConnectionState
import com.example.ui.LiveAnalyzerViewModel
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun StationControlRoomCard(
    viewModel: LiveAnalyzerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stationState by viewModel.stationState.collectAsState()
    val composedFrame by viewModel.composedBroadcastFrame.collectAsState()
    val supabaseConnState by viewModel.supabaseService.connectionState.collectAsState()
    val supabaseSession by viewModel.supabaseService.currentSession.collectAsState()
    val supabaseTeams by viewModel.supabaseService.liveLeaderboardStream.collectAsState()
    val youtubeState by viewModel.youtubeSessionState.collectAsState()
    val youtubeBroadcastInfo by viewModel.youtubeBroadcastInfo.collectAsState()
    val audioState by AudioMixerManager.mixerState.collectAsState()
    val musicTrackInfo by LocalMusicPlayerManager.musicState.collectAsState()

    val videoFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.startVideoFileAnalysis(uri)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MvpCardGlass)
            .border(1.dp, MvpCyanBorder, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Top Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (stationState.isRecording || stationState.isLiveActive) MvpDanger else MvpSuccess)
                    )
                    Text(
                        text = "Station Control Room",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = MvpTextTitle
                    )
                }
                Text(
                    text = "Independent broadcast feed & recording master",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = MvpTextSubtitle
                )
            }

            // Smoothness mode indicator pill
            Surface(
                color = if (stationState.smoothnessMode == SmoothnessMode.PERFORMANCE) MvpCyanDim else Color(0x1A0284C7),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (stationState.smoothnessMode == SmoothnessMode.PERFORMANCE) MvpCyanPrimary else MvpBlueButton
                )
            ) {
                Text(
                    text = "${stationState.smoothnessMode.label.uppercase()} MODE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = if (stationState.smoothnessMode == SmoothnessMode.PERFORMANCE) MvpCyanPrimary else MvpBlueAccent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // --- 2. Live Program Preview (YouTube Player Style) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF000000))
                .border(1.dp, MvpCyanBorder, RoundedCornerShape(12.dp))
        ) {
            // Thin top bar above the player: LIVE / REC dot + short title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B1322))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (stationState.isRecording || stationState.isLiveActive) MvpDanger else MvpSuccess)
                    )
                    Text(
                        text = if (stationState.isRecording) "REC • PROGRAM FEED" else if (stationState.isLiveActive) "LIVE • PROGRAM FEED" else "LIVE PROGRAM PREVIEW",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = Color.White
                    )
                }

                if (stationState.isRecording) {
                    Text(
                        text = "%02d:%02d".format(
                            stationState.recordingDurationSeconds / 60,
                            stationState.recordingDurationSeconds % 60
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = MvpDanger
                    )
                }
            }

            // 16:9 Video Canvas (fills area with ContentScale.Crop, no watermark or text across picture)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF070B14)),
                contentAlignment = Alignment.Center
            ) {
                val programImageBitmap = remember(composedFrame) {
                    com.example.ui.util.FrameBitmapConverter.toStationProgramImageBitmap(composedFrame)
                }
                if (programImageBitmap != null) {
                    Image(
                        bitmap = programImageBitmap,
                        contentDescription = "Station Program Output",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MvpCyanPrimary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Awaiting video frames...",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            color = MvpTextMuted
                        )
                    }
                }
            }

            // Thin bottom bar under the player: resolution / fps only
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B1322))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${composedFrame?.width ?: stationState.resolution.width}x${composedFrame?.height ?: stationState.resolution.height} • ${stationState.fps.fps} FPS",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    color = MvpTextSubtitle
                )

                Text(
                    text = "${composedFrame?.compositionTimeMs ?: 0}ms latency",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = MvpTextMuted
                )
            }
            // --- LIVE BROADCAST STANDINGS ---
// Same Supabase leaderboard data used by the broadcast compositor.
// This is display-only: Supabase remains the source of truth.
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF070B14))
        .border(
            1.dp,
            MvpCyanBorder,
            RoundedCornerShape(12.dp)
        )
        .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "LIVE BROADCAST STANDINGS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = MvpTextTitle
            )

            Text(
                text = when (supabaseConnState) {
                    is SupabaseConnectionState.Connected ->
                        "Supabase Realtime • synced"

                    is SupabaseConnectionState.Connecting ->
                        "Supabase Realtime • connecting"

                    is SupabaseConnectionState.SyncError ->
                        "Supabase Realtime • sync error"

                    is SupabaseConnectionState.NotConnected ->
                        "Supabase Realtime • offline"
                },
                fontSize = 9.sp,
                fontFamily = FontFamily.SansSerif,
                color = when (supabaseConnState) {
                    is SupabaseConnectionState.Connected ->
                        MvpSuccess

                    else ->
                        MvpTextMuted
                }
            )
        }

        Text(
            text = "${supabaseTeams.size} TEAMS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = MvpCyanPrimary
        )
    }

    val standingTeams =
        remember(supabaseTeams) {
            supabaseTeams.map { entry ->
                com.example.core.model.TeamLiveState(
                    teamNumber = entry.slotNumber,
                    teamName = entry.teamName,
                    currentMatchKills = entry.killPoints,
                    currentMatchPoints = entry.totalPoints,
                    currentAlivePlayers = entry.alivePlayers,
                    rank = entry.rank,
                    players = emptyList(),
                    isEliminated = entry.alivePlayers <= 0
                )
            }
        }

    EsportsStandingTable(
        teams = standingTeams,
        modifier = Modifier.fillMaxWidth(),
        isStreamOverlay = true
    )
}
        }

        // Video Source Selector & Meme Controller inside Station Desk
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MvpCardGlassVariant)
                .border(1.dp, MvpBorderSubtle, RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Video Input Source",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    color = MvpTextTitle
                )
                val uiState by viewModel.uiState.collectAsState()
                Text(
                    text = if (uiState.isVideoFileSource) "Playing local video file" else "Live Screen Capture",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = MvpTextSubtitle
                )
            }

            val uiState by viewModel.uiState.collectAsState()

            Button(
                onClick = {
                    if (uiState.isVideoFileSource) {
                        viewModel.switchToLiveScreenCapture()
                    } else {
                        videoFilePickerLauncher.launch("video/*")
                    }
                },
                modifier = Modifier.height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isVideoFileSource) MvpBlueButton else MvpCardGlass,
                    contentColor = Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MvpCyanPrimary),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isVideoFileSource) Icons.Default.Refresh else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = if (uiState.isVideoFileSource) "LIVE SCREEN" else "SELECT VIDEO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        // Meme / Video Overlay Picker
        val tableControls by com.example.services.streaming.StandingTableControlsManager.controlsState.collectAsState()
        val localContext = LocalContext.current
        val memePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                com.example.platform.android.VideoUriValidator.processAndSelectVideoUri(localContext, uri)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MvpCardGlassVariant)
                .border(1.dp, MvpBorderSubtle, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Meme Video Overlay",
                    color = MvpTextTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = if (!tableControls.tickerLoopVideoUri.isNullOrBlank()) "Video meme active" else "No video meme selected",
                    color = if (!tableControls.tickerLoopVideoUri.isNullOrBlank()) MvpCyanPrimary else MvpTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        try {
                            memePickerLauncher.launch(arrayOf("video/*"))
                        } catch (_: Exception) {
                            com.example.platform.android.LocalVideoPickerActivity.launch(localContext)
                        }
                    },
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF0E1B33),
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MvpCyanPrimary),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "CHOOSE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = Color.White
                    )
                }
                Button(
                    onClick = {
                        if (tableControls.tickerLoopVideoUri.isNullOrBlank()) {
                            try {
                                memePickerLauncher.launch(arrayOf("video/*"))
                            } catch (_: Exception) {
                                com.example.platform.android.LocalVideoPickerActivity.launch(localContext)
                            }
                        } else {
                            com.example.services.streaming.StandingTableControlsManager.setTickerLoopVideo(
                                tableControls.tickerLoopVideoUri,
                                !tableControls.tickerLoopVideoEnabled
                            )
                        }
                    },
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tableControls.tickerLoopVideoEnabled && !tableControls.tickerLoopVideoUri.isNullOrBlank()) MvpSuccess else MvpDanger
                    ),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (tableControls.tickerLoopVideoEnabled && !tableControls.tickerLoopVideoUri.isNullOrBlank()) "ON" else "OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = Color.White
                    )
                }
            }
        }

        // --- 3. Supabase Sync Status Ribbon (Non-writable scoring source) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MvpCardGlassVariant)
                .border(1.dp, MvpBorderSubtle, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Supabase Live Scoring",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    color = MvpTextMuted
                )
                Text(
                    text = when (supabaseConnState) {
                        is SupabaseConnectionState.Connected -> "Connected • ${supabaseSession?.tournamentTitle ?: "PUBG Live"} (${supabaseSession?.effectiveMap ?: "Erangel"})"
                        is SupabaseConnectionState.Connecting -> "Connecting to Supabase..."
                        is SupabaseConnectionState.SyncError -> "Connection error"
                        is SupabaseConnectionState.NotConnected -> "Not connected"
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    color = if (supabaseConnState is SupabaseConnectionState.Connected) MvpSuccess else MvpWarning
                )
            }

            Surface(
                color = MvpCardGlass,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MvpCyanBorder)
            ) {
                Text(
                    text = "${supabaseTeams.size} TEAMS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = MvpCyanPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // --- 4. Master Output: LOCAL RECORD ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (stationState.isRecording) MvpDangerDim else MvpCardGlassVariant)
                .border(
                    1.dp,
                    if (stationState.isRecording) MvpDanger else MvpBorderSubtle,
                    RoundedCornerShape(12.dp)
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Local MP4 Recording",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = if (stationState.isRecording) MvpDanger else MvpTextTitle
                    )
                    Text(
                        text = if (stationState.isRecording) "Recording to Movies/MVP-Esports" else "Saves composed feed to phone gallery",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = MvpTextSubtitle
                    )
                }

                if (stationState.isRecording) {
                    Text(
                        text = "%02d:%02d".format(
                            stationState.recordingDurationSeconds / 60,
                            stationState.recordingDurationSeconds % 60
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        color = MvpDanger
                    )
                }
            }

            Button(
                onClick = {
                    if (stationState.isRecording) {
                        viewModel.stopStationRecording()
                    } else {
                        viewModel.startStationRecording(context)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (stationState.isRecording) MvpDanger else MvpBlueButton,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = if (stationState.isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (stationState.isRecording) "STOP RECORDING" else "START RECORDING",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        // --- 5. Station Settings: Resolution, FPS & Smoothness ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MvpCardGlassVariant)
                .border(1.dp, MvpBorderSubtle, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Stream Format & Performance",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = MvpTextTitle
            )

            // Smoothness Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(SmoothnessMode.PERFORMANCE, SmoothnessMode.QUALITY).forEach { mode ->
                    val isSelected = stationState.smoothnessMode == mode
                    Button(
                        onClick = { viewModel.setStationSmoothnessMode(mode) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MvpBlueButton else MvpCardGlass,
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MvpCyanPrimary else MvpBorderSubtle),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text(
                            text = "${mode.label} (${mode.previewRes})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            // Resolution & FPS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Resolution:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    color = MvpTextSubtitle
                )
              listOf(
                    StationResolution.RES_360P,
                    StationResolution.RES_480P,
                    StationResolution.RES_720P,
                    StationResolution.RES_1080P
                ).forEach { res ->
                    val isSelected = stationState.resolution == res
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setStationResolution(res) },
                        label = { Text(res.label, fontSize = 11.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MvpBlueButton,
                            selectedLabelColor = Color.White,
                            containerColor = MvpCardGlass,
                            labelColor = MvpTextSubtitle
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) MvpCyanPrimary else MvpBorderSubtle
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "FPS:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    color = MvpTextSubtitle
                )
                listOf(
    StationFps.FPS_24,
    StationFps.FPS_30,
    StationFps.FPS_60
).forEach { fps ->
                    val isSelected = stationState.fps == fps
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setStationFps(fps) },
                        label = { Text(fps.label, fontSize = 11.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MvpBlueButton,
                            selectedLabelColor = Color.White,
                            containerColor = MvpCardGlass,
                            labelColor = MvpTextSubtitle
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) MvpCyanPrimary else MvpBorderSubtle
                        )
                    )
                }
            }
        }

        // --- 6. Color Desk ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MvpCardGlassVariant)
                .border(1.dp, MvpBorderSubtle, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Color Grading & Filter Presets",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = MvpTextTitle
                )
                Text(
                    text = "Reset",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = MvpCyanPrimary,
                    modifier = Modifier.clickable { viewModel.resetStationColorDesk() }
                )
            }

            // Presets row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(ColorPreset.NONE, ColorPreset.WARM, ColorPreset.COOL, ColorPreset.VINTAGE).forEach { preset ->
                    val isSelected = stationState.colorDesk.preset == preset
                    Button(
                        onClick = { viewModel.setStationColorPreset(preset) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MvpBlueButton else MvpCardGlass,
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MvpCyanPrimary else MvpBorderSubtle),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = preset.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            // Sliders with Cyan Accents
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Brightness", fontSize = 11.sp, fontFamily = FontFamily.SansSerif, color = MvpTextSubtitle)
                    Text("%.2f".format(stationState.colorDesk.brightness), fontSize = 11.sp, fontFamily = FontFamily.SansSerif, color = Color.White)
                }
                Slider(
                    value = stationState.colorDesk.brightness,
                    onValueChange = { viewModel.setStationBrightness(it) },
                    valueRange = -1.0f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = MvpCyanPrimary,
                        activeTrackColor = MvpCyanPrimary,
                        inactiveTrackColor = MvpBorderSubtle
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Contrast", fontSize = 11.sp, fontFamily = FontFamily.SansSerif, color = MvpTextSubtitle)
                    Text("%.2f".format(stationState.colorDesk.contrast), fontSize = 11.sp, fontFamily = FontFamily.SansSerif, color = Color.White)
                }
                Slider(
                    value = stationState.colorDesk.contrast,
                    onValueChange = { viewModel.setStationContrast(it) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = MvpCyanPrimary,
                        activeTrackColor = MvpCyanPrimary,
                        inactiveTrackColor = MvpBorderSubtle
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saturation", fontSize = 11.sp, fontFamily = FontFamily.SansSerif, color = MvpTextSubtitle)
                    Text("%.2f".format(stationState.colorDesk.saturation), fontSize = 11.sp, fontFamily = FontFamily.SansSerif, color = Color.White)
                }
                Slider(
                    value = stationState.colorDesk.saturation,
                    onValueChange = { viewModel.setStationSaturation(it) },
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = MvpCyanPrimary,
                        activeTrackColor = MvpCyanPrimary,
                        inactiveTrackColor = MvpBorderSubtle
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        // --- 7. Audio Desk Quick Channels ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MvpCardGlassVariant)
                .border(1.dp, MvpBorderSubtle, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Audio Mixer",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = MvpTextTitle
            )

            // Game Audio Channel
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Game",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    color = if (audioState.gameMuted) MvpDanger else Color.White,
                    modifier = Modifier.width(42.dp)
                )
                Slider(
                    value = audioState.gameVolume,
                    onValueChange = { AudioMixerManager.setGameVolume(it) },
                    modifier = Modifier.weight(1f).height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = MvpCyanPrimary, activeTrackColor = MvpCyanPrimary, inactiveTrackColor = MvpBorderSubtle)
                )
                Text(
                    text = if (audioState.gameMuted) "MUTED" else "${(audioState.gameVolume * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    color = if (audioState.gameMuted) MvpDanger else MvpTextSubtitle,
                    modifier = Modifier.width(46.dp).clickable { AudioMixerManager.toggleGameMute() }
                )
            }

            // Voice Mic Channel
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Mic",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    color = if (audioState.micMuted) MvpDanger else Color.White,
                    modifier = Modifier.width(42.dp)
                )
                Slider(
                    value = audioState.micVolume,
                    onValueChange = { AudioMixerManager.setMicVolume(it) },
                    modifier = Modifier.weight(1f).height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = MvpCyanPrimary, activeTrackColor = MvpCyanPrimary, inactiveTrackColor = MvpBorderSubtle)
                )
                Text(
                    text = if (audioState.micMuted) "MUTED" else "${(audioState.micVolume * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    color = if (audioState.micMuted) MvpDanger else MvpTextSubtitle,
                    modifier = Modifier.width(46.dp).clickable { AudioMixerManager.toggleMicMute() }
                )
            }

            // Music Channel
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Music",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    color = if (audioState.musicMuted) MvpDanger else Color.White,
                    modifier = Modifier.width(42.dp)
                )
                Slider(
                    value = audioState.musicVolume,
                    onValueChange = { AudioMixerManager.setMusicVolume(it) },
                    modifier = Modifier.weight(1f).height(24.dp),
                    colors = SliderDefaults.colors(thumbColor = MvpCyanPrimary, activeTrackColor = MvpCyanPrimary, inactiveTrackColor = MvpBorderSubtle)
                )
                Text(
                    text = if (audioState.musicMuted) "MUTED" else "${(audioState.musicVolume * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    color = if (audioState.musicMuted) MvpDanger else MvpTextSubtitle,
                    modifier = Modifier.width(46.dp).clickable { AudioMixerManager.toggleMusicMute() }
                )
            }
        }
    }
}
