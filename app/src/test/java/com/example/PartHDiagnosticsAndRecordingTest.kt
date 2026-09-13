package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.example.services.composition.ComposedBroadcastFrame
import com.example.services.streaming.BroadcastPipelineMetrics
import com.example.services.streaming.BroadcastRecordingManager
import com.example.services.streaming.BroadcastStreamingPipeline
import com.example.services.streaming.MediaCodecVideoEncoder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PartHDiagnosticsAndRecordingTest {

    private fun createTestBitmap(width: Int = 1280, height: Int = 720): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLUE)
        return bmp
    }

    @Test
    fun testDiagnosticsStateCalculationAndUptime() {
        val metrics = BroadcastPipelineMetrics(
            isActive = true,
            rtmpUrl = "rtmp://localhost/live",
            framesComposed = 100L,
            framesEncoded = 90L,
            bytesEncoded = 1024L,
            currentFps = 28.5,
            currentBitrateKbps = 2400,
            isEncoderRunning = true,
            isRtmpConnected = true,
            uptimeSeconds = 120L,
            targetFps = 30,
            audioBitrateKbps = 128,
            isAudioActive = true,
            healthStatus = "HEALTHY"
        )

        assertTrue(metrics.isActive)
        assertEquals("rtmp://localhost/live", metrics.rtmpUrl)
        assertEquals(100L, metrics.framesComposed)
        assertEquals(90L, metrics.framesEncoded)
        assertEquals(10L, metrics.framesComposed - metrics.framesEncoded) // Dropped frames
        assertEquals(28.5, metrics.currentFps, 0.01)
        assertEquals(2400, metrics.currentBitrateKbps)
        assertEquals(120L, metrics.uptimeSeconds)
        assertEquals(30, metrics.targetFps)
        assertEquals(128, metrics.audioBitrateKbps)
        assertTrue(metrics.isAudioActive)
        assertEquals("HEALTHY", metrics.healthStatus)
    }

    @Test
    fun testHealthStatusTransitions() {
        // We can test the local health status logic directly inside the pipeline.
        val context = RuntimeEnvironment.getApplication()
        val pipeline = BroadcastStreamingPipeline.getInstance(context)

        // Reflection or public access check on health calculation:
        // Default initially should be disconnected
        val initialMetrics = pipeline.pipelineMetrics.value
        assertEquals("DISCONNECTED", initialMetrics.healthStatus)
    }

    @Test
    fun testRecordingStateTransitionsAndCleanup() {
        val context = RuntimeEnvironment.getApplication()
        val recordingManager = BroadcastRecordingManager.getInstance()

        assertFalse("Recording should not be active initially", recordingManager.isRecording.value)
        assertEquals(0L, recordingManager.recordingDurationSeconds.value)
        assertNull(recordingManager.recordingError.value)

        // Start Recording
        val startRes = recordingManager.startRecording(context, width = 640, height = 360, fps = 30)
        assertTrue("Recording start should succeed", startRes.isSuccess)
        assertTrue("Recording should be active", recordingManager.isRecording.value)
        assertNotNull("File creation status should be populated", recordingManager.fileCreationStatus.value)

        // Stop Recording
        val stopRes = recordingManager.stopRecording()
        assertTrue("Recording stop should succeed", stopRes.isSuccess)
        assertFalse("Recording should be inactive after stopping", recordingManager.isRecording.value)

        // Verify resources are released cleanly and error state is clear
        assertNull(recordingManager.recordingError.value)
    }

    @Test
    fun testRecordingIndependentFromRTMP() {
        val context = RuntimeEnvironment.getApplication()
        val pipeline = BroadcastStreamingPipeline.getInstance(context)
        val recordingManager = BroadcastRecordingManager.getInstance()

        // Configure pipeline and start streaming (simulated / connected)
        val pipeRes = pipeline.startPipeline(rtmpUrl = null)
        assertTrue(pipeRes.isSuccess)

        // Start recording concurrently
        val recRes = recordingManager.startRecording(context)
        assertTrue(recRes.isSuccess)

        assertTrue(pipeline.pipelineMetrics.value.isActive)
        assertTrue(recordingManager.isRecording.value)

        // Stop recording only - pipeline must remain active!
        val stopRecRes = recordingManager.stopRecording()
        assertTrue(stopRecRes.isSuccess)

        assertFalse(recordingManager.isRecording.value)
        assertTrue("Stopping recording must NOT stop the active live RTMP broadcast", pipeline.pipelineMetrics.value.isActive)

        // Cleanup
        pipeline.stopPipeline()
    }

    @Test
    fun testRecordingErrorHandlingOnInvalidBitmap() {
        val encoder = MediaCodecVideoEncoder()
        encoder.configureEncoder(640, 360, 1000000, 30)

        val recycledBitmap = createTestBitmap(640, 360)
        recycledBitmap.recycle() // Intentionally recycle to cause exception on encode

        val result = encoder.encodeFrame(recycledBitmap, 1000L)
        assertTrue("Encoding recycled bitmap should fail gracefully", result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }
}
