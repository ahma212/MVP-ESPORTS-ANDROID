package com.example

import com.example.core.model.DetectedEventType
import com.example.core.model.DetectionEvidence
import com.example.services.detection.IDetectionResult
import com.example.services.detection.pubg.KillFeedCause
import com.example.services.detection.pubg.KillFeedVisualIcon
import com.example.services.detection.pubg.PUBGKillFeedDecisionEngine
import com.example.services.detection.pubg.PUBGKillFeedDetector
import com.example.services.detection.pubg.PUBGKillFeedLineSegmenter
import com.example.services.detection.pubg.PUBGKillFeedVisualParser
import com.example.services.detection.pubg.PUBGVisualAIAssistant
import com.example.services.detection.pubg.PUBGVisualIconDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verification test suite for Phase K4-A: PUBG Kill Feed Visual Evidence Detection.
 */
class PhaseK4AKillFeedVisualEvidenceTest {

    private lateinit var detector: PUBGKillFeedDetector

    @Before
    fun setup() {
        detector = PUBGKillFeedDetector()
        detector.reset()
    }

    private fun createEvidence(
        leftText: String,
        rightText: String,
        cause: String,
        hasKnock: Boolean = false,
        hasFinish: Boolean = false,
        hasTeamKill: Boolean = false,
        hasRevive: Boolean = false,
        clarity: Float = 0.95f,
        transitioning: Boolean = false,
        timestamp: Long = 1000L,
        extraMeta: Map<String, String> = emptyMap()
    ): DetectionEvidence {
        val meta = mutableMapOf<String, String>(
            "left_text" to leftText,
            "right_text" to rightText,
            "cause" to cause,
            "has_knock" to hasKnock.toString(),
            "has_finish" to hasFinish.toString(),
            "has_team_kill" to hasTeamKill.toString(),
            "has_revive" to hasRevive.toString(),
            "clarity" to clarity.toString(),
            "transitioning" to transitioning.toString()
        )
        meta.putAll(extraMeta)

        return DetectionEvidence(
            frameTimestamp = timestamp,
            roiId = "admin_kill_feed_roi",
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 640,
            croppedHeight = 180,
            referenceId = "frame_$timestamp",
            metadata = meta
        )
    }

    // ========================================================
    // REQUIREMENT 2: Real visual icon recognition
    // ========================================================

    @Test
    fun `test Visual Icon Recognition - All PUBG Feed Icons`() {
        // Weapon / Gun
        val gunRes = PUBGVisualIconDetector.detectIcon("PlayerA [M416] PlayerB")
        assertEquals(KillFeedVisualIcon.GUN, gunRes.icon)
        assertFalse(gunRes.isKnock)

        // Grenade
        val nadeRes = PUBGVisualIconDetector.detectIcon("PlayerA [GRENADE] PlayerB")
        assertEquals(KillFeedVisualIcon.GRENADE, nadeRes.icon)

        // Vehicle
        val vehRes = PUBGVisualIconDetector.detectIcon("PlayerA [VEHICLE] PlayerB")
        assertEquals(KillFeedVisualIcon.VEHICLE, vehRes.icon)

        // Molotov / Fire
        val moloRes = PUBGVisualIconDetector.detectIcon("PlayerA [MOLOTOV] PlayerB")
        assertEquals(KillFeedVisualIcon.MOLOTOV, moloRes.icon)

        // Red Zone
        val redRes = PUBGVisualIconDetector.detectIcon("PlayerB killed by RED ZONE")
        assertEquals(KillFeedVisualIcon.RED_ZONE, redRes.icon)
        assertTrue(redRes.isEnvironment)

        // Playzone
        val zoneRes = PUBGVisualIconDetector.detectIcon("PlayerB died outside PLAYZONE")
        assertEquals(KillFeedVisualIcon.PLAYZONE, zoneRes.icon)
        assertTrue(zoneRes.isEnvironment)

        // Blue Zone
        val blueRes = PUBGVisualIconDetector.detectIcon("PlayerB fell to BLUE ZONE")
        assertEquals(KillFeedVisualIcon.BLUE_ZONE, blueRes.icon)
        assertTrue(blueRes.isEnvironment)

        // Airstrike
        val airRes = PUBGVisualIconDetector.detectIcon("PlayerB eliminated by AIRSTRIKE")
        assertEquals(KillFeedVisualIcon.AIRSTRIKE, airRes.icon)

        // Water / Drowning
        val waterRes = PUBGVisualIconDetector.detectIcon("PlayerB drowned in water")
        assertEquals(KillFeedVisualIcon.WATER, waterRes.icon)

        // Fall
        val fallRes = PUBGVisualIconDetector.detectIcon("PlayerB fell from high ground")
        assertEquals(KillFeedVisualIcon.FALL, fallRes.icon)

        // Trap / Mine
        val trapRes = PUBGVisualIconDetector.detectIcon("PlayerB hit a spike trap")
        assertEquals(KillFeedVisualIcon.TRAP_MINE, trapRes.icon)

        // Suicide / Self-Kill
        val suiRes = PUBGVisualIconDetector.detectIcon("PlayerB committed suicide")
        assertEquals(KillFeedVisualIcon.SUICIDE, suiRes.icon)
        assertTrue(suiRes.isSelfKill)

        // Knock / Down
        val knockRes = PUBGVisualIconDetector.detectIcon("PlayerA knocked down PlayerB")
        assertEquals(KillFeedVisualIcon.KNOCK, knockRes.icon)
        assertTrue(knockRes.isKnock)

        // Finish (Helmet)
        val finishRes = PUBGVisualIconDetector.detectIcon("PlayerA finished off [HELMET] PlayerB")
        assertEquals(KillFeedVisualIcon.FINISH_HELMET, finishRes.icon)
        assertTrue(finishRes.isFinish)

        // Team Kill / Friendly Fire
        val tkRes = PUBGVisualIconDetector.detectIcon("PlayerA [TEAM_KILL] PlayerB")
        assertEquals(KillFeedVisualIcon.TEAM_KILL, tkRes.icon)
        assertTrue(tkRes.isTeamKill)

        // Revive
        val revRes = PUBGVisualIconDetector.detectIcon("PlayerA [REVIVE] PlayerB")
        assertEquals(KillFeedVisualIcon.REVIVE, revRes.icon)
        assertTrue(revRes.isRevive)
    }

