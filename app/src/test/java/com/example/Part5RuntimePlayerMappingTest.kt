package com.example

import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.EsportsEventType
import com.example.core.model.PlayerLiveState
import com.example.core.model.TeamLiveState
import com.example.services.streaming.LocalLiveRuntimeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit & Integration Test Suite for PART 5 — Fixed Detected Identity → Runtime Player Mapping.
 */
class Part5RuntimePlayerMappingTest {

    @Before
    fun setup() {
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

        val team3 = TeamLiveState(
            teamNumber = 3,
            teamName = "Team Gamma",
            currentAlivePlayers = 4,
            players = listOf(
                PlayerLiveState(playerName = "GammaP1", teamNumber = 3),
                PlayerLiveState(playerName = "GammaP2", teamNumber = 3),
                PlayerLiveState(playerName = "GammaP3", teamNumber = 3),
                PlayerLiveState(playerName = "GammaP4", teamNumber = 3)
            )
        )

        LocalLiveRuntimeManager.initializeFromRoster(listOf(team1, team3))
    }

    @Test
    fun testTeam1Player4MapsExactlyToTeam1Player4() {
        val event = EsportsDetectedEvent(
            id = "evt_p5_t1p4",
            sessionId = "s1",
            eventType = EsportsEventType.KILL,
            timestampMs = 1000L,
            killerPlayerName = "Team 1 Player 4",
            killerTeamTag = "T1",
            victimPlayerName = "GammaP1",
            victimTeamTag = "T3",
            weaponUsed = "M416",
            confidence = 0.95f,
            source = "TEST",
            frameReferenceId = 100L,
            metadata = mapOf("decision_type" to "ENEMY_KILL", "kill_credit_allowed" to "true", "credited_kill_count" to "1")
        )

        LocalLiveRuntimeManager.processDetectedEvent(event)

        val teams = LocalLiveRuntimeManager.teamsState.value
        val t1 = teams.find { it.teamNumber == 1 }!!

        // Only Player 4 (index 3) should receive the kill, others 0
        assertEquals(0, t1.players[0].currentMatchKills)
        assertEquals(0, t1.players[1].currentMatchKills)
        assertEquals(0, t1.players[2].currentMatchKills)
        assertEquals(1, t1.players[3].currentMatchKills)
    }

    @Test
    fun testTeam3Player2MapsExactlyToTeam3Player2() {
        val event = EsportsDetectedEvent(
            id = "evt_p5_t3p2",
            sessionId = "s1",
            eventType = EsportsEventType.KILL,
            timestampMs = 2000L,
            killerPlayerName = "AlphaP1",
            killerTeamTag = "T1",
            victimPlayerName = "Team 3 Player 2",
            victimTeamTag = "T3",
            weaponUsed = "SCAR-L",
            confidence = 0.95f,
            source = "TEST",
            frameReferenceId = 200L,
            metadata = mapOf("decision_type" to "ENEMY_KILL", "kill_credit_allowed" to "true", "credited_kill_count" to "1")
        )

        LocalLiveRuntimeManager.processDetectedEvent(event)

        val teams = LocalLiveRuntimeManager.teamsState.value
        val t3 = teams.find { it.teamNumber == 3 }!!

        // Only Player 2 (index 1) should be eliminated, others alive
        assertTrue(t3.players[0].alive)
        assertTrue(t3.players[0].eliminated == false)

        assertTrue(t3.players[1].eliminated)
        assertTrue(t3.players[1].alive == false)

        assertTrue(t3.players[2].alive)
        assertTrue(t3.players[3].alive)

        assertEquals(3, t3.currentAlivePlayers)
    }

    @Test
    fun testCorrectKillerReceivesKillAndVictimEliminated() {
        val event = EsportsDetectedEvent(
            id = "evt_p5_correct_killer",
            sessionId = "s1",
            eventType = EsportsEventType.KILL,
            timestampMs = 3000L,
            killerPlayerName = "T1 P4",
            killerTeamTag = "T1",
            victimPlayerName = "T3 P2",
            victimTeamTag = "T3",
            weaponUsed = "AWM",
            confidence = 0.95f,
            source = "TEST",
            frameReferenceId = 300L,
            metadata = mapOf("decision_type" to "ENEMY_KILL", "kill_credit_allowed" to "true", "credited_kill_count" to "1")
        )

        LocalLiveRuntimeManager.processDetectedEvent(event)

        val teams = LocalLiveRuntimeManager.teamsState.value
        val t1 = teams.find { it.teamNumber == 1 }!!
        val t3 = teams.find { it.teamNumber == 3 }!!

        assertEquals(1, t1.players[3].currentMatchKills)
        assertTrue(t3.players[1].eliminated)
    }

