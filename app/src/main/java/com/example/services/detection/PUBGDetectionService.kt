package com.example.services.detection

import com.example.core.model.DetectedEvent
import com.example.core.model.DetectionEvidence
import com.example.core.model.DetectionFrame
import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.FrameAnalysisInput
import com.example.core.model.FrameAnalysisResult
import com.example.core.model.RoiRegion
import com.example.services.analysis.RoiCropPipeline
import com.example.services.detection.pubg.PUBGKillFeedDetector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Real PUBG Mobile Detection Service for Phase 4C & 4C.2.
 *
 * Architecture:
 * MediaProjection / Frame Capture
 * → FrameSampler
 * → ROI Crop (configured dynamically by user/admin)
 * → PUBGDetectionService
 * → Registered IPUBGVisualDetector modules (PUBGKillFeedDetector)
 * → DetectedEvent
 * → ConfidenceGate
 * → AUTO_PROCESS (>= 0.50) / ADMIN_REVIEW (< 0.50)
 * → Supabase Persistence Pipeline
 *
 * Constraints:
 * - Does NOT hardcode kill-feed coordinates; dynamically operates on the configured ROI.
 * - Does NOT invent fake/mock detections.
 * - Platform-independent: Contains no Android UI or hardware capture code, ready for Windows runtime.
 */
