package com.example.ui

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.services.overlay.FloatingPointerOverlay
import com.example.services.supabase.SupabaseConnectionState
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.FrameBitmapConverter
import com.google.android.gms.auth.api.signin.GoogleSignIn

@Composable
fun LiveAnalyzerScreen(
    viewModel: LiveAnalyzerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val rois by viewModel.rois.collectAsState()
    val selectedRoi by viewModel.selectedRoi.collectAsState()
    val latestPreviewFrame by viewModel.latestPreviewFrame.collectAsState()
    val latestSampledFrame by viewModel.latestSampledFrame.collectAsState()
    val croppedFrame by viewModel.croppedFrame.collectAsState()
    val adminReviewQueue by viewModel.adminReviewQueue.collectAsState()
    val realDetectionInspectionState by viewModel.realDetectionInspectionState.collectAsState()

    val youtubeSessionState by viewModel.youtubeSessionState.collectAsState()
    val youtubeChannelInfo by viewModel.youtubeChannelInfo.collectAsState()
    val youtubeBroadcastInfo by viewModel.youtubeBroadcastInfo.collectAsState()
    val youtubeError by viewModel.youtubeError.collectAsState()
    val youtubeIsAuthorized by viewModel.youtubeIsAuthorized.collectAsState()

    val previewBitmap = remember(latestPreviewFrame) {
        FrameBitmapConverter.toImageBitmap(latestPreviewFrame)
    }

    val croppedBitmap = remember(croppedFrame) {
        FrameBitmapConverter.toImageBitmap(croppedFrame)
    }

    val context = LocalContext.current
    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }

    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startScreenCaptureWithPermission(result.resultCode, result.data!!)
        } else {
            viewModel.onCapturePermissionDenied()
        }
    }

    var isPointerEnabled by remember { mutableStateOf(com.example.platform.android.ScreenCaptureService.isPointerEnabled) }

    val youtubeConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.retryYouTubeToken(context)
        } else {
            viewModel.onConsentDeclined(result.resultCode)
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        viewModel.handleYouTubeSignInResult(task, context) { consentIntent ->
            youtubeConsentLauncher.launch(consentIntent)
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = MvpNavyBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MvpCardGlass)
                    .border(1.dp, MvpCyanBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "LIVE ANALYSER",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = MvpTextTitle,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Real-time esports tournament broadcast & telemetry engine",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = MvpTextSubtitle
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (uiState.isCapturingActive) MvpSuccessDim else MvpCyanDim)
                        .border(1.dp, if (uiState.isCapturingActive) MvpSuccess else MvpCyanPrimary, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (uiState.isCapturingActive) "LIVE" else "READY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = if (uiState.isCapturingActive) MvpSuccess else MvpCyanPrimary
                    )
                }
            }

            // Arena Tabs Navigation (STATION DESK / CAPTURE & LIVE / AI & ROI)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MvpCardGlassVariant)
                    .border(1.dp, MvpBorderSubtle, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabTitles = listOf("STATION DESK", "CAPTURE & LIVE", "AI & ROI")
                tabTitles.forEachIndexed { index, title ->
                    val isSelected = uiState.activeTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MvpBlueButton else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) MvpCyanPrimary else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.setTab(index) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = if (isSelected) Color.White else MvpTextSubtitle
                        )
                    }
                }
            }

            when (uiState.activeTab) {
                0 -> {
                    // TAB 0: MASTER STATION CONTROL ROOM DESK
                    SupabaseConnectCard(viewModel)

                    if (!uiState.isCapturingActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MvpCardGlassVariant)
                                .border(1.dp, MvpCyanBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Game Screen Capture Inactive",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    color = MvpCyanPrimary
                                )
                                Text(
                                    text = "Start capture to feed gameplay into Station program desk",
                                    fontSize = 11.sp,
                                    color = MvpTextSubtitle,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            Button(
                                onClick = {
                                    if (mediaProjectionManager != null) {
                                        captureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                    } else {
                                        viewModel.startScreenCapture()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MvpBlueButton),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("START CAPTURE", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, color = Color.White)
                            }
                        }
                    }

                    StationControlRoomCard(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                1 -> {
                    // TAB 1: CAPTURE & LIVE STREAMING
                    SupabaseConnectCard(viewModel)

                    val tableControls = com.example.services.streaming.StandingTableControlsManager.controlsState.collectAsState().value

                    // Screen Capture Status Card
                    StatusBadge(
                        label = "Screen Capture",
                        value = uiState.screenCaptureStatusText
                    )

                    // Capture Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (mediaProjectionManager != null) {
                                    captureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                } else {
                                    viewModel.startScreenCapture()
                                }
                            },
                            enabled = !uiState.isCapturingActive,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MvpBlueButton,
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF0F1A30),
                                disabledContentColor = MvpTextMuted
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (!uiState.isCapturingActive) Color.White else MvpTextMuted
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "START CAPTURE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.stopScreenCapture() },
                            enabled = uiState.isCapturingActive,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF0E1B33),
                                contentColor = MvpDanger,
                                disabledContainerColor = MvpCardGlass,
                                disabledContentColor = MvpTextMuted
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (uiState.isCapturingActive) MvpDanger else MvpBorderSubtle
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "STOP CAPTURE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    // Floating Pointer Overlay Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MvpCardGlassVariant)
                            .border(1.dp, MvpBorderSubtle, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(
                                text = "Floating Pointer Overlay",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = MvpTextTitle
                            )
                            Text(
                                text = "Show controls & live stats over games. Toggle anytime.",
                                fontSize = 11.sp,
                                color = MvpTextSubtitle,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        Button(
                            onClick = {
                                if (isPointerEnabled) {
                                    isPointerEnabled = false
                                    com.example.platform.android.ScreenCaptureService.setPointerEnabled(context, false)
                                } else {
                                    if (!FloatingPointerOverlay.checkOverlayPermission(context)) {
                                        FloatingPointerOverlay.requestOverlayPermission(context)
                                    } else {
                                        isPointerEnabled = true
                                        com.example.platform.android.ScreenCaptureService.setPointerEnabled(context, true)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPointerEnabled) MvpSuccess else MvpBlueButton,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isPointerEnabled) "POINTER ON" else "POINTER OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

                    // YouTube Live Studio Card
                    YouTubeLiveStudioCard(
                        sessionState = youtubeSessionState,
                        channelInfo = youtubeChannelInfo,
                        broadcastInfo = youtubeBroadcastInfo,
                        errorMessage = youtubeError,
                        isAuthorized = youtubeIsAuthorized,
                        onAuthorize = {
                            viewModel.clearYouTubeError()
                            googleSignInLauncher.launch(viewModel.getYouTubeSignInIntent(context))
                        },
                        onStartLive = { title, desc, privacy -> viewModel.startYouTubeLive(title, desc, privacy) },
                        onStopLive = { viewModel.stopYouTubeLive() },
                        onDisconnect = { viewModel.disconnectYouTube() }
                    )

                    // Broadcast Recording & Stream Diagnostics Card
                    BroadcastDiagnosticsAndRecordingCard()

                    // Broadcast Lifecycle Controller
                    BroadcastLifecycleControlCard()

                    // Scene Engine, Audio Mixer, and Ticker Studio Card
                    val sceneState = com.example.services.scene.EsportsSceneEngine.sceneState.collectAsState().value
                    val audioState = com.example.services.audio.AudioMixerManager.mixerState.collectAsState().value

                    BroadcastSceneStudioCard(
                        sceneState = sceneState,
                        audioState = audioState
                    )

                    StandingTableControlsCard(
                        state = tableControls
                    )
                }

                else -> {
                    // TAB 2: AI INSPECTOR & ROI
                    RoiEditorCard(
                        rois = rois,
                        selectedRoi = selectedRoi,
                        frameWidth = latestPreviewFrame?.width ?: latestSampledFrame?.width ?: 1080,
                        frameHeight = latestPreviewFrame?.height ?: latestSampledFrame?.height ?: 1920,
                        frameBitmap = previewBitmap,
                        croppedBitmap = croppedBitmap,
                        framesReceived = uiState.framesReceived,
                        framesSampled = uiState.framesSampled,
                        captureFps = uiState.captureFps,
                        previewFps = uiState.previewFps,
                        analysisFps = uiState.analysisFps,
                        targetSampleFps = uiState.targetSampleFps,
                        isAiAssistEnabled = uiState.isAiAssistEnabled,
                        isAiAvailable = viewModel.isAiAvailable,
                        aiStatusText = uiState.aiDetectionStatusText,
                        onTargetSampleFpsChanged = { viewModel.setTargetSampleFps(it) },
                        onSelectRoi = { viewModel.selectRoi(it) },
                        onUpdateRoi = { viewModel.updateRoi(it) },
                        onToggleRoiEnabled = { viewModel.toggleRoiEnabled(it) },
                        onDeleteRoi = { viewModel.deleteRoi(it) },
                        onResetDefaults = { viewModel.resetRoiDefaults() },
                        onAddNewRoi = { viewModel.addNewRoi(it) },
                        onToggleAiAssist = { viewModel.toggleAiAssist() }
                    )

                    RealDetectionInspectorCard(
                        inspectionState = realDetectionInspectionState,
                        fullFrameBitmap = previewBitmap,
                        croppedRoiBitmap = croppedBitmap
                    )

                    ConfidenceRuleViewer()

                    AdminReviewCard(
                        pendingEvents = adminReviewQueue,
                        onConfirmEvent = { viewModel.confirmAdminEvent(it) },
                        onRejectEvent = { id, reason -> viewModel.rejectAdminEvent(id, reason) }
                    )

                    // Infrastructure Status Badges
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MvpCardGlassVariant)
                            .border(1.dp, MvpBorderSubtle, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatusBadge(
                            label = "Input Status",
                            value = uiState.inputStatusText
                        )

                        StatusBadge(
                            label = "AI Detection",
                            value = uiState.aiDetectionStatusText
                        )

                        StatusBadge(
                            label = "Supabase",
                            value = uiState.supabaseStatusText
                        )

                        StatusBadge(
                            label = "YouTube",
                            value = uiState.youtubeStatusText
                        )
                    }

                    ArchitecturePipelineViewer()
                }
            }
        }
    }
}

