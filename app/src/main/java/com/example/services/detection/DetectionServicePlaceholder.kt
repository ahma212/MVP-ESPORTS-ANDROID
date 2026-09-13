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
 * Foundation placeholder implementation of [IDetectionService] for Phase 4B and 4C.
 *
 * Adheres strictly to the foundational architectural rules:
 * - Does NOT emit fake AI detections or mock events.
 * - Does NOT invent fake confidence values.
 * - Safely returns [IDetectionResult.NoDetection] until real visual PUBG detection is plugged in.
 * - Preserves complete compatibility with Phase 4B telemetry and UI pipelines.
 */
class DetectionServicePlaceholder(
    private val delegate: DetectionEngineFoundation = DetectionEngineFoundation()
) : IDetectionService {

    override val detectionState: StateFlow<DetectionState> = delegate.detectionState
    override val detectedEventsFlow: SharedFlow<EsportsDetectedEvent> = delegate.detectedEventsFlow
    override val structuredEventsFlow: SharedFlow<DetectedEvent> = delegate.structuredEventsFlow
    override val detectionResultsFlow: SharedFlow<IDetectionResult> = delegate.detectionResultsFlow
    override val analysisResultFlow: SharedFlow<FrameAnalysisResult> = delegate.analysisResultFlow

    override suspend fun initialize(): Result<Unit> = delegate.initialize()

    override suspend fun processFrame(frame: DetectionFrame) = delegate.processFrame(frame)

    override suspend fun detectInRoi(frame: DetectionFrame, roi: RoiRegion): IDetectionResult =
        delegate.detectInRoi(frame, roi)

    override suspend fun analyzeEvidenceCrop(evidence: DetectionEvidence): IDetectionResult =
        delegate.analyzeEvidenceCrop(evidence)

    override suspend fun analyzeFrameInput(input: FrameAnalysisInput): FrameAnalysisResult =
        delegate.analyzeFrameInput(input)

    override fun shutdown() = delegate.shutdown()
}
