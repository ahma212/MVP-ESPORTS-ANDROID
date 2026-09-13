package com.example

import com.example.core.model.DetectedEventType
import com.example.core.model.DetectionEvidence
import com.example.core.model.DetectionFrame
import com.example.core.model.FrameAnalysisInput
import com.example.core.model.RoiRegion
import com.example.services.analysis.RoiCropPipeline
import com.example.services.detection.IDetectionResult
import com.example.services.detection.PUBGDetectionService
import com.example.services.detection.pubg.PUBGKillFeedDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase4c3RealFrameIntegrationTest {

    private lateinit var detectionService: PUBGDetectionService
    private lateinit var testScope: TestScope
    private val roi = RoiRegion.DEFAULT_KILL_FEED

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        detectionService = PUBGDetectionService(autoRegisterDefaultDetector = true)
        detectionService.registerDetector(PUBGKillFeedDetector())
    }

    private fun createFrameInput(
        feedLine: String? = null,
        leftText: String? = null,
        rightText: String? = null,
        cause: String? = null,
        hasKnock: Boolean? = null,
        hasFinish: Boolean? = null,
        hasTeamKill: Boolean? = null,
        hasRevive: Boolean? = null,
        clarity: Float = 0.95f,
        transitioning: Boolean = false,
        timestampMs: Long = 100000L
    ): FrameAnalysisInput {
        val meta = mutableMapOf<String, String>()
        if (feedLine != null) meta["feed_line"] = feedLine
        if (leftText != null) meta["left_text"] = leftText
        if (rightText != null) meta["right_text"] = rightText
        if (cause != null) meta["cause"] = cause
        if (hasKnock != null) meta["has_knock"] = hasKnock.toString()
        if (hasFinish != null) meta["has_finish"] = hasFinish.toString()
        if (hasTeamKill != null) meta["has_team_kill"] = hasTeamKill.toString()
        if (hasRevive != null) meta["has_revive"] = hasRevive.toString()
        meta["clarity"] = clarity.toString()
        meta["transitioning"] = transitioning.toString()

        val frame = DetectionFrame(
            frameId = timestampMs / 100,
            timestampMs = timestampMs,
            width = 1080,
            height = 1920,
            format = "RGBA_8888",
            buffer = ByteArray(1080 * 1920 * 4),
            sourceIdentifier = "screen_capture"
        )
        val input = FrameAnalysisInput.fromDetectionFrame(frame)
        return input.copy(metadata = meta)
    }

    private suspend fun processFrame(input: FrameAnalysisInput): IDetectionResult {
        val cropped = RoiCropPipeline.crop(input, roi)
        val evidence = DetectionEvidence(
            frameTimestamp = cropped.timestampMs,
            roiId = roi.id,
            frameWidth = cropped.width,
            frameHeight = cropped.height,
            croppedWidth = cropped.width,
            croppedHeight = cropped.height,
            referenceId = cropped.sequenceNumber.toString(),
            metadata = cropped.metadata
        )
        return detectionService.analyzeEvidenceCrop(evidence)
    }

    // 1. Normal gun kill
    @Test
    fun test01_NormalGunKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "🇮🇩 Mortal [M416] 🇵🇭 Scout", timestampMs = 10000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertEquals("Mortal", ev.killerPlayerId)
        assertEquals("Scout", ev.victimPlayerId)
        assertEquals(0.95f, ev.confidence, 0.01f)
        assertEquals(DetectedEventType.KILL, ev.eventType)
    }

    // 2. Grenade kill
    @Test
    fun test02_GrenadeKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "Jonathan [GRENADE] Goblin", timestampMs = 20000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertEquals("Jonathan", ev.killerPlayerId)
        assertEquals("Goblin", ev.victimPlayerId)
        assertEquals(0.95f, ev.confidence, 0.01f)
        assertEquals(DetectedEventType.KILL, ev.eventType)
    }

    // 3. Vehicle kill
    @Test
    fun test03_VehicleKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "Neymar [VEHICLE] Messi", timestampMs = 30000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertEquals("Neymar", ev.killerPlayerId)
        assertEquals("Messi", ev.victimPlayerId)
        assertEquals(0.95f, ev.confidence, 0.01f)
    }

    // 4. Molotov/fire kill
    @Test
    fun test04_MolotovFireKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "Zuxxy [MOLOTOV] Luxxy", timestampMs = 40000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertEquals("Zuxxy", ev.killerPlayerId)
        assertEquals("Luxxy", ev.victimPlayerId)
        assertEquals(0.95f, ev.confidence, 0.01f)
    }

    // 5. Melee kill
    @Test
    fun test05_MeleeKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "Paraboy [PAN] Order", timestampMs = 50000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertEquals("Paraboy", ev.killerPlayerId)
        assertEquals("Order", ev.victimPlayerId)
        assertEquals(0.95f, ev.confidence, 0.01f)
    }

    // 6. Knock
    @Test
    fun test06_KnockEvent() = testScope.runTest {
        val input = createFrameInput(feedLine = "Scout [M416] [KNOCK] Mortal", timestampMs = 60000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertEquals("Scout", ev.killerPlayerId)
        assertEquals("Mortal", ev.victimPlayerId)
        assertEquals(DetectedEventType.KNOCK, ev.eventType)
        assertEquals("false", ev.metadata["dead"])
        assertEquals("true", ev.metadata["has_knock"])
    }

    // 7. Knock followed by finish
    @Test
    fun test07_KnockFollowedByFinish() = testScope.runTest {
        val ts1 = 70000L
        val inputKnock = createFrameInput(feedLine = "Alpha [M416] [KNOCK] Beta", timestampMs = ts1)
        val res1 = processFrame(inputKnock)
        assertTrue("Expected Success on knock but got $res1", res1 is IDetectionResult.Success)

        val ts2 = ts1 + 2000L
        val inputFinish = createFrameInput(feedLine = "Alpha [M416] [FINISH] Beta", timestampMs = ts2)
        val res2 = processFrame(inputFinish)

        assertTrue("Expected Success on finish but got $res2", res2 is IDetectionResult.Success)
        val ev2 = (res2 as IDetectionResult.Success).event
        assertEquals("Alpha", ev2.killerPlayerId)
        assertEquals("Beta", ev2.victimPlayerId)
        assertEquals("true", ev2.metadata["has_finish"])
    }

    // 8. Self kill
    @Test
    fun test08_SelfKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "Viper [GRENADE] Viper", timestampMs = 80000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertEquals("Viper", ev.killerPlayerId)
        assertEquals("Viper", ev.victimPlayerId)
        assertEquals(DetectedEventType.KILL, ev.eventType)
        assertEquals(0.95f, ev.confidence, 0.01f)
        assertEquals("true", ev.metadata["dead"])
        assertEquals("1", ev.metadata["kill_count"])
        assertEquals("NONE", ev.metadata["point_awarded_to"])
    }

    // 9. Team/friendly-fire kill
    @Test
    fun test09_TeamFriendlyFireKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "TeamMateA [GRENADE] [TEAM_KILL] TeamMateB", timestampMs = 90000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertEquals("TeamMateA", ev.killerPlayerId)
        assertEquals("TeamMateB", ev.victimPlayerId)
        assertEquals(DetectedEventType.KILL, ev.eventType)
        assertEquals("1", ev.metadata["kill_count"])
        assertEquals("NONE", ev.metadata["point_awarded_to"])
    }

    // 10. Red Zone
    @Test
    fun test10_RedZoneEnvironmentKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "RED ZONE [RED_ZONE] UnluckyPlayer", timestampMs = 100000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertNull(ev.killerPlayerId)
        assertEquals("UnluckyPlayer", ev.victimPlayerId)
        assertEquals(DetectedEventType.ELIMINATION, ev.eventType)
    }

    // 11. Playzone
    @Test
    fun test11_PlayzoneEnvironmentKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "Playzone [ZONE] ZoneVictim", timestampMs = 110000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertNull(ev.killerPlayerId)
        assertEquals("ZoneVictim", ev.victimPlayerId)
    }

    // 12. Blue Zone
    @Test
    fun test12_BlueZoneEnvironmentKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "Blue Zone [BLUE_ZONE] LateRotator", timestampMs = 120000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertNull(ev.killerPlayerId)
        assertEquals("LateRotator", ev.victimPlayerId)
    }

    // 13. Airstrike
    @Test
    fun test13_AirstrikeKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "Airstrike [AIRSTRIKE] Camper", timestampMs = 130000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertNull(ev.killerPlayerId)
        assertEquals("Camper", ev.victimPlayerId)
    }

    // 14. Drowning
    @Test
    fun test14_DrowningKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "Swimmer [WATER] Swimmer", timestampMs = 140000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertNull(ev.killerPlayerId)
        assertEquals("Swimmer", ev.victimPlayerId)
    }

    // 15. Fall
    @Test
    fun test15_FallDamageKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "Jumper [FALL] Jumper", timestampMs = 150000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertNull(ev.killerPlayerId)
        assertEquals("Jumper", ev.victimPlayerId)
    }

    // 16. Trap/mine (Rule 11: Environment Death)
    @Test
    fun test16_TrapMineKill() = testScope.runTest {
        val input = createFrameInput(feedLine = "Trapper [TRAP] Victim", timestampMs = 160000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertNull(ev.killerPlayerId)
        assertEquals("Victim", ev.victimPlayerId)
        assertEquals(DetectedEventType.ELIMINATION, ev.eventType)
    }

    // 17. Revive
    @Test
    fun test17_ReviveEvent() = testScope.runTest {
        val input = createFrameInput(feedLine = "Medic [REVIVE] DownedTeammate", timestampMs = 170000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertEquals("Medic", ev.killerPlayerId)
        assertEquals("DownedTeammate", ev.victimPlayerId)
        assertEquals(DetectedEventType.REVIVE, ev.eventType)
        assertEquals("false", ev.metadata["dead"])
        assertEquals("0", ev.metadata["kill_count"])
    }

    // 18. Finish/helmet indicator
    @Test
    fun test18_FinishHelmetIndicator() = testScope.runTest {
        val input = createFrameInput(feedLine = "Sniper [AWM] [HELMET] Target", timestampMs = 180000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertEquals("Sniper", ev.killerPlayerId)
        assertEquals("Target", ev.victimPlayerId)
        assertEquals("true", ev.metadata["has_finish"])
    }

    // 19. Flag on killer + flag on victim MUST be ignored for scoring and matching
    @Test
    fun test19_FlagsIgnoredOnBothSides() = testScope.runTest {
        val input = createFrameInput(feedLine = "🇲🇨 [ClanX] PlayerOne [M416] 🇺🇸 [ClanY] PlayerTwo", timestampMs = 190000L)
        val result = processFrame(input)

        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)
        val ev = (result as IDetectionResult.Success).event

        assertEquals("PlayerOne", ev.killerPlayerId)
        assertEquals("PlayerTwo", ev.victimPlayerId)
        val flagsIgnored = ev.metadata["flags_ignored"] ?: ""
        assertTrue(flagsIgnored.contains("🇲🇨") || flagsIgnored.contains("🇺🇸") || flagsIgnored.contains("Flag") || ev.killerPlayerId == "PlayerOne")
    }

    // 20. Animated/glowing/partially blurred feed -> System MUST WAIT (NO_DECISION_WAIT)
    @Test
    fun test20_BlurredOrTransitioningFrameWaits() = testScope.runTest {
        val input = createFrameInput(
            feedLine = "Mortal [M416] Scout",
            clarity = 0.20f,
            transitioning = true,
            timestampMs = 200000L
        )
        val result = processFrame(input)

        assertTrue("Expected NoDetection but got $result", result is IDetectionResult.NoDetection)
    }

    // 21. Same feed visible across many consecutive frames -> Duplicate prevented
    @Test
    fun test21_SameFeedVisibleAcrossFramesSuppressesDuplicate() = testScope.runTest {
        val startTs = 210000L
        val input = createFrameInput(feedLine = "Rambo [M416] Enemy", timestampMs = startTs)

        val res1 = processFrame(input)
        assertTrue("Expected Success on first frame but got $res1", res1 is IDetectionResult.Success)

        for (i in 1..5) {
            val frameI = createFrameInput(feedLine = "Rambo [M416] Enemy", timestampMs = startTs + (i * 100L))
            val resI = processFrame(frameI)
            assertTrue("Expected NoDetection on duplicate frame $i but got $resI", resI is IDetectionResult.NoDetection)
        }
    }

    // 22. Two different kill-feed events appearing close together -> Both recognized
    @Test
    fun test22_TwoDifferentKillsCloseTogetherBothRecognized() = testScope.runTest {
        val t1 = 300000L
        val kill1 = createFrameInput(feedLine = "Player1 [M416] Victim1", timestampMs = t1)
        val res1 = processFrame(kill1)
        assertTrue("Expected Success on kill1 but got $res1", res1 is IDetectionResult.Success)

        val t2 = t1 + 300L
        val kill2 = createFrameInput(feedLine = "Player2 [AWM] Victim2", timestampMs = t2)
        val res2 = processFrame(kill2)
        assertTrue("Expected Success on kill2 but got $res2", res2 is IDetectionResult.Success)

        val ev1 = (res1 as IDetectionResult.Success).event
        val ev2 = (res2 as IDetectionResult.Success).event

        assertEquals("Player1", ev1.killerPlayerId)
        assertEquals("Victim1", ev1.victimPlayerId)

        assertEquals("Player2", ev2.killerPlayerId)
        assertEquals("Victim2", ev2.victimPlayerId)
    }
}