class PUBGDetectionService(
    private val autoRegisterDefaultDetector: Boolean = false
) : IDetectionService {

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

    private val _latestDetectionResult = MutableStateFlow<IDetectionResult?>(null)
    val latestDetectionResult: StateFlow<IDetectionResult?> = _latestDetectionResult.asStateFlow()

    private val _latestDetectedEvent = MutableStateFlow<DetectedEvent?>(null)
    val latestDetectedEvent: StateFlow<DetectedEvent?> = _latestDetectedEvent.asStateFlow()

    private val _lastProcessedTimestamp = MutableStateFlow<Long>(0L)
    val lastProcessedTimestamp: StateFlow<Long> = _lastProcessedTimestamp.asStateFlow()

    private val registeredDetectors = CopyOnWriteArrayList<IPUBGVisualDetector>()

    override suspend fun initialize(): Result<Unit> {
        if (autoRegisterDefaultDetector) {
            registerDetector(PUBGKillFeedDetector())
        }
        _detectionState.value = DetectionState.Ready("PUBG_DETECTION_SERVICE_READY_PHASE_4C2")
        return Result.success(Unit)
    }

    /**
     * Registers a modular visual detector (e.g. Kill-feed matcher, Name OCR, Knock detector).
     * Enables adding future detectors without modifying the core detection engine.
     */
    fun registerDetector(detector: IPUBGVisualDetector) {
        registeredDetectors.removeAll { it.detectorId == detector.detectorId }
        registeredDetectors.add(detector)
        registeredDetectors.sortByDescending { it.priority }
    }

    /**
     * Unregisters a detector by its identifier.
     */
    fun unregisterDetector(detectorId: String) {
        registeredDetectors.removeAll { it.detectorId == detectorId }
    }

    /**
     * Returns a snapshot list of currently registered detectors.
     */
    fun getRegisteredDetectors(): List<IPUBGVisualDetector> = registeredDetectors.toList()

    override suspend fun processFrame(frame: DetectionFrame) {
        // Platform-independent frame ingestion hook
    }

    override suspend fun detectInRoi(frame: DetectionFrame, roi: RoiRegion): IDetectionResult {
        if (!roi.enabled) {
            val noDetection = IDetectionResult.NoDetection(
                frameTimestamp = frame.timestampMs,
                roiId = roi.id
            )
            _latestDetectionResult.value = noDetection
            _detectionResultsFlow.tryEmit(noDetection)
            return noDetection
        }

        val pixelRect = roi.toPixelRect(frame.width, frame.height)
        val croppedBuf = if (frame.buffer != null) {
            RoiCropPipeline.cropRgbaBuffer(frame.buffer, frame.width, frame.height, pixelRect)
        } else null

        val cropW = pixelRect.width
        val cropH = pixelRect.height

        val evidence = DetectionEvidence(
            frameTimestamp = frame.timestampMs,
            roiId = roi.id,
            frameWidth = frame.width,
            frameHeight = frame.height,
            croppedWidth = cropW,
            croppedHeight = cropH,
            referenceId = frame.frameId.toString(),
            buffer = croppedBuf ?: ByteArray(0),
            sourceBuffer = frame.buffer,
            croppedBuffer = croppedBuf ?: ByteArray(0),
            metadata = mapOf(
                "roi_name" to roi.name,
                "format" to frame.format,
                "source" to frame.sourceIdentifier,
                "crop_rect" to "${pixelRect.left},${pixelRect.top},$cropW,$cropH"
            )
        )

        return analyzeEvidenceCrop(evidence)
    }

    override suspend fun analyzeEvidenceCrop(evidence: DetectionEvidence): IDetectionResult {
        _lastProcessedTimestamp.value = evidence.frameTimestamp
        // Iterate registered detector modules in priority order
        for (detector in registeredDetectors) {
            val result = detector.analyze(evidence)
            if (result is IDetectionResult.Success) {
                _latestDetectionResult.value = result
                _latestDetectedEvent.value = result.event

                for (event in result.allEvents) {
                    _detectionResultsFlow.tryEmit(IDetectionResult.Success(event))
                    _structuredEventsFlow.tryEmit(event)

                    val esportsEvent = EsportsDetectedEvent(
                        id = event.eventId,
                        sessionId = com.example.services.streaming.LocalLiveRuntimeManager.broadcastState.value.sessionId.ifBlank { "live_session" },
                        eventType = when (event.eventType) {
                            com.example.core.model.DetectedEventType.KILL -> com.example.core.model.EsportsEventType.KILL
                            com.example.core.model.DetectedEventType.KNOCK -> com.example.core.model.EsportsEventType.KNOCK
                            com.example.core.model.DetectedEventType.ELIMINATION -> com.example.core.model.EsportsEventType.ELIMINATION
                            com.example.core.model.DetectedEventType.REVIVE -> com.example.core.model.EsportsEventType.REVIVE
                            else -> com.example.core.model.EsportsEventType.OTHER
                        },
                        timestampMs = event.timestamp,
                        killerPlayerName = event.killerPlayerId,
                        killerTeamTag = event.killerTeamId,
                        victimPlayerName = event.victimPlayerId,
                        victimTeamTag = event.victimTeamId,
                        weaponUsed = event.metadata["cause"],
                        confidence = event.confidence,
                        source = event.source,
                        frameReferenceId = event.frameTimestamp,
                        metadata = event.metadata
                    )
                    _detectedEventsFlow.tryEmit(esportsEvent)
                }

                return result
            }
        }

        // Default Phase 4C.1 behavior: Safe NO_DETECTION until real marked screenshot patterns are plugged in
        val noDetection = IDetectionResult.NoDetection(
            frameTimestamp = evidence.frameTimestamp,
            roiId = evidence.roiId
        )
        _latestDetectionResult.value = noDetection
        _detectionResultsFlow.tryEmit(noDetection)
        return noDetection
    }

    override suspend fun analyzeFrameInput(input: FrameAnalysisInput): FrameAnalysisResult {
        val currentRoi = input.appliedRoi ?: RoiRegion.DEFAULT_KILL_FEED
        if (!currentRoi.enabled) {
            val frameResult = FrameAnalysisResult(
                sequenceNumber = input.sequenceNumber,
                frameTimestampMs = input.timestampMs,
                processingStartTimeMs = System.currentTimeMillis(),
                processingEndTimeMs = System.currentTimeMillis(),
                analyzerState = "NO_DETECTION",
                appliedRoi = currentRoi,
                confidence = null,
                detectedEvents = emptyList()
            )
            _analysisResultFlow.tryEmit(frameResult)
            return frameResult
        }

        val croppedInput = if (input.isCropped) input else RoiCropPipeline.crop(input, currentRoi)
        val pixelRect = currentRoi.toPixelRect(input.width, input.height)

        val evidence = DetectionEvidence(
            frameTimestamp = croppedInput.timestampMs,
            roiId = currentRoi.id,
            frameWidth = input.width,
            frameHeight = input.height,
            croppedWidth = croppedInput.width,
            croppedHeight = croppedInput.height,
            referenceId = croppedInput.sequenceNumber.toString(),
            buffer = croppedInput.buffer ?: ByteArray(0),
            sourceBuffer = input.buffer,
            croppedBuffer = croppedInput.buffer ?: ByteArray(0),
            metadata = croppedInput.metadata
        )

        val detectionResult = analyzeEvidenceCrop(evidence)

        val frameResult = FrameAnalysisResult(
            sequenceNumber = input.sequenceNumber,
            frameTimestampMs = input.timestampMs,
            processingStartTimeMs = System.currentTimeMillis(),
            processingEndTimeMs = System.currentTimeMillis(),
            analyzerState = if (detectionResult is IDetectionResult.Success) "EVENT_DETECTED" else "NO_DETECTION",
            appliedRoi = currentRoi,
            confidence = (detectionResult as? IDetectionResult.Success)?.event?.confidence,
            detectedEvents = emptyList()
        )

        _analysisResultFlow.tryEmit(frameResult)
        return frameResult
    }

    override fun shutdown() {
        registeredDetectors.clear()
        _detectionState.value = DetectionState.Off
    }

    companion object {
        @Volatile
        private var instance: PUBGDetectionService? = null

        fun getInstance(): PUBGDetectionService {
            return instance ?: synchronized(this) {
                instance ?: PUBGDetectionService(autoRegisterDefaultDetector = true).also {
                    instance = it
                    it._detectionState.value = DetectionState.Ready("PUBG_DETECTION_SERVICE_READY")
                }
            }
        }
    }
}
