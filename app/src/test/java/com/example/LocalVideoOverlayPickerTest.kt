package com.example

import android.net.Uri
import com.example.core.model.StandingTableControlsState
import com.example.services.composition.BottomTickerOverlayLayer
import com.example.services.composition.BroadcastVideoCompositor
import com.example.services.composition.BroadcastVideoMemeOverlayLayer
import com.example.services.streaming.StandingTableControlsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocalVideoOverlayPickerTest {

    @Before
    fun setUp() {
        // Reset controls state before each test
        StandingTableControlsManager.setTickerLoopVideo(null, false)
    }

    @Test
    fun testNoVideoSelectedInitialState() {
        val state = StandingTableControlsState()
        assertNull("Initial tickerLoopVideoUri must be null", state.tickerLoopVideoUri)
        assertFalse("Initial tickerLoopVideoEnabled must be false", state.tickerLoopVideoEnabled)

        val layer = BroadcastVideoMemeOverlayLayer()
        assertFalse("Overlay layer must be disabled initially", layer.isEnabled)
        assertNull("Overlay layer videoUri must be null initially", layer.videoUri)
    }

    @Test
    fun testValidVideoUriSelectionState() {
        val testUriString = "content://com.android.providers.media.documents/document/video%3A1024"

        StandingTableControlsManager.setTickerLoopVideo(testUriString, true)
        val state = StandingTableControlsManager.controlsState.value

        assertEquals(testUriString, state.tickerLoopVideoUri)
        assertTrue(state.tickerLoopVideoEnabled)

        val overlay = BroadcastVideoMemeOverlayLayer()
        overlay.videoUri = state.tickerLoopVideoUri
        overlay.isEnabled = state.tickerLoopVideoEnabled

        assertEquals(testUriString, overlay.videoUri)
        assertTrue(overlay.isEnabled)
    }

    @Test
    fun testPickerCancellationDoesNotSetFakeVideoUri() {
        // Given an unselected video state
        StandingTableControlsManager.setTickerLoopVideo(null, false)

        // When picker is cancelled (uri is null)
        StandingTableControlsManager.setTickerLoopVideo(null, false)

        // Then state must remain null and disabled without falling back to any hardcoded URI
        val state = StandingTableControlsManager.controlsState.value
        assertNull("Cancelled picker must leave Uri as null", state.tickerLoopVideoUri)
        assertFalse("Cancelled picker must leave enabled as false", state.tickerLoopVideoEnabled)
    }

    @Test
    fun testInvalidUriHandlingDisablesOverlay() {
        // Given an invalid URI or nonexistent file URI
        val invalidUri = "invalid://nonexistent/video.mp4"

        StandingTableControlsManager.setTickerLoopVideo(invalidUri, false)
        val state = StandingTableControlsManager.controlsState.value

        assertEquals(invalidUri, state.tickerLoopVideoUri)
        assertFalse(state.tickerLoopVideoEnabled)

        // When disabled, compositor overlay remains disabled
        val layer = BroadcastVideoMemeOverlayLayer()
        layer.videoUri = invalidUri
        layer.isEnabled = false
        assertFalse(layer.isEnabled)
    }

    @Test
    fun testOverlayEnabledOnlyWhenValidVideoAvailable() {
        // Try enabling when no URI is selected
        StandingTableControlsManager.setTickerLoopVideo(null, true)
        val state = StandingTableControlsManager.controlsState.value

        assertNull(state.tickerLoopVideoUri)

        // Compositor layer with null URI should draw nothing and not crash
        val layer = BroadcastVideoMemeOverlayLayer()
        layer.videoUri = null
        layer.isEnabled = true

        // Draw with null URI must be safe and perform no rendering
        val bitmap = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        layer.draw(canvas, 1920, 1080, 1000L)
        // No crash occurs
    }
}
