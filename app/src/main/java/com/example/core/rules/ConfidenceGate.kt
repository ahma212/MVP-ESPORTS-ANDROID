package com.example.core.rules

import com.example.core.model.DetectedEvent
import com.example.services.detection.DetectionDecision

/**
 * Shared, platform-independent Confidence Gate rules engine.
 *
 * Rules:
 * 1. Confidence >= 0.50f (50% or higher) -> AUTO_PROCESS
 *    High-confidence events MUST NOT wait for Admin approval.
 * 2. Confidence < 0.50f (below 50%) -> ADMIN_REVIEW
 *    Low-confidence events MUST enter the Admin Review queue.
 */
object ConfidenceGate {
    /**
     * Strict cutoff threshold for automatic event processing.
     */
    const val THRESHOLD_AUTO_PROCESS: Float = 0.50f

    /**
     * Evaluates a raw confidence score and returns the definitive routing decision.
     */
    fun evaluate(confidence: Float): DetectionDecision {
        return if (confidence >= THRESHOLD_AUTO_PROCESS) {
            DetectionDecision.AUTO_PROCESS
        } else {
            DetectionDecision.ADMIN_REVIEW
        }
    }

    /**
     * Determines whether an event meets the automatic processing threshold.
     */
    fun shouldAutoProcess(confidence: Float): Boolean {
        return confidence >= THRESHOLD_AUTO_PROCESS
    }

    /**
     * Determines whether an event requires manual Admin review.
     */
    fun requiresAdminReview(confidence: Float): Boolean {
        return confidence < THRESHOLD_AUTO_PROCESS
    }

    /**
     * Evaluates a structured [DetectedEvent].
     */
    fun evaluateEvent(event: DetectedEvent): DetectionDecision {
        return evaluate(event.confidence)
    }
}
