package com.example

import com.example.core.model.DetectedEvent
import com.example.core.model.DetectedEventType
import com.example.core.model.DetectionEvidence
import com.example.core.model.DetectionFrame
import com.example.core.model.FrameAnalysisInput
import com.example.core.model.RoiRegion
import com.example.services.analysis.FrameSampler
import com.example.services.analysis.RoiCropPipeline
import com.example.services.detection.DetectionDecision
import com.example.services.detection.IDetectionResult
import com.example.services.detection.IPUBGVisualDetector
import com.example.services.detection.PUBGDetectionService
import com.example.services.detection.DetectionState
import com.example.services.detection.pubg.PUBGKillFeedDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class Part13DuplicateDetectorInvocationFixTest {

    private lateinit var detectionService: PUBGDetectionService
    private val testRoi = RoiRegion(
        id = "test_kill_feed_roi",
        name = "TEST_KILL_FEED",
        x = 0.6f,
        y = 0.05f,
        width = 0.35f,
        height = 0.15f,
        enabled = true
    )

    @Before
    fun setup() {
        // Create an isolated instance of the detection service to avoid interfering with global state
        detectionService = PUBGDetectionService(autoRegisterDefaultDetector = false)
    }

    @Test
    fun testOneSampledFrameCausesExactlyOneDetectorInvocation() = runBlocking {
        val invokeCount = AtomicInteger(0)

        // Register a spy detector that increments the count
        val spyDetector = object : IPUBGVisualDetector {
            override val detectorId = "SPY_DETECTOR"
            override val description = "Counts invocations"
            override val priority = 100

            override suspend fun analyze(evidence: DetectionEvidence): IDetectionResult {
                invokeCount.incrementAndGet()
                return IDetectionResult.NoDetection(evidence.frameTimestamp, evidence.roiId)
            }
        }

        detectionService.registerDetector(spyDetector)
        detectionService.initialize()

        // Create a single dummy frame input
        val frame = DetectionFrame(
            frameId = 1L,
            timestampMs = 1000L,
            width = 1920,
            height = 1080,
            format = "RGBA_8888",
            buffer = ByteArray(100),
            sourceIdentifier = "test_source"
        )

        val analysisInput = FrameAnalysisInput.fromDetectionFrame(frame, com.example.services.video.VideoSourceType.ANDROID_MEDIA_PROJECTION)
        val croppedInput = RoiCropPipeline.crop(analysisInput, testRoi)

        // Invoke the detection service exactly once
        detectionService.analyzeFrameInput(croppedInput)

        assertEquals("Detector must be invoked exactly once per sampled frame", 1, invokeCount.get())
    }

    @Test
    fun testRoiIsCorrectlyPassedToTheDetector() = runBlocking {
        var passedRoiId: String? = null
        var passedMetadata: Map<String, String>? = null

        val spyDetector = object : IPUBGVisualDetector {
            override val detectorId = "SPY_ROI_DETECTOR"
            override val description = "Checks ROI payload"
            override val priority = 100

            override suspend fun analyze(evidence: DetectionEvidence): IDetectionResult {
                passedRoiId = evidence.roiId
                passedMetadata = evidence.metadata
                return IDetectionResult.NoDetection(evidence.frameTimestamp, evidence.roiId)
            }
        }

        detectionService.registerDetector(spyDetector)
        detectionService.initialize()

        val frame = DetectionFrame(
            frameId = 42L,
            timestampMs = 5000L,
            width = 1920,
            height = 1080,
            format = "RGBA_8888",
            buffer = ByteArray(100),
            sourceIdentifier = "live_capture"
        )

        val analysisInput = FrameAnalysisInput.fromDetectionFrame(frame, com.example.services.video.VideoSourceType.ANDROID_MEDIA_PROJECTION)
        val croppedInput = RoiCropPipeline.crop(analysisInput, testRoi)

        detectionService.analyzeFrameInput(croppedInput)

        assertEquals("The correct ROI ID must be passed inside the Evidence", "test_kill_feed_roi", passedRoiId)
        assertNotNull(passedMetadata)
        assertNotNull(passedMetadata!!["crop_rect"])
    }

    @Test
    fun testDetectorEventsAreStillEmittedCorrectly() = runBlocking {
        val mockEvent = DetectedEvent(
            eventId = "mock_evt_1",
            eventType = DetectedEventType.KILL,
            timestamp = 1000L,
            confidence = 0.95f,
            killerPlayerId = "Mortal",
            killerTeamId = "SOUL",
            victimPlayerId = "Scout",
            victimTeamId = "OR",
            source = "test_detector",
            frameTimestamp = 1000L,
            roiId = "test_kill_feed_roi",
            metadata = mapOf("cause" to "M416")
        )

        val successDetector = object : IPUBGVisualDetector {
            override val detectorId = "SUCCESS_DETECTOR"
            override val description = "Emits event"
            override val priority = 100

            override suspend fun analyze(evidence: DetectionEvidence): IDetectionResult {
                return IDetectionResult.Success(mockEvent)
            }
        }

        detectionService.registerDetector(successDetector)
        detectionService.initialize()

        val frame = DetectionFrame(
            frameId = 1L,
            timestampMs = 1000L,
            width = 1920,
            height = 1080,
            format = "RGBA_8888",
            buffer = ByteArray(100),
            sourceIdentifier = "test"
        )

        val analysisInput = FrameAnalysisInput.fromDetectionFrame(frame, com.example.services.video.VideoSourceType.ANDROID_MEDIA_PROJECTION)
        val croppedInput = RoiCropPipeline.crop(analysisInput, testRoi)

        val job = launch(Dispatchers.Default) {
            val esportsEvent = detectionService.detectedEventsFlow.first()
            assertEquals("mock_evt_1", esportsEvent.id)
            assertEquals("Mortal", esportsEvent.killerPlayerName)
            assertEquals("Scout", esportsEvent.victimPlayerName)
            assertEquals(com.example.core.model.EsportsEventType.KILL, esportsEvent.eventType)
        }

        detectionService.analyzeFrameInput(croppedInput)
        job.join()
    }

    @Test
    fun testKnockRemainsSeparateFromKill() = runBlocking {
        val feedDetector = PUBGKillFeedDetector()
        feedDetector.reset()

        val knockEvidence = DetectionEvidence(
            frameTimestamp = 1000L,
            roiId = "roi",
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 300,
            croppedHeight = 100,
            referenceId = "f1",
            metadata = mapOf(
                "left_text" to "Mortal",
                "right_text" to "Scout",
                "cause" to "M416",
                "has_knock" to "true",
                "has_finish" to "false",
                "clarity" to "0.95"
            )
        )

        val knockResult = feedDetector.analyze(knockEvidence)
        assertTrue(knockResult is IDetectionResult.Success)
        val knockEvent = (knockResult as IDetectionResult.Success).event
        assertEquals(DetectedEventType.KNOCK, knockEvent.eventType)

        val killEvidence = DetectionEvidence(
            frameTimestamp = 1500L,
            roiId = "roi",
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 300,
            croppedHeight = 100,
            referenceId = "f2",
            metadata = mapOf(
                "left_text" to "Mortal",
                "right_text" to "Scout",
                "cause" to "M416",
                "has_knock" to "false",
                "has_finish" to "false",
                "clarity" to "0.95"
            )
        )

        val killResult = feedDetector.analyze(killEvidence)
        assertTrue(killResult is IDetectionResult.Success)
        val killEvent = (killResult as IDetectionResult.Success).event
        assertEquals(DetectedEventType.KILL, killEvent.eventType)
    }

    @Test
    fun testTemporalDeduplicationStillWorks() = runBlocking {
        val feedDetector = PUBGKillFeedDetector()
        feedDetector.reset()

        val evidence1 = DetectionEvidence(
            frameTimestamp = 1000L,
            roiId = "roi",
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 300,
            croppedHeight = 100,
            referenceId = "f1",
            metadata = mapOf(
                "left_text" to "Mortal",
                "right_text" to "Scout",
                "cause" to "M416",
                "has_knock" to "false",
                "has_finish" to "true",
                "clarity" to "0.95"
            )
        )

        // First event should succeed
        val result1 = feedDetector.analyze(evidence1)
        assertTrue(result1 is IDetectionResult.Success)

        // Immediate identical event (same killer, same victim, within temporal window) should be deduplicated
        val evidence2 = DetectionEvidence(
            frameTimestamp = 1100L,
            roiId = "roi",
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 300,
            croppedHeight = 100,
            referenceId = "f2",
            metadata = mapOf(
                "left_text" to "Mortal",
                "right_text" to "Scout",
                "cause" to "M416",
                "has_knock" to "false",
                "has_finish" to "true",
                "clarity" to "0.95"
            )
        )

        val result2 = feedDetector.analyze(evidence2)
        assertTrue(result2 is IDetectionResult.NoDetection)
    }

    @Test
    fun testDetectorLifecycleStartStopWorksCorrectly() = runBlocking {
        // Uninitialized state check or initialization hook
        val service = PUBGDetectionService(autoRegisterDefaultDetector = false)
        service.initialize()
        val readyState = service.detectionState.value
        assertTrue("Expected service state to be ready", readyState is DetectionState.Ready)

        service.shutdown()
        val offState = service.detectionState.value
        assertTrue("Expected service state to be Off after shutdown", offState is DetectionState.Off)
    }
}
