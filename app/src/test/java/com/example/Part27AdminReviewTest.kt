package com.example

import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.EsportsEventType
import com.example.core.model.EventProcessingStatus
import com.example.core.model.PlayerLiveState
import com.example.core.model.TeamLiveState
import com.example.core.rules.ScoringRules
import com.example.services.event.EventProcessor
import com.example.services.event.EventProcessorHooks
import com.example.services.streaming.LocalLiveRuntimeManager
import com.example.services.supabase.ISupabaseService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Part27AdminReviewTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockSupabase = object : ISupabaseService {
        override val connectionState = kotlinx.coroutines.flow.MutableStateFlow(
            com.example.services.supabase.SupabaseConnectionState.Connected("https://mock.supabase.co")
        )
        override val liveEventsStream = kotlinx.coroutines.flow.MutableSharedFlow<EsportsDetectedEvent>()
        override val liveLeaderboardStream = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.core.model.LeaderboardEntry>>(emptyList())
        override val currentSession = kotlinx.coroutines.flow.MutableStateFlow<com.example.services.supabase.api.SbSession?>(null)
        override val teamsCount = kotlinx.coroutines.flow.MutableStateFlow(0)
        override val playersCount = kotlinx.coroutines.flow.MutableStateFlow(0)
        override val activeSessionId: String? = null

        override suspend fun connect(supabaseUrl: String, anonKey: String): Result<Unit> = Result.success(Unit)
        override suspend fun disconnect() {}
        override suspend fun recordDetectedEvent(event: EsportsDetectedEvent): Result<Unit> = Result.success(Unit)
        override suspend fun confirmAdminReviewEvent(eventId: String, confirmedBy: String): Result<Unit> = Result.success(Unit)
        override suspend fun rejectAdminReviewEvent(eventId: String, reason: String, rejectedBy: String): Result<Unit> = Result.success(Unit)
        override suspend fun fetchAdminReviewQueue(sessionId: String): Result<List<EsportsDetectedEvent>> = Result.success(emptyList())
        override suspend fun getOrCreateMatchSession(tournamentName: String, matchNumber: Int) = Result.success(
            com.example.core.model.MatchSession(id = "test_session", tournamentName = tournamentName, matchNumber = matchNumber)
        )
    }

    private lateinit var eventProcessor: EventProcessor

    @Before
    fun setUp() {
        // Clear state of runtime scoreboard
        LocalLiveRuntimeManager.resetSession()

        // Enforce clean singleton instance
        eventProcessor = EventProcessor(mockSupabase, testScope) as EventProcessor
        EventProcessor.setInstance(eventProcessor)
        
        // Attach EventProcessorHooks to bridge EventProcessor -> LocalLiveRuntimeManager
        EventProcessorHooks.attachTo(eventProcessor, mockSupabase, testScope)
    }

    @Test
    fun testEmptyQueueDisplaysCorrectly() {
        val reviewQueue = eventProcessor.adminReviewQueue.value
        assertTrue("Queue must start empty", reviewQueue.isEmpty())
    }

    @Test
    fun testPendingReviewAppearsInQueue() = runTest(testDispatcher) {
        val lowConfEvent = createLowConfidenceEvent("evt_low_1", EsportsEventType.KILL)
        eventProcessor.ingestDetectedEvent(lowConfEvent)

        val reviewQueue = eventProcessor.adminReviewQueue.value
        assertEquals("Queue should have exactly 1 event", 1, reviewQueue.size)
        assertEquals("Event ID matches", "evt_low_1", reviewQueue[0].id)
    }

    @Test
    fun testYesConfirmsExactlyOnceAndSavesScore() = runTest(testDispatcher) {
        val roster = createTestRoster()
        LocalLiveRuntimeManager.initializeFromRoster(roster)

        val lowConfEvent = createLowConfidenceEvent("evt_confirm_once", EsportsEventType.KILL)
        eventProcessor.ingestDetectedEvent(lowConfEvent)

        // Confirm
        eventProcessor.confirmEvent("evt_confirm_once", "test_admin")

        // Scoreboard updated
        val nv = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        assertEquals("NV match kills should be 1 after approval", 1, nv.currentMatchKills)

        // Check empty queue
        assertTrue("Queue should be empty after confirmation", eventProcessor.adminReviewQueue.value.isEmpty())

        // Try double-confirming (prevent duplicate review action)
        try {
            eventProcessor.confirmEvent("evt_confirm_once", "test_admin")
            fail("Confirming a completed event must throw an exception")
        } catch (e: Exception) {
            assertTrue(e is IllegalStateException || e is IllegalArgumentException)
        }
    }

    @Test
    fun testNoRejectsExactlyOnceAndChangesNoScore() = runTest(testDispatcher) {
        val roster = createTestRoster()
        LocalLiveRuntimeManager.initializeFromRoster(roster)

        val lowConfEvent = createLowConfidenceEvent("evt_reject_once", EsportsEventType.KILL)
        eventProcessor.ingestDetectedEvent(lowConfEvent)

        // Reject
        eventProcessor.rejectEvent("evt_reject_once", "Not a real kill", "test_admin")

        // Scoreboard unchanged
        val nv = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        assertEquals("NV match kills should be 0 after rejection", 0, nv.currentMatchKills)

        // Check empty queue
        assertTrue("Queue should be empty after rejection", eventProcessor.adminReviewQueue.value.isEmpty())

        // Try double-rejecting (prevent duplicate review action)
        try {
            eventProcessor.rejectEvent("evt_reject_once", "Already rejected", "test_admin")
            fail("Rejecting a completed event must throw an exception")
        } catch (e: Exception) {
            assertTrue(e is IllegalStateException || e is IllegalArgumentException)
        }
    }

    @Test
    fun testFailedConfirmationRemainsPendingAndChangesNoScore() = runTest(testDispatcher) {
        val roster = createTestRoster()
        LocalLiveRuntimeManager.initializeFromRoster(roster)

        val lowConfEvent = createLowConfidenceEvent("fail_confirm", EsportsEventType.KILL)
        eventProcessor.ingestDetectedEvent(lowConfEvent)

        // Trigger simulated failure
        try {
            eventProcessor.confirmEvent("fail_confirm", "test_admin")
            fail("Should throw simulated exception")
        } catch (e: Exception) {
            assertEquals("Simulated confirmation failure", e.message)
        }

        // Verify it remains pending in the queue
        val reviewQueue = eventProcessor.adminReviewQueue.value
        assertEquals("Queue should still contain the event", 1, reviewQueue.size)
        assertEquals("Event ID matches", "fail_confirm", reviewQueue[0].id)

        // Scoreboard unchanged
        val nv = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        assertEquals("NV match kills should remain 0", 0, nv.currentMatchKills)
    }

    @Test
    fun testKnockIsNeverConvertedToKill() = runTest(testDispatcher) {
        val roster = createTestRoster()
        LocalLiveRuntimeManager.initializeFromRoster(roster)

        val knockEvent = createLowConfidenceEvent("evt_knock_test", EsportsEventType.KNOCK)
        eventProcessor.ingestDetectedEvent(knockEvent)

        // Confirm the KNOCK
        eventProcessor.confirmEvent("evt_knock_test", "test_admin")

        // Check scoreboard: player knocked is true, but match kills is STILL 0 (knock is NOT a kill)
        val nv = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 1 }!!
        assertEquals("Kills must be 0 for KNOCK events", 0, nv.currentMatchKills)

        val player = nv.players.find { it.playerName == "Paraboy" }!!
        // Since Paraboy knocked Revo, Revo should be knocked
        val a7 = LocalLiveRuntimeManager.teamsState.value.find { it.teamNumber == 2 }!!
        val victimPlayer = a7.players.find { it.playerName == "Revo" }!!
        assertTrue("Victim should be knocked", victimPlayer.knocked)
        assertTrue("Victim remains alive", victimPlayer.alive)
    }

    private fun createLowConfidenceEvent(id: String, eventType: EsportsEventType): EsportsDetectedEvent {
        return EsportsDetectedEvent(
            id = id,
            sessionId = "test_session",
            eventType = eventType,
            killerPlayerName = "Paraboy",
            killerTeamTag = "NV",
            victimPlayerName = "Revo",
            victimTeamTag = "A7",
            confidence = 0.35f, // Below 0.50 threshold
            metadata = mapOf(
                "decision_type" to if (eventType == EsportsEventType.KNOCK) "KNOCK" else "ENEMY_KILL",
                "kill_credit_allowed" to "true",
                "credited_kill_count" to "1",
                "point_awarded_to" to "Paraboy",
                "dead" to if (eventType == EsportsEventType.KNOCK) "false" else "true"
            )
        )
    }

    private fun createTestRoster(): List<TeamLiveState> {
        return listOf(
            TeamLiveState(
                teamNumber = 1,
                teamName = "NOVA ESPORTS",
                currentAlivePlayers = 4,
                players = listOf(
                    PlayerLiveState(playerId = "t1_p1", playerName = "Paraboy", teamNumber = 1, alive = true)
                )
            ),
            TeamLiveState(
                teamNumber = 2,
                teamName = "ALPHA 7",
                currentAlivePlayers = 4,
                players = listOf(
                    PlayerLiveState(playerId = "t2_p1", playerName = "Revo", teamNumber = 2, alive = true)
                )
            )
        )
    }
}
