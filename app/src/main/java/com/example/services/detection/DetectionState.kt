package com.example.services.detection

/**
 * State of the AI/CV Detection service.
 */
sealed class DetectionState {
    object Off : DetectionState()
    object Initializing : DetectionState()
    data class Ready(val modelName: String = "PUBG_CV_MODEL_UNLOADED") : DetectionState()
    data class Analyzing(val framesProcessed: Long, val fps: Float) : DetectionState()
    data class Error(val reason: String) : DetectionState()
}