    @Test
    fun testWrongPlayerAndWrongTeamAreNeverUpdated() {
        val event = EsportsDetectedEvent(
            id = "evt_p5_wrong_target",
            sessionId = "s1",
            eventType = EsportsEventType.KILL,
            timestampMs = 4000L,
            killerPlayerName = "T1 P1",
            killerTeamTag = "T1",
            victimPlayerName = "T3 P1",
            victimTeamTag = "T3",
            weaponUsed = "UZI",
            confidence = 0.95f,
            source = "TEST",
            frameReferenceId = 400L,
            metadata = mapOf("decision_type" to "ENEMY_KILL", "kill_credit_allowed" to "true", "credited_kill_count" to "1")
        )

        LocalLiveRuntimeManager.processDetectedEvent(event)

        val teams = LocalLiveRuntimeManager.teamsState.value
        val t1 = teams.find { it.teamNumber == 1 }!!
        val t3 = teams.find { it.teamNumber == 3 }!!

        // Check that Player 2, 3, 4 of Team 1 received 0 kills
        assertEquals(0, t1.players[1].currentMatchKills)
        assertEquals(0, t1.players[2].currentMatchKills)
        assertEquals(0, t1.players[3].currentMatchKills)

        // Check that Player 2, 3, 4 of Team 3 are not eliminated
        assertTrue(t3.players[1].alive)
        assertTrue(t3.players[2].alive)
        assertTrue(t3.players[3].alive)
    }

    @Test
    fun testMissingOrUnknownPlayerDoesNotFallBackToAnotherPlayer() {
        val event = EsportsDetectedEvent(
            id = "evt_p5_unknown",
            sessionId = "s1",
            eventType = EsportsEventType.KILL,
            timestampMs = 5000L,
            killerPlayerName = "NonExistentPlayer99",
            killerTeamTag = "T1",
            victimPlayerName = "GhostVictimX",
            victimTeamTag = "T3",
            weaponUsed = "GUN",
            confidence = 0.95f,
            source = "TEST",
            frameReferenceId = 500L,
            metadata = mapOf("decision_type" to "ENEMY_KILL", "kill_credit_allowed" to "true", "credited_kill_count" to "1")
        )

        LocalLiveRuntimeManager.processDetectedEvent(event)

        val teams = LocalLiveRuntimeManager.teamsState.value
        val t1 = teams.find { it.teamNumber == 1 }!!
        val t3 = teams.find { it.teamNumber == 3 }!!

        // No player in Team 1 should have kills awarded
        for (p in t1.players) {
            assertEquals(0, p.currentMatchKills)
        }

        // No player in Team 3 should be eliminated
        for (p in t3.players) {
            assertTrue(p.alive)
            assertTrue(!p.eliminated)
        }
    }

    @Test
    fun testTwoSimultaneousKillFeedEventsRemainIndependentlyMapped() {
        val event1 = EsportsDetectedEvent(
            id = "evt_p5_simultaneous_1",
            sessionId = "s1",
            eventType = EsportsEventType.KILL,
            timestampMs = 6000L,
            killerPlayerName = "T1 P1",
            killerTeamTag = "T1",
            victimPlayerName = "T3 P1",
            victimTeamTag = "T3",
            weaponUsed = "M416",
            confidence = 0.95f,
            source = "TEST",
            frameReferenceId = 600L,
            metadata = mapOf("decision_type" to "ENEMY_KILL", "kill_credit_allowed" to "true", "credited_kill_count" to "1")
        )

        val event2 = EsportsDetectedEvent(
            id = "evt_p5_simultaneous_2",
            sessionId = "s1",
            eventType = EsportsEventType.KILL,
            timestampMs = 6000L,
            killerPlayerName = "T1 P4",
            killerTeamTag = "T1",
            victimPlayerName = "T3 P4",
            victimTeamTag = "T3",
            weaponUsed = "AWM",
            confidence = 0.95f,
            source = "TEST",
            frameReferenceId = 600L,
            metadata = mapOf("decision_type" to "ENEMY_KILL", "kill_credit_allowed" to "true", "credited_kill_count" to "1")
        )

        LocalLiveRuntimeManager.processDetectedEvent(event1)
        LocalLiveRuntimeManager.processDetectedEvent(event2)

        val teams = LocalLiveRuntimeManager.teamsState.value
        val t1 = teams.find { it.teamNumber == 1 }!!
        val t3 = teams.find { it.teamNumber == 3 }!!

        assertEquals(1, t1.players[0].currentMatchKills) // T1 P1
        assertEquals(1, t1.players[3].currentMatchKills) // T1 P4

        assertTrue(t3.players[0].eliminated) // T3 P1
        assertTrue(t3.players[3].eliminated) // T3 P4
        assertEquals(2, t3.currentAlivePlayers)
    }
}
