package com.example.services.composition

import android.graphics.Canvas

/**
 * Extensible interface representing an independent visual broadcast overlay layer
 * rendered on top of the base captured PUBG Mobile video frame.
 *
 * Part A Foundation architecture:
 * Broadcast Frame
 *     │
 *     ├── PUBG Full Screen Base (ScreenCaptureService frame buffer)
 *     │
 *     ├── Overall Standing Layer (IBroadcastLayer)
 *     │
 *     └── MVP ESPORTS Ticker Layer (IBroadcastLayer)
 *
 * Allows subsequent broadcast graphics (VIP Milestones, Custom Text, Scenes, Logos)
 * to be added without modifying the core compositor pipeline.
 */
interface IBroadcastLayer {
    /**
     * Unique alphanumeric identifier for this layer.
     */
    val layerId: String

    /**
     * Human-readable display name for studio operator logging.
     */
    val layerName: String

    /**
     * Toggles whether this layer is drawn during composition.
     */
    var isEnabled: Boolean

    /**
     * Z-index rendering order: lower values rendered earlier; base frame is 0.
     */
    val zIndex: Int

    /**
     * Renders the layer content onto the broadcast Canvas.
     *
     * @param canvas Hardware or software Canvas backing the final composed broadcast frame.
     * @param frameWidth Width of the base video frame in pixels.
     * @param frameHeight Height of the base video frame in pixels.
     * @param timestampMs Presentation timestamp of the frame in milliseconds.
     */
    fun draw(canvas: Canvas, frameWidth: Int, frameHeight: Int, timestampMs: Long)
}
