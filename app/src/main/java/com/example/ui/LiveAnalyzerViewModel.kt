package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.DetectedEvent
import com.example.core.model.DetectionFrame
import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.FrameAnalysisInput
import com.example.core.model.LeaderboardEntry
import com.example.core.model.RoiRegion
import com.example.platform.PlatformDetector
import com.example.platform.PlatformType
import com.example.platform.VideoInputFactory
import com.example.platform.android.AndroidScreenCaptureService
import com.example.services.analysis.FrameSampler
import com.example.services.analysis.RoiCropPipeline
import com.example.services.detection.IDetectionResult
import com.example.services.detection.IDetectionService
import com.example.services.detection.PUBGDetectionService
import com.example.ui.components.RealDetectionInspectionState
import com.example.services.event.EventProcessor
import com.example.services.event.IEventProcessingService
import com.example.services.roi.IRoiConfigurationService
import com.example.services.roi.InMemoryRoiConfigurationService
import com.example.services.supabase.ISupabaseService
import com.example.services.supabase.SupabaseService
import com.example.services.video.IVideoInputService
import com.example.services.video.VideoCaptureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.example.platform.android.ScreenCaptureService
import com.example.services.streaming.IYouTubeLiveService
import com.example.services.streaming.LiveSessionState
import com.example.services.streaming.YouTubeChannelInfo
import com.example.services.streaming.YouTubeLiveBroadcastInfo
import com.example.services.streaming.YouTubeLiveService

import android.net.Uri
import com.example.platform.android.AndroidVideoFileInputService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext


/**
 * UI State for the Live Analyzer main screen with Phase 4B Frame Analysis & ROI Telemetry.
 */
data class LiveAnalyzerUiState(
    val platformType: PlatformType = PlatformDetector.getCurrentPlatform(),
    val inputStatusText: String = "Not connected",
    val videoSourceText: String = "Not connected",
    val screenCaptureStatusText: String = "OFF",
    val isCapturingActive: Boolean = false,
    val isVideoFileSource: Boolean = false,
    val selectedVideoFileName: String? = null,
    val isAiAssistEnabled: Boolean = false,
    val aiDetectionStatusText: String = "OFF (OCR only)",
    val supabaseStatusText: String = "Not connected",
    val youtubeStatusText: String = "Not connected",
    val framesCaptured: Long = 0L,
    val captureFps: Int = 0,
    val previewFps: Double = 0.0,
    val resolutionText: String = "None",
    val activeTab: Int = 0,
    val framesReceived: Long = 0L,
    val framesSampled: Long = 0L,
    val analysisFps: Double = 0.0,
    val targetSampleFps: Double = 5.0
)

class LiveAnalyzerViewModel(application: Application) : AndroidViewModel(application) {

    private val liveScreenCaptureService: IVideoInputService = VideoInputFactory.createVideoInputService(application)
    private var videoFileInputService: AndroidVideoFileInputService? = null
    var videoInputService: IVideoInputService = liveScreenCaptureService
        private set

    val detectionService: IDetectionService = PUBGDetectionService.getInstance()
    val supabaseService: ISupabaseService = SupabaseService()
    val eventService: IEventProcessingService = EventProcessor.getInstance(supabaseService)

