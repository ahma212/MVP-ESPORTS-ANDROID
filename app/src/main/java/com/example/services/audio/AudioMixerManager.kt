package com.example.services.audio

import android.content.Context
import android.media.AudioManager
import com.example.core.model.AudioMixerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Production Audio Mixer Layer supporting separate channels:
 * - Game Audio
 * - Mic / Commentary
 * - Music
 * Volumes 0-100% and individual Mute/Unmute controls, routing to actual player pipelines.
 */
object AudioMixerManager {
    private val _mixerState = MutableStateFlow(AudioMixerState())
    val mixerState: StateFlow<AudioMixerState> = _mixerState.asStateFlow()

    fun setGameVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _mixerState.update { it.copy(gameVolume = clamped) }
    }

    fun setMicVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _mixerState.update { it.copy(micVolume = clamped) }
    }

    fun setMusicVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _mixerState.update { current ->
            LocalMusicPlayerManager.updateVolume(clamped, current.musicMuted)
            current.copy(musicVolume = clamped)
        }
    }

    fun toggleGameMute() {
        _mixerState.update { it.copy(gameMuted = !it.gameMuted) }
    }

    fun toggleMicMute() {
        _mixerState.update { it.copy(micMuted = !it.micMuted) }
    }

    fun toggleMusicMute() {
        _mixerState.update {
            val newMute = !it.musicMuted
            LocalMusicPlayerManager.updateVolume(it.musicVolume, newMute)
            it.copy(musicMuted = newMute)
        }
    }

    /**
     * Applies mixer state to system audio manager streams and sub-managers.
     */
    fun applyToSystem(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val state = _mixerState.value
            val musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val effectiveMusicVol = if (state.musicMuted) 0 else (musicMax * state.musicVolume).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, effectiveMusicVol, 0)
        } catch (e: Exception) {
            // Ignore restricted audio manager calls in sandbox environments
        }
    }
}