    // ========================================================
    // REQUIREMENT 3: KNOCK indicator critical evidence
    // If a knock/down icon appears, victim must NOT be treated as finally killed!
    // ========================================================

    @Test
    fun `test Knock Indicator - Victim is NOT treated as finally killed`() = runBlocking {
        val knockEvidence = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "M416",
            hasKnock = true,
            timestamp = 2000L
        )

        val result = detector.analyze(knockEvidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        val evt = success.event

        // Event type must be KNOCK, NOT KILL
        assertEquals(DetectedEventType.KNOCK, evt.eventType)

        // Victim must NOT be dead
        assertEquals("false", evt.metadata["dead"])

        // Kill count must be 0
        assertEquals("0", evt.metadata["kill_count"])

        // No kill point awarded to knocker
        assertEquals("NONE", evt.metadata["point_awarded_to"])

        // Has knock flag true
        assertEquals("true", evt.metadata["has_knock"])
    }

    // ========================================================
    // REQUIREMENT 1 & 5: Multi-line segmentation & independent tracking
    // ========================================================

    @Test
    fun `test Multi-Line Feed Detection - Slices and analyzes independent lines`() = runBlocking {
        val multiLineEvidence = createEvidence(
            leftText = "",
            rightText = "",
            cause = "",
            timestamp = 3000L,
            extraMeta = mapOf(
                "feed_line" to "Mortal [M416] Scout\nJonathan [GRENADE] [KNOCK] Goblin\nZone [PLAYZONE] Runner"
            )
        )

        val lineResults = detector.analyzeLines(multiLineEvidence)
        assertEquals(3, lineResults.size)

        // Line 0: Mortal killed Scout with M416
        assertEquals("Mortal", lineResults[0].killerName)
        assertEquals("Scout", lineResults[0].victimName)
        assertEquals(KillFeedVisualIcon.GUN, lineResults[0].detectedIcon)
        assertFalse(lineResults[0].hasKnockIndicator)

        // Line 1: Jonathan knocked Goblin with grenade
        assertEquals("Jonathan", lineResults[1].killerName)
        assertEquals("Goblin", lineResults[1].victimName)
        assertTrue(lineResults[1].hasKnockIndicator)

        // Line 2: Playzone killed Runner
        assertNull(lineResults[2].killerName)
        assertEquals("Runner", lineResults[2].victimName)
        assertEquals(KillFeedVisualIcon.PLAYZONE, lineResults[2].detectedIcon)
    }

    // ========================================================
    // REQUIREMENT 4 & 6: Multi-frame temporal tracking & stable evidence
    // ========================================================

