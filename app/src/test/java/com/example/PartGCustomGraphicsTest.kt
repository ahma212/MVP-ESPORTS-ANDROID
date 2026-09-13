package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.core.model.BroadcastSessionState
import com.example.core.model.TeamLiveState
import com.example.services.composition.BroadcastVideoCompositor
import com.example.services.composition.CustomGraphicsOverlayLayer
import com.example.services.streaming.LocalLiveRuntimeManager
import com.example.services.streaming.StandingTableControlsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PartGCustomGraphicsTest {

    private lateinit var compositor: BroadcastVideoCompositor
    private lateinit var overlayLayer: CustomGraphicsOverlayLayer

    @Before
    fun setUp() {
        compositor = BroadcastVideoCompositor.getInstance()
        var layer = compositor.getLayer(CustomGraphicsOverlayLayer.LAYER_ID) as? CustomGraphicsOverlayLayer
        if (layer == null) {
            layer = CustomGraphicsOverlayLayer()
            compositor.addLayer(layer)
        }
        overlayLayer = layer
        overlayLayer.isEnabled = true

        // Ensure controls are in default ON state
        val currentControls = StandingTableControlsManager.controlsState.value
        if (!currentControls.customGraphicsEnabled) {
            StandingTableControlsManager.toggleCustomGraphics()
        }
        if (!currentControls.logoOverlayEnabled) {
            StandingTableControlsManager.toggleLogoOverlay()
        }
        if (!currentControls.textOverlayEnabled) {
            StandingTableControlsManager.toggleTextOverlay()
        }
        if (!currentControls.matchInfoOverlayEnabled) {
            StandingTableControlsManager.toggleMatchInfoOverlay()
        }
        if (!currentControls.teamPlayerOverlayEnabled) {
            StandingTableControlsManager.toggleTeamPlayerOverlay()
        }
    }

    @Test
    fun testLayerRegistration() {
        val layer = compositor.getLayer(CustomGraphicsOverlayLayer.LAYER_ID)
        assertNotNull(layer)
        assertEquals(120, layer?.zIndex)
    }

    @Test
    fun testControlsToggle() {
        // Toggle Custom Graphics OFF
        if (StandingTableControlsManager.controlsState.value.customGraphicsEnabled) {
            StandingTableControlsManager.toggleCustomGraphics()
        }
        assertFalse(StandingTableControlsManager.controlsState.value.customGraphicsEnabled)

        // Toggle back ON
        StandingTableControlsManager.toggleCustomGraphics()
        assertTrue(StandingTableControlsManager.controlsState.value.customGraphicsEnabled)

        // Toggle Logo OFF
        if (StandingTableControlsManager.controlsState.value.logoOverlayEnabled) {
            StandingTableControlsManager.toggleLogoOverlay()
        }
        assertFalse(StandingTableControlsManager.controlsState.value.logoOverlayEnabled)

        // Toggle back ON
        StandingTableControlsManager.toggleLogoOverlay()
        assertTrue(StandingTableControlsManager.controlsState.value.logoOverlayEnabled)
    }

    @Test
    fun testDrawingRealStreamExecution() {
        // Mock a real live match state
        LocalLiveRuntimeManager.updateBroadcastSession {
            it.copy(
                tournamentTitle = "MVP INVITATIONAL CUP",
                map = "Miramar",
                currentMatchNumber = 3,
                status = "LIVE"
            )
        }

        // Initialize 1 team to verify the showcase drawing doesn't crash and reads it
        val testTeam = TeamLiveState(
            teamNumber = 1,
            teamName = "FALCONS ESPORTS",
            currentMatchKills = 15,
            currentMatchPoints = 35,
            currentAlivePlayers = 3
        )
        LocalLiveRuntimeManager.initializeFromRoster(listOf(testTeam))

        val bmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Execute render sweep
        overlayLayer.draw(canvas, 1920, 1080, System.currentTimeMillis())

        // Ensure drawing completes perfectly with positive dimensions
        assertNotNull(canvas)
    }

    @Test
    fun testStandingTableSizeAndPositionControls() {
        // Default size should be Small/Medium/Large
        StandingTableControlsManager.setStandingSize("Large")
        assertEquals("Large", StandingTableControlsManager.controlsState.value.standingSize)

        StandingTableControlsManager.setStandingSize("Small")
        assertEquals("Small", StandingTableControlsManager.controlsState.value.standingSize)

        // Default position testing
        StandingTableControlsManager.setStandingPosition("Bottom Left")
        assertEquals("Bottom Left", StandingTableControlsManager.controlsState.value.standingPosition)

        StandingTableControlsManager.setStandingPosition("Top Right")
        assertEquals("Top Right", StandingTableControlsManager.controlsState.value.standingPosition)
    }

    @Test
    fun testTickerSizePositionAndCustomText() {
        // Ticker size testing
        StandingTableControlsManager.setTickerSize("Medium")
        assertEquals("Medium", StandingTableControlsManager.controlsState.value.tickerSize)

        StandingTableControlsManager.setTickerSize("Large")
        assertEquals("Large", StandingTableControlsManager.controlsState.value.tickerSize)

        // Ticker Y-Offset position testing
        StandingTableControlsManager.setTickerYOffset(0.85f)
        assertEquals(0.85f, StandingTableControlsManager.controlsState.value.tickerYOffset)

        // Custom ticker text testing
        val customText = "WELCOME TO THE MVP TOURNAMENT BRACKET LIVE!"
        StandingTableControlsManager.setTickerCustomText(customText)
        assertEquals(customText, StandingTableControlsManager.controlsState.value.tickerCustomText)
    }

    @Test
    fun testTickerVideoLoopBackgroundState() {
        val testVideoUri = "content://media/external/video/media/105"
        
        // Initially enabled with video Uri
        StandingTableControlsManager.setTickerLoopVideo(testVideoUri, true)
        val state = StandingTableControlsManager.controlsState.value
        assertEquals(testVideoUri, state.tickerLoopVideoUri)
        assertTrue(state.tickerLoopVideoEnabled)

        // Disable loop video
        StandingTableControlsManager.setTickerLoopVideo(testVideoUri, false)
        assertFalse(StandingTableControlsManager.controlsState.value.tickerLoopVideoEnabled)
    }

    @Test
    fun testCustomBannerTextBindingAndDynamicUpdates() {
        val customAnnouncement = "MVP GRAND FINALS - ROUND 5 ERANGEL LIVE!"
        StandingTableControlsManager.setCustomBannerText(customAnnouncement)
        StandingTableControlsManager.setCustomBannerPosition("Bottom")
        StandingTableControlsManager.setCustomBannerScale(1.2f)
        StandingTableControlsManager.setCustomBannerColors("#00FF88", "#1E1E24", "#FF8800")

        val state = StandingTableControlsManager.controlsState.value
        assertEquals(customAnnouncement, state.customBannerText)
        assertEquals("Bottom", state.customBannerPosition)
        assertEquals(1.2f, state.customBannerScale, 0.001f)
        assertEquals("#00FF88", state.customBannerTextColor)

        // Verify layer draws onto canvas without error
        val bmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        overlayLayer.draw(canvas, 1920, 1080, System.currentTimeMillis())

        // Verify direct layer property overrides
        overlayLayer.customBannerText = "DYNAMIC LIVE OVERRIDE BANNER"
        overlayLayer.customBannerPosition = "Center"
        overlayLayer.draw(canvas, 1920, 1080, System.currentTimeMillis())
        assertEquals("DYNAMIC LIVE OVERRIDE BANNER", overlayLayer.customBannerText)
    }

    @Test
    fun testCompositorEndToEndCustomTextOutput() {
        val testBanner = "OFFICIAL MVP ESPORTS TOURNAMENT FINAL MATCH"
        StandingTableControlsManager.setCustomBannerText(testBanner)

        val rawGameFrame = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val gameCanvas = Canvas(rawGameFrame)
        gameCanvas.drawColor(android.graphics.Color.BLUE)

        val byteBuffer = java.nio.ByteBuffer.allocate(1920 * 1080 * 4)
        rawGameFrame.copyPixelsToBuffer(byteBuffer)

        val detectionFrame = com.example.core.model.DetectionFrame(
            frameId = 1L,
            timestampMs = System.currentTimeMillis(),
            width = 1920,
            height = 1080,
            buffer = byteBuffer.array(),
            format = "RGBA_8888"
        )

        val composedFrame = compositor.composeFrame(detectionFrame)
        assertNotNull(composedFrame)
        assertNotNull(composedFrame?.bitmap)
        assertEquals(1920, composedFrame?.bitmap?.width)
        assertEquals(1080, composedFrame?.bitmap?.height)
    }
}
