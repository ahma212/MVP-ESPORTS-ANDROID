package com.example.services.overlay

import android.content.res.Configuration
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Bitmap
import android.widget.ImageView
import java.nio.ByteBuffer
import java.util.Locale
import com.example.core.model.RoiRegion
import com.example.core.model.DetectionFrame
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.provider.MediaStore
import com.example.services.audio.AudioMixerManager
import com.example.services.audio.LocalMusicPlayerManager
import com.example.services.audio.MicrophoneCommentaryManager
import com.example.services.audio.InternalAudioCaptureManager
import com.example.services.streaming.StandingTableControlsManager
import com.example.services.streaming.LocalLiveRuntimeManager
import com.example.MainActivity
import com.example.core.model.EsportsScene
import com.example.services.scene.EsportsSceneEngine
import com.example.platform.android.ScreenCaptureService
import com.example.services.streaming.BroadcastController
import com.example.services.streaming.BroadcastLifecycleState
import com.example.services.streaming.BroadcastStreamingPipeline
import com.example.services.streaming.IYouTubeLiveService
import com.example.services.streaming.LiveSessionState
import com.example.services.streaming.YouTubeLiveService
import com.example.services.streaming.BroadcastRecordingManager
import com.example.services.station.*
import com.example.services.streaming.IFloatingControlService
import com.example.services.streaming.FloatingControlState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MVP Floating Pointer Overlay Manager.
 * Operates as a native system window overlay (TYPE_APPLICATION_OVERLAY) for in-game streaming controls.
 */
class FloatingPointerOverlay(private val context: Context) : IFloatingControlService {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs: SharedPreferences = context.getSharedPreferences("mvp_pointer_prefs", Context.MODE_PRIVATE)

    private val _controlState = MutableStateFlow(FloatingControlState())
    override val controlState: StateFlow<FloatingControlState> = _controlState.asStateFlow()

    override fun showPointer() {
        showOverlay()
        _controlState.value = _controlState.value.copy(isVisible = true)
    }

    override fun hidePointer() {
        hideOverlay()
        _controlState.value = _controlState.value.copy(isVisible = false)
    }

    override fun setExpanded(expanded: Boolean) {
        if (expanded != isExpanded) {
            toggleExpand()
        }
    }

    override fun setPointerSize(size: String) {
        pointerSizePreset = size
        pointerView?.let { updatePointerSizeAndStyle(it) }
        _controlState.value = _controlState.value.copy(pointerSize = size)
    }

    override fun updatePosition(x: Int, y: Int) {
        currentParams?.let { params ->
            params.x = x
            params.y = y
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_SECURE
            overlayContainer?.let { container ->
                if (isAttached) {
                    windowManager.updateViewLayout(container, params)
                }
            }
        }
        _controlState.value = _controlState.value.copy(lastPositionX = x, lastPositionY = y)
    }

    private var overlayContainer: FrameLayout? = null
    private var pointerView: FrameLayout? = null
    private var expandedCardView: LinearLayout? = null
    private var currentParams: WindowManager.LayoutParams? = null
    private var confirmationLayout: LinearLayout? = null

    private var isExpanded = false
    private var isAttached = false