    @Test
    fun `test Multi-Frame Temporal Tracking across consecutive frames`() = runBlocking {
        val frame1 = createEvidence(
            leftText = "Viper",
            rightText = "ClutchGod",
            cause = "AWM",
            clarity = 0.50f, // borderline clarity
            timestamp = 4001L
        )

        // Frame 1: Observed, starts multi-frame track
        val linesFrame1 = detector.analyzeLines(frame1)
        assertEquals(1, linesFrame1.size)
        assertEquals(1, linesFrame1[0].temporalEvidence.frameCount)

        // Frame 2: Same event observed in next frame (stable)
        val frame2 = createEvidence(
            leftText = "Viper",
            rightText = "ClutchGod",
            cause = "AWM",
            clarity = 0.90f,
            timestamp = 4033L
        )
        val linesFrame2 = detector.analyzeLines(frame2)
        assertEquals(1, linesFrame2.size)
        assertEquals(2, linesFrame2[0].temporalEvidence.frameCount)
        assertTrue(linesFrame2[0].temporalEvidence.isMultiFrameValidated)
        assertEquals(1.0f, linesFrame2[0].temporalEvidence.consistencyScore, 0.01f)
    }

    // ========================================================
    // REQUIREMENT 7: Duplicate-event protection
    // ========================================================

    @Test
    fun `test Duplicate Protection - Consecutive frames emit exactly ONE event`() = runBlocking {
        val frame1 = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "M416",
            timestamp = 5000L
        )

        val result1 = detector.analyze(frame1)
        assertTrue("Frame 1 must emit success event", result1 is IDetectionResult.Success)

        // Frame 2 within 100ms: same kill-feed bar
        val frame2 = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "M416",
            timestamp = 5100L
        )

        val result2 = detector.analyze(frame2)
        assertTrue("Frame 2 must be suppressed as duplicate", result2 is IDetectionResult.NoDetection)

        // Frame 3 within 500ms: still suppressed
        val frame3 = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "M416",
            timestamp = 5500L
        )
        val result3 = detector.analyze(frame3)
        assertTrue("Frame 3 must be suppressed as duplicate", result3 is IDetectionResult.NoDetection)
    }

    // ========================================================
    // REQUIREMENT 8: Unclear evidence remains unresolved (no guessing)
    // ========================================================

    @Test
    fun `test Unclear Evidence remains unresolved without guessing`() = runBlocking {
        // Blurry frame (clarity < 0.35)
        val blurryEvidence = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "M416",
            clarity = 0.20f,
            timestamp = 6000L
        )

        val result = detector.analyze(blurryEvidence)
        assertTrue("Blurry frame must NOT emit an event", result is IDetectionResult.NoDetection)

        // Transitioning / animating bar
        val transitioningEvidence = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "M416",
            transitioning = true,
            timestamp = 6100L
        )
        val resultTrans = detector.analyze(transitioningEvidence)
        assertTrue("Transitioning bar must NOT emit an event", resultTrans is IDetectionResult.NoDetection)

        // Missing victim name (corrupted)
        val missingVictimEvidence = createEvidence(
            leftText = "Mortal",
            rightText = "",
            cause = "M416",
            timestamp = 6200L
        )
        val resultMissing = detector.analyze(missingVictimEvidence)
        assertTrue("Missing victim name must NOT emit a kill", resultMissing is IDetectionResult.NoDetection)
    }

    // ========================================================
    // REQUIREMENT 9: AI-assisted visual analysis layer
    // AI assists with ambiguous evidence without bypassing rules engine
    // ========================================================

    @Test
    fun `test AI-assisted visual analysis improves ambiguous feed without bypassing rules engine`() = runBlocking {
        val aiAssistant = PUBGVisualAIAssistant.getInstance()
        val ambiguousSlice = PUBGKillFeedLineSegmenter.KillFeedLineSlice(
            lineIndex = 0,
            rawText = "PlayerA knocked down PlayerB with grenade",
            subRect = null,
            metadata = mapOf("ai_assist_requested" to true)
        )

        val aiResult = aiAssistant.analyzeVisualEvidence(ambiguousSlice, null)
        assertNotNull(aiResult)
        assertEquals(KillFeedVisualIcon.KNOCK, aiResult!!.suggestedIcon)
        assertEquals(true, aiResult.hasKnockIndicator)

        // Confirm rules engine still enforces knock rule deterministically
        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = "PlayerA",
            rawRight = "PlayerB",
            cause = KillFeedCause.KNOCK,
            hasKnock = aiResult.hasKnockIndicator == true,
            confidence = aiResult.visualConfidence
        )
        assertEquals(false, decision.dead)
        assertEquals(0, decision.killCount)
        assertNull(decision.pointAwardedTo)
    }
}
