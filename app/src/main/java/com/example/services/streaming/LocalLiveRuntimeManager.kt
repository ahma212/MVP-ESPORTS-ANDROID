package com.example.services.streaming

import com.example.core.model.BroadcastSessionState
import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.EsportsEventType
import com.example.core.model.PlayerLiveState
import com.example.core.model.TeamLiveState
import com.example.core.rules.ScoringRules
import com.example.services.detection.pubg.PUBGTextNormalizer
import com.example.services.detection.pubg.TournamentRoster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Collections

/**
 * Authoritative Live Tournament Runtime State Manager (Phase 5 / K5).
 * Connects K4-A and K4-B detection decisions directly to live match scores,
 * cumulative tournament totals, standings, VIP milestones, and broadcast overlays.
 */
object LocalLiveRuntimeManager {
    private val _broadcastState = MutableStateFlow(BroadcastSessionState())
    val broadcastState: StateFlow<BroadcastSessionState> = _broadcastState.asStateFlow()

    private val _teamsState = MutableStateFlow<List<TeamLiveState>>(emptyList())
    val teamsState: StateFlow<List<TeamLiveState>> = _teamsState.asStateFlow()

    // Deduplication tracking: Ensures no K4 event can update the scoreboard twice
    private val processedEventIds = Collections.synchronizedSet(HashSet<String>())

    fun updateBroadcastSession(updater: (BroadcastSessionState) -> BroadcastSessionState) {
        _broadcastState.update(updater)
    }

    fun isEventProcessed(eventId: String): Boolean = processedEventIds.contains(eventId)

    fun resetProcessedEvents() {
        processedEventIds.clear()
    }

    fun isRosterLoaded(): Boolean = _teamsState.value.isNotEmpty()

    /**
     * Initializes the live state from a real tournament roster, strictly capping at 16 teams.
     * Automatically registers real players into TournamentRoster registry.
     */
    fun initializeFromRoster(teams: List<TeamLiveState>) {
        val cappedTeams = teams.take(16).mapIndexed { index, team ->
            val aliveCount = if (team.players.isNotEmpty()) {
                team.players.count { !it.eliminated }
            } else {
                team.currentAlivePlayers
            }
            team.copy(
                rank = index + 1,
                currentAlivePlayers = aliveCount
            )
        }
        _teamsState.value = cappedTeams

        // Synchronize with TournamentRoster registry
        TournamentRoster.clearRoster()
        cappedTeams.forEach { team ->
            team.players.forEachIndexed { pIndex, player ->
                TournamentRoster.registerPlayer(
                    teamNumber = team.teamNumber,
                    playerNumber = pIndex + 1,
                    inGameName = player.playerName,
                    teamName = team.teamName,
                    teamTag = "T${team.teamNumber}"
                )
            }
        }
    }

    /**
     * Starts the next tournament match:
     * - Increments currentMatchNumber
     * - Resets current-match player kills and alive states (alive=true, knocked=false, eliminated=false)
     * - Resets current-match team kills and current-match points
     * - PRESERVES cumulative tournament kills and tournament points across all teams and players!
     */
    fun startNextMatch(mapName: String = "Erangel"): Int {
        var nextMatchNum = 1
        _broadcastState.update { current ->
            nextMatchNum = current.currentMatchNumber + 1
            current.copy(
                currentMatchNumber = nextMatchNum,
                map = mapName,
                startedAt = System.currentTimeMillis(),
                elapsedTime = 0L
            )
        }

        _teamsState.update { currentTeams ->
            val resetTeams = currentTeams.map { team ->
                val resetPlayers = team.players.map { player ->
                    player.copy(
                        currentMatchKills = 0,
                        // tournamentKills PRESERVED
                        alive = true,
                        knocked = false,
                        eliminated = false
                    )
                }
                team.copy(
                    currentMatchKills = 0,
                    currentMatchPoints = 0,
                    currentMatchPlacement = null,
                    placementPoints = 0,
                    // tournamentKills and tournamentPoints PRESERVED
                    currentAlivePlayers = resetPlayers.count { !it.eliminated },
                    players = resetPlayers
                )
            }
            recalculateRanks(resetTeams)
        }

        return nextMatchNum
    }