    // Phase 5A YouTube Live Engine Service
    val youtubeLiveService: IYouTubeLiveService = YouTubeLiveService.getInstance(application)
    val youtubeSessionState: StateFlow<LiveSessionState> = youtubeLiveService.sessionState
    val youtubeChannelInfo: StateFlow<YouTubeChannelInfo?> = youtubeLiveService.channelInfo
    val youtubeBroadcastInfo: StateFlow<YouTubeLiveBroadcastInfo?> = youtubeLiveService.currentBroadcast
    private val _authError = MutableStateFlow<String?>(null)
    val youtubeError: StateFlow<String?> = combine(
        youtubeLiveService.errorMessage,
        _authError
    ) { serviceErr, authErr ->
        authErr ?: serviceErr
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val youtubeIsAuthorized: StateFlow<Boolean> = youtubeLiveService.isAuthorized

    val broadcastCompositor: com.example.services.composition.IBroadcastCompositor = com.example.services.composition.BroadcastVideoCompositor.getInstance()
    val composedBroadcastFrame: StateFlow<com.example.services.composition.ComposedBroadcastFrame?> = broadcastCompositor.latestComposedFrame

    val supabaseTeamsProvider: () -> List<com.example.core.model.TeamLiveState> = {
        supabaseService.liveLeaderboardStream.value.map { entry ->
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

    val broadcastPipeline: com.example.services.streaming.BroadcastStreamingPipeline =
        com.example.services.streaming.BroadcastStreamingPipeline.getInstance(application)
    val pipelineState: StateFlow<com.example.services.streaming.StreamingState> = broadcastPipeline.streamingState


    val broadcastPipelineMetrics: StateFlow<com.example.services.streaming.BroadcastPipelineMetrics> =
        broadcastPipeline.pipelineMetrics

    // Phase 4B Services
    val frameSampler: FrameSampler = FrameSampler(initialTargetFps = 5.0, coroutineScope = viewModelScope)
    val roiService: IRoiConfigurationService = InMemoryRoiConfigurationService.getInstance()

    // Phase 4C Services & Queues
    val adminReviewQueue: StateFlow<List<EsportsDetectedEvent>> = eventService.adminReviewQueue

    val isAiAssistEnabled: StateFlow<Boolean> = com.example.services.detection.pubg.PUBGVisualAIAssistant.getInstance().isAiAssistEnabled
    val isAiAvailable: Boolean
        get() = com.example.services.detection.pubg.PUBGVisualAIAssistant.getInstance().isAvailable

    fun setAiAssistEnabled(enabled: Boolean) {
        com.example.services.detection.pubg.PUBGVisualAIAssistant.getInstance().setAiAssistEnabled(enabled)
    }

    fun toggleAiAssist(): Boolean {
        return com.example.services.detection.pubg.PUBGVisualAIAssistant.getInstance().toggleAiAssist()
    }

    val captureState: StateFlow<VideoCaptureState>
        get() = videoInputService.captureState

    val rois: StateFlow<List<RoiRegion>> = roiService.rois
    val selectedRoi: StateFlow<RoiRegion?> = roiService.selectedRoi

    // Dedicated smooth preview frame (Direct from hardware capture stream)
    private val _latestPreviewFrame = MutableStateFlow<DetectionFrame?>(null)
    val latestPreviewFrame: StateFlow<DetectionFrame?> = _latestPreviewFrame.asStateFlow()

    // Downsampled analysis frame (Configurable 1-30 FPS)
    val latestSampledFrame: StateFlow<FrameAnalysisInput?> = frameSampler.latestSampledFrame

    private val _croppedFrame = MutableStateFlow<FrameAnalysisInput?>(null)
    val croppedFrame: StateFlow<FrameAnalysisInput?> = _croppedFrame.asStateFlow()

    private val _realDetectionInspectionState = MutableStateFlow(RealDetectionInspectionState())
    val realDetectionInspectionState: StateFlow<RealDetectionInspectionState> = _realDetectionInspectionState.asStateFlow()

    private val _uiState = MutableStateFlow(LiveAnalyzerUiState())
    init {
        (broadcastCompositor as? com.example.services.composition.BroadcastVideoCompositor)?.setTeamsProvider(supabaseTeamsProvider)
    }

    val uiState: StateFlow<LiveAnalyzerUiState> = _uiState.asStateFlow()

    private var previewWindowStartMs = System.currentTimeMillis()
    private var previewFramesInWindow = 0
    private var captureStateJob: Job? = null
    private var frameFlowJob: Job? = null

    init {
        com.example.services.event.EventProcessorHooks.attachTo(eventService, supabaseService, viewModelScope)
        bindCurrentVideoService(liveScreenCaptureService)

        viewModelScope.launch {
            com.example.services.detection.pubg.PUBGVisualAIAssistant.getInstance().isAiAssistEnabled.collect { enabled ->
                val isAvail = com.example.services.detection.pubg.PUBGVisualAIAssistant.getInstance().isAvailable
                val statusText = if (!enabled) {
                    "OFF (OCR only)"
                } else if (!isAvail) {
                    "AI offline, OCR only"
                } else {
                    "ACTIVE (AI Assist ON)"
                }
                _uiState.value = _uiState.value.copy(
                    isAiAssistEnabled = enabled,
                    aiDetectionStatusText = statusText
                )
            }
        }

        viewModelScope.launch {
            com.example.services.station.StationDeskManager.stationState.collect { state ->
                if (state.isHeavy) {
                    frameSampler.setTargetFps(3.0)
                } else {
                    frameSampler.setTargetFps(5.0)
                }
            }
        }

        // Process sampled frames through AI analysis pipeline without altering preview crop
        viewModelScope.launch {
            frameSampler.sampledFrameFlow.collect { sampledInput ->
                val currentRoi = roiService.selectedRoi.value ?: RoiRegion.DEFAULT_KILL_FEED
                val croppedForAnalysis = RoiCropPipeline.crop(sampledInput, currentRoi)
                // Pass to detection service (real PUBG OCR / kill feed detection)
                detectionService.analyzeFrameInput(croppedForAnalysis)
            }
        }

        // Track FrameSampler telemetry
        viewModelScope.launch {
            frameSampler.framesReceived.collect { received ->
                _uiState.value = _uiState.value.copy(framesReceived = received)
            }
        }

        viewModelScope.launch {
            frameSampler.framesSampled.collect { sampled ->
                _uiState.value = _uiState.value.copy(framesSampled = sampled)
            }
        }

        viewModelScope.launch {
            frameSampler.currentAnalysisFps.collect { analysisFps ->
                _uiState.value = _uiState.value.copy(analysisFps = analysisFps)
            }
        }

        viewModelScope.launch {
            frameSampler.targetSampleFps.collect { targetFps ->
                _uiState.value = _uiState.value.copy(targetSampleFps = targetFps)
            }
        }

        // Collect Detection Results for Real Detection Inspector
        viewModelScope.launch {
            detectionService.detectionResultsFlow.collect { result ->
                val currentRoi = roiService.selectedRoi.value ?: RoiRegion.DEFAULT_KILL_FEED
                val state = _realDetectionInspectionState.value
                val now = System.currentTimeMillis()
                val dropped = (_uiState.value.framesReceived - _uiState.value.framesSampled).coerceAtLeast(0L)

                when (result) {
                    is IDetectionResult.Success -> {
                        val ev = result.event
                        val isAutoProcess = ev.confidence >= 0.50f
                        _realDetectionInspectionState.value = state.copy(
                            activeRoi = currentRoi,
                            ocrLeftText = ev.metadata["raw_left"] ?: "-",
                            ocrRightText = ev.metadata["raw_right"] ?: "-",
                            detectedIcons = ev.metadata["cause"] ?: ev.eventType.name,
                            ignoredFlags = ev.metadata["flags_ignored"] ?: "None",
                            detectedEventType = ev.eventType.name,
                            killerName = ev.killerPlayerId ?: "-",
                            victimName = ev.victimPlayerId ?: "-",
                            confidence = ev.confidence,
                            decisionStatus = if (isAutoProcess) "AUTO_PROCESS" else "ADMIN_REVIEW",
                            decisionReason = "Rule #${ev.metadata["rule_number"] ?: "1"}: ${ev.metadata["explanation"] ?: "Detection verified"}",
                            uniqueEventId = ev.eventId,
                            frameTimestampMs = ev.frameTimestamp,
                            capturedFps = _uiState.value.captureFps,
                            sampledFps = _uiState.value.targetSampleFps.toInt(),
                            analyzedFps = _uiState.value.analysisFps,
                            droppedFrames = dropped,
                            detectorLatencyMs = (now - ev.frameTimestamp).coerceAtLeast(0L)
                        )

                        com.example.services.station.StationDeskManager.updateDetectionPreview(
                            killer = ev.killerPlayerId,
                            victim = ev.victimPlayerId,
                            weapon = ev.metadata["cause"] ?: ev.eventType.name,
                            confidence = ev.confidence,
                            rule = ev.metadata["rule_number"]
                        )

                        // Forward into EventProcessor pipeline (Auto-processes high confidence, queues low confidence for Admin Review)
                        val esportsEvent = com.example.core.model.EsportsDetectedEvent(
                            id = ev.eventId,
                            sessionId = com.example.services.streaming.LocalLiveRuntimeManager.broadcastState.value.sessionId.ifBlank { "live_session" },
                            eventType = when (ev.eventType) {
                                com.example.core.model.DetectedEventType.KILL -> com.example.core.model.EsportsEventType.KILL
                                com.example.core.model.DetectedEventType.KNOCK -> com.example.core.model.EsportsEventType.KNOCK
                                com.example.core.model.DetectedEventType.ELIMINATION -> com.example.core.model.EsportsEventType.ELIMINATION
                                com.example.core.model.DetectedEventType.REVIVE -> com.example.core.model.EsportsEventType.REVIVE
                                else -> com.example.core.model.EsportsEventType.OTHER
                            },
                            timestampMs = ev.timestamp,
                            killerPlayerName = ev.killerPlayerId,
                            killerTeamTag = ev.killerTeamId,
                            victimPlayerName = ev.victimPlayerId,
                            victimTeamTag = ev.victimTeamId,
                            weaponUsed = ev.metadata["cause"],
                            confidence = ev.confidence,
                            source = ev.source,
                            frameReferenceId = ev.frameTimestamp,
                            metadata = ev.metadata
                        )
                        eventService.ingestDetectedEvent(esportsEvent)
                    }
                    is IDetectionResult.NoDetection -> {
                        _realDetectionInspectionState.value = state.copy(
                            activeRoi = currentRoi,
                            ocrLeftText = "-",
                            ocrRightText = "-",
                            detectedIcons = "-",
                            ignoredFlags = "-",
                            detectedEventType = "-",
                            killerName = "-",
                            victimName = "-",
                            confidence = 0.0f,
                            decisionStatus = "NO_DECISION_WAIT",
                            decisionReason = "Waiting for clear visual kill-feed evidence",
                            uniqueEventId = "-",
                            frameTimestampMs = result.frameTimestamp,
                            capturedFps = _uiState.value.captureFps,
                            sampledFps = _uiState.value.targetSampleFps.toInt(),
                            analyzedFps = _uiState.value.analysisFps,
                            droppedFrames = dropped
                        )
                    }
                    is IDetectionResult.Failure -> {
                        _realDetectionInspectionState.value = state.copy(
                            activeRoi = currentRoi,
                            decisionStatus = "NO_DECISION_WAIT",
                            decisionReason = "Analysis error: ${result.reason}",
                            capturedFps = _uiState.value.captureFps,
                            sampledFps = _uiState.value.targetSampleFps.toInt(),
                            analyzedFps = _uiState.value.analysisFps,
                            droppedFrames = dropped
                        )
                    }
                }
            }
        }

        // Sync YouTube Live session state to UI & Foreground Service Notification
        viewModelScope.launch {
            youtubeLiveService.sessionState.collect { state ->
                val statusText = state.name
                _uiState.value = _uiState.value.copy(youtubeStatusText = statusText)
                val isLive = state == LiveSessionState.LIVE
                com.example.services.station.StationDeskManager.updateLiveState(isLive, statusText)
                if (ScreenCaptureService.isRunning) {
                    val fullNotificationText = "Capture: ACTIVE | YouTube: $statusText"
                    ScreenCaptureService.updateLiveStatus(getApplication(), fullNotificationText)
                }
            }
        }

        // Sync Supabase connection state to UI
        viewModelScope.launch {
            supabaseService.connectionState.collect { state ->
                val statusText = when (state) {
                    is com.example.services.supabase.SupabaseConnectionState.Connected -> "Connected"
                    is com.example.services.supabase.SupabaseConnectionState.Connecting -> "Connecting..."
                    is com.example.services.supabase.SupabaseConnectionState.SyncError -> "Error"
                    is com.example.services.supabase.SupabaseConnectionState.NotConnected -> "Not connected"
                }
                _uiState.value = _uiState.value.copy(supabaseStatusText = statusText)
            }
        }

        // Auto-connect to Supabase on startup with hardcoded project credentials
        if (supabaseService.connectionState.value !is com.example.services.supabase.SupabaseConnectionState.Connected) {
            connectSupabase(SupabaseService.SUPABASE_URL, SupabaseService.SUPABASE_ANON_KEY)
        }
    }

    private var pendingGoogleAccount: android.accounts.Account? = null
    private var pendingConsentCallback: ((Intent) -> Unit)? = null

    fun clearYouTubeError() {
        _authError.value = null
    }

    // Note in a comment: DEVELOPER_ERROR 10 means SHA-1 + Web client ID must be set in Google Cloud. Do not invent a fake client ID.
    fun getYouTubeSignInIntent(context: Context): Intent {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/youtube"),
                Scope("https://www.googleapis.com/auth/youtube.force-ssl")
            )

        val gso = builder.build()
        val client = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    fun handleYouTubeSignInResult(
        task: Task<GoogleSignInAccount>,
        context: Context,
        onUserRecoverableAuth: ((Intent) -> Unit)? = null
    ) {
        _authError.value = null
        pendingConsentCallback = onUserRecoverableAuth

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null && account.account != null) {
                    val googleAcc = account.account!!
                    pendingGoogleAccount = googleAcc
                    fetchYouTubeTokenAndAuthorize(googleAcc, context, onUserRecoverableAuth)
                } else {
                    val err = "Google account selection cancelled or no account returned."
                    _authError.value = err
                    _uiState.value = _uiState.value.copy(youtubeStatusText = "AUTH_CANCELLED")
                }
            } catch (e: ApiException) {
                // Note in a comment: DEVELOPER_ERROR 10 means SHA-1 + Web client ID must be set in Google Cloud. Do not invent a fake client ID.
                val statusString = when (e.statusCode) {
                    CommonStatusCodes.DEVELOPER_ERROR -> "DEVELOPER_ERROR (10)"
                    CommonStatusCodes.SIGN_IN_REQUIRED -> "SIGN_IN_REQUIRED (4)"
                    CommonStatusCodes.NETWORK_ERROR -> "NETWORK_ERROR (7)"
                    CommonStatusCodes.INTERNAL_ERROR -> "INTERNAL_ERROR (8)"
                    CommonStatusCodes.CANCELED -> "CANCELED (16)"
                    CommonStatusCodes.API_NOT_CONNECTED -> "API_NOT_CONNECTED (17)"
                    else -> CommonStatusCodes.getStatusCodeString(e.statusCode)
                }
                val devNote = if (e.statusCode == CommonStatusCodes.DEVELOPER_ERROR) {
                    " (DEVELOPER_ERROR 10 means SHA-1 + Web client ID must be set in Google Cloud)"
                } else ""
                val err = "Google Sign-In failed [ApiException: $statusString]$devNote: ${e.localizedMessage ?: e.message ?: "Code ${e.statusCode}"}"
                _authError.value = err
                _uiState.value = _uiState.value.copy(youtubeStatusText = "AUTH_ERROR: $statusString")
            } catch (e: Exception) {
                val err = "Google Sign-In error: ${e.localizedMessage ?: e.message}"
                _authError.value = err
                _uiState.value = _uiState.value.copy(youtubeStatusText = "AUTH_ERROR")
            }
        }
    }

