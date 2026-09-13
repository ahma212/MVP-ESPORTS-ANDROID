package com.example

import com.example.core.model.DetectedEventType
import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.EsportsEventType
import com.example.core.model.TeamLiveState
import com.example.core.rules.ScoringRules
import com.example.services.streaming.LocalLiveRuntimeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit & Integration Test Suite for PART 9 — Placement Score Runtime Integration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Part9PlacementScoreTest {

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
    fun testFirstPlaceTeamReceivesCorrectPlacementPoints() {
        val awarded = LocalLiveRuntimeManager.recordTeamPlacement(1, 1, "evt_1st")
        assertTrue(awarded)

        val team1 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        assertEquals(1, team1.currentMatchPlacement)
        assertEquals(10, team1.placementPoints)
        assertEquals(10, team1.currentMatchPoints)
        assertEquals(10, team1.tournamentPoints)
        assertEquals(1, team1.rank)
    }

    @Test
    fun testSecondPlaceTeamReceivesCorrectPlacementPoints() {
        val awarded = LocalLiveRuntimeManager.recordTeamPlacement(2, 2, "evt_2nd")
        assertTrue(awarded)

        val team2 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 2 }!!
        assertEquals(2, team2.currentMatchPlacement)
        assertEquals(6, team2.placementPoints)
        assertEquals(6, team2.currentMatchPoints)
        assertEquals(6, team2.tournamentPoints)
    }

    @Test
    fun testMultipleTeamsReceiveTheirOwnCorrectPlacementPoints() {
        LocalLiveRuntimeManager.recordTeamPlacement(1, 1, "evt_1")
        LocalLiveRuntimeManager.recordTeamPlacement(2, 2, "evt_2")
        LocalLiveRuntimeManager.recordTeamPlacement(3, 3, "evt_3")

        val teams = LocalLiveRuntimeManager.teamsState.value
        val t1 = teams.find { it.teamNumber == 1 }!!
        val t2 = teams.find { it.teamNumber == 2 }!!
        val t3 = teams.find { it.teamNumber == 3 }!!

        assertEquals(10, t1.currentMatchPoints)
        assertEquals(6, t2.currentMatchPoints)
        assertEquals(5, t3.currentMatchPoints)
        assertEquals(1, t1.rank)
        assertEquals(2, t2.rank)
        assertEquals(3, t3.rank)
    }

    @Test
    fun testPlacementPointsAndKillPointsCombinedCorrectly() {
        // Award team 1 a kill (1 pt) and 1st place (10 pts)
        val killEvent = EsportsDetectedEvent(
            id = "kill_1",
            sessionId = "s1",
            eventType = EsportsEventType.KILL,
            confidence = 0.9f,
            killerPlayerName = "T1 P1",
            killerTeamTag = "1",
            victimPlayerName = "T2 P1",
            victimTeamTag = "2",
            metadata = mapOf("kill_credit_allowed" to "true", "credited_kill_count" to "1")
        )
        LocalLiveRuntimeManager.processDetectedEvent(killEvent)

        LocalLiveRuntimeManager.recordTeamPlacement(1, 1, "placement_1")

        val t1 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        assertEquals(1, t1.currentMatchKills)
        assertEquals(1, t1.tournamentKills)
        assertEquals(1, t1.currentMatchKills * ScoringRules.POINTS_PER_KILL) // 1 kill point
        assertEquals(10, t1.placementPoints) // 10 placement points
        assertEquals(11, t1.currentMatchPoints) // 1 + 10 = 11 pts
        assertEquals(11, t1.tournamentPoints)
    }

    @Test
    fun testDuplicatePlacementProtection() {
        // First placement event
        val awarded1 = LocalLiveRuntimeManager.recordTeamPlacement(1, 1, "evt_dup")
        assertTrue(awarded1)

        val t1Before = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        assertEquals(10, t1Before.currentMatchPoints)

        // Duplicate call with same eventId or same match placement
        val awarded2 = LocalLiveRuntimeManager.recordTeamPlacement(1, 1, "evt_dup")
        assertFalse(awarded2)

        val t1After = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        // Points should not increase again
        assertEquals(10, t1After.currentMatchPoints)
        assertEquals(10, t1After.tournamentPoints)
    }

    @Test
    fun testRepeatedPlacementDetectionDoesNotIncreaseScore() {
        LocalLiveRuntimeManager.recordTeamPlacement(2, 4, "placement_4th")
        val t2 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 2 }!!
        assertEquals(4, t2.currentMatchPoints) // 4th place = 4 pts

        // Try to record 4th place again for team 2 in same match
        LocalLiveRuntimeManager.recordTeamPlacement(2, 4, "placement_4th_repeat")
        assertEquals(4, t2.currentMatchPoints)
    }

    @Test
    fun testCorrectTeamReceivesPointsAndWrongTeamDoesNot() {
        LocalLiveRuntimeManager.recordTeamPlacement(3, 1, "t3_1st")
        val t1 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        val t3 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 3 }!!

        assertEquals(0, t1.currentMatchPoints)
        assertEquals(10, t3.currentMatchPoints)
        assertEquals(1, t3.rank)
    }

    @Test
    fun testInvalidOrUnknownPlacementGivesNoPoints() {
        val invalidZero = LocalLiveRuntimeManager.recordTeamPlacement(1, 0, "zero")
        assertFalse(invalidZero)

        val invalidNegative = LocalLiveRuntimeManager.recordTeamPlacement(1, -1, "neg")
        assertFalse(invalidNegative)

        val invalidOutOfBounds = LocalLiveRuntimeManager.recordTeamPlacement(1, 17, "out")
        assertFalse(invalidOutOfBounds)

        val t1 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        assertEquals(0, t1.currentMatchPoints)
    }

    @Test
    fun testMatchEndFinalizationAndCumulativeTournamentPointsPreserved() {
        // Match 1: Team 1 gets 1st place (10 pts)
        LocalLiveRuntimeManager.recordTeamPlacement(1, 1, "match1_t1")
        val t1Match1 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        assertEquals(10, t1Match1.tournamentPoints)

        // Start next match
        LocalLiveRuntimeManager.startNextMatch("Miramar")

        val t1Match2Start = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        assertEquals(0, t1Match2Start.currentMatchPoints)
        assertEquals(10, t1Match2Start.tournamentPoints) // Cumulative preserved!
        assert(t1Match2Start.currentMatchPlacement == null)

        // Match 2: Team 1 gets 2nd place (6 pts)
        LocalLiveRuntimeManager.recordTeamPlacement(1, 2, "match2_t1")
        val t1Match2End = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        assertEquals(6, t1Match2End.currentMatchPoints)
        assertEquals(16, t1Match2End.tournamentPoints) // 10 + 6 = 16 cumulative tournament points
    }

    @Test
    fun testProcessedDetectedPlacementEventIntegration() {
        val placementEvent = EsportsDetectedEvent(
            id = "det_placement_1",
            sessionId = "s1",
            eventType = EsportsEventType.PLACEMENT,
            confidence = 0.95f,
            killerTeamTag = "2",
            metadata = mapOf("team_number" to "2", "placement" to "1")
        )
        LocalLiveRuntimeManager.processDetectedEvent(placementEvent)

        val t2 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 2 }!!
        assertEquals(1, t2.currentMatchPlacement)
        assertEquals(10, t2.currentMatchPoints)
    }
}
