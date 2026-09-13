package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.services.audio.AudioMixerManager
import com.example.services.audio.LocalMusicPlayerManager
import com.example.services.audio.MusicPcmDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit & Integration Test Suite for PART 7 — Music Audio Track Selection Fix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Part7MusicTrackSelectionTest {

    @Test
    fun testMusicMixerAndDecoderIndependentControls() {
        AudioMixerManager.setMusicVolume(0.75f)
        AudioMixerManager.toggleMusicMute()
        val stateMuted = AudioMixerManager.mixerState.value
        assertTrue(stateMuted.musicMuted)
        assertEquals(0.75f, stateMuted.musicVolume)

        AudioMixerManager.toggleMusicMute()
        val stateUnmuted = AudioMixerManager.mixerState.value
        assertTrue(!stateUnmuted.musicMuted)

        // Ensure starting state is looping = true
        if (!LocalMusicPlayerManager.musicState.value.isLooping) {
            LocalMusicPlayerManager.toggleLoop()
        }
        LocalMusicPlayerManager.toggleLoop()
        val playerState = LocalMusicPlayerManager.musicState.value
        assertTrue(!playerState.isLooping)
        LocalMusicPlayerManager.toggleLoop()
        assertTrue(LocalMusicPlayerManager.musicState.value.isLooping)
    }

    @Test
    fun testMusicPcmDecoderGracefulFailureOnInvalidUri() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val invalidUri = Uri.parse("content://invalid/nonexistent.mp3")
        val decoder = MusicPcmDecoder(context, invalidUri)

        var threwException = false
        try {
            decoder.start()
        } catch (e: Exception) {
            threwException = true
        }
        assertTrue("Expected graceful failure/exception when starting decoder with invalid media", threwException)
        decoder.stop()
    }

    @Test
    fun testDecoderResourceCleanupOnStop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val invalidUri = Uri.parse("content://invalid/nonexistent.mp3")
        val decoder = MusicPcmDecoder(context, invalidUri)
        // Ensure stop() does not throw even if never started or failed
        decoder.stop()
        assertTrue(true)
    }
}
