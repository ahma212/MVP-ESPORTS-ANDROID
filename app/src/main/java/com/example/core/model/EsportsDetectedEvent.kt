package com.example.core.model

/**
 * Structured detected esports event.
 * Represents in-game PUBG Mobile kill feed, knock, elimination, or revive occurrences.
 *
 * Core rule:
 * If confidence >= 0.50f (50%), status is AUTO_PROCESSED.
 * If confidence < 0.50f (<50%), status is PENDING_ADMIN_REVIEW.
 */
data class EsportsDetectedEvent(
    val id: String,
    val sessionId: String,
    val eventType: EsportsEventType,
    val timestampMs: Long = System.currentTimeMillis(),
    val killerPlayerName: String? = null,
    val killerTeamTag: String? = null,
    val victimPlayerName: String? = null,
    val victimTeamTag: String? = null,
    val weaponUsed: String? = null,
    val confidence: Float,
    val source: String = "SCREEN_CAPTURE",
    val frameReferenceId: Long? = null,
    val status: EventProcessingStatus = if (confidence >= 0.50f) {
        EventProcessingStatus.AUTO_PROCESSED
    } else {
        EventProcessingStatus.PENDING_ADMIN_REVIEW
    },
    val reviewNote: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    val isHighConfidence: Boolean
        get() = confidence >= 0.50f

    val requiresAdminReview: Boolean
        get() = !isHighConfidence && status == EventProcessingStatus.PENDING_ADMIN_REVIEW
}
