package com.example.platform.desktop

import com.example.core.model.DetectionFrame
import com.example.services.video.IVideoInputService
import com.example.services.video.VideoCaptureState
import com.example.services.video.VideoSourceType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Windows PC / Desktop Screen Capture adapter contract placeholder.
 *
 * Prepared for future Windows desktop deployment (DirectX Desktop Duplication API /
 * WinRT Windows.Graphics.Capture API) sharing the identical IVideoInputService interface
 * and event processing pipeline.
 */
class DesktopScreenCapturePlaceholder : IVideoInputService {

    private val _captureState = MutableStateFlow<VideoCaptureState>(VideoCaptureState.Idle)
    override val captureState: StateFlow<VideoCaptureState> = _captureState.asStateFlow()

    private val _frameFlow = MutableSharedFlow<DetectionFrame>(extraBufferCapacity = 64)
    override val frameFlow: SharedFlow<DetectionFrame> = _frameFlow.asSharedFlow()

    override val activeSource: VideoSourceType = VideoSourceType.WINDOWS_DESKTOP_DUPLICATION

    override suspend fun startCapture(): Result<Unit> {
        _captureState.value = VideoCaptureState.Capturing(
            sourceType = VideoSourceType.WINDOWS_DESKTOP_DUPLICATION,
            width = 1920,
            height = 1080,
            fps = 60,
            framesCaptured = 0L
        )
        return Result.success(Unit)
    }

    override suspend fun stopCapture(): Result<Unit> {
        _captureState.value = VideoCaptureState.Idle
        return Result.success(Unit)
    }

    override fun release() {
        _captureState.value = VideoCaptureState.Idle
    }
}
