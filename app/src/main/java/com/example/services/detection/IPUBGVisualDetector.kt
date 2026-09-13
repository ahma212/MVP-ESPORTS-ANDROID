package com.example.services.detection

import com.example.core.model.DetectionEvidence

/**
 * Platform-independent extension point interface for PUBG Mobile visual detector modules.
 *
 * Future visual detectors (Kill Feed Icon Matcher, Knock Pattern Detector, OCR Player Name Extractor, etc.)
 * can be plugged into the detection pipeline without modifying the core detection engine or UI.
 *
 * Designed to run identically across Android and Windows desktop environments.
 */
interface IPUBGVisualDetector {
    /**
     * Unique identifier for this detector module (e.g. "PUBG_KILL_FEED_DETECTOR_V1").
     */
    val detectorId: String

    /**
     * Human-readable description of what this detector module analyzes.
     */
    val description: String

    /**
     * Priority weight of the detector when multiple detectors evaluate the same evidence.
     * Higher numbers execute first.
     */
    val priority: Int get() = 0

    /**
     * Evaluates a crop with full [DetectionEvidence] (source frame, ROI crop, timestamps, dimensions).
     *
     * In Phase 4C.1, returns [IDetectionResult.NoDetection] until real visual patterns from marked screenshots are supplied.
     */
    suspend fun analyze(evidence: DetectionEvidence): IDetectionResult
}
