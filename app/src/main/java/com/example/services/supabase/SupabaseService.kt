package com.example.services.supabase

import android.util.Log
import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.EsportsEventType
import com.example.core.model.LeaderboardEntry
import com.example.core.model.MatchSession
import com.example.services.supabase.api.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import java.util.UUID

class SupabaseService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : ISupabaseService {

    private val _connectionState = MutableStateFlow<SupabaseConnectionState>(SupabaseConnectionState.NotConnected)
    override val connectionState: StateFlow<SupabaseConnectionState> = _connectionState.asStateFlow()

    private val _liveEventsStream = MutableSharedFlow<EsportsDetectedEvent>(extraBufferCapacity = 64)
    override val liveEventsStream: SharedFlow<EsportsDetectedEvent> = _liveEventsStream.asSharedFlow()

    private val _liveLeaderboardStream = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    override val liveLeaderboardStream: StateFlow<List<LeaderboardEntry>> = _liveLeaderboardStream.asStateFlow()

    private val _currentSession = MutableStateFlow<SbSession?>(null)
    override val currentSession: StateFlow<SbSession?> = _currentSession.asStateFlow()

    private val _teamsCount = MutableStateFlow(0)
    override val teamsCount: StateFlow<Int> = _teamsCount.asStateFlow()

    private val _playersCount = MutableStateFlow(0)
    override val playersCount: StateFlow<Int> = _playersCount.asStateFlow()

    override val activeSessionId: String?
        get() = _currentSession.value?.id ?: currentActiveSessionId

    private var currentSupabaseUrl: String? = null
    private var currentAnonKey: String? = null
    private var api: SupabaseApi? = null
    private var realtimeClient: RealtimeClient? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    private var heartbeatJob: Job? = null
    
    private var currentActiveSessionId: String? = null
    private var activeMatchId: String? = null
    
    private var teamsState = emptyMap<String, SbTeam>()
    private var playersState = emptyMap<String, SbPlayer>()
    private var sessionMatches: List<SbMatch> = emptyList()

    private val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    companion object {
        const val SUPABASE_URL = "https://rsqakcncemlkscobizcr.supabase.co"
        const val SUPABASE_ANON_KEY = "sb_publishable_uo4Pa8vev48bV3KP75rr8A_G-_72OvB"
        const val FALLBACK_SESSION_ID = "f50bfd5c-1857-4c3b-953b-c7bce8bdd553"
    }



    override suspend fun connect(supabaseUrl: String, anonKey: String): Result<Unit> {
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            _connectionState.value = SupabaseConnectionState.NotConnected
            return Result.failure(IllegalArgumentException("Supabase URL and Anon Key are required."))
        }

        _connectionState.value = SupabaseConnectionState.Connecting
        currentSupabaseUrl = supabaseUrl
        currentAnonKey = anonKey

