package com.example.services.detection.pubg

import com.example.core.model.Player

/**
 * Registry holding official tournament slot-booking roster data.
 *
 * Maps (teamNumber, playerNumber) e.g., Team 3 Player 2 -> Player(id, inGameName, teamId, ...)
 */
object TournamentRoster {

    private val rosterMap = mutableMapOf<Pair<Int, Int>, Player>()

    fun registerPlayer(
        teamNumber: Int,
        playerNumber: Int,
        inGameName: String,
        realName: String? = null,
        teamName: String? = null,
        teamTag: String? = null
    ) {
        val player = Player(
            id = "t${teamNumber}_p${playerNumber}",
            inGameName = inGameName,
            realName = realName,
            teamId = "team_${teamNumber}",
            teamTag = teamTag ?: "T${teamNumber}",
            slotNumber = teamNumber,
            kills = 0,
            isAlive = true
        )
        rosterMap[Pair(teamNumber, playerNumber)] = player
    }

    fun resolvePlayer(teamNumber: Int, playerNumber: Int): Player? {
        return rosterMap[Pair(teamNumber, playerNumber)]
    }

    fun resolvePlayer(identity: PUBGTextNormalizer.TeamPlayerIdentity?): Player? {
        if (identity == null) return null
        return resolvePlayer(identity.teamNumber, identity.playerNumber)
    }

    fun resolvePlayerByName(rawPlayerName: String?): Player? {
        if (rawPlayerName.isNullOrBlank()) return null
        val cleanName = PUBGTextNormalizer.extractCleanDisplayName(rawPlayerName)
        val targetNorm = PUBGTextNormalizer.normalizePlayerName(cleanName)
        if (targetNorm.isBlank()) return null

        // Priority B: Exact ID match
        for ((_, player) in rosterMap) {
            if (player.id == rawPlayerName || player.id.equals(rawPlayerName, ignoreCase = true)) {
                return player
            }
        }

        // Priority C: Normalized name exact match
        for ((_, player) in rosterMap) {
            val playerNorm = PUBGTextNormalizer.normalizePlayerName(player.inGameName)
            if (playerNorm.isNotBlank() && playerNorm == targetNorm) {
                return player
            }
        }

        // Priority D: Fuzzy match (threshold >= 0.85f)
        var bestMatch: Player? = null
        var bestScore = 0.0f
        for ((_, player) in rosterMap) {
            val score = PUBGTextNormalizer.fuzzyMatchScore(player.inGameName, cleanName)
            if (score > bestScore) {
                bestScore = score
                bestMatch = player
            }
        }

        if (bestScore >= 0.85f) {
            return bestMatch
        }

        return null
    }

    data class RosterResolution(
        val player: Player?,
        val hasConflict: Boolean,
        val conflictReason: String? = null
    )

    /**
     * Resolves player with cross-checking between Team Number + Player Number and Player Name.
     * If both are present and disagree, flags a conflict.
     */
    fun resolvePlayerAdvancedWithConflictCheck(identity: PUBGTextNormalizer.TeamPlayerIdentity?, rawPlayerName: String?): RosterResolution {
        val playerByNum = resolvePlayer(identity)

        val nameCandidate = if (rawPlayerName != null && identity != null) {
            rawPlayerName.replace(identity.rawText, "").trim()
        } else {
            rawPlayerName
        }

        val playerByName = resolvePlayerByName(nameCandidate)

        if (identity != null && playerByNum != null && playerByName != null) {
            if (playerByNum.id != playerByName.id) {
                return RosterResolution(
                    player = null,
                    hasConflict = true,
                    conflictReason = "Conflict: Team ${identity.teamNumber} Player ${identity.playerNumber} (${playerByNum.inGameName}) conflicts with name '$nameCandidate' (${playerByName.inGameName})"
                )
            } else {
                return RosterResolution(player = playerByNum, hasConflict = false)
            }
        }

        if (identity != null) {
            return RosterResolution(player = playerByNum, hasConflict = false)
        }

        if (!nameCandidate.isNullOrBlank()) {
            return RosterResolution(player = playerByName, hasConflict = false)
        }

        return RosterResolution(player = null, hasConflict = false)
    }

    /**
     * Resolves player using priority:
     * A. Team Number + Player Number
     * B. Player UID/ID from roster when available
     * C. Normalized / Stylized player name exact match
     * D. Fuzzy / stylized name matching fallback (threshold >= 0.85f)
     */
    fun resolvePlayerAdvanced(identity: PUBGTextNormalizer.TeamPlayerIdentity?, rawPlayerName: String?): Player? {
        val res = resolvePlayerAdvancedWithConflictCheck(identity, rawPlayerName)
        if (res.hasConflict) return null
        return res.player
    }

    fun clearRoster() {
        rosterMap.clear()
    }

    fun isRosterLoaded(): Boolean = rosterMap.isNotEmpty()
}