    /**
     * Records a valid team placement result (1st - 16th), calculates placement points,
     * updates current match points and tournament cumulative points, and updates standing ranks.
     * Enforces duplicate protection so the same match placement is not awarded twice.
     */
    fun recordTeamPlacement(teamNumber: Int, placement: Int, eventId: String = ""): Boolean {
        if (placement <= 0 || placement > 16) return false
        if (eventId.isNotBlank() && !processedEventIds.add(eventId)) {
            return false
        }

        val placementPoints = ScoringRules.getPlacementPoints(placement)
        var updated = false
        _teamsState.update { currentTeams ->
            val newTeams = currentTeams.map { team ->
                if (team.teamNumber == teamNumber) {
                    if (team.currentMatchPlacement != null) {
                        team // Duplicate protection: already awarded placement for this match
                    } else {
                        updated = true
                        team.copy(
                            currentMatchPlacement = placement,
                            placementPoints = placementPoints,
                            currentMatchPoints = team.currentMatchPoints + placementPoints,
                            tournamentPoints = team.tournamentPoints + placementPoints
                        )
                    }
                } else {
                    team
                }
            }
            if (updated) {
                recalculateRanks(newTeams)
            } else {
                currentTeams
            }
        }
        return updated
    }

    /**
     * Dynamically sets match format (Solo, Duo, Squad) and adjusts team player slots accordingly
     * for already loaded real tournament teams without generating fake players.
     */
    fun setMatchFormat(format: String) {
        _broadcastState.update { it.copy(matchType = format) }
        val playersPerTeam = when (format.lowercase()) {
            "solo" -> 1
            "duo" -> 2
            else -> 4
        }
        
        _teamsState.update { currentTeams ->
            currentTeams.map { team ->
                val adjustedPlayers = mutableListOf<PlayerLiveState>()
                for (pNum in 1..playersPerTeam) {
                    val rosterP = TournamentRoster.resolvePlayer(team.teamNumber, pNum)
                    if (rosterP != null) {
                        val existing = team.players.find { it.playerId == rosterP.id || it.playerName == rosterP.inGameName }
                        adjustedPlayers.add(existing ?: PlayerLiveState(
                            playerId = rosterP.id,
                            playerName = rosterP.inGameName,
                            teamNumber = team.teamNumber,
                            alive = rosterP.isAlive,
                            knocked = false,
                            eliminated = !rosterP.isAlive
                        ))
                    } else if (pNum <= team.players.size) {
                        adjustedPlayers.add(team.players[pNum - 1])
                    }
                }
                val aliveCount = adjustedPlayers.count { !it.eliminated }
                team.copy(players = adjustedPlayers, currentAlivePlayers = aliveCount)
            }
        }
    }

    /**
     * Resets entire session and tournament totals to initial state without inserting fake teams.
     */
    fun resetSession() {
        processedEventIds.clear()
        _broadcastState.value = BroadcastSessionState()
        _teamsState.update { currentTeams ->
            currentTeams.map { team ->
                val resetPlayers = team.players.map { player ->
                    player.copy(
                        currentMatchKills = 0,
                        tournamentKills = 0,
                        alive = true,
                        knocked = false,
                        eliminated = false
                    )
                }
                team.copy(
                    currentMatchKills = 0,
                    currentMatchPoints = 0,
                    currentMatchPlacement = null,
                    placementPoints = 0,
                    tournamentKills = 0,
                    tournamentPoints = 0,
                    currentAlivePlayers = resetPlayers.count { !it.eliminated },
                    players = resetPlayers
                )
            }
        }
    }

