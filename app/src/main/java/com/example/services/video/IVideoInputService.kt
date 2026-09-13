package com.example.services.video

import com.example.core.model.DetectionFrame
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract interface for all video frame acquisition sources.
 *
 * This design ensures complete separation of the video capture mechanism from the
 * detection engine and UI, allowing seamless operation across:
 * 1. Android MediaProjection / VirtualDisplay
 * 2. Windows Desktop Duplication / WinRT Graphics Capture
 * 3. Video file feeds / Test stream sources
 * 4. External camera or HDMI capture cards
 * 5. Future PRISM stream inputs
 */
interface IVideoInputService {
    /**
     * Current state of the video capture pipeline.
     */
    val captureState: StateFlow<VideoCaptureState>

    /**
     * Stream of raw video frames emitted to the AI/CV layer.
     */
    val frameFlow: SharedFlow<DetectionFrame>

    /**
     * Active video source type.
     */
    val activeSource: VideoSourceType

    /**
     * Initiates video capture on the current platform.
     */
    suspend fun startCapture(): Result<Unit>

    /**
     * Stops video capture and releases active display/hardware resources.
     */
    suspend fun stopCapture(): Result<Unit>

    /**
     * Cleans up resources when the service is destroyed.
     */
    fun release()
}
