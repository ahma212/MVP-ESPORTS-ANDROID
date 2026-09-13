package com.example.platform.android

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.core.model.DetectionFrame
import com.example.services.video.IVideoInputService
import com.example.services.video.VideoCaptureState
import com.example.services.video.VideoSourceType
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android implementation of IVideoInputService orchestrating real Android MediaProjection
 * via ScreenCaptureService.
 */
class AndroidScreenCaptureService(
    private val context: Context
) : IVideoInputService {

    override val captureState: StateFlow<VideoCaptureState> = ScreenCaptureService.captureState
    override val frameFlow: SharedFlow<DetectionFrame> = ScreenCaptureService.frameFlow

    override val activeSource: VideoSourceType
        get() = if (captureState.value is VideoCaptureState.Capturing) {
            VideoSourceType.ANDROID_MEDIA_PROJECTION
        } else {
            VideoSourceType.NONE
        }

    private var pendingResultCode: Int = 0
    private var pendingResultData: Intent? = null

    /**
     * Attaches authorized MediaProjection permission result intent received from activity launcher.
     */
    fun attachMediaProjectionIntent(resultCode: Int, data: Intent) {
        pendingResultCode = resultCode
        pendingResultData = data
    }

    /**
     * Starts the real foreground screen capture service.
     */
    override suspend fun startCapture(): Result<Unit> {
        val resultCode = pendingResultCode
        val resultData = pendingResultData

        if (resultCode == 0 || resultData == null) {
            return Result.failure(IllegalStateException("MediaProjection permission has not been granted or attached."))
        }

        return try {
            val displayMetrics = context.resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val densityDpi = displayMetrics.densityDpi

            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
                putExtra(ScreenCaptureService.EXTRA_WIDTH, width)
                putExtra(ScreenCaptureService.EXTRA_HEIGHT, height)
                putExtra(ScreenCaptureService.EXTRA_DENSITY_DPI, densityDpi)
            }

            ContextCompat.startForegroundService(context, intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Stops the real foreground capture service and tears down hardware resources.
     */
    override suspend fun stopCapture(): Result<Unit> {
        return try {
            ScreenCaptureService.stopService(context)
            pendingResultCode = 0
            pendingResultData = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun release() {
        ScreenCaptureService.stopService(context)
        pendingResultCode = 0
        pendingResultData = null
    }
}
