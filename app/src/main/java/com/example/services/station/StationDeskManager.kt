package com.example.services.station

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.util.Log
import com.example.services.streaming.BroadcastRecordingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class StationResolution(val width: Int, val height: Int, val label: String) {
    RES_720P(1280, 720, "720p"),
    RES_1080P(1920, 1080, "1080p")
}

enum class StationFps(val fps: Int, val label: String) {
    FPS_24(24, "24 FPS"),
    FPS_30(30, "30 FPS"),
    FPS_60(60, "60 FPS")
}

enum class StationQuality(val bitrate: Int, val label: String) {
    LOW(1500000, "Low (1.5M)"),
    MEDIUM(4000000, "Medium (4.0M)"),
    HIGH(8000000, "High (8.0M)")
}

enum class SmoothnessMode(val label: String, val previewRes: String, val targetDetectFps: Float) {
    PERFORMANCE("Performance", "720p", 5.0f),
    QUALITY("Quality", "1080p", 8.0f)
}

enum class ColorPreset(val label: String) {
    NONE("None"),
    WARM("Warm"),
    COOL("Cool"),
    VINTAGE("Vintage")
}

data class ColorDeskState(
    val brightness: Float = 0.0f,     // -1.0f .. 1.0f
    val contrast: Float = 1.0f,       //  0.5f .. 2.0f
    val saturation: Float = 1.0f,     //  0.0f .. 2.0f
    val shadows: Float = 0.0f,        // -1.0f .. 1.0f
    val highlights: Float = 0.0f,     // -1.0f .. 1.0f
    val preset: ColorPreset = ColorPreset.NONE
)

data class StationState(
    val resolution: StationResolution = StationResolution.RES_720P,
    val fps: StationFps = StationFps.FPS_30,
    val quality: StationQuality = StationQuality.MEDIUM,
    val smoothnessMode: SmoothnessMode = SmoothnessMode.PERFORMANCE,
    val colorDesk: ColorDeskState = ColorDeskState(),
    val isRecording: Boolean = false,
    val recordingDurationSeconds: Long = 0L,
    val recordingFileName: String? = null,
    val recordingFilePath: String? = null,
    val isLiveActive: Boolean = false,
    val liveStatus: String = "IDLE",
    // Detection info preview
    val lastKiller: String? = null,
    val lastVictim: String? = null,
    val lastWeapon: String? = null,
    val lastConfidence: Float = 0f,
    val lastRule: String? = null,
    val isHeavy: Boolean = false,
    val autoDowngraded: Boolean = false,
    val currentFps: Int = 0
)

/**
 * Master Station Video Desk Manager.
 *
 * Central authority for:
 * - Color grading matrix computation
 * - Program output resolution, FPS, and bitrate quality
 * - Recording orchestration (decoupled from YouTube Live)
 * - Smoothness mode (Performance vs Quality)
 */
object StationDeskManager {
    private const val TAG = "StationDeskManager"

    private val _stationState = MutableStateFlow(StationState())
    val stationState: StateFlow<StationState> = _stationState.asStateFlow()

    @Volatile
    private var cachedColorFilter: ColorFilter? = null

    init {
        recomputeColorFilter()
    }

    fun setResolution(resolution: StationResolution) {
        _stationState.update { it.copy(resolution = resolution) }
    }

    fun setFps(fps: StationFps) {
        _stationState.update { it.copy(fps = fps) }
    }

    fun setQuality(quality: StationQuality) {
        _stationState.update { it.copy(quality = quality) }
    }

    fun updatePerformanceMetrics(fps: Int, isHeavy: Boolean) {
        _stationState.update { it.copy(currentFps = fps, isHeavy = isHeavy) }
    }

    fun setSmoothnessMode(mode: SmoothnessMode) {
        _stationState.update { 
            it.copy(
                smoothnessMode = mode,
                resolution = if (mode == SmoothnessMode.PERFORMANCE) StationResolution.RES_720P else StationResolution.RES_1080P,
                fps = if (mode == SmoothnessMode.PERFORMANCE) StationFps.FPS_30 else StationFps.FPS_60,
                quality = if (mode == SmoothnessMode.PERFORMANCE) StationQuality.MEDIUM else StationQuality.HIGH
            ) 
        }
    }

    fun setBrightness(brightness: Float) {
        _stationState.update { 
            it.copy(colorDesk = it.colorDesk.copy(brightness = brightness.coerceIn(-1.0f, 1.0f)))
        }
        recomputeColorFilter()
    }

    fun setContrast(contrast: Float) {
        _stationState.update { 
            it.copy(colorDesk = it.colorDesk.copy(contrast = contrast.coerceIn(0.5f, 2.0f)))
        }
        recomputeColorFilter()
    }

    fun setSaturation(saturation: Float) {
        _stationState.update { 
            it.copy(colorDesk = it.colorDesk.copy(saturation = saturation.coerceIn(0.0f, 2.0f)))
        }
        recomputeColorFilter()
    }

    fun setShadows(shadows: Float) {
        _stationState.update { 
            it.copy(colorDesk = it.colorDesk.copy(shadows = shadows.coerceIn(-1.0f, 1.0f)))
        }
        recomputeColorFilter()
    }

    fun setHighlights(highlights: Float) {
        _stationState.update { 
            it.copy(colorDesk = it.colorDesk.copy(highlights = highlights.coerceIn(-1.0f, 1.0f)))
        }
        recomputeColorFilter()
    }

