package com.example.services.detection

import com.example.core.model.DetectedEvent
import com.example.core.model.DetectionEvidence
import com.example.core.model.DetectionFrame
import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.FrameAnalysisInput
import com.example.core.model.FrameAnalysisResult
import com.example.core.model.RoiRegion
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-independent contract for the Esports AI / Computer Vision event detection pipeline.
 *
 * Designed to execute identically across Android and Windows environments.
 *
 * Pipeline:
 * Captured Frame -> ROI Crop -> Detection Service -> Structured Detected Event -> Confidence Score -> Confidence Gate -> Auto Process OR Admin Review
 */
interface IDetectionService {
    /**
     * Observable state of the detection engine.
     */
    val detectionState: StateFlow<DetectionState>

    /**
     * Stream of detected in-game events emitted by the detection engine.
     */
    val detectedEventsFlow: SharedFlow<EsportsDetectedEvent>

    /**
     * Stream of structured [DetectedEvent] instances (Phase 4C standard).
     */
    val structuredEventsFlow: SharedFlow<DetectedEvent>

    /**
     * Stream of full detection results with evidence and confidence routing decisions.
     */
    val detectionResultsFlow: SharedFlow<IDetectionResult>

    /**
     * Stream of frame analysis results.
     */
    val analysisResultFlow: SharedFlow<FrameAnalysisResult>

    /**
     * Initializes the CV model / detection pipeline.
     */
    suspend fun initialize(): Result<Unit>

    /**
     * Submits a raw video frame with configured ROI for analysis.
     */
    suspend fun processFrame(frame: DetectionFrame)

    /**
     * Platform-independent detection pass taking a frame and ROI configuration.
     * Produces a structured [IDetectionResult].
     */
    suspend fun detectInRoi(frame: DetectionFrame, roi: RoiRegion): IDetectionResult

    /**
     * Analyzes an isolated ROI crop with attached [DetectionEvidence].
     */
    suspend fun analyzeEvidenceCrop(evidence: DetectionEvidence): IDetectionResult

    /**
     * Submits a sampled & cropped FrameAnalysisInput for analysis (Phase 4B backward compatibility).
     */
    suspend fun analyzeFrameInput(input: FrameAnalysisInput): FrameAnalysisResult

    /**
     * Shuts down the detection engine and frees model weights/memory.
     */
    fun shutdown()
}
