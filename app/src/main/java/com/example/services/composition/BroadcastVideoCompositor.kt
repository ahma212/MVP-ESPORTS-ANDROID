package com.example.services.composition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.core.model.DetectionFrame
import com.example.services.streaming.StandingTableControlsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Real-Time Broadcast Video Compositor Foundation (Part A).
 *
 * Implements [IBroadcastCompositor] and [com.example.services.streaming.ICompositionProcessor].
 *
 * Execution Pipeline:
 * Real Captured PUBG Frame (ScreenCaptureService)
 *         ↓
 * Broadcast Compositor Base Layer
 *         ↓
 * Overall Standing Overlay Layer (EsportsStandingTable integration)
 *         ↓
 * MVP ESPORTS Bottom Ticker Layer (EsportsBottomTicker integration)
 *         ↓
 * Final Composed Broadcast Frame (MediaCodec / Broadcast Program Output)
 *
 * Guarantees:
 * 1. Base layer is strictly the real captured PUBG mobile screen frame (never fake/placeholder).
 * 2. PUBG gameplay remains fully visible beneath semi-transparent esports overlays.
 * 3. Operator controls (Floating pointer, ROI boxes, buttons) are strictly excluded from broadcast output.
 * 4. Memory-optimized zero-allocation buffer reuse across high-frequency 30/60 FPS frames.
 */
class BroadcastVideoCompositor private constructor() : IBroadcastCompositor {

    companion object {
        @Volatile
        private var instance: BroadcastVideoCompositor? = null

        fun getInstance(): BroadcastVideoCompositor {
            return instance ?: synchronized(this) {
                instance ?: BroadcastVideoCompositor().also { instance = it }
            }
        }
    }

    private val layers = ConcurrentHashMap<String, IBroadcastLayer>()

    override val standingOverlay = OverallStandingOverlayLayer()
    fun setTeamsProvider(provider: () -> List<com.example.core.model.TeamLiveState>) {
        standingOverlay.teamsProvider = provider
        customGraphicsOverlay.teamsProvider = provider
        vipMilestoneOverlay.teamsProvider = provider
    }
    override val tickerOverlay = BottomTickerOverlayLayer()
    val sceneGraphicsOverlay = SceneGraphicsOverlayLayer()
    val vipMilestoneOverlay = VipMilestoneOverlayLayer()
    val customGraphicsOverlay = CustomGraphicsOverlayLayer()
    val videoMemeOverlay = BroadcastVideoMemeOverlayLayer()

    private val _composedFrames = MutableSharedFlow<ComposedBroadcastFrame>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val composedFrames: SharedFlow<ComposedBroadcastFrame> = _composedFrames.asSharedFlow()

    private val _latestComposedFrame = MutableStateFlow<ComposedBroadcastFrame?>(null)
    override val latestComposedFrame: StateFlow<ComposedBroadcastFrame?> = _latestComposedFrame.asStateFlow()

    private val _isCompositing = MutableStateFlow(false)
    override val isCompositing: StateFlow<Boolean> = _isCompositing.asStateFlow()

    private var frameAttachmentJob: Job? = null
    private var controlsObserverJob: Job? = null
    private var sceneObserverJob: Job? = null
    private var milestoneObserverJob: Job? = null

    // Reusable bitmaps and canvas to eliminate garbage collection pressure
    @Volatile
    private var cachedBaseBitmap: Bitmap? = null
    @Volatile
    private var cachedOutputBitmap: Bitmap? = null
    private var cachedCanvas: Canvas? = null
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Smoothness tracking (zero-allocation ring buffer)
    private val frameTimesArray = LongArray(60)
    private var frameTimesIndex = 0
    private var frameTimesCount = 0
    private var lastFpsUpdate = 0L
    private var frameCount = 0
    @Volatile
    private var currentFps = 0
    @Volatile
    private var isHeavy = false

    init {
        // Register default Part A, Part E, Part F, and Part G broadcast layers
        addLayer(standingOverlay)
        addLayer(tickerOverlay)
        addLayer(sceneGraphicsOverlay)
        addLayer(vipMilestoneOverlay)
        addLayer(customGraphicsOverlay)
        addLayer(videoMemeOverlay)
    }

    override fun addLayer(layer: IBroadcastLayer) {
        // Floating pointer, cropper, and operator control overlays must NOT appear in broadcast composition
        if (layer.layerId.contains("pointer", ignoreCase = true) ||
            layer.layerId.contains("cropper", ignoreCase = true) ||
            layer.layerId.contains("control", ignoreCase = true) ||
            layer.layerId.contains("overlay_badge", ignoreCase = true)
        ) {
            android.util.Log.w("BroadcastVideoCompositor", "Excluded operator layer '${layer.layerId}' from broadcast composition")
            return
        }
        layers[layer.layerId] = layer
    }

    override fun removeLayer(layerId: String) {
        layers.remove(layerId)
    }

    override fun getLayer(layerId: String): IBroadcastLayer? {
        return layers[layerId]
    }