    private var overlayJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.Main + overlayJob)
    private val youtubeService: IYouTubeLiveService = YouTubeLiveService.getInstance(context)
    private val broadcastController = BroadcastController.getInstance(context)
    private val pipeline = BroadcastStreamingPipeline.getInstance(context)
    private var isActionInProgress = false

    // Current pointer size preset
    private var pointerSizePreset = "SMALL" // SMALL, MEDIUM, LARGE
    private var isMinimized = false
    private var currentSection: OverlaySection? = null

    // Real-time telemetry updating handler
    private val uiUpdateHandler = Handler(Looper.getMainLooper())
    private var telemetryRunnable: Runnable? = null

    // Real dynamic content UI references
    private lateinit var liveStatusVal: TextView
    private lateinit var ytStatusVal: TextView
    private lateinit var capStatusVal: TextView
    private lateinit var srvStatusVal: TextView
    private lateinit var actionBtn: Button

    // YouTube Tab Views
    private lateinit var ytAccountVal: TextView
    private lateinit var ytConnectionStatusVal: TextView
    private lateinit var ytBroadcastStatusVal: TextView
    private lateinit var ytBroadcastBadgeVal: TextView
    private lateinit var ytDurationVal: TextView
    private lateinit var ytIngestVal: TextView
    private lateinit var ytErrorLayout: LinearLayout
    private lateinit var ytErrorVal: TextView
    private lateinit var ytConnectBtn: Button
    private lateinit var ytStartBtn: Button
    private lateinit var ytPauseResumeBtn: Button
    private lateinit var ytStopBtn: Button

    // Capture Tab Views (Part 12)
    private lateinit var capStatusTextVal: TextView
    private lateinit var capResVal: TextView
    private lateinit var capFpsVal: TextView
    private lateinit var capFrameCountVal: TextView
    private lateinit var capDroppedVal: TextView
    private lateinit var capProjectionVal: TextView

    // Monitor Tab Views (Part 13)
    private lateinit var monBroadcastStateVal: TextView
    private lateinit var monCaptureVal: TextView
    private lateinit var monFpsVal: TextView
    private lateinit var monTargetFpsVal: TextView
    private lateinit var monDroppedVal: TextView
    private lateinit var monLastFrameVal: TextView
    private lateinit var monH264Val: TextView
    private lateinit var monEncodedFramesVal: TextView
    private lateinit var monVideoBitrateVal: TextView
    private lateinit var monAudioStatusVal: TextView
    private lateinit var monAudioBitrateVal: TextView
    private lateinit var monRtmpVal: TextView
    private lateinit var monUptimeVal: TextView
    private lateinit var monRecordingVal: TextView
    private lateinit var monRoiVal: TextView
    private lateinit var monErrorVal: TextView

    // Admin Review Views (Part 27)
    private lateinit var pendingCountText: TextView
    private lateinit var noPendingText: TextView
    private lateinit var eventDetailsContainer: LinearLayout
    private lateinit var reviewErrorText: TextView
    private lateinit var revEventIdVal: TextView
    private lateinit var revEventTypeVal: TextView
    private lateinit var revKillerVal: TextView
    private lateinit var revVictimVal: TextView
    private lateinit var revConfidenceVal: TextView
    private lateinit var revTimestampVal: TextView
    private lateinit var revEvidenceVal: TextView
    private lateinit var confirmBtn: Button
    private lateinit var rejectBtn: Button

    // ROI & Detector Tab Views (Part 14)
    private lateinit var roiOverlayCanvasView: RoiOverlayCanvasView
    private lateinit var cropPreviewImageView: ImageView
    private lateinit var roiStatusVal: TextView
    private lateinit var roiCoordsVal: TextView
    private lateinit var detAiAssistVal: TextView
    private lateinit var aiAssistToggleBtn: Button
    private lateinit var detEngineVal: TextView
    private lateinit var detFramesVal: TextView
    private lateinit var detLastProcVal: TextView
    private lateinit var detConfidenceVal: TextView
    private lateinit var detEventTypeVal: TextView
    private lateinit var detAdminReviewVal: TextView

    // Audio Studio Tab Views (Part 15)
    private lateinit var gameVolSeekBar: SeekBar
    private lateinit var gameVolText: TextView
    private lateinit var gameMuteToggleBtn: Button

    private lateinit var micVolSeekBar: SeekBar
    private lateinit var micVolText: TextView
    private lateinit var micMuteToggleBtn: Button
    private lateinit var micStartStopToggleBtn: Button

    private lateinit var musicVolSeekBar: SeekBar
    private lateinit var musicVolText: TextView
    private lateinit var musicMuteToggleBtn: Button

    private lateinit var musicTrackSpinner: Spinner
    private lateinit var musicPlayPauseToggleBtn: Button
    private lateinit var musicLoopToggleBtn: Button
    private lateinit var musicClearToggleBtn: Button

    private lateinit var audGameCapVal: TextView
    private lateinit var audMicCapVal: TextView
    private lateinit var audMusicPlayVal: TextView
    private lateinit var audEncoderVal: TextView
    private lateinit var audPcmMixerVal: TextView

    // Section 5: Broadcast Graphics / Standing Table / Bottom Ticker / VIP Milestone Views
    private lateinit var standingTableToggleBtn: Button
    private lateinit var top3ModeToggleBtn: Button
    private lateinit var fullStandingsToggleBtn: Button
    private lateinit var standingOverlayStatusVal: TextView
    private lateinit var top3ModeStatusVal: TextView
    private lateinit var bottomTickerToggleBtn: Button
    private lateinit var tickerSpeedToggleBtn: Button
    private lateinit var tickerOverlayStatusVal: TextView
    private lateinit var vipKillCardToggleBtn: Button
    private lateinit var vipPointCardToggleBtn: Button
    private lateinit var vipKillCardStatusVal: TextView
    private lateinit var vipPointCardStatusVal: TextView
    private lateinit var vipPointCardStatusVal2: TextView // Not needed, let's keep it clean
    private lateinit var liveTeamsCountVal: TextView

    // New Part 28 Pointer Graphics Controls
    private lateinit var standingSizeBtn: Button
    private lateinit var standingPosBtn: Button
    private lateinit var tickerSizeBtn: Button
    private lateinit var tickerYOffsetBtn: Button
    private lateinit var tickerCustomTextBtn: Button
    private lateinit var tickerLoopVideoBtn: Button
    private lateinit var tickerLoopVideoSelectBtn: Button
    private lateinit var customGraphicsMasterBtn: Button
    private lateinit var customLogoBtn: Button
    private lateinit var customTextBtn: Button
    private lateinit var customMatchInfoBtn: Button
    private lateinit var customTeamShowcaseBtn: Button

    // Section 6: Scene Studio / Timer Views
    private lateinit var sceneEngineStatusVal: TextView
    private lateinit var startingCountdownBtn: Button
    private lateinit var liveMatchBtn: Button
    private lateinit var matchEndBtn: Button
    private lateinit var breakBtn: Button
    private lateinit var nextMatchCountdownBtn: Button
    private lateinit var endingBtn: Button
    private lateinit var timerStartBtn: Button
    private lateinit var timerPauseBtn: Button
    private lateinit var timerResumeBtn: Button
    private lateinit var timerResetBtn: Button

    // Section 7: YouTube Live Chat Views
    private lateinit var chatConnectionStatusVal: TextView
    private lateinit var activeChatStreamVal: TextView
    private lateinit var chatMessagesScrollContainer: LinearLayout
    private lateinit var chatInputEditText: android.widget.EditText
    private lateinit var chatSendBtn: Button
    private lateinit var chatRefreshBtn: Button
    private val cachedChatMessages = mutableSetOf<String>()
    private var isSendingChat = false
    private var chatPollingHandler: Handler? = null
    private var chatPollingRunnable: Runnable? = null

    private val localMusicTracks = mutableListOf<Pair<String, Uri>>()

    // Station Desk Overlay Views
    private lateinit var stationRecordBtn: Button
    private lateinit var stationRecordStatusVal: TextView
    private lateinit var stationOutputVal: TextView
    private lateinit var stationLiveStatusVal: TextView
    private lateinit var stationPerfBtn: Button
    private lateinit var stationQualityBtn: Button
    private lateinit var stationRes720Btn: Button
    private lateinit var stationRes1080Btn: Button
    private lateinit var stationFps24Btn: Button
    private lateinit var stationFps30Btn: Button
    private lateinit var stationBrightVal: TextView
    private lateinit var stationContrastVal: TextView
    private lateinit var stationSatVal: TextView
    private lateinit var stationBrightBar: SeekBar
    private lateinit var stationContrastBar: SeekBar
    private lateinit var stationSatBar: SeekBar
    private lateinit var stationPresetNoneBtn: Button
    private lateinit var stationPresetWarmBtn: Button
    private lateinit var stationPresetCoolBtn: Button
    private lateinit var stationPresetVintageBtn: Button

    // Menu and Section Container references
    private var categoryMenuContainer: LinearLayout? = null
    private var sectionContentContainer: FrameLayout? = null
    private var headerTitleText: TextView? = null
    private var backButtonView: TextView? = null
    private var sectionViewsMap = mutableMapOf<OverlaySection, View>()

    var onStopSessionRequested: (() -> Unit)? = null

    /**
     * 10 Master Control Room Category Sections
     */
    enum class OverlaySection(val sectionId: Int, val title: String, val subtitle: String, val badge: String) {
        STATION_DESK(0, "Station Desk", "Master Record, Look & FPS", "STATION"),
        YOUTUBE_STUDIO(1, "YouTube Studio", "Stream & Broadcast Control", "LIVE"),
        MONITOR(2, "Monitor", "Live FPS & Telemetry", "STATS"),
        ROI_DETECTOR(3, "ROI / Detector", "Kill Feed & OCR Engine", "OCR"),
        AUDIO_STUDIO(4, "Audio Studio", "Game Sound & Voice Mic", "AUDIO"),
        BROADCAST_GRAPHICS(5, "Broadcast Graphics", "Scoreboard, Ticker & VIP", "HUD"),
        SCENE_STUDIO(6, "Scene Studio", "Broadcast Scenes & Overlays", "SCENE"),
        YOUTUBE_LIVE_CHAT(7, "YouTube Live Chat", "Real-Time Chat Monitor", "CHAT"),
        MORE_SETTINGS(8, "More / Settings", "Pointer Size & Preferences", "CONFIG"),
        ADMIN_REVIEW(9, "Admin Review", "Confirm & Verify Detections", "REVIEW")
    }

    companion object {
        fun checkOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun requestOverlayPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showOverlay(statusText: String = "LIVE") {
        if (isAttached || !checkOverlayPermission(context)) return

        // Read last configuration or defaults
        pointerSizePreset = prefs.getString("pointer_size", "SMALL") ?: "SMALL"
        val savedX = prefs.getInt("pointer_last_x", 100)
        val savedY = prefs.getInt("pointer_last_y", 300)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }
        currentParams = params

        val root = OverlayRootLayout(
            context = context,
            onBackPressed = {
                if (currentSection != null) {
                    showCategoryMenu()
                } else {
                    toggleExpand()
                }
            },
            onConfigChanged = {
                val metrics = context.resources.displayMetrics
                val screenW = metrics.widthPixels
                val screenH = metrics.heightPixels
                val maxLimitX = screenW - dpToPx(50f)
                val maxLimitY = screenH - dpToPx(50f)
                var updated = false
                if (params.x > maxLimitX) {
                    params.x = maxLimitX.coerceAtLeast(0)
                    updated = true
                }
                if (params.y > maxLimitY) {
                    params.y = maxLimitY.coerceAtLeast(0)
                    updated = true
                }
                if (updated && isAttached) {
                    try {
                        windowManager.updateViewLayout(overlayContainer, params)
                    } catch (_: Exception) {}
                }
            }
        )

        // 1. Minimized Circular Pointer
        val pointer = FrameLayout(context).apply {
            updatePointerSizeAndStyle(this)
        }

        // 2. Expanded Master Control Room Panel (VIP dark translucent esports style)
        val expandedCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dpToPx(14f), dpToPx(12f), dpToPx(14f), dpToPx(14f))
            
            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14f).toFloat()
                setColor(Color.parseColor("#E6070A1E")) // Translucent VIP Deep Indigo-Black
                setStroke(dpToPx(2f), Color.parseColor("#FF8B5CF6")) // Neon Purple esports theme border
            }
            background = cardBg

            // Layout width & height bounds
            val cardParams = LinearLayout.LayoutParams(dpToPx(330f), LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams = cardParams

            // 2A. Header Section with Drag Area and Navigation
            val headerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dpToPx(10f))
            }

            val dotIndicator = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#FF22C55E"))
                }
                layoutParams = LinearLayout.LayoutParams(dpToPx(8f), dpToPx(8f)).apply {
                    rightMargin = dpToPx(6f)
                }
            }

            val backBtn = TextView(context).apply {
                text = "◀ BACK"
                setTextColor(Color.parseColor("#FF00F0FF"))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                visibility = View.GONE
                setPadding(0, dpToPx(2f), dpToPx(8f), dpToPx(2f))
                setOnClickListener {
                    showCategoryMenu()
                }
            }
            backButtonView = backBtn

            val titleText = TextView(context).apply {
                text = "MVP MASTER CONTROL"
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            }
            headerTitleText = titleText

            val dragBar = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(20f), 1f)
            }

            val minimizeBtn = TextView(context).apply {
                text = "—"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(8f), dpToPx(2f), dpToPx(8f), dpToPx(2f))
                setOnClickListener {
                    toggleExpand()
                }
            }

            val closeBtn = TextView(context).apply {
                text = "✖"
                setTextColor(Color.parseColor("#FFEF4444"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(8f), dpToPx(2f), dpToPx(8f), dpToPx(2f))
                setOnClickListener {
                    hideOverlay()
                }
            }

            headerLayout.addView(dotIndicator)
            headerLayout.addView(backBtn)
            headerLayout.addView(titleText)
            headerLayout.addView(dragBar)
            headerLayout.addView(minimizeBtn)
            headerLayout.addView(closeBtn)
            addView(headerLayout)

            // Make the expanded header draggable so user can move the panel around gameplay
            var headerInitX = 0
            var headerInitY = 0
            var headerTouchX = 0f
            var headerTouchY = 0f
            headerLayout.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        headerInitX = params.x
                        headerInitY = params.y
                        headerTouchX = event.rawX
                        headerTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - headerTouchX).toInt()
                        val dy = (event.rawY - headerTouchY).toInt()
                        params.x = (headerInitX + dx).coerceAtLeast(0)
                        params.y = (headerInitY + dy).coerceAtLeast(0)
                        if (isAttached) {
                            windowManager.updateViewLayout(root, params)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        prefs.edit()
                            .putInt("pointer_last_x", params.x)
                            .putInt("pointer_last_y", params.y)
                            .apply()
                        true
                    }
                    else -> false
                }
            }

            // 2B. Main Category Menu (Nested List)
            val menuScroll = android.widget.ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(280f)
                )
            }

            val menuContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            categoryMenuContainer = menuContainer

            // Populate Category Menu with the 8 sections
            OverlaySection.values().forEach { section ->
                val categoryRow = createCategoryMenuItem(section) {
                    openSection(section)
                }
                menuContainer.addView(categoryRow)
            }
            menuScroll.addView(menuContainer)
            addView(menuScroll)

            // 2C. Section Content Container (Only one section visible at a time)
            val contentFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }
            sectionContentContainer = contentFrame

            // Create and cache all 10 section views
            val s0 = createStationDeskSection()
            val s1 = createYoutubeStudioSection()
            val s2 = createMonitorSection()
            val s3 = createRoiDetectorSection()
            val s4 = createAudioStudioSection()
            val s5 = createBroadcastGraphicsSection()
            val s6 = createSceneStudioSection()
            val s7 = createLiveChatSection()
            val s8 = createMoreSettingsSection(params)
            val s9 = createAdminReviewSection()

            sectionViewsMap[OverlaySection.STATION_DESK] = s0
            sectionViewsMap[OverlaySection.YOUTUBE_STUDIO] = s1
            sectionViewsMap[OverlaySection.MONITOR] = s2
            sectionViewsMap[OverlaySection.ROI_DETECTOR] = s3
            sectionViewsMap[OverlaySection.AUDIO_STUDIO] = s4
            sectionViewsMap[OverlaySection.BROADCAST_GRAPHICS] = s5
            sectionViewsMap[OverlaySection.SCENE_STUDIO] = s6
            sectionViewsMap[OverlaySection.YOUTUBE_LIVE_CHAT] = s7
            sectionViewsMap[OverlaySection.MORE_SETTINGS] = s8
            sectionViewsMap[OverlaySection.ADMIN_REVIEW] = s9

            contentFrame.addView(s0)
            contentFrame.addView(s1)
            contentFrame.addView(s2)
            contentFrame.addView(s3)
            contentFrame.addView(s4)
            contentFrame.addView(s5)
            contentFrame.addView(s6)
            contentFrame.addView(s7)
            contentFrame.addView(s8)
            contentFrame.addView(s9)

            addView(contentFrame)
        }

        root.addView(pointer)
        root.addView(expandedCard)

        // Smooth Drag & Touch Handler for Collapsed Pointer
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        pointer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > dpToPx(4f) || Math.abs(dy) > dpToPx(4f)) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy

                    // Enforce boundary safety
                    if (params.x < 0) params.x = 0
                    if (params.y < 0) params.y = 0

                    if (isAttached) {
                        windowManager.updateViewLayout(root, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        toggleExpand()
                    } else {
                        // Persist last coordinate location safely
                        prefs.edit()
                            .putInt("pointer_last_x", params.x)
                            .putInt("pointer_last_y", params.y)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }

        pointerView = pointer
        expandedCardView = expandedCard
        overlayContainer = root

        try {
            if (!overlayJob.isActive) {
                overlayJob = SupervisorJob()
                scope = CoroutineScope(Dispatchers.Main + overlayJob)
            }
            windowManager.addView(root, params)
            isAttached = true
            startTelemetryUpdates()
            observeSessionState()
        } catch (_: Exception) {
            isAttached = false
        }
    }

    private fun showCategoryMenu() {
        currentSection = null
        backButtonView?.visibility = View.GONE
        headerTitleText?.text = "MVP MASTER CONTROL"
        categoryMenuContainer?.parent?.let { (it as? View)?.visibility = View.VISIBLE }
        sectionContentContainer?.visibility = View.GONE
        sectionViewsMap.values.forEach { it.visibility = View.GONE }
    }

    private fun openSection(section: OverlaySection) {
        currentSection = section
        backButtonView?.visibility = View.VISIBLE
        headerTitleText?.text = section.title.uppercase()
        categoryMenuContainer?.parent?.let { (it as? View)?.visibility = View.GONE }
        sectionContentContainer?.visibility = View.VISIBLE

        sectionViewsMap.forEach { (sec, view) ->
            view.visibility = if (sec == section) View.VISIBLE else View.GONE
        }

        if (section == OverlaySection.YOUTUBE_LIVE_CHAT) {
            startChatPolling()
        } else {
            stopChatPolling()
        }
    }

    private fun createCategoryMenuItem(section: OverlaySection, onClick: () -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(10f), dpToPx(8f), dpToPx(10f), dpToPx(8f))
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6f)
            }

            val itemBg = GradientDrawable().apply {
                cornerRadius = dpToPx(8f).toFloat()
                setColor(Color.parseColor("#661E1B4B")) // Subtle translucent card
                setStroke(1, Color.parseColor("#4D8B5CF6"))
            }
            background = itemBg

            // Number badge / Index
            val numView = TextView(context).apply {
                text = "${section.sectionId}"
                setTextColor(Color.parseColor("#FF00F0FF"))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, dpToPx(8f), 0)
            }

            val textLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val titleView = TextView(context).apply {
                text = section.title
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }

            val subtitleView = TextView(context).apply {
                text = section.subtitle
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 9.5f
            }

            textLayout.addView(titleView)
            textLayout.addView(subtitleView)

            val badgeView = TextView(context).apply {
                text = section.badge
                setTextColor(Color.parseColor("#FF8B5CF6"))
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(6f), dpToPx(2f), dpToPx(6f), dpToPx(2f))
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(4f).toFloat()
                    setColor(Color.parseColor("#408B5CF6"))
                }
            }

            val arrowView = TextView(context).apply {
                text = "›"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(6f), 0, 0, 0)
            }

            addView(numView)
            addView(textLayout)
            addView(badgeView)
            addView(arrowView)

            setOnClickListener {
                onClick()
            }
        }
    }

    private fun toggleExpand() {
        val params = currentParams ?: return
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_SECURE
        isExpanded = !isExpanded
        if (isExpanded) {
            showCategoryMenu()
            pointerView?.visibility = View.GONE
            expandedCardView?.visibility = View.VISIBLE
        } else {
            expandedCardView?.visibility = View.GONE
            pointerView?.visibility = View.VISIBLE
        }
        if (isAttached && overlayContainer != null) {
            windowManager.updateViewLayout(overlayContainer, params)
        }
    }

    private fun updatePointerSizeAndStyle(pointer: FrameLayout) {
        val dpSize = when (pointerSizePreset) {
            "SMALL" -> 44f
            "MEDIUM" -> 56f
            "LARGE" -> 68f
            else -> 44f
        }

        val pxSize = dpToPx(dpSize)
        val sizeParams = FrameLayout.LayoutParams(pxSize, pxSize).apply {
            gravity = Gravity.CENTER
        }
        pointer.layoutParams = sizeParams

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#E6070A1E")) // Deep premium dark indigo-black
            setStroke(dpToPx(2.5f), Color.parseColor("#FF8B5CF6")) // Neon Violet/Purple outline
        }
        pointer.background = bg
        pointer.removeAllViews()

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // Inner Glowing Cyan Core Dot
        val dot = View(context).apply {
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF00F0FF")) // Neon Cyan core dot
            }
            background = dotBg
            val dotSize = dpToPx(if (pointerSizePreset == "SMALL") 8f else 12f)
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize)
        }

        contentLayout.addView(dot)
        pointer.addView(contentLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })
    }

    private data class RowResult(
        val rowView: LinearLayout,
        val labelView: TextView,
        val valueView: TextView
    )

    // ==========================================
    // SECTION 0: STATION DESK (MASTER VIDEO CONTROL)
    // ==========================================
    private fun createStationDeskSection(): View {
        val scroll = android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(340f)
            )
            visibility = View.GONE
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(4f), 0, dpToPx(8f))
        }

        // 1. Status & Subtitle Header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dpToPx(6f))
        }
        val headerTitle = TextView(context).apply {
            text = "STATION MASTER VIDEO DESK"
            setTextColor(Color.parseColor("#FFEA580C"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        val headerSub = TextView(context).apply {
            text = "Pre-YouTube Composed Program Desk"
            setTextColor(Color.parseColor("#FF94A3B8"))
            textSize = 9.5f
        }
        header.addView(headerTitle)
        header.addView(headerSub)
        container.addView(header)

        // 2. Master Record Button (Prominent)
        stationRecordBtn = Button(context).apply {
            text = "START RECORD"
            setBackgroundColor(Color.parseColor("#FFEA580C"))
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40f)
            )
            setOnClickListener {
                if (StationDeskManager.stationState.value.isRecording) {
                    StationDeskManager.stopRecordingFromStation()
                } else {
                    StationDeskManager.startRecordingFromStation(context)
                }
            }
        }
        container.addView(stationRecordBtn)

        // 3. Status Rows
        val rRec = createRow("Local Record:", "IDLE")
        stationRecordStatusVal = rRec.valueView
        container.addView(rRec.rowView)

        val rPath = createRow("Save Target:", "Movies/MVP-Esports")
        stationOutputVal = rPath.valueView
        container.addView(rPath.rowView)

        val rLive = createRow("YouTube Live:", "IDLE")
        stationLiveStatusVal = rLive.valueView
        container.addView(rLive.rowView)

        // 4. Smoothness / Performance Mode
        val perfLabel = TextView(context).apply {
            text = "PERFORMANCE ENGINE"
            setTextColor(Color.parseColor("#FF94A3B8"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(6f), 0, dpToPx(3f))
        }
        container.addView(perfLabel)

        val modeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dpToPx(4f))
        }
        stationPerfBtn = Button(context).apply {
            text = "PERFORMANCE (720p)"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f).apply {
                rightMargin = dpToPx(2f)
            }
            setOnClickListener {
                StationDeskManager.setSmoothnessMode(SmoothnessMode.PERFORMANCE)
            }
        }
        stationQualityBtn = Button(context).apply {
            text = "QUALITY (1080p)"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f).apply {
                leftMargin = dpToPx(2f)
            }
            setOnClickListener {
                StationDeskManager.setSmoothnessMode(SmoothnessMode.QUALITY)
            }
        }
        modeRow.addView(stationPerfBtn)
        modeRow.addView(stationQualityBtn)
        container.addView(modeRow)

        // 5. Resolution & FPS
        val resLabel = TextView(context).apply {
            text = "RESOLUTION & FPS"
            setTextColor(Color.parseColor("#FF94A3B8"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(4f), 0, dpToPx(3f))
        }
        container.addView(resLabel)

        val resFpsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dpToPx(4f))
        }
        stationRes720Btn = Button(context).apply {
            text = "720p"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(30f), 1f).apply {
                rightMargin = dpToPx(2f)
            }
            setOnClickListener { StationDeskManager.setResolution(StationResolution.RES_720P) }
        }
        stationRes1080Btn = Button(context).apply {
            text = "1080p"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(30f), 1f).apply {
                rightMargin = dpToPx(4f)
            }
            setOnClickListener { StationDeskManager.setResolution(StationResolution.RES_1080P) }
        }
        stationFps24Btn = Button(context).apply {
            text = "24 FPS"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(30f), 1f).apply {
                rightMargin = dpToPx(2f)
            }
            setOnClickListener { StationFps.FPS_24?.let { StationDeskManager.setFps(it) } }
        }
        stationFps30Btn = Button(context).apply {
            text = "30 FPS"
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(30f), 1f)
            setOnClickListener { StationFps.FPS_30?.let { StationDeskManager.setFps(it) } }
        }
        resFpsRow.addView(stationRes720Btn)
        resFpsRow.addView(stationRes1080Btn)
        resFpsRow.addView(stationFps24Btn)
        resFpsRow.addView(stationFps30Btn)
        container.addView(resFpsRow)

        // 6. Color Grading (Look)
        val colorHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(6f), 0, dpToPx(3f))
        }
        val colorLabel = TextView(context).apply {
            text = "COLOR GRADING LOOK"
            setTextColor(Color.parseColor("#FF94A3B8"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val resetBtn = TextView(context).apply {
            text = "RESET"
            setTextColor(Color.parseColor("#FFEA580C"))
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { StationDeskManager.resetColorDesk() }
        }
        colorHeader.addView(colorLabel)
        colorHeader.addView(resetBtn)
        container.addView(colorHeader)

        // Presets row
        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dpToPx(4f))
        }
        stationPresetNoneBtn = Button(context).apply {
            text = "NONE"
            textSize = 9f
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(28f), 1f).apply { rightMargin = dpToPx(1f) }
            setOnClickListener { StationDeskManager.setColorPreset(ColorPreset.NONE) }
        }
        stationPresetWarmBtn = Button(context).apply {
            text = "WARM"
            textSize = 9f
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(28f), 1f).apply { rightMargin = dpToPx(1f) }
            setOnClickListener { StationDeskManager.setColorPreset(ColorPreset.WARM) }
        }
        stationPresetCoolBtn = Button(context).apply {
            text = "COOL"
            textSize = 9f
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(28f), 1f).apply { rightMargin = dpToPx(1f) }
            setOnClickListener { StationDeskManager.setColorPreset(ColorPreset.COOL) }
        }
        stationPresetVintageBtn = Button(context).apply {
            text = "VINTAGE"
            textSize = 8.5f
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(28f), 1f)
            setOnClickListener { StationDeskManager.setColorPreset(ColorPreset.VINTAGE) }
        }
        presetRow.addView(stationPresetNoneBtn)
        presetRow.addView(stationPresetWarmBtn)
        presetRow.addView(stationPresetCoolBtn)
        presetRow.addView(stationPresetVintageBtn)
        container.addView(presetRow)

        // Brightness row
        val brightRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(2f), 0, dpToPx(2f))
        }
        val brightLbl = TextView(context).apply {
            text = "Brightness"
            setTextColor(Color.parseColor("#FF94A3B8"))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(dpToPx(65f), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        stationBrightBar = SeekBar(context).apply {
            max = 200
            progress = 100
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val valF = (progress - 100) / 100f
                        StationDeskManager.setBrightness(valF)
                    }
                }
                override fun onStartTrackingTouch(b: SeekBar?) {}
                override fun onStopTrackingTouch(b: SeekBar?) {}
            })
        }
        stationBrightVal = TextView(context).apply {
            text = "0.0"
            setTextColor(Color.WHITE)
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(dpToPx(32f), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        brightRow.addView(brightLbl)
        brightRow.addView(stationBrightBar)
        brightRow.addView(stationBrightVal)
        container.addView(brightRow)

        // Contrast row
        val contrastRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(2f), 0, dpToPx(2f))
        }
        val contrastLbl = TextView(context).apply {
            text = "Contrast"
            setTextColor(Color.parseColor("#FF94A3B8"))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(dpToPx(65f), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        stationContrastBar = SeekBar(context).apply {
            max = 150
            progress = 50
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val valF = 0.5f + (progress / 100f)
                        StationDeskManager.setContrast(valF)
                    }
                }
                override fun onStartTrackingTouch(b: SeekBar?) {}
                override fun onStopTrackingTouch(b: SeekBar?) {}
            })
        }
        stationContrastVal = TextView(context).apply {
            text = "1.0"
            setTextColor(Color.WHITE)
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(dpToPx(32f), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        contrastRow.addView(contrastLbl)
        contrastRow.addView(stationContrastBar)
        contrastRow.addView(stationContrastVal)
        container.addView(contrastRow)

        // Saturation row
        val satRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(2f), 0, dpToPx(2f))
        }
        val satLbl = TextView(context).apply {
            text = "Saturation"
            setTextColor(Color.parseColor("#FF94A3B8"))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(dpToPx(65f), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        stationSatBar = SeekBar(context).apply {
            max = 200
            progress = 100
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val valF = progress / 100f
                        StationDeskManager.setSaturation(valF)
                    }
                }
                override fun onStartTrackingTouch(b: SeekBar?) {}
                override fun onStopTrackingTouch(b: SeekBar?) {}
            })
        }
        stationSatVal = TextView(context).apply {
            text = "1.0"
            setTextColor(Color.WHITE)
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(dpToPx(32f), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        satRow.addView(satLbl)
        satRow.addView(stationSatBar)
        satRow.addView(stationSatVal)
        container.addView(satRow)

        // Supabase Status Note
        val note = TextView(context).apply {
            text = "SUPABASE SYNC: READ-ONLY (SCORES NOT MODIFIED BY STATION)"
            setTextColor(Color.parseColor("#FF64748B"))
            textSize = 8.5f
            setPadding(0, dpToPx(6f), 0, 0)
        }
        container.addView(note)

        scroll.addView(container)
        return scroll
    }

    private fun updateStationDeskUi(state: StationState) {
        if (!::stationRecordBtn.isInitialized) return

        uiUpdateHandler.post {
            stationRecordBtn.text = if (state.isRecording) {
                "STOP RECORD (%02d:%02d)".format(
                    state.recordingDurationSeconds / 60,
                    state.recordingDurationSeconds % 60
                )
            } else {
                "START RECORD"
            }
            stationRecordBtn.setBackgroundColor(
                if (state.isRecording) Color.parseColor("#FFDC2626") else Color.parseColor("#FFEA580C")
            )

            stationRecordStatusVal.text = if (state.isRecording) "RECORDING ACTIVE" else "IDLE"
            stationRecordStatusVal.setTextColor(
                if (state.isRecording) Color.parseColor("#FFEF4444") else Color.parseColor("#FF94A3B8")
            )

            stationOutputVal.text = if (state.isRecording) (state.recordingFileName ?: "MVP_STATION.mp4") else "Movies/MVP-Esports"
            stationLiveStatusVal.text = state.liveStatus.uppercase()
            stationLiveStatusVal.setTextColor(
                if (state.isLiveActive) Color.parseColor("#FF22C55E") else Color.parseColor("#FF94A3B8")
            )

            // Smoothness Buttons
            val isPerf = state.smoothnessMode == SmoothnessMode.PERFORMANCE
            stationPerfBtn.setBackgroundColor(if (isPerf) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))
            stationQualityBtn.setBackgroundColor(if (!isPerf) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))

            // Resolution Buttons
            val is720 = state.resolution == StationResolution.RES_720P
            stationRes720Btn.setBackgroundColor(if (is720) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))
            stationRes1080Btn.setBackgroundColor(if (!is720) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))

            // FPS Buttons
            val is24 = state.fps == StationFps.FPS_24
            stationFps24Btn.setBackgroundColor(if (is24) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))
            stationFps30Btn.setBackgroundColor(if (!is24) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))

            // Presets
            stationPresetNoneBtn.setBackgroundColor(if (state.colorDesk.preset == ColorPreset.NONE) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))
            stationPresetWarmBtn.setBackgroundColor(if (state.colorDesk.preset == ColorPreset.WARM) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))
            stationPresetCoolBtn.setBackgroundColor(if (state.colorDesk.preset == ColorPreset.COOL) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))
            stationPresetVintageBtn.setBackgroundColor(if (state.colorDesk.preset == ColorPreset.VINTAGE) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))

            // Sliders & Text
            stationBrightVal.text = "%.2f".format(state.colorDesk.brightness)
            val brightProgress = ((state.colorDesk.brightness + 1.0f) * 100).toInt().coerceIn(0, 200)
            if (stationBrightBar.progress != brightProgress) {
                stationBrightBar.progress = brightProgress
            }

            stationContrastVal.text = "%.2f".format(state.colorDesk.contrast)
            val contrastProgress = ((state.colorDesk.contrast - 0.5f) * 100).toInt().coerceIn(0, 150)
            if (stationContrastBar.progress != contrastProgress) {
                stationContrastBar.progress = contrastProgress
            }

            stationSatVal.text = "%.2f".format(state.colorDesk.saturation)
            val satProgress = (state.colorDesk.saturation * 100).toInt().coerceIn(0, 200)
            if (stationSatBar.progress != satProgress) {
                stationSatBar.progress = satProgress
            }
        }
    }

    // ==========================================
    // SECTION 1: YOUTUBE STUDIO
    // ==========================================
    private fun createYoutubeStudioSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dpToPx(4f), 0, dpToPx(4f))

            // 1. Status Bar & Duration Header
            val statusHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dpToPx(8f))
            }

            ytBroadcastBadgeVal = TextView(context).apply {
                text = "IDLE"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(8f), dpToPx(3f), dpToPx(8f), dpToPx(3f))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(4f).toFloat()
                    setColor(Color.parseColor("#FF334155"))
                }
            }

            ytDurationVal = TextView(context).apply {
                text = "0s"
                setTextColor(Color.parseColor("#FF38BDF8"))
                textSize = 12f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            statusHeader.addView(ytBroadcastBadgeVal)
            statusHeader.addView(ytDurationVal)
            addView(statusHeader)

            // 2. Real Telemetry Rows
            val r1 = createRow("Live Broadcast:", "IDLE")
            liveStatusVal = r1.valueView
            ytBroadcastStatusVal = r1.valueView
            addView(r1.rowView)

            val r2 = createRow("Account:", "No Active Account")
            ytAccountVal = r2.valueView
            addView(r2.rowView)

            val r3 = createRow("YouTube Connect:", "DISCONNECTED")
            ytStatusVal = r3.valueView
            ytConnectionStatusVal = r3.valueView
            addView(r3.rowView)

            val r4 = createRow("Stream Ingest:", "Standby")
            ytIngestVal = r4.valueView
            addView(r4.rowView)

            val r5 = createRow("Screen Capture:", "OFF")
            capStatusVal = r5.valueView
            addView(r5.rowView)

            val r6 = createRow("Background Service:", "STOPPED")
            srvStatusVal = r6.valueView
            addView(r6.rowView)

            // 3. Dedicated Real Error Alert Layout
            ytErrorLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(dpToPx(8f), dpToPx(6f), dpToPx(8f), dpToPx(6f))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(6f).toFloat()
                    setColor(Color.parseColor("#FF451A1A"))
                    setStroke(dpToPx(1f), Color.parseColor("#FFEF4444"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(6f)
                    bottomMargin = dpToPx(6f)
                }

                ytErrorVal = TextView(context).apply {
                    text = ""
                    setTextColor(Color.parseColor("#FFFCA5A5"))
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                }
                addView(ytErrorVal)
            }
            addView(ytErrorLayout)

            // 4. Primary Broadcast Actions (START / PAUSE-RESUME / STOP)
            val primaryBtnLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(8f), 0, 0)
            }

            actionBtn = Button(context).apply {
                text = "START LIVE"
                setBackgroundColor(Color.parseColor("#FF16A34A"))
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    triggerStartLiveAction()
                }
            }
            ytStartBtn = actionBtn

            ytPauseResumeBtn = Button(context).apply {
                text = "PAUSE"
                setBackgroundColor(Color.parseColor("#FFD97706"))
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dpToPx(4f)
                }
                setOnClickListener {
                    triggerPauseResumeAction()
                }
            }

            ytStopBtn = Button(context).apply {
                text = "STOP LIVE"
                setBackgroundColor(Color.parseColor("#FFDC2626"))
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dpToPx(4f)
                }
                setOnClickListener {
                    triggerStopLiveAction()
                }
            }

            primaryBtnLayout.addView(ytStartBtn)
            primaryBtnLayout.addView(ytPauseResumeBtn)
            primaryBtnLayout.addView(ytStopBtn)
            addView(primaryBtnLayout)

            // 5. Secondary Row (CONNECT / OPEN APP)
            val secondaryBtnLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(6f), 0, 0)
            }

            ytConnectBtn = Button(context).apply {
                text = "CONNECT"
                setBackgroundColor(Color.parseColor("#FF4F46E5"))
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    scope.launch {
                        val authorized = youtubeService.isAuthorized.value
                        if (authorized) {
                            youtubeService.disconnect()
                        } else {
                            val appIntent = Intent(context, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            context.startActivity(appIntent)
                            toggleExpand()
                        }
                    }
                }
            }

            val appBtn = Button(context).apply {
                text = "OPEN APP"
                setBackgroundColor(Color.parseColor("#FF334155"))
                setTextColor(Color.WHITE)
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dpToPx(6f)
                }
                setOnClickListener {
                    val appIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(appIntent)
                }
            }

            secondaryBtnLayout.addView(ytConnectBtn)
            secondaryBtnLayout.addView(appBtn)
            addView(secondaryBtnLayout)
        }
    }

    private fun triggerStartLiveAction() {
        if (isActionInProgress) return
        val currentBroadcastState = broadcastController.broadcastState.value
        if (currentBroadcastState == BroadcastLifecycleState.LIVE || currentBroadcastState == BroadcastLifecycleState.PAUSED) {
            return
        }

        isActionInProgress = true
        updateControlsState()

        scope.launch {
            try {
                val isYtAuth = youtubeService.isAuthorized.value
                var targetBroadcastId: String? = null
                if (isYtAuth) {
                    val curB = youtubeService.currentBroadcast.value
                    if (curB != null && curB.lifeCycleStatus != "complete") {
                        targetBroadcastId = curB.broadcastId
                    } else {
                        val bRes = youtubeService.createLiveBroadcast(
                            title = "MVP ESPORTS PK LIVE TOURNAMENT",
                            description = "Live PUBG Mobile esports action analyzed by MVP ESPORTS."
                        )
                        if (bRes.isSuccess) {
                            val bInfo = bRes.getOrNull()!!
                            val sRes = youtubeService.createLiveStream("MVP ESPORTS STREAM")
                            if (sRes.isSuccess) {
                                val streamId = sRes.getOrNull()!!
                                val bindRes = youtubeService.bindBroadcastToStream(bInfo.broadcastId, streamId)
                                if (bindRes.isSuccess) {
                                    targetBroadcastId = bInfo.broadcastId
                                }
                            }
                        }
                    }
                }

                broadcastController.prepareBroadcast()
                val startRes = broadcastController.startBroadcast()
                if (startRes.isSuccess && targetBroadcastId != null) {
                    youtubeService.startBroadcast(targetBroadcastId)
                }
            } finally {
                isActionInProgress = false
                updateControlsState()
            }
        }
    }

    private fun triggerPauseResumeAction() {
        if (isActionInProgress) return
        isActionInProgress = true
        updateControlsState()

        scope.launch {
            try {
                val bState = broadcastController.broadcastState.value
                if (bState == BroadcastLifecycleState.LIVE) {
                    broadcastController.pauseBroadcast()
                } else if (bState == BroadcastLifecycleState.PAUSED) {
                    broadcastController.resumeBroadcast()
                }
            } finally {
                isActionInProgress = false
                updateControlsState()
            }
        }
    }

    private fun triggerStopLiveAction() {
        requestStopLiveConfirmation {
            if (isActionInProgress) return@requestStopLiveConfirmation
            isActionInProgress = true
            updateControlsState()

            scope.launch {
                try {
                    onStopSessionRequested?.invoke()
                    broadcastController.endBroadcast()
                    youtubeService.stopBroadcast()
                } finally {
                    isActionInProgress = false
                    updateControlsState()
                }
            }
        }
    }

    private fun requestStopLiveConfirmation(onConfirmed: () -> Unit) {
        val root = overlayContainer ?: return
        
        // Remove any existing confirmation view first
        confirmationLayout?.let { root.removeView(it) }

        val confirmCard = LinearLayout(context)
        confirmCard.orientation = LinearLayout.VERTICAL
        confirmCard.setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
        
        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12f).toFloat()
            setColor(Color.parseColor("#FF1E293B")) // Slate 800 dark background
            setStroke(dpToPx(2f), Color.parseColor("#FFEF4444")) // Alert red accent border
        }
        confirmCard.background = cardBg

        // Layout width control
        confirmCard.layoutParams = FrameLayout.LayoutParams(dpToPx(280f), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }

        val titleText = TextView(context).apply {
            text = "STOP LIVE?"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(8f))
        }

        val descText = TextView(context).apply {
            text = "This will stop:\n• YouTube live\n• streaming session\n• live session\n\n(Capture and background service will remain active independently)"
            setTextColor(Color.parseColor("#FF94A3B8"))
            textSize = 12f
            gravity = Gravity.LEFT
            setPadding(0, 0, 0, dpToPx(14f))
        }

        val btnsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val yesBtn = Button(context).apply {
            text = "YES, STOP"
            setBackgroundColor(Color.parseColor("#FFDC2626"))
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                onConfirmed()
                root.removeView(confirmCard)
                confirmationLayout = null
            }
        }

        val noBtn = Button(context).apply {
            text = "CANCEL"
            setBackgroundColor(Color.parseColor("#FF475569"))
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dpToPx(8f)
            }
            setOnClickListener {
                root.removeView(confirmCard)
                confirmationLayout = null
            }
        }

        btnsLayout.addView(yesBtn)
        btnsLayout.addView(noBtn)

        confirmCard.addView(titleText)
        confirmCard.addView(descText)
        confirmCard.addView(btnsLayout)

        confirmationLayout = confirmCard
        root.addView(confirmCard)
    }

    // ==========================================
    // SECTION 2: MONITOR & TELEMETRY
    // ==========================================
    private fun createMonitorSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dpToPx(4f), 0, dpToPx(4f))

            val r1 = createRow("Broadcast State:", "IDLE")
            monBroadcastStateVal = r1.valueView
            addView(r1.rowView)

            val r2 = createRow("Screen Capture:", "OFF")
            monCaptureVal = r2.valueView
            addView(r2.rowView)

            val r3 = createRow("Actual Captured FPS:", "0 FPS")
            monFpsVal = r3.valueView
            addView(r3.rowView)

            val r4 = createRow("Target FPS:", "30 FPS")
            monTargetFpsVal = r4.valueView
            addView(r4.rowView)

            val r5 = createRow("Dropped Frames:", "0")
            monDroppedVal = r5.valueView
            addView(r5.rowView)

            val r6 = createRow("Last Frame Time:", "N/A")
            monLastFrameVal = r6.valueView
            addView(r6.rowView)

            val r7 = createRow("H.264 Encoder:", "STOPPED")
            monH264Val = r7.valueView
            addView(r7.rowView)

            val r8 = createRow("Encoded Frames:", "0")
            monEncodedFramesVal = r8.valueView
            addView(r8.rowView)

            val r9 = createRow("Video Bitrate:", "N/A")
            monVideoBitrateVal = r9.valueView
            addView(r9.rowView)

            val r10 = createRow("AAC Audio Status:", "INACTIVE")
            monAudioStatusVal = r10.valueView
            addView(r10.rowView)

            val r11 = createRow("Audio Bitrate:", "N/A")
            monAudioBitrateVal = r11.valueView
            addView(r11.rowView)

            val r12 = createRow("RTMP Connection:", "DISCONNECTED")
            monRtmpVal = r12.valueView
            addView(r12.rowView)

            val r13 = createRow("Stream Duration:", "0s")
            monUptimeVal = r13.valueView
            addView(r13.rowView)

            val r14 = createRow("Recording Status:", "OFF")
            monRecordingVal = r14.valueView
            addView(r14.rowView)

            val r15 = createRow("ROI / Detector:", "ACTIVE")
            monRoiVal = r15.valueView
            addView(r15.rowView)

            val r16 = createRow("Diagnostic Error:", "None")
            monErrorVal = r16.valueView
            addView(r16.rowView)

            // Compact Local Recording Controls Group
            val recTitleText = TextView(context).apply {
                text = "LOCAL RECORDING CONTROLS"
                setTextColor(Color.parseColor("#FF00F0FF"))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(8f), 0, dpToPx(4f))
            }
            addView(recTitleText)

            val profileLabel = TextView(context).apply {
                text = "Recording Quality Profile:"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 10f
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))
            }
            addView(profileLabel)

            val profileRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dpToPx(4f))
            }

            val recordingManager = com.example.services.streaming.BroadcastRecordingManager.getInstance()

            var updateProfileBtns: (() -> Unit)? = null
            var updateFpsBtns: (() -> Unit)? = null

            val highBtn = Button(context).apply {
                text = "HIGH"
                textSize = 9f
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f)
                setOnClickListener {
                    recordingManager.setSelectedProfile(com.example.services.streaming.RecordingProfile.HIGH)
                    updateProfileBtns?.invoke()
                }
            }

            val medBtn = Button(context).apply {
                text = "MEDIUM"
                textSize = 9f
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f).apply {
                    leftMargin = dpToPx(4f)
                }
                setOnClickListener {
                    recordingManager.setSelectedProfile(com.example.services.streaming.RecordingProfile.MEDIUM)
                    updateProfileBtns?.invoke()
                }
            }

            val lowBtn = Button(context).apply {
                text = "LOW"
                textSize = 9f
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f).apply {
                    leftMargin = dpToPx(4f)
                }
                setOnClickListener {
                    recordingManager.setSelectedProfile(com.example.services.streaming.RecordingProfile.LOW)
                    updateProfileBtns?.invoke()
                }
            }

            profileRow.addView(highBtn)
            profileRow.addView(medBtn)
            profileRow.addView(lowBtn)
            addView(profileRow)

            updateProfileBtns = {
                val current = recordingManager.selectedProfile.value
                highBtn.setBackgroundColor(if (current == com.example.services.streaming.RecordingProfile.HIGH) Color.parseColor("#FF1E3A8A") else Color.parseColor("#FF334155"))
                highBtn.setTextColor(Color.WHITE)
                medBtn.setBackgroundColor(if (current == com.example.services.streaming.RecordingProfile.MEDIUM) Color.parseColor("#FF1E3A8A") else Color.parseColor("#FF334155"))
                medBtn.setTextColor(Color.WHITE)
                lowBtn.setBackgroundColor(if (current == com.example.services.streaming.RecordingProfile.LOW) Color.parseColor("#FF1E3A8A") else Color.parseColor("#FF334155"))
                lowBtn.setTextColor(Color.WHITE)
            }
            updateProfileBtns.invoke()

            val fpsLabel = TextView(context).apply {
                text = "Frame Rate (FPS):"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 10f
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))
            }
            addView(fpsLabel)

            val fpsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dpToPx(6f))
            }

            val fps30Btn = Button(context).apply {
                text = "30 FPS"
                textSize = 9f
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f)
                setOnClickListener {
                    recordingManager.setSelectedFps(30)
                    updateFpsBtns?.invoke()
                }
            }

            val fps60Btn = Button(context).apply {
                text = "60 FPS"
                textSize = 9f
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f).apply {
                    leftMargin = dpToPx(4f)
                }
                setOnClickListener {
                    recordingManager.setSelectedFps(60)
                    updateFpsBtns?.invoke()
                }
            }

            fpsRow.addView(fps30Btn)
            fpsRow.addView(fps60Btn)
            addView(fpsRow)

            updateFpsBtns = {
                val currentFps = recordingManager.selectedFps.value
                fps30Btn.setBackgroundColor(if (currentFps == 30) Color.parseColor("#FF1E3A8A") else Color.parseColor("#FF334155"))
                fps30Btn.setTextColor(Color.WHITE)
                fps60Btn.setBackgroundColor(if (currentFps == 60) Color.parseColor("#FF1E3A8A") else Color.parseColor("#FF334155"))
                fps60Btn.setTextColor(Color.WHITE)
            }
            updateFpsBtns.invoke()

            // START / STOP recording action layout
            val recordActionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(4f), 0, 0)
            }

            val startRecBtn = Button(context).apply {
                text = "START RECORDING"
                setBackgroundColor(Color.parseColor("#FF22C55E"))
                setTextColor(Color.WHITE)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(38f), 1f)
                setOnClickListener {
                    val res = StationDeskManager.startRecordingFromStation(context)
                    if (res.isFailure) {
                        Log.e("FloatingPointer", "Recording start failed: ${res.exceptionOrNull()?.message}")
                    }
                }
            }

            val stopRecBtn = Button(context).apply {
                text = "STOP RECORDING"
                setBackgroundColor(Color.parseColor("#FFDC2626"))
                setTextColor(Color.WHITE)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(38f), 1f).apply {
                    leftMargin = dpToPx(6f)
                }
                setOnClickListener {
                    StationDeskManager.stopRecordingFromStation()
                }
            }

            recordActionRow.addView(startRecBtn)
            recordActionRow.addView(stopRecBtn)
            addView(recordActionRow)
        }
    }

    // ==========================================
    // SECTION 3: ROI / DETECTOR
    // ==========================================
    private var cachedOverlayFrameBitmap: Bitmap? = null
    private var cachedCropPreviewBitmap: Bitmap? = null

    private fun convertFrameToBitmap(frame: DetectionFrame?): Bitmap? {
        if (frame == null) return null
        val buf = frame.buffer ?: return null
        val expectedSize = frame.width * frame.height * 4
        if (frame.width <= 0 || frame.height <= 0 || buf.size < expectedSize) return null
        return try {
            var bmp = cachedOverlayFrameBitmap
            if (bmp == null || bmp.width != frame.width || bmp.height != frame.height || bmp.isRecycled) {
                bmp = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                cachedOverlayFrameBitmap = bmp
            }
            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(buf, 0, expectedSize))
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun createRoiDetectorSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dpToPx(4f), 0, dpToPx(4f))

            val titleView = TextView(context).apply {
                text = "ROI EDITOR & DETECTOR MONITOR"
                setTextColor(Color.parseColor("#FF00F0FF"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dpToPx(6f))
            }
            addView(titleView)

            roiOverlayCanvasView = RoiOverlayCanvasView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(130f)
                ).apply {
                    bottomMargin = dpToPx(8f)
                }
                onRoiChanged = { updatedRoi ->
                    scope.launch {
                        com.example.services.roi.InMemoryRoiConfigurationService.getInstance().saveRoi(updatedRoi)
                    }
                }
            }
            addView(roiOverlayCanvasView)

            val previewTitle = TextView(context).apply {
                text = "CROP PREVIEW (KILL_FEED):"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dpToPx(2f))
            }
            addView(previewTitle)

            cropPreviewImageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(40f)
                ).apply {
                    bottomMargin = dpToPx(8f)
                }
                setBackgroundColor(Color.parseColor("#FF0F172A"))
                scaleType = ImageView.ScaleType.FIT_XY
            }
            addView(cropPreviewImageView)

            // Dedicated In-Game Cropper Overlay Controls (ROI EDIT ON / SAVE & HIDE / ROI EDIT OFF)
            val cropperControlsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dpToPx(6f))
            }

            val roiEditOnBtn = Button(context).apply {
                text = "ROI EDIT ON"
                setBackgroundColor(Color.parseColor("#FF0284C7")) // Cyan/Blue
                setTextColor(Color.WHITE)
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(34f), 1f)
                setOnClickListener {
                    FloatingRoiScreenCropper.startEdit(context) { savedRoi ->
                        scope.launch {
                            com.example.services.roi.InMemoryRoiConfigurationService.getInstance().saveRoi(savedRoi)
                        }
                    }
                }
            }

            val roiSaveHideBtn = Button(context).apply {
                text = "SAVE & HIDE"
                setBackgroundColor(Color.parseColor("#FF10B981")) // Emerald Green
                setTextColor(Color.WHITE)
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(34f), 1f).apply {
                    leftMargin = dpToPx(4f)
                    rightMargin = dpToPx(4f)
                }
                setOnClickListener {
                    FloatingRoiScreenCropper.saveAndHide()
                }
            }

            val roiEditOffBtn = Button(context).apply {
                text = "ROI EDIT OFF"
                setBackgroundColor(Color.parseColor("#FF475569")) // Slate Gray
                setTextColor(Color.parseColor("#FFEF4444")) // Red
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(34f), 1f)
                setOnClickListener {
                    FloatingRoiScreenCropper.stopEdit()
                }
            }

            cropperControlsRow.addView(roiEditOnBtn)
            cropperControlsRow.addView(roiSaveHideBtn)
            cropperControlsRow.addView(roiEditOffBtn)
            addView(cropperControlsRow)

            val roiBtnsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dpToPx(6f))
            }

            val roiToggleBtn = Button(context).apply {
                text = "TOGGLE ROI"
                setBackgroundColor(Color.parseColor("#FF334155"))
                setTextColor(Color.WHITE)
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f)
                setOnClickListener {
                    scope.launch {
                        val current = com.example.services.roi.InMemoryRoiConfigurationService.getInstance().selectedRoi.value
                        if (current != null) {
                            com.example.services.roi.InMemoryRoiConfigurationService.getInstance().toggleRoiEnabled(current.id)
                        }
                    }
                }
            }

            val roiResetBtn = Button(context).apply {
                text = "RESET ROI"
                setBackgroundColor(Color.parseColor("#FF334155"))
                setTextColor(Color.WHITE)
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f).apply {
                    leftMargin = dpToPx(6f)
                }
                setOnClickListener {
                    scope.launch {
                        com.example.services.roi.InMemoryRoiConfigurationService.getInstance().resetToDefaults()
                    }
                }
            }

            roiBtnsRow.addView(roiToggleBtn)
            roiBtnsRow.addView(roiResetBtn)
            addView(roiBtnsRow)

            val aiAssistRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dpToPx(6f))
            }

            aiAssistToggleBtn = Button(context).apply {
                val isAiOn = com.example.services.detection.pubg.PUBGVisualAIAssistant.getInstance().isAiAssistEnabled.value
                text = if (isAiOn) "AI ASSIST: ON" else "AI ASSIST: OFF"
                setBackgroundColor(if (isAiOn) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))
                setTextColor(Color.WHITE)
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(32f))
                setOnClickListener {
                    val next = com.example.services.detection.pubg.PUBGVisualAIAssistant.getInstance().toggleAiAssist()
                    text = if (next) "AI ASSIST: ON" else "AI ASSIST: OFF"
                    setBackgroundColor(if (next) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))
                }
            }
            aiAssistRow.addView(aiAssistToggleBtn)
            addView(aiAssistRow)

            val rRoiStatus = createRow("ROI Status:", "ACTIVE")
            roiStatusVal = rRoiStatus.valueView
            addView(rRoiStatus.rowView)

            val rAiAssistStatus = createRow("AI Assist Engine:", "OFF (OCR only)")
            detAiAssistVal = rAiAssistStatus.valueView
            addView(rAiAssistStatus.rowView)

            val rRoiCoords = createRow("ROI Coords:", "X: 0.60 | Y: 0.04 | W: 0.38 | H: 0.22")
            roiCoordsVal = rRoiCoords.valueView
            addView(rRoiCoords.rowView)

            val rDetEngine = createRow("Detector Engine:", "RUNNING")
            detEngineVal = rDetEngine.valueView
            addView(rDetEngine.rowView)

            val rDetFrames = createRow("Live Frame Stream:", "RECEIVING")
            detFramesVal = rDetFrames.valueView
            addView(rDetFrames.rowView)

            val rDetLastProc = createRow("Last Processed Time:", "N/A")
            detLastProcVal = rDetLastProc.valueView
            addView(rDetLastProc.rowView)

            val rDetConfidence = createRow("Detection Confidence:", "N/A")
            detConfidenceVal = rDetConfidence.valueView
            addView(rDetConfidence.rowView)

            val rDetEventType = createRow("Latest Event Type:", "None")
            detEventTypeVal = rDetEventType.valueView
            addView(rDetEventType.rowView)

            val rDetAdminReview = createRow("Admin Review Routing:", "N/A")
            detAdminReviewVal = rDetAdminReview.valueView
            addView(rDetAdminReview.rowView)
        }
    }

    private fun scanLocalMusicFiles(): List<Pair<String, Uri>> {
        val list = mutableListOf<Pair<String, Uri>>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: c.getString(nameCol) ?: "Track $id"
                    val contentUri = android.content.ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    list.add(Pair(title, contentUri))
                }
            }
        } catch (_: Exception) {}

        if (list.isEmpty()) {
            list.add(Pair("No local music files found", Uri.EMPTY))
        }
        return list
    }

    // ==========================================
    // SECTION 4: AUDIO STUDIO
    // ==========================================
    private fun createAudioStudioSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dpToPx(4f), 0, dpToPx(4f))

            // Section Header
            val titleText = TextView(context).apply {
                text = "AUDIO STUDIO & PCM MIXER"
                setTextColor(Color.parseColor("#FF00F0FF"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dpToPx(6f))
            }
            addView(titleText)

            val currentMixer = AudioMixerManager.mixerState.value

            // 1. GAME AUDIO CHANNEL
            val gameHeaderRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))
                val lbl = TextView(context).apply {
                    text = "GAME AUDIO VOLUME"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                gameVolText = TextView(context).apply {
                    text = "${(currentMixer.gameVolume * 100).toInt()}%"
                    setTextColor(Color.parseColor("#FF22C55E"))
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                }
                addView(lbl)
                addView(gameVolText)
            }
            addView(gameHeaderRow)

            val gameCtrlRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dpToPx(6f))

                gameVolSeekBar = SeekBar(context).apply {
                    max = 100
                    progress = (currentMixer.gameVolume * 100).toInt()
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                            if (fromUser) AudioMixerManager.setGameVolume(p / 100f)
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                }

                gameMuteToggleBtn = Button(context).apply {
                    text = if (currentMixer.gameMuted) "UNMUTE GAME" else "MUTE GAME"
                    setBackgroundColor(if (currentMixer.gameMuted) Color.parseColor("#FFDC2626") else Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(100f), dpToPx(32f)).apply {
                        leftMargin = dpToPx(6f)
                    }
                    setOnClickListener {
                        AudioMixerManager.toggleGameMute()
                    }
                }

                addView(gameVolSeekBar)
                addView(gameMuteToggleBtn)
            }
            addView(gameCtrlRow)

            // 2. MICROPHONE CHANNEL
            val micHeaderRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))
                val lbl = TextView(context).apply {
                    text = "MICROPHONE VOLUME"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                micVolText = TextView(context).apply {
                    text = "${(currentMixer.micVolume * 100).toInt()}%"
                    setTextColor(Color.parseColor("#FF22C55E"))
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                }
                addView(lbl)
                addView(micVolText)
            }
            addView(micHeaderRow)

            val micCtrlRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dpToPx(2f))

                micVolSeekBar = SeekBar(context).apply {
                    max = 100
                    progress = (currentMixer.micVolume * 100).toInt()
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                            if (fromUser) AudioMixerManager.setMicVolume(p / 100f)
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                }

                micMuteToggleBtn = Button(context).apply {
                    text = if (currentMixer.micMuted) "UNMUTE MIC" else "MUTE MIC"
                    setBackgroundColor(if (currentMixer.micMuted) Color.parseColor("#FFDC2626") else Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(100f), dpToPx(32f)).apply {
                        leftMargin = dpToPx(6f)
                    }
                    setOnClickListener {
                        AudioMixerManager.toggleMicMute()
                    }
                }

                addView(micVolSeekBar)
                addView(micMuteToggleBtn)
            }
            addView(micCtrlRow)

            val micStartRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dpToPx(6f))

                micStartStopToggleBtn = Button(context).apply {
                    val isRec = MicrophoneCommentaryManager.micState.value.isRecording
                    text = if (isRec) "STOP MIC" else "START MIC"
                    setBackgroundColor(if (isRec) Color.parseColor("#FFDC2626") else Color.parseColor("#FF0284C7"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(30f))
                    setOnClickListener {
                        if (MicrophoneCommentaryManager.micState.value.isRecording) {
                            MicrophoneCommentaryManager.stopMicrophone()
                        } else {
                            MicrophoneCommentaryManager.startMicrophone(context)
                        }
                    }
                }
                addView(micStartStopToggleBtn)
            }
            addView(micStartRow)

            // 3. MUSIC CHANNEL
            val musicHeaderRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))
                val lbl = TextView(context).apply {
                    text = "MUSIC VOLUME"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                musicVolText = TextView(context).apply {
                    text = "${(currentMixer.musicVolume * 100).toInt()}%"
                    setTextColor(Color.parseColor("#FF22C55E"))
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                }
                addView(lbl)
                addView(musicVolText)
            }
            addView(musicHeaderRow)

            val musicCtrlRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dpToPx(6f))

                musicVolSeekBar = SeekBar(context).apply {
                    max = 100
                    progress = (currentMixer.musicVolume * 100).toInt()
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                            if (fromUser) AudioMixerManager.setMusicVolume(p / 100f)
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                }

                musicMuteToggleBtn = Button(context).apply {
                    text = if (currentMixer.musicMuted) "UNMUTE MUSIC" else "MUTE MUSIC"
                    setBackgroundColor(if (currentMixer.musicMuted) Color.parseColor("#FFDC2626") else Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(100f), dpToPx(32f)).apply {
                        leftMargin = dpToPx(6f)
                    }
                    setOnClickListener {
                        AudioMixerManager.toggleMusicMute()
                    }
                }

                addView(musicVolSeekBar)
                addView(musicMuteToggleBtn)
            }
            addView(musicCtrlRow)

            // 4. MUSIC SELECTION & PLAYER CONTROLS
            val musicSectionTitle = TextView(context).apply {
                text = "LOCAL MUSIC PLAYER"
                setTextColor(Color.parseColor("#FF38BDF8"))
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(4f), 0, dpToPx(2f))
            }
            addView(musicSectionTitle)

            localMusicTracks.clear()
            localMusicTracks.addAll(scanLocalMusicFiles())

            val trackTitles = localMusicTracks.map { it.first }
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, trackTitles).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            musicTrackSpinner = Spinner(context).apply {
                setAdapter(adapter)
                setBackgroundColor(Color.parseColor("#FF1E293B"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(36f)).apply {
                    bottomMargin = dpToPx(6f)
                }
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position in localMusicTracks.indices) {
                            val track = localMusicTracks[position]
                            if (track.second != Uri.EMPTY) {
                                LocalMusicPlayerManager.loadAndPlay(context, track.second, track.first)
                            }
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
            addView(musicTrackSpinner)

            val musicBtnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dpToPx(8f))

                musicPlayPauseToggleBtn = Button(context).apply {
                    val isPlaying = LocalMusicPlayerManager.musicState.value.isPlaying
                    text = if (isPlaying) "PAUSE" else "PLAY"
                    setBackgroundColor(if (isPlaying) Color.parseColor("#FFF59E0B") else Color.parseColor("#FF22C55E"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(30f), 1f)
                    setOnClickListener {
                        val state = LocalMusicPlayerManager.musicState.value
                        if (state.isPlaying) {
                            LocalMusicPlayerManager.pause()
                        } else {
                            if (state.mediaUri != null) {
                                LocalMusicPlayerManager.resume()
                            } else {
                                val selectedPos = musicTrackSpinner.selectedItemPosition
                                if (selectedPos in localMusicTracks.indices) {
                                    val track = localMusicTracks[selectedPos]
                                    if (track.second != Uri.EMPTY) {
                                        LocalMusicPlayerManager.loadAndPlay(context, track.second, track.first)
                                    }
                                }
                            }
                        }
                    }
                }

                musicLoopToggleBtn = Button(context).apply {
                    val isLoop = LocalMusicPlayerManager.musicState.value.isLooping
                    text = if (isLoop) "LOOP: ON" else "LOOP: OFF"
                    setBackgroundColor(Color.parseColor("#FF334155"))
                    setTextColor(if (isLoop) Color.parseColor("#FF38BDF8") else Color.parseColor("#FF94A3B8"))
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(30f), 1f).apply {
                        leftMargin = dpToPx(4f)
                    }
                    setOnClickListener {
                        LocalMusicPlayerManager.toggleLoop()
                    }
                }

                musicClearToggleBtn = Button(context).apply {
                    text = "CLEAR"
                    setBackgroundColor(Color.parseColor("#FF7F1D1D"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(30f), 1f).apply {
                        leftMargin = dpToPx(4f)
                    }
                    setOnClickListener {
                        LocalMusicPlayerManager.clear()
                    }
                }

                addView(musicPlayPauseToggleBtn)
                addView(musicLoopToggleBtn)
                addView(musicClearToggleBtn)
            }
            addView(musicBtnRow)

            // 5. REAL AUDIO & ENCODER TELEMETRY
            val rGame = createRow("Game Capture:", "STOPPED")
            audGameCapVal = rGame.valueView
            addView(rGame.rowView)

            val rMic = createRow("Mic Status:", "STOPPED")
            audMicCapVal = rMic.valueView
            addView(rMic.rowView)

            val rMus = createRow("Music Status:", "No music loaded")
            audMusicPlayVal = rMus.valueView
            addView(rMus.rowView)

            val rEnc = createRow("AAC Encoder:", "AAC-LC 44.1kHz Stereo @ 128kbps")
            audEncoderVal = rEnc.valueView
            addView(rEnc.rowView)

            val rMix = createRow("PCM Matrix:", "3-Ch PCM Matrix (G:100% | M:100% | BGM:100%)")
            audPcmMixerVal = rMix.valueView
            addView(rMix.rowView)
        }
    }

    // ==========================================
    // SECTION 5: BROADCAST GRAPHICS (STANDING TABLE & BOTTOM TICKER)
    // ==========================================
    private fun createBroadcastGraphicsSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dpToPx(4f), 0, dpToPx(4f))

            val titleText = TextView(context).apply {
                text = "BROADCAST GRAPHICS & TICKER CONTROLS"
                setTextColor(Color.parseColor("#FF00F0FF"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dpToPx(6f))
            }
            addView(titleText)

            val state = StandingTableControlsManager.controlsState.value

            // Subheader 1: Standing Table
            val standingHeader = TextView(context).apply {
                text = "--- STANDING TABLE OVERLAY ---"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(2f), 0, dpToPx(4f))
            }
            addView(standingHeader)

            // 1. Standing Table ON/OFF
            val standingRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "STANDING TABLE OVERLAY"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                standingTableToggleBtn = Button(context).apply {
                    text = if (state.scoreboardEnabled) "ON" else "OFF"
                    setBackgroundColor(if (state.scoreboardEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FFDC2626"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        StandingTableControlsManager.toggleScoreboard()
                    }
                }

                addView(lbl)
                addView(standingTableToggleBtn)
            }
            addView(standingRow)

            // 2. Top 3 Mode ON/OFF
            val top3Row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "TOP 3 MODE"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                top3ModeToggleBtn = Button(context).apply {
                    text = if (state.top3ModeEnabled) "ON" else "OFF"
                    setBackgroundColor(if (state.top3ModeEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        StandingTableControlsManager.toggleTop3Mode()
                    }
                }

                addView(lbl)
                addView(top3ModeToggleBtn)
            }
            addView(top3Row)

            // 3. Full Standings Mode ON/OFF
            val fullStandingsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "FULL STANDINGS (16 TEAMS)"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                fullStandingsToggleBtn = Button(context).apply {
                    text = if (state.fullStandingsEnabled) "ON" else "OFF"
                    setBackgroundColor(if (state.fullStandingsEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        StandingTableControlsManager.toggleFullStandings()
                    }
                }

                addView(lbl)
                addView(fullStandingsToggleBtn)
            }
            addView(fullStandingsRow)

            // 3a. Standing Table Size Control
            val standingSizeRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "STANDING TABLE SIZE"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                standingSizeBtn = Button(context).apply {
                    text = state.standingSize
                    setBackgroundColor(Color.parseColor("#FF0284C7"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        val current = StandingTableControlsManager.controlsState.value.standingSize
                        val next = when (current) {
                            "Small" -> "Medium"
                            "Medium" -> "Large"
                            else -> "Small"
                        }
                        StandingTableControlsManager.setStandingSize(next)
                    }
                }

                addView(lbl)
                addView(standingSizeBtn)
            }
            addView(standingSizeRow)

            // 3b. Standing Table Position Control
            val standingPosRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(4f))

                val lbl = TextView(context).apply {
                    text = "STANDING POSITION"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                standingPosBtn = Button(context).apply {
                    text = state.standingPosition
                    setBackgroundColor(Color.parseColor("#FF0284C7"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        val current = StandingTableControlsManager.controlsState.value.standingPosition
                        val next = when (current) {
                            "Top Right" -> "Top Left"
                            "Top Left" -> "Bottom Left"
                            "Bottom Left" -> "Bottom Right"
                            else -> "Top Right"
                        }
                        StandingTableControlsManager.setStandingPosition(next)
                    }
                }

                addView(lbl)
                addView(standingPosBtn)
            }
            addView(standingPosRow)

            // Standing Telemetry & Status Rows
            val r1 = createRow("Scoreboard State:", "ENABLED")
            standingOverlayStatusVal = r1.valueView
            addView(r1.rowView)

            val r2 = createRow("Display Format:", "FULL (16 Teams)")
            top3ModeStatusVal = r2.valueView
            addView(r2.rowView)

            // Subheader 2: Bottom Ticker Controls
            val tickerHeader = TextView(context).apply {
                text = "--- BOTTOM TICKER OVERLAY ---"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(8f), 0, dpToPx(4f))
            }
            addView(tickerHeader)

            // 4. Bottom Ticker ON/OFF
            val tickerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "BOTTOM TICKER"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                bottomTickerToggleBtn = Button(context).apply {
                    text = if (state.bottomTickerEnabled) "ON" else "OFF"
                    setBackgroundColor(if (state.bottomTickerEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FFDC2626"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        StandingTableControlsManager.toggleBottomTicker()
                    }
                }

                addView(lbl)
                addView(bottomTickerToggleBtn)
            }
            addView(tickerRow)

            // 5. Ticker Speed Toggle
            val tickerSpeedRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "TICKER SPEED"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                tickerSpeedToggleBtn = Button(context).apply {
                    text = "${state.tickerSpeedMultiplier}x"
                    setBackgroundColor(Color.parseColor("#FF0284C7"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        val nextSpeed = when (StandingTableControlsManager.controlsState.value.tickerSpeedMultiplier) {
                            1.0f -> 1.5f
                            1.5f -> 2.0f
                            2.0f -> 0.5f
                            else -> 1.0f
                        }
                        StandingTableControlsManager.setTickerSpeed(nextSpeed)
                    }
                }

                addView(lbl)
                addView(tickerSpeedToggleBtn)
            }
            addView(tickerSpeedRow)

            // 5a. Ticker Size (Height) Toggle
            val tickerSizeRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "TICKER SIZE (HEIGHT)"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                tickerSizeBtn = Button(context).apply {
                    text = state.tickerSize
                    setBackgroundColor(Color.parseColor("#FF0284C7"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        val current = StandingTableControlsManager.controlsState.value.tickerSize
                        val next = when (current) {
                            "Small" -> "Medium"
                            "Medium" -> "Large"
                            else -> "Small"
                        }
                        StandingTableControlsManager.setTickerSize(next)
                    }
                }

                addView(lbl)
                addView(tickerSizeBtn)
            }
            addView(tickerSizeRow)

            // 5b. Ticker Position (Y-Offset) Toggle
            val tickerPosRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "TICKER POSITION"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                tickerYOffsetBtn = Button(context).apply {
                    val currentOffset = state.tickerYOffset
                    text = when (currentOffset) {
                        0.94f -> "Bottom"
                        0.85f -> "Lower Third"
                        0.05f -> "Top Layout"
                        else -> "Custom"
                    }
                    setBackgroundColor(Color.parseColor("#FF0284C7"))
                    setTextColor(Color.WHITE)
                    textSize = 8f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        val current = StandingTableControlsManager.controlsState.value.tickerYOffset
                        val next = when (current) {
                            0.94f -> 0.85f
                            0.85f -> 0.05f
                            else -> 0.94f
                        }
                        StandingTableControlsManager.setTickerYOffset(next)
                    }
                }

                addView(lbl)
                addView(tickerYOffsetBtn)
            }
            addView(tickerPosRow)

            // 5c. Custom Ticker Text Entry
            val customTextLabel = TextView(context).apply {
                text = "CUSTOM TICKER TEXT ENTER:"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 8f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(4f), 0, dpToPx(2f))
            }
            addView(customTextLabel)

            val tickerInputRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val editTxt = android.widget.EditText(context).apply {
                    hint = "Enter Custom Ticker Text"
                    setText(state.tickerCustomText)
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.GRAY)
                    textSize = 10f
                    setBackgroundColor(Color.parseColor("#FF1E293B"))
                    setPadding(dpToPx(8f), dpToPx(6f), dpToPx(8f), dpToPx(6f))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                        rightMargin = dpToPx(4f)
                    }
                }

                tickerCustomTextBtn = Button(context).apply {
                    text = "SET"
                    setBackgroundColor(Color.parseColor("#FF16A34A"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(60f), dpToPx(32f))
                    setOnClickListener {
                        StandingTableControlsManager.setTickerCustomText(editTxt.text.toString())
                    }
                }

                addView(editTxt)
                addView(tickerCustomTextBtn)
            }
            addView(tickerInputRow)

            // 5d. Custom Loop Video for Bottom Ticker Background
            val videoLoopRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(4f))

                val lbl = TextView(context).apply {
                    text = "TICKER VIDEO LOOP BG"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                tickerLoopVideoSelectBtn = Button(context).apply {
                    val hasVideo = state.tickerLoopVideoUri != null
                    text = if (hasVideo) "VIDEO SELECTED" else "NO VIDEO SELECTED"
                    setBackgroundColor(if (hasVideo) Color.parseColor("#FF1E293B") else Color.parseColor("#FF0284C7"))
                    setTextColor(Color.WHITE)
                    textSize = 8f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(100f), dpToPx(32f)).apply {
                        rightMargin = dpToPx(4f)
                    }
                    setOnClickListener {
                        com.example.platform.android.LocalVideoPickerActivity.launch(context)
                    }
                }

                tickerLoopVideoBtn = Button(context).apply {
                    val enabled = state.tickerLoopVideoEnabled && state.tickerLoopVideoUri != null
                    text = if (enabled) "ON" else "OFF"
                    setBackgroundColor(if (enabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FFDC2626"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(50f), dpToPx(32f))
                    setOnClickListener {
                        val curr = StandingTableControlsManager.controlsState.value
                        if (curr.tickerLoopVideoUri == null) {
                            com.example.platform.android.LocalVideoPickerActivity.launch(context)
                        } else {
                            StandingTableControlsManager.setTickerLoopVideo(curr.tickerLoopVideoUri, !curr.tickerLoopVideoEnabled)
                        }
                    }
                }

                addView(lbl)
                addView(tickerLoopVideoSelectBtn)
                addView(tickerLoopVideoBtn)
            }
            addView(videoLoopRow)

            // Ticker Telemetry & Status Row
            val rTicker = createRow("Ticker Status:", "ACTIVE (SCROLLING)")
            tickerOverlayStatusVal = rTicker.valueView
            addView(rTicker.rowView)

            // Subheader 3: VIP Milestone Cards Controls
            val vipHeader = TextView(context).apply {
                text = "--- VIP MILESTONE CARDS ---"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(8f), 0, dpToPx(4f))
            }
            addView(vipHeader)

            // 6. VIP Kill Card ON/OFF
            val vipKillRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "VIP KILL CARD"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                vipKillCardToggleBtn = Button(context).apply {
                    text = if (state.killCardEnabled) "ON" else "OFF"
                    setBackgroundColor(if (state.killCardEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FFDC2626"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        StandingTableControlsManager.toggleKillCard()
                    }
                }

                addView(lbl)
                addView(vipKillCardToggleBtn)
            }
            addView(vipKillRow)

            // 7. VIP Point Card ON/OFF
            val vipPointRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(4f))

                val lbl = TextView(context).apply {
                    text = "VIP POINT CARD"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                vipPointCardToggleBtn = Button(context).apply {
                    text = if (state.milestoneCardEnabled) "ON" else "OFF"
                    setBackgroundColor(if (state.milestoneCardEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FFDC2626"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        StandingTableControlsManager.toggleMilestoneCard()
                    }
                }

                addView(lbl)
                addView(vipPointCardToggleBtn)
            }
            addView(vipPointRow)

            // VIP Milestones Telemetry Rows
            val rVipKill = createRow("VIP Kill Card Status:", "ACTIVE (5, 10, 15... KILLS)")
            vipKillCardStatusVal = rVipKill.valueView
            addView(rVipKill.rowView)

            val rVipPoint = createRow("VIP Point Card Status:", "ACTIVE (20, 50, 80... PTS)")
            vipPointCardStatusVal = rVipPoint.valueView
            addView(rVipPoint.rowView)

            // Subheader 4: Custom Graphics Controls
            val customGraphicsHeader = TextView(context).apply {
                text = "--- CUSTOM GRAPHICS OVERLAYS ---"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(8f), 0, dpToPx(4f))
            }
            addView(customGraphicsHeader)

            // Custom Graphics Master Switch
            val customMasterRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "CUSTOM GRAPHICS MASTER"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                customGraphicsMasterBtn = Button(context).apply {
                    text = if (state.customGraphicsEnabled) "ON" else "OFF"
                    setBackgroundColor(if (state.customGraphicsEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FFDC2626"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        StandingTableControlsManager.toggleCustomGraphics()
                    }
                }

                addView(lbl)
                addView(customGraphicsMasterBtn)
            }
            addView(customMasterRow)

            // Custom Logo Toggle
            val customLogoRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "LOGO OVERLAY"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                customLogoBtn = Button(context).apply {
                    text = if (state.logoOverlayEnabled) "ON" else "OFF"
                    setBackgroundColor(if (state.logoOverlayEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        StandingTableControlsManager.toggleLogoOverlay()
                    }
                }

                addView(lbl)
                addView(customLogoBtn)
            }
            addView(customLogoRow)

            // Custom Text Overlay Toggle
            val customTextRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "TEXT OVERLAY"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                customTextBtn = Button(context).apply {
                    text = if (state.textOverlayEnabled) "ON" else "OFF"
                    setBackgroundColor(if (state.textOverlayEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        StandingTableControlsManager.toggleTextOverlay()
                    }
                }

                addView(lbl)
                addView(customTextBtn)
            }
            addView(customTextRow)

            // Custom Match Info Overlay Toggle
            val customMatchInfoRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(2f))

                val lbl = TextView(context).apply {
                    text = "MATCH INFORMATION"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                customMatchInfoBtn = Button(context).apply {
                    text = if (state.matchInfoOverlayEnabled) "ON" else "OFF"
                    setBackgroundColor(if (state.matchInfoOverlayEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        StandingTableControlsManager.toggleMatchInfoOverlay()
                    }
                }

                addView(lbl)
                addView(customMatchInfoBtn)
            }
            addView(customMatchInfoRow)

            // Custom Team/Player Showcase Toggle
            val customTeamShowcaseRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2f), 0, dpToPx(4f))

                val lbl = TextView(context).apply {
                    text = "TEAM/PLAYER SHOWCASE"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                customTeamShowcaseBtn = Button(context).apply {
                    text = if (state.teamPlayerOverlayEnabled) "ON" else "OFF"
                    setBackgroundColor(if (state.teamPlayerOverlayEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(80f), dpToPx(32f))
                    setOnClickListener {
                        StandingTableControlsManager.toggleTeamPlayerOverlay()
                    }
                }

                addView(lbl)
                addView(customTeamShowcaseBtn)
            }
            addView(customTeamShowcaseRow)

            // Live Runtime Summary
            val r3 = createRow("Live Runtime Teams:", "0 Teams")
            liveTeamsCountVal = r3.valueView
            addView(r3.rowView)

            val infoText = TextView(context).apply {
                text = "• Controls directly toggle Standing Table, Ticker & VIP Milestone Cards in live RTMP composite video\n• Floating controls remain private and never leak into broadcast video"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 10f
                setPadding(0, dpToPx(8f), 0, 0)
            }
            addView(infoText)
        }
    }

    // ==========================================
    // SECTION 6: SCENE STUDIO
    // ==========================================
    private fun createSceneStudioSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dpToPx(4f), 0, dpToPx(4f))

            val titleText = TextView(context).apply {
                text = "BROADCAST SCENE STUDIO"
                setTextColor(Color.parseColor("#FF00F0FF"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dpToPx(6f))
            }
            addView(titleText)

            // Current Scene / Scene Status Row
            val statusRow = createRow("Scene Status:", "IDLE")
            sceneEngineStatusVal = statusRow.valueView
            sceneEngineStatusVal.setTextColor(Color.parseColor("#FFFF6600"))
            addView(statusRow.rowView)

            // Scene Transition Buttons Label
            val transitionLabel = TextView(context).apply {
                text = "TRANSITION TO SCENE:"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(8f), 0, dpToPx(4f))
            }
            addView(transitionLabel)

            // 1. STARTING COUNTDOWN & LIVE MATCH
            val row1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dpToPx(4f))
                
                startingCountdownBtn = Button(context).apply {
                    text = "COUNTDOWN"
                    setBackgroundColor(Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f).apply {
                        rightMargin = dpToPx(4f)
                    }
                    setOnClickListener {
                        EsportsSceneEngine.transitionTo(EsportsScene.STARTING_COUNTDOWN)
                    }
                }

                liveMatchBtn = Button(context).apply {
                    text = "LIVE MATCH"
                    setBackgroundColor(Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f)
                    setOnClickListener {
                        EsportsSceneEngine.transitionTo(EsportsScene.LIVE_MATCH)
                    }
                }
                addView(startingCountdownBtn)
                addView(liveMatchBtn)
            }
            addView(row1)

            // 2. BREAK & NEXT MATCH
            val row2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dpToPx(4f))

                breakBtn = Button(context).apply {
                    text = "BREAK"
                    setBackgroundColor(Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f).apply {
                        rightMargin = dpToPx(4f)
                    }
                    setOnClickListener {
                        EsportsSceneEngine.transitionTo(EsportsScene.BREAK)
                    }
                }

                nextMatchCountdownBtn = Button(context).apply {
                    text = "NEXT MATCH"
                    setBackgroundColor(Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f)
                    setOnClickListener {
                        EsportsSceneEngine.transitionTo(EsportsScene.NEXT_MATCH_COUNTDOWN)
                    }
                }
                addView(breakBtn)
                addView(nextMatchCountdownBtn)
            }
            addView(row2)

            // 3. MATCH END & ENDING
            val row3 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dpToPx(4f))

                matchEndBtn = Button(context).apply {
                    text = "MATCH END"
                    setBackgroundColor(Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f).apply {
                        rightMargin = dpToPx(4f)
                    }
                    setOnClickListener {
                        EsportsSceneEngine.transitionTo(EsportsScene.MATCH_ENDED)
                    }
                }

                endingBtn = Button(context).apply {
                    text = "ENDING"
                    setBackgroundColor(Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f)
                    setOnClickListener {
                        EsportsSceneEngine.transitionTo(EsportsScene.ENDING)
                    }
                }
                addView(matchEndBtn)
                addView(endingBtn)
            }
            addView(row3)

            // Timer Controls Label
            val timerLabel = TextView(context).apply {
                text = "TIMER ACTION ENGINE:"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dpToPx(8f), 0, dpToPx(4f))
            }
            addView(timerLabel)

            // Timer controls Row
            val timerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dpToPx(4f))

                timerPauseBtn = Button(context).apply {
                    text = "PAUSE"
                    setBackgroundColor(Color.parseColor("#FFF59E0B"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f).apply {
                        rightMargin = dpToPx(4f)
                    }
                    setOnClickListener {
                        EsportsSceneEngine.pauseTimer()
                    }
                }

                timerResumeBtn = Button(context).apply {
                    text = "RESUME"
                    setBackgroundColor(Color.parseColor("#FF16A34A"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f).apply {
                        rightMargin = dpToPx(4f)
                    }
                    setOnClickListener {
                        EsportsSceneEngine.resumeTimer()
                    }
                }

                timerResetBtn = Button(context).apply {
                    text = "RESET"
                    setBackgroundColor(Color.parseColor("#FFDC2626"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(32f), 1f)
                    setOnClickListener {
                        EsportsSceneEngine.resetTimer()
                    }
                }
                addView(timerPauseBtn)
                addView(timerResumeBtn)
                addView(timerResetBtn)
            }
            addView(timerRow)

            val infoText = TextView(context).apply {
                text = "• Scene switches directly overlay in live RTMP composition\n• Background streaming session is completely unaffected by scene shifts\n• Floating controls remain private and never leak into broadcast video"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 10f
                setPadding(0, dpToPx(8f), 0, 0)
            }
            addView(infoText)
        }
    }

    // ==========================================
    // SECTION 7: YOUTUBE LIVE CHAT
    // ==========================================
    private fun createLiveChatSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dpToPx(4f), 0, dpToPx(4f))

            val titleText = TextView(context).apply {
                text = "YOUTUBE LIVE CHAT"
                setTextColor(Color.parseColor("#FF00F0FF"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dpToPx(6f))
            }
            addView(titleText)

            val r1 = createRow("Chat Connection:", "READY")
            chatConnectionStatusVal = r1.valueView
            chatConnectionStatusVal.setTextColor(Color.parseColor("#FF22C55E"))
            addView(r1.rowView)

            val r2 = createRow("Active Stream:", "DISCONNECTED")
            activeChatStreamVal = r2.valueView
            addView(r2.rowView)

            // Scrollable incoming chat message container
            val scrollWrapper = android.widget.ScrollView(context).apply {
                isVerticalScrollBarEnabled = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(130f)
                ).apply {
                    topMargin = dpToPx(6f)
                    bottomMargin = dpToPx(6f)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(6f).toFloat()
                    setColor(Color.parseColor("#FF0F172A"))
                    setStroke(1, Color.parseColor("#FF334155"))
                }
            }

            chatMessagesScrollContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(8f))
            }

            val welcomeMsg = TextView(context).apply {
                text = "Connecting to YouTube Live Chat..."
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 10f
            }
            chatMessagesScrollContainer.addView(welcomeMsg)
            scrollWrapper.addView(chatMessagesScrollContainer)
            addView(scrollWrapper)

            // Chat input row
            val inputRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(2f), 0, dpToPx(4f))

                chatInputEditText = android.widget.EditText(context).apply {
                    hint = "Type reply to stream chat..."
                    setHintTextColor(Color.parseColor("#FF64748B"))
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    setPadding(dpToPx(8f), dpToPx(6f), dpToPx(8f), dpToPx(6f))
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(36f), 1f).apply {
                        rightMargin = dpToPx(4f)
                    }
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(6f).toFloat()
                        setColor(Color.parseColor("#FF1E293B"))
                        setStroke(1, Color.parseColor("#FF475569"))
                    }
                }

                chatSendBtn = Button(context).apply {
                    text = "SEND"
                    setBackgroundColor(Color.parseColor("#FF2563EB"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(64f), dpToPx(36f))
                    setOnClickListener {
                        sendOutgoingChatMessage()
                    }
                }

                addView(chatInputEditText)
                addView(chatSendBtn)
            }
            addView(inputRow)

            // Refresh / Clear Chat Actions Row
            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dpToPx(2f), 0, 0)

                chatRefreshBtn = Button(context).apply {
                    text = "REFRESH CHAT"
                    setBackgroundColor(Color.parseColor("#FF334155"))
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dpToPx(100f), dpToPx(28f))
                    setOnClickListener {
                        pollYouTubeLiveChatNow()
                    }
                }
                addView(chatRefreshBtn)
            }
            addView(actionRow)

            val infoText = TextView(context).apply {
                text = "• Polls real YouTube Live Chat via API when broadcast is active\n• Operator replies send directly to live stream viewers"
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 9f
                setPadding(0, dpToPx(6f), 0, 0)
            }
            addView(infoText)
        }
    }

    // ==========================================
    // SECTION 9: ADMIN REVIEW
    // ==========================================
    private fun createAdminReviewSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dpToPx(4f), 0, dpToPx(4f))

            // Title and Pending Count Badge
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dpToPx(4f))
            }

            val titleText = TextView(context).apply {
                text = "ADMIN REVIEW QUEUE"
                setTextColor(Color.parseColor("#FF00F0FF"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            headerRow.addView(titleText)

            pendingCountText = TextView(context).apply {
                text = "0 PENDING"
                setTextColor(Color.parseColor("#FFFF9800"))
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(6f), dpToPx(2f), dpToPx(6f), dpToPx(2f))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#33FFFF98"))
                    setStroke(dpToPx(1f), Color.parseColor("#FFFF9800"))
                    cornerRadius = dpToPx(4f).toFloat()
                }
            }
            headerRow.addView(pendingCountText)
            addView(headerRow)

            // Subtitle description
            val subtitleText = TextView(context).apply {
                text = "Rule: Events with confidence < 50% require Admin approval. Detections >= 50% auto-process."
                setTextColor(Color.parseColor("#FF94A3B8"))
                textSize = 9f
                setPadding(0, 0, 0, dpToPx(6f))
            }
            addView(subtitleText)

            // Divider Line
            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1f)).apply {
                    bottomMargin = dpToPx(8f)
                }
                setBackgroundColor(Color.parseColor("#FF334155"))
            }
            addView(divider)

            // Empty state view
            noPendingText = TextView(context).apply {
                text = "No pending reviews"
                setTextColor(Color.parseColor("#FF64748B"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(24f), 0, dpToPx(24f))
                visibility = View.VISIBLE
            }
            addView(noPendingText)

            // Event Details Container
            eventDetailsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(0, 0, 0, dpToPx(8f))

                // Event ID
                val r1 = createRow("Event ID:", "-")
                revEventIdVal = r1.valueView
                addView(r1.rowView)

                // Event Type
                val r2 = createRow("Event Type:", "-")
                revEventTypeVal = r2.valueView
                addView(r2.rowView)

                // Killer
                val r3 = createRow("Killer/Player:", "-")
                revKillerVal = r3.valueView
                addView(r3.rowView)

                // Victim
                val r4 = createRow("Victim/Player:", "-")
                revVictimVal = r4.valueView
                addView(r4.rowView)

                // Confidence
                val r5 = createRow("Confidence:", "-")
                revConfidenceVal = r5.valueView
                addView(r5.rowView)

                // Timestamp
                val r6 = createRow("Timestamp:", "-")
                revTimestampVal = r6.valueView
                addView(r6.rowView)

                // Evidence
                val r7 = createRow("Evidence/Status:", "-")
                revEvidenceVal = r7.valueView
                addView(r7.rowView)
            }
            addView(eventDetailsContainer)

            // Visible Error text for Overlay
            reviewErrorText = TextView(context).apply {
                setTextColor(Color.parseColor("#FFEF4444"))
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(4f), 0, dpToPx(4f))
                visibility = View.GONE
            }
            addView(reviewErrorText)

            // Action Buttons Row
            val buttonsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(4f), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            confirmBtn = Button(context).apply {
                text = "YES / CONFIRM"
                setBackgroundColor(Color.parseColor("#FF22C55E"))
                setTextColor(Color.WHITE)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(36f), 1f)
            }

            rejectBtn = Button(context).apply {
                text = "NO / REJECT"
                setBackgroundColor(Color.parseColor("#FFDC2626"))
                setTextColor(Color.WHITE)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(36f), 1f).apply {
                    leftMargin = dpToPx(6f)
                }
            }

            buttonsRow.addView(confirmBtn)
            buttonsRow.addView(rejectBtn)
            addView(buttonsRow)
        }
    }

    private fun updateAdminReviewUI(queue: List<com.example.core.model.EsportsDetectedEvent>) {
        if (!::pendingCountText.isInitialized) return

        pendingCountText.text = "${queue.size} PENDING"

        if (queue.isEmpty()) {
            noPendingText.visibility = View.VISIBLE
            eventDetailsContainer.visibility = View.GONE
            confirmBtn.visibility = View.GONE
            rejectBtn.visibility = View.GONE
            reviewErrorText.visibility = View.GONE
        } else {
            noPendingText.visibility = View.GONE
            eventDetailsContainer.visibility = View.VISIBLE
            confirmBtn.visibility = View.VISIBLE
            rejectBtn.visibility = View.VISIBLE

            val event = queue.first()

            revEventIdVal.text = if (event.id.length > 8) event.id.take(8) else event.id
            revEventTypeVal.text = event.eventType.name
            revKillerVal.text = "${event.killerPlayerName ?: "Unknown"} [${event.killerTeamTag ?: "No Team"}]"
            revVictimVal.text = "${event.victimPlayerName ?: "Unknown"} [${event.victimTeamTag ?: "No Team"}]"
            revConfidenceVal.text = "${(event.confidence * 100).toInt()}%"
            
            val formattedTime = try {
                java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(event.timestampMs))
            } catch (_: Exception) {
                event.timestampMs.toString()
            }
            revTimestampVal.text = formattedTime
            revEvidenceVal.text = event.metadata["explanation"] ?: event.reviewNote ?: "Awaiting Admin Review"

            confirmBtn.setOnClickListener {
                scope.launch {
                    try {
                        com.example.services.event.EventProcessor.getInstance().confirmEvent(event.id, "AdminOverlay")
                        reviewErrorText.visibility = View.GONE
                    } catch (e: Exception) {
                        reviewErrorText.text = "Confirm failed: ${e.message}"
                        reviewErrorText.visibility = View.VISIBLE
                    }
                }
            }

            rejectBtn.setOnClickListener {
                scope.launch {
                    try {
                        com.example.services.event.EventProcessor.getInstance().rejectEvent(event.id, "Admin manual rejection", "AdminOverlay")
                        reviewErrorText.visibility = View.GONE
                    } catch (e: Exception) {
                        reviewErrorText.text = "Reject failed: ${e.message}"
                        reviewErrorText.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    // ==========================================
    // SECTION 8: MORE / SETTINGS
    // ==========================================
    private fun createMoreSettingsSection(params: WindowManager.LayoutParams): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dpToPx(4f), 0, dpToPx(4f))

            val sizeLbl = TextView(context).apply {
                text = "POINTER SIZE PRESET:"
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dpToPx(6f))
            }
            addView(sizeLbl)

            val sizeRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val smallBtn = Button(context).apply {
                text = "SMALL"
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    changePointerSize("SMALL")
                }
            }

            val medBtn = Button(context).apply {
                text = "MEDIUM"
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dpToPx(4f)
                }
                setOnClickListener {
                    changePointerSize("MEDIUM")
                }
            }

            val largeBtn = Button(context).apply {
                text = "LARGE"
                textSize = 10f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dpToPx(4f)
                }
                setOnClickListener {
                    changePointerSize("LARGE")
                }
            }

            sizeRow.addView(smallBtn)
            sizeRow.addView(medBtn)
            sizeRow.addView(largeBtn)
            addView(sizeRow)

            val resetBtn = Button(context).apply {
                text = "RESET OVERLAY POSITION"
                setBackgroundColor(Color.parseColor("#FF334155"))
                setTextColor(Color.WHITE)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(10f)
                }
                setOnClickListener {
                    params.x = 100
                    params.y = 200
                    if (isAttached && overlayContainer != null) {
                        windowManager.updateViewLayout(overlayContainer, params)
                    }
                    prefs.edit().putInt("pointer_last_x", 100).putInt("pointer_last_y", 200).apply()
                }
            }
            addView(resetBtn)
        }
    }

    private fun changePointerSize(size: String) {
        pointerSizePreset = size
        prefs.edit().putString("pointer_size", size).apply()
        pointerView?.let { updatePointerSizeAndStyle(it) }
    }

    private fun createRow(label: String, initialVal: String): RowResult {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(3f), 0, dpToPx(3f))
        }

        val lblText = TextView(context).apply {
            text = label
            setTextColor(Color.parseColor("#FF94A3B8"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
        }

        val valText = TextView(context).apply {
            text = initialVal
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f)
        }

        row.addView(lblText)
        row.addView(valText)
        return RowResult(row, lblText, valText)
    }

    private fun observeSessionState() {
        scope.launch {
            StationDeskManager.stationState.collectLatest { state ->
                updateStationDeskUi(state)
            }
        }

        scope.launch {
            broadcastController.broadcastState.collectLatest {
                updateControlsState()
            }
        }

        scope.launch {
            broadcastController.broadcastError.collectLatest { error ->
                updateErrorDisplay(error ?: youtubeService.errorMessage.value)
            }
        }

        scope.launch {
            youtubeService.sessionState.collectLatest {
                updateControlsState()
            }
        }

        scope.launch {
            youtubeService.errorMessage.collectLatest { ytErr ->
                updateErrorDisplay(broadcastController.broadcastError.value ?: ytErr)
            }
        }

        scope.launch {
            youtubeService.isAuthorized.collectLatest { authorized ->
                val statusText = if (authorized) "CONNECTED" else "NOT CONNECTED"
                val statusColor = if (authorized) Color.parseColor("#FF22C55E") else Color.parseColor("#FFDC2626")

                ytStatusVal.text = statusText
                ytStatusVal.setTextColor(statusColor)

                // YouTube tab connection status
                ytConnectionStatusVal.text = if (authorized) "CONNECTED" else "DISCONNECTED"
                ytConnectionStatusVal.setTextColor(statusColor)

                // Update connect/disconnect button text & styling
                ytConnectBtn.text = if (authorized) "DISCONNECT" else "CONNECT"
                ytConnectBtn.setBackgroundColor(if (authorized) Color.parseColor("#FF64748B") else Color.parseColor("#FF4F46E5"))
            }
        }

        scope.launch {
            youtubeService.channelInfo.collectLatest { info ->
                ytAccountVal.text = info?.title ?: "No Active Account"
            }
        }

        scope.launch {
            youtubeService.currentBroadcast.collectLatest { info ->
                if (::ytIngestVal.isInitialized) {
                    if (info != null) {
                        ytIngestVal.text = if (!info.rtmpIngestUrl.isNullOrBlank()) "RTMP Ingest Active" else "YouTube: ${info.lifeCycleStatus}"
                        ytIngestVal.setTextColor(Color.parseColor("#FF38BDF8"))
                    } else {
                        ytIngestVal.text = "Standby"
                        ytIngestVal.setTextColor(Color.parseColor("#FF94A3B8"))
                    }
                }
            }
        }

        scope.launch {
            com.example.services.event.EventProcessor.getInstance().adminReviewQueue.collectLatest { queue ->
                updateAdminReviewUI(queue)
            }
        }
    }

    private fun updateErrorDisplay(msg: String?) {
        if (!::ytErrorLayout.isInitialized || !::ytErrorVal.isInitialized) return
        if (!msg.isNullOrBlank()) {
            ytErrorVal.text = "ERROR: $msg"
            ytErrorLayout.visibility = View.VISIBLE
        } else {
            ytErrorVal.text = ""
            ytErrorLayout.visibility = View.GONE
        }
    }

    private fun updateControlsState() {
        if (!::ytStartBtn.isInitialized || !::ytPauseResumeBtn.isInitialized || !::ytStopBtn.isInitialized) return

        val bState = broadcastController.broadcastState.value
        val ytState = youtubeService.sessionState.value

        val isError = bState == BroadcastLifecycleState.ERROR || ytState == LiveSessionState.ERROR
        val isLive = !isError && (bState == BroadcastLifecycleState.LIVE || ytState == LiveSessionState.LIVE)
        val isPaused = !isError && bState == BroadcastLifecycleState.PAUSED
        val isStarting = !isError && ytState == LiveSessionState.STARTING
        val isStopping = !isError && ytState == LiveSessionState.STOPPING

        // 1. Text & Status Displays
        val statusName = when {
            isError -> "ERROR"
            isLive -> "LIVE"
            isPaused -> "PAUSED"
            isStarting -> "STARTING"
            isStopping -> "STOPPING"
            bState == BroadcastLifecycleState.READY || ytState == LiveSessionState.READY -> "READY"
            else -> "IDLE"
        }

        liveStatusVal.text = statusName
        ytBroadcastStatusVal.text = statusName

        val statusColor = when {
            isError -> Color.parseColor("#FFEF4444")
            isLive -> Color.parseColor("#FFEF4444")
            isPaused -> Color.parseColor("#FFF59E0B")
            isStarting -> Color.parseColor("#FFEAB308")
            isStopping -> Color.parseColor("#FF64748B")
            statusName == "READY" -> Color.parseColor("#FF60A5FA")
            else -> Color.parseColor("#FF94A3B8")
        }
        liveStatusVal.setTextColor(statusColor)
        ytBroadcastStatusVal.setTextColor(statusColor)

        // 2. Status Badge
        when {
            isError -> {
                ytBroadcastBadgeVal.text = "ERROR"
                ytBroadcastBadgeVal.setTextColor(Color.parseColor("#FFFCA5A5"))
                (ytBroadcastBadgeVal.background as? GradientDrawable)?.setColor(Color.parseColor("#FF991B1B"))
            }
            isLive -> {
                ytBroadcastBadgeVal.text = "● LIVE"
                ytBroadcastBadgeVal.setTextColor(Color.WHITE)
                (ytBroadcastBadgeVal.background as? GradientDrawable)?.setColor(Color.parseColor("#FFDC2626"))
            }
            isPaused -> {
                ytBroadcastBadgeVal.text = "❚❚ PAUSED"
                ytBroadcastBadgeVal.setTextColor(Color.WHITE)
                (ytBroadcastBadgeVal.background as? GradientDrawable)?.setColor(Color.parseColor("#FFD97706"))
            }
            isStarting -> {
                ytBroadcastBadgeVal.text = "STARTING..."
                ytBroadcastBadgeVal.setTextColor(Color.WHITE)
                (ytBroadcastBadgeVal.background as? GradientDrawable)?.setColor(Color.parseColor("#FFEAB308"))
            }
            isStopping -> {
                ytBroadcastBadgeVal.text = "STOPPING..."
                ytBroadcastBadgeVal.setTextColor(Color.WHITE)
                (ytBroadcastBadgeVal.background as? GradientDrawable)?.setColor(Color.parseColor("#FF64748B"))
            }
            statusName == "READY" -> {
                ytBroadcastBadgeVal.text = "READY"
                ytBroadcastBadgeVal.setTextColor(Color.parseColor("#FF93C5FD"))
                (ytBroadcastBadgeVal.background as? GradientDrawable)?.setColor(Color.parseColor("#FF1E3A8A"))
            }
            else -> {
                ytBroadcastBadgeVal.text = "IDLE"
                ytBroadcastBadgeVal.setTextColor(Color.parseColor("#FF94A3B8"))
                (ytBroadcastBadgeVal.background as? GradientDrawable)?.setColor(Color.parseColor("#FF334155"))
            }
        }

        // 3. Buttons enablement & visibility
        if (isActionInProgress) {
            ytStartBtn.isEnabled = false
            ytPauseResumeBtn.isEnabled = false
            ytStopBtn.isEnabled = false
        } else {
            ytStartBtn.isEnabled = !isLive && !isPaused && !isStarting && !isStopping
            ytPauseResumeBtn.isEnabled = isLive || isPaused
            ytStopBtn.isEnabled = isLive || isPaused || isStarting
        }

        if (isLive) {
            ytStartBtn.visibility = View.GONE
            ytPauseResumeBtn.visibility = View.VISIBLE
            ytPauseResumeBtn.text = "PAUSE"
            ytPauseResumeBtn.setBackgroundColor(Color.parseColor("#FFD97706"))
            ytStopBtn.visibility = View.VISIBLE
        } else if (isPaused) {
            ytStartBtn.visibility = View.GONE
            ytPauseResumeBtn.visibility = View.VISIBLE
            ytPauseResumeBtn.text = "RESUME"
            ytPauseResumeBtn.setBackgroundColor(Color.parseColor("#FF2563EB"))
            ytStopBtn.visibility = View.VISIBLE
        } else {
            ytStartBtn.visibility = View.VISIBLE
            ytStartBtn.text = if (isError) "RETRY START" else "START LIVE"
            ytStartBtn.setBackgroundColor(if (isError) Color.parseColor("#FFEA580C") else Color.parseColor("#FF16A34A"))
            ytPauseResumeBtn.visibility = View.GONE
            ytStopBtn.visibility = if (isStarting) View.VISIBLE else View.GONE
        }
    }

    private fun startTelemetryUpdates() {
        telemetryRunnable = object : Runnable {
            override fun run() {
                val service = ScreenCaptureService.activeInstance
                val active = ScreenCaptureService.isRunning

                // 1. Update Live status views (Tab 1)
                capStatusVal.text = if (active) "ON" else "OFF"
                capStatusVal.setTextColor(if (active) Color.parseColor("#FF22C55E") else Color.parseColor("#FFDC2626"))

                srvStatusVal.text = if (active) "RUNNING" else "STOPPED"
                srvStatusVal.setTextColor(if (active) Color.parseColor("#FF22C55E") else Color.parseColor("#FFDC2626"))

                // Update Duration
                val metrics = pipeline.pipelineMetrics.value
                val isLiveOrPaused = broadcastController.broadcastState.value == BroadcastLifecycleState.LIVE ||
                        broadcastController.broadcastState.value == BroadcastLifecycleState.PAUSED ||
                        youtubeService.sessionState.value == LiveSessionState.LIVE
                val durationSeconds = if (isLiveOrPaused) metrics.uptimeSeconds else 0L
                if (::ytDurationVal.isInitialized) {
                    ytDurationVal.text = formatDuration(durationSeconds)
                }

                // 2. Update Capture Tab values (Tab 3) (Part 12)
                capStatusTextVal.text = if (active) "ON" else "OFF"
                capStatusTextVal.setTextColor(if (active) Color.parseColor("#FF22C55E") else Color.parseColor("#FFDC2626"))

                if (service != null && active) {
                    capResVal.text = "${service.getWidth()} × ${service.getHeight()}"
                    capFpsVal.text = "${service.getFps()} FPS"
                    capFrameCountVal.text = String.format("%,d", service.getFrameCount())
                    capDroppedVal.text = "${service.getDroppedFrames()}"
                    capProjectionVal.text = "ACTIVE"
                    capProjectionVal.setTextColor(Color.parseColor("#FF22C55E"))
                } else {
                    capResVal.text = "CAPTURE OFF"
                    capFpsVal.text = "0 FPS"
                    capFrameCountVal.text = "0"
                    capDroppedVal.text = "0"
                    capProjectionVal.text = "INACTIVE"
                    capProjectionVal.setTextColor(Color.parseColor("#FFDC2626"))
                }

                // 3. Update Monitor Tab values (Tab 4) (Part 13)
                val bState = broadcastController.broadcastState.value
                val ytState = youtubeService.sessionState.value

                val stateName = when {
                    bState == BroadcastLifecycleState.ERROR || ytState == LiveSessionState.ERROR -> "ERROR"
                    bState == BroadcastLifecycleState.LIVE || ytState == LiveSessionState.LIVE -> "LIVE"
                    bState == BroadcastLifecycleState.PAUSED -> "PAUSED"
                    bState == BroadcastLifecycleState.READY || ytState == LiveSessionState.READY -> "READY"
                    ytState == LiveSessionState.STARTING -> "STARTING"
                    else -> "IDLE"
                }
                if (::monBroadcastStateVal.isInitialized) {
                    monBroadcastStateVal.text = stateName
                    monBroadcastStateVal.setTextColor(
                        when (stateName) {
                            "LIVE", "ERROR" -> Color.parseColor("#FFEF4444")
                            "PAUSED" -> Color.parseColor("#FFF59E0B")
                            "STARTING", "READY" -> Color.parseColor("#FF60A5FA")
                            else -> Color.parseColor("#FF94A3B8")
                        }
                    )
                }

                if (::monCaptureVal.isInitialized) {
                    monCaptureVal.text = if (active) "RUNNING" else "STOPPED"
                    monCaptureVal.setTextColor(if (active) Color.parseColor("#FF22C55E") else Color.parseColor("#FFDC2626"))
                }

                val fps = service?.getFps() ?: metrics.currentFps.toInt()
                if (::monFpsVal.isInitialized) {
                    monFpsVal.text = if (active || fps > 0) "$fps FPS" else "N/A"
                    monFpsVal.setTextColor(if (fps > 0) Color.parseColor("#FF38BDF8") else Color.parseColor("#FF94A3B8"))
                }

                if (::monTargetFpsVal.isInitialized) {
                    monTargetFpsVal.text = "${metrics.targetFps} FPS"
                }

                val dropped = service?.getDroppedFrames() ?: 0L
                if (::monDroppedVal.isInitialized) {
                    monDroppedVal.text = "$dropped"
                    monDroppedVal.setTextColor(if (dropped > 0) Color.parseColor("#FFF59E0B") else Color.parseColor("#FF94A3B8"))
                }

                val lastTs = service?.getLastFrameTimestamp() ?: 0L
                if (::monLastFrameVal.isInitialized) {
                    if (lastTs > 0) {
                        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                        monLastFrameVal.text = sdf.format(java.util.Date(lastTs))
                    } else {
                        monLastFrameVal.text = "N/A"
                    }
                }

                if (::monH264Val.isInitialized) {
                    val encRunning = metrics.isEncoderRunning
                    monH264Val.text = if (encRunning) "RUNNING" else "STOPPED"
                    monH264Val.setTextColor(if (encRunning) Color.parseColor("#FF22C55E") else Color.parseColor("#FF94A3B8"))
                }

                if (::monEncodedFramesVal.isInitialized) {
                    val encFrames = metrics.framesEncoded
                    monEncodedFramesVal.text = if (encFrames > 0) String.format("%,d", encFrames) else "0"
                }

                if (::monVideoBitrateVal.isInitialized) {
                    val vBitrate = metrics.currentBitrateKbps
                    monVideoBitrateVal.text = if (vBitrate > 0) String.format("%,d kbps", vBitrate) else "N/A"
                    monVideoBitrateVal.setTextColor(if (vBitrate > 0) Color.parseColor("#FF38BDF8") else Color.parseColor("#FF94A3B8"))
                }

                if (::monAudioStatusVal.isInitialized) {
                    val audioActive = metrics.isAudioActive
                    monAudioStatusVal.text = if (audioActive) "ACTIVE" else "INACTIVE"
                    monAudioStatusVal.setTextColor(if (audioActive) Color.parseColor("#FF22C55E") else Color.parseColor("#FF94A3B8"))
                }

                if (::monAudioBitrateVal.isInitialized) {
                    val aBitrate = metrics.audioBitrateKbps
                    monAudioBitrateVal.text = if (aBitrate > 0) "$aBitrate kbps" else "N/A"
                }

                if (::monRtmpVal.isInitialized) {
                    val rtmpConn = metrics.isRtmpConnected
                    monRtmpVal.text = if (rtmpConn) "CONNECTED" else "DISCONNECTED"
                    monRtmpVal.setTextColor(if (rtmpConn) Color.parseColor("#FF22C55E") else Color.parseColor("#FFDC2626"))
                }

                if (::monUptimeVal.isInitialized) {
                    val uptime = metrics.uptimeSeconds
                    monUptimeVal.text = if (uptime > 0) formatDuration(uptime) else "0s"
                }

                if (::monRecordingVal.isInitialized) {
                    val recMgr = com.example.services.streaming.BroadcastRecordingManager.getInstance()
                    val isRec = recMgr.isRecording.value
                    val recDuration = recMgr.recordingDurationSeconds.value
                    if (isRec) {
                        monRecordingVal.text = "RECORDING (${formatDuration(recDuration)})"
                        monRecordingVal.setTextColor(Color.parseColor("#FFEF4444"))
                    } else {
                        val fileStatus = recMgr.fileCreationStatus.value ?: "OFF"
                        monRecordingVal.text = fileStatus
                        monRecordingVal.setTextColor(Color.parseColor("#FF94A3B8"))
                    }
                }

                if (::monRoiVal.isInitialized) {
                    val rEnabled = com.example.services.roi.InMemoryRoiConfigurationService.getInstance().selectedRoi.value?.enabled ?: false
                    monRoiVal.text = if (rEnabled) "ACTIVE" else "OFF"
                    monRoiVal.setTextColor(if (rEnabled) Color.parseColor("#FF22C55E") else Color.parseColor("#FF94A3B8"))
                }

                if (::monErrorVal.isInitialized) {
                    val errMsg = com.example.services.streaming.BroadcastRecordingManager.getInstance().recordingError.value
                        ?: broadcastController.broadcastError.value 
                        ?: youtubeService.errorMessage.value 
                        ?: service?.getLastError() 
                        ?: metrics.lastError
                    if (!errMsg.isNullOrBlank()) {
                        monErrorVal.text = errMsg
                        monErrorVal.setTextColor(Color.parseColor("#FFEF4444"))
                    } else {
                        monErrorVal.text = "None"
                        monErrorVal.setTextColor(Color.parseColor("#FF94A3B8"))
                    }
                }

                // 4. Update ROI / Detector Tab values (Section 3)
                val roiService = com.example.services.roi.InMemoryRoiConfigurationService.getInstance()
                val detService = com.example.services.detection.PUBGDetectionService.getInstance()
                val currentRoi = roiService.selectedRoi.value ?: com.example.core.model.RoiRegion.DEFAULT_KILL_FEED
                val latestFrame = service?.latestFrame?.value
                val detState = detService.detectionState.value
                val isDetActive = detState is com.example.services.detection.DetectionState.Ready ||
                        detState is com.example.services.detection.DetectionState.Analyzing
                val lastEvent = detService.latestDetectedEvent.value
                val lastProcTs = detService.lastProcessedTimestamp.value

                if (::roiOverlayCanvasView.isInitialized) {
                    val frameBmp = convertFrameToBitmap(latestFrame)
                    roiOverlayCanvasView.updateFrameAndRoi(frameBmp, currentRoi)
                }

                if (::cropPreviewImageView.isInitialized) {
                    if (latestFrame != null && currentRoi.enabled) {
                        val pixelRect = currentRoi.toPixelRect(latestFrame.width, latestFrame.height)
                        val croppedBuffer = com.example.services.analysis.RoiCropPipeline.cropRgbaBuffer(
                            latestFrame.buffer ?: ByteArray(0),
                            latestFrame.width,
                            latestFrame.height,
                            pixelRect
                        )
                        if (croppedBuffer != null && pixelRect.width > 0 && pixelRect.height > 0) {
                            var cropBmp = cachedCropPreviewBitmap
                            if (cropBmp == null || cropBmp.width != pixelRect.width || cropBmp.height != pixelRect.height || cropBmp.isRecycled) {
                                cropBmp = Bitmap.createBitmap(pixelRect.width, pixelRect.height, Bitmap.Config.ARGB_8888)
                                cachedCropPreviewBitmap = cropBmp
                            }
                            cropBmp.copyPixelsFromBuffer(ByteBuffer.wrap(croppedBuffer, 0, pixelRect.width * pixelRect.height * 4))
                            cropPreviewImageView.setImageBitmap(cropBmp)
                        } else {
                            cropPreviewImageView.setImageBitmap(null)
                        }
                    } else {
                        cropPreviewImageView.setImageBitmap(null)
                    }
                }

                if (::roiStatusVal.isInitialized) {
                    roiStatusVal.text = if (currentRoi.enabled) "ACTIVE" else "DISABLED"
                    roiStatusVal.setTextColor(if (currentRoi.enabled) Color.parseColor("#FF22C55E") else Color.parseColor("#FF94A3B8"))
                }

                val aiAssist = com.example.services.detection.pubg.PUBGVisualAIAssistant.getInstance()
                val isAiOn = aiAssist.isAiAssistEnabled.value
                val isAiAvail = aiAssist.isAvailable

                if (::aiAssistToggleBtn.isInitialized) {
                    aiAssistToggleBtn.text = if (isAiOn) "AI ASSIST: ON" else "AI ASSIST: OFF"
                    aiAssistToggleBtn.setBackgroundColor(if (isAiOn) Color.parseColor("#FFEA580C") else Color.parseColor("#FF334155"))
                }

                if (::detAiAssistVal.isInitialized) {
                    if (!isAiOn) {
                        detAiAssistVal.text = "OFF (OCR only)"
                        detAiAssistVal.setTextColor(Color.parseColor("#FF94A3B8"))
                    } else if (!isAiAvail) {
                        detAiAssistVal.text = "AI offline, OCR only"
                        detAiAssistVal.setTextColor(Color.parseColor("#FFF59E0B"))
                    } else {
                        detAiAssistVal.text = "ACTIVE (AI Assist ON)"
                        detAiAssistVal.setTextColor(Color.parseColor("#FF22C55E"))
                    }
                }

                if (::roiCoordsVal.isInitialized) {
                    roiCoordsVal.text = String.format(Locale.US, "X: %.3f | Y: %.3f | W: %.3f | H: %.3f", currentRoi.x, currentRoi.y, currentRoi.width, currentRoi.height)
                }

                if (::detEngineVal.isInitialized) {
                    detEngineVal.text = if (isDetActive) "RUNNING" else "STOPPED"
                    detEngineVal.setTextColor(if (isDetActive) Color.parseColor("#FF22C55E") else Color.parseColor("#FFDC2626"))
                }

                if (::detFramesVal.isInitialized) {
                    val receiving = active && latestFrame != null
                    detFramesVal.text = if (receiving) "RECEIVING" else "NO_FRAMES"
                    detFramesVal.setTextColor(if (receiving) Color.parseColor("#FF38BDF8") else Color.parseColor("#FF94A3B8"))
                }

                if (::detLastProcVal.isInitialized) {
                    if (lastProcTs > 0) {
                        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                        detLastProcVal.text = sdf.format(java.util.Date(lastProcTs))
                    } else {
                        detLastProcVal.text = "N/A"
                    }
                }

                if (::detConfidenceVal.isInitialized) {
                    if (lastEvent != null) {
                        val confPct = (lastEvent.confidence * 100).toInt()
                        detConfidenceVal.text = "$confPct%"
                        detConfidenceVal.setTextColor(if (lastEvent.isHighConfidence) Color.parseColor("#FF22C55E") else Color.parseColor("#FFF59E0B"))
                    } else {
                        detConfidenceVal.text = "N/A"
                        detConfidenceVal.setTextColor(Color.parseColor("#FF94A3B8"))
                    }
                }

                if (::detEventTypeVal.isInitialized) {
                    if (lastEvent != null) {
                        detEventTypeVal.text = lastEvent.eventType.name
                        detEventTypeVal.setTextColor(
                            when (lastEvent.eventType) {
                                com.example.core.model.DetectedEventType.KNOCK -> Color.parseColor("#FF00F0FF")
                                com.example.core.model.DetectedEventType.KILL,
                                com.example.core.model.DetectedEventType.ELIMINATION -> Color.parseColor("#FFEF4444")
                                else -> Color.parseColor("#FF38BDF8")
                            }
                        )
                    } else {
                        detEventTypeVal.text = "None"
                        detEventTypeVal.setTextColor(Color.parseColor("#FF94A3B8"))
                    }
                }

                if (::detAdminReviewVal.isInitialized) {
                    if (lastEvent != null) {
                        if (lastEvent.requiresAdminReview) {
                            detAdminReviewVal.text = "PENDING_REVIEW (<50%)"
                            detAdminReviewVal.setTextColor(Color.parseColor("#FFF59E0B"))
                        } else {
                            detAdminReviewVal.text = "AUTO_PROCESSED (≥50%)"
                            detAdminReviewVal.setTextColor(Color.parseColor("#FF22C55E"))
                        }
                    } else {
                        detAdminReviewVal.text = "N/A"
                        detAdminReviewVal.setTextColor(Color.parseColor("#FF94A3B8"))
                    }
                }

                // 5. Update Audio Studio Tab values (Section 4)
                val mixerState = AudioMixerManager.mixerState.value
                val micState = MicrophoneCommentaryManager.micState.value
                val musicState = LocalMusicPlayerManager.musicState.value
                val internalAudioState = InternalAudioCaptureManager.internalAudioState.value

                if (::gameVolText.isInitialized) {
                    val pct = (mixerState.gameVolume * 100).toInt()
                    gameVolText.text = if (mixerState.gameMuted) "MUTED" else "$pct%"
                    gameVolText.setTextColor(if (mixerState.gameMuted) Color.parseColor("#FF94A3B8") else Color.parseColor("#FF22C55E"))
                }

                if (::gameMuteToggleBtn.isInitialized) {
                    gameMuteToggleBtn.text = if (mixerState.gameMuted) "UNMUTE GAME" else "MUTE GAME"
                    gameMuteToggleBtn.setBackgroundColor(if (mixerState.gameMuted) Color.parseColor("#FFDC2626") else Color.parseColor("#FF334155"))
                }

                if (::micVolText.isInitialized) {
                    val pct = (mixerState.micVolume * 100).toInt()
                    micVolText.text = if (mixerState.micMuted) "MUTED" else "$pct%"
                    micVolText.setTextColor(if (mixerState.micMuted) Color.parseColor("#FF94A3B8") else Color.parseColor("#FF22C55E"))
                }

                if (::micMuteToggleBtn.isInitialized) {
                    micMuteToggleBtn.text = if (mixerState.micMuted) "UNMUTE MIC" else "MUTE MIC"
                    micMuteToggleBtn.setBackgroundColor(if (mixerState.micMuted) Color.parseColor("#FFDC2626") else Color.parseColor("#FF334155"))
                }

                if (::micStartStopToggleBtn.isInitialized) {
                    micStartStopToggleBtn.text = if (micState.isRecording) "STOP MIC" else "START MIC"
                    micStartStopToggleBtn.setBackgroundColor(if (micState.isRecording) Color.parseColor("#FFDC2626") else Color.parseColor("#FF0284C7"))
                }

                if (::musicVolText.isInitialized) {
                    val pct = (mixerState.musicVolume * 100).toInt()
                    musicVolText.text = if (mixerState.musicMuted) "MUTED" else "$pct%"
                    musicVolText.setTextColor(if (mixerState.musicMuted) Color.parseColor("#FF94A3B8") else Color.parseColor("#FF22C55E"))
                }

                if (::musicMuteToggleBtn.isInitialized) {
                    musicMuteToggleBtn.text = if (mixerState.musicMuted) "UNMUTE MUSIC" else "MUTE MUSIC"
                    musicMuteToggleBtn.setBackgroundColor(if (mixerState.musicMuted) Color.parseColor("#FFDC2626") else Color.parseColor("#FF334155"))
                }

                if (::musicPlayPauseToggleBtn.isInitialized) {
                    musicPlayPauseToggleBtn.text = if (musicState.isPlaying) "PAUSE" else "PLAY"
                    musicPlayPauseToggleBtn.setBackgroundColor(if (musicState.isPlaying) Color.parseColor("#FFF59E0B") else Color.parseColor("#FF22C55E"))
                }

                if (::musicLoopToggleBtn.isInitialized) {
                    musicLoopToggleBtn.text = if (musicState.isLooping) "LOOP: ON" else "LOOP: OFF"
                    musicLoopToggleBtn.setTextColor(if (musicState.isLooping) Color.parseColor("#FF38BDF8") else Color.parseColor("#FF94A3B8"))
                }

                if (::audGameCapVal.isInitialized) {
                    audGameCapVal.text = if (internalAudioState.isCapturing) "CAPTURING (44.1kHz)" else "STOPPED"
                    audGameCapVal.setTextColor(if (internalAudioState.isCapturing) Color.parseColor("#FF22C55E") else Color.parseColor("#FF94A3B8"))
                }

                if (::audMicCapVal.isInitialized) {
                    val rmsPct = (micState.peakVolume * 100).toInt()
                    audMicCapVal.text = if (micState.isRecording) "RECORDING ($rmsPct%)" else if (!micState.hasPermission) "NO PERMISSION" else "STOPPED"
                    audMicCapVal.setTextColor(if (micState.isRecording) Color.parseColor("#FF22C55E") else Color.parseColor("#FF94A3B8"))
                }

                if (::audMusicPlayVal.isInitialized) {
                    val title = musicState.songTitle
                    val status = if (musicState.isPlaying) "PLAYING" else "PAUSED/STOPPED"
                    audMusicPlayVal.text = "$title ($status)"
                    audMusicPlayVal.setTextColor(if (musicState.isPlaying) Color.parseColor("#FF00F0FF") else Color.parseColor("#FF94A3B8"))
                }

                if (::audEncoderVal.isInitialized) {
                    val pipelineActive = pipeline.pipelineMetrics.value.isActive
                    audEncoderVal.text = if (pipelineActive) "AAC-LC 44.1kHz Stereo @ 128kbps (ACTIVE)" else "AAC Encoder Ready"
                    audEncoderVal.setTextColor(if (pipelineActive) Color.parseColor("#FF22C55E") else Color.parseColor("#FF94A3B8"))
                }

                if (::audPcmMixerVal.isInitialized) {
                    val gPct = (mixerState.gameVolume * 100).toInt()
                    val mPct = (mixerState.micVolume * 100).toInt()
                    val bgmPct = (mixerState.musicVolume * 100).toInt()
                    audPcmMixerVal.text = "3-Ch PCM Matrix (G:$gPct% | M:$mPct% | BGM:$bgmPct%)"
                }

                // 6. Update Standing Table Controls (Section 5)
                val standingState = StandingTableControlsManager.controlsState.value
                val liveTeams = LocalLiveRuntimeManager.teamsState.value

                if (::standingTableToggleBtn.isInitialized) {
                    standingTableToggleBtn.text = if (standingState.scoreboardEnabled) "ON" else "OFF"
                    standingTableToggleBtn.setBackgroundColor(
                        if (standingState.scoreboardEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FFDC2626")
                    )
                }

                if (::top3ModeToggleBtn.isInitialized) {
                    top3ModeToggleBtn.text = if (standingState.top3ModeEnabled) "ON" else "OFF"
                    top3ModeToggleBtn.setBackgroundColor(
                        if (standingState.top3ModeEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FF334155")
                    )
                }

                if (::fullStandingsToggleBtn.isInitialized) {
                    fullStandingsToggleBtn.text = if (standingState.fullStandingsEnabled) "ON" else "OFF"
                    fullStandingsToggleBtn.setBackgroundColor(
                        if (standingState.fullStandingsEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FF334155")
                    )
                }

                if (::standingSizeBtn.isInitialized) {
                    standingSizeBtn.text = standingState.standingSize
                }

                if (::standingPosBtn.isInitialized) {
                    standingPosBtn.text = standingState.standingPosition
                }

                if (::standingOverlayStatusVal.isInitialized) {
                    val active = standingState.scoreboardEnabled
                    standingOverlayStatusVal.text = if (active) "ACTIVE IN BROADCAST" else "DISABLED"
                    standingOverlayStatusVal.setTextColor(
                        if (active) Color.parseColor("#FF22C55E") else Color.parseColor("#FFDC2626")
                    )
                }

                if (::top3ModeStatusVal.isInitialized) {
                    top3ModeStatusVal.text = if (standingState.top3ModeEnabled) "TOP 3 TEAMS" else "FULL STANDINGS (16 TEAMS)"
                    top3ModeStatusVal.setTextColor(
                        if (standingState.top3ModeEnabled) Color.parseColor("#FF00F0FF") else Color.parseColor("#FF22C55E")
                    )
                }

                if (::bottomTickerToggleBtn.isInitialized) {
                    bottomTickerToggleBtn.text = if (standingState.bottomTickerEnabled) "ON" else "OFF"
                    bottomTickerToggleBtn.setBackgroundColor(
                        if (standingState.bottomTickerEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FFDC2626")
                    )
                }

                if (::tickerSpeedToggleBtn.isInitialized) {
                    tickerSpeedToggleBtn.text = "${standingState.tickerSpeedMultiplier}x"
                }

                if (::tickerSizeBtn.isInitialized) {
                    tickerSizeBtn.text = standingState.tickerSize
                }

                if (::tickerYOffsetBtn.isInitialized) {
                    tickerYOffsetBtn.text = when (standingState.tickerYOffset) {
                        0.94f -> "Bottom"
                        0.85f -> "Lower Third"
                        0.05f -> "Top Layout"
                        else -> "Custom"
                    }
                }

                if (::tickerLoopVideoSelectBtn.isInitialized) {
                    val hasVideo = standingState.tickerLoopVideoUri != null
                    tickerLoopVideoSelectBtn.text = if (hasVideo) "VIDEO SELECTED" else "NO VIDEO SELECTED"
                    tickerLoopVideoSelectBtn.setBackgroundColor(if (hasVideo) Color.parseColor("#FF1E293B") else Color.parseColor("#FF0284C7"))
                }

                if (::tickerLoopVideoBtn.isInitialized) {
                    val activeVideo = standingState.tickerLoopVideoEnabled && standingState.tickerLoopVideoUri != null
                    tickerLoopVideoBtn.text = if (activeVideo) "ON" else "OFF"
                    tickerLoopVideoBtn.setBackgroundColor(
                        if (activeVideo) Color.parseColor("#FF16A34A") else Color.parseColor("#FFDC2626")
                    )
                }

                if (::tickerOverlayStatusVal.isInitialized) {
                    val active = standingState.bottomTickerEnabled
                    val speedText = "${standingState.tickerSpeedMultiplier}x"
                    val isVideo = if (standingState.tickerLoopVideoEnabled && standingState.tickerLoopVideoUri != null) " + LOOP VIDEO" else ""
                    tickerOverlayStatusVal.text = if (active) "ACTIVE (SCROLLING @ $speedText$isVideo)" else "DISABLED"
                    tickerOverlayStatusVal.setTextColor(
                        if (active) Color.parseColor("#FF22C55E") else Color.parseColor("#FFDC2626")
                    )
                }

                if (::vipKillCardToggleBtn.isInitialized) {
                    vipKillCardToggleBtn.text = if (standingState.killCardEnabled) "ON" else "OFF"
                    vipKillCardToggleBtn.setBackgroundColor(
                        if (standingState.killCardEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FFDC2626")
                    )
                }

                if (::vipPointCardToggleBtn.isInitialized) {
                    vipPointCardToggleBtn.text = if (standingState.milestoneCardEnabled) "ON" else "OFF"
                    vipPointCardToggleBtn.setBackgroundColor(
                        if (standingState.milestoneCardEnabled) Color.parseColor("#FF16A34A") else Color.parseColor("#FFDC2626")
                    )
                }

                if (::vipKillCardStatusVal.isInitialized) {
                    val active = standingState.killCardEnabled
                    vipKillCardStatusVal.text = if (active) "ACTIVE (5, 10, 15... KILLS)" else "DISABLED"
                    vipKillCardStatusVal.setTextColor(
                        if (active) Color.parseColor("#FF22C55E") else Color.parseColor("#FFDC2626")
                    )
                }

                if (::vipPointCardStatusVal.isInitialized) {
                    val active = standingState.milestoneCardEnabled
                    vipPointCardStatusVal.text = if (active) "ACTIVE (20, 50, 80... PTS)" else "DISABLED"
                    vipPointCardStatusVal.setTextColor(
                        if (active) Color.parseColor("#FF22C55E") else Color.parseColor("#FFDC2626")
                    )
                }

                if (::customGraphicsMasterBtn.isInitialized) {
                    val active = standingState.customGraphicsEnabled
                    customGraphicsMasterBtn.text = if (active) "ON" else "OFF"
                    customGraphicsMasterBtn.setBackgroundColor(
                        if (active) Color.parseColor("#FF16A34A") else Color.parseColor("#FFDC2626")
                    )
                }

                if (::customLogoBtn.isInitialized) {
                    val active = standingState.logoOverlayEnabled
                    customLogoBtn.text = if (active) "ON" else "OFF"
                    customLogoBtn.setBackgroundColor(
                        if (active) Color.parseColor("#FF16A34A") else Color.parseColor("#FF334155")
                    )
                }

                if (::customTextBtn.isInitialized) {
                    val active = standingState.textOverlayEnabled
                    customTextBtn.text = if (active) "ON" else "OFF"
                    customTextBtn.setBackgroundColor(
                        if (active) Color.parseColor("#FF16A34A") else Color.parseColor("#FF334155")
                    )
                }

                if (::customMatchInfoBtn.isInitialized) {
                    val active = standingState.matchInfoOverlayEnabled
                    customMatchInfoBtn.text = if (active) "ON" else "OFF"
                    customMatchInfoBtn.setBackgroundColor(
                        if (active) Color.parseColor("#FF16A34A") else Color.parseColor("#FF334155")
                    )
                }

                if (::customTeamShowcaseBtn.isInitialized) {
                    val active = standingState.teamPlayerOverlayEnabled
                    customTeamShowcaseBtn.text = if (active) "ON" else "OFF"
                    customTeamShowcaseBtn.setBackgroundColor(
                        if (active) Color.parseColor("#FF16A34A") else Color.parseColor("#FF334155")
                    )
                }

                if (::liveTeamsCountVal.isInitialized) {
                    val aliveCount = liveTeams.sumOf { it.currentAlivePlayers }
                    liveTeamsCountVal.text = "${liveTeams.size} Teams ($aliveCount Players Alive)"
                    liveTeamsCountVal.setTextColor(Color.parseColor("#FF38BDF8"))
                }

                // 7. Update Scene Studio / Timers Controls
                val sceneState = EsportsSceneEngine.sceneState.value
                val currentScene = sceneState.currentScene

                if (::sceneEngineStatusVal.isInitialized) {
                    val isRunning = sceneState.isRunning
                    val remainingSecs = when (currentScene) {
                        EsportsScene.STARTING_COUNTDOWN, EsportsScene.NEXT_MATCH_COUNTDOWN -> sceneState.remainingCountdownSeconds
                        EsportsScene.BREAK -> sceneState.remainingBreakSeconds
                        EsportsScene.ENDING -> sceneState.remainingEndingSeconds
                        else -> 0
                    }
                    val timerStr = if (isRunning && remainingSecs > 0) {
                        val mins = remainingSecs / 60
                        val secs = remainingSecs % 60
                        String.format(" (%02d:%02d)", mins, secs)
                    } else {
                        ""
                    }
                    sceneEngineStatusVal.text = "${currentScene.name}$timerStr"
                }

                // Update scene transition button visual state (Highlight active one)
                if (::startingCountdownBtn.isInitialized) {
                    val active = currentScene == EsportsScene.STARTING_COUNTDOWN
                    startingCountdownBtn.setBackgroundColor(if (active) Color.parseColor("#FF1E1B4B") else Color.parseColor("#FF334155"))
                    startingCountdownBtn.setTextColor(if (active) Color.parseColor("#FF00F0FF") else Color.WHITE)
                }
                if (::liveMatchBtn.isInitialized) {
                    val active = currentScene == EsportsScene.LIVE_MATCH
                    liveMatchBtn.setBackgroundColor(if (active) Color.parseColor("#FF1E1B4B") else Color.parseColor("#FF334155"))
                    liveMatchBtn.setTextColor(if (active) Color.parseColor("#FF00F0FF") else Color.WHITE)
                }
                if (::breakBtn.isInitialized) {
                    val active = currentScene == EsportsScene.BREAK
                    breakBtn.setBackgroundColor(if (active) Color.parseColor("#FF1E1B4B") else Color.parseColor("#FF334155"))
                    breakBtn.setTextColor(if (active) Color.parseColor("#FF00F0FF") else Color.WHITE)
                }
                if (::nextMatchCountdownBtn.isInitialized) {
                    val active = currentScene == EsportsScene.NEXT_MATCH_COUNTDOWN
                    nextMatchCountdownBtn.setBackgroundColor(if (active) Color.parseColor("#FF1E1B4B") else Color.parseColor("#FF334155"))
                    nextMatchCountdownBtn.setTextColor(if (active) Color.parseColor("#FF00F0FF") else Color.WHITE)
                }
                if (::matchEndBtn.isInitialized) {
                    val active = currentScene == EsportsScene.MATCH_ENDED
                    matchEndBtn.setBackgroundColor(if (active) Color.parseColor("#FF1E1B4B") else Color.parseColor("#FF334155"))
                    matchEndBtn.setTextColor(if (active) Color.parseColor("#FF00F0FF") else Color.WHITE)
                }
                if (::endingBtn.isInitialized) {
                    val active = currentScene == EsportsScene.ENDING
                    endingBtn.setBackgroundColor(if (active) Color.parseColor("#FF1E1B4B") else Color.parseColor("#FF334155"))
                    endingBtn.setTextColor(if (active) Color.parseColor("#FF00F0FF") else Color.WHITE)
                }

                // Update Timer actions state
                if (::timerPauseBtn.isInitialized && ::timerResumeBtn.isInitialized && ::timerResetBtn.isInitialized) {
                    val hasTimer = currentScene == EsportsScene.STARTING_COUNTDOWN || currentScene == EsportsScene.BREAK || currentScene == EsportsScene.ENDING
                    timerPauseBtn.isEnabled = hasTimer && sceneState.isRunning
                    timerResumeBtn.isEnabled = hasTimer && !sceneState.isRunning
                    timerResetBtn.isEnabled = hasTimer

                    timerPauseBtn.alpha = if (timerPauseBtn.isEnabled) 1.0f else 0.5f
                    timerResumeBtn.alpha = if (timerResumeBtn.isEnabled) 1.0f else 0.5f
                    timerResetBtn.alpha = if (timerResetBtn.isEnabled) 1.0f else 0.5f
                }

                uiUpdateHandler.postDelayed(this, 1000)
            }
        }
        uiUpdateHandler.post(telemetryRunnable!!)
    }

    private fun startChatPolling() {
        if (chatPollingHandler != null) return // Prevent duplicate polling jobs
        chatPollingHandler = Handler(Looper.getMainLooper())
        chatPollingRunnable = object : Runnable {
            override fun run() {
                pollYouTubeLiveChatNow { nextDelay ->
                    chatPollingHandler?.postDelayed(this, nextDelay)
                }
            }
        }
        chatPollingHandler?.post(chatPollingRunnable!!)
    }

    private fun stopChatPolling() {
        chatPollingRunnable?.let { chatPollingHandler?.removeCallbacks(it) }
        chatPollingHandler = null
        chatPollingRunnable = null
    }

    private fun pollYouTubeLiveChatNow(onComplete: ((Long) -> Unit)? = null) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            var nextDelay = 10000L
            try {
                val broadcastInfo = youtubeService.currentBroadcast.value
                if (broadcastInfo == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (::chatConnectionStatusVal.isInitialized) chatConnectionStatusVal.text = "NO BROADCAST"
                        if (::activeChatStreamVal.isInitialized) activeChatStreamVal.text = "DISCONNECTED"
                        onComplete?.invoke(10000L)
                    }
                    return@launch
                }

                // Detect when active broadcast / live chat is no longer active (Status ended/complete)
                if (broadcastInfo.lifeCycleStatus.equals("complete", ignoreCase = true) || 
                    broadcastInfo.lifeCycleStatus.equals("completed", ignoreCase = true)) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (::chatConnectionStatusVal.isInitialized) {
                            chatConnectionStatusVal.text = "BROADCAST ENDED"
                            chatConnectionStatusVal.setTextColor(Color.parseColor("#FFDC2626"))
                        }
                        stopChatPolling() // Stop chat polling cleanly when the broadcast ends
                    }
                    return@launch
                }

                val chatResult = youtubeService.getLiveChatId(broadcastInfo.broadcastId)
                if (chatResult.isSuccess && chatResult.getOrNull() != null) {
                    val liveChatId = chatResult.getOrNull()!!
                    val msgResult = youtubeService.fetchLiveChatMessages(liveChatId)
                    if (msgResult.isSuccess) {
                        val messages = msgResult.getOrNull() ?: emptyList()
                        // Use YouTube's returned pollingIntervalMillis
                        nextDelay = youtubeService.lastPollingIntervalMs.coerceAtLeast(1000L)

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (::chatConnectionStatusVal.isInitialized) {
                                chatConnectionStatusVal.text = "CONNECTED (LIVE)"
                                chatConnectionStatusVal.setTextColor(Color.parseColor("#FF22C55E"))
                            }
                            if (::activeChatStreamVal.isInitialized) {
                                activeChatStreamVal.text = broadcastInfo.title
                            }

                            // Append new unique messages (duplicate-message protection preserved)
                            messages.forEach { msg ->
                                if (!cachedChatMessages.contains(msg.messageId)) {
                                    cachedChatMessages.add(msg.messageId)
                                    appendChatMessageView(msg.authorName, msg.messageText, msg.isModerator || msg.isOwner)
                                }
                            }
                        }
                    } else {
                        val errMessage = msgResult.exceptionOrNull()?.message ?: ""
                        if (errMessage.contains("liveChatEnded", ignoreCase = true) || errMessage.contains("403") || errMessage.contains("broadcastEnded", ignoreCase = true)) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (::chatConnectionStatusVal.isInitialized) {
                                    chatConnectionStatusVal.text = "CHAT CLOSED"
                                    chatConnectionStatusVal.setTextColor(Color.parseColor("#FFDC2626"))
                                }
                                stopChatPolling() // Stop polling when chat is closed
                            }
                            return@launch
                        }
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (::chatConnectionStatusVal.isInitialized) {
                            chatConnectionStatusVal.text = "WAITING FOR CHAT ID"
                            chatConnectionStatusVal.setTextColor(Color.parseColor("#FFF59E0B"))
                        }
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (::chatConnectionStatusVal.isInitialized) {
                        chatConnectionStatusVal.text = "API ERROR"
                        chatConnectionStatusVal.setTextColor(Color.parseColor("#FFDC2626"))
                    }
                }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (chatPollingHandler != null) {
                        onComplete?.invoke(nextDelay)
                    }
                }
            }
        }
    }

    private fun appendChatMessageView(author: String, text: String, isSpecial: Boolean) {
        if (!::chatMessagesScrollContainer.isInitialized) return
        val msgView = TextView(context).apply {
            val prefix = if (isSpecial) "[MOD/OWNER] " else ""
            this.text = "$prefix$author: $text"
            setTextColor(if (isSpecial) Color.parseColor("#FF00F0FF") else Color.WHITE)
            textSize = 10f
            setPadding(0, dpToPx(2f), 0, dpToPx(2f))
        }
        chatMessagesScrollContainer.addView(msgView)
        // Limit max rendered messages to 100 to avoid memory growth
        if (chatMessagesScrollContainer.childCount > 100) {
            chatMessagesScrollContainer.removeViewAt(0)
        }
    }

    private fun sendOutgoingChatMessage() {
        if (!::chatInputEditText.isInitialized) return
        val text = chatInputEditText.text.toString().trim()
        if (text.isEmpty()) return

        if (isSendingChat) return // Prevent accidental duplicate sends
        isSendingChat = true

        if (::chatSendBtn.isInitialized) {
            chatSendBtn.isEnabled = false
            chatSendBtn.alpha = 0.5f
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val broadcastInfo = youtubeService.currentBroadcast.value
                if (broadcastInfo == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendChatMessageView("SYSTEM ERROR", "No active broadcast connected.", true)
                        isSendingChat = false
                        if (::chatSendBtn.isInitialized) {
                            chatSendBtn.isEnabled = true
                            chatSendBtn.alpha = 1.0f
                        }
                    }
                    return@launch
                }

                val chatResult = youtubeService.getLiveChatId(broadcastInfo.broadcastId)
                val liveChatId = chatResult.getOrNull()
                if (!liveChatId.isNullOrBlank()) {
                    val sendResult = youtubeService.sendLiveChatMessage(liveChatId, text)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (sendResult.isSuccess) {
                            appendChatMessageView("You (Operator)", text, true)
                            chatInputEditText.setText("") // Clear field on success
                        } else {
                            val errorMsg = sendResult.exceptionOrNull()?.message ?: "Unknown API error"
                            appendChatMessageView("SYSTEM ERROR", "Failed to send: $errorMsg", true)
                            android.widget.Toast.makeText(context, "Chat send failed: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                        }
                        isSendingChat = false
                        if (::chatSendBtn.isInitialized) {
                            chatSendBtn.isEnabled = true
                            chatSendBtn.alpha = 1.0f
                        }
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val errorMsg = chatResult.exceptionOrNull()?.message ?: "Live chat ID missing."
                        appendChatMessageView("SYSTEM ERROR", "Failed to send: $errorMsg", true)
                        android.widget.Toast.makeText(context, "Chat send failed: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                        isSendingChat = false
                        if (::chatSendBtn.isInitialized) {
                            chatSendBtn.isEnabled = true
                            chatSendBtn.alpha = 1.0f
                        }
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val errorMsg = e.localizedMessage ?: "Unknown exception"
                    appendChatMessageView("SYSTEM ERROR", "Failed to send: $errorMsg", true)
                    android.widget.Toast.makeText(context, "Chat send failed: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                    isSendingChat = false
                    if (::chatSendBtn.isInitialized) {
                        chatSendBtn.isEnabled = true
                        chatSendBtn.alpha = 1.0f
                    }
                }
            }
        }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0s"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m ${s}s"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    fun updateStatus(statusText: String) {
        // Callback helper for parent notification status updates
    }

    fun hideOverlay() {
        telemetryRunnable?.let { uiUpdateHandler.removeCallbacks(it) }
        stopChatPolling()
        overlayJob.cancel()
        if (isAttached && overlayContainer != null) {
            try {
                windowManager.removeView(overlayContainer)
            } catch (_: Exception) {}
            isAttached = false
            overlayContainer = null
            pointerView = null
            expandedCardView = null
        }
    }
}

private class OverlayRootLayout(
    context: Context,
    private val onBackPressed: () -> Unit,
    private val onConfigChanged: () -> Unit
) : FrameLayout(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onConfigChanged()
    }
}
