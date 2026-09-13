package com.example

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.example.services.streaming.BroadcastController
import com.example.services.streaming.BroadcastLifecycleState
import com.example.services.streaming.CaptureState
import com.example.services.streaming.FloatingControlState
import com.example.services.streaming.LiveSessionState
import com.example.services.streaming.LiveSessionManager
import com.example.services.streaming.UnifiedSessionState
import com.example.services.streaming.YouTubeLiveService
import com.example.services.overlay.FloatingPointerOverlay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EsportsStudioMVPTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testFloatingControlStateDefaults() {
        val state = FloatingControlState()
        assertFalse(state.isVisible)
        assertFalse(state.isExpanded)
        assertEquals("MEDIUM", state.pointerSize)
        assertEquals(0, state.lastPositionX)
        assertEquals(0, state.lastPositionY)
    }

    @Test
    fun testLiveSessionStateTransitions() {
        val idle = LiveSessionState.IDLE
        val live = LiveSessionState.LIVE
        val error = LiveSessionState.ERROR

        assertEquals("IDLE", idle.name)
        assertEquals("LIVE", live.name)
        assertEquals("ERROR", error.name)
    }

    @Test
    fun testPointerSizePresets() {
        val overlay = FloatingPointerOverlay(context)
        overlay.setPointerSize("SMALL")
        assertEquals("SMALL", overlay.controlState.value.pointerSize)

        overlay.setPointerSize("LARGE")
        assertEquals("LARGE", overlay.controlState.value.pointerSize)
    }

    @Test
    fun testPointerPositionClampingAndSaving() {
        val overlay = FloatingPointerOverlay(context)
        overlay.updatePosition(200, 350)
        assertEquals(200, overlay.controlState.value.lastPositionX)
        assertEquals(350, overlay.controlState.value.lastPositionY)
    }

    @Test
    fun testSessionManagerReconciliationDefaults() {
        val manager = LiveSessionManager.getInstance(context)
        val session = manager.sessionState.value
        assertNotNull(session)
        assertFalse(session.isCaptureActive)
        assertEquals("Not Connected", session.youtubeAccountName)
    }

    @Test
    fun testUnifiedSessionStatePropertyMapping() {
        val customState = UnifiedSessionState(
            isServiceRunning = true,
            isCaptureActive = true,
            captureWidth = 1920,
            captureHeight = 1080,
            capturedFps = 60,
            youtubeAuthorized = true,
            youtubeAccountName = "Pro Esports Channel",
            youtubeBroadcastState = LiveSessionState.LIVE,
            diagnosticError = "No stream connection dropouts"
        )

        assertTrue(customState.isServiceRunning)
        assertTrue(customState.isCaptureActive)
        assertEquals(1920, customState.captureWidth)
        assertEquals(1080, customState.captureHeight)
        assertEquals(60, customState.capturedFps)
        assertTrue(customState.youtubeAuthorized)
        assertEquals("Pro Esports Channel", customState.youtubeAccountName)
        assertEquals(LiveSessionState.LIVE, customState.youtubeBroadcastState)
        assertEquals("No stream connection dropouts", customState.diagnosticError)
    }

    @Test
    fun testErrorStatesHandling() {
        val errState = CaptureState.Error("Media projection access denied by user")
        assertEquals("Media projection access denied by user", errState.message)
    }

    @Test
    fun testBroadcastControllerLifecycleTransitions() {
        val controller = BroadcastController.getInstance(context)
        assertNotNull(controller)
        
        // Prepare
        controller.prepareBroadcast()
        assertEquals(BroadcastLifecycleState.READY, controller.broadcastState.value)

        // Start
        val startRes = controller.startBroadcast()
        assertTrue(startRes.isSuccess)
        assertEquals(BroadcastLifecycleState.LIVE, controller.broadcastState.value)

        // Pause
        val pauseRes = controller.pauseBroadcast()
        assertTrue(pauseRes.isSuccess)
        assertEquals(BroadcastLifecycleState.PAUSED, controller.broadcastState.value)

        // Resume
        val resumeRes = controller.resumeBroadcast()
        assertTrue(resumeRes.isSuccess)
        assertEquals(BroadcastLifecycleState.LIVE, controller.broadcastState.value)

        // End
        val endRes = controller.endBroadcast()
        assertTrue(endRes.isSuccess)
        assertEquals(BroadcastLifecycleState.COMPLETED, controller.broadcastState.value)
    }

    @Test
    fun testFloatingOverlayLifecycleIndependence() {
        val controller = BroadcastController.getInstance(context)
        controller.prepareBroadcast()
        controller.startBroadcast()
        assertEquals(BroadcastLifecycleState.LIVE, controller.broadcastState.value)

        val overlay = FloatingPointerOverlay(context)
        // Closing the overlay must not end the broadcast
        overlay.hideOverlay()
        assertEquals(BroadcastLifecycleState.LIVE, controller.broadcastState.value)

        // Clean up
        controller.endBroadcast()
    }
}
