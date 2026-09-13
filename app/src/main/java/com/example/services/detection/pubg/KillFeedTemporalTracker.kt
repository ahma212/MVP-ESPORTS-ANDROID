package com.example.services.detection.pubg

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks PUBG Mobile kill feed bars across consecutive video frames to provide:
 * 1. Temporal / Animation stability (avoids premature decisions on blurry/animating frames).
 * 2. Duplicate protection (ensures a kill-feed bar lasting 3-5 seconds across multiple frames emits exactly ONE event).
 * 3. Knock -> Finish state tracking (correlates downed players and subsequent eliminations).
 */
class KillFeedTemporalTracker(
    private val duplicateWindowMs: Long = 6000L,
    private val knockRetentionMs: Long = 60000L
) {

    data class PendingKnock(
        val killerName: String?,
        val victimName: String,
        val weaponUsed: String?,
        val timestampMs: Long
    )

    data class FinalizedEventRecord(
        val eventKey: String,
        val firstSeenTimestampMs: Long,
        val finalizedTimestampMs: Long
    )

    // Tracks finalized events to prevent duplicate emissions
    private val finalizedEvents = ConcurrentHashMap<String, FinalizedEventRecord>()

    // Tracks pending knocks: victimName (normalized) -> PendingKnock
    private val pendingKnocks = ConcurrentHashMap<String, PendingKnock>()

    // Tracks active bars in transit across frames to ensure stable readability
    private val barClarityHistory = ConcurrentHashMap<String, MutableList<Float>>()

    // Multi-frame active line tracks: trackKey -> ActiveLineTrack
    data class ActiveLineTrack(
        val trackKey: String,
        val lineSlot: Int,
        val firstSeenTimestampMs: Long,
        var lastSeenTimestampMs: Long,
        var frameCount: Int,
        val observations: MutableList<LineFrameObservation> = mutableListOf(),
        var stabilizedLeft: String,
        var stabilizedRight: String,
        var stabilizedIcon: KillFeedVisualIcon,
        var stabilizedCause: KillFeedCause,
        var stabilizedHasKnock: Boolean,
        var stabilizedHasFinish: Boolean,
        var isEmitted: Boolean = false,
        var emittedTimestampMs: Long = 0L
    ) {
        val durationMs: Long get() = lastSeenTimestampMs - firstSeenTimestampMs
        val consistencyScore: Float
            get() {
                if (observations.isEmpty()) return 0.0f
                val agreeing = observations.count {
                    it.detectedIcon == stabilizedIcon && it.hasKnock == stabilizedHasKnock
                }
                return agreeing.toFloat() / observations.size
            }
    }

    private val activeLineTracks = ConcurrentHashMap<String, ActiveLineTrack>()

    /**
     * Builds a deterministic key for an event.
     */
    fun buildEventKey(
        rawLeft: String,
        rawRight: String,
        cause: KillFeedCause,
        hasKnock: Boolean
    ): String {
        val normLeft = PUBGTextNormalizer.normalizePlayerName(rawLeft)
        val normRight = PUBGTextNormalizer.normalizePlayerName(rawRight)
        return "${normLeft}::${cause.name}::${normRight}::${if (hasKnock) "KNOCK" else "KILL"}"
    }

    /**
     * Records a visual frame observation for a specific kill-feed line.
     * Implements Requirement 4: Track the same feed line across consecutive frames.
     */
    fun recordLineObservation(
        extraction: PUBGKillFeedVisualParser.VisualExtraction,
        timestampMs: Long
    ): ActiveLineTrack {
        cleanExpiredRecords(timestampMs)
        val eventKey = buildEventKey(extraction.rawLeft, extraction.rawRight, extraction.cause, extraction.hasKnock)

        val track = activeLineTracks.compute(eventKey) { _, existing ->
            if (existing != null) {
                existing.lastSeenTimestampMs = timestampMs
                existing.frameCount++
                existing.observations.add(
                    LineFrameObservation(
                        timestampMs = timestampMs,
                        rawText = extraction.rawLineText.ifBlank { "${extraction.rawLeft} -> ${extraction.rawRight}" },
                        detectedIcon = extraction.detectedIcon,
                        hasKnock = extraction.hasKnock,
                        clarityScore = extraction.clarityScore
                    )
                )
                // If knock indicator is detected in ANY frame of this track, enforce knock flag
                if (extraction.hasKnock || extraction.detectedIcon == KillFeedVisualIcon.KNOCK) {
                    existing.stabilizedHasKnock = true
                    if (existing.stabilizedCause == KillFeedCause.UNKNOWN) {
                        existing.stabilizedCause = KillFeedCause.KNOCK
                    }
                    if (existing.stabilizedIcon == KillFeedVisualIcon.UNKNOWN) {
                        existing.stabilizedIcon = KillFeedVisualIcon.KNOCK
                    }
                }
                existing
            } else {
                ActiveLineTrack(
                    trackKey = eventKey,
                    lineSlot = extraction.lineIndex,
                    firstSeenTimestampMs = timestampMs,
                    lastSeenTimestampMs = timestampMs,
                    frameCount = 1,
                    observations = mutableListOf(
                        LineFrameObservation(
                            timestampMs = timestampMs,
                            rawText = extraction.rawLineText.ifBlank { "${extraction.rawLeft} -> ${extraction.rawRight}" },
                            detectedIcon = extraction.detectedIcon,
                            hasKnock = extraction.hasKnock,
                            clarityScore = extraction.clarityScore
                        )
                    ),
                    stabilizedLeft = extraction.rawLeft,
                    stabilizedRight = extraction.rawRight,
                    stabilizedIcon = extraction.detectedIcon,
                    stabilizedCause = extraction.cause,
                    stabilizedHasKnock = extraction.hasKnock || extraction.detectedIcon == KillFeedVisualIcon.KNOCK,
                    stabilizedHasFinish = extraction.hasFinishHelmet || extraction.detectedIcon == KillFeedVisualIcon.FINISH_HELMET
                )
            }
        }!!

        return track
    }

    /**
     * Evaluates whether an active line track has stabilized and is ready to finalize.
     * Enforces:
     * - Multi-frame consistency (or high-confidence single frame)
     * - Unclear evidence remains unresolved (Requirement 8)
     */
    fun isTrackReadyToFinalize(
        track: ActiveLineTrack,
        isTransitioning: Boolean,
        clarityScore: Float
    ): Boolean {
        if (track.isEmitted) return false
        if (isTransitioning) return false

        if (clarityScore < 0.35f) return false

        val cleanLeft = PUBGTextNormalizer.stripFlagsAndDecorations(track.stabilizedLeft)
        val cleanRight = PUBGTextNormalizer.stripFlagsAndDecorations(track.stabilizedRight)

        // Victim name is mandatory for all kills/knocks
        if (cleanRight.isBlank() || cleanRight.length < 2) return false
        if (PUBGTextNormalizer.hasUnclearTeamPlayerToken(cleanRight)) return false

        // Killer name is mandatory unless environmental or knock without attacker
        val isEnv = track.stabilizedCause.isEnvironment || track.stabilizedIcon.isEnvironment
        if (!isEnv && track.stabilizedCause != KillFeedCause.KNOCK && cleanLeft.isBlank()) {
            return false
        }
        if (!isEnv && PUBGTextNormalizer.hasUnclearTeamPlayerToken(cleanLeft)) return false

        // Unclear icon with low clarity remains unresolved
        if (track.stabilizedIcon == KillFeedVisualIcon.UNKNOWN && clarityScore < 0.70f) {
            return false
        }

        // Multi-frame stability: require at least 2 consecutive frames or single clear frame (>= 0.40)
        return track.frameCount >= 2 || clarityScore >= 0.40f
    }

    /**
     * Builds structured stable evidence for a detected line (Requirement 6).
     */
    fun buildStableEvidence(
        track: ActiveLineTrack,
        confidence: Float,
        reason: String,
        isUnresolved: Boolean,
        aiReasoning: String? = null
    ): KillFeedLineEvidence {
        val temporalEvidence = TemporalEvidence(
            frameCount = track.frameCount,
            firstSeenTimestampMs = track.firstSeenTimestampMs,
            lastSeenTimestampMs = track.lastSeenTimestampMs,
            durationMs = track.durationMs,
            consistencyScore = track.consistencyScore,
            isMultiFrameValidated = track.frameCount >= 2,
            observations = track.observations.toList()
        )

        val isEnv = track.stabilizedCause.isEnvironment || track.stabilizedIcon.isEnvironment ||
                track.stabilizedCause in setOf(KillFeedCause.RED_ZONE, KillFeedCause.PLAYZONE, KillFeedCause.BLUE_ZONE, KillFeedCause.AIRSTRIKE, KillFeedCause.WATER, KillFeedCause.FALL)
        val isSuicide = track.stabilizedCause == KillFeedCause.SUICIDE || track.stabilizedIcon == KillFeedVisualIcon.SUICIDE

        val cleanKiller = if (isEnv || isSuicide) null else PUBGTextNormalizer.extractCleanDisplayName(track.stabilizedLeft).takeIf { it.isNotBlank() }
        val cleanVictim = PUBGTextNormalizer.extractCleanDisplayName(track.stabilizedRight).takeIf { it.isNotBlank() }

        return KillFeedLineEvidence(
            lineIndex = track.lineSlot,
            rawText = "${track.stabilizedLeft} -> ${track.stabilizedRight}",
            killerName = cleanKiller,
            victimName = cleanVictim,
            detectedIcon = track.stabilizedIcon,
            cause = track.stabilizedCause,
            hasKnockIndicator = track.stabilizedHasKnock,
            hasFinishIndicator = track.stabilizedHasFinish,
            hasTeamKillIndicator = track.stabilizedIcon == KillFeedVisualIcon.TEAM_KILL || track.stabilizedCause == KillFeedCause.TEAM_KILL,
            hasReviveIndicator = track.stabilizedIcon == KillFeedVisualIcon.REVIVE || track.stabilizedCause == KillFeedCause.REVIVE,
            temporalEvidence = temporalEvidence,
            confidenceScore = confidence,
            timestampMs = track.lastSeenTimestampMs,
            evidenceReason = reason,
            isUnresolved = isUnresolved,
            aiAssistedReasoning = aiReasoning
        )
    }

    /**
     * Evaluates whether a frame observation is clear enough or should WAIT.
     *
     * Temporal / Animation Rule:
     * - If killer name is unclear -> WAIT
     * - If victim name is unclear -> WAIT
     * - If required icon is unclear -> WAIT
     * - If feed is still transitioning/animating -> WAIT
     * - Returns true if ready to finalize; false if should wait.
     */
    fun isVisualEvidenceReady(
        rawLeft: String,
        rawRight: String,
        cause: KillFeedCause,
        isTransitioning: Boolean,
        clarityScore: Float
    ): Boolean {
        // Animation / transition in progress or unclear team/player token
        if (isTransitioning || PUBGTextNormalizer.hasUnclearTeamPlayerToken(rawLeft) || PUBGTextNormalizer.hasUnclearTeamPlayerToken(rawRight)) return false

        // Blurry or unreadable text
        if (clarityScore < 0.35f) return false

        val cleanLeft = PUBGTextNormalizer.stripFlagsAndDecorations(rawLeft)
        val cleanRight = PUBGTextNormalizer.stripFlagsAndDecorations(rawRight)

        // Right text (victim) is always required for all kills and knocks
        if (cleanRight.isBlank() || cleanRight.length < 2) return false

        // Left text is required unless it's a generic knock without weapon/attacker
        val leftIsSystem = PUBGTextNormalizer.isSystemText(cleanLeft) || cause.isEnvironment
        if (!leftIsSystem && cause != KillFeedCause.KNOCK && cleanLeft.isBlank()) {
            return false
        }

        // Check if cause icon is completely unknown
        if (cause == KillFeedCause.UNKNOWN && clarityScore < 0.70f) {
            return false
        }

        val eventKey = buildEventKey(rawLeft, rawRight, cause, hasKnock = false)
        val history = barClarityHistory.computeIfAbsent(eventKey) { mutableListOf() }
        history.add(clarityScore)

        // If clarity is sufficient in single frame (e.g. >= 0.40), or read across 2+ consecutive frames
        return clarityScore >= 0.40f || history.size >= 2
    }

    /**
     * Checks if this event has already been finalized recently (Duplicate Protection).
     */
    fun isDuplicateEvent(
        rawLeft: String,
        rawRight: String,
        cause: KillFeedCause,
        hasKnock: Boolean,
        currentTimestampMs: Long
    ): Boolean {
        cleanExpiredRecords(currentTimestampMs)
        val key = buildEventKey(rawLeft, rawRight, cause, hasKnock)
        val record = finalizedEvents[key] ?: return false
        return (currentTimestampMs - record.finalizedTimestampMs) < duplicateWindowMs
    }

    /**
     * Marks an event as finalized to block subsequent duplicate frame emissions.
     */
    fun markEventFinalized(
        rawLeft: String,
        rawRight: String,
        cause: KillFeedCause,
        hasKnock: Boolean,
        timestampMs: Long
    ) {
        val key = buildEventKey(rawLeft, rawRight, cause, hasKnock)
        finalizedEvents[key] = FinalizedEventRecord(
            eventKey = key,
            firstSeenTimestampMs = timestampMs,
            finalizedTimestampMs = timestampMs
        )

        activeLineTracks[key]?.apply {
            isEmitted = true
            emittedTimestampMs = timestampMs
        }

        val normVictim = PUBGTextNormalizer.normalizePlayerName(rawRight)
        if (hasKnock) {
            // Record pending knock
            val cleanLeft = PUBGTextNormalizer.extractCleanDisplayName(rawLeft)
            pendingKnocks[normVictim] = PendingKnock(
                killerName = cleanLeft,
                victimName = PUBGTextNormalizer.extractCleanDisplayName(rawRight),
                weaponUsed = cause.name,
                timestampMs = timestampMs
            )
        } else {
            // If victim was killed, consume pending knock
            pendingKnocks.remove(normVictim)
        }
    }

    /**
     * Retrieves any pending knock for a victim player.
     */
    fun getPendingKnock(victimRaw: String): PendingKnock? {
        val norm = PUBGTextNormalizer.normalizePlayerName(victimRaw)
        return pendingKnocks[norm]
    }

    /**
     * Clears old tracking state.
     */
    fun reset() {
        finalizedEvents.clear()
        pendingKnocks.clear()
        barClarityHistory.clear()
        activeLineTracks.clear()
    }

    private fun cleanExpiredRecords(nowMs: Long) {
        finalizedEvents.entries.removeIf { nowMs - it.value.finalizedTimestampMs > duplicateWindowMs }
        pendingKnocks.entries.removeIf { nowMs - it.value.timestampMs > knockRetentionMs }
        activeLineTracks.entries.removeIf { nowMs - it.value.lastSeenTimestampMs > duplicateWindowMs }
        if (barClarityHistory.size > 200) {
            barClarityHistory.clear()
        }
    }
}
