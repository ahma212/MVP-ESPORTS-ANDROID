package com.example.services.video

/**
 * State machine for the abstract video input pipeline.
 */
sealed class VideoCaptureState {
    object Idle : VideoCaptureState()
    object Initializing : VideoCaptureState()
    data class Capturing(
        val sourceType: VideoSourceType,
        val width: Int,
        val height: Int,
        val fps: Int = 30,
        val framesCaptured: Long = 0L,
        val startTimeMs: Long = System.currentTimeMillis()
    ) : VideoCaptureState()
    data class Paused(val reason: String = "User paused") : VideoCaptureState()
    data class Error(val message: String, val throwable: Throwable? = null) : VideoCaptureState()
}
