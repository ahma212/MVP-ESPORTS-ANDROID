package com.example.core.model

/**
 * Platform-independent result produced by a frame analysis / CV pass.
 *
 * In Phase 4B, this model serves as a clean architectural contract.
 * No fake confidence scores or artificial detection events are created.
 */
data class FrameAnalysisResult(
    val sequenceNumber: Long,
    val frameTimestampMs: Long,
    val processingStartTimeMs: Long,
    val processingEndTimeMs: Long,
    val processingDurationMs: Long = (processingEndTimeMs - processingStartTimeMs).coerceAtLeast(0L),
    val analyzerState: String = "IDLE",
    val appliedRoi: RoiRegion? = null,
    val confidence: Float? = null,
    val detectedEvents: List<EsportsDetectedEvent> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) {
    val hasDetections: Boolean get() = detectedEvents.isNotEmpty()

    companion object {
        fun empty(
            input: FrameAnalysisInput,
            startTimeMs: Long = System.currentTimeMillis(),
            state: String = "IDLE"
        ): FrameAnalysisResult {
            val now = System.currentTimeMillis()
            return FrameAnalysisResult(
                sequenceNumber = input.sequenceNumber,
                frameTimestampMs = input.timestampMs,
                processingStartTimeMs = startTimeMs,
                processingEndTimeMs = now,
                processingDurationMs = (now - startTimeMs).coerceAtLeast(0L),
                analyzerState = state,
                appliedRoi = input.appliedRoi,
                confidence = null,
                detectedEvents = emptyList()
            )
        }
    }
}
