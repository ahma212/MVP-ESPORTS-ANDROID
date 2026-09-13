package com.example.services.scene

import com.example.core.model.EsportsScene
import com.example.core.model.SceneEngineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Production Scene State Machine manager for Esports Broadcast.
 * Manages explicit state transitions and timers for countdowns, breaks, matches, and endings.
 */
object EsportsSceneEngine {
    private val _sceneState = MutableStateFlow(SceneEngineState())
    val sceneState: StateFlow<SceneEngineState> = _sceneState.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun transitionTo(scene: EsportsScene) {
        timerJob?.cancel()
        _sceneState.update { current ->
            val newRemaining = when (scene) {
                EsportsScene.STARTING_COUNTDOWN -> current.countdownMinutes * 60
                EsportsScene.BREAK -> current.breakMinutes * 60
                EsportsScene.ENDING -> current.endingMinutes * 60
                else -> 0
            }
            current.copy(
                currentScene = scene,
                remainingCountdownSeconds = if (scene == EsportsScene.STARTING_COUNTDOWN) newRemaining else current.remainingCountdownSeconds,
                remainingBreakSeconds = if (scene == EsportsScene.BREAK) newRemaining else current.remainingBreakSeconds,
                remainingEndingSeconds = if (scene == EsportsScene.ENDING) newRemaining else current.remainingEndingSeconds,
                isRunning = scene == EsportsScene.STARTING_COUNTDOWN || scene == EsportsScene.BREAK || scene == EsportsScene.ENDING
            )
        }

        if (scene == EsportsScene.STARTING_COUNTDOWN || scene == EsportsScene.BREAK || scene == EsportsScene.ENDING) {
            startTimer(scene)
        }
    }

    fun setCountdownMinutes(minutes: Int) {
        _sceneState.update { it.copy(countdownMinutes = minutes, remainingCountdownSeconds = minutes * 60) }
    }

    fun setBreakMinutes(minutes: Int) {
        _sceneState.update { it.copy(breakMinutes = minutes, remainingBreakSeconds = minutes * 60) }
    }

    fun setEndingMinutes(minutes: Int) {
        _sceneState.update { it.copy(endingMinutes = minutes, remainingEndingSeconds = minutes * 60) }
    }

    private fun startTimer(scene: EsportsScene) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1000L)
                val current = _sceneState.value
                if (!current.isRunning) break

                when (scene) {
                    EsportsScene.STARTING_COUNTDOWN -> {
                        if (current.remainingCountdownSeconds > 1) {
                            _sceneState.update { it.copy(remainingCountdownSeconds = it.remainingCountdownSeconds - 1) }
                        } else {
                            // Countdown reached zero -> LIVE_MATCH (stream remains alive)
                            transitionTo(EsportsScene.LIVE_MATCH)
                            break
                        }
                    }
                    EsportsScene.BREAK -> {
                        if (current.remainingBreakSeconds > 1) {
                            _sceneState.update { it.copy(remainingBreakSeconds = it.remainingBreakSeconds - 1) }
                        } else {
                            // Break reached zero -> NEXT_MATCH_COUNTDOWN or LIVE_MATCH
                            transitionTo(EsportsScene.STARTING_COUNTDOWN)
                            break
                        }
                    }
                    EsportsScene.ENDING -> {
                        if (current.remainingEndingSeconds > 1) {
                            _sceneState.update { it.copy(remainingEndingSeconds = it.remainingEndingSeconds - 1) }
                        } else {
                            // Ending reached zero -> STOPPED (Only now is stream allowed to stop)
                            transitionTo(EsportsScene.STOPPED)
                            break
                        }
                    }
                    else -> break
                }
            }
        }
    }

    fun stopTimers() {
        timerJob?.cancel()
        _sceneState.update { it.copy(isRunning = false) }
    }

    fun pauseTimer() {
        stopTimers()
    }

    fun resumeTimer() {
        val current = _sceneState.value
        val scene = current.currentScene
        if (!current.isRunning && (scene == EsportsScene.STARTING_COUNTDOWN || scene == EsportsScene.BREAK || scene == EsportsScene.ENDING)) {
            _sceneState.update { it.copy(isRunning = true) }
            startTimer(scene)
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        _sceneState.update { current ->
            val scene = current.currentScene
            val resetSeconds = when (scene) {
                EsportsScene.STARTING_COUNTDOWN -> current.countdownMinutes * 60
                EsportsScene.BREAK -> current.breakMinutes * 60
                EsportsScene.ENDING -> current.endingMinutes * 60
                else -> 0
            }
            current.copy(
                remainingCountdownSeconds = if (scene == EsportsScene.STARTING_COUNTDOWN) resetSeconds else current.remainingCountdownSeconds,
                remainingBreakSeconds = if (scene == EsportsScene.BREAK) resetSeconds else current.remainingBreakSeconds,
                remainingEndingSeconds = if (scene == EsportsScene.ENDING) resetSeconds else current.remainingEndingSeconds,
                isRunning = false
            )
        }
    }
}