    fun setColorPreset(preset: ColorPreset) {
        val updatedDesk = when (preset) {
            ColorPreset.NONE -> ColorDeskState(preset = ColorPreset.NONE)
            ColorPreset.WARM -> ColorDeskState(
                brightness = 0.05f,
                contrast = 1.05f,
                saturation = 1.15f,
                shadows = 0.05f,
                highlights = -0.05f,
                preset = ColorPreset.WARM
            )
            ColorPreset.COOL -> ColorDeskState(
                brightness = 0.0f,
                contrast = 1.08f,
                saturation = 0.95f,
                shadows = -0.05f,
                highlights = 0.05f,
                preset = ColorPreset.COOL
            )
            ColorPreset.VINTAGE -> ColorDeskState(
                brightness = 0.04f,
                contrast = 0.95f,
                saturation = 0.85f,
                shadows = 0.12f,
                highlights = -0.08f,
                preset = ColorPreset.VINTAGE
            )
        }
        _stationState.update { it.copy(colorDesk = updatedDesk) }
        recomputeColorFilter()
    }

    fun resetColorDesk() {
        _stationState.update { it.copy(colorDesk = ColorDeskState()) }
        recomputeColorFilter()
    }

    fun updateRecordingState(isRecording: Boolean, durationSeconds: Long = 0L, fileName: String? = null, filePath: String? = null) {
        _stationState.update {
            it.copy(
                isRecording = isRecording,
                recordingDurationSeconds = durationSeconds,
                recordingFileName = fileName ?: it.recordingFileName,
                recordingFilePath = filePath ?: it.recordingFilePath
            )
        }
    }

    fun updateLiveState(isLive: Boolean, status: String) {
        _stationState.update {
            it.copy(
                isLiveActive = isLive,
                liveStatus = status
            )
        }
    }

    fun updateDetectionPreview(killer: String?, victim: String?, weapon: String?, confidence: Float, rule: String?) {
        _stationState.update {
            it.copy(
                lastKiller = killer,
                lastVictim = victim,
                lastWeapon = weapon,
                lastConfidence = confidence,
                lastRule = rule
            )
        }
    }

    fun getColorFilter(): ColorFilter? {
        if (_stationState.value.isHeavy) {
            return null // Priority: Drop color filters if phone load is heavy to protect framerate
        }
        return cachedColorFilter
    }

    private fun recomputeColorFilter() {
        val desk = _stationState.value.colorDesk
        val isDefault = desk.preset == ColorPreset.NONE &&
                desk.brightness == 0f &&
                desk.contrast == 1f &&
                desk.saturation == 1f &&
                desk.shadows == 0f &&
                desk.highlights == 0f

        if (isDefault) {
            cachedColorFilter = null
            return
        }

        val cm = ColorMatrix()

        // 1. Saturation
        if (desk.saturation != 1.0f) {
            val satMatrix = ColorMatrix()
            satMatrix.setSaturation(desk.saturation)
            cm.postConcat(satMatrix)
        }

        // 2. Contrast & Brightness & Highlights/Shadows
        val scale = desk.contrast
        val translate = (1f - scale) * 128f + (desk.brightness * 128f) + (desk.highlights * 40f) + (desk.shadows * 30f)
        val cbMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(cbMatrix)

        // 3. Preset color shifts
        when (desk.preset) {
            ColorPreset.WARM -> {
                val warmMatrix = ColorMatrix(floatArrayOf(
                    1.08f, 0f, 0f, 0f, 10f,
                    0f, 1.02f, 0f, 0f, 4f,
                    0f, 0f, 0.90f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(warmMatrix)
            }
            ColorPreset.COOL -> {
                val coolMatrix = ColorMatrix(floatArrayOf(
                    0.92f, 0f, 0f, 0f, -8f,
                    0f, 0.98f, 0f, 0f, 0f,
                    0f, 0f, 1.10f, 0f, 14f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(coolMatrix)
            }
            ColorPreset.VINTAGE -> {
                val vintageMatrix = ColorMatrix(floatArrayOf(
                    1.05f, 0.05f, 0.02f, 0f, 15f,
                    0.02f, 0.95f, 0.02f, 0f, 8f,
                    0.01f, 0.02f, 0.82f, 0f, -5f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(vintageMatrix)
            }
            ColorPreset.NONE -> {}
        }

        cachedColorFilter = ColorMatrixColorFilter(cm)
    }

    fun startRecordingFromStation(context: Context): Result<Unit> {
        val state = _stationState.value
        val res = BroadcastRecordingManager.getInstance().startRecording(
            context = context,
            width = state.resolution.width,
            height = state.resolution.height,
            fps = state.fps.fps,
            bitrate = state.quality.bitrate
        )
        return res
    }

    fun stopRecordingFromStation(): Result<Unit> {
        return BroadcastRecordingManager.getInstance().stopRecording()
    }

    fun triggerAutoDowngrade() {
        _stationState.update {
            if (it.smoothnessMode == SmoothnessMode.QUALITY) {
                // First downgrade: 1080p60 -> 1080p30
                if (it.fps == StationFps.FPS_60) {
                    it.copy(fps = StationFps.FPS_30, autoDowngraded = true)
                } else {
                    // Second downgrade: 1080p30 -> 720p30 PERFORMANCE
                    it.copy(
                        smoothnessMode = SmoothnessMode.PERFORMANCE,
                        resolution = StationResolution.RES_720P,
                        fps = StationFps.FPS_30,
                        quality = StationQuality.MEDIUM,
                        autoDowngraded = true
                    )
                }
            } else {
                it
            }
        }
    }
}
