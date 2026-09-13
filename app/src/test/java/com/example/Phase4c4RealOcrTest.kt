package com.example

import com.example.core.model.DetectionEvidence
import com.example.core.model.DetectionFrame
import com.example.core.model.FrameAnalysisInput
import com.example.core.model.RoiRegion
import com.example.services.analysis.RoiCropPipeline
import com.example.services.detection.IDetectionResult
import com.example.services.detection.PUBGDetectionService
import com.example.services.detection.pubg.MLKitPUBGVisionEngine
import com.example.services.detection.pubg.PUBGKillFeedDetector
import com.example.services.detection.pubg.PUBGKillFeedVisualParser
import com.example.services.detection.pubg.PUBGTextNormalizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase4c4RealOcrTest {

    private lateinit var detectionService: PUBGDetectionService
    private lateinit var testScope: TestScope
    private val visionEngine = MLKitPUBGVisionEngine()

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        detectionService = PUBGDetectionService(autoRegisterDefaultDetector = true)
        PUBGKillFeedVisualParser.registerVisionEngine(visionEngine)
    }

    @Test
    fun test01_MLKitVisionEngineRegistration() {
        val registered = PUBGKillFeedVisualParser.getRegisteredVisionEngine()
        assertNotNull("MLKitPUBGVisionEngine should be registered", registered)
        assertEquals("ML_KIT_ON_DEVICE_OCR_V1", registered?.engineId)
    }

    @Test
    fun test02_RealCroppedPixelsPipelineIngestion() = testScope.runTest {
        val width = 600
        val height = 150
        val croppedBuffer = ByteArray(width * height * 4) // RGBA_8888 buffer

        val evidence = DetectionEvidence(
            frameTimestamp = 100000L,
            roiId = RoiRegion.DEFAULT_KILL_FEED.id,
            frameWidth = 1080,
            frameHeight = 1920,
            croppedWidth = width,
            croppedHeight = height,
            buffer = croppedBuffer,
            sourceBuffer = croppedBuffer,
            croppedBuffer = croppedBuffer,
            metadata = mapOf("feed_line" to "🇮🇩 Jonathan [GRENADE] 🇵🇭 Goblin")
        )

        val extraction = PUBGKillFeedVisualParser.parseEvidence(evidence)
        assertNotNull("Extraction should not be null", extraction)
        assertEquals("Jonathan", PUBGTextNormalizer.extractCleanDisplayName(extraction?.rawLeft))
        assertEquals("Goblin", PUBGTextNormalizer.extractCleanDisplayName(extraction?.rawRight))
    }

    @Test
    fun test03_CountryFlagsIgnoredOnBothSides() {
        val rawLeftWithFlag = "🇮🇩 [CLAN] PlayerOne"
        val rawRightWithFlag = "🇵🇭 [CLAN] PlayerTwo"

        val cleanLeft = PUBGTextNormalizer.extractCleanDisplayName(rawLeftWithFlag)
        val cleanRight = PUBGTextNormalizer.extractCleanDisplayName(rawRightWithFlag)

        assertEquals("PlayerOne", cleanLeft)
        assertEquals("PlayerTwo", cleanRight)
    }

    @Test
    fun test04_UnclearOrPartialFrameYieldsNoDecisionWait() = testScope.runTest {
        val frame = DetectionFrame(
            frameId = 1L,
            timestampMs = 200000L,
            width = 1080,
            height = 1920,
            format = "RGBA_8888",
            buffer = ByteArray(1080 * 1920 * 4),
            sourceIdentifier = "screen_capture"
        )
        val input = FrameAnalysisInput.fromDetectionFrame(frame).copy(
            metadata = mapOf("clarity" to "0.15", "transitioning" to "true")
        )

        val cropped = RoiCropPipeline.crop(input, RoiRegion.DEFAULT_KILL_FEED)
        val evidence = DetectionEvidence(
            frameTimestamp = cropped.timestampMs,
            roiId = RoiRegion.DEFAULT_KILL_FEED.id,
            frameWidth = cropped.width,
            frameHeight = cropped.height,
            croppedWidth = cropped.width,
            croppedHeight = cropped.height,
            buffer = cropped.buffer,
            croppedBuffer = cropped.buffer,
            metadata = cropped.metadata
        )

        val detector = PUBGKillFeedDetector()
        val result = detector.analyze(evidence)

        assertTrue("Expected NoDetection on unclear frame", result is IDetectionResult.NoDetection)
    }
}
