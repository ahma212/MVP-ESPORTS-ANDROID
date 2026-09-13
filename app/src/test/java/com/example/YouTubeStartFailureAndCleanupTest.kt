package com.example

import android.content.Context
import com.example.services.audio.InternalAudioCaptureManager
import com.example.services.audio.LocalMusicPlayerManager
import com.example.services.audio.MicrophoneCommentaryManager
import com.example.services.streaming.BroadcastConfig
import com.example.services.streaming.BroadcastController
import com.example.services.streaming.BroadcastLifecycleState
import com.example.services.streaming.BroadcastStreamingPipeline
import com.example.services.streaming.IYouTubeIngest
import com.example.services.streaming.IVideoEncoder
import com.example.services.streaming.LiveSessionManager
import com.example.services.streaming.LiveSessionState
import com.example.services.streaming.MediaCodecAudioEncoder
import com.example.services.streaming.MediaCodecVideoEncoder
import com.example.services.streaming.RtmpIngestGateway
import com.example.services.streaming.StreamingState
import com.example.services.streaming.YouTubeLiveService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YouTubeStartFailureAndCleanupTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun testYouTubeServiceRejectsUnauthenticatedRequests() = runBlocking {
        val ytService = YouTubeLiveService.getInstance(context)
        ytService.disconnect()

        assertFalse("Should not be authorized initially", ytService.isAuthorized.value)

        // Attempt broadcast creation without auth
        val bRes = ytService.createLiveBroadcast("Title", "Description", "public")
        assertTrue("Broadcast creation without auth must fail", bRes.isFailure)
        assertEquals(LiveSessionState.ERROR, ytService.sessionState.value)
        assertNotNull(ytService.errorMessage.value)

        // Attempt stream creation without auth
        val sRes = ytService.createLiveStream("Title")
        assertTrue("Stream creation without auth must fail", sRes.isFailure)
        assertEquals(LiveSessionState.ERROR, ytService.sessionState.value)

        // Attempt bind without auth
        val bindRes = ytService.bindBroadcastToStream("b1", "s1")
        assertTrue("Stream bind without auth must fail", bindRes.isFailure)
        assertEquals(LiveSessionState.ERROR, ytService.sessionState.value)

        // Attempt start broadcast without auth
        val startRes = ytService.startBroadcast("b1")
        assertTrue("Start broadcast without auth must fail", startRes.isFailure)
        assertEquals(LiveSessionState.ERROR, ytService.sessionState.value)
    }

    @Test
    fun testPipelineRollbackOnRtmpConnectionFailure() = runBlocking {
        val pipeline = BroadcastStreamingPipeline.getInstance(context)
        pipeline.stopPipeline()

        // Attempt to connect to an unreachable/invalid RTMP endpoint
        val startRes = pipeline.startPipeline(
            context = context,
            rtmpUrl = "rtmp://127.0.0.1:9999/invalid/app"
        )

        assertTrue("Pipeline start to invalid RTMP should fail", startRes.isFailure)
        assertFalse("Pipeline should not be marked active", pipeline.pipelineMetrics.value.isActive)
        assertTrue(
            "Streaming state should be StreamError or NotConnected",
            pipeline.streamingState.value is StreamingState.StreamError ||
            pipeline.streamingState.value is StreamingState.NotConnected
        )
    }

    @Test
    fun testPipelineRollbackOnFailingEncoder() = runBlocking {
        val failingEncoder = object : IVideoEncoder {
            override val isRunning: Boolean = false
            override val encodedFrames: SharedFlow<com.example.services.streaming.EncodedVideoFrame>? = null
            override fun configureEncoder(width: Int, height: Int, bitrate: Int, fps: Int): Result<Unit> {
                return Result.failure(IllegalStateException("Hardware codec unavailable"))
            }
            override fun encodeFrame(bitmap: android.graphics.Bitmap, presentationTimeUs: Long): Result<ByteArray> {
                return Result.failure(IllegalStateException("Encoder not configured"))
            }
            override fun stopEncoder() {}
        }

        val pipeline = BroadcastStreamingPipeline(
            encoder = failingEncoder,
            ingestGateway = RtmpIngestGateway()
        )

        val result = pipeline.startPipeline(context = context, rtmpUrl = "rtmp://mock/live/key")
        assertTrue("Pipeline must fail when encoder configure fails", result.isFailure)
        assertFalse(pipeline.pipelineMetrics.value.isActive)
        assertTrue(pipeline.streamingState.value is StreamingState.StreamError)
    }

    @Test
    fun testPipelineStartLiveStreamRollbackWhenTransitionFails() = runBlocking {
        val ytService = YouTubeLiveService.getInstance(context)
        ytService.disconnect() // Unauthenticated -> transition will fail

        val pipeline = BroadcastStreamingPipeline.getInstance(context)
        pipeline.stopPipeline()

        val startLiveRes = pipeline.startLiveStream(context, "dummy_broadcast_id")
        assertTrue("startLiveStream without valid RTMP and auth must fail", startLiveRes.isFailure)
        assertFalse("Pipeline must not be active after failure", pipeline.pipelineMetrics.value.isActive)
        assertNotEquals("Should not be StreamingLive", StreamingState.StreamingLive::class, pipeline.streamingState.value::class)
    }

    @Test
    fun testDeterministicAndIdempotentCleanupSequence() = runBlocking {
        val pipeline = BroadcastStreamingPipeline.getInstance(context)
        val controller = BroadcastController.getInstance(context)
        val sessionManager = LiveSessionManager.getInstance(context)
        val gateway = RtmpIngestGateway()
        val videoEncoder = MediaCodecVideoEncoder()
        val audioEncoder = MediaCodecAudioEncoder()

        // 1. Initial stop on idle components
        val stop1 = pipeline.stopPipeline()
        assertTrue("stopPipeline must succeed even when idle", stop1.isSuccess)

        val stop2 = pipeline.stopPipeline()
        assertTrue("Subsequent stopPipeline must be idempotent and succeed", stop2.isSuccess)

        // 2. Gateway disconnect idempotency
        gateway.disconnectIngest()
        assertFalse(gateway.isConnected)
        gateway.disconnectIngest()
        assertFalse(gateway.isConnected)

        // 3. Encoder stop idempotency
        videoEncoder.stopEncoder()
        assertFalse(videoEncoder.isRunning)
        videoEncoder.stopEncoder()
        assertFalse(videoEncoder.isRunning)

        audioEncoder.stopEncoder()
        assertFalse(audioEncoder.isRunning)
        audioEncoder.stopEncoder()
        assertFalse(audioEncoder.isRunning)

        // 4. Audio managers stop idempotency
        MicrophoneCommentaryManager.stopMicrophone()
        assertFalse(MicrophoneCommentaryManager.micState.value.isRecording)
        MicrophoneCommentaryManager.stopMicrophone()
        assertFalse(MicrophoneCommentaryManager.micState.value.isRecording)

        InternalAudioCaptureManager.stopCapture()
        assertFalse(InternalAudioCaptureManager.internalAudioState.value.isCapturing)
        InternalAudioCaptureManager.stopCapture()
        assertFalse(InternalAudioCaptureManager.internalAudioState.value.isCapturing)

        LocalMusicPlayerManager.stop()
        assertFalse(LocalMusicPlayerManager.musicState.value.isPlaying)
        LocalMusicPlayerManager.stop()
        assertFalse(LocalMusicPlayerManager.musicState.value.isPlaying)

        // 5. Coordinated session cleanup
        val coordStop1 = sessionManager.stopCoordinatedSession()
        assertTrue("stopCoordinatedSession must succeed", coordStop1.isSuccess)
        val coordStop2 = sessionManager.stopCoordinatedSession()
        assertTrue("Second stopCoordinatedSession must succeed idempotently", coordStop2.isSuccess)

        // 6. BroadcastController end idempotency
        val end1 = controller.endBroadcast()
        assertTrue("endBroadcast must succeed", end1.isSuccess)
        val end2 = controller.endBroadcast()
        assertTrue("Second endBroadcast must succeed idempotently", end2.isSuccess)
        assertEquals(BroadcastLifecycleState.COMPLETED, controller.broadcastState.value)
    }
}
