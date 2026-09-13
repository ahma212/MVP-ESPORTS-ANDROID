package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.example.core.model.DetectionFrame
import com.example.core.model.PlayerLiveState
import com.example.core.model.TeamLiveState
import com.example.services.composition.BottomTickerOverlayLayer
import com.example.services.composition.BroadcastVideoCompositor
import com.example.services.composition.IBroadcastLayer
import com.example.services.composition.OverallStandingOverlayLayer
import com.example.services.streaming.LocalLiveRuntimeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
class PartABroadcastCompositorTest {

    private lateinit var compositor: BroadcastVideoCompositor

    @Before
    fun setUp() {
        compositor = BroadcastVideoCompositor.getInstance()
        // Reset layers to default enabled state
        compositor.setStandingOverlayEnabled(true)
        compositor.setTickerOverlayEnabled(true)
        compositor.removeLayer("scene_graphics_overlay")
        compositor.removeLayer("vip_milestone_card")
        compositor.removeLayer("custom_graphics_overlay")

        // Seed 4 test teams with realistic tournament data
        val testTeams = (1..4).map { teamNum ->
            TeamLiveState(
                teamNumber = teamNum,
                teamName = "TEAM $teamNum",
                currentMatchKills = teamNum * 3,
                currentMatchPoints = teamNum * 10,
                rank = teamNum,
                currentAlivePlayers = 4,
                players = (1..4).map { pIdx ->
                    PlayerLiveState(
                        playerName = "P_${teamNum}_$pIdx",
                        teamNumber = teamNum,
                        eliminated = false,
                        knocked = false
                    )
                }
            )
        }
        LocalLiveRuntimeManager.initializeFromRoster(testTeams)
    }

    private fun createDummyPubgFrame(width: Int = 1280, height: Int = 720, frameId: Long = 100L): DetectionFrame {
        val totalBytes = width * height * 4
        val buffer = ByteArray(totalBytes)
        // Fill with a distinct PUBG green/dark tint (e.g. #1E3A2F)
        for (i in 0 until totalBytes step 4) {
            buffer[i] = 0x1E.toByte()     // R
            buffer[i + 1] = 0x3A.toByte() // G
            buffer[i + 2] = 0x2F.toByte() // B
            buffer[i + 3] = 0xFF.toByte() // A
        }
        return DetectionFrame(
            frameId = frameId,
            timestampMs = System.currentTimeMillis(),
            width = width,
            height = height,
            format = "RGBA_8888",
            buffer = buffer,
            sourceIdentifier = "android_screen_capture"
        )
    }

    @Test
    fun testCompositorInitializesWithDefaultLayers() {
        val layers = compositor.getAllLayers()
        assertTrue("Compositor must have registered layers", layers.isNotEmpty())
        assertNotNull(compositor.getLayer(OverallStandingOverlayLayer.LAYER_ID))
        assertNotNull(compositor.getLayer(BottomTickerOverlayLayer.LAYER_ID))

        // Check zIndex ordering (Standing 100 < Ticker 200)
        assertTrue(compositor.standingOverlay.zIndex < compositor.tickerOverlay.zIndex)
    }

    @Test
    fun testRealCapturedPubgFrameBecomesBaseLayer() {
        val frame = createDummyPubgFrame(width = 800, height = 600, frameId = 1L)
        val composed = compositor.composeFrame(frame)

        assertNotNull("Composed broadcast frame must not be null", composed)
        assertEquals(frame.frameId, composed!!.frameId)
        assertEquals(frame.width, composed.width)
        assertEquals(frame.height, composed.height)
        assertNotNull("Composed output must contain a valid Bitmap", composed.bitmap)
        assertFalse("Composed bitmap must not be recycled", composed.bitmap.isRecycled)
        assertEquals(800, composed.bitmap.width)
        assertEquals(600, composed.bitmap.height)

        // Verify latest composed frame is updated
        assertEquals(composed, compositor.latestComposedFrame.value)
    }