    fun retryYouTubeToken(context: Context) {
        val account = pendingGoogleAccount
        if (account != null) {
            viewModelScope.launch(Dispatchers.IO) {
                fetchYouTubeTokenAndAuthorize(account, context, pendingConsentCallback)
            }
        } else {
            _authError.value = "No pending Google account found to retry YouTube token."
        }
    }

    fun onConsentDeclined(resultCode: Int) {
        val err = "YouTube access approval cancelled or declined by user (Result code: $resultCode)."
        _authError.value = err
        _uiState.value = _uiState.value.copy(youtubeStatusText = "CONSENT_DECLINED")
    }

    private suspend fun fetchYouTubeTokenAndAuthorize(
        account: android.accounts.Account,
        context: Context,
        onUserRecoverableAuth: ((Intent) -> Unit)?
    ) {
        _uiState.value = _uiState.value.copy(youtubeStatusText = "GETTING_ACCESS_TOKEN")
        // Request scopes: https://www.googleapis.com/auth/youtube AND https://www.googleapis.com/auth/youtube.force-ssl
        val scope = "oauth2:https://www.googleapis.com/auth/youtube https://www.googleapis.com/auth/youtube.force-ssl"

        try {
            // Call GoogleAuthUtil.getToken on background thread
            val accessToken = GoogleAuthUtil.getToken(context, account, scope)
            if (!accessToken.isNullOrBlank()) {
                _authError.value = null
                _uiState.value = _uiState.value.copy(youtubeStatusText = "AUTHORIZING")
                val authResult = youtubeLiveService.authorizeWithToken(accessToken)
                if (authResult.isSuccess) {
                    _authError.value = null
                    _uiState.value = _uiState.value.copy(youtubeStatusText = "READY")
                } else {
                    val err = authResult.exceptionOrNull()?.message ?: "Failed to authorize YouTube channel"
                    _authError.value = err
                    _uiState.value = _uiState.value.copy(youtubeStatusText = "AUTH_ERROR")
                }
            } else {
                val err = "GoogleAuthUtil returned empty access token for ${account.name}."
                _authError.value = err
                _uiState.value = _uiState.value.copy(youtubeStatusText = "AUTH_ERROR")
            }
        } catch (e: UserRecoverableAuthException) {
            val intent = e.intent
            if (intent != null) {
                _uiState.value = _uiState.value.copy(youtubeStatusText = "AWAITING_APPROVAL")
                // If UserRecoverableAuthException: launch e.intent so the user can approve YouTube access, then retry getToken.
                if (onUserRecoverableAuth != null) {
                    withContext(Dispatchers.Main) {
                        onUserRecoverableAuth(intent)
                    }
                } else if (context is android.app.Activity) {
                    context.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            } else {
                val err = "User recoverable authentication required, but no resolution intent provided: ${e.message}"
                _authError.value = err
                _uiState.value = _uiState.value.copy(youtubeStatusText = "AUTH_ERROR")
            }
        } catch (e: Exception) {
            // Show the exact error string on YouTubeLiveStudioCard. Never swallow.
            val err = "GoogleAuthUtil.getToken failed: ${e.localizedMessage ?: e.message}"
            _authError.value = err
            _uiState.value = _uiState.value.copy(youtubeStatusText = "AUTH_ERROR")
        }
    }

    fun startYouTubeLive(
        title: String = "MVP ESPORTS PK LIVE",
        description: String = "Live PUBG Mobile esports tournament analyzed by MVP ESPORTS.",
        privacyStatus: String = "public"
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(youtubeStatusText = "CREATING_BROADCAST")
            val bRes = youtubeLiveService.createLiveBroadcast(title, description, privacyStatus)
            if (bRes.isFailure) {
                val err = bRes.exceptionOrNull()?.message ?: "Failed to create YouTube broadcast"
                _uiState.value = _uiState.value.copy(youtubeStatusText = "ERROR")
                return@launch
            }
            val bInfo = bRes.getOrNull()!!

            _uiState.value = _uiState.value.copy(youtubeStatusText = "CREATING_STREAM")
            val sRes = youtubeLiveService.createLiveStream(title)
            if (sRes.isFailure) {
                youtubeLiveService.stopBroadcast(bInfo.broadcastId)
                _uiState.value = _uiState.value.copy(youtubeStatusText = "ERROR")
                return@launch
            }
            val streamId = sRes.getOrNull()!!

            _uiState.value = _uiState.value.copy(youtubeStatusText = "BINDING_STREAM")
            val bindRes = youtubeLiveService.bindBroadcastToStream(bInfo.broadcastId, streamId)
            if (bindRes.isFailure) {
                youtubeLiveService.stopBroadcast(bInfo.broadcastId)
                _uiState.value = _uiState.value.copy(youtubeStatusText = "ERROR")
                return@launch
            }
            val boundInfo = bindRes.getOrNull()!!

            // Start hardware encoder & RTMP transport pipeline BEFORE transitioning YouTube state
            _uiState.value = _uiState.value.copy(youtubeStatusText = "CONNECTING_RTMP")
            val state = com.example.services.station.StationDeskManager.stationState.value
            val pipeRes = broadcastPipeline.startPipeline(
                context = getApplication(),
                rtmpUrl = boundInfo.rtmpIngestUrl,
                width = state.resolution.width,
                height = state.resolution.height,
                bitrate = state.quality.bitrate,
                fps = state.fps.fps
            )
            if (pipeRes.isFailure) {
                youtubeLiveService.stopBroadcast(bInfo.broadcastId)
                broadcastPipeline.stopPipeline()
                _uiState.value = _uiState.value.copy(youtubeStatusText = "ERROR")
                return@launch
            }

            // Once RTMP ingest connection and hardware encoders are actively running, transition YouTube broadcast to LIVE
            _uiState.value = _uiState.value.copy(youtubeStatusText = "STARTING_BROADCAST")
            val startRes = youtubeLiveService.startBroadcast(bInfo.broadcastId)
            if (startRes.isFailure) {
                broadcastPipeline.stopPipeline()
                youtubeLiveService.stopBroadcast(bInfo.broadcastId)
                _uiState.value = _uiState.value.copy(youtubeStatusText = "ERROR")
                return@launch
            }
        }
    }

