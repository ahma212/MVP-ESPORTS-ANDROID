package com.example

import com.example.core.model.DetectedEvent
import com.example.core.model.DetectedEventType
import com.example.core.model.DetectionEvidence
import com.example.core.model.DetectionFrame
import com.example.core.model.RoiRegion
import com.example.core.rules.ConfidenceGate
import com.example.services.detection.DetectionDecision
import com.example.services.detection.DetectionEngineFoundation
import com.example.services.detection.IDetectionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4C — Detection Engine Foundation Test Suite.
 *
 * Validates the platform-independent detection contracts, confidence rules,
 * evidence pipeline, and event routing architecture.
 */
class Phase4cDetectionEngineTest {

    // 1. confidence 0.50 -> AUTO_PROCESS
    @Test
    fun `test confidence 0_50 produces AUTO_PROCESS`() {
        val decision = ConfidenceGate.evaluate(0.50f)
        assertEquals(DetectionDecision.AUTO_PROCESS, decision)
        assertTrue(ConfidenceGate.shouldAutoProcess(0.50f))
        assertFalse(ConfidenceGate.requiresAdminReview(0.50f))

        val event = DetectedEvent(
            eventId = "evt_50",
            eventType = DetectedEventType.KNOCK,
            frameTimestamp = 1000L,
            confidence = 0.50f,
            roiId = "roi_kill_feed"
        )
        assertEquals(DetectionDecision.AUTO_PROCESS, ConfidenceGate.evaluateEvent(event))
        assertTrue(event.isHighConfidence)
        assertFalse(event.requiresAdminReview)
    }

    // 2. confidence 0.94 -> AUTO_PROCESS
    @Test
    fun `test confidence 0_94 produces AUTO_PROCESS`() {
        val decision = ConfidenceGate.evaluate(0.94f)
        assertEquals(DetectionDecision.AUTO_PROCESS, decision)
        assertTrue(ConfidenceGate.shouldAutoProcess(0.94f))
        assertFalse(ConfidenceGate.requiresAdminReview(0.94f))

        val event = DetectedEvent(
            eventId = "evt_94",
            eventType = DetectedEventType.KILL,
            frameTimestamp = 2000L,
            confidence = 0.94f,
            killerPlayerId = "SniperKing",
            victimPlayerId = "Runner99",
            roiId = "roi_kill_feed"
        )
        assertEquals(DetectionDecision.AUTO_PROCESS, ConfidenceGate.evaluateEvent(event))
        assertTrue(event.isHighConfidence)
        assertFalse(event.requiresAdminReview)
    }

    // 3. confidence 0.49 -> ADMIN_REVIEW
    @Test
    fun `test confidence 0_49 produces ADMIN_REVIEW`() {
        val decision = ConfidenceGate.evaluate(0.49f)
        assertEquals(DetectionDecision.ADMIN_REVIEW, decision)
        assertFalse(ConfidenceGate.shouldAutoProcess(0.49f))
        assertTrue(ConfidenceGate.requiresAdminReview(0.49f))

        val event = DetectedEvent(
            eventId = "evt_49",
            eventType = DetectedEventType.ELIMINATION,
            frameTimestamp = 3000L,
            confidence = 0.49f,
            roiId = "roi_kill_feed"
        )
        assertEquals(DetectionDecision.ADMIN_REVIEW, ConfidenceGate.evaluateEvent(event))
        assertFalse(event.isHighConfidence)
        assertTrue(event.requiresAdminReview)
    }

    // 4. confidence 0.10 -> ADMIN_REVIEW
    @Test
    fun `test confidence 0_10 produces ADMIN_REVIEW`() {
        val decision = ConfidenceGate.evaluate(0.10f)
        assertEquals(DetectionDecision.ADMIN_REVIEW, decision)
        assertFalse(ConfidenceGate.shouldAutoProcess(0.10f))
        assertTrue(ConfidenceGate.requiresAdminReview(0.10f))

        val event = DetectedEvent(
            eventId = "evt_10",
            eventType = DetectedEventType.REVIVE,
            frameTimestamp = 4000L,
            confidence = 0.10f,
            roiId = "roi_kill_feed"
        )
        assertEquals(DetectionDecision.ADMIN_REVIEW, ConfidenceGate.evaluateEvent(event))
        assertFalse(event.isHighConfidence)
        assertTrue(event.requiresAdminReview)
    }