    @Test
    fun testOverallStandingLayerCanBeToggled() {
        val frame = createDummyPubgFrame()

        // 1. Enabled: activeLayers should include standing overlay
        compositor.setStandingOverlayEnabled(true)
        val composedOn = compositor.composeFrame(frame)
        assertNotNull(composedOn)
        assertTrue(composedOn!!.activeLayers.contains(OverallStandingOverlayLayer.LAYER_ID))

        // 2. Disabled: activeLayers should exclude standing overlay
        compositor.setStandingOverlayEnabled(false)
        val composedOff = compositor.composeFrame(frame)
        assertNotNull(composedOff)
        assertFalse(composedOff!!.activeLayers.contains(OverallStandingOverlayLayer.LAYER_ID))
    }

    @Test
    fun testBottomTickerLayerCanBeToggled() {
        val frame = createDummyPubgFrame()

        // 1. Enabled: activeLayers should include bottom ticker
        compositor.setTickerOverlayEnabled(true)
        val composedOn = compositor.composeFrame(frame)
        assertNotNull(composedOn)
        assertTrue(composedOn!!.activeLayers.contains(BottomTickerOverlayLayer.LAYER_ID))

        // 2. Disabled: activeLayers should exclude bottom ticker
        compositor.setTickerOverlayEnabled(false)
        val composedOff = compositor.composeFrame(frame)
        assertNotNull(composedOff)
        assertFalse(composedOff!!.activeLayers.contains(BottomTickerOverlayLayer.LAYER_ID))
    }

    @Test
    fun testIndependentLayerControls() {
        val frame = createDummyPubgFrame()

        // Case A: Both ON
        compositor.setStandingOverlayEnabled(true)
        compositor.setTickerOverlayEnabled(true)
        var result = compositor.composeFrame(frame)
        assertEquals(2, result!!.activeLayers.size)
        assertTrue(result.activeLayers.contains(OverallStandingOverlayLayer.LAYER_ID))
        assertTrue(result.activeLayers.contains(BottomTickerOverlayLayer.LAYER_ID))

        // Case B: Only Standing ON
        compositor.setStandingOverlayEnabled(true)
        compositor.setTickerOverlayEnabled(false)
        result = compositor.composeFrame(frame)
        assertEquals(1, result!!.activeLayers.size)
        assertTrue(result.activeLayers.contains(OverallStandingOverlayLayer.LAYER_ID))

        // Case C: Only Ticker ON
        compositor.setStandingOverlayEnabled(false)
        compositor.setTickerOverlayEnabled(true)
        result = compositor.composeFrame(frame)
        assertEquals(1, result!!.activeLayers.size)
        assertTrue(result.activeLayers.contains(BottomTickerOverlayLayer.LAYER_ID))

        // Case D: Both OFF (Clean PUBG screen output)
        compositor.setStandingOverlayEnabled(false)
        compositor.setTickerOverlayEnabled(false)
        result = compositor.composeFrame(frame)
        assertTrue("All overlays disabled should produce 0 active overlay layers", result!!.activeLayers.isEmpty())
    }

    @Test
    fun testConfigurablePositionAndScale() {
        val standing = compositor.standingOverlay
        standing.xOffsetPercent = 0.50f
        standing.yOffsetPercent = 0.10f
        standing.scale = 1.2f
        standing.alpha = 0.85f

        assertEquals(0.50f, standing.xOffsetPercent, 0.001f)
        assertEquals(0.10f, standing.yOffsetPercent, 0.001f)
        assertEquals(1.2f, standing.scale, 0.001f)
        assertEquals(0.85f, standing.alpha, 0.001f)

        val ticker = compositor.tickerOverlay
        ticker.yOffsetPercent = 0.90f
        ticker.heightPx = 50f
        ticker.speedMultiplier = 1.5f
        ticker.customText = "CUSTOM CHAMPIONSHIP BROADCAST TICKER"

        assertEquals(0.90f, ticker.yOffsetPercent, 0.001f)
        assertEquals(50f, ticker.heightPx, 0.001f)
        assertEquals(1.5f, ticker.speedMultiplier, 0.001f)
        assertEquals("CUSTOM CHAMPIONSHIP BROADCAST TICKER", ticker.customText)

        // Composition still succeeds with custom configurations
        compositor.setStandingOverlayEnabled(true)
        compositor.setTickerOverlayEnabled(true)
        val composed = compositor.composeFrame(createDummyPubgFrame())
        assertNotNull(composed)
        assertEquals(2, composed!!.activeLayers.size)
    }

