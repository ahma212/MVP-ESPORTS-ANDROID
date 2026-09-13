package com.example.services.detection.pubg

import com.example.core.model.EsportsEventType

/**
 * High-level PUBG Mobile kill-feed recognition decision.
 *
 * Models the exact outcome of the PDF decision tree:
 * - KNOCK: Downed player (0 kill count, 0 points)
 * - ENEMY_KILL: Valid kill on enemy (1 kill count, 1 point awarded to killer)
 * - SELF_KILL: Suicide / Self damage (1 kill count, 0 points to any player)
 * - TEAM_KILL: Team kill / Friendly fire (1 kill count, 0 points to any player)
 * - ENV_DEATH: Bluezone, Playzone, Red Zone, Fall, Drown, etc. (0 kill count, 0 points)
 * - REVIVE: Player revived (0 kill count, 0 points)
 * - NO_DECISION_WAIT: Unclear/animated frame, must wait for clear evidence
 */
enum class PUBGDecisionType {
    KNOCK,
    ENEMY_KILL,
    SELF_KILL,
    TEAM_KILL,
    ENV_DEATH,
    REVIVE,
    NO_DECISION_WAIT
}

data class PUBGKillFeedDecision(
    val decisionType: PUBGDecisionType,
    val esportEventType: EsportsEventType,
    val dead: Boolean,
    /**
     * Elimination event count according to PUBG spec (1 when victim is eliminated, 0 when alive/knocked/revived).
     * NOTE: For credited player kills, inspect [killCreditAllowed] and [creditedKillCount].
     */
    val killCount: Int,
    val pointAwardedTo: String?,
    val killerName: String?,
    val victimName: String?,
    val killerTeam: String?,
    val victimTeam: String?,
    val cause: KillFeedCause,
    val ruleNumber: Int,
    val confidence: Float,
    val explanation: String,
    val rawLeft: String,
    val rawRight: String,
    val hasKnock: Boolean,
    val hasFinishHelmet: Boolean
) {
    val isKnock: Boolean get() = decisionType == PUBGDecisionType.KNOCK
    val isKill: Boolean get() = decisionType == PUBGDecisionType.ENEMY_KILL || decisionType == PUBGDecisionType.SELF_KILL || decisionType == PUBGDecisionType.TEAM_KILL
    /**
     * Indicates whether a player is credited with a kill point in tournament standings.
     * True ONLY for ENEMY_KILL where a valid killer is identified and pointAwardedTo is not null.
     */
    val killCreditAllowed: Boolean get() = decisionType == PUBGDecisionType.ENEMY_KILL && pointAwardedTo != null
    /**
     * The number of credited kills awarded to a player (1 for valid ENEMY_KILL, 0 for SELF_KILL, TEAM_KILL, ENV_DEATH, KNOCK, REVIVE).
     */
    val creditedKillCount: Int get() = if (killCreditAllowed) 1 else 0
    val isAutoProcessable: Boolean get() = confidence >= 0.50f && decisionType != PUBGDecisionType.NO_DECISION_WAIT
    val requiresAdminReview: Boolean get() = confidence < 0.50f && decisionType != PUBGDecisionType.NO_DECISION_WAIT
}
