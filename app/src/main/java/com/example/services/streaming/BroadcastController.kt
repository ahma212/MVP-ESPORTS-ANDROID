package com.example.services.streaming

import android.content.Context
import com.example.core.model.EsportsScene
import com.example.services.scene.EsportsSceneEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BroadcastLifecycleState {
    IDLE,
    READY,
    LIVE,
    PAUSED,
    COMPLETED,
    ERROR
}

class BroadcastController private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val sessionManager = LiveSessionManager.getInstance(context)
    private val pipeline = BroadcastStreamingPipeline.getInstance(context)

    private val _broadcastState = MutableStateFlow(BroadcastLifecycleState.IDLE)
    val broadcastState: StateFlow<BroadcastLifecycleState> = _broadcastState.asStateFlow()

    private val _broadcastError = MutableStateFlow<String?>(null)
    val broadcastError: StateFlow<String?> = _broadcastError.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: BroadcastController? = null

        fun getInstance(context: Context): BroadcastController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BroadcastController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun prepareBroadcast() {
        if (_broadcastState.value == BroadcastLifecycleState.IDLE || _broadcastState.value == BroadcastLifecycleState.COMPLETED) {
            _broadcastError.value = null
            _broadcastState.value = BroadcastLifecycleState.READY
        }
    }

    fun startBroadcast(): Result<Unit> {
        if (_broadcastState.value != BroadcastLifecycleState.READY && _broadcastState.value != BroadcastLifecycleState.ERROR) {
            return Result.failure(IllegalStateException("Cannot start from state ${_broadcastState.value}"))
        }

        _broadcastError.value = null
        val startResult = sessionManager.startCoordinatedSession()
        
        return if (startResult.isSuccess) {
            _broadcastState.value = BroadcastLifecycleState.LIVE
            Result.success(Unit)
        } else {
            _broadcastError.value = startResult.exceptionOrNull()?.message ?: "Startup failed"
            _broadcastState.value = BroadcastLifecycleState.ERROR
            startResult
        }
    }

    fun pauseBroadcast(): Result<Unit> {
        if (_broadcastState.value != BroadcastLifecycleState.LIVE) {
            return Result.failure(IllegalStateException("Only LIVE broadcasts can be paused"))
        }

        val pauseResult = pipeline.stopPipeline() // Actually stop transport
        if (pauseResult.isSuccess) {
            _broadcastState.value = BroadcastLifecycleState.PAUSED
            return Result.success(Unit)
        } else {
            _broadcastError.value = pauseResult.exceptionOrNull()?.message ?: "Pause failed"
            return pauseResult
        }
    }

    fun resumeBroadcast(): Result<Unit> {
        if (_broadcastState.value != BroadcastLifecycleState.PAUSED) {
            return Result.failure(IllegalStateException("Only PAUSED broadcasts can be resumed"))
        }

        val resumeResult = sessionManager.startCoordinatedSession()
        if (resumeResult.isSuccess) {
            _broadcastState.value = BroadcastLifecycleState.LIVE
            return Result.success(Unit)
        } else {
            _broadcastError.value = resumeResult.exceptionOrNull()?.message ?: "Resume failed"
            return resumeResult
        }
    }

    fun nextMatch(): Result<Unit> {
        if (_broadcastState.value != BroadcastLifecycleState.LIVE && _broadcastState.value != BroadcastLifecycleState.PAUSED) {
             return Result.failure(IllegalStateException("Cannot transition match from ${_broadcastState.value}"))
        }
        
        val liveState = LocalLiveRuntimeManager.broadcastState.value
        val currentMatchNum = liveState.currentMatchNumber
        val totalMatches = liveState.totalMatches
        if (totalMatches > 0 && currentMatchNum >= totalMatches) {
             return Result.failure(IllegalStateException("No next match available (Series completed: Match $currentMatchNum of $totalMatches)."))
        }

        // 1. Move to Match End Scene
        EsportsSceneEngine.transitionTo(EsportsScene.ENDING)
        
        // 2. Load next match & reset match statistics while preserving cumulative tournament totals
        LocalLiveRuntimeManager.startNextMatch()
        
        // 3. Return to live gameplay
        EsportsSceneEngine.transitionTo(EsportsScene.LIVE_MATCH)
        
        return Result.success(Unit)
    }

    fun endBroadcast(): Result<Unit> {
        if (_broadcastState.value == BroadcastLifecycleState.COMPLETED) {
            return Result.success(Unit)
        }

        _broadcastState.value = BroadcastLifecycleState.COMPLETED
        
        scope.launch {
            val stopResult = sessionManager.stopCoordinatedSession()
            if (stopResult.isFailure) {
                _broadcastError.value = stopResult.exceptionOrNull()?.message ?: "End cleanup failed"
                _broadcastState.value = BroadcastLifecycleState.ERROR
            }
        }
        
        return Result.success(Unit)
    }
}