    override fun getAllLayers(): List<IBroadcastLayer> {
        return layers.values.sortedBy { it.zIndex }
    }

    override fun setStandingOverlayEnabled(enabled: Boolean) {
        standingOverlay.isEnabled = enabled
    }

    override fun setTickerOverlayEnabled(enabled: Boolean) {
        tickerOverlay.isEnabled = enabled
    }

    /**
     * Attaches directly to a live frame stream from ScreenCaptureService or IVideoInputService.
     */
    override fun attachFrameSource(frameFlow: SharedFlow<DetectionFrame>, scope: CoroutineScope) {
        frameAttachmentJob?.cancel()
        controlsObserverJob?.cancel()
        sceneObserverJob?.cancel()
        milestoneObserverJob?.cancel()

        _isCompositing.value = true

        // Synchronize with operator toggle controls from StandingTableControlsManager
        controlsObserverJob = scope.launch {
            StandingTableControlsManager.controlsState.collect { controls ->
                standingOverlay.isEnabled = controls.scoreboardEnabled
                standingOverlay.top3Mode = controls.top3ModeEnabled
                
                // Standing Scale/Size mapping
                standingOverlay.scale = when (controls.standingSize) {
                    "Small" -> 0.75f
                    "Large" -> 1.25f
                    else -> 1.0f // "Medium"
                }

                // Standing Position mapping
                when (controls.standingPosition) {
                    "Top Left" -> {
                        standingOverlay.xOffsetPercent = 0.05f
                        standingOverlay.yOffsetPercent = 0.05f
                    }
                    "Bottom Left" -> {
                        standingOverlay.xOffsetPercent = 0.05f
                        standingOverlay.yOffsetPercent = 0.55f
                    }
                    "Bottom Right" -> {
                        standingOverlay.xOffsetPercent = 0.68f
                        standingOverlay.yOffsetPercent = 0.55f
                    }
                    else -> { // "Top Right"
                        standingOverlay.xOffsetPercent = 0.68f
                        standingOverlay.yOffsetPercent = 0.05f
                    }
                }

                tickerOverlay.isEnabled = controls.bottomTickerEnabled
                tickerOverlay.speedMultiplier = controls.tickerSpeedMultiplier

                // Ticker Size (height + textScale) mapping
                when (controls.tickerSize) {
                    "Small" -> {
                        tickerOverlay.heightPx = 30f
                        tickerOverlay.textScale = 0.8f
                    }
                    "Large" -> {
                        tickerOverlay.heightPx = 60f
                        tickerOverlay.textScale = 1.3f
                    }
                    else -> { // "Medium"
                        tickerOverlay.heightPx = 42f
                        tickerOverlay.textScale = 1.0f
                    }
                }

                tickerOverlay.yOffsetPercent = controls.tickerYOffset
                tickerOverlay.customText = controls.tickerCustomText
                tickerOverlay.loopVideoUri = controls.tickerLoopVideoUri
                tickerOverlay.loopVideoEnabled = controls.tickerLoopVideoEnabled

                videoMemeOverlay.videoUri = controls.tickerLoopVideoUri
                videoMemeOverlay.isEnabled = controls.tickerLoopVideoEnabled

                customGraphicsOverlay.isEnabled = controls.customGraphicsEnabled
                customGraphicsOverlay.customBannerText = controls.customBannerText
                customGraphicsOverlay.customBannerPosition = controls.customBannerPosition
                customGraphicsOverlay.customBannerScale = controls.customBannerScale
                vipMilestoneOverlay.isEnabled = controls.killCardEnabled || controls.milestoneCardEnabled
            }
        }

        // Synchronize with EsportsSceneEngine state (enabling scene graphics overlay for countdowns, breaks, endings)
        sceneObserverJob = scope.launch {
            com.example.services.scene.EsportsSceneEngine.sceneState.collect { sceneState ->
                val isLive = sceneState.currentScene == com.example.core.model.EsportsScene.LIVE_MATCH
                sceneGraphicsOverlay.isEnabled = !isLive
            }
        }

        // Observe teams state for VIP milestones
        milestoneObserverJob = scope.launch {
            var previousTeams = emptyList<com.example.core.model.TeamLiveState>()
            com.example.services.streaming.LocalLiveRuntimeManager.teamsState.collect { currentTeams ->
                if (previousTeams.isNotEmpty()) {
                    vipMilestoneOverlay.detectMilestones(previousTeams, currentTeams)
                }
                previousTeams = currentTeams
            }
        }

        // Ingest and composite real frames in background
        frameAttachmentJob = scope.launch {
            frameFlow.collect { rawFrame ->
                composeFrame(rawFrame)
            }
        }
    }

