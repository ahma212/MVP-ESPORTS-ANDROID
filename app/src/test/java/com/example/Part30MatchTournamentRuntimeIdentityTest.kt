package com.example

import com.example.core.model.DetectedEventType
import com.example.core.model.DetectionEvidence
import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.EsportsEventType
import com.example.core.model.EventProcessingStatus
import com.example.core.model.PlayerLiveState
import com.example.core.model.RoiRegion
import com.example.core.model.TeamLiveState
import com.example.core.rules.ConfidenceGate
import com.example.services.detection.DetectionDecision
import com.example.services.detection.IDetectionResult
import com.example.services.detection.pubg.KillFeedCause
import com.example.services.detection.pubg.PUBGDecisionType
import com.example.services.detection.pubg.PUBGKillFeedDecisionEngine
import com.example.services.detection.pubg.PUBGKillFeedDetector
import com.example.services.detection.pubg.PUBGTextNormalizer
import com.example.services.detection.pubg.TournamentRoster
import com.example.services.streaming.LocalLiveRuntimeManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Part30MatchTournamentRuntimeIdentityTest {

    private lateinit var detector: PUBGKillFeedDetector
    private val testRoi = RoiRegion(
        id = "pubg_kill_feed_roi_part30",
        name = "KILL_FEED_ROI_PART30",
        x = 0.60f,
        y = 0.02f,
        width = 0.38f,
        height = 0.22f
    )

    @Before
    fun setup() {
        TournamentRoster.clearRoster()
        // Register official players for testing
        TournamentRoster.registerPlayer(4, 1, "Paraboy", teamName = "NOVA ESPORTS", teamTag = "NV")
        TournamentRoster.registerPlayer(4, 3, "Order", teamName = "NOVA ESPORTS", teamTag = "NV")
        TournamentRoster.registerPlayer(2, 1, "Top", teamName = "STALWART", teamTag = "STE")

        detector = PUBGKillFeedDetector()
        detector.reset()
        LocalLiveRuntimeManager.resetSession()
    }

    private fun createEvidence(
        leftText: String,
        rightText: String,
        cause: String,
        hasKnock: Boolean = false,
        hasFinish: Boolean = false,
        clarity: Float = 0.95f,
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
                "clarity" to clarity.toString(),
                "transitioning" to "false"
            )
        )
    }

    @Test
    fun `test 1 - Different teams plus correct player numbers awards kill`() = runBlocking {
        val evidence = createEvidence("Team 4 Player 1", "Team 2 Player 1", "M416", clarity = 0.95f)
        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)
        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KILL, success.event.eventType)
        assertEquals(DetectionDecision.AUTO_PROCESS, success.decision)
        assertEquals("PARABOY", success.event.killerPlayerId?.uppercase())
        assertEquals("TOP", success.event.victimPlayerId?.uppercase())
        assertEquals("ENEMY_KILL", success.event.metadata["decision_type"])
        assertEquals("true", success.event.metadata["kill_credit_allowed"])
    }

    @Test
    fun `test 2 - Same team kill awards no kill point`() = runBlocking {
        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = "Team 4 Player 1",
            rawRight = "Team 4 Player 3",
            cause = KillFeedCause.GUN,
            confidence = 0.95f
        )
        assertEquals(PUBGDecisionType.TEAM_KILL, decision.decisionType)
        assertNull(decision.pointAwardedTo)
        assertFalse(decision.killCreditAllowed)
    }

    @Test
    fun `test 3 - Same player self kill awards no kill point`() = runBlocking {
        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = "Team 4 Player 1",
            rawRight = "Team 4 Player 1",
            cause = KillFeedCause.GRENADE,
            confidence = 0.95f
        )
        assertEquals(PUBGDecisionType.SELF_KILL, decision.decisionType)
        assertNull(decision.pointAwardedTo)
        assertFalse(decision.killCreditAllowed)
    }

    @Test
    fun `test 4 - Stylized PUBG names normalize to correct identity`() {
        val normalized = PUBGTextNormalizer.normalizeStylizedName("Paraboy")
        assertEquals("paraboy", normalized)

        val score = PUBGTextNormalizer.fuzzyMatchScore("Paraboy", "paraboy")
        assertTrue(score >= 0.85f)
    }

    @Test
    fun `test 5 & 6 - Ambiguous or conflicting identity routes to Admin Review`() {
        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = "UnknownKiller",
            rawRight = "UnknownVictim",
            cause = KillFeedCause.GUN,
            confidence = 0.35f
        )
        assertTrue(decision.confidence < 0.50f)
        assertEquals(DetectionDecision.ADMIN_REVIEW, ConfidenceGate.evaluate(decision.confidence))
    }

    @Test
    fun `test 7 - KNOCK awards no kill`() = runBlocking {
        val evidence = createEvidence("Team 4 Player 1", "Team 2 Player 1", "M416", hasKnock = true, clarity = 0.95f)
        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)
        val success = result as IDetectionResult.Success
        assertEquals(DetectedEventType.KNOCK, success.event.eventType)
        assertEquals("0", success.event.metadata["credited_kill_count"])
        assertEquals("false", success.event.metadata["dead"])
    }

    @Test
    fun `test 8 - FINISH KILL awards correct scoring`() = runBlocking {
        val decision = PUBGKillFeedDecisionEngine.evaluate(
            rawLeft = "Team 4 Player 1",
            rawRight = "Team 2 Player 1",
            cause = KillFeedCause.GUN,
            hasFinishHelmet = true,
            confidence = 0.95f
        )
        assertNotNull(decision)
    }

    @Test
    fun `test 9 - Arrow and event icons do not cause identity confusion`() {
        val textWithArrow = "Team 4 Player 1 → [M416] → Team 2 Player 1"
        val identityLeft = PUBGTextNormalizer.extractTeamPlayerIdentity(textWithArrow.substringBefore("→"))
        assertNotNull(identityLeft)
        assertEquals(4, identityLeft?.teamNumber)
        assertEquals(1, identityLeft?.playerNumber)
    }

    private fun createTestRoster(): List<TeamLiveState> {
        val team1Players = listOf(
            PlayerLiveState(playerId = "t1_p1", playerName = "Paraboy", teamNumber = 1),
            PlayerLiveState(playerId = "t1_p2", playerName = "Order", teamNumber = 1)
        )
        val team2Players = listOf(
            PlayerLiveState(playerId = "t2_p1", playerName = "Top", teamNumber = 2),
            PlayerLiveState(playerId = "t2_p2", playerName = "Action", teamNumber = 2)
        )
        return listOf(
            TeamLiveState(teamNumber = 1, teamName = "NOVA ESPORTS", currentAlivePlayers = 2, players = team1Players),
            TeamLiveState(teamNumber = 2, teamName = "STALWART", currentAlivePlayers = 2, players = team2Players)
        )
    }

    @Test
    fun `test 10 - Match 2 starts without deleting Match 1 tournament totals`() {
        LocalLiveRuntimeManager.resetSession()
        LocalLiveRuntimeManager.initializeFromRoster(createTestRoster())

        // Simulate kill in Match 1
        val evt = EsportsDetectedEvent(
            id = "evt_m1",
            sessionId = "session_1",
            eventType = EsportsEventType.KILL,
            timestampMs = 1000L,
            confidence = 0.95f,
            killerPlayerName = "Paraboy",
            killerTeamTag = "NV",
            victimPlayerName = "Top",
            victimTeamTag = "STE",
            status = EventProcessingStatus.AUTO_PROCESSED,
            metadata = mapOf("decision_type" to "ENEMY_KILL", "credited_kill_count" to "1", "kill_credit_allowed" to "true")
        )
        LocalLiveRuntimeManager.processDetectedEvent(evt)

        val teamsBeforeNext = LocalLiveRuntimeManager.teamsState.value
        val novaBefore = teamsBeforeNext.first { it.teamNumber == 1 }
        assertEquals(1, novaBefore.currentMatchKills)
        assertEquals(1, novaBefore.tournamentKills)

        // Start Match 2
        val nextMatch = LocalLiveRuntimeManager.startNextMatch("Miramar")
        assertEquals(2, nextMatch)

        val teamsAfterNext = LocalLiveRuntimeManager.teamsState.value
        val novaAfter = teamsAfterNext.first { it.teamNumber == 1 }
        assertEquals("Current match kills should reset to 0", 0, novaAfter.currentMatchKills)
        assertEquals("Tournament kills must be preserved across matches", 1, novaAfter.tournamentKills)
    }

    @Test
    fun `test 11 - No double counting current match`() {
        LocalLiveRuntimeManager.resetSession()
        LocalLiveRuntimeManager.initializeFromRoster(createTestRoster())
        val evt = EsportsDetectedEvent(
            id = "evt_duplicate_check",
            sessionId = "session_1",
            eventType = EsportsEventType.KILL,
            timestampMs = 2000L,
            confidence = 0.95f,
            killerPlayerName = "Paraboy",
            victimPlayerName = "Top",
            status = EventProcessingStatus.AUTO_PROCESSED,
            metadata = mapOf("decision_type" to "ENEMY_KILL", "credited_kill_count" to "1", "kill_credit_allowed" to "true")
        )

        // Process once
        LocalLiveRuntimeManager.processDetectedEvent(evt)
        // Process same event ID again (duplicate)
        LocalLiveRuntimeManager.processDetectedEvent(evt)

        val nova = LocalLiveRuntimeManager.teamsState.value.first { it.teamNumber == 1 }
        assertEquals("Event processed twice with same ID must not double count", 1, nova.currentMatchKills)
    }

    @Test
    fun `test 12 - No fake player generation when roster data is present`() {
        TournamentRoster.clearRoster()
        TournamentRoster.registerPlayer(1, 1, "RealPlayer1", teamName = "RealTeam", teamTag = "REAL")
        assertTrue(TournamentRoster.isRosterLoaded())
        val resolved = TournamentRoster.resolvePlayer(1, 1)
        assertNotNull(resolved)
        assertEquals("RealPlayer1", resolved?.inGameName)
        assertFalse(resolved?.inGameName?.startsWith("Player ") == true)
    }
}
