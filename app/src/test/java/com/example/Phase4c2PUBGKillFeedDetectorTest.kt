package com.example

import com.example.core.model.DetectedEventType
import com.example.core.model.DetectionEvidence
import com.example.core.model.RoiRegion
import com.example.services.detection.DetectionDecision
import com.example.services.detection.IDetectionResult
import com.example.services.detection.PUBGDetectionService
import com.example.services.detection.pubg.KillFeedCause
import com.example.services.detection.pubg.KillFeedTemporalTracker
import com.example.services.detection.pubg.PUBGDecisionType
import com.example.services.detection.pubg.PUBGKillFeedDecisionEngine
import com.example.services.detection.pubg.PUBGKillFeedDetector
import com.example.services.detection.pubg.PUBGTextNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 4C.2 — Real PUBG Mobile Kill-Feed Detector Test Suite.
 *
 * Verifies all 20 rules from PUBG_Kill_Feed_Detector_Rules.pdf, flag removal,
 * temporal transition handling, duplicate protection, knock-to-finish state,
 * and 50% confidence gating.
 */
class Phase4c2PUBGKillFeedDetectorTest {

    private lateinit var detector: PUBGKillFeedDetector
    private val testRoi = RoiRegion(
        id = "admin_custom_kill_feed_roi",
        name = "ADMIN_CONFIGURED_KILL_FEED",
        x = 0.62f,
        y = 0.03f,
        width = 0.36f,
        height = 0.20f
    )

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
        timestamp: Long = 1000L
    ): DetectionEvidence {
        return DetectionEvidence(
            frameTimestamp = timestamp,
            roiId = testRoi.id,
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 690,
            croppedHeight = 216,
            referenceId = "frame_$timestamp",
            metadata = mapOf(
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
        )
    }

    // --- RULE 1: Normal Kill by weapon (A [ GUN ICON ] B) -> KILL, dead=true, kill_count=1, point=PlayerA ---
    @Test
    fun `test Rule 1 - Normal Gun Kill awards point to PlayerA`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "M416",
            timestamp = 1001L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KILL, success.event.eventType)
        assertEquals("Mortal", success.event.killerPlayerId)
        assertEquals("Scout", success.event.victimPlayerId)
        assertEquals(DetectionDecision.AUTO_PROCESS, success.decision)
        assertEquals("1", success.event.metadata["kill_count"])
        assertEquals("true", success.event.metadata["dead"])
        assertEquals("Mortal", success.event.metadata["point_awarded_to"])
        assertEquals("1", success.event.metadata["rule_number"])
        assertEquals(testRoi.id, success.event.roiId)
    }

    // --- RULE 2: Kill by grenade (A [ GRENADE ICON ] B) -> KILL, dead=true, kill_count=1, point=PlayerA ---
    @Test
    fun `test Rule 2 - Grenade Kill awards point to PlayerA`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Jonathan",
            rightText = "Goblin",
            cause = "GRENADE",
            timestamp = 1002L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KILL, success.event.eventType)
        assertEquals("Jonathan", success.event.killerPlayerId)
        assertEquals("Goblin", success.event.victimPlayerId)
        assertEquals("Jonathan", success.event.metadata["point_awarded_to"])
        assertEquals("2", success.event.metadata["rule_number"])
    }

    // --- RULE 3: Kill by vehicle (A [ VEHICLE ICON ] B) -> KILL, dead=true, kill_count=1, point=PlayerA ---
    @Test
    fun `test Rule 3 - Vehicle Kill awards point to PlayerA`() = runBlocking {
        val evidence = createEvidence(
            leftText = "ClutchGod",
            rightText = "Viper",
            cause = "VEHICLE",
            timestamp = 1003L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KILL, success.event.eventType)
        assertEquals("ClutchGod", success.event.killerPlayerId)
        assertEquals("Viper", success.event.victimPlayerId)
        assertEquals("ClutchGod", success.event.metadata["point_awarded_to"])
        assertEquals("3", success.event.metadata["rule_number"])
    }

    // --- RULE 4: Kill by fire / molotov (A [ MOLOTOV ICON ] B) -> KILL, dead=true, kill_count=1, point=PlayerA ---
    @Test
    fun `test Rule 4 - Molotov Kill awards point to PlayerA`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Zuxxy",
            rightText = "Luxxy",
            cause = "MOLOTOV",
            timestamp = 1004L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KILL, success.event.eventType)
        assertEquals("Zuxxy", success.event.killerPlayerId)
        assertEquals("Luxxy", success.event.victimPlayerId)
        assertEquals("Zuxxy", success.event.metadata["point_awarded_to"])
        assertEquals("4", success.event.metadata["rule_number"])
    }

    // --- RULE 5: Kill by Red Zone (A [ RED ZONE ICON ] B) -> ENV_DEATH, dead=true, kill_count=0, point=0 ---
    @Test
    fun `test Rule 5 - Red Zone Kill gives 0 points to any player`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Redzone",
            rightText = "PlayerX",
            cause = "RED_ZONE",
            timestamp = 1005L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.ELIMINATION, success.event.eventType)
        assertNull(success.event.killerPlayerId)
        assertEquals("PlayerX", success.event.victimPlayerId)
        assertEquals("NONE", success.event.metadata["point_awarded_to"])
        assertEquals("0", success.event.metadata["kill_count"])
        assertEquals("5", success.event.metadata["rule_number"])
    }

    // --- RULE 6: Kill by Playzone (A [ PLAYZONE ICON ] B) -> ENV_DEATH, dead=true, kill_count=0, point=0 ---
    @Test
    fun `test Rule 6 - Playzone Kill gives 0 points to any player`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Playzone",
            rightText = "Straggler99",
            cause = "PLAYZONE",
            timestamp = 1006L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.ELIMINATION, success.event.eventType)
        assertNull(success.event.killerPlayerId)
        assertEquals("Straggler99", success.event.victimPlayerId)
        assertEquals("NONE", success.event.metadata["point_awarded_to"])
        assertEquals("0", success.event.metadata["kill_count"])
        assertEquals("6", success.event.metadata["rule_number"])
    }

    // --- RULE 7: Kill by Blue Zone (A [ BLUE ZONE ICON ] B) -> ENV_DEATH, dead=true, kill_count=0, point=0 ---
    @Test
    fun `test Rule 7 - Bluezone Kill gives 0 points to any player`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Bluezone",
            rightText = "LateRunner",
            cause = "BLUE_ZONE",
            timestamp = 1007L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.ELIMINATION, success.event.eventType)
        assertNull(success.event.killerPlayerId)
        assertEquals("LateRunner", success.event.victimPlayerId)
        assertEquals("NONE", success.event.metadata["point_awarded_to"])
        assertEquals("7", success.event.metadata["rule_number"])
    }

    // --- RULE 8: Kill by Airstrike (A [ AIRSTRIKE ICON ] B) -> ENV_DEATH, dead=true, kill_count=0, point=0 ---
    @Test
    fun `test Rule 8 - Airstrike Kill gives 0 points`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Airstrike",
            rightText = "UnluckyPlayer",
            cause = "AIRSTRIKE",
            timestamp = 1008L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.ELIMINATION, success.event.eventType)
        assertEquals("NONE", success.event.metadata["point_awarded_to"])
        assertEquals("8", success.event.metadata["rule_number"])
    }

    // --- RULE 9: Kill by Drowning (A [ WATER ICON ] B) -> ENV_DEATH, dead=true, kill_count=0, point=0 ---
    @Test
    fun `test Rule 9 - Drowning Kill gives 0 points`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Water",
            rightText = "Swimmer7",
            cause = "WATER",
            timestamp = 1009L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.ELIMINATION, success.event.eventType)
        assertEquals("NONE", success.event.metadata["point_awarded_to"])
        assertEquals("9", success.event.metadata["rule_number"])
    }

    // --- RULE 10: Kill by Fall Damage (A [ FALL ICON ] B) -> ENV_DEATH, dead=true, kill_count=0, point=0 ---
    @Test
    fun `test Rule 10 - Fall Damage Kill gives 0 points`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Fall",
            rightText = "Climber9",
            cause = "FALL",
            timestamp = 1010L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.ELIMINATION, success.event.eventType)
        assertEquals("NONE", success.event.metadata["point_awarded_to"])
        assertEquals("10", success.event.metadata["rule_number"])
    }

    // --- RULE 11: Kill by Trap / Mine (A [ TRAP / MINE ICON ] B) -> ENV_DEATH, dead=true, kill_count=0, point=0 ---
    @Test
    fun `test Rule 11 - Trap Mine Kill gives 0 points`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Trap",
            rightText = "Walker12",
            cause = "TRAP",
            timestamp = 1011L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.ELIMINATION, success.event.eventType)
        assertEquals("NONE", success.event.metadata["point_awarded_to"])
        assertEquals("11", success.event.metadata["rule_number"])
    }

    // --- RULE 12: Self Kill / Suicide (A [ SUICIDE ICON ] B or PlayerA -> PlayerA) -> dead=true, kill_count=1, point=0 ---
    @Test
    fun `test Rule 12 - Self Kill Suicide awards 0 points to any player`() = runBlocking {
        val evidence = createEvidence(
            leftText = "PlayerOne",
            rightText = "PlayerOne",
            cause = "GRENADE",
            timestamp = 1012L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KILL, success.event.eventType)
        assertEquals("PlayerOne", success.event.victimPlayerId)
        assertEquals("NONE", success.event.metadata["point_awarded_to"])
        assertEquals("1", success.event.metadata["kill_count"])
        assertEquals("12", success.event.metadata["rule_number"])
    }

    // --- RULE 13: Player B Knocked by A with Gun (A [ GUN ICON ] KNOCK ICON B) -> KNOCK, dead=false, kill_count=0, point=0 ---
    @Test
    fun `test Rule 13 - Gun Knock is NOT a kill and awards 0 points`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "AKM",
            hasKnock = true,
            timestamp = 1013L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KNOCK, success.event.eventType)
        assertEquals("Scout", success.event.victimPlayerId)
        assertEquals("0", success.event.metadata["kill_count"])
        assertEquals("false", success.event.metadata["dead"])
        assertEquals("NONE", success.event.metadata["point_awarded_to"])
        assertEquals("13", success.event.metadata["rule_number"])
    }

    // --- RULE 14: Player B Knocked by A with Grenade (A [ GRENADE ICON ] KNOCK ICON B) -> KNOCK, dead=false, kill_count=0, point=0 ---
    @Test
    fun `test Rule 14 - Grenade Knock is NOT a kill`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Neyoo",
            rightText = "Snax",
            cause = "GRENADE",
            hasKnock = true,
            timestamp = 1014L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KNOCK, success.event.eventType)
        assertEquals("0", success.event.metadata["kill_count"])
        assertEquals("14", success.event.metadata["rule_number"])
    }

    // --- RULE 15: Generic Knock (A [ KNOCK ICON ] B) -> KNOCK, dead=false, kill_count=0, point=0 ---
    @Test
    fun `test Rule 15 - Generic Knock without weapon is NOT a kill`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Attacker",
            rightText = "Victim",
            cause = "KNOCK",
            hasKnock = true,
            timestamp = 1015L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KNOCK, success.event.eventType)
        assertEquals("15", success.event.metadata["rule_number"])
    }

    // --- RULE 16: Player B Finished from Knock with Helmet Icon -> Finish, not a new kill ---
    @Test
    fun `test Rule 16 - Finish from Knock with Helmet Icon`() = runBlocking {
        val evidence = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "M416",
            hasFinish = true,
            timestamp = 1016L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals("16", success.event.metadata["rule_number"])
        assertEquals("0", success.event.metadata["kill_count"])
    }

    // --- RULE 17 & 18: Team Kill / Friendly Fire -> dead=true, kill_count=1, point=0 ---
    @Test
    fun `test Rule 17 & 18 - Team Kill Friendly Fire gives 0 points to killer`() = runBlocking {
        val evidence = createEvidence(
            leftText = "[SOUL] Mortal",
            rightText = "[SOUL] Viper",
            cause = "M416",
            hasTeamKill = true,
            timestamp = 1017L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KILL, success.event.eventType)
        assertEquals("NONE", success.event.metadata["point_awarded_to"])
        assertEquals("1", success.event.metadata["kill_count"])
        assertEquals("17", success.event.metadata["rule_number"])
    }

    // --- RULE 19: Downed with Any Icon -> KNOCK, dead=false, kill_count=0, point=0 ---
    @Test
    fun `test Rule 19 - Downed with Any Icon`() = runBlocking {
        val evidence = createEvidence(
            leftText = "PlayerA",
            rightText = "PlayerB",
            cause = "VEHICLE",
            hasKnock = true,
            timestamp = 1019L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KNOCK, success.event.eventType)
        assertEquals("19", success.event.metadata["rule_number"])
    }

    // --- RULE 20: Revive (A [ REVIVE ICON ] B) -> REVIVE, dead=false, kill_count=0, point=0 ---
    @Test
    fun `test Rule 20 - Revive is NOT a kill`() = runBlocking {
        val evidence = createEvidence(
            leftText = "MedicA",
            rightText = "PatientB",
            cause = "REVIVE",
            hasRevive = true,
            timestamp = 1020L
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.REVIVE, success.event.eventType)
        assertEquals("0", success.event.metadata["kill_count"])
        assertEquals("false", success.event.metadata["dead"])
        assertEquals("20", success.event.metadata["rule_number"])
    }

    // --- MANDATORY RULE: Country flags and badges MUST BE COMPLETELY IGNORED ---
    @Test
    fun `test Country flags on killer and victim are completely ignored`() = runBlocking {
        val rawLine = "🇮🇩 JMBODBERGCHAKRA [M416] 🇭🇰 KoZaw"
        val evidence = DetectionEvidence(
            frameTimestamp = 2000L,
            roiId = testRoi.id,
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 600,
            croppedHeight = 180,
            referenceId = "frame_flags_01",
            metadata = mapOf(
                "feed_line" to rawLine,
                "clarity" to "0.95"
            )
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals("JMBODBERGCHAKRA", success.event.killerPlayerId)
        assertEquals("KoZaw", success.event.victimPlayerId)
        assertEquals("JMBODBERGCHAKRA", success.event.metadata["point_awarded_to"])
        assertFalse(success.event.killerPlayerId!!.contains("🇮🇩"))
        assertFalse(success.event.victimPlayerId!!.contains("🇭🇰"))
    }

    // --- TEMPORAL / ANIMATION RULE: Blurry or transitioning frame WAITS for readable evidence ---
    @Test
    fun `test Blurry or transitioning frame waits and does not create premature kill`() = runBlocking {
        val transitioningEvidence = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "M416",
            transitioning = true, // Frame is still transitioning/moving
            clarity = 0.80f,
            timestamp = 3000L
        )

        val result1 = detector.analyze(transitioningEvidence)
        assertTrue("Should wait when transitioning", result1 is IDetectionResult.NoDetection)

        val blurryEvidence = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "M416",
            transitioning = false,
            clarity = 0.20f, // Too blurry to read safely
            timestamp = 3050L
        )

        val result2 = detector.analyze(blurryEvidence)
        assertTrue("Should wait when clarity is too low", result2 is IDetectionResult.NoDetection)

        // When stable clear frame arrives, finalizes cleanly
        val clearEvidence = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "M416",
            transitioning = false,
            clarity = 0.95f,
            timestamp = 3100L
        )

        val result3 = detector.analyze(clearEvidence)
        assertTrue("Should succeed on clear stable frame", result3 is IDetectionResult.Success)
        val success = result3 as IDetectionResult.Success
        assertEquals("Mortal", success.event.killerPlayerId)
        assertEquals("Scout", success.event.victimPlayerId)
    }

    // --- DUPLICATE PROTECTION: Consecutive frames of same kill-feed bar generate exactly ONE event ---
    @Test
    fun `test Consecutive frames of the same feed bar do not duplicate events`() = runBlocking {
        val frame1 = createEvidence(
            leftText = "Alpha",
            rightText = "Bravo",
            cause = "SCAR-L",
            timestamp = 4000L
        )
        val frame2 = createEvidence(
            leftText = "Alpha",
            rightText = "Bravo",
            cause = "SCAR-L",
            timestamp = 4100L // 100ms later (same kill feed bar visible)
        )
        val frame3 = createEvidence(
            leftText = "Alpha",
            rightText = "Bravo",
            cause = "SCAR-L",
            timestamp = 4200L // 200ms later
        )

        val result1 = detector.analyze(frame1)
        val result2 = detector.analyze(frame2)
        val result3 = detector.analyze(frame3)

        assertTrue("First frame emits event", result1 is IDetectionResult.Success)
        assertTrue("Second frame suppressed by duplicate protection", result2 is IDetectionResult.NoDetection)
        assertTrue("Third frame suppressed by duplicate protection", result3 is IDetectionResult.NoDetection)
    }

    // --- KNOCK TO FINISH & ZONE BLEED ---
    @Test
    fun `test Knock followed by later weapon kill awards finish point to killer`() = runBlocking {
        // Frame 1: Mortal knocks Scout
        val knockFrame = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "AWM",
            hasKnock = true,
            timestamp = 5000L
        )
        val resultKnock = detector.analyze(knockFrame)
        assertTrue(resultKnock is IDetectionResult.Success)
        assertEquals(DetectedEventType.KNOCK, (resultKnock as IDetectionResult.Success).event.eventType)

        // Frame 2: 15 seconds later, Mortal finishes Scout with weapon
        val finishFrame = createEvidence(
            leftText = "Mortal",
            rightText = "Scout",
            cause = "M416",
            hasKnock = false,
            timestamp = 20000L
        )
        val resultFinish = detector.analyze(finishFrame)
        assertTrue(resultFinish is IDetectionResult.Success)
        val finishSuccess = resultFinish as IDetectionResult.Success
        assertEquals(DetectedEventType.KILL, finishSuccess.event.eventType)
        assertEquals("Mortal", finishSuccess.event.metadata["point_awarded_to"])
    }

    @Test
    fun `test Knocked player who dies to Zone awards 0 points to knocker`() = runBlocking {
        // Frame 1: PlayerA knocks PlayerB
        val knockFrame = createEvidence(
            leftText = "PlayerA",
            rightText = "PlayerB",
            cause = "M416",
            hasKnock = true,
            timestamp = 6000L
        )
        detector.analyze(knockFrame)

        // Frame 2: PlayerB bleeds out / dies to Playzone
        val zoneFrame = createEvidence(
            leftText = "Playzone",
            rightText = "PlayerB",
            cause = "PLAYZONE",
            hasKnock = false,
            timestamp = 25000L
        )
        val resultZone = detector.analyze(zoneFrame)
        assertTrue(resultZone is IDetectionResult.Success)
        val zoneSuccess = resultZone as IDetectionResult.Success
        assertEquals(DetectedEventType.ELIMINATION, zoneSuccess.event.eventType)
        assertEquals("NONE", zoneSuccess.event.metadata["point_awarded_to"]) // 0 points under zone rule
    }

    // --- 50% CONFIDENCE GATE ---
    @Test
    fun `test Confidence Gate - 50 percent or higher auto processes below 50 percent routes to Admin Review`() = runBlocking {
        // 50% clarity -> AUTO_PROCESS
        val highConfidenceEvidence = createEvidence(
            leftText = "Killer50",
            rightText = "Victim50",
            cause = "M416",
            clarity = 0.50f,
            timestamp = 7000L
        )
        val highResult = detector.analyze(highConfidenceEvidence)
        assertTrue(highResult is IDetectionResult.Success)
        assertEquals(DetectionDecision.AUTO_PROCESS, (highResult as IDetectionResult.Success).decision)

        // 45% clarity -> ADMIN_REVIEW
        val lowConfidenceEvidence = createEvidence(
            leftText = "Killer45",
            rightText = "Victim45",
            cause = "M416",
            clarity = 0.45f,
            timestamp = 8000L
        )
        val lowResult = detector.analyze(lowConfidenceEvidence)
        assertTrue(lowResult is IDetectionResult.Success)
        assertEquals(DetectionDecision.ADMIN_REVIEW, (lowResult as IDetectionResult.Success).decision)
    }

    // --- INTEGRATION WITH PUBGDetectionService ---
    @Test
    fun `test PUBGDetectionService integrates real PUBG detector seamlessly`() = runBlocking {
        val service = PUBGDetectionService(autoRegisterDefaultDetector = true)
        service.initialize()

        val evidence = createEvidence(
            leftText = "SOUL | Mortal",
            rightText = "TX | Scout",
            cause = "M416",
            timestamp = 9000L
        )

        val result = service.analyzeEvidenceCrop(evidence)
        assertTrue(result is IDetectionResult.Success)
        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KILL, success.event.eventType)
        assertEquals("Mortal", success.event.killerPlayerId)
        assertEquals("Scout", success.event.victimPlayerId)
    }
}