@Composable
fun SupabaseConnectCard(
    viewModel: LiveAnalyzerViewModel,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.supabaseService.connectionState.collectAsState()
    val teams by viewModel.supabaseService.liveLeaderboardStream.collectAsState(initial = emptyList())
    val teamsCount by viewModel.supabaseService.teamsCount.collectAsState()
    val playersCount by viewModel.supabaseService.playersCount.collectAsState()
    val currentSession by viewModel.supabaseService.currentSession.collectAsState()

    val displayTeamsCount = if (teamsCount > 0) teamsCount else teams.size

    val statusText = when (val state = connectionState) {
        is SupabaseConnectionState.NotConnected -> "Not connected"
        is SupabaseConnectionState.Connecting -> "Connecting..."
        is SupabaseConnectionState.Connected -> "Connected"
        is SupabaseConnectionState.SyncError -> "Error: ${state.message}"
    }

    val statusColor = when (connectionState) {
        is SupabaseConnectionState.Connected -> MvpSuccess
        is SupabaseConnectionState.Connecting -> MvpCyanPrimary
        is SupabaseConnectionState.SyncError -> MvpDanger
        is SupabaseConnectionState.NotConnected -> MvpTextMuted
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MvpCardGlass)
            .border(1.dp, MvpCyanBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = "Supabase Live Scoring",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = MvpTextTitle
                )
            }

            Text(
                text = statusText,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MvpCardGlassVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Teams: $displayTeamsCount",
                color = MvpTextTitle,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Players: $playersCount",
                color = MvpTextTitle,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold
            )
            if (connectionState is SupabaseConnectionState.Connected) {
                val mapName = currentSession?.effectiveMap ?: "Live"
                Text(
                    text = "Map: $mapName",
                    color = MvpTextSubtitle,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            } else if (connectionState !is SupabaseConnectionState.Connecting) {
                Button(
                    onClick = { viewModel.connectSupabase() },
                    colors = ButtonDefaults.buttonColors(containerColor = MvpBlueButton),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("RETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                }
            }
        }

        if (connectionState is SupabaseConnectionState.Connected && currentSession != null) {
            val session = currentSession!!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Session: ${session.id.take(8)}...",
                    color = MvpTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif
                )
                session.tournamentTitle?.let { title ->
                    Text(
                        text = title,
                        color = MvpTextSubtitle,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
