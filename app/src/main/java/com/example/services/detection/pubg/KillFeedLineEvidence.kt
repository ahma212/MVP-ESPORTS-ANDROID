package com.example.services.detection.pubg

/**
 * Stable, multi-signal evidence output for an independently detected kill-feed line.
 * Satisfies Requirement 6 of K4-A.
 */
data class KillFeedLineEvidence(
    val lineIndex: Int,
    val rawText: String,
    val killerName: String?,
    val victimName: String?,
    val detectedIcon: KillFeedVisualIcon,
    val cause: KillFeedCause,
    val hasKnockIndicator: Boolean,
    val hasFinishIndicator: Boolean,
    val hasTeamKillIndicator: Boolean = false,
    val hasReviveIndicator: Boolean = false,
    val temporalEvidence: TemporalEvidence,
    val confidenceScore: Float,
    val timestampMs: Long,
    val evidenceReason: String,
    val isUnresolved: Boolean = false,
    val aiAssistedReasoning: String? = null
)

/**
 * Multi-frame temporal tracking evidence confirming consistency across consecutive frames.
 */
data class TemporalEvidence(
    val frameCount: Int,
    val firstSeenTimestampMs: Long,
    val lastSeenTimestampMs: Long,
    val durationMs: Long,
    val consistencyScore: Float,
    val isMultiFrameValidated: Boolean,
    val observations: List<LineFrameObservation> = emptyList()
)

/**
 * Per-frame visual snapshot recorded while tracking a line.
 */
data class LineFrameObservation(
    val timestampMs: Long,
    val rawText: String,
    val detectedIcon: KillFeedVisualIcon,
    val hasKnock: Boolean,
    val clarityScore: Float
)