    /**
     * Processes a valid K4-B EsportsDetectedEvent to update authoritative live runtime stats.
     *
     * Rules enforced:
     * 1. Duplicate event protection: Event ID is checked against processedEventIds.
     * 2. ENEMY_KILL: Valid killer awarded 1 match kill, 1 tournament kill, and POINTS_PER_KILL points.
     *    Victim eliminated (dead=true, eliminated=true, knocked=false).
     * 3. SELF_KILL: Victim eliminated. 0 killer credit, 0 points awarded, pointAwardedTo=NONE.
     * 4. TEAM_KILL: Victim eliminated. 0 killer credit, 0 points awarded, pointAwardedTo=NONE.
     * 5. ENV_DEATH / ELIMINATION: Victim eliminated. 0 killer credit, 0 points awarded, pointAwardedTo=NONE.
     * 6. KNOCK: Victim knocked (alive=true, knocked=true, eliminated=false). Victim remains alive, alive player count unchanged. 0 points awarded.
     * 7. REVIVE: Victim restored (alive=true, knocked=false, eliminated=false). 0 points awarded.
     * 8. Uses K4-B creditedKillCount semantics.
     */
    fun processDetectedEvent(event: EsportsDetectedEvent) {
        val eventId = event.id.ifBlank { "${event.timestampMs}_${event.killerPlayerName}_${event.victimPlayerName}_${event.eventType}" }
        if (!processedEventIds.add(eventId)) {
            // Already processed this exact event ID — suppress duplicate to protect scoreboard
            return
        }

        val metadata = event.metadata

        val isPlacementEvent = event.eventType == EsportsEventType.PLACEMENT ||
                metadata["event_type"] == "PLACEMENT" ||
                metadata.containsKey("placement")

        if (isPlacementEvent) {
            val placement = metadata["placement"]?.toIntOrNull()
                ?: event.reviewNote?.toIntOrNull()
                ?: 0
            if (placement > 0) {
                val teamNum = metadata["team_number"]?.toIntOrNull()
                    ?: event.killerTeamTag?.toIntOrNull()
                    ?: PUBGTextNormalizer.extractTeamPlayerIdentity(event.killerPlayerName ?: "")?.teamNumber
                if (teamNum != null) {
                    recordTeamPlacement(teamNum, placement, "")
                    return
                }
            }
        }

        val decisionTypeStr = metadata["decision_type"]

        val killerRawName = event.killerPlayerName
        val victimRawName = event.victimPlayerName

        val killerIdentity = PUBGTextNormalizer.extractTeamPlayerIdentity(killerRawName ?: "")
        val victimIdentity = PUBGTextNormalizer.extractTeamPlayerIdentity(victimRawName ?: "")

        val killerTeamNum = killerIdentity?.teamNumber ?: event.killerTeamTag?.toIntOrNull()
        val victimTeamNum = victimIdentity?.teamNumber ?: event.victimTeamTag?.toIntOrNull()

        // Determine classified semantic decision type
        val isKnock = decisionTypeStr == "KNOCK" || event.eventType == EsportsEventType.KNOCK
        val isRevive = decisionTypeStr == "REVIVE" || event.eventType == EsportsEventType.REVIVE
        val isSelfKill = decisionTypeStr == "SELF_KILL" || (killerRawName != null && victimRawName != null && PUBGTextNormalizer.isSamePlayerName(killerRawName, victimRawName))
        val isTeamKill = decisionTypeStr == "TEAM_KILL" || (killerTeamNum != null && victimTeamNum != null && killerTeamNum == victimTeamNum && !isSelfKill)
        val isEnvDeath = decisionTypeStr == "ENV_DEATH" || (killerRawName == null || PUBGTextNormalizer.isSystemText(killerRawName)) && event.eventType != EsportsEventType.KILL

        val isElimination = !isKnock && !isRevive // ENEMY_KILL, SELF_KILL, TEAM_KILL, ENV_DEATH, ELIMINATION

        val creditedKillCount = metadata["credited_kill_count"]?.toIntOrNull()
            ?: if (metadata["kill_credit_allowed"] == "true" || (event.eventType == EsportsEventType.KILL && !isSelfKill && !isTeamKill && !isEnvDeath)) 1 else 0
        val killCreditAllowed = (metadata["kill_credit_allowed"] == "true") || (creditedKillCount > 0) || (event.eventType == EsportsEventType.KILL && !isSelfKill && !isTeamKill && !isEnvDeath)

        // Strict kill credit verification: ONLY allow kill credit if ENEMY_KILL and creditedKillCount > 0
        val awardKillCredit = killCreditAllowed && !isKnock && !isRevive && !isSelfKill && !isTeamKill && !isEnvDeath && (creditedKillCount > 0)

        _teamsState.update { currentTeams ->
            // First find target killer and victim teams
            var killerTeamFound: TeamLiveState? = null
            var victimTeamFound: TeamLiveState? = null

            for (team in currentTeams) {
                if (matchesTeam(team, victimTeamNum, event.victimTeamTag, victimRawName, victimIdentity)) {
                    victimTeamFound = team
                }
                if (matchesTeam(team, killerTeamNum, event.killerTeamTag, killerRawName, killerIdentity)) {
                    killerTeamFound = team
                }
            }

            val newTeams = currentTeams.map { team ->
                var updatedTeam = team

                // 1. Process Victim (Knock, Elimination, or Revive)
                val isVictimTeam = (victimTeamFound != null && team.teamNumber == victimTeamFound.teamNumber) ||
                        matchesTeam(team, victimTeamNum, event.victimTeamTag, victimRawName, victimIdentity)

                if (isVictimTeam) {
                    val newPlayers = team.players.mapIndexed { pIndex, player ->
                        if (matchesPlayer(player, pIndex, victimRawName, victimIdentity)) {
                            when {
                                isKnock -> player.copy(knocked = true, alive = true, eliminated = false)
                                isRevive -> player.copy(alive = true, knocked = false, eliminated = false)
                                isElimination -> player.copy(alive = false, knocked = false, eliminated = true)
                                else -> player
                            }
                        } else {
                            player
                        }
                    }
                    val newlyEliminated = newPlayers.count { it.eliminated } - team.players.count { it.eliminated }
                    val aliveCount = if (team.players.isEmpty()) {
                        if (isElimination) (team.currentAlivePlayers - 1).coerceAtLeast(0) else team.currentAlivePlayers
                    } else if (newlyEliminated > 0) {
                        (team.currentAlivePlayers - newlyEliminated).coerceAtLeast(0)
                    } else if (isElimination && team.players.none { matchesPlayer(it, 0, victimRawName, victimIdentity) }) {
                        // If player wasn't specifically identified in players list, still decrement team alive count
                        (team.currentAlivePlayers - 1).coerceAtLeast(0)
                    } else {
                        newPlayers.count { !it.eliminated }.coerceAtMost(team.currentAlivePlayers)
                    }
                    val isTeamEliminated = (aliveCount <= 0)
                    updatedTeam = updatedTeam.copy(
                        players = newPlayers,
                        currentAlivePlayers = aliveCount,
                        isEliminated = isTeamEliminated
                    )
                }

                // 2. Process Killer (Points/Kills for valid Enemy Kill ONLY)
                val isKillerTeam = (killerTeamFound != null && team.teamNumber == killerTeamFound.teamNumber) ||
                        matchesTeam(team, killerTeamNum, event.killerTeamTag, killerRawName, killerIdentity)

                if (isKillerTeam && awardKillCredit) {
                    val newPlayers = updatedTeam.players.mapIndexed { pIndex, player ->
                        if (matchesPlayer(player, pIndex, killerRawName, killerIdentity)) {
                            player.copy(
                                currentMatchKills = player.currentMatchKills + 1,
                                tournamentKills = player.tournamentKills + 1
                            )
                        } else {
                            player
                        }
                    }
                    updatedTeam = updatedTeam.copy(
                        players = newPlayers,
                        currentMatchKills = updatedTeam.currentMatchKills + 1,
                        currentMatchPoints = updatedTeam.currentMatchPoints + ScoringRules.POINTS_PER_KILL,
                        tournamentKills = updatedTeam.tournamentKills + 1,
                        tournamentPoints = updatedTeam.tournamentPoints + ScoringRules.POINTS_PER_KILL
                    )
                }

                updatedTeam
            }

            recalculateRanks(newTeams)
        }
    }

