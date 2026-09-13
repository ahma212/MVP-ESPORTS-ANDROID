package com.example.services.event

import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.LeaderboardEntry
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Event processing engine that enforces the core esports business logic and confidence rules.
 */
interface IEventProcessingService {
    /**
     * Observable stream of auto-processed and admin-confirmed events.
     */
    val processedEventsStream: SharedFlow<EsportsDetectedEvent>

    /**
     * Observable queue of low-confidence events awaiting admin approval.
     */
    val adminReviewQueue: StateFlow<List<EsportsDetectedEvent>>

    /**
     * Live calculated leaderboard for tournament display and stream overlay.
     */
    val leaderboardState: StateFlow<List<LeaderboardEntry>>

    /**
     * Ingests a new detected event from the AI / CV engine.
     */
    suspend fun ingestDetectedEvent(event: EsportsDetectedEvent)

    /**
     * Admin action to approve a low-confidence detected event.
     */
    suspend fun confirmEvent(eventId: String, confirmedBy: String)

    /**
     * Admin action to reject a false detection.
     */
    suspend fun rejectEvent(eventId: String, reason: String, rejectedBy: String)

    /**
     * Resets match state for a new session.
     */
    fun resetSession()
}
