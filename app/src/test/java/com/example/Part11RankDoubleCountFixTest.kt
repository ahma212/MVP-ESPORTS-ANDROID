package com.example

import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.EsportsEventType
import com.example.core.model.TeamLiveState
import com.example.core.rules.ScoringRules
import com.example.services.streaming.LocalLiveRuntimeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit and Integration Test Suite for PART 11 — Placement Rank Double-Count Fix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Part11RankDoubleCountFixTest {

    @Before
    fun setup() {
        LocalLiveRuntimeManager.resetSession()
        LocalLiveRuntimeManager.initializeFromRoster(
            listOf(
                TeamLiveState(teamNumber = 1, teamName = "Team Alpha"),
                TeamLiveState(teamNumber = 2, teamName = "Team Bravo"),
                TeamLiveState(teamNumber = 3, teamName = "Team Charlie")
            )
        )
    }

    @Test
    fun testCurrentMatchPointsAndKillsCountedExactlyOnce() {
        // Award placement 1 (10 points) to Team Alpha (Team 1)
        val awarded1 = LocalLiveRuntimeManager.recordTeamPlacement(1, 1, "evt_alpha_placement")
        assertTrue(awarded1)

        // Award 1 kill to Team Alpha (1 point) via EsportsDetectedEvent
        val killEvent = EsportsDetectedEvent(
            id = "kill_1",
            sessionId = "session_1",
            eventType = EsportsEventType.KILL,
            confidence = 0.99f,
            killerPlayerName = "T1_P1",
            killerTeamTag = "1",
            victimPlayerName = "T2_P1",
            victimTeamTag = "2"
        )
        LocalLiveRuntimeManager.processDetectedEvent(killEvent)

        val team1 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        // Team Alpha points: 10 (placement) + 1 (kill) = 11 points
        assertEquals(11, team1.currentMatchPoints)
        assertEquals(11, team1.tournamentPoints)

        // Verify that the rank calculation did not double-count current match points
        // If it double-counted, it would think Team 1 has 11 + 11 = 22 points
        // Here we verify that Team 1 is rank 1 and has exactly 11 points in tournamentPoints
        assertEquals(1, team1.rank)
    }

    @Test
    fun testRankingOrderIsCorrectAfterMultipleMatches() {
        // --- MATCH 1 ---
        // Team Alpha (Team 1) gets 1st place (10 points) + 2 kills (2 points) = 12 total points
        LocalLiveRuntimeManager.recordTeamPlacement(1, 1, "m1_p1")
        LocalLiveRuntimeManager.processDetectedEvent(EsportsDetectedEvent(
            id = "m1_k1", sessionId = "s", eventType = EsportsEventType.KILL, killerPlayerName = "T1_P1", killerTeamTag = "1", victimPlayerName = "T2_P1", victimTeamTag = "2", confidence = 0.99f
        ))
        LocalLiveRuntimeManager.processDetectedEvent(EsportsDetectedEvent(
            id = "m1_k2", sessionId = "s", eventType = EsportsEventType.KILL, killerPlayerName = "T1_P1", killerTeamTag = "1", victimPlayerName = "T2_P2", victimTeamTag = "2", confidence = 0.99f
        ))

        // Team Bravo (Team 2) gets 2nd place (6 points) + 1 kill (1 point) = 7 total points
        LocalLiveRuntimeManager.recordTeamPlacement(2, 2, "m1_p2")
        LocalLiveRuntimeManager.processDetectedEvent(EsportsDetectedEvent(
            id = "m1_k3", sessionId = "s", eventType = EsportsEventType.KILL, killerPlayerName = "T2_P1", killerTeamTag = "2", victimPlayerName = "T3_P1", victimTeamTag = "3", confidence = 0.99f
        ))

        // Team Charlie (Team 3) gets 3rd place (4 points) + 0 kills = 4 total points
        LocalLiveRuntimeManager.recordTeamPlacement(3, 3, "m1_p3")

        // End Match 1 and Start Match 2
        LocalLiveRuntimeManager.startNextMatch("Miramar")

        // Verify that tournament total totals are preserved but currentMatchPoints/Kills are reset to 0
        val t1AfterM1 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        assertEquals(0, t1AfterM1.currentMatchPoints)
        assertEquals(0, t1AfterM1.currentMatchKills)
        assertEquals(12, t1AfterM1.tournamentPoints)
        assertEquals(2, t1AfterM1.tournamentKills)

        // --- MATCH 2 ---
        // In Match 2, Team Charlie (Team 3) gets 1st place (10 points) + 5 kills (5 points) = 15 points
        LocalLiveRuntimeManager.recordTeamPlacement(3, 1, "m2_p1")
        for (i in 1..5) {
            LocalLiveRuntimeManager.processDetectedEvent(EsportsDetectedEvent(
                id = "m2_k_$i", sessionId = "s", eventType = EsportsEventType.KILL, killerPlayerName = "T3_P1", killerTeamTag = "3", victimPlayerName = "T2_P1", victimTeamTag = "2", confidence = 0.99f
            ))
        }

        // Team Bravo (Team 2) gets 2nd place (6 points) + 0 kills = 6 points
        LocalLiveRuntimeManager.recordTeamPlacement(2, 2, "m2_p2")

        // Team Alpha (Team 1) gets 3rd place (4 points) + 1 kill = 5 points
        LocalLiveRuntimeManager.recordTeamPlacement(1, 3, "m2_p3")
        LocalLiveRuntimeManager.processDetectedEvent(EsportsDetectedEvent(
            id = "m2_k_alpha", sessionId = "s", eventType = EsportsEventType.KILL, killerPlayerName = "T1_P1", killerTeamTag = "1", victimPlayerName = "T2_P2", victimTeamTag = "2", confidence = 0.99f
        ))

        // Total Cumulative points expectation:
        // Team 1: Match 1 (12) + Match 2 (6) = 18 points, Kills = 3
        // Team 2: Match 1 (7) + Match 2 (6) = 13 points, Kills = 1
        // Team 3: Match 1 (5) + Match 2 (15) = 20 points, Kills = 5

        val teams = LocalLiveRuntimeManager.teamsState.value
        val team1 = teams.find { it.teamNumber == 1 }!!
        val team2 = teams.find { it.teamNumber == 2 }!!
        val team3 = teams.find { it.teamNumber == 3 }!!

        println("DEBUG TEAM 1: currentMatchPoints=${team1.currentMatchPoints}, currentMatchKills=${team1.currentMatchKills}, tournamentPoints=${team1.tournamentPoints}, tournamentKills=${team1.tournamentKills}, rank=${team1.rank}")
        println("DEBUG TEAM 2: currentMatchPoints=${team2.currentMatchPoints}, currentMatchKills=${team2.currentMatchKills}, tournamentPoints=${team2.tournamentPoints}, tournamentKills=${team2.tournamentKills}, rank=${team2.rank}")
        println("DEBUG TEAM 3: currentMatchPoints=${team3.currentMatchPoints}, currentMatchKills=${team3.currentMatchKills}, tournamentPoints=${team3.tournamentPoints}, tournamentKills=${team3.tournamentKills}, rank=${team3.rank}")

        assertEquals(18, team1.tournamentPoints)
        assertEquals(13, team2.tournamentPoints)
        assertEquals(20, team3.tournamentPoints)

        // Expected standings order:
        // Rank 1: Team Charlie (Team 3) with 20 points
        // Rank 2: Team Alpha (Team 1) with 18 points
        // Rank 3: Team Bravo (Team 2) with 13 points

        assertEquals(1, team3.rank)
        assertEquals(2, team1.rank)
        assertEquals(3, team2.rank)
    }
}
