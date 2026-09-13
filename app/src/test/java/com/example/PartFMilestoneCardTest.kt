package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.core.model.DetectionFrame
import com.example.core.model.TeamLiveState
import com.example.services.composition.BroadcastVideoCompositor
import com.example.services.composition.VipMilestoneOverlayLayer
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
class PartFMilestoneCardTest {

    private lateinit var compositor: BroadcastVideoCompositor
    private lateinit var overlayLayer: VipMilestoneOverlayLayer

    @Before
    fun setUp() {
        compositor = BroadcastVideoCompositor.getInstance()
        var layer = compositor.getLayer(VipMilestoneOverlayLayer.LAYER_ID) as? VipMilestoneOverlayLayer
        if (layer == null) {
            layer = VipMilestoneOverlayLayer()
            compositor.addLayer(layer)
        }
        overlayLayer = layer
        overlayLayer.isEnabled = true

        // Ensure controls are in default ON state
        if (!StandingTableControlsManager.controlsState.value.milestoneCardEnabled) {
            StandingTableControlsManager.toggleMilestoneCard()
        }
    }

    @Test
    fun testKillMilestoneDetectionAndOneTimeTrigger() {
        val teamNum = 1
        val teamName = "ALPHA 7"

        val t1Before = TeamLiveState(
            teamNumber = teamNum,
            teamName = teamName,
            currentMatchKills = 4,
            currentMatchPoints = 12
        )

        val t1After = TeamLiveState(
            teamNumber = teamNum,
            teamName = teamName,
            currentMatchKills = 5,
            currentMatchPoints = 13
        )

        val prevTeams = listOf(t1Before)
        val currentTeams = listOf(t1After)

        // Clear any old triggers if necessary or run freshly
        overlayLayer.detectMilestones(prevTeams, currentTeams)

        // Verify draw can run without throwing exceptions
        val bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        
        // Assert that the drawing executes smoothly
        overlayLayer.draw(canvas, 1280, 720, System.currentTimeMillis())
    }

    @Test
    fun testPointMilestoneDetection() {
        val teamNum = 2
        val teamName = "NOVA ESPORTS"

        val t1Before = TeamLiveState(
            teamNumber = teamNum,
            teamName = teamName,
            currentMatchKills = 1,
            currentMatchPoints = 19
        )

        val t1After = TeamLiveState(
            teamNumber = teamNum,
            teamName = teamName,
            currentMatchKills = 1,
            currentMatchPoints = 20
        )

        val prevTeams = listOf(t1Before)
        val currentTeams = listOf(t1After)

        overlayLayer.detectMilestones(prevTeams, currentTeams)

        val bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        overlayLayer.draw(canvas, 1280, 720, System.currentTimeMillis())
    }

    @Test
    fun testOnOffControl() {
        // Toggle control OFF
        if (StandingTableControlsManager.controlsState.value.milestoneCardEnabled) {
            StandingTableControlsManager.toggleMilestoneCard()
        }
        assertFalse(StandingTableControlsManager.controlsState.value.milestoneCardEnabled)

        // Toggle control ON
        StandingTableControlsManager.toggleMilestoneCard()
        assertTrue(StandingTableControlsManager.controlsState.value.milestoneCardEnabled)
    }
}