    fun stopYouTubeLive() {
        viewModelScope.launch {
            youtubeLiveService.stopBroadcast()
            broadcastPipeline.stopPipeline()
        }
    }

    fun startVideoPipeline(rtmpUrl: String? = null) {
        val state = com.example.services.station.StationDeskManager.stationState.value
        broadcastPipeline.startPipeline(
            context = getApplication(),
            rtmpUrl = rtmpUrl,
            width = state.resolution.width,
            height = state.resolution.height,
            bitrate = state.quality.bitrate,
            fps = state.fps.fps
        )
    }

    fun stopVideoPipeline() {
        broadcastPipeline.stopPipeline()
    }

    fun disconnectYouTube() {
        _authError.value = null
        pendingGoogleAccount = null
        viewModelScope.launch {
            youtubeLiveService.disconnect()
        }
    }

    fun setTargetSampleFps(fps: Double) {
        frameSampler.setTargetSampleFps(fps)
    }

    fun selectRoi(id: String) {
        roiService.selectRoi(id)
        recomputeCrop()
    }

    fun updateRoi(roi: RoiRegion) {
        viewModelScope.launch {
            roiService.updateRoi(roi)
            recomputeCrop(roi)
        }
    }

    fun toggleRoiEnabled(id: String) {
        viewModelScope.launch {
            roiService.toggleRoiEnabled(id)
            recomputeCrop()
        }
    }

