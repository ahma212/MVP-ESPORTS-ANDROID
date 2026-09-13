package com.example.services.supabase.api

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

/**
 * Flexible adapter to safely parse numbers that might arrive as Float/Double (e.g. 0.0)
 * into Int without crashing Moshi.
 */
class FlexibleIntJsonAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Int {
        return if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull<Unit>()
            0
        } else {
            reader.nextDouble().toInt()
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Int) {
        writer.value(value)
    }
}

@JsonClass(generateAdapter = true)
data class SbSession(
    val id: String,
    val status: String,
    @Json(name = "tournament_title") val tournamentTitle: String? = null,
    @Json(name = "current_match_id") val currentMatchId: String? = null,
    @Json(name = "current_match_number") val rawMatchNumber: Double? = null,
    @Json(name = "current_map") val currentMap: String? = null,
    @Json(name = "map") val map: String? = null,
    @Json(name = "current_match_type") val currentMatchType: String? = null,
    @Json(name = "match_type") val matchType: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
) {
    val effectiveMap: String? get() = currentMap ?: map
    val currentMatchNumber: Int? get() = rawMatchNumber?.toInt()
}

@JsonClass(generateAdapter = true)
data class SbMatch(
    val id: String,
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "match_number") val rawMatchNumber: Double? = null,
    val map: String? = null,
    val status: String? = null
) {
    val matchNumber: Int? get() = rawMatchNumber?.toInt()
}

@JsonClass(generateAdapter = true)
data class SbTeam(
    val id: String,
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "team_key") val teamKey: String? = null,
    @Json(name = "team_name") val teamName: String? = null,
    @Json(name = "current_match_kills") val rawKills: Double? = 0.0,
    @Json(name = "current_match_points") val rawPoints: Double? = 0.0,
    @Json(name = "current_alive_players") val rawAlive: Double? = 4.0,
    @Json(name = "current_match_placement") val rawPlacement: Double? = null,
    @Json(name = "placement_points") val rawPlacementPoints: Double? = 0.0,
    @Json(name = "rank") val rawRank: Double? = null,
    @Json(name = "is_eliminated") val isEliminated: Boolean = false
) {
    val currentMatchKills: Int get() = rawKills?.toInt() ?: 0
    val currentMatchPoints: Int get() = rawPoints?.toInt() ?: 0
    val currentAlivePlayers: Int get() = rawAlive?.toInt() ?: 4
    val currentMatchPlacement: Int? get() = rawPlacement?.toInt()
    val placementPoints: Int get() = rawPlacementPoints?.toInt() ?: 0
    val rank: Int? get() = rawRank?.toInt()
}

@JsonClass(generateAdapter = true)
data class SbPlayer(
    val id: String,
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "team_id") val teamId: String? = null,
    @Json(name = "player_name") val playerName: String = "",
    @Json(name = "player_uid") val playerUid: String? = null,
    @Json(name = "uid") val uid: String? = null,
    @Json(name = "is_alive") val isAlive: Boolean = true,
    @Json(name = "alive") val alive: Boolean = true,
    @Json(name = "current_match_kills") val rawKills: Double? = 0.0,
    @Json(name = "is_knocked") val isKnocked: Boolean = false
) {
    val effectiveUid: String? get() = playerUid ?: uid
    val effectiveIsAlive: Boolean get() = isAlive && alive
    val currentMatchKills: Int get() = rawKills?.toInt() ?: 0
}

@JsonClass(generateAdapter = true)
data class RpcEventRequest(
    val p_session_id: String,
    val p_broadcast_match_id: String?,
    val p_event_type: String,
    val p_source: String = "detector",
    val p_killer_player_id: String?,
    val p_victim_player_id: String?,
    val p_killer_team_id: String?,
    val p_victim_team_id: String?,
    val p_external_event_id: String,
    val p_detection_confidence: Double,
    val p_event_payload: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class AuthLoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class AuthTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String? = "bearer",
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null
)
