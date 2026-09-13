package com.example.services.detection.pubg

import com.example.core.model.EsportsEventType

/**
 * Deterministic rules engine executing the authoritative PUBG Mobile Kill-Feed decision logic.
 *
 * Implements the exact 20-rule matrix from PUBG_Kill_Feed_Detector_Rules.pdf:
 *
 * 1. Has Revive -> REVIVE (Not a kill, 0 points)
 * 2. Has Knock (or Finish-From-Knock) -> KNOCK / FINISH (Not a new kill, 0 points)
 * 3. Left is System OR Environment Cause (Playzone, Bluezone, Red Zone, Airstrike, Drowning, Fall, Trap, Fire Env) -> ENV_DEATH (dead=true, 0 points to any player)
 * 4. Same Name OR Suicide Icon -> SELF_KILL (dead=true, kill_count=1, 0 points)
 * 5. Same Team OR Team-Kill/Friendly-Fire Icon -> TEAM_KILL (dead=true, kill_count=1, 0 points)
 * 6. Enemy Kill (Gun, Grenade, Vehicle, Molotov, Melee) -> ENEMY_KILL (dead=true, kill_count=1, 1 point awarded to Left Player)
 */
object PUBGKillFeedDecisionEngine {

    /**
     * Evaluates visual extraction attributes against the authoritative PDF decision tree.
     */
    fun evaluate(
        rawLeft: String,
        rawRight: String,
        cause: KillFeedCause,
        hasKnock: Boolean = false,
        hasFinishHelmet: Boolean = false,
        hasTeamKillIcon: Boolean = false,
        hasReviveIcon: Boolean = false,
        confidence: Float = 0.90f
    ): PUBGKillFeedDecision {
        // Check for unclear/corrupted Team or Player tokens (e.g. T3 P?, T? P2) -> WAIT
        if (PUBGTextNormalizer.hasUnclearTeamPlayerToken(rawLeft) || PUBGTextNormalizer.hasUnclearTeamPlayerToken(rawRight)) {
            return PUBGKillFeedDecision(
                decisionType = PUBGDecisionType.NO_DECISION_WAIT,
                esportEventType = EsportsEventType.ELIMINATION,
                dead = false,
                killCount = 0,
                pointAwardedTo = null,
                killerName = null,
                victimName = null,
                killerTeam = null,
                victimTeam = null,
                cause = cause,
                ruleNumber = 0,
                confidence = confidence,
                explanation = "Unclear Team Number / Player Number detected. Waiting for subsequent clear frames.",
                rawLeft = rawLeft,
                rawRight = rawRight,
                hasKnock = hasKnock,
                hasFinishHelmet = hasFinishHelmet
            )
        }

        val killerIdentity = PUBGTextNormalizer.extractTeamPlayerIdentity(rawLeft)
        val victimIdentity = PUBGTextNormalizer.extractTeamPlayerIdentity(rawRight)

        // Robust identity resolution with conflict check
        val killerResolution = TournamentRoster.resolvePlayerAdvancedWithConflictCheck(killerIdentity, rawLeft)
        val victimResolution = TournamentRoster.resolvePlayerAdvancedWithConflictCheck(victimIdentity, rawRight)

        val killerConflict = killerResolution.hasConflict
        val victimConflict = victimResolution.hasConflict

        if (killerConflict || victimConflict) {
            val reason = killerResolution.conflictReason ?: victimResolution.conflictReason ?: "Identity conflict detected."
            return PUBGKillFeedDecision(
                decisionType = PUBGDecisionType.NO_DECISION_WAIT,
                esportEventType = EsportsEventType.ELIMINATION,
                dead = false,
                killCount = 0,
                pointAwardedTo = null,
                killerName = null,
                victimName = null,
                killerTeam = null,
                victimTeam = null,
                cause = cause,
                ruleNumber = 0,
                confidence = 0.35f, // Below 50% confidence gate -> Admin Review
                explanation = "Conflicting evidence: $reason. Routed to Admin Review.",
                rawLeft = rawLeft,
                rawRight = rawRight,
                hasKnock = hasKnock,
                hasFinishHelmet = hasFinishHelmet
            )
        }

        val killerRosterPlayer = killerResolution.player
        val victimRosterPlayer = victimResolution.player

        val cleanLeft = when {
            killerRosterPlayer != null -> killerRosterPlayer.inGameName
            killerIdentity != null -> killerIdentity.formattedKey
            else -> PUBGTextNormalizer.extractCleanDisplayName(rawLeft)
        }

        val cleanRight = when {
            victimRosterPlayer != null -> victimRosterPlayer.inGameName
            victimIdentity != null -> victimIdentity.formattedKey
            else -> PUBGTextNormalizer.extractCleanDisplayName(rawRight)
        }

        val leftTeam = killerRosterPlayer?.teamTag
            ?: (killerIdentity?.let { "T${it.teamNumber}" })
            ?: PUBGTextNormalizer.extractTeamTag(rawLeft)

        val rightTeam = victimRosterPlayer?.teamTag
            ?: (victimIdentity?.let { "T${it.teamNumber}" })
            ?: PUBGTextNormalizer.extractTeamTag(rawRight)

        val killerTeamNumber = killerRosterPlayer?.slotNumber ?: killerIdentity?.teamNumber
        val victimTeamNumber = victimRosterPlayer?.slotNumber ?: victimIdentity?.teamNumber

        val leftIsSystem = PUBGTextNormalizer.isSystemText(cleanLeft) || cause.isEnvironment
        val sameName = (killerIdentity != null && victimIdentity != null && killerIdentity == victimIdentity) ||
                PUBGTextNormalizer.isSamePlayerName(cleanLeft, cleanRight) ||
                (killerRosterPlayer != null && victimRosterPlayer != null && killerRosterPlayer.id == victimRosterPlayer.id)
        
        val sameTeam = (killerTeamNumber != null && victimTeamNumber != null && killerTeamNumber == victimTeamNumber) ||
                (killerIdentity != null && victimIdentity != null && killerIdentity.teamNumber == victimIdentity.teamNumber) ||
                PUBGTextNormalizer.isSameTeam(leftTeam, rightTeam)

        // 1. REVIVE RULE (Rule 20)
        if (hasReviveIcon || cause == KillFeedCause.REVIVE) {
            return PUBGKillFeedDecision(
                decisionType = PUBGDecisionType.REVIVE,
                esportEventType = EsportsEventType.REVIVE,
                dead = false,
                killCount = 0,
                pointAwardedTo = null,
                killerName = cleanLeft.ifBlank { null },
                victimName = cleanRight.ifBlank { null },
                killerTeam = leftTeam,
                victimTeam = rightTeam,
                cause = KillFeedCause.REVIVE,
                ruleNumber = 20,
                confidence = confidence,
                explanation = "Player $cleanRight revived by $cleanLeft (Not a kill)",
                rawLeft = rawLeft,
                rawRight = rawRight,
                hasKnock = false,
                hasFinishHelmet = false
            )
        }

        // 2. KNOCK RULES (Rules 13, 14, 15, 16, 19)
        if (hasKnock || cause == KillFeedCause.KNOCK || hasFinishHelmet || cause == KillFeedCause.FINISH_FROM_KNOCK) {
            val ruleNum = when {
                hasFinishHelmet || cause == KillFeedCause.FINISH_FROM_KNOCK -> 16
                cause == KillFeedCause.GUN || cause == KillFeedCause.HEADSHOT -> 13
                cause == KillFeedCause.GRENADE -> 14
                cause == KillFeedCause.KNOCK || cause == KillFeedCause.UNKNOWN -> 15
                else -> 19
            }
            val explanation = if (hasFinishHelmet || cause == KillFeedCause.FINISH_FROM_KNOCK) {
                "Player $cleanRight finished from knock (Helmet icon, not a new kill)"
            } else {
                "Player $cleanRight knocked down by $cleanLeft (Downed, wait for finish)"
            }

            return PUBGKillFeedDecision(
                decisionType = PUBGDecisionType.KNOCK,
                esportEventType = EsportsEventType.KNOCK,
                dead = false,
                killCount = 0,
                pointAwardedTo = null,
                killerName = if (leftIsSystem) null else cleanLeft,
                victimName = cleanRight,
                killerTeam = leftTeam,
                victimTeam = rightTeam,
                cause = if (hasFinishHelmet) KillFeedCause.FINISH_FROM_KNOCK else KillFeedCause.KNOCK,
                ruleNumber = ruleNum,
                confidence = confidence,
                explanation = explanation,
                rawLeft = rawLeft,
                rawRight = rawRight,
                hasKnock = true,
                hasFinishHelmet = hasFinishHelmet
            )
        }

        // 3. ENVIRONMENTAL SYSTEM DEATHS (Rules 5, 6, 7, 8, 9, 10, 11)
        if (leftIsSystem || cause.isEnvironment) {
            val effectiveCause = when {
                cause != KillFeedCause.UNKNOWN && cause.isEnvironment -> cause
                cleanLeft.contains("red", ignoreCase = true) -> KillFeedCause.RED_ZONE
                cleanLeft.contains("blue", ignoreCase = true) -> KillFeedCause.BLUE_ZONE
                cleanLeft.contains("zone", ignoreCase = true) -> KillFeedCause.PLAYZONE
                cleanLeft.contains("air", ignoreCase = true) -> KillFeedCause.AIRSTRIKE
                cleanLeft.contains("water", ignoreCase = true) || cleanLeft.contains("drown", ignoreCase = true) -> KillFeedCause.WATER
                cleanLeft.contains("fall", ignoreCase = true) -> KillFeedCause.FALL
                cleanLeft.contains("trap", ignoreCase = true) || cleanLeft.contains("mine", ignoreCase = true) -> KillFeedCause.TRAP_MINE
                else -> KillFeedCause.PLAYZONE
            }

            return PUBGKillFeedDecision(
                decisionType = PUBGDecisionType.ENV_DEATH,
                esportEventType = EsportsEventType.ELIMINATION,
                dead = true,
                killCount = 0, // Environment deaths do NOT award player kills
                pointAwardedTo = null, // No player gets points
                killerName = null,
                victimName = cleanRight,
                killerTeam = null,
                victimTeam = rightTeam,
                cause = effectiveCause,
                ruleNumber = effectiveCause.ruleNumber,
                confidence = confidence,
                explanation = "Player $cleanRight eliminated by environment (${effectiveCause.description}) - 0 points awarded",
                rawLeft = rawLeft,
                rawRight = rawRight,
                hasKnock = false,
                hasFinishHelmet = false
            )
        }

        // 4. SELF KILL / SUICIDE (Rule 12)
        if (sameName || cause == KillFeedCause.SUICIDE) {
            return PUBGKillFeedDecision(
                decisionType = PUBGDecisionType.SELF_KILL,
                esportEventType = EsportsEventType.KILL,
                dead = true,
                killCount = 1,
                pointAwardedTo = null, // Suicide = 0 points awarded to any player
                killerName = cleanLeft,
                victimName = cleanRight,
                killerTeam = leftTeam,
                victimTeam = rightTeam,
                cause = KillFeedCause.SUICIDE,
                ruleNumber = 12,
                confidence = confidence,
                explanation = "Self Kill / Suicide by $cleanLeft - 0 points awarded",
                rawLeft = rawLeft,
                rawRight = rawRight,
                hasKnock = false,
                hasFinishHelmet = false
            )
        }

        // 5. TEAM KILL / FRIENDLY FIRE (Rules 17, 18)
        if (sameTeam || hasTeamKillIcon || cause == KillFeedCause.TEAM_KILL || cause == KillFeedCause.FRIENDLY_FIRE) {
            val ruleNum = if (cause == KillFeedCause.FRIENDLY_FIRE) 18 else 17
            val effCause = if (cause == KillFeedCause.FRIENDLY_FIRE) KillFeedCause.FRIENDLY_FIRE else KillFeedCause.TEAM_KILL

            return PUBGKillFeedDecision(
                decisionType = PUBGDecisionType.TEAM_KILL,
                esportEventType = EsportsEventType.KILL,
                dead = true,
                killCount = 1,
                pointAwardedTo = null, // Team kill = 0 points awarded
                killerName = cleanLeft,
                victimName = cleanRight,
                killerTeam = leftTeam,
                victimTeam = rightTeam,
                cause = effCause,
                ruleNumber = ruleNum,
                confidence = confidence,
                explanation = "Team Kill / Friendly Fire: $cleanLeft eliminated teammate $cleanRight - 0 points awarded",
                rawLeft = rawLeft,
                rawRight = rawRight,
                hasKnock = false,
                hasFinishHelmet = false
            )
        }

        // 6. ENEMY KILL (Rules 1, 2, 3, 4, Melee)
        val finalCause = if (cause == KillFeedCause.UNKNOWN) KillFeedCause.GUN else cause
        val ruleNum = when (finalCause) {
            KillFeedCause.GUN, KillFeedCause.HEADSHOT, KillFeedCause.MELEE -> 1
            KillFeedCause.GRENADE -> 2
            KillFeedCause.VEHICLE -> 3
            KillFeedCause.MOLOTOV -> 4
            else -> 1
        }

        return PUBGKillFeedDecision(
            decisionType = PUBGDecisionType.ENEMY_KILL,
            esportEventType = EsportsEventType.KILL,
            dead = true,
            killCount = 1,
            pointAwardedTo = cleanLeft, // Player A receives the kill point!
            killerName = cleanLeft,
            victimName = cleanRight,
            killerTeam = leftTeam,
            victimTeam = rightTeam,
            cause = finalCause,
            ruleNumber = ruleNum,
            confidence = confidence,
            explanation = "Valid Enemy Kill: $cleanLeft eliminated $cleanRight with ${finalCause.description} - 1 point awarded to $cleanLeft",
            rawLeft = rawLeft,
            rawRight = rawRight,
            hasKnock = false,
            hasFinishHelmet = false
        )
    }
}