        val moshi = Moshi.Builder()
            .add(FlexibleIntJsonAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val authClient = httpClient.newBuilder()
            .addInterceptor(AuthInterceptor(anonKey) { null })
            .build()
            
        val retrofit = Retrofit.Builder()
            .baseUrl(supabaseUrl + (if (supabaseUrl.endsWith("/")) "" else "/"))
            .client(authClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            
        api = retrofit.create(SupabaseApi::class.java)

        return try {
            // Step 1: Load latest live_broadcast_sessions where status in ('live','ready') order by updated_at desc
            var sessions = try {
                api!!.getSessions(status = "in.(live,ready)", order = "updated_at.desc", limit = 1)
            } catch (e: Exception) {
                Log.w("Supabase", "Failed to query status in.(live,ready), trying fallback", e)
                emptyList()
            }
            
            // Step 2: Fallback to specific session id f50bfd5c-1857-4c3b-953b-c7bce8bdd553 if none found
            if (sessions.isEmpty()) {
                try {
                    sessions = api!!.getSessions(id = "eq.$FALLBACK_SESSION_ID")
                } catch (e: Exception) {
                    Log.w("Supabase", "Failed to query fallback session id $FALLBACK_SESSION_ID", e)
                }
            }
            
            if (sessions.isEmpty()) {
                throw IllegalStateException("No live or ready session found")
            }
            
            val session = sessions.first()
            loadSessionData(session)
            
            // Step 3: Connect Realtime on sessions, teams, players, events
            realtimeClient?.disconnect()
            realtimeClient = RealtimeClient(authClient, supabaseUrl, anonKey, session.id)
            realtimeClient?.connect()
            
            syncJob?.cancel()
            syncJob = scope.launch {
                realtimeClient?.updates?.collect { update ->
                    handleRealtimeUpdate(update)
                }
            }
            
            heartbeatJob?.cancel()
            heartbeatJob = scope.launch {
                while (isActive) {
                    delay(30000)
                    realtimeClient?.heartbeat()
                }
            }

            _connectionState.value = SupabaseConnectionState.Connected(
                projectUrl = supabaseUrl,
                activeSessionId = session.id
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("Supabase", "Connect failed", e)
            _connectionState.value = SupabaseConnectionState.SyncError("Failed to reach Supabase: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    private suspend fun loadSessionData(session: SbSession) {
        currentActiveSessionId = session.id
        activeMatchId = session.currentMatchId
        _currentSession.value = session
        
        val initialTeams = try { api!!.getTeams("eq.${session.id}") } catch (_: Exception) { emptyList() }
        val initialPlayers = try { api!!.getPlayers("eq.${session.id}") } catch (_: Exception) { emptyList() }
        sessionMatches = try { api!!.getMatches(sessionId = "eq.${session.id}", order = "created_at.desc", limit = 10) } catch (_: Exception) { emptyList() }
        
        teamsState = initialTeams.associateBy { it.id }
        playersState = initialPlayers.associateBy { it.id }
        
        _teamsCount.value = teamsState.size
        _playersCount.value = playersState.size
        
        recalculateLeaderboard()
        Log.d("Supabase", "Loaded session ${session.id}: ${teamsState.size} teams, ${playersState.size} players, ${sessionMatches.size} matches")
    }

    private suspend fun refreshTeamsAndPlayers(sessionId: String) {
        try {
            val initialTeams = api?.getTeams("eq.$sessionId") ?: emptyList()
            val initialPlayers = api?.getPlayers("eq.$sessionId") ?: emptyList()
            sessionMatches = try { api?.getMatches(sessionId = "eq.$sessionId", order = "created_at.desc", limit = 10) ?: emptyList() } catch (_: Exception) { emptyList() }
            
            teamsState = initialTeams.associateBy { it.id }
            playersState = initialPlayers.associateBy { it.id }
            _teamsCount.value = teamsState.size
            _playersCount.value = playersState.size
            recalculateLeaderboard()
            Log.d("Supabase", "Refreshed teams & players for session $sessionId: ${teamsState.size} teams, ${playersState.size} players")
        } catch (e: Exception) {
            Log.w("Supabase", "Failed to refresh teams/players for session $sessionId", e)
        }
    }

    suspend fun attachToSession(sessionId: String): Result<Unit> {
        return try {
            val sessionList = api?.getSessions(id = "eq.$sessionId") ?: emptyList()
            val session = sessionList.firstOrNull() ?: SbSession(id = sessionId, status = "live")
            loadSessionData(session)
            realtimeClient?.updateSessionId(sessionId)
            _connectionState.value = SupabaseConnectionState.Connected(
                projectUrl = currentSupabaseUrl ?: "",
                activeSessionId = sessionId
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("Supabase", "Failed to attach to session $sessionId", e)
            Result.failure(e)
        }
    }
    
    private fun handleRealtimeUpdate(update: JSONObject) {
        val payload = update.optJSONObject("payload") ?: update
        val data = payload.optJSONObject("data")

        // If table is missing at top level, read payload.data.table
        val table = if (payload.has("table")) {
            payload.optString("table")
        } else {
            data?.optString("table") ?: ""
        }

        if (table.isBlank()) return

        // Parse record from payload, payload.data, record, and new
        val record = payload.optJSONObject("record")
            ?: payload.optJSONObject("new")
            ?: data?.optJSONObject("record")
            ?: data?.optJSONObject("new")
            ?: return

        when (table) {
            "live_broadcast_sessions" -> {
                val sessionId = record.optString("id")
                val status = record.optString("status")
                Log.d("Supabase", "Realtime session update: id=$sessionId status=$status")
                if (status == "live" || status == "ready") {
                    scope.launch {
                        if (sessionId != currentActiveSessionId) {
                            Log.i("Supabase", "Admin switched/started broadcast session $sessionId. Attaching & reloading...")
                            attachToSession(sessionId)
                        } else {
                            val updatedSession = SbSession(
                                id = sessionId,
                                status = status,
                                tournamentTitle = record.optString("tournament_title", _currentSession.value?.tournamentTitle),
                                currentMatchId = record.optString("current_match_id", activeMatchId),
                                rawMatchNumber = if (record.has("current_match_number")) record.optDouble("current_match_number", 1.0) else _currentSession.value?.rawMatchNumber,
                                currentMap = record.optString("current_map", _currentSession.value?.currentMap),
                                map = record.optString("map", _currentSession.value?.map),
                                currentMatchType = record.optString("current_match_type", _currentSession.value?.currentMatchType),
                                matchType = record.optString("match_type", _currentSession.value?.matchType),
                                updatedAt = record.optString("updated_at", _currentSession.value?.updatedAt)
                            )
                            _currentSession.value = updatedSession
                            activeMatchId = updatedSession.currentMatchId
                            // Reload teams/players on live session change
                            refreshTeamsAndPlayers(sessionId)
                        }
                    }
                }
            }
            "live_broadcast_teams" -> {
                val teamSessionId = record.optString("session_id")
                // Filter updates by active session_id
                if (currentActiveSessionId != null && teamSessionId.isNotBlank() && teamSessionId != currentActiveSessionId) {
                    return
                }
                val teamId = record.optString("id")
                val existing = teamsState[teamId]
                val updatedTeam = SbTeam(
                    id = teamId,
                    sessionId = if (teamSessionId.isNotBlank()) teamSessionId else (existing?.sessionId ?: currentActiveSessionId ?: ""),
                    teamKey = record.optString("team_key", existing?.teamKey),
                    teamName = record.optString("team_name", existing?.teamName),
                    rawKills = record.optDouble("current_match_kills", existing?.rawKills ?: 0.0),
                    rawPoints = record.optDouble("current_match_points", existing?.rawPoints ?: 0.0),
                    rawAlive = record.optDouble("current_alive_players", existing?.rawAlive ?: 4.0),
                    rawPlacement = if (record.has("current_match_placement") && !record.isNull("current_match_placement")) record.optDouble("current_match_placement") else existing?.rawPlacement,
                    rawPlacementPoints = record.optDouble("placement_points", existing?.rawPlacementPoints ?: 0.0),
                    rawRank = if (record.has("rank") && !record.isNull("rank")) record.optDouble("rank") else existing?.rawRank,
                    isEliminated = record.optBoolean("is_eliminated", existing?.isEliminated ?: false)
                )
                teamsState = teamsState.toMutableMap().apply { put(teamId, updatedTeam) }
                _teamsCount.value = teamsState.size
                recalculateLeaderboard()
            }
            "live_broadcast_players" -> {
                val playerSessionId = record.optString("session_id")
                // Filter updates by active session_id
                if (currentActiveSessionId != null && playerSessionId.isNotBlank() && playerSessionId != currentActiveSessionId) {
                    return
                }
                val playerId = record.optString("id")
                val existing = playersState[playerId]
                val updatedPlayer = SbPlayer(
                    id = playerId,
                    sessionId = if (playerSessionId.isNotBlank()) playerSessionId else (existing?.sessionId ?: currentActiveSessionId ?: ""),
                    teamId = record.optString("team_id", existing?.teamId),
                    playerName = record.optString("player_name", existing?.playerName ?: ""),
                    playerUid = if (record.has("player_uid")) record.optString("player_uid") else record.optString("uid", existing?.playerUid ?: existing?.uid),
                    uid = if (record.has("uid")) record.optString("uid") else record.optString("player_uid", existing?.uid),
                    isAlive = if (record.has("is_alive")) record.optBoolean("is_alive", true) else record.optBoolean("alive", existing?.isAlive ?: true),
                    alive = if (record.has("alive")) record.optBoolean("alive", true) else record.optBoolean("is_alive", existing?.alive ?: true),
                    rawKills = record.optDouble("current_match_kills", existing?.rawKills ?: 0.0),
                    isKnocked = record.optBoolean("is_knocked", existing?.isKnocked ?: false)
                )
                playersState = playersState.toMutableMap().apply { put(playerId, updatedPlayer) }
                _playersCount.value = playersState.size
            }
            "live_broadcast_matches" -> {
                val matchSessionId = record.optString("session_id")
                if (currentActiveSessionId == null || matchSessionId == currentActiveSessionId) {
                    scope.launch {
                        currentActiveSessionId?.let { refreshTeamsAndPlayers(it) }
                    }
                }
            }
        }
    }
    
    private fun recalculateLeaderboard() {
        val entries = teamsState.values.map { team ->
            val num = team.teamKey?.removePrefix("team-")?.toIntOrNull() ?: 0
            LeaderboardEntry(
                rank = 0,
                teamId = team.id,
                teamName = team.teamName ?: "Team $num",
                teamTag = "T$num",
                slotNumber = num,
                killPoints = team.currentMatchKills,
                placementPoints = team.placementPoints,
                totalPoints = team.currentMatchPoints,
                alivePlayers = team.currentAlivePlayers
            )
        }.sortedWith(compareByDescending<LeaderboardEntry> { it.totalPoints }.thenByDescending { it.killPoints })
        
        val ranked = entries.mapIndexed { index, entry -> entry.copy(rank = index + 1) }
        _liveLeaderboardStream.value = ranked
    }

    override suspend fun disconnect() {
        currentSupabaseUrl = null
        currentAnonKey = null
        syncJob?.cancel()
        heartbeatJob?.cancel()
        realtimeClient?.disconnect()
        _connectionState.value = SupabaseConnectionState.NotConnected
    }

    override suspend fun recordDetectedEvent(event: EsportsDetectedEvent): Result<Unit> {
        if (_connectionState.value !is SupabaseConnectionState.Connected) {
            return Result.failure(IllegalStateException("Supabase is not connected. Event persistence skipped."))
        }
        
        val sessionId = activeSessionId ?: return Result.failure(IllegalStateException("No active session"))
        
        val killerPlayer = event.killerPlayerName?.let { resolvePlayer(it) }
        val victimPlayer = event.victimPlayerName?.let { resolvePlayer(it) }

        val killerTeamId = resolveTeamId(event.killerPlayerName, killerPlayer)
        val victimTeamId = resolveTeamId(event.victimPlayerName, victimPlayer)

        // Event type mapping: KILL->kill, KNOCK->knock, ELIMINATION->elimination, REVIVE->player_revive, PLACEMENT->placement
        val mappedEventType = when (event.eventType) {
            EsportsEventType.KILL -> "kill"
            EsportsEventType.KNOCK -> "knock"
            EsportsEventType.ELIMINATION -> "elimination"
            EsportsEventType.REVIVE -> "player_revive"
            EsportsEventType.PLACEMENT -> "placement"
            else -> "kill"
        }

        // p_broadcast_match_id: only send if it is a valid uuid of live_broadcast_matches for this session. Otherwise null.
        val validMatchId = when {
            activeMatchId != null && uuidRegex.matches(activeMatchId!!) && sessionMatches.any { it.id.equals(activeMatchId, ignoreCase = true) } -> {
                activeMatchId
            }
            sessionMatches.isNotEmpty() -> {
                val candidate = sessionMatches.firstOrNull { it.status == "live" && uuidRegex.matches(it.id) }
                    ?: sessionMatches.firstOrNull { uuidRegex.matches(it.id) }
                candidate?.id
            }
            else -> null
        }

        // Stable external event ID hash: sessionId + eventType + killer + victim + 2-second timestamp bucket
        val timeBucket = (event.timestampMs / 2000L) * 2000L
        val kKey = killerPlayer?.id ?: (event.killerPlayerName?.trim()?.lowercase() ?: "")
        val vKey = victimPlayer?.id ?: (event.victimPlayerName?.trim()?.lowercase() ?: "")
        val rawHashInput = "$sessionId:$mappedEventType:$kKey:$vKey:$timeBucket"
        val stableExternalEventId = UUID.nameUUIDFromBytes(rawHashInput.toByteArray(Charsets.UTF_8)).toString()
        
        val req = RpcEventRequest(
            p_session_id = sessionId,
            p_broadcast_match_id = validMatchId,
            p_event_type = mappedEventType,
            p_source = "detector",
            p_killer_player_id = killerPlayer?.id,
            p_victim_player_id = victimPlayer?.id,
            p_killer_team_id = killerTeamId,
            p_victim_team_id = victimTeamId,
            p_external_event_id = stableExternalEventId,
            p_detection_confidence = event.confidence.toDouble(),
            p_event_payload = mapOf(
                "killer" to (event.killerPlayerName ?: ""),
                "victim" to (event.victimPlayerName ?: "")
            )
        )
        
        return try {
            api?.applyLiveBroadcastEvent(req)
            _liveEventsStream.tryEmit(event)
            Result.success(Unit)
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) {
                Log.w("Supabase", "RPC unauthorized (${e.code()}).", e)
                // Keep Connected for READ: Do NOT transition connectionState to SyncError
                return Result.failure(e)
            }
            Log.e("Supabase", "RPC HTTP error ${e.code()}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("Supabase", "RPC failed", e)
            Result.failure(e)
        }
    }

    override suspend fun confirmAdminReviewEvent(eventId: String, confirmedBy: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun rejectAdminReviewEvent(eventId: String, reason: String, rejectedBy: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun fetchAdminReviewQueue(sessionId: String): Result<List<EsportsDetectedEvent>> {
        return Result.success(emptyList())
    }

    override suspend fun getOrCreateMatchSession(
        tournamentName: String,
        matchNumber: Int
    ): Result<MatchSession> {
        return Result.success(
            MatchSession(
                id = activeSessionId ?: "session_${System.currentTimeMillis()}",
                tournamentName = tournamentName,
                matchNumber = matchNumber
            )
        )
    }
    
    /**
     * Resolves a player by:
     * 1. player_uid / uid
     * 2. player_name (exact case-insensitive)
     * 3. player_name (normalized alphanumeric)
     * 4. team_key number + similar IGN
     * 5. substring overlap
     */
    fun resolvePlayer(rawName: String?): SbPlayer? {
        if (rawName.isNullOrBlank()) return null
        val trimmed = rawName.trim()
        val lower = trimmed.lowercase()

        // 1. Match by player_uid or uid
        playersState.values.firstOrNull {
            val uid = (it.playerUid ?: it.uid)?.trim()?.lowercase()
            uid != null && uid == lower
        }?.let { return it }

        // 2. Exact match by player_name (case-insensitive)
        playersState.values.firstOrNull {
            it.playerName.trim().equals(trimmed, ignoreCase = true)
        }?.let { return it }

        // 3. Normalized alphanumeric match (removes special chars, clan tags)
        val normTarget = lower.replace(Regex("[^a-z0-9]"), "")
        if (normTarget.length >= 2) {
            playersState.values.firstOrNull {
                val normPlayer = it.playerName.lowercase().replace(Regex("[^a-z0-9]"), "")
                normPlayer == normTarget
            }?.let { return it }
        }

        // 4. Team key number + similar IGN
        val teamNumberMatch = Regex("""(?:team\s*|slot\s*|t|\b)(\d{1,2})\b""", RegexOption.IGNORE_CASE).find(rawName)
        val slotNum = teamNumberMatch?.groupValues?.get(1)?.toIntOrNull()
        if (slotNum != null && slotNum in 1..25) {
            val matchingTeam = teamsState.values.firstOrNull {
                it.teamKey?.removePrefix("team-")?.toIntOrNull() == slotNum
            }
            if (matchingTeam != null) {
                val teamPlayers = playersState.values.filter { it.teamId == matchingTeam.id }
                if (teamPlayers.isNotEmpty()) {
                    val bestInTeam = teamPlayers.firstOrNull { p ->
                        val normP = p.playerName.lowercase().replace(Regex("[^a-z0-9]"), "")
                        normTarget.contains(normP) || normP.contains(normTarget)
                    }
                    if (bestInTeam != null) {
                        return bestInTeam
                    }
                }
            }
        }

        // 5. Substring overlap across all players if length >= 3
        if (normTarget.length >= 3) {
            playersState.values.firstOrNull {
                val normP = it.playerName.lowercase().replace(Regex("[^a-z0-9]"), "")
                normP.length >= 3 && (normTarget.contains(normP) || normP.contains(normTarget))
            }?.let { return it }
        }

        return null
    }

    private fun resolveTeamId(rawName: String?, player: SbPlayer?): String? {
        if (player?.teamId != null) return player.teamId
        if (rawName.isNullOrBlank()) return null
        val teamNumberMatch = Regex("""(?:team\s*|slot\s*|t|\b)(\d{1,2})\b""", RegexOption.IGNORE_CASE).find(rawName)
        val slotNum = teamNumberMatch?.groupValues?.get(1)?.toIntOrNull()
        if (slotNum != null && slotNum in 1..25) {
            return teamsState.values.firstOrNull {
                it.teamKey?.removePrefix("team-")?.toIntOrNull() == slotNum
            }?.id
        }
        return null
    }
}
