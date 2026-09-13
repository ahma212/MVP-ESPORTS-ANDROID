package com.example.services.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MusicPlayerState(
    val isPlaying: Boolean = false,
    val mediaUri: String? = null,
    val songTitle: String = "No music loaded",
    val isLooping: Boolean = true
)

/**
 * Local Music Player Manager handling local audio file playback,
 * play, pause, stop, loop, and volume routing through the audio mixer.
 */
object LocalMusicPlayerManager {
    private val _musicState = MutableStateFlow(MusicPlayerState())
    val musicState: StateFlow<MusicPlayerState> = _musicState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    fun loadAndPlay(context: Context, uri: Uri, title: String) {
        try {
            stop()
            mediaPlayer = MediaPlayer.create(context, uri).apply {
                isLooping = _musicState.value.isLooping
                val mixer = AudioMixerManager.mixerState.value
                val effectiveVol = if (mixer.musicMuted) 0f else mixer.musicVolume
                setVolume(effectiveVol, effectiveVol)
                setOnCompletionListener {
                    if (!isLooping) {
                        stop()
                    }
                }
                start()
            }
            _musicState.update { it.copy(isPlaying = true, mediaUri = uri.toString(), songTitle = title) }
        } catch (e: Exception) {
            _musicState.update { it.copy(isPlaying = false, songTitle = "Error playing audio: ${e.message}") }
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _musicState.update { it.copy(isPlaying = false) }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun resume() {
        try {
            if (mediaPlayer != null && mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
                _musicState.update { it.copy(isPlaying = true) }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun clear() {
        stop()
        _musicState.update { it.copy(isPlaying = false, mediaUri = null, songTitle = "No music loaded") }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaPlayer = null
        _musicState.update { it.copy(isPlaying = false, mediaUri = null, songTitle = "Music stopped") }
    }

    fun toggleLoop() {
        val newLoop = !_musicState.value.isLooping
        mediaPlayer?.isLooping = newLoop
        _musicState.update { it.copy(isLooping = newLoop) }
    }

    fun updateVolume(volume: Float, muted: Boolean) {
        try {
            val vol = if (muted) 0f else volume.coerceIn(0f, 1f)
            mediaPlayer?.setVolume(vol, vol)
        } catch (e: Exception) {
            // ignore
        }
    }
}
