package com.example.services.detection.pubg

import com.example.core.model.DetectionEvidence

/**
 * Clean architectural extension point interface for real-frame vision processing,
 * OCR text extraction, and kill-feed icon classification.
 *
 * When an external ML Kit OCR, TFLite icon classifier, or ONNX engine is plugged in,
 * it implements this interface to convert raw frame crops into structured [PUBGKillFeedVisualParser.VisualExtraction].
 *
 * If no vision engine is attached or the crop image is unreadable/unclear,
 * the pipeline safely defaults to NO_DECISION_WAIT without guessing player names.
 */
interface IPUBGVisionEngine {
    /**
     * Unique identifier for the vision/OCR engine (e.g. "ML_KIT_OCR_V1", "TFLITE_ICON_CLASSIFIER").
     */
    val engineId: String

    /**
     * Human-readable description of engine capabilities.
     */
    val description: String

    /**
     * Analyzes raw pixel crop evidence and extracts structured visual elements.
     * Returns null or VisualExtraction with clarityScore < 0.35f if unreadable/unclear.
     */
    suspend fun processCrop(evidence: DetectionEvidence): PUBGKillFeedVisualParser.VisualExtraction?
}
