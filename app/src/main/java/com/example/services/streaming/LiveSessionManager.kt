package com.example.services.streaming

import android.content.Context
import com.example.platform.android.AndroidBackgroundCaptureService
import com.example.platform.android.ScreenCaptureService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Coordinated central session manager implementing ILiveSessionManager.
 */
class LiveSessionManager private constructor(private val context: Context) : ILiveSessionManager {

    private val youtubeService: IYouTubeLiveService = YouTubeLiveService.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _sessionState = MutableStateFlow(UnifiedSessionState())
    override val sessionState: StateFlow<UnifiedSessionState> = _sessionState.asStateFlow()

    private var monitorJob: Job? = null

    init {
        startSyncLoop()
    }

    companion object {
        @Volatile
        private var INSTANCE: LiveSessionManager? = null

        fun getInstance(context: Context): LiveSessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = LiveSessionManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private fun startSyncLoop() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (true) {
                try {
                    val captureService = ScreenCaptureService.activeInstance
                    val isCaptureActive = ScreenCaptureService.isRunning

                    val ytState = youtubeService.sessionState.value
                    val ytAuthorized = youtubeService.isAuthorized.value
                    val ytChannel = youtubeService.channelInfo.value
                    val errorMsg = youtubeService.errorMessage.value ?: captureService?.getLastError()

                    val uptime = if (isCaptureActive && captureService != null) {
                        val startTime = captureService.getCaptureStartTime()
                        if (startTime > 0) (System.currentTimeMillis() - startTime) / 1000L else 0L
                    } else {
                        0L
                    }

                    val fpsVal = captureService?.getFps() ?: 0
                    val frameCountVal = captureService?.getFrameCount() ?: 0L
                    val droppedVal = captureService?.getDroppedFrames() ?: 0L
                    val lastTsVal = captureService?.getLastFrameTimestamp() ?: 0L

                    // Update platform capture proxy
                    AndroidBackgroundCaptureService.getInstance(context).updateMetrics(
                        fps = fpsVal,
                        count = frameCountVal,
                        dropped = droppedVal,
                        lastTs = lastTsVal,
                        error = errorMsg
                    )

                    _sessionState.value = UnifiedSessionState(
                        isServiceRunning = isCaptureActive,
                        isCaptureActive = isCaptureActive,
                        captureWidth = captureService?.getWidth() ?: 0,
                        captureHeight = captureService?.getHeight() ?: 0,
                        capturedFps = fpsVal,
                        frameCount = frameCountVal,
                        droppedFrames = droppedVal,
                        lastFrameTimestamp = lastTsVal,
                        youtubeAuthorized = ytAuthorized,
                        youtubeAccountName = ytChannel?.title ?: "Not Connected",
                        youtubeBroadcastState = ytState,
                        uptimeSeconds = uptime,
                        diagnosticError = errorMsg
                    )
                } catch (e: Exception) {
                    // Fail-safe protection
                }
                delay(1000)
            }
        }
    }

    override fun startCoordinatedSession(): Result<Unit> {
        val currentBroadcast = youtubeService.currentBroadcast.value
        val rtmpUrl = currentBroadcast?.rtmpIngestUrl
        val state = com.example.services.station.StationDeskManager.stationState.value
        val pipeRes = BroadcastStreamingPipeline.getInstance(context).startPipeline(
            context = context,
            rtmpUrl = rtmpUrl,
            width = state.resolution.width,
            height = state.resolution.height,
            bitrate = state.quality.bitrate,
            fps = state.fps.fps
        )
        if (pipeRes.isFailure) {
            return pipeRes
        }

        if (currentBroadcast?.broadcastId != null && youtubeService.isAuthorized.value) {
            scope.launch {
                val startRes = youtubeService.startBroadcast(currentBroadcast.broadcastId)
                if (startRes.isFailure) {
                    BroadcastStreamingPipeline.getInstance(context).stopPipeline()
                }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun stopCoordinatedSession(): Result<Unit> {
        return try {
            youtubeService.stopBroadcast()
            BroadcastStreamingPipeline.getInstance(context).stopPipeline()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun destroySession() {
        monitorJob?.cancel()
    }
}
