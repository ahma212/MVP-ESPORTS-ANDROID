package com.example

import com.example.core.model.DetectedEventType
import com.example.core.model.DetectionEvidence
import com.example.core.model.RoiRegion
import com.example.services.detection.IDetectionResult
import com.example.services.detection.PUBGDetectionService
import com.example.services.detection.pubg.PUBGKillFeedDetector
import com.example.services.detection.pubg.PUBGKillFeedLineSegmenter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit & Integration Test Suite for PART 3 — PUBG Mobile Multi-Line / Double-Line Kill Feed Detection.
 */
class MultiLineKillFeedDetectionTest {

    private lateinit var detector: PUBGKillFeedDetector
    private lateinit var detectionService: PUBGDetectionService
    private val testRoi = RoiRegion.DEFAULT_KILL_FEED

    @Before
    fun setup() {
        detector = PUBGKillFeedDetector()
        detector.reset()
        detectionService = PUBGDetectionService(autoRegisterDefaultDetector = false)
        detectionService.registerDetector(detector)
    }

    @Test
    fun testSegmentLinesSplitsMultiLineTextStringWithNewlines() {
        val evidence = DetectionEvidence(
            frameTimestamp = 1000L,
            roiId = testRoi.id,
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 1920,
            croppedHeight = 1080,
            metadata = mapOf(
                "feed_line" to "Mortal [M416] Scout\nRunner [GRENADE] Alpha"
            )
        )

        val slices = PUBGKillFeedLineSegmenter.segmentLines(evidence)
        assertEquals(2, slices.size)
        assertEquals(0, slices[0].lineIndex)
        assertEquals("Mortal [M416] Scout", slices[0].rawText)
        assertEquals(1, slices[1].lineIndex)
        assertEquals("Runner [GRENADE] Alpha", slices[1].rawText)
    }

    @Test
    fun testSegmentLinesSplitsMultilineFeedText() {
        val evidence = DetectionEvidence(
            frameTimestamp = 1000L,
            roiId = testRoi.id,
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 1920,
            croppedHeight = 1080,
            metadata = mapOf(
                "feed_line" to "Mortal [M416] Scout\nRunner [GRENADE] Alpha"
            )
        )

        val slices = PUBGKillFeedLineSegmenter.segmentLines(evidence)
        assertEquals(2, slices.size)
        assertEquals("Mortal [M416] Scout", slices[0].rawText)
        assertEquals("Runner [GRENADE] Alpha", slices[1].rawText)
    }

    @Test
    fun testDetectorAnalyzeDetectsBothRowsWhenTwoFeedLinesAppear() = runBlocking {
        val evidence = DetectionEvidence(
            frameTimestamp = 1000L,
            roiId = testRoi.id,
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 1920,
            croppedHeight = 1080,
            metadata = mapOf(
                "feed_line" to "Mortal [M416] Scout\nRunner [GRENADE] Alpha"
            )
        )

        val result = detector.analyze(evidence)
        assertTrue("Expected Success but got $result", result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(2, success.allEvents.size)

        val event0 = success.allEvents[0]
        assertEquals("Mortal", event0.killerPlayerId)
        assertEquals("Scout", event0.victimPlayerId)
        assertEquals(DetectedEventType.KILL, event0.eventType)
        assertEquals("GUN", event0.metadata["cause"])

        val event1 = success.allEvents[1]
        assertEquals("Runner", event1.killerPlayerId)
        assertEquals("Alpha", event1.victimPlayerId)
        assertEquals(DetectedEventType.KILL, event1.eventType)
        assertEquals("GRENADE", event1.metadata["cause"])
    }

    @Test
    fun testMultiLineNewlineTextStringDoesNotMergeAcrossRows() = runBlocking {
        val evidence = DetectionEvidence(
            frameTimestamp = 2000L,
            roiId = testRoi.id,
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 1920,
            croppedHeight = 1080,
            metadata = mapOf(
                "raw_ocr_text" to "Mortal [AKM] Scout\nRunner [VEHICLE] Bravo"
            )
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(2, success.allEvents.size)

        val ev0 = success.allEvents[0]
        val ev1 = success.allEvents[1]

        assertEquals("Mortal", ev0.killerPlayerId)
        assertEquals("Scout", ev0.victimPlayerId)

        assertEquals("Runner", ev1.killerPlayerId)
        assertEquals("Bravo", ev1.victimPlayerId)
        assertEquals("VEHICLE", ev1.metadata["cause"])
    }

    @Test
    fun testDuplicateSuppressionAcrossFramesForMultiLineFeed() = runBlocking {
        val evidenceFrame1 = DetectionEvidence(
            frameTimestamp = 3000L,
            roiId = testRoi.id,
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 1920,
            croppedHeight = 1080,
            metadata = mapOf(
                "feed_line" to "Mortal [M416] Scout\nRunner [GRENADE] Alpha"
            )
        )

        val result1 = detector.analyze(evidenceFrame1)
        assertTrue(result1 is IDetectionResult.Success)
        assertEquals(2, (result1 as IDetectionResult.Success).allEvents.size)

        val evidenceFrame2 = DetectionEvidence(
            frameTimestamp = 3050L,
            roiId = testRoi.id,
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 1920,
            croppedHeight = 1080,
            metadata = mapOf(
                "feed_line" to "Mortal [M416] Scout\nRunner [GRENADE] Alpha"
            )
        )

        val result2 = detector.analyze(evidenceFrame2)
        assertTrue("Expected NoDetection on duplicate frame but got $result2", result2 is IDetectionResult.NoDetection)
    }

    @Test
    fun testIncompleteRowInMultiLineFeedDoesNotCreateFakePlayer() = runBlocking {
        val evidence = DetectionEvidence(
            frameTimestamp = 4000L,
            roiId = testRoi.id,
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 1920,
            croppedHeight = 1080,
            metadata = mapOf(
                "feed_line" to "Mortal [M416] Scout\nUnreadable -> "
            )
        )

        val result = detector.analyze(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(1, success.allEvents.size)
        assertEquals("Mortal", success.allEvents[0].killerPlayerId)
        assertEquals("Scout", success.allEvents[0].victimPlayerId)
    }

    @Test
    fun testPUBGDetectionServiceEmitsAllEventsOnMultiLinePass() = runBlocking {
        val evidence = DetectionEvidence(
            frameTimestamp = 5000L,
            roiId = testRoi.id,
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 1920,
            croppedHeight = 1080,
            metadata = mapOf(
                "feed_line" to "Player1 [M416] Player2\nPlayer3 [AWM] Player4"
            )
        )

        val result = detectionService.analyzeEvidenceCrop(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(2, success.allEvents.size)
    }
}
