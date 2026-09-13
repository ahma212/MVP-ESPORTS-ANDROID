package com.example

import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.EsportsEventType
import com.example.core.model.PlayerLiveState
import com.example.core.model.TeamLiveState
import com.example.services.detection.pubg.TournamentRoster
import com.example.services.streaming.LocalLiveRuntimeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePlayerRemovalRuntimeTest {

    @Before
    fun setup() {
        LocalLiveRuntimeManager.resetSession()
        LocalLiveRuntimeManager.initializeFromRoster(emptyList())
        TournamentRoster.clearRoster()
    }

    @Test
    fun `TEST 1 - Empty roster yields zero players automatically generated`() {
        LocalLiveRuntimeManager.initializeFromRoster(emptyList())
        val teams = LocalLiveRuntimeManager.teamsState.value
        assertTrue("Teams list must be empty when no roster is supplied", teams.isEmpty())

        // Even after switching match formats, zero players/teams are generated
        LocalLiveRuntimeManager.setMatchFormat("Squad")
        val squadTeams = LocalLiveRuntimeManager.teamsState.value
        assertTrue("No teams/players generated after setMatchFormat on empty roster", squadTeams.isEmpty())
    }

    @Test
    fun `TEST 2 - Incomplete squad keeps only supplied real players`() {
        val incompleteTeam = TeamLiveState(
            teamNumber = 1,
            teamName = "ALPHA Esports",
            players = listOf(
                PlayerLiveState(playerId = "a_p1", playerName = "PlayerA", teamNumber = 1),
                PlayerLiveState(playerId = "a_p2", playerName = "PlayerB", teamNumber = 1),
                PlayerLiveState(playerId = "a_p3", playerName = "PlayerC", teamNumber = 1)
            ),
            currentAlivePlayers = 3
        )
        LocalLiveRuntimeManager.initializeFromRoster(listOf(incompleteTeam))

        // Set Squad format (4 max per team)
        LocalLiveRuntimeManager.setMatchFormat("Squad")
        val currentTeams = LocalLiveRuntimeManager.teamsState.value
        val team1 = currentTeams.first { it.teamNumber == 1 }

        assertEquals("Squad must have exactly 3 real players", 3, team1.players.size)
        assertFalse("Must not generate Player D or P1_4", team1.players.any { it.playerName.contains("P1_") || it.playerName.contains("Player D") })
        assertEquals("PlayerA", team1.players[0].playerName)
        assertEquals("PlayerB", team1.players[1].playerName)
        assertEquals("PlayerC", team1.players[2].playerName)
    }

    @Test
    fun `TEST 3 - Missing player slot creates no placeholder player`() {
        val teamWithGaps = TeamLiveState(
            teamNumber = 2,
            teamName = "BRAVO Esports",
            players = listOf(
                PlayerLiveState(playerId = "b_p1", playerName = "Bravo1", teamNumber = 2)
            ),
            currentAlivePlayers = 1
        )
        LocalLiveRuntimeManager.initializeFromRoster(listOf(teamWithGaps))

        // Try setting Squad format (4 players per squad)
        LocalLiveRuntimeManager.setMatchFormat("Squad")
        val team2 = LocalLiveRuntimeManager.teamsState.value.first { it.teamNumber == 2 }

        assertEquals("Only 1 real player exists in team", 1, team2.players.size)
        assertFalse("No placeholder P2_2, P2_3, P2_4 generated", team2.players.any { it.playerName.startsWith("P2_") })
    }

    @Test
    fun `TEST 4 - Unknown kill-feed identity creates no new player`() {
        val team1 = TeamLiveState(
            teamNumber = 1,
            teamName = "Alpha",
            players = listOf(
                PlayerLiveState(playerId = "t1_p1", playerName = "AlphaOne", teamNumber = 1)
            ),
            currentAlivePlayers = 1
        )
        LocalLiveRuntimeManager.initializeFromRoster(listOf(team1))

        // Unknown killer and victim not in roster
        val unknownEvt = EsportsDetectedEvent(
            id = "evt_unknown_99",
            sessionId = "s1",
            eventType = EsportsEventType.KILL,
            confidence = 0.95f,
            killerPlayerName = "GhostKiller999",
            killerTeamTag = "99",
            victimPlayerName = "AnonymousVictim888",
            victimTeamTag = "88"
        )

        LocalLiveRuntimeManager.processDetectedEvent(unknownEvt)

        val teamsAfter = LocalLiveRuntimeManager.teamsState.value
        assertEquals("Teams count must remain unchanged at 1", 1, teamsAfter.size)
        assertEquals("Team 1 player count must remain 1", 1, teamsAfter.first().players.size)
        assertFalse("GhostKiller999 must NOT be added to roster", teamsAfter.flatMap { it.players }.any { it.playerName == "GhostKiller999" })
        assertFalse("AnonymousVictim888 must NOT be added to roster", teamsAfter.flatMap { it.players }.any { it.playerName == "AnonymousVictim888" })
    }

    @Test
    fun `TEST 5 - Valid explicit roster preserves all supplied players`() {
        val realTeam1 = TeamLiveState(
            teamNumber = 1,
            teamName = "NOVA ESPORTS",
            players = listOf(
                PlayerLiveState(playerId = "nv_1", playerName = "Paraboy", teamNumber = 1),
                PlayerLiveState(playerId = "nv_2", playerName = "Order", teamNumber = 1),
                PlayerLiveState(playerId = "nv_3", playerName = "Jimmy", teamNumber = 1),
                PlayerLiveState(playerId = "nv_4", playerName = "King", teamNumber = 1)
            ),
            currentAlivePlayers = 4
        )
        LocalLiveRuntimeManager.initializeFromRoster(listOf(realTeam1))

        val loadedTeams = LocalLiveRuntimeManager.teamsState.value
        assertEquals(1, loadedTeams.size)
        val loadedNv = loadedTeams.first()
        assertEquals(4, loadedNv.players.size)
        assertEquals("Paraboy", loadedNv.players[0].playerName)
        assertEquals("Order", loadedNv.players[1].playerName)
        assertEquals("Jimmy", loadedNv.players[2].playerName)
        assertEquals("King", loadedNv.players[3].playerName)
    }

    @Test
    fun `TEST 6 - Standing table state has zero fake players`() {
        val duoTeam = TeamLiveState(
            teamNumber = 3,
            teamName = "DUO TEAM",
            players = listOf(
                PlayerLiveState(playerId = "d1", playerName = "DuoAlpha", teamNumber = 3),
                PlayerLiveState(playerId = "d2", playerName = "DuoBeta", teamNumber = 3)
            ),
            currentAlivePlayers = 2
        )
        LocalLiveRuntimeManager.initializeFromRoster(listOf(duoTeam))

        val teams = LocalLiveRuntimeManager.teamsState.value
        val standingPlayers = teams.flatMap { it.players }

        assertEquals(2, standingPlayers.size)
        assertTrue("All standing players match real configured names", standingPlayers.all { it.playerName == "DuoAlpha" || it.playerName == "DuoBeta" })
        assertFalse("No placeholder names in standing table state", standingPlayers.any { it.playerName.contains("P3_") || it.playerName.contains("Player") })
    }
}