    /**
     * Core composition engine.
     * Takes the real captured PUBG mobile frame, paints it as the background,
     * overlays active esports graphics, and outputs the final broadcast frame.
     */
    @Synchronized
    override fun composeFrame(rawFrame: DetectionFrame): ComposedBroadcastFrame? {
        val buffer = rawFrame.buffer ?: return null
        val width = rawFrame.width
        val height = rawFrame.height
        if (width <= 0 || height <= 0) return null

        val expectedSize = width * height * 4
        if (buffer.size < expectedSize) return null

        val startTimestamp = System.currentTimeMillis()

        try {
            // 1. Allocate or reuse base bitmap matching frame dimensions
            var baseBmp = cachedBaseBitmap
            if (baseBmp == null || baseBmp.width != width || baseBmp.height != height || baseBmp.isRecycled) {
                baseBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                cachedBaseBitmap = baseBmp
            }

            // Copy raw pixels of the REAL PUBG screen capture into base bitmap
            val byteBuffer = ByteBuffer.wrap(buffer, 0, expectedSize)
            baseBmp.copyPixelsFromBuffer(byteBuffer)

            // 2. Allocate or reuse output bitmap and Canvas
            var outBmp = cachedOutputBitmap
            if (outBmp == null || outBmp.width != width || outBmp.height != height || outBmp.isRecycled) {
                outBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                cachedOutputBitmap = outBmp
                cachedCanvas = Canvas(outBmp)
            }
            val canvas = cachedCanvas ?: Canvas(outBmp).also { cachedCanvas = it }

            // 3. Draw Base Layer: The real full-screen PUBG gameplay with Station Color Grading
            basePaint.colorFilter = com.example.services.station.StationDeskManager.getColorFilter()
            canvas.drawBitmap(baseBmp, 0f, 0f, basePaint)

            // 4. Render Active Broadcast Overlay Layers (in strict z-index order)
            // Encoded video is gameplay + standing + ticker + milestones + memes only.
            // FloatingPointerOverlay views and operator controls are strictly excluded.
            val enabledLayers = layers.values.filter { layer ->
                layer.isEnabled &&
                    !layer.layerId.contains("pointer", ignoreCase = true) &&
                    !layer.layerId.contains("cropper", ignoreCase = true) &&
                    !layer.layerId.contains("control", ignoreCase = true)
            }.sortedBy { it.zIndex }
            val activeLayerIds = mutableListOf<String>()

            for (layer in enabledLayers) {
                layer.draw(canvas, width, height, rawFrame.timestampMs)
                activeLayerIds.add(layer.layerId)
            }

            val elapsed = System.currentTimeMillis() - startTimestamp
            
            // Auto-Downgrade logic (zero-allocation ring buffer)
            frameCount++
            frameTimesArray[frameTimesIndex] = elapsed
            frameTimesIndex = (frameTimesIndex + 1) % 60
            if (frameTimesCount < 60) frameTimesCount++
            
            val now = System.currentTimeMillis()
            if (now - lastFpsUpdate > 1000) {
                currentFps = frameCount
                frameCount = 0
                lastFpsUpdate = now
                
                var sum = 0L
                for (i in 0 until frameTimesCount) {
                    sum += frameTimesArray[i]
                }
                val avgTime = if (frameTimesCount > 0) sum.toDouble() / frameTimesCount else 0.0
                isHeavy = avgTime > 20.0
                
                // Update StationDeskManager
                com.example.services.station.StationDeskManager.updatePerformanceMetrics(currentFps, isHeavy)
                
                // If in QUALITY and heavy for 2 seconds, trigger auto-downgrade
                val state = com.example.services.station.StationDeskManager.stationState.value
                if (state.smoothnessMode == com.example.services.station.SmoothnessMode.QUALITY && isHeavy) {
                    com.example.services.station.StationDeskManager.triggerAutoDowngrade()
                }
            }

            val composed = ComposedBroadcastFrame(
                frameId = rawFrame.frameId,
                timestampMs = rawFrame.timestampMs,
                width = width,
                height = height,
                bitmap = outBmp,
                activeLayers = activeLayerIds,
                compositionTimeMs = elapsed
            )

            _latestComposedFrame.value = composed
            _composedFrames.tryEmit(composed)

            return composed
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Backward-compatible bridge to existing [com.example.services.streaming.ICompositionProcessor].
     * Program output is strictly gameplay + standing + ticker + milestones + memes only.
     * Overlay screenshots or badges are strictly NOT drawn into the compose bitmap.
     */
    override fun compositeOverlay(frame: DetectionFrame, overlayBadge: Bitmap?): Bitmap {
        val composed = composeFrame(frame)
        return composed?.bitmap
            ?: cachedOutputBitmap
            ?: Bitmap.createBitmap(frame.width.coerceAtLeast(1), frame.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    }

    override fun release() {
        frameAttachmentJob?.cancel()
        frameAttachmentJob = null
        controlsObserverJob?.cancel()
        controlsObserverJob = null
        sceneObserverJob?.cancel()
        sceneObserverJob = null
        milestoneObserverJob?.cancel()
        milestoneObserverJob = null
        _isCompositing.value = false

        cachedBaseBitmap?.recycle()
        cachedBaseBitmap = null

        cachedOutputBitmap?.recycle()
        cachedOutputBitmap = null

        _latestComposedFrame.value = null
    }
}
