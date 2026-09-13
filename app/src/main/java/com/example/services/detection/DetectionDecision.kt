package com.example.services.detection

/**
 * Routing decision produced by the ConfidenceGate based on event confidence.
 *
 * Rules:
 * - [AUTO_PROCESS]: Confidence >= 0.50f (50% or higher). Event is processed automatically and NEVER blocks for Admin.
 * - [ADMIN_REVIEW]: Confidence < 0.50f (below 50%). Low-confidence event routed to the Admin Review queue.
 * - [REJECT]: Manually or policy rejected false detection.
 */
enum class DetectionDecision {
    AUTO_PROCESS,
    ADMIN_REVIEW,
    REJECT
}
