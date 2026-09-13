package com.example.services.supabase.api

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Header
import retrofit2.http.Body
import okhttp3.ResponseBody

interface SupabaseApi {
    @GET("rest/v1/live_broadcast_sessions")
    suspend fun getSessions(
        @Query("select") select: String = "*",
        @Query("status") status: String? = null,
        @Query("order") order: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("id") id: String? = null
    ): List<SbSession>

    @GET("rest/v1/live_broadcast_teams")
    suspend fun getTeams(
        @Query("session_id") sessionId: String,
        @Query("select") select: String = "*"
    ): List<SbTeam>

    @GET("rest/v1/live_broadcast_players")
    suspend fun getPlayers(
        @Query("session_id") sessionId: String,
        @Query("select") select: String = "*"
    ): List<SbPlayer>

    @GET("rest/v1/live_broadcast_matches")
    suspend fun getMatches(
        @Query("session_id") sessionId: String,
        @Query("select") select: String = "*",
        @Query("order") order: String? = null,
        @Query("limit") limit: Int? = null
    ): List<SbMatch>

    @POST("auth/v1/token?grant_type=password")
    suspend fun loginWithPassword(
        @Body request: AuthLoginRequest
    ): AuthTokenResponse

    @POST("rest/v1/rpc/apply_live_broadcast_event")
    suspend fun applyLiveBroadcastEvent(
        @Body payload: RpcEventRequest
    ): ResponseBody
}
