package com.example

import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.EsportsEventType
import com.example.core.model.PlayerLiveState
import com.example.core.model.TeamLiveState
import com.example.services.streaming.LocalLiveRuntimeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Phase5bStandingTableTest {
    @Before
    fun setup() {
        val t1 = TeamLiveState(
            teamNumber = 1,
            teamName = "Alpha",
            players = listOf(
                PlayerLiveState(playerName = "A1", teamNumber = 1),
                PlayerLiveState(playerName = "A2", teamNumber = 1)
            ),
            currentAlivePlayers = 2
        )
        val t2 = TeamLiveState(
            teamNumber = 2,
            teamName = "Bravo",
            players = listOf(
                PlayerLiveState(playerName = "B1", teamNumber = 2),
                PlayerLiveState(playerName = "B2", teamNumber = 2)
            ),
            currentAlivePlayers = 2
        )
        LocalLiveRuntimeManager.initializeFromRoster(listOf(t1, t2))
    }

    @Test
    fun testKillUpdatesPointsAndEliminatesPlayer() {
        val event = EsportsDetectedEvent(
            id = "evt1",
            sessionId = "s1",
            eventType = EsportsEventType.KILL,
            confidence = 0.9f,
            killerPlayerName = "A1",
            killerTeamTag = "1",
            victimPlayerName = "B1",
            victimTeamTag = "2"
        )
        LocalLiveRuntimeManager.processDetectedEvent(event)

        val state = LocalLiveRuntimeManager.teamsState.value
        val t1 = state.find { it.teamNumber == 1 }!!
        val t2 = state.find { it.teamNumber == 2 }!!

        assertEquals(1, t1.currentMatchPoints)
        assertEquals(1, t1.currentMatchKills)
        assertEquals(1, t1.rank)

        assertEquals(1, t2.currentAlivePlayers)
        assertTrue(t2.players.find { it.playerName == "B1" }!!.eliminated)
        assertEquals(2, t2.rank)
    }

    @Test
    fun testKnockDoesNotDecrementAliveCount() {
        // Knock event should mark player as knocked, but NOT eliminated, so ALIVE count remains 2
        val knockEvent = EsportsDetectedEvent(
            id = "evt_knock",
            sessionId = "s1",
            eventType = EsportsEventType.KNOCK,
            confidence = 0.95f,
            killerPlayerName = "A1",
            killerTeamTag = "1",
            victimPlayerName = "B1",
            victimTeamTag = "2"
        )
        LocalLiveRuntimeManager.processDetectedEvent(knockEvent)

        val state = LocalLiveRuntimeManager.teamsState.value
        val t2 = state.find { it.teamNumber == 2 }!!

        val knockedPlayer = t2.players.find { it.playerName == "B1" }!!
        assertTrue("Player should be knocked", knockedPlayer.knocked)
        assertTrue("Player should not be eliminated", !knockedPlayer.eliminated)
        assertEquals("Alive count must remain 2 when knocked", 2, t2.currentAlivePlayers)
    }

    @Test
    fun testMax16TeamsEnforced() {
        val twentyTeams = (1..20).map { id ->
            TeamLiveState(
                teamNumber = id,
                teamName = "Team $id",
                players = listOf(PlayerLiveState(playerName = "P$id", teamNumber = id)),
                currentAlivePlayers = 1
            )
        }
        LocalLiveRuntimeManager.initializeFromRoster(twentyTeams)
        val state = LocalLiveRuntimeManager.teamsState.value

        assertEquals("Must strictly cap at maximum 16 teams", 16, state.size)
        assertEquals(16, state.last().teamNumber)
    }

    @Test
    fun testMatchFormatDynamicPlayerCount() {
        LocalLiveRuntimeManager.setMatchFormat("Solo")
        val soloTeams = LocalLiveRuntimeManager.teamsState.value
        assertEquals(1, soloTeams.first().players.size)

        LocalLiveRuntimeManager.setMatchFormat("Duo")
        val duoTeams = LocalLiveRuntimeManager.teamsState.value
        assertEquals(2, duoTeams.first().players.size)

        LocalLiveRuntimeManager.setMatchFormat("Squad")
        val squadTeams = LocalLiveRuntimeManager.teamsState.value
        assertEquals("Squad format with 2 real players retains only those 2 real players", 2, squadTeams.first().players.size)
    }
}