    @Test
    fun testExtensibleLayerArchitectureAllowsFutureLayers() {
        val customLayerId = "future_sponsor_logo_layer"
        var customLayerDrawn = false

        val testCustomLayer = object : IBroadcastLayer {
            override val layerId: String = customLayerId
            override val layerName: String = "Tournament Sponsor Logo"
            override var isEnabled: Boolean = true
            override val zIndex: Int = 300

            override fun draw(canvas: Canvas, frameWidth: Int, frameHeight: Int, timestampMs: Long) {
                customLayerDrawn = true
            }
        }

        compositor.addLayer(testCustomLayer)
        assertEquals(testCustomLayer, compositor.getLayer(customLayerId))

        val composed = compositor.composeFrame(createDummyPubgFrame())
        assertNotNull(composed)
        assertTrue(customLayerDrawn)
        assertTrue(composed!!.activeLayers.contains(customLayerId))

        // Clean up
        compositor.removeLayer(customLayerId)
        val afterRemoval = compositor.composeFrame(createDummyPubgFrame())
        assertFalse(afterRemoval!!.activeLayers.contains(customLayerId))
    }

    @Test
    fun testICompositionProcessorContractFulfillment() {
        val frame = createDummyPubgFrame()
        val badge = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)

        val result = compositor.compositeOverlay(frame, badge)
        assertNotNull(result)
        assertEquals(frame.width, result.width)
        assertEquals(frame.height, result.height)
    }

    @Test
    fun testCompositorLayerOrderHierarchy() {
        val memeLayer = compositor.videoMemeOverlay
        val standingLayer = compositor.standingOverlay
        val tickerLayer = compositor.tickerOverlay
        val milestoneLayer = compositor.vipMilestoneOverlay

        // 1. Gameplay frame (drawn first on canvas)
        // 2. Meme / extra video (behind, zIndex: 18)
        // 3. Overall standing table (zIndex: 100)
        // 4. Bottom ticker (zIndex: 200)
        // 5. Milestone cards in front (zIndex: 300)
        assertTrue("Meme layer must render behind Overall Standing Table", memeLayer.zIndex < standingLayer.zIndex)
        assertTrue("Standing Table must render behind Bottom Ticker", standingLayer.zIndex < tickerLayer.zIndex)
        assertTrue("Bottom Ticker must render behind Milestone Cards", tickerLayer.zIndex < milestoneLayer.zIndex)
        assertEquals("Standing table zIndex must be 100", 100, standingLayer.zIndex)
        assertEquals("Bottom ticker zIndex must be 200", 200, tickerLayer.zIndex)
        assertEquals("Milestone card zIndex must be 300 (front)", 300, milestoneLayer.zIndex)
    }

    @Test
    fun testKnockedPlayerCountPreservedInOverlay() {
        // Knock player 1 on team 1
        val updatedTeams = LocalLiveRuntimeManager.teamsState.value.map { team ->
            if (team.teamNumber == 1) {
                team.copy(
                    players = team.players.mapIndexed { idx, p ->
                        if (idx == 0) p.copy(knocked = true, eliminated = false) else p
                    }
                )
            } else team
        }
        LocalLiveRuntimeManager.initializeFromRoster(updatedTeams)

        val team1 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        val nonEliminated = team1.players.count { !it.eliminated }
        assertEquals("Alive count must remain 4 when player is knocked but not eliminated", 4, nonEliminated)

        // Draw with standing overlay enabled
        compositor.setStandingOverlayEnabled(true)
        val frame = createDummyPubgFrame()
        val composed = compositor.composeFrame(frame)
        assertNotNull(composed)
    }
}
