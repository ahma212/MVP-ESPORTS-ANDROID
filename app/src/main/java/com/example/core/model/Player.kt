package com.example.core.model

/**
 * Player identity model.
 */
data class Player(
    val id: String,
    val inGameName: String,
    val realName: String? = null,
    val teamId: String,
    val teamTag: String? = null,
    val slotNumber: Int? = null,
    val kills: Int = 0,
    val isAlive: Boolean = true
)

/**
 * Team identity model.
 */
data class Team(
    val id: String,
    val name: String,
    val tag: String,
    val slotNumber: Int,
    val logoUrl: String? = null,
    val totalKills: Int = 0,
    val placement: Int? = null,
    val totalPoints: Int = 0,
    val aliveCount: Int = 4
)

/**
 * Live tournament match session.
 */
data class MatchSession(
    val id: String,
    val tournamentName: String = "MVP ESPORTS PK CHAMPIONSHIP",
    val matchNumber: Int = 1,
    val totalMatches: Int = 0,
    val mapName: String = "Erangel",
    val status: String = "LIVE",
    val startTimeMs: Long = System.currentTimeMillis(),
    val totalTeams: Int = 16,
    val aliveTeams: Int = 16,
    val alivePlayers: Int = 64
)

/**
 * Live Leaderboard entry for broadcast overlay and scoring.
 */
data class LeaderboardEntry(
    val rank: Int,
    val teamId: String,
    val teamName: String,
    val teamTag: String,
    val slotNumber: Int,
    val killPoints: Int,
    val placementPoints: Int,
    val totalPoints: Int,
    val alivePlayers: Int
)
