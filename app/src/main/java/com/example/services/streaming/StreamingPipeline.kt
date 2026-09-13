package com.example.services.streaming

import android.graphics.Bitmap
import com.example.core.model.DetectionFrame
import kotlinx.coroutines.flow.SharedFlow

/**
 * Stage 1: Screen Capture Source Contract
 * Exposes the captured frame stream to downsampling and encoding steps.
 */
interface IScreenCaptureSource {
    val rawFrames: SharedFlow<DetectionFrame>
    fun getDimensions(): Pair<Int, Int>
}

/**
 * Stage 2: Composition / Graphic Overlays Processor
 * Enables combining raw game frames with diagnostic watermarks or tournament overlays.
 */
interface ICompositionProcessor {
    /**
     * Composites visual indicators (e.g. OCR overlays, stream badges) on the frame.
     */
    fun compositeOverlay(frame: DetectionFrame, overlayBadge: Bitmap?): Bitmap
}

/**
 * Stage 3: Hardware Video Encoder Contract
 * Manages AMC MediaCodec initialization, frame format negotiation, and raw H264/AAC bitstream encoding.
 */
interface IVideoEncoder {
    val isRunning: Boolean get() = false
    val encodedFrames: SharedFlow<EncodedVideoFrame>? get() = null
    fun configureEncoder(width: Int, height: Int, bitrate: Int, fps: Int): Result<Unit>
    fun encodeFrame(bitmap: Bitmap, presentationTimeUs: Long): Result<ByteArray>
    fun stopEncoder()
}

/**
 * Stage 4: YouTube RTMP Ingest Gateway
 * Establishes FLV multiplexing and TCP socket transmission to the target YouTube RTMP endpoint.
 */
interface IYouTubeIngest {
    val isConnected: Boolean get() = false
    fun connectIngest(rtmpUrl: String): Result<Unit>
    fun transmitMuxedData(data: ByteArray, length: Int): Result<Unit>
    fun disconnectIngest()
}