    private fun recalculateRanks(teams: List<TeamLiveState>): List<TeamLiveState> {
        val sortedTeams = teams.sortedWith(
            compareByDescending<TeamLiveState> { it.tournamentPoints }
                .thenByDescending { it.tournamentKills }
                .thenBy { it.teamNumber }
        )
        return sortedTeams.mapIndexed { index, t -> t.copy(rank = index + 1) }
    }

    private fun matchesTeam(
        team: TeamLiveState,
        teamNum: Int?,
        teamTag: String?,
        playerName: String?,
        identity: PUBGTextNormalizer.TeamPlayerIdentity?
    ): Boolean {
        if (teamNum != null && team.teamNumber == teamNum) return true
        if (identity != null && team.teamNumber == identity.teamNumber) return true
        if (!teamTag.isNullOrBlank()) {
            val upperTag = teamTag.uppercase()
            val upperName = team.teamName.uppercase()
            if (upperName.contains(upperTag) || upperTag.contains(upperName)) return true
            if (team.teamNumber.toString() == teamTag || "T${team.teamNumber}".equals(teamTag, ignoreCase = true) || "TEAM_${team.teamNumber}".equals(teamTag, ignoreCase = true)) return true
            // Also check standard esports tag acronyms (e.g. NV -> NOVA ESPORTS, A7 -> ALPHA 7, 4AM -> 4 ANGRY MEN, BTR -> BIGETRON, STE -> STALWART)
            if (matchesCommonTeamTag(team.teamName, upperTag)) return true
        }
        if (!playerName.isNullOrBlank()) {
            val norm = PUBGTextNormalizer.normalizePlayerName(playerName)
            if (team.players.any { PUBGTextNormalizer.normalizePlayerName(it.playerName) == norm }) return true
        }
        return false
    }

