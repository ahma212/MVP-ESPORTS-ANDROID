package com.example.services.supabase

import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.LeaderboardEntry
import com.example.core.model.MatchSession
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single Source of Truth service interface for Supabase data operations.
 *
 * All tournament sessions, matches, teams, players, detected events, admin reviews,
 * and live leaderboards are synchronized through this service.
 *
 * If Supabase is unavailable, this service does NOT silently fall back to mock data.
 */
interface ISupabaseService {
    val connectionState: StateFlow<SupabaseConnectionState>

    val liveEventsStream: SharedFlow<EsportsDetectedEvent>

    val liveLeaderboardStream: StateFlow<List<LeaderboardEntry>>

    val currentSession: StateFlow<com.example.services.supabase.api.SbSession?>

    val teamsCount: StateFlow<Int>

    val playersCount: StateFlow<Int>

    val activeSessionId: String?

    /**
     * Connects to Supabase with project credentials.
     */
    suspend fun connect(supabaseUrl: String, anonKey: String): Result<Unit>

    /**
     * Disconnects from Supabase.
     */
    suspend fun disconnect()

    /**
     * Pushes a detected or auto-processed event to the remote Supabase database.
     */
    suspend fun recordDetectedEvent(event: EsportsDetectedEvent): Result<Unit>

    /**
     * Admin confirms a low-confidence event.
     */
    suspend fun confirmAdminReviewEvent(eventId: String, confirmedBy: String): Result<Unit>

    /**
     * Admin rejects a low-confidence false detection.
     */
    suspend fun rejectAdminReviewEvent(eventId: String, reason: String, rejectedBy: String): Result<Unit>

    /**
     * Fetches the list of low-confidence events waiting in the Admin Review Queue.
     */
    suspend fun fetchAdminReviewQueue(sessionId: String): Result<List<EsportsDetectedEvent>>

    /**
     * Creates or attaches to a tournament match session.
     */
    suspend fun getOrCreateMatchSession(tournamentName: String, matchNumber: Int): Result<MatchSession>
}
