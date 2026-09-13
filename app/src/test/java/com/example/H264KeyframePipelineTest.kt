package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.services.streaming.EncodedVideoFrame
import com.example.services.streaming.MediaCodecVideoEncoder
import com.example.services.streaming.RtmpIngestGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class H264KeyframePipelineTest {

    @Test
    fun testMediaCodecVideoEncoderSingleInstanceAndConfigureCleanup() {
        val encoder = MediaCodecVideoEncoder()
        assertFalse(encoder.isRunning)

        val config1 = encoder.configureEncoder(1280, 720, 2500000, 30)
        assertTrue(config1.isSuccess)
        assertTrue(encoder.isRunning)
        assertEquals(1280, encoder.currentWidth)

        // Reconfigure should stop existing instance cleanly and start new configuration
        val config2 = encoder.configureEncoder(1920, 1080, 4500000, 60)
        assertTrue(config2.isSuccess)
        assertTrue(encoder.isRunning)
        assertEquals(1920, encoder.currentWidth)

        encoder.stopEncoder()
        assertFalse(encoder.isRunning)
    }

    @Test
    fun testMonotonicVideoPtsOrdering() {
        val encoder = MediaCodecVideoEncoder()
        encoder.configureEncoder(640, 360, 1000000, 30)

        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)

        // Encode frame 1
        encoder.encodeFrame(bitmap, presentationTimeUs = 10_000L)
        val pts1 = encoder.lastPresentationTimeUs
        assertEquals(10_000L, pts1)

        // Encode frame 2 with non-monotonic/regressed PTS
        encoder.encodeFrame(bitmap, presentationTimeUs = 5_000L)
        val pts2 = encoder.lastPresentationTimeUs
        assertTrue("PTS must strictly increase even when input regresses", pts2 > pts1)

        encoder.stopEncoder()
    }

    @Test
    fun testFlvHeaderPackagingOrderForSpsPpsAndKeyframe() {
        val gateway = RtmpIngestGateway()

        // 1. Sequence Header (SPS/PPS AVCDecoderConfigurationRecord)
        val spsPpsRecord = byteArrayOf(
            0x01, 0x64, 0x00, 0x1F, 0xFF.toByte(), 0xE1.toByte(),
            0x00, 0x0A, 0x67, 0x64, 0x00, 0x1F, 0xAC.toByte(), 0xD9.toByte(), 0x40, 0x50, 0x05, 0xBB.toByte(),
            0x01, 0x00, 0x04, 0x68, 0xEB.toByte(), 0xE3.toByte(), 0xCB.toByte()
        )

        val configFlvTag = gateway.packageAsFlvVideoTag(
            h264Data = spsPpsRecord,
            length = spsPpsRecord.size,
            isKeyFrame = true,
            timestampMs = 0L,
            isCodecConfig = true
        )

        assertNotNull(configFlvTag)
        assertEquals(0x09.toByte(), configFlvTag[0]) // TagType = Video
        assertEquals(0x17.toByte(), configFlvTag[11]) // 0x17 = Keyframe + AVC
        assertEquals(0x00.toByte(), configFlvTag[12]) // 0x00 = AVC Sequence Header

        // 2. IDR Keyframe Packet
        val idrFrame = byteArrayOf(0x00, 0x00, 0x00, 0x04, 0x65, 0x10, 0x20, 0x30)
        val idrFlvTag = gateway.packageAsFlvVideoTag(
            h264Data = idrFrame,
            length = idrFrame.size,
            isKeyFrame = true,
            timestampMs = 33L,
            isCodecConfig = false
        )

        assertNotNull(idrFlvTag)
        assertEquals(0x09.toByte(), idrFlvTag[0]) // TagType = Video
        assertEquals(0x17.toByte(), idrFlvTag[11]) // 0x17 = Keyframe + AVC
        assertEquals(0x01.toByte(), idrFlvTag[12]) // 0x01 = AVC NALU

        // 3. Inter-frame (P-frame) Packet
        val pFrame = byteArrayOf(0x00, 0x00, 0x00, 0x04, 0x61, 0x10, 0x20, 0x30)
        val pFlvTag = gateway.packageAsFlvVideoTag(
            h264Data = pFrame,
            length = pFrame.size,
            isKeyFrame = false,
            timestampMs = 66L,
            isCodecConfig = false
        )

        assertNotNull(pFlvTag)
        assertEquals(0x09.toByte(), pFlvTag[0]) // TagType = Video
        assertEquals(0x27.toByte(), pFlvTag[11]) // 0x27 = Inter-frame + AVC
        assertEquals(0x01.toByte(), pFlvTag[12]) // 0x01 = AVC NALU
    }

    @Test
    fun testEncodedVideoFrameDataClass() {
        val sampleBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65)
        val frame = EncodedVideoFrame(
            data = sampleBytes,
            presentationTimeUs = 1_000_000L,
            isKeyFrame = true,
            isCodecConfig = false,
            width = 1280,
            height = 720
        )

        assertEquals(1000L, frame.timestampMs)
        assertTrue(frame.isKeyFrame)
        assertFalse(frame.isCodecConfig)
        assertEquals(1280, frame.width)
        assertEquals(720, frame.height)
    }
}
