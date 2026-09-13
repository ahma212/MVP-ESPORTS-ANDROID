package com.example.services.detection.pubg

import com.example.core.model.DetectedEvent
import com.example.core.model.DetectedEventType
import com.example.core.model.DetectionEvidence
import com.example.core.rules.ConfidenceGate
import com.example.services.detection.IDetectionResult
import com.example.services.detection.IPUBGVisualDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Phase 4C.2 & K4-A — Real PUBG Mobile Multi-Line Kill-Feed Detector.
 *
 * Implements authoritative PUBG Mobile kill-feed recognition according to PUBG_Kill_Feed_Detector_Rules.pdf.
 *
 * Capabilities & Adherence:
 * 1. Multi-Line ROI Segmentation: Analyzes each visible kill-feed line independently (Req 1 & 5).
 * 2. Visual Icon Recognition: Multimodal detection of weapons, environment, knock, finish, revive, teamkill (Req 2).
 * 3. KNOCK Indicator: If knock icon appears, victim is NEVER treated as finally killed (Req 3).
 * 4. Multi-Frame Temporal Tracking: Combines OCR + Icons + Arrow markers + Temporal consistency (Req 4).
 * 5. Stable Evidence Output: Generates structured evidence with confidence, timestamps, reasons (Req 6).
 * 6. Duplicate Protection: Suppresses duplicate emissions for bars spanning multiple frames (Req 7).
 * 7. Unclear Evidence: Unclear lines remain unresolved without guessing (Req 8).
 * 8. AI-Assisted Vision: Optional AI layer analyzes ambiguous crops strictly as evidence (Req 9).
 */