    // 5. NO_DETECTION produces no event
    @Test
    fun `test NO_DETECTION produces no event and maintains safe foundation`() = runBlocking {
        val engine = DetectionEngineFoundation()
        engine.initialize()

        val frame = DetectionFrame(
            frameId = 101L,
            timestampMs = 5000L,
            width = 1920,
            height = 1080
        )
        val roi = RoiRegion.DEFAULT_KILL_FEED

        val result = engine.detectInRoi(frame, roi)
        assertTrue(result is IDetectionResult.NoDetection)

        val noDetection = result as IDetectionResult.NoDetection
        assertEquals(5000L, noDetection.frameTimestamp)
        assertEquals(roi.id, noDetection.roiId)
    }

    // 6. detected event preserves ROI ID
    @Test
    fun `test detected event preserves ROI ID`() = runBlocking {
        val engine = DetectionEngineFoundation()
        engine.initialize()

        val customRoi = RoiRegion(
            id = "custom_kill_feed_v2",
            name = "KILL_FEED_UPPER_RIGHT",
            x = 0.70f,
            y = 0.05f,
            width = 0.28f,
            height = 0.18f
        )

        val evidence = DetectionEvidence(
            frameTimestamp = 6000L,
            roiId = customRoi.id,
            frameWidth = 1920,
            frameHeight = 1080,
            croppedWidth = 537,
            croppedHeight = 194,
            referenceId = "frame_6000"
        )

        // Inject simulated detection handler for unit testing
        engine.setDetectionHandler { ev ->
            val detected = DetectedEvent(
                eventId = "evt_custom_roi",
                eventType = DetectedEventType.KILL,
                frameTimestamp = ev.frameTimestamp,
                confidence = 0.88f,
                killerPlayerId = "ProPlayer",
                victimPlayerId = "NoobPlayer",
                evidence = ev,
                roiId = ev.roiId
            )
            IDetectionResult.Success(detected, ConfidenceGate.evaluate(detected.confidence))
        }

        val result = engine.analyzeEvidenceCrop(evidence)
        assertTrue(result is IDetectionResult.Success)

        val success = result as IDetectionResult.Success
        assertEquals(customRoi.id, success.event.roiId)
        assertEquals(customRoi.id, success.event.evidence?.roiId)
        assertEquals(DetectionDecision.AUTO_PROCESS, success.decision)
    }

    // 7. detected event preserves evidence reference
    @Test
    fun `test detected event preserves evidence reference`() {
        val sampleBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val evidence = DetectionEvidence(
            frameTimestamp = 7000L,
            roiId = "roi_feed_01",
            frameWidth = 1080,
            frameHeight = 2400,
            croppedWidth = 324,
            croppedHeight = 360,
            referenceId = "ref_frame_7000",
            buffer = sampleBytes,
            metadata = mapOf("source" to "SCREEN_CAPTURE", "weapon" to "M416")
        )

        val event = DetectedEvent(
            eventId = "evt_evidence_test",
            eventType = DetectedEventType.KILL,
            timestamp = 7050L,
            frameTimestamp = 7000L,
            confidence = 0.75f,
            killerPlayerId = "Hunter",
            victimPlayerId = "Prey",
            killerTeamId = "TEAM_A",
            victimTeamId = "TEAM_B",
            evidence = evidence,
            roiId = "roi_feed_01",
            source = "SCREEN_CAPTURE",
            metadata = mapOf("weapon" to "M416")
        )

        assertNotNull(event.evidence)
        assertEquals(7000L, event.evidence?.frameTimestamp)
        assertEquals("ref_frame_7000", event.evidence?.referenceId)
        assertEquals("roi_feed_01", event.evidence?.roiId)
        assertEquals(1080, event.evidence?.frameWidth)
        assertEquals(2400, event.evidence?.frameHeight)
        assertEquals(324, event.evidence?.croppedWidth)
        assertEquals(360, event.evidence?.croppedHeight)
        assertEquals(4, event.evidence?.buffer?.size)
        assertEquals("M416", event.evidence?.metadata?.get("weapon"))
    }

    // 8. all required event types are supported
    @Test
    fun `test all required event types are supported`() {
        val requiredTypes = listOf(
            DetectedEventType.KNOCK,
            DetectedEventType.KILL,
            DetectedEventType.ELIMINATION,
            DetectedEventType.REVIVE,
            DetectedEventType.POINTS_ADJUSTMENT,
            DetectedEventType.KILL_ADJUSTMENT,
            DetectedEventType.PLACEMENT,
            DetectedEventType.WINNER,
            DetectedEventType.OTHER
        )

        assertEquals(9, DetectedEventType.values().size)
        for (type in requiredTypes) {
            val event = DetectedEvent(
                eventId = "test_${type.name}",
                eventType = type,
                frameTimestamp = 8000L,
                confidence = 0.60f,
                roiId = "roi_test"
            )
            assertEquals(type, event.eventType)
        }
    }
}