    private fun matchesCommonTeamTag(teamName: String, tag: String): Boolean {
        val upperName = teamName.uppercase()
        val upperTag = tag.uppercase()
        return when (upperTag) {
            "NV", "NOVA" -> upperName.contains("NOVA")
            "A7", "ALPHA7" -> upperName.contains("ALPHA")
            "STE", "STALWART" -> upperName.contains("STALWART")
            "VPE", "VAMPIRE" -> upperName.contains("VAMPIRE")
            "FLC", "FALCONS" -> upperName.contains("FALCONS")
            "REGN", "REJECT" -> upperName.contains("REGN") || upperName.contains("REJECT")
            "DRS" -> upperName.contains("DRS")
            "IHC" -> upperName.contains("IHC")
            "4AM" -> upperName.contains("4 ANGRY") || upperName.contains("4AM")
            "GEN", "GENG" -> upperName.contains("GEN.G") || upperName.contains("GENG")
            "BRU", "BURIRAM" -> upperName.contains("BURIRAM")
            "BTR", "BIGETRON" -> upperName.contains("BIGETRON")
            "NAVI" -> upperName.contains("NAVI")
            "ZEUS" -> upperName.contains("ZEUS")
            "TM", "TWISTED" -> upperName.contains("TWISTED")
            "MVP" -> upperName.contains("MVP")
            else -> false
        }
    }

    private fun matchesPlayer(
        player: PlayerLiveState,
        playerIndex: Int,
        playerName: String?,
        identity: PUBGTextNormalizer.TeamPlayerIdentity?
    ): Boolean {
        // 1. Strict Team Number + Player Number slot matching
        if (identity != null) {
            if (player.teamNumber == identity.teamNumber && playerIndex == identity.playerNumber - 1) {
                // If playerName is also provided, verify it doesn't conflict
                if (!playerName.isNullOrBlank()) {
                    val cleanName = PUBGTextNormalizer.extractCleanDisplayName(playerName)
                    val nameCandidate = cleanName.replace(identity.rawText, "").trim()
                    if (nameCandidate.isNotBlank()) {
                        val normTarget = PUBGTextNormalizer.normalizePlayerName(nameCandidate)
                        val normPlayer = PUBGTextNormalizer.normalizePlayerName(player.playerName)
                        if (normTarget.isNotBlank() && normPlayer.isNotBlank() && normPlayer != normTarget) {
                            val score = PUBGTextNormalizer.fuzzyMatchScore(player.playerName, nameCandidate)
                            if (score < 0.85f) {
                                // Conflict between slot number and player name -> reject wrong fallback
                                return false
                            }
                        }
                    }
                }
                return true
            }
            return false // Identity specified but did not match this slot -> do not fall back to other players in team
        }

        // 2. Strict Player Name matching (no loose substring matching to prevent wrong player updates)
        if (playerName.isNullOrBlank()) return false
        val cleanName = PUBGTextNormalizer.extractCleanDisplayName(playerName)
        val normTarget = PUBGTextNormalizer.normalizePlayerName(cleanName)
        val normPlayer = PUBGTextNormalizer.normalizePlayerName(player.playerName)

        if (normPlayer.isNotBlank() && normTarget.isNotBlank() && normPlayer == normTarget) {
            return true
        }

        if (player.playerName.equals(cleanName, ignoreCase = true)) {
            return true
        }

        val fuzzyScore = PUBGTextNormalizer.fuzzyMatchScore(player.playerName, cleanName)
        return fuzzyScore >= 0.85f
    }
}