class PUBGKillFeedDetector(
    val temporalTracker: KillFeedTemporalTracker = KillFeedTemporalTracker(),
    private val aiAssistant: IPUBGVisualAIAssistant = PUBGVisualAIAssistant.getInstance()
) : IPUBGVisualDetector {

    init {
        if (PUBGKillFeedVisualParser.getRegisteredVisionEngine() == null) {
            PUBGKillFeedVisualParser.registerVisionEngine(MLKitPUBGVisionEngine())
        }
    }

    override val detectorId: String = "PUBG_REAL_KILL_FEED_DETECTOR_PHASE_4C2"
    override val description: String = "Authoritative PUBG Mobile 20-Rule Multi-Line Kill-Feed Recognition Engine"
    override val priority: Int = 100

    private val _lastDetectedLines = MutableStateFlow<List<KillFeedLineEvidence>>(emptyList())
    val lastDetectedLines: StateFlow<List<KillFeedLineEvidence>> = _lastDetectedLines.asStateFlow()

    /**
     * Analyzes each visible kill-feed line in [evidence] independently.
     * Returns the structured evidence list for all visible lines.
     */
    suspend fun analyzeLines(evidence: DetectionEvidence): List<KillFeedLineEvidence> {
        val extractions = PUBGKillFeedVisualParser.parseMultipleLines(evidence)
        if (extractions.isEmpty()) return emptyList()

        val lineResults = mutableListOf<KillFeedLineEvidence>()

        for (extraction in extractions) {
            var activeExtraction = extraction

            // Optional AI assistance for borderline / ambiguous feed evidence (Req 9)
            // When ON, PUBGVisualAIAssistant + MLKit vision may sharpen/clarify the ROI crop and help name extraction.
            // When OFF, only the existing OCR/icon path runs. No extra AI work.
            val isAiOn = aiAssistant.isAiAssistEnabled.value || (evidence.metadata["ai_assist_requested"] as? Boolean ?: false)
            if (isAiOn && !activeExtraction.isTransitioning) {
                // If names are incomplete and pixel crop buffer is present, leverage MLKit vision engine
                if ((activeExtraction.rawLeft.isBlank() || activeExtraction.rawRight.isBlank()) &&
                    (evidence.croppedBuffer != null || evidence.buffer != null)
                ) {
                    try {
                        val visionCrop = PUBGKillFeedVisualParser.getRegisteredVisionEngine()?.processCrop(evidence)
                        if (visionCrop != null) {
                            activeExtraction = activeExtraction.copy(
                                rawLeft = if (activeExtraction.rawLeft.isBlank()) visionCrop.rawLeft else activeExtraction.rawLeft,
                                rawRight = if (activeExtraction.rawRight.isBlank()) visionCrop.rawRight else activeExtraction.rawRight,
                                clarityScore = maxOf(activeExtraction.clarityScore, visionCrop.clarityScore)
                            )
                        }
                    } catch (_: Throwable) {
                        // Keep detecting smoothly if vision crop fails
                    }
                }

                val lineSlice = PUBGKillFeedLineSegmenter.KillFeedLineSlice(
                    lineIndex = activeExtraction.lineIndex,
                    rawText = activeExtraction.rawLineText.ifBlank { "${activeExtraction.rawLeft} -> ${activeExtraction.rawRight}" },
                    subRect = null,
                    metadata = evidence.metadata
                )
                val aiEvidence = aiAssistant.analyzeVisualEvidence(lineSlice, activeExtraction)
                if (aiEvidence != null) {
                    val aiIcon = aiEvidence.suggestedIcon ?: activeExtraction.detectedIcon
                    val clarifiedKiller = aiEvidence.suggestedKillerName?.takeIf { it.isNotBlank() } ?: activeExtraction.rawLeft
                    val clarifiedVictim = aiEvidence.suggestedVictimName?.takeIf { it.isNotBlank() } ?: activeExtraction.rawRight
                    activeExtraction = activeExtraction.copy(
                        rawLeft = clarifiedKiller,
                        rawRight = clarifiedVictim,
                        detectedIcon = aiIcon,
                        cause = if (aiEvidence.hasKnockIndicator == true) KillFeedCause.KNOCK
                                else if (aiEvidence.hasFinishIndicator == true) KillFeedCause.FINISH_FROM_KNOCK
                                else aiIcon.toKillFeedCause(),
                        hasKnock = aiEvidence.hasKnockIndicator ?: activeExtraction.hasKnock,
                        hasFinishHelmet = aiEvidence.hasFinishIndicator ?: activeExtraction.hasFinishHelmet,
                        clarityScore = maxOf(activeExtraction.clarityScore, aiEvidence.visualConfidence),
                        iconReason = aiEvidence.reasoning
                    )
                }
            }

            // Record multi-frame temporal observation
            val track = temporalTracker.recordLineObservation(activeExtraction, evidence.frameTimestamp)

            // Check if track is ready or unresolved
            val isReady = temporalTracker.isTrackReadyToFinalize(
                track = track,
                isTransitioning = activeExtraction.isTransitioning,
                clarityScore = activeExtraction.clarityScore
            )

            val isDuplicate = temporalTracker.isDuplicateEvent(
                rawLeft = track.stabilizedLeft,
                rawRight = track.stabilizedRight,
                cause = track.stabilizedCause,
                hasKnock = track.stabilizedHasKnock,
                currentTimestampMs = evidence.frameTimestamp
            )

            if (!isReady || isDuplicate) {
                // Keep recorded as unresolved/duplicate without creating a new kill event
                lineResults.add(
                    temporalTracker.buildStableEvidence(
                        track = track,
                        confidence = activeExtraction.clarityScore,
                        reason = if (isDuplicate) "DUPLICATE_SUPPRESSED: Ongoing feed bar already emitted"
                                 else "UNRESOLVED_WAIT: Waiting for additional clear frames or valid player name",
                        isUnresolved = !isDuplicate
                    )
                )
                continue
            }

            // Run authoritative 20-rule decision engine
            val decision = PUBGKillFeedDecisionEngine.evaluate(
                rawLeft = track.stabilizedLeft,
                rawRight = track.stabilizedRight,
                cause = track.stabilizedCause,
                hasKnock = track.stabilizedHasKnock,
                hasFinishHelmet = track.stabilizedHasFinish,
                hasTeamKillIcon = track.stabilizedIcon == KillFeedVisualIcon.TEAM_KILL || track.stabilizedCause == KillFeedCause.TEAM_KILL,
                hasReviveIcon = track.stabilizedIcon == KillFeedVisualIcon.REVIVE || track.stabilizedCause == KillFeedCause.REVIVE,
                confidence = activeExtraction.clarityScore
            )

            // Critical KNOCK Rule (Req 3): If knock icon detected, victim is NOT finally killed
            val isKnock = decision.hasKnock || track.stabilizedHasKnock || activeExtraction.detectedIcon == KillFeedVisualIcon.KNOCK

            val stableEvidence = temporalTracker.buildStableEvidence(
                track = track,
                confidence = decision.confidence,
                reason = decision.explanation,
                isUnresolved = false,
                aiReasoning = activeExtraction.iconReason.takeIf { it.isNotBlank() }
            )

            lineResults.add(stableEvidence)
        }

        _lastDetectedLines.value = lineResults
        return lineResults
    }

    override suspend fun analyze(evidence: DetectionEvidence): IDetectionResult {
        val lineEvidences = analyzeLines(evidence)
        if (lineEvidences.isEmpty()) {
            return IDetectionResult.NoDetection(evidence.frameTimestamp, evidence.roiId)
        }

        // Find all resolved (non-unresolved) lines that have not yet been emitted or suppressed
        val actionableLines = lineEvidences.filter { !it.isUnresolved && !it.evidenceReason.startsWith("DUPLICATE_SUPPRESSED") }
        if (actionableLines.isEmpty()) {
            return IDetectionResult.NoDetection(evidence.frameTimestamp, evidence.roiId)
        }

        val generatedEvents = mutableListOf<DetectedEvent>()

        for (actionableLine in actionableLines) {
            val rawLeftStr = actionableLine.rawText.substringBefore("->").trim()
            val rawRightStr = actionableLine.rawText.substringAfter("->").trim()

            // Evaluate authoritative decision
            val decision = PUBGKillFeedDecisionEngine.evaluate(
                rawLeft = rawLeftStr,
                rawRight = rawRightStr,
                cause = actionableLine.cause,
                hasKnock = actionableLine.hasKnockIndicator,
                hasFinishHelmet = actionableLine.hasFinishIndicator,
                hasTeamKillIcon = actionableLine.hasTeamKillIndicator,
                hasReviveIcon = actionableLine.hasReviveIndicator,
                confidence = actionableLine.confidenceScore
            )

            // Mark finalized in tracker so subsequent frames suppress this specific event
            temporalTracker.markEventFinalized(
                rawLeft = rawLeftStr,
                rawRight = rawRightStr,
                cause = actionableLine.cause,
                hasKnock = actionableLine.hasKnockIndicator,
                timestampMs = evidence.frameTimestamp
            )

            val detectedEventType = when (decision.decisionType) {
                PUBGDecisionType.KNOCK -> DetectedEventType.KNOCK
                PUBGDecisionType.REVIVE -> DetectedEventType.REVIVE
                PUBGDecisionType.ENV_DEATH -> DetectedEventType.ELIMINATION
                PUBGDecisionType.SELF_KILL,
                PUBGDecisionType.TEAM_KILL,
                PUBGDecisionType.ENEMY_KILL -> DetectedEventType.KILL
                PUBGDecisionType.NO_DECISION_WAIT -> DetectedEventType.OTHER
            }

            val eventId = "pubg_evt_${evidence.frameTimestamp}_line${actionableLine.lineIndex}_${UUID.randomUUID().toString().take(8)}"

            val detectedEvent = DetectedEvent(
                eventId = eventId,
                eventType = detectedEventType,
                timestamp = System.currentTimeMillis(),
                frameTimestamp = evidence.frameTimestamp,
                confidence = decision.confidence,
                killerPlayerId = decision.pointAwardedTo ?: decision.killerName,
                victimPlayerId = decision.victimName,
                killerTeamId = decision.killerTeam,
                victimTeamId = decision.victimTeam,
                evidence = evidence,
                roiId = evidence.roiId,
                source = "SCREEN_CAPTURE",
                metadata = mapOf(
                    "cause" to decision.cause.name,
                    "detected_icon" to actionableLine.detectedIcon.name,
                    "rule_number" to decision.ruleNumber.toString(),
                    "dead" to decision.dead.toString(),
                    "kill_count" to decision.killCount.toString(),
                    "credited_kill_count" to decision.creditedKillCount.toString(),
                    "point_awarded_to" to (decision.pointAwardedTo ?: "NONE"),
                    "kill_credit_allowed" to decision.killCreditAllowed.toString(),
                    "decision_type" to decision.decisionType.name,
                    "has_knock" to decision.hasKnock.toString(),
                    "has_finish" to decision.hasFinishHelmet.toString(),
                    "raw_left" to decision.rawLeft,
                    "raw_right" to decision.rawRight,
                    "explanation" to decision.explanation,
                    "line_index" to actionableLine.lineIndex.toString(),
                    "frame_count" to actionableLine.temporalEvidence.frameCount.toString(),
                    "temporal_consistency" to actionableLine.temporalEvidence.consistencyScore.toString()
                )
            )

            generatedEvents.add(detectedEvent)
        }

        if (generatedEvents.isEmpty()) {
            return IDetectionResult.NoDetection(evidence.frameTimestamp, evidence.roiId)
        }

        val primaryEvent = generatedEvents.first()
        val primaryDecision = ConfidenceGate.evaluate(primaryEvent.confidence)

        return IDetectionResult.Success(
            event = primaryEvent,
            decision = primaryDecision,
            allEvents = generatedEvents
        )
    }

    /**
     * Resets internal tracking caches.
     */
    fun reset() {
        temporalTracker.reset()
        _lastDetectedLines.value = emptyList()
    }
}
