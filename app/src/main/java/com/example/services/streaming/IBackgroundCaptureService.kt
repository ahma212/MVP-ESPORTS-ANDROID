package com.example.services.streaming

import kotlinx.coroutines.flow.StateFlow

/**
 * Capture states for background platform recorder.
 */
sealed class CaptureState {
    object Idle : CaptureState()
    object Starting : CaptureState()
    data class Capturing(val width: Int, val height: Int, val fps: Int) : CaptureState()
    data class Error(val message: String) : CaptureState()
}

/**
 * Platform-independent abstraction for the background capture pipeline.
 */
interface IBackgroundCaptureService {
    val captureState: StateFlow<CaptureState>
    val fps: StateFlow<Int>
    val frameCount: StateFlow<Long>
    val droppedFrames: StateFlow<Long>
    val lastFrameTimestamp: StateFlow<Long>
    val lastError: StateFlow<String?>

    fun startCapture(width: Int, height: Int, densityDpi: Int): Result<Unit>
    fun stopCapture(): Result<Unit>
}
