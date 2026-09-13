package com.example.services.streaming

import kotlinx.coroutines.flow.StateFlow

/**
 * Reconciled single-source-of-truth state for the active live session.
 */
data class UnifiedSessionState(
    val isServiceRunning: Boolean = false,
    val isCaptureActive: Boolean = false,
    val captureWidth: Int = 0,
    val captureHeight: Int = 0,
    val capturedFps: Int = 0,
    val frameCount: Long = 0L,
    val droppedFrames: Long = 0L,
    val lastFrameTimestamp: Long = 0L,
    val youtubeAuthorized: Boolean = false,
    val youtubeAccountName: String = "Not Connected",
    val youtubeBroadcastState: LiveSessionState = LiveSessionState.IDLE,
    val uptimeSeconds: Long = 0L,
    val diagnosticError: String? = null
)

/**
 * Central state coordinator and logic manager for the esports live broadcast pipeline.
 */
interface ILiveSessionManager {
    val sessionState: StateFlow<UnifiedSessionState>

    /**
     * Initializes the coordinated live session.
     */
    fun startCoordinatedSession(): Result<Unit>

    /**
     * Terminate stream session explicitly (Stop Live).
     */
    suspend fun stopCoordinatedSession(): Result<Unit>

    /**
     * Disconnects / cleans up resources.
     */
    fun destroySession()
}
