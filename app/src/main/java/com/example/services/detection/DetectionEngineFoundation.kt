package com.example.services.detection

import com.example.core.model.DetectedEvent
import com.example.core.model.DetectionEvidence
import com.example.core.model.DetectionFrame
import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.FrameAnalysisInput
import com.example.core.model.FrameAnalysisResult
import com.example.core.model.RoiRegion
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform-independent Detection Engine Foundation for Phase 4C.
 *
 * Adheres strictly to the architectural constraints:
 * - Does NOT emit fake AI detections or mock events.
 * - Does NOT invent fake confidence values.
 * - Safely returns [IDetectionResult.NoDetection] until the real visual detection model is plugged in.
 * - Preserves ROI configuration and evidence pipeline for frame inspection.
 * - Platform-independent: runs on Android, Windows, and backend test runners.
 */
class DetectionEngineFoundation : IDetectionService {

    private val _detectionState = MutableStateFlow<DetectionState>(DetectionState.Off)
    override val detectionState: StateFlow<DetectionState> = _detectionState.asStateFlow()

    private val _detectedEventsFlow = MutableSharedFlow<EsportsDetectedEvent>(extraBufferCapacity = 64)
    override val detectedEventsFlow: SharedFlow<EsportsDetectedEvent> = _detectedEventsFlow.asSharedFlow()

    private val _structuredEventsFlow = MutableSharedFlow<DetectedEvent>(extraBufferCapacity = 64)
    override val structuredEventsFlow: SharedFlow<DetectedEvent> = _structuredEventsFlow.asSharedFlow()

    private val _detectionResultsFlow = MutableSharedFlow<IDetectionResult>(extraBufferCapacity = 64)
    override val detectionResultsFlow: SharedFlow<IDetectionResult> = _detectionResultsFlow.asSharedFlow()

    private val _analysisResultFlow = MutableSharedFlow<FrameAnalysisResult>(extraBufferCapacity = 64)
    override val analysisResultFlow: SharedFlow<FrameAnalysisResult> = _analysisResultFlow.asSharedFlow()

    private var activeHandler: ((DetectionEvidence) -> IDetectionResult)? = null

    override suspend fun initialize(): Result<Unit> {
        _detectionState.value = DetectionState.Ready("PHASE_4C_DETECTION_FOUNDATION_READY")
        return Result.success(Unit)
    }

    /**
     * Optional hook for plugging in real visual recognition backends (ONNX, TFLite, OCR).
     */
    fun setDetectionHandler(handler: ((DetectionEvidence) -> IDetectionResult)?) {
        this.activeHandler = handler
    }

    override suspend fun processFrame(frame: DetectionFrame) {
        // Foundation pass-through
    }

    override suspend fun detectInRoi(frame: DetectionFrame, roi: RoiRegion): IDetectionResult {
        val cropW = (frame.width * roi.width).toInt().coerceAtLeast(1)
        val cropH = (frame.height * roi.height).toInt().coerceAtLeast(1)

        val evidence = DetectionEvidence(
            frameTimestamp = frame.timestampMs,
            roiId = roi.id,
            frameWidth = frame.width,
            frameHeight = frame.height,
            croppedWidth = cropW,
            croppedHeight = cropH,
            referenceId = frame.frameId.toString(),
            buffer = frame.buffer,
            metadata = mapOf(
                "roi_name" to roi.name,
                "format" to frame.format,
                "source" to frame.sourceIdentifier
            )
        )

        return analyzeEvidenceCrop(evidence)
    }

    override suspend fun analyzeEvidenceCrop(evidence: DetectionEvidence): IDetectionResult {
        val result = activeHandler?.invoke(evidence) ?: IDetectionResult.NoDetection(
            frameTimestamp = evidence.frameTimestamp,
            roiId = evidence.roiId
        )

        _detectionResultsFlow.tryEmit(result)

        if (result is IDetectionResult.Success) {
            _structuredEventsFlow.tryEmit(result.event)
        }

        return result
    }

    override suspend fun analyzeFrameInput(input: FrameAnalysisInput): FrameAnalysisResult {
        val currentRoi = input.appliedRoi ?: RoiRegion.DEFAULT_KILL_FEED
        val cropW = (input.width * currentRoi.width).toInt().coerceAtLeast(1)
        val cropH = (input.height * currentRoi.height).toInt().coerceAtLeast(1)

        val evidence = DetectionEvidence(
            frameTimestamp = input.timestampMs,
            roiId = currentRoi.id,
            frameWidth = input.width,
            frameHeight = input.height,
            croppedWidth = cropW,
            croppedHeight = cropH,
            referenceId = input.sequenceNumber.toString(),
            buffer = input.buffer,
            metadata = input.metadata
        )

        val detectionResult = analyzeEvidenceCrop(evidence)

        val frameResult = FrameAnalysisResult(
            sequenceNumber = input.sequenceNumber,
            frameTimestampMs = input.timestampMs,
            processingStartTimeMs = System.currentTimeMillis(),
            processingEndTimeMs = System.currentTimeMillis(),
            analyzerState = if (detectionResult is IDetectionResult.Success) "EVENT_DETECTED" else "NO_DETECTION",
            appliedRoi = input.appliedRoi,
            confidence = (detectionResult as? IDetectionResult.Success)?.event?.confidence,
            detectedEvents = emptyList()
        )

        _analysisResultFlow.tryEmit(frameResult)
        return frameResult
    }

    override fun shutdown() {
        _detectionState.value = DetectionState.Off
    }
}
