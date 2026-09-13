package com.example.services.analysis

import com.example.core.model.DetectionFrame
import com.example.core.model.FrameAnalysisInput
import com.example.services.video.VideoSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Platform-independent Frame Sampler with configurable rate and bounded backpressure.
 * Prevents memory accumulation and drops stale frames so the analysis engine processes
 * the most recent frame without lag.
 */
class FrameSampler(
    initialTargetFps: Double = 5.0,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job())
) {
    private val _targetSampleFps = MutableStateFlow(initialTargetFps.coerceIn(1.0, 60.0))
    val targetSampleFps: StateFlow<Double> = _targetSampleFps.asStateFlow()

    private val _framesReceived = MutableStateFlow(0L)
    val framesReceived: StateFlow<Long> = _framesReceived.asStateFlow()

    private val _framesSampled = MutableStateFlow(0L)
    val framesSampled: StateFlow<Long> = _framesSampled.asStateFlow()

    private val _currentAnalysisFps = MutableStateFlow(0.0)
    val currentAnalysisFps: StateFlow<Double> = _currentAnalysisFps.asStateFlow()

    // Bounded flow with single-item capacity dropping oldest on backpressure to guarantee zero queue accumulation
    private val _sampledFrameFlow = MutableSharedFlow<FrameAnalysisInput>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val sampledFrameFlow: SharedFlow<FrameAnalysisInput> = _sampledFrameFlow.asSharedFlow()

    private val _latestSampledFrame = MutableStateFlow<FrameAnalysisInput?>(null)
    val latestSampledFrame: StateFlow<FrameAnalysisInput?> = _latestSampledFrame.asStateFlow()

    private var lastSampleTimestampMs: Long = 0L
    private var fpsWindowStartMs: Long = System.currentTimeMillis()
    private var samplesInWindow: Int = 0

    /**
     * Updates target sample rate in frames per second.
     */
    fun setTargetSampleFps(fps: Double) {
        _targetSampleFps.value = fps.coerceIn(0.5, 60.0)
    }

    /**
     * Alias for setTargetSampleFps for compatibility.
     */
    fun setTargetFps(fps: Double) {
        setTargetSampleFps(fps)
    }

    private var totalFramesReceivedCounter = 0L

    /**
     * Ingests an incoming hardware/video frame from IVideoInputService.
     * Evaluates sampling interval and drops frames between target timestamps.
     */
    fun onIncomingFrame(
        frame: DetectionFrame,
        sourceType: VideoSourceType = VideoSourceType.ANDROID_MEDIA_PROJECTION
    ) {
        totalFramesReceivedCounter++

        val now = System.currentTimeMillis()
        val targetIntervalMs = (1000.0 / _targetSampleFps.value).toLong()

        if (now - lastSampleTimestampMs >= targetIntervalMs || lastSampleTimestampMs == 0L) {
            lastSampleTimestampMs = now
            val sampledCount = _framesSampled.value + 1
            _framesSampled.value = sampledCount
            _framesReceived.value = totalFramesReceivedCounter

            samplesInWindow++
            if (now - fpsWindowStartMs >= 1000L) {
                val elapsed = (now - fpsWindowStartMs).toDouble()
                _currentAnalysisFps.value = (samplesInWindow * 1000.0) / elapsed
                samplesInWindow = 0
                fpsWindowStartMs = now
            }

            val analysisInput = FrameAnalysisInput.fromDetectionFrame(frame, sourceType)
            _latestSampledFrame.value = analysisInput
            _sampledFrameFlow.tryEmit(analysisInput)
        }
    }

    /**
     * Resets counters when capture stops or changes source.
     */
    fun reset() {
        totalFramesReceivedCounter = 0L
        _framesReceived.value = 0L
        _framesSampled.value = 0L
        _currentAnalysisFps.value = 0.0
        lastSampleTimestampMs = 0L
        fpsWindowStartMs = System.currentTimeMillis()
        samplesInWindow = 0
        _latestSampledFrame.value = null
    }
}
