package com.example.services.detection

import com.example.core.model.DetectedEvent
import com.example.core.rules.ConfidenceGate

/**
 * Platform-independent result returned by [IDetectionService] passes.
 */
sealed interface IDetectionResult {

    /**
     * Successful visual detection of an in-game event with attached evidence.
     */
    data class Success(
        val event: DetectedEvent,
        val decision: DetectionDecision = ConfidenceGate.evaluate(event.confidence),
        val allEvents: List<DetectedEvent> = listOf(event)
    ) : IDetectionResult

    /**
     * No visual event detected in the ROI during this frame pass.
     * Returned as the standard safe state during Phase 4C Foundation.
     */
    data class NoDetection(
        val frameTimestamp: Long,
        val roiId: String
    ) : IDetectionResult

    /**
     * Frame processing or model inference failure.
     */
    data class Failure(
        val reason: String,
        val frameTimestamp: Long,
        val roiId: String
    ) : IDetectionResult
}