    fun deleteRoi(id: String) {
        viewModelScope.launch {
            roiService.deleteRoi(id)
            recomputeCrop()
        }
    }

    fun resetRoiDefaults() {
        viewModelScope.launch {
            roiService.resetToDefaults()
            recomputeCrop()
        }
    }

    fun addNewRoi(name: String) {
        val newRoi = RoiRegion(
            id = "roi_${System.currentTimeMillis()}",
            name = name.ifBlank { "ROI_${System.currentTimeMillis() % 1000}" },
            x = 0.2f,
            y = 0.2f,
            width = 0.3f,
            height = 0.2f,
            enabled = true
        )
        viewModelScope.launch {
            roiService.saveRoi(newRoi)
            roiService.selectRoi(newRoi.id)
            recomputeCrop(newRoi)
        }
    }

    private fun recomputeCrop(overrideRoi: RoiRegion? = null) {
        val currentRoi = overrideRoi ?: roiService.selectedRoi.value ?: RoiRegion.DEFAULT_KILL_FEED
        val preview = _latestPreviewFrame.value
        if (preview != null) {
            val input = FrameAnalysisInput.fromDetectionFrame(preview, videoInputService.activeSource)
            _croppedFrame.value = RoiCropPipeline.crop(input, currentRoi.copy(enabled = true))
        } else {
            val sampled = frameSampler.latestSampledFrame.value ?: return
            _croppedFrame.value = RoiCropPipeline.crop(sampled, currentRoi.copy(enabled = true))
        }
    }

