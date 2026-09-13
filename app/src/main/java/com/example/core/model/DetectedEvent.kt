package com.example.core.model

/**
 * Structured detected esports in-game event.
 * Platform-independent data model designed for Phase 4C Foundation and future cross-platform execution (Android & Windows).
 *
 * @param eventId Unique identifier for the detected event.
 * @param eventType Classification of the detected occurrence (KNOCK, KILL, ELIMINATION, etc.).
 * @param timestamp System arrival/creation timestamp (ms).
 * @param frameTimestamp Exact capture timestamp of the analyzed frame (ms).
 * @param confidence Optical detection confidence score in the range [0.0, 1.0].
 * @param killerPlayerId Nullable unique ID or registered in-game name of the killer/instigator.
 * @param victimPlayerId Nullable unique ID or registered in-game name of the victim.
 * @param killerTeamId Nullable team tag / clan ID of the killer.
 * @param victimTeamId Nullable team tag / clan ID of the victim.
 * @param evidence Exact ROI crop & frame metadata backing this detection for admin review.
 * @param roiId Configured ROI region ID where the event was detected.
 * @param source Source identifier (e.g., "SCREEN_CAPTURE", "VIDEO_FILE", "EXTERNAL_STREAM").
 * @param metadata Additional key-value attributes (weapon name, headshot flag, distance, etc.).
 */
data class DetectedEvent(
    val eventId: String,
    val eventType: DetectedEventType,
    val timestamp: Long = System.currentTimeMillis(),
    val frameTimestamp: Long,
    val confidence: Float,
    val killerPlayerId: String? = null,
    val victimPlayerId: String? = null,
    val killerTeamId: String? = null,
    val victimTeamId: String? = null,
    val evidence: DetectionEvidence? = null,
    val roiId: String,
    val source: String = "SCREEN_CAPTURE",
    val metadata: Map<String, String> = emptyMap()
) {
    val isHighConfidence: Boolean
        get() = confidence >= 0.50f

    val requiresAdminReview: Boolean
        get() = !isHighConfidence
}
