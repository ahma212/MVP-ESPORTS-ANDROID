package com.example

import com.example.core.model.DetectedEventType
import com.example.core.model.DetectionEvidence
import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.EsportsEventType
import com.example.core.model.PlayerLiveState
import com.example.core.model.RoiRegion
import com.example.core.model.TeamLiveState
import com.example.services.detection.IDetectionResult
import com.example.services.detection.pubg.KillFeedCause
import com.example.services.detection.pubg.PUBGDecisionType
import com.example.services.detection.pubg.PUBGKillFeedDecisionEngine
import com.example.services.detection.pubg.PUBGKillFeedDetector
import com.example.services.detection.pubg.PUBGTextNormalizer
import com.example.services.detection.pubg.TournamentRoster
import com.example.services.streaming.LocalLiveRuntimeManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit & Integration Test Suite for PART 4 — Team & Player Identity Matching.
 */
class Part4TeamPlayerIdentityMatchingTest {

    private val testRoi = RoiRegion.DEFAULT_KILL_FEED

    @Before
    fun setup() {
        TournamentRoster.clearRoster()
        LocalLiveRuntimeManager.resetProcessedEvents()

        val team1 = TeamLiveState(
            teamNumber = 1,
            teamName = "Team Alpha",
            currentAlivePlayers = 4,
            players = listOf(
                PlayerLiveState(playerName = "AlphaP1", teamNumber = 1),
                PlayerLiveState(playerName = "AlphaP2", teamNumber = 1),
                PlayerLiveState(playerName = "AlphaP3", teamNumber = 1),
                PlayerLiveState(playerName = "AlphaP4", teamNumber = 1)
            )
        )

        val team2 = TeamLiveState(
            teamNumber = 2,
            teamName = "Team Bravo",
            currentAlivePlayers = 4,
            players = listOf(
                PlayerLiveState(playerName = "BravoP1", teamNumber = 2),
                PlayerLiveState(playerName = "BravoP2", teamNumber = 2),
                PlayerLiveState(playerName = "BravoP3", teamNumber = 2),
                PlayerLiveState(playerName = "BravoP4", teamNumber = 2)
            )
        )

        LocalLiveRuntimeManager.initializeFromRoster(listOf(team1, team2))
    }

    @Test
    fun testPlayerNameToCorrectRosterPlayer() {
        val player = TournamentRoster.resolvePlayerByName("AlphaP2")
        assertNotNull(player)
        assertEquals("AlphaP2", player?.inGameName)
        assertEquals("team_1", player?.teamId)
    }

    @Test
    fun testPlayerNameToCorrectTeam() {
        val player = TournamentRoster.resolvePlayerByName("BravoP1")
        assertNotNull(player)
        assertEquals("team_2", player?.teamId)
    }

    @Test
    fun testTeamNumberAndPlayerNumberToExactPlayer() {
        val identity = PUBGTextNormalizer.TeamPlayerIdentity(1, 4, "T1 P4")
        val player = TournamentRoster.resolvePlayer(identity)
        assertNotNull(player)
        assertEquals("AlphaP4", player?.inGameName)
        assertEquals("t1_p4", player?.id)
    }

    @Test
    fun testTeam1Player4MustNotMatchTeam1Player3() {
        val p4 = TournamentRoster.resolvePlayer(1, 4)
        val p3 = TournamentRoster.resolvePlayer(1, 3)
        assertNotNull(p4)
        assertNotNull(p3)
        assertTrue(p4?.id != p3?.id)
        assertEquals("AlphaP4", p4?.inGameName)
        assertEquals("AlphaP3", p3?.inGameName)
    }

    @Test
    fun testTeam1Player4MustNotMatchTeam2Player4() {
        val t1p4 = TournamentRoster.resolvePlayer(1, 4)
        val t2p4 = TournamentRoster.resolvePlayer(2, 4)
        assertNotNull(t1p4)
        assertNotNull(t2p4)
        assertTrue(t1p4?.teamId != t2p4?.teamId)
        assertEquals("AlphaP4", t1p4?.inGameName)
    }

    @Test
    fun testKillerAndVictimResolvedIndependently() {
        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = "T1 P4",
            rawRight = "T2 P2",
            cause = KillFeedCause.GUN,
            hasKnock = false,
            confidence = 0.95f
        )
        assertEquals("AlphaP4", decision.killerName)
        assertEquals("BravoP2", decision.victimName)
        assertEquals("T1", decision.killerTeam)
        assertEquals("T2", decision.victimTeam)
    }

    @Test
    fun testCorrectKillerReceivesKillAndVictimEliminated() {
        val event = EsportsDetectedEvent(
            id = "evt_test_7_8",
            sessionId = "session_1",
            eventType = EsportsEventType.KILL,
            timestampMs = System.currentTimeMillis(),
            killerPlayerName = "T1 P4",
            killerTeamTag = "T1",
            victimPlayerName = "T2 P2",
            victimTeamTag = "T2",
            weaponUsed = "GUN",
            confidence = 0.95f,
            source = "TEST",
            frameReferenceId = 1000L,
            metadata = mapOf("decision_type" to "ENEMY_KILL", "kill_credit_allowed" to "true", "credited_kill_count" to "1")
        )

        LocalLiveRuntimeManager.processDetectedEvent(event)

        val teams = LocalLiveRuntimeManager.teamsState.value
        val t1 = teams.find { it.teamNumber == 1 }
        val t2 = teams.find { it.teamNumber == 2 }

        assertNotNull(t1)
        assertNotNull(t2)

        val killerPlayer = t1?.players?.get(3)
        assertEquals(1, killerPlayer?.currentMatchKills)

        val victimPlayer = t2?.players?.get(1)
        assertTrue(victimPlayer?.eliminated == true)
        assertTrue(victimPlayer?.alive == false)
        assertEquals(3, t2?.currentAlivePlayers)
    }

    @Test
    fun testUnknownPlayerDoesNotCreateFakePlayer() {
        val unknownPlayer = TournamentRoster.resolvePlayerByName("NonExistentPlayerXYZ")
        assertNull(unknownPlayer)
        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = "NonExistentPlayerXYZ",
            rawRight = "AnotherUnknown",
            cause = KillFeedCause.GUN
        )
        assertNotNull(decision)
    }

    @Test
    fun testNameNumberConflictDoesNotUpdateWrongPlayer() {
        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = "Team 1 Player 4 BravoP1",
            rawRight = "T2 P2",
            cause = KillFeedCause.GUN,
            confidence = 0.95f
        )
        assertEquals(PUBGDecisionType.NO_DECISION_WAIT, decision.decisionType)
        assertTrue(decision.confidence < 0.5f)
    }

    @Test
    fun testTwoSimultaneousKillFeedRowsKeepIdentitiesSeparate() = runBlocking {
        val detector = PUBGKillFeedDetector()
        detector.reset()

        val evidence = DetectionEvidence(
            frameTimestamp = 5000L,
            roiId = testRoi.id,
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 1920,
            croppedHeight = 1080,
            metadata = mapOf(
                "feed_line" to "T1 P1 [M416] T2 P1\nT1 P4 [AWM] T2 P2"
            )
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)
        val success = result as IDetectionResult.Success
        assertEquals(2, success.allEvents.size)

        val ev0 = success.allEvents[0]
        val ev1 = success.allEvents[1]

        assertEquals("AlphaP1", ev0.killerPlayerId)
        assertEquals("BravoP1", ev0.victimPlayerId)

        assertEquals("AlphaP4", ev1.killerPlayerId)
        assertEquals("BravoP2", ev1.victimPlayerId)
    }
}
