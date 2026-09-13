package com.example.core.model

data class BroadcastSessionState(
    val sessionId: String = "",
    val tournamentId: String? = null,
    val tournamentTitle: String = "MVP ESPORTS PK",
    val status: String = "WAITING",
    val currentMatchId: String = "",
    val currentMatchNumber: Int = 1,
    val totalMatches: Int = 0,
    val map: String = "Erangel",
    val matchType: String = "Squad",
    val startedAt: Long = 0L,
    val elapsedTime: Long = 0L
)

data class TeamLiveState(
    val teamNumber: Int,
    val teamName: String,
    val logoReference: String? = null,
    val currentMatchKills: Int = 0,
    val currentMatchPoints: Int = 0,
    val currentAlivePlayers: Int = 4,
    val tournamentKills: Int = 0,
    val tournamentPoints: Int = 0,
    val rank: Int = 0,
    val players: List<PlayerLiveState> = emptyList(),
    val currentMatchPlacement: Int? = null,
    val placementPoints: Int = 0,
    val isEliminated: Boolean = false
)

data class PlayerLiveState(
    val playerId: String? = null,
    val playerName: String,
    val playerUid: String? = null,
    val teamNumber: Int,
    val currentMatchKills: Int = 0,
    val tournamentKills: Int = 0,
    val alive: Boolean = true,
    val knocked: Boolean = false,
    val eliminated: Boolean = false
)