    fun startScreenCaptureWithPermission(resultCode: Int, data: Intent) {
        val service = videoInputService
        if (service is AndroidScreenCaptureService) {
            service.attachMediaProjectionIntent(resultCode, data)
        }
        viewModelScope.launch {
            videoInputService.startCapture()
        }
    }

    fun onCapturePermissionDenied() {
        _uiState.value = _uiState.value.copy(
            inputStatusText = "Screen capture permission denied",
            screenCaptureStatusText = "DENIED"
        )
    }

    fun startScreenCapture() {
        viewModelScope.launch {
            videoInputService.startCapture()
        }
    }

    fun stopScreenCapture() {
        viewModelScope.launch {
            videoInputService.stopCapture()
        }
    }

    fun confirmAdminEvent(eventId: String, confirmedBy: String = "Admin") {
        viewModelScope.launch {
            eventService.confirmEvent(eventId, confirmedBy)
        }
    }

    fun rejectAdminEvent(eventId: String, reason: String = "Admin manual rejection", rejectedBy: String = "Admin") {
        viewModelScope.launch {
            eventService.rejectEvent(eventId, reason, rejectedBy)
        }
    }

    private fun bindCurrentVideoService(service: IVideoInputService) {
        captureStateJob?.cancel()
        frameFlowJob?.cancel()

        videoInputService = service
        broadcastCompositor.attachFrameSource(service.frameFlow, viewModelScope)

        // Collect video capture state updates to sync UI indicators
        captureStateJob = viewModelScope.launch {
            service.captureState.collect { state ->
                when (state) {
                    is VideoCaptureState.Idle -> {
                        _uiState.value = _uiState.value.copy(
                            inputStatusText = "Not connected",
                            videoSourceText = "Not connected",
                            screenCaptureStatusText = "OFF",
                            isCapturingActive = false,
                            framesCaptured = 0L,
                            captureFps = 0,
                            previewFps = 0.0,
                            resolutionText = "None"
                        )
                        _latestPreviewFrame.value = null
                        frameSampler.reset()
                        _croppedFrame.value = null
                        previewFramesInWindow = 0
                        previewWindowStartMs = System.currentTimeMillis()
                    }
                    is VideoCaptureState.Initializing -> {
                        _uiState.value = _uiState.value.copy(
                            inputStatusText = "Initializing...",
                            videoSourceText = service.activeSource.displayName,
                            screenCaptureStatusText = "STARTING",
                            isCapturingActive = false
                        )
                    }
                    is VideoCaptureState.Capturing -> {
                        _uiState.value = _uiState.value.copy(
                            inputStatusText = "Connected",
                            videoSourceText = state.sourceType.displayName,
                            screenCaptureStatusText = "ACTIVE",
                            isCapturingActive = true,
                            isVideoFileSource = state.sourceType == com.example.services.video.VideoSourceType.VIDEO_FILE_FEED,
                            framesCaptured = state.framesCaptured,
                            captureFps = state.fps,
                            resolutionText = "${state.width}x${state.height}"
                        )
                    }
                    is VideoCaptureState.Paused -> {
                        _uiState.value = _uiState.value.copy(
                            inputStatusText = "Paused",
                            screenCaptureStatusText = "PAUSED",
                            isCapturingActive = false
                        )
                    }
                    is VideoCaptureState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            inputStatusText = "Error: ${state.message}",
                            screenCaptureStatusText = "ERROR",
                            isCapturingActive = false
                        )
                    }
                }
            }
        }

        // Dedicated Parallel Ingestion:
        // 1. Direct preview update capped at 30 fps for Compose UI
        // 2. ROI crop preview updated at most 8 fps
        // 3. Downsampled analysis stream via FrameSampler (4.5 - 5.0 fps)
        var lastPreviewPushTimeMs = 0L
        var lastCropPushTimeMs = 0L

        frameFlowJob = viewModelScope.launch {
            service.frameFlow.collect { frame ->
                val now = System.currentTimeMillis()

                // Direct Preview Stream (Capped at 30 fps for UI smoothness)
                if (now - lastPreviewPushTimeMs >= 33L) {
                    _latestPreviewFrame.value = frame
                    lastPreviewPushTimeMs = now
                }

                // Live Cropped Inspection Preview (Capped at 8 fps)
                if (now - lastCropPushTimeMs >= 125L) {
                    val currentRoi = roiService.selectedRoi.value ?: RoiRegion.DEFAULT_KILL_FEED
                    val input = FrameAnalysisInput.fromDetectionFrame(frame, service.activeSource)
                    _croppedFrame.value = RoiCropPipeline.crop(input, currentRoi.copy(enabled = true))
                    lastCropPushTimeMs = now
                }

                previewFramesInWindow++
                if (now - previewWindowStartMs >= 1000L) {
                    val elapsed = (now - previewWindowStartMs).toDouble()
                    val calculatedPreviewFps = (previewFramesInWindow * 1000.0) / elapsed
                    _uiState.value = _uiState.value.copy(previewFps = calculatedPreviewFps)
                    previewFramesInWindow = 0
                    previewWindowStartMs = now
                }

                // Independent Analysis Stream (Configurable FPS)
                frameSampler.onIncomingFrame(frame, service.activeSource)
            }
        }
    }

    /**
     * Starts analysis of a real user-selected Android video file (MP4/MKV) via SAF.
     */
    fun startVideoFileAnalysis(videoUri: Uri, fileName: String? = null) {
        viewModelScope.launch {
            // Stop current source if active
            videoInputService.stopCapture()

            val fileService = VideoInputFactory.createVideoFileInputService(getApplication(), videoUri) as AndroidVideoFileInputService
            videoFileInputService = fileService
            _uiState.value = _uiState.value.copy(
                isVideoFileSource = true,
                selectedVideoFileName = fileName ?: videoUri.lastPathSegment ?: "Video File"
            )

            bindCurrentVideoService(fileService)
            fileService.startCapture()
        }
    }

    /**
     * Pauses local video file playback.
     */
    fun pauseVideoFilePlayback() {
        videoFileInputService?.pausePlayback()
    }

    /**
     * Resumes local video file playback.
     */
    fun resumeVideoFilePlayback() {
        videoFileInputService?.resumePlayback()
    }

    /**
     * Seeks to a specific position in the local video file.
     */
    fun seekVideoFile(positionMs: Long) {
        videoFileInputService?.seekTo(positionMs)
    }

    /**
     * Stops video file playback and restores live MediaProjection screen capture as active source.
     */
    fun switchToLiveScreenCapture() {
        viewModelScope.launch {
            videoFileInputService?.stopCapture()
            videoFileInputService = null
            _uiState.value = _uiState.value.copy(
                isVideoFileSource = false,
                selectedVideoFileName = null
            )
            bindCurrentVideoService(liveScreenCaptureService)
        }
    }

    fun setTab(index: Int) {
        _uiState.value = _uiState.value.copy(activeTab = index)
    }

    fun connectSupabase(
        url: String = SupabaseService.SUPABASE_URL,
        key: String = SupabaseService.SUPABASE_ANON_KEY
    ) {
        viewModelScope.launch {
            supabaseService.connect(url, key)
        }
    }

    // --- Station Control Desk API ---
    val stationState: StateFlow<com.example.services.station.StationState> =
        com.example.services.station.StationDeskManager.stationState

    fun setStationResolution(res: com.example.services.station.StationResolution) {
        com.example.services.station.StationDeskManager.setResolution(res)
    }

    fun setStationFps(fps: com.example.services.station.StationFps) {
        com.example.services.station.StationDeskManager.setFps(fps)
    }

    fun setStationQuality(quality: com.example.services.station.StationQuality) {
        com.example.services.station.StationDeskManager.setQuality(quality)
    }

    fun setStationSmoothnessMode(mode: com.example.services.station.SmoothnessMode) {
        com.example.services.station.StationDeskManager.setSmoothnessMode(mode)
        setTargetSampleFps(mode.targetDetectFps.toDouble())
    }

    fun setStationColorPreset(preset: com.example.services.station.ColorPreset) {
        com.example.services.station.StationDeskManager.setColorPreset(preset)
    }

    fun setStationBrightness(v: Float) {
        com.example.services.station.StationDeskManager.setBrightness(v)
    }

    fun setStationContrast(v: Float) {
        com.example.services.station.StationDeskManager.setContrast(v)
    }

    fun setStationSaturation(v: Float) {
        com.example.services.station.StationDeskManager.setSaturation(v)
    }

    fun setStationShadows(v: Float) {
        com.example.services.station.StationDeskManager.setShadows(v)
    }

    fun setStationHighlights(v: Float) {
        com.example.services.station.StationDeskManager.setHighlights(v)
    }

    fun resetStationColorDesk() {
        com.example.services.station.StationDeskManager.resetColorDesk()
    }

    fun startStationRecording(context: Context): Result<Unit> {
        return com.example.services.station.StationDeskManager.startRecordingFromStation(context)
    }

    fun stopStationRecording(): Result<Unit> {
        return com.example.services.station.StationDeskManager.stopRecordingFromStation()
    }

    override fun onCleared() {
        super.onCleared()
        videoInputService.release()
        detectionService.shutdown()
    }
}
