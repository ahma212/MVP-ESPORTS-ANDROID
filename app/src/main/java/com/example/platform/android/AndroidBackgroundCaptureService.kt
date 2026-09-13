package com.example.platform.android

import android.content.Context
import android.content.Intent
import com.example.services.streaming.IBackgroundCaptureService
import com.example.services.streaming.CaptureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidBackgroundCaptureService private constructor(private val context: Context) : IBackgroundCaptureService {

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    override val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _fps = MutableStateFlow(0)
    override val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _frameCount = MutableStateFlow(0L)
    override val frameCount: StateFlow<Long> = _frameCount.asStateFlow()

    private val _droppedFrames = MutableStateFlow(0L)
    override val droppedFrames: StateFlow<Long> = _droppedFrames.asStateFlow()

    private val _lastFrameTimestamp = MutableStateFlow(0L)
    override val lastFrameTimestamp: StateFlow<Long> = _lastFrameTimestamp.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: AndroidBackgroundCaptureService? = null

        fun getInstance(context: Context): AndroidBackgroundCaptureService {
            return INSTANCE ?: synchronized(this) {
                val instance = AndroidBackgroundCaptureService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    override fun startCapture(width: Int, height: Int, densityDpi: Int): Result<Unit> {
        // Foreground service is typically launched via MediaProjection permission activity handler
        return Result.success(Unit)
    }

    override fun stopCapture(): Result<Unit> {
        ScreenCaptureService.stopService(context)
        _captureState.value = CaptureState.Idle
        return Result.success(Unit)
    }

    fun updateMetrics(fps: Int, count: Long, dropped: Long, lastTs: Long, error: String?) {
        _fps.value = fps
        _frameCount.value = count
        _droppedFrames.value = dropped
        _lastFrameTimestamp.value = lastTs
        _lastError.value = error
        _captureState.value = if (ScreenCaptureService.isRunning) {
            CaptureState.Capturing(1080, 2400, fps)
        } else {
            CaptureState.Idle
        }
    }
}
