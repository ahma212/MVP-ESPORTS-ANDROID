package com.example.core.rules

import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.EventProcessingStatus

/**
 * Shared, platform-independent confidence threshold and business rules.
 *
 * Rules:
 * 1. Confidence >= 0.50f (50% or higher) -> Process automatically.
 * 2. Confidence < 0.50f (below 50%) -> Send to Admin Review Queue.
 * 3. High-confidence events must NOT wait for admin approval.
 */
object ConfidenceRule {
    const val THRESHOLD_AUTO_PROCESS: Float = 0.50f

    /**
     * Determines whether an event meets the automatic processing threshold.
     */
    fun shouldAutoProcess(confidence: Float): Boolean {
        return confidence >= THRESHOLD_AUTO_PROCESS
    }

    /**
     * Evaluates an incoming detected event and assigns its processing destination.
     */
    fun evaluate(confidence: Float): EventProcessingStatus {
        return if (shouldAutoProcess(confidence)) {
            EventProcessingStatus.AUTO_PROCESSED
        } else {
            EventProcessingStatus.PENDING_ADMIN_REVIEW
        }
    }

    /**
     * Applies the rule to an existing event.
     */
    fun applyRule(event: EsportsDetectedEvent): EsportsDetectedEvent {
        val targetStatus = evaluate(event.confidence)
        return event.copy(status = targetStatus)
    }
}
