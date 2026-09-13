package com.example.services.composition

import android.graphics.Bitmap

/**
 * Output data model produced by the Broadcast Video Compositor.
 *
 * Encapsulates the final broadcast program frame combining the real captured
 * PUBG Mobile screen frame as the base layer with active esports tournament overlays
 * (Overall Standing table and MVP ESPORTS bottom ticker).
 *
 * This frame is directly consumable by hardware video encoders (MediaCodec)
 * or local RTMP muxing pipelines.
 */
data class ComposedBroadcastFrame(
    val frameId: Long,
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val bitmap: Bitmap,
    val activeLayers: List<String>,
    val compositionTimeMs: Long
)
