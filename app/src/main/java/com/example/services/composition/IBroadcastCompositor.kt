package com.example.services.composition

import com.example.core.model.DetectionFrame
import com.example.services.streaming.ICompositionProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stage 2 Broadcast Composition Engine Contract.
 *
 * Extends [ICompositionProcessor] to bridge raw hardware screen captures
 * into high-fidelity composed esports broadcast frames ready for encoding.
 */
interface IBroadcastCompositor : ICompositionProcessor {

    /**
     * Flow of all composed broadcast frames for downstream video encoders.
     */
    val composedFrames: SharedFlow<ComposedBroadcastFrame>

    /**
     * Most recent composed broadcast frame for operator program monitor previews.
     */
    val latestComposedFrame: StateFlow<ComposedBroadcastFrame?>

    /**
     * Indicates whether the compositor is currently ingesting and composing frames.
     */
    val isCompositing: StateFlow<Boolean>

    /**
     * Direct reference to the Overall Standing overlay layer.
     */
    val standingOverlay: OverallStandingOverlayLayer

    /**
     * Direct reference to the MVP ESPORTS bottom ticker overlay layer.
     */
    val tickerOverlay: BottomTickerOverlayLayer

    /**
     * Adds or replaces an extensible overlay layer.
     */
    fun addLayer(layer: IBroadcastLayer)

    /**
     * Removes an overlay layer by its unique identifier.
     */
    fun removeLayer(layerId: String)

    /**
     * Retrieves a registered layer by identifier.
     */
    fun getLayer(layerId: String): IBroadcastLayer?

    /**
     * Returns an unmodifiable snapshot of all registered layers ordered by z-index.
     */
    fun getAllLayers(): List<IBroadcastLayer>

    /**
     * Composites the full PUBG screen capture with all active broadcast layers.
     *
     * @param rawFrame Real captured PUBG Mobile frame from ScreenCaptureService.
     * @return Composed broadcast frame, or null if buffer is invalid.
     */
    fun composeFrame(rawFrame: DetectionFrame): ComposedBroadcastFrame?

    /**
     * Attaches directly to a live frame stream from ScreenCaptureService.
     */
    fun attachFrameSource(frameFlow: SharedFlow<DetectionFrame>, scope: CoroutineScope)

    /**
     * Toggles visibility of the Overall Standing overlay.
     */
    fun setStandingOverlayEnabled(enabled: Boolean)

    /**
     * Toggles visibility of the Bottom Ticker overlay.
     */
    fun setTickerOverlayEnabled(enabled: Boolean)

    /**
     * Releases cached bitmap resources.
     */
    fun release()
}
