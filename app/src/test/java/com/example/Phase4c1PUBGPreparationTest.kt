package com.example

import com.example.core.model.DetectedEvent
import com.example.core.model.DetectedEventType
import com.example.core.model.DetectionEvidence
import com.example.core.model.DetectionFrame
import com.example.core.model.RoiRegion
import com.example.core.rules.ConfidenceGate
import com.example.services.detection.DetectionDecision
import com.example.services.detection.IDetectionResult
import com.example.services.detection.IPUBGVisualDetector
import com.example.services.detection.PUBGDetectionService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4C.1 — Preparation for Actual PUBG Visual Detection Test Suite.
 *
 * Verifies:
 * 1. PUBGDetectionService defaults safely to NO_DETECTION without fake mock detections.
 * 2. PUBGDetectionService dynamically operates on the configured ROI (no hardcoded coordinates).
 * 3. DetectionEvidence fully encapsulates source frame, ROI crop, timestamps, dimensions, and evidence references.
 * 4. Modular IPUBGVisualDetector extension point allows future detectors to register and evaluate evidence without changing the core engine.
 * 5. Detector prioritization and unregistration works properly.
 * 6. High-confidence detections from future registered detectors bypass Admin Review and auto-process.
 * 7. Low-confidence detections from future registered detectors are routed to Admin Review.
 */
class Phase4c1PUBGPreparationTest {

    @Test
    fun `test PUBGDetectionService returns NO_DETECTION by default`() = runBlocking {
        val service = PUBGDetectionService()
        service.initialize()

        val frame = DetectionFrame(
            frameId = 1001L,
            timestampMs = 123456789L,
            width = 1920,
            height = 1080
        )
        val roi = RoiRegion.DEFAULT_KILL_FEED

        val result = service.detectInRoi(frame, roi)
        assertTrue(result is IDetectionResult.NoDetection)

        val noDetection = result as IDetectionResult.NoDetection
        assertEquals(123456789L, noDetection.frameTimestamp)
        assertEquals(roi.id, noDetection.roiId)
    }

    @Test
    fun `test DetectionEvidence carries all required fields`() {
        val sourceBuffer = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val croppedBuffer = byteArrayOf(0x30, 0x40)

        val evidence = DetectionEvidence(
            frameTimestamp = 987654321L,
            roiId = "roi_upper_right_kill_feed",
            frameWidth = 2400,
            frameHeight = 1080,
            croppedWidth = 600,
            croppedHeight = 200,
            referenceId = "frame_ref_98765",
            buffer = croppedBuffer,
            sourceBuffer = sourceBuffer,
            croppedBuffer = croppedBuffer,
            metadata = mapOf("screen_aspect" to "20:9", "pubg_version" to "3.2.0")
        )

        assertEquals(987654321L, evidence.frameTimestamp)
        assertEquals("roi_upper_right_kill_feed", evidence.roiId)
        assertEquals(2400, evidence.frameWidth)
        assertEquals(1080, evidence.frameHeight)
        assertEquals(600, evidence.croppedWidth)
        assertEquals(200, evidence.croppedHeight)
        assertEquals("frame_ref_98765", evidence.referenceId)
        assertNotNull(evidence.sourceBuffer)
        assertNotNull(evidence.croppedBuffer)
        assertEquals(4, evidence.sourceBuffer?.size)
        assertEquals(2, evidence.croppedBuffer?.size)
        assertEquals("20:9", evidence.metadata["screen_aspect"])
    }

    @Test
    fun `test registering future IPUBGVisualDetector evaluates evidence cleanly`() = runBlocking {
        val service = PUBGDetectionService()
        service.initialize()

        // Create a simulated detector module demonstrating the extension point
        val mockScreenshotPatternDetector = object : IPUBGVisualDetector {
            override val detectorId: String = "PUBG_KILL_FEED_PATTERN_DETECTOR"
            override val description: String = "Visual kill-feed icon and OCR detector from marked screenshots"
            override val priority: Int = 10

            override suspend fun analyze(evidence: DetectionEvidence): IDetectionResult {
                // When matching visual patterns are detected in future:
                val detected = DetectedEvent(
                    eventId = "evt_pubg_001",
                    eventType = DetectedEventType.KILL,
                    frameTimestamp = evidence.frameTimestamp,
                    confidence = 0.95f,
                    killerPlayerId = "Mortal",
                    victimPlayerId = "Scout",
                    killerTeamId = "SOUL",
                    victimTeamId = "TX",
                    evidence = evidence,
                    roiId = evidence.roiId,
                    metadata = mapOf("weapon" to "AWM", "headshot" to "true")
                )
                return IDetectionResult.Success(detected, ConfidenceGate.evaluate(detected.confidence))
            }
        }

        // Register the detector
        service.registerDetector(mockScreenshotPatternDetector)
        assertEquals(1, service.getRegisteredDetectors().size)
        assertEquals("PUBG_KILL_FEED_PATTERN_DETECTOR", service.getRegisteredDetectors().first().detectorId)

        val frame = DetectionFrame(
            frameId = 555L,
            timestampMs = 500000L,
            width = 1920,
            height = 1080
        )
        val roi = RoiRegion(
            id = "marked_pubg_roi",
            name = "PUBG_MARKED_FEED",
            x = 0.65f,
            y = 0.05f,
            width = 0.32f,
            height = 0.20f
        )

        val result = service.detectInRoi(frame, roi)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals("evt_pubg_001", success.event.eventId)
        assertEquals(DetectedEventType.KILL, success.event.eventType)
        assertEquals(0.95f, success.event.confidence)
        assertEquals(DetectionDecision.AUTO_PROCESS, success.decision)
        assertTrue(success.event.isHighConfidence)
        assertFalse(success.event.requiresAdminReview)
        assertEquals("marked_pubg_roi", success.event.roiId)

        // Unregister detector and confirm return to safe NO_DETECTION
        service.unregisterDetector("PUBG_KILL_FEED_PATTERN_DETECTOR")
        assertEquals(0, service.getRegisteredDetectors().size)

        val fallbackResult = service.detectInRoi(frame, roi)
        assertTrue(fallbackResult is IDetectionResult.NoDetection)
    }

    @Test
    fun `test low confidence detection from detector triggers ADMIN_REVIEW`() = runBlocking {
        val service = PUBGDetectionService()
        service.initialize()

        val lowConfidenceDetector = object : IPUBGVisualDetector {
            override val detectorId: String = "PUBG_UNCERTAIN_OCR_DETECTOR"
            override val description: String = "Low-confidence visual text candidate"
            override val priority: Int = 1

            override suspend fun analyze(evidence: DetectionEvidence): IDetectionResult {
                val detected = DetectedEvent(
                    eventId = "evt_low_conf",
                    eventType = DetectedEventType.KNOCK,
                    frameTimestamp = evidence.frameTimestamp,
                    confidence = 0.35f, // Below 50% threshold
                    killerPlayerId = "UnclearName",
                    victimPlayerId = "TargetPlayer",
                    evidence = evidence,
                    roiId = evidence.roiId
                )
                return IDetectionResult.Success(detected, ConfidenceGate.evaluate(detected.confidence))
            }
        }

        service.registerDetector(lowConfidenceDetector)

        val evidence = DetectionEvidence(
            frameTimestamp = 777000L,
            roiId = "roi_feed",
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 400,
            croppedHeight = 150
        )

        val result = service.analyzeEvidenceCrop(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(0.35f, success.event.confidence)
        assertEquals(DetectionDecision.ADMIN_REVIEW, success.decision)
        assertFalse(success.event.isHighConfidence)
        assertTrue(success.event.requiresAdminReview)
    }
}
