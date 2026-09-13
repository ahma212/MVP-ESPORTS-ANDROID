package com.example

import com.example.core.model.DetectionFrame
import com.example.core.model.FrameAnalysisInput
import com.example.core.model.PixelRect
import com.example.core.model.RoiRegion
import com.example.core.rules.ConfidenceRule
import com.example.services.analysis.FrameSampler
import com.example.services.analysis.RoiCropPipeline
import com.example.services.detection.DetectionServicePlaceholder
import com.example.services.roi.InMemoryRoiConfigurationService
import com.example.services.video.VideoSourceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase4bFoundationTest {

    @Test
    fun `test normalized RoiRegion calculations and clamping`() {
        val roi = RoiRegion(
            id = "test_roi",
            name = "TEST_BOX",
            x = 0.5f,
            y = 0.25f,
            width = 0.25f,
            height = 0.5f,
            enabled = true
        )

        // Test 1080x1920
        val pixelRect1080 = roi.toPixelRect(1080, 1920)
        assertEquals(540, pixelRect1080.left)
        assertEquals(480, pixelRect1080.top)
        assertEquals(270, pixelRect1080.width)
        assertEquals(960, pixelRect1080.height)

        // Test 1920x1080 (landscape)
        val pixelRectLandscape = roi.toPixelRect(1920, 1080)
        assertEquals(960, pixelRectLandscape.left)
        assertEquals(270, pixelRectLandscape.top)
        assertEquals(480, pixelRectLandscape.width)
        assertEquals(540, pixelRectLandscape.height)

        // Test clamping of out-of-bounds coordinates
        val clamped = roi.clamped(newX = 0.9f, newY = 0.8f, newW = 0.3f, newH = 0.4f)
        assertTrue(clamped.x + clamped.width <= 1.0f)
        assertTrue(clamped.y + clamped.height <= 1.0f)
    }

    @Test
    fun `test RoiCropPipeline extracts correct sub-region bytes safely`() {
        val width = 4
        val height = 4
        val bytesPerPixel = 4
        val buffer = ByteArray(width * height * bytesPerPixel) { it.toByte() }

        val input = FrameAnalysisInput(
            sequenceNumber = 1L,
            timestampMs = 1000L,
            width = width,
            height = height,
            format = "RGBA_8888",
            buffer = buffer,
            sourceType = VideoSourceType.ANDROID_MEDIA_PROJECTION
        )

        val roi = RoiRegion(
            id = "roi_crop",
            name = "CROP_CENTER",
            x = 0.25f, // pixel x = 1
            y = 0.25f, // pixel y = 1
            width = 0.5f, // pixel w = 2
            height = 0.5f, // pixel h = 2
            enabled = true
        )

        val cropped = RoiCropPipeline.crop(input, roi)
        assertTrue(cropped.isCropped)
        assertEquals(2, cropped.width)
        assertEquals(2, cropped.height)
        assertNotNull(cropped.buffer)
        assertEquals(2 * 2 * 4, cropped.buffer!!.size)
    }

    @Test
    fun `test FrameSampler configurable rate and bounded buffering`() {
        val sampler = FrameSampler(initialTargetFps = 10.0)
        assertEquals(10.0, sampler.targetSampleFps.value, 0.001)

        sampler.setTargetSampleFps(5.0)
        assertEquals(5.0, sampler.targetSampleFps.value, 0.001)

        val dummyFrame = DetectionFrame(
            frameId = 100L,
            timestampMs = 5000L,
            width = 1920,
            height = 1080,
            format = "RGBA_8888"
        )

        sampler.onIncomingFrame(dummyFrame, VideoSourceType.ANDROID_MEDIA_PROJECTION)
        assertEquals(1L, sampler.framesReceived.value)
        assertEquals(1L, sampler.framesSampled.value)

        val latest = sampler.latestSampledFrame.value
        assertNotNull(latest)
        assertEquals(100L, latest?.sequenceNumber)
        assertEquals(1920, latest?.width)
        assertEquals(1080, latest?.height)
    }

    @Test
    fun `test InMemoryRoiConfigurationService operations`() = runTest {
        val roiService = InMemoryRoiConfigurationService()
        val initialRois = roiService.rois.value
        assertTrue(initialRois.isNotEmpty())
        assertTrue(initialRois.any { it.name == "KILL_FEED" })

        // Save new ROI
        val customRoi = RoiRegion(
            id = "custom_1",
            name = "CUSTOM_RADAR",
            x = 0.1f,
            y = 0.1f,
            width = 0.2f,
            height = 0.2f,
            enabled = true
        )
        roiService.saveRoi(customRoi)
        assertTrue(roiService.rois.value.any { it.id == "custom_1" })

        // Toggle enabled
        roiService.toggleRoiEnabled("custom_1")
        assertFalse(roiService.rois.value.first { it.id == "custom_1" }.enabled)

        // Delete ROI
        roiService.deleteRoi("custom_1")
        assertFalse(roiService.rois.value.any { it.id == "custom_1" })

        // Reset
        roiService.resetToDefaults()
        assertEquals(3, roiService.rois.value.size)
    }

    @Test
    fun `test DetectionServicePlaceholder emits no fake detections or confidence`() = runTest {
        val detector = DetectionServicePlaceholder()
        detector.initialize()

        val input = FrameAnalysisInput(
            sequenceNumber = 42L,
            timestampMs = 123456L,
            width = 1080,
            height = 2400
        )

        val result = detector.analyzeFrameInput(input)
        assertEquals(42L, result.sequenceNumber)
        assertNull(result.confidence)
        assertFalse(result.hasDetections)
        assertTrue(result.detectedEvents.isEmpty())
    }

    @Test
    fun `test ConfidenceRule business logic`() {
        assertTrue(ConfidenceRule.shouldAutoProcess(0.50f))
        assertTrue(ConfidenceRule.shouldAutoProcess(0.85f))
        assertFalse(ConfidenceRule.shouldAutoProcess(0.49f))
        assertFalse(ConfidenceRule.shouldAutoProcess(0.10f))
    }

    @Test
    fun `test ContentScale Fit geometry calculation for portrait frame inside 16-9 viewport`() {
        // Viewport: 1920x1080 (16:9)
        val viewportW = 1920f
        val viewportH = 1080f

        // Portrait frame: 1080x2400 (aspect 9:20)
        val frameW = 1080f
        val frameH = 2400f

        // ContentScale.Fit scale
        val scale = minOf(viewportW / frameW, viewportH / frameH) // 1080 / 2400 = 0.45
        assertEquals(0.45f, scale, 0.001f)

        val displayedW = frameW * scale // 486
        val displayedH = frameH * scale // 1080
        val imgLeft = (viewportW - displayedW) / 2f // (1920 - 486) / 2 = 717
        val imgTop = (viewportH - displayedH) / 2f // 0

        assertEquals(486f, displayedW, 0.01f)
        assertEquals(1080f, displayedH, 0.01f)
        assertEquals(717f, imgLeft, 0.01f)
        assertEquals(0f, imgTop, 0.01f)

        // Map normalized ROI (x=0.5, y=0.5, w=0.2, h=0.2) to screen coordinates
        val normRoi = RoiRegion(
            id = "test_center",
            name = "CENTER",
            x = 0.5f,
            y = 0.5f,
            width = 0.2f,
            height = 0.2f
        )

        val screenBoxLeft = imgLeft + (normRoi.x * displayedW)
        val screenBoxTop = imgTop + (normRoi.y * displayedH)
        val screenBoxW = normRoi.width * displayedW
        val screenBoxH = normRoi.height * displayedH

        assertEquals(717f + 243f, screenBoxLeft, 0.01f)
        assertEquals(540f, screenBoxTop, 0.01f)
        assertEquals(97.2f, screenBoxW, 0.01f)
        assertEquals(216f, screenBoxH, 0.01f)

        // Verify inverse mapping: delta of +48.6px on X in screen coordinates equals exactly +0.1 in normalized X
        val deltaScreenX = 48.6f
        val deltaNormX = deltaScreenX / displayedW
        assertEquals(0.1f, deltaNormX, 0.001f)
    }

    @Test
    fun `test 4-Corner ROI resizing math preserves opposite anchors`() {
        val originalRoi = RoiRegion(
            id = "resize_test",
            name = "RESIZE_TEST",
            x = 0.2f,
            y = 0.2f,
            width = 0.4f,
            height = 0.4f
        )
        val displayedW = 1000f
        val displayedH = 1000f
        val imgLeft = 0f
        val imgTop = 0f

        // Current coordinates
        val curLeft = imgLeft + (originalRoi.x * displayedW) // 200
        val curTop = imgTop + (originalRoi.y * displayedH) // 200
        val curRight = curLeft + (originalRoi.width * displayedW) // 600
        val curBottom = curTop + (originalRoi.height * displayedH) // 600

        // 1. Drag Top-Left handle inwards (+50px X, +50px Y)
        val dragAmountX = 50f
        val dragAmountY = 50f
        val newLeft = (curLeft + dragAmountX).coerceIn(imgLeft, curRight - 20f) // 250
        val newTop = (curTop + dragAmountY).coerceIn(imgTop, curBottom - 20f) // 250

        val normX = ((newLeft - imgLeft) / displayedW).coerceIn(0.0f, 1.0f) // 0.25
        val normY = ((newTop - imgTop) / displayedH).coerceIn(0.0f, 1.0f) // 0.25
        val normW = ((curRight - newLeft) / displayedW).coerceIn(0.01f, 1.0f - normX) // (600 - 250)/1000 = 0.35
        val normH = ((curBottom - newTop) / displayedH).coerceIn(0.01f, 1.0f - normY) // (600 - 250)/1000 = 0.35

        assertEquals(0.25f, normX, 0.001f)
        assertEquals(0.25f, normY, 0.001f)
        assertEquals(0.35f, normW, 0.001f)
        assertEquals(0.35f, normH, 0.001f)

        // Opposite bottom-right anchor preserved: normX + normW == 0.60
        assertEquals(0.60f, normX + normW, 0.001f)
        assertEquals(0.60f, normY + normH, 0.001f)

        // 2. Drag Bottom-Right handle outwards (+100px X, +100px Y)
        val brDragX = 100f
        val brDragY = 100f
        val newRight = (curRight + brDragX).coerceIn(curLeft + 20f, imgLeft + displayedW) // 700
        val newBottom = (curBottom + brDragY).coerceIn(curTop + 20f, imgTop + displayedH) // 700

        val brNormX = ((curLeft - imgLeft) / displayedW).coerceIn(0.0f, 1.0f) // 0.20 (unchanged)
        val brNormY = ((curTop - imgTop) / displayedH).coerceIn(0.0f, 1.0f) // 0.20 (unchanged)
        val brNormW = ((newRight - curLeft) / displayedW).coerceIn(0.01f, 1.0f - brNormX) // (700-200)/1000 = 0.50
        val brNormH = ((newBottom - curTop) / displayedH).coerceIn(0.01f, 1.0f - brNormY) // (700-200)/1000 = 0.50

        assertEquals(0.20f, brNormX, 0.001f)
        assertEquals(0.20f, brNormY, 0.001f)
        assertEquals(0.50f, brNormW, 0.001f)
        assertEquals(0.50f, brNormH, 0.001f)
    }

    @Test
    fun `test real phone 1080x2400 portrait frame crop extraction with default KILL_FEED`() {
        val srcW = 1080
        val srcH = 2400
        val buffer = ByteArray(srcW * srcH * 4) { ((it % 256).toByte()) }

        val input = FrameAnalysisInput(
            sequenceNumber = 500L,
            timestampMs = 999999L,
            width = srcW,
            height = srcH,
            buffer = buffer,
            sourceType = VideoSourceType.ANDROID_MEDIA_PROJECTION
        )

        val killFeedRoi = RoiRegion.DEFAULT_KILL_FEED
        val cropped = RoiCropPipeline.crop(input, killFeedRoi)

        assertTrue(cropped.isCropped)
        val pixelRect = killFeedRoi.toPixelRect(srcW, srcH)
        assertEquals(pixelRect.width, cropped.width)
        assertEquals(pixelRect.height, cropped.height)
        assertNotNull(cropped.buffer)
        assertEquals(pixelRect.width * pixelRect.height * 4, cropped.buffer!!.size)
    }

    @Test
    fun `test 1 top-left crop`() {
        val srcW = 1000
        val srcH = 1000
        val input = FrameAnalysisInput(
            sequenceNumber = 1L, timestampMs = 1L, width = srcW, height = srcH,
            buffer = ByteArray(srcW * srcH * 4) { 1 }
        )
        val roi = RoiRegion("tl", "TOP_LEFT", 0.0f, 0.0f, 0.2f, 0.2f)
        val cropped = RoiCropPipeline.crop(input, roi)
        assertEquals(200, cropped.width)
        assertEquals(200, cropped.height)
        assertEquals(200 * 200 * 4, cropped.buffer?.size)
    }

    @Test
    fun `test 2 center crop`() {
        val srcW = 1000
        val srcH = 1000
        val input = FrameAnalysisInput(
            sequenceNumber = 2L, timestampMs = 2L, width = srcW, height = srcH,
            buffer = ByteArray(srcW * srcH * 4) { 2 }
        )
        val roi = RoiRegion("ctr", "CENTER", 0.4f, 0.4f, 0.2f, 0.2f)
        val cropped = RoiCropPipeline.crop(input, roi)
        assertEquals(200, cropped.width)
        assertEquals(200, cropped.height)
        assertEquals(200 * 200 * 4, cropped.buffer?.size)
    }

    @Test
    fun `test 3 full-frame crop`() {
        val srcW = 1080
        val srcH = 1920
        val input = FrameAnalysisInput(
            sequenceNumber = 3L, timestampMs = 3L, width = srcW, height = srcH,
            buffer = ByteArray(srcW * srcH * 4) { 3 }
        )
        val roi = RoiRegion("full", "FULL_FRAME", 0.0f, 0.0f, 1.0f, 1.0f)
        val cropped = RoiCropPipeline.crop(input, roi)
        assertEquals(1080, cropped.width)
        assertEquals(1920, cropped.height)
        assertEquals(1080 * 1920 * 4, cropped.buffer?.size)
    }

    @Test
    fun `test 4 small crop`() {
        val srcW = 1080
        val srcH = 1920
        val input = FrameAnalysisInput(
            sequenceNumber = 4L, timestampMs = 4L, width = srcW, height = srcH,
            buffer = ByteArray(srcW * srcH * 4) { 4 }
        )
        val roi = RoiRegion("small", "SMALL", 0.5f, 0.5f, 0.05f, 0.05f)
        val cropped = RoiCropPipeline.crop(input, roi)
        assertEquals(54, cropped.width)
        assertEquals(96, cropped.height)
        assertEquals(54 * 96 * 4, cropped.buffer?.size)
    }

    @Test
    fun `test 5 portrait 1080x2125 source`() {
        val srcW = 1080
        val srcH = 2125
        val input = FrameAnalysisInput(
            sequenceNumber = 5L, timestampMs = 5L, width = srcW, height = srcH,
            buffer = ByteArray(srcW * srcH * 4) { 5 }
        )
        val roi = RoiRegion("p2125", "PORTRAIT_2125", 0.1f, 0.2f, 0.8f, 0.15f)
        val cropped = RoiCropPipeline.crop(input, roi)
        assertEquals(864, cropped.width)
        assertEquals(318, cropped.height)
        assertEquals(864 * 318 * 4, cropped.buffer?.size)
    }

    @Test
    fun `test 6 ROI moved near boundaries`() {
        val srcW = 1080
        val srcH = 2400
        val input = FrameAnalysisInput(
            sequenceNumber = 6L, timestampMs = 6L, width = srcW, height = srcH,
            buffer = ByteArray(srcW * srcH * 4) { 6 }
        )
        // ROI near bottom right corner
        val roi = RoiRegion("br", "BOTTOM_RIGHT_CORNER", 0.8f, 0.9f, 0.2f, 0.1f)
        val cropped = RoiCropPipeline.crop(input, roi)
        assertEquals(216, cropped.width)
        assertEquals(240, cropped.height)
        assertEquals(216 * 240 * 4, cropped.buffer?.size)
    }

    @Test
    fun `test touch hit-testing priority corner over edge and edge over body`() {
        val displayedW = 1000f
        val displayedH = 1000f
        val imgLeft = 0f
        val imgTop = 0f
        val roi = RoiRegion("test", "TEST", 0.2f, 0.2f, 0.4f, 0.4f)

        val bLeft = imgLeft + (roi.x * displayedW) // 200
        val bTop = imgTop + (roi.y * displayedH) // 200
        val bRight = bLeft + (roi.width * displayedW) // 600
        val bBottom = bTop + (roi.height * displayedH) // 600

        val cornerHitRadius = 40f
        val edgeHitMargin = 32f

        // Helper hit-test function replicating RoiEditorCard priority
        fun hitTest(x: Float, y: Float): String {
            val dNW = kotlin.math.hypot(x - bLeft, y - bTop)
            val dNE = kotlin.math.hypot(x - bRight, y - bTop)
            val dSW = kotlin.math.hypot(x - bLeft, y - bBottom)
            val dSE = kotlin.math.hypot(x - bRight, y - bBottom)

            val minCorner = minOf(dNW, dNE, dSW, dSE)
            return if (minCorner <= cornerHitRadius) {
                when (minCorner) {
                    dNW -> "RESIZE_NW"
                    dNE -> "RESIZE_NE"
                    dSW -> "RESIZE_SW"
                    else -> "RESIZE_SE"
                }
            } else {
                val inXSpan = x in (bLeft - edgeHitMargin)..(bRight + edgeHitMargin)
                val inYSpan = y in (bTop - edgeHitMargin)..(bBottom + edgeHitMargin)

                val distTop = kotlin.math.abs(y - bTop)
                val distBottom = kotlin.math.abs(y - bBottom)
                val distLeft = kotlin.math.abs(x - bLeft)
                val distRight = kotlin.math.abs(x - bRight)

                when {
                    distTop <= edgeHitMargin && inXSpan -> "RESIZE_N"
                    distBottom <= edgeHitMargin && inXSpan -> "RESIZE_S"
                    distLeft <= edgeHitMargin && inYSpan -> "RESIZE_W"
                    distRight <= edgeHitMargin && inYSpan -> "RESIZE_E"
                    x in bLeft..bRight && y in bTop..bBottom -> "MOVE"
                    else -> "NONE"
                }
            }
        }

        // Exact corners
        assertEquals("RESIZE_NW", hitTest(200f, 200f))
        assertEquals("RESIZE_NE", hitTest(600f, 200f))
        assertEquals("RESIZE_SW", hitTest(200f, 600f))
        assertEquals("RESIZE_SE", hitTest(600f, 600f))

        // Edges
        assertEquals("RESIZE_N", hitTest(400f, 200f))
        assertEquals("RESIZE_S", hitTest(400f, 600f))
        assertEquals("RESIZE_W", hitTest(200f, 400f))
        assertEquals("RESIZE_E", hitTest(600f, 400f))

        // Body interior
        assertEquals("MOVE", hitTest(400f, 400f))
        assertEquals("MOVE", hitTest(350f, 350f))

        // Outside
        assertEquals("NONE", hitTest(50f, 50f))
        assertEquals("NONE", hitTest(800f, 800f))
    }

    @Test
    fun `test edge resize math anchors opposite edge and bounds clamping`() {
        val originalRoi = RoiRegion(
            id = "edge_test",
            name = "EDGE_TEST",
            x = 0.2f,
            y = 0.2f,
            width = 0.4f,
            height = 0.4f
        )
        val minSize = 0.04f

        // Top edge resize (N): anchor is fixedBottom = 0.60f
        val fixedBottom = originalRoi.y + originalRoi.height
        val dragY = -0.10f // drag upwards
        val desiredTop = (originalRoi.y + dragY).coerceIn(0.0f, fixedBottom - minSize)
        val newHeight = fixedBottom - desiredTop
        assertEquals(0.10f, desiredTop, 0.001f)
        assertEquals(0.50f, newHeight, 0.001f)
        assertEquals(0.60f, desiredTop + newHeight, 0.001f)

        // Right edge resize (E): anchor is fixedLeft = 0.20f
        val fixedLeft = originalRoi.x
        val dragX = 0.25f // drag rightwards
        val desiredRight = (originalRoi.x + originalRoi.width + dragX).coerceIn(fixedLeft + minSize, 1.0f)
        val newWidth = desiredRight - fixedLeft
        assertEquals(0.85f, desiredRight, 0.001f)
        assertEquals(0.65f, newWidth, 0.001f)
    }

    @Test
    fun `test landscape and portrait screen recording aspect containment`() {
        // Landscape Source 1920x1080 (16:9) inside 16:9 Viewport
        val lSrcW = 1920f
        val lSrcH = 1080f
        val lViewW = 1920f
        val lViewH = 1080f
        val lScale = minOf(lViewW / lSrcW, lViewH / lSrcH)
        val lDispW = lSrcW * lScale
        val lDispH = lSrcH * lScale
        assertEquals(1920f, lDispW, 0.01f)
        assertEquals(1080f, lDispH, 0.01f)
        val lImgLeft = (lViewW - lDispW) / 2f
        val lImgTop = (lViewH - lDispH) / 2f
        assertEquals(0f, lImgLeft, 0.01f)
        assertEquals(0f, lImgTop, 0.01f)

        // Portrait Source 1080x2400 (9:20) inside 16:10 Viewport (1600x1000)
        val pSrcW = 1080f
        val pSrcH = 2400f
        val pViewW = 1600f
        val pViewH = 1000f
        val pScale = minOf(pViewW / pSrcW, pViewH / pSrcH) // 1000 / 2400 = 0.41666f
        val pDispW = pSrcW * pScale // 450f
        val pDispH = pSrcH * pScale // 1000f
        assertEquals(450f, pDispW, 0.01f)
        assertEquals(1000f, pDispH, 0.01f)
        val pImgLeft = (pViewW - pDispW) / 2f
        val pImgTop = (pViewH - pDispH) / 2f
        assertEquals(575f, pImgLeft, 0.01f)
        assertEquals(0f, pImgTop, 0.01f)
        // Entire height visible from top to bottom without clipping
        assertEquals(pViewH, pDispH, 0.01f)
    }
}
