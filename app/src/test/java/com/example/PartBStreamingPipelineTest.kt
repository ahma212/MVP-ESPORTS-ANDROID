package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.core.model.DetectionFrame
import com.example.services.composition.BroadcastVideoCompositor
import com.example.services.composition.ComposedBroadcastFrame
import com.example.services.streaming.BroadcastPipelineMetrics
import com.example.services.streaming.BroadcastStreamingPipeline
import com.example.services.streaming.EncodedVideoFrame
import com.example.services.streaming.IYouTubeIngest
import com.example.services.streaming.MediaCodecVideoEncoder
import com.example.services.streaming.RtmpIngestGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PartBStreamingPipelineTest {

    private fun createTestBitmap(width: Int = 1280, height: Int = 720, color: Int = Color.BLUE): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }


    private fun createMockRtmpInputStream(): InputStream {
        val mockInput = java.io.ByteArrayOutputStream()
        // Handshake S0, S1, S2 (1 + 1536 + 1536 bytes)
        mockInput.write(ByteArray(3073) { 0x03.toByte() })
        
        // Connect response (Transaction 1.0)
        mockInput.write(byteArrayOf(0x03))
        mockInput.write(byteArrayOf(0,0,0, 0,0,21, 20, 0,0,0,0))
        mockInput.write(byteArrayOf(0x02, 0x00, 0x07))
        mockInput.write("_result".toByteArray())
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(63, -16, 0, 0, 0, 0, 0, 0)) // 1.0
        mockInput.write(byteArrayOf(0x05, 0x05))

        // CreateStream response (Transaction 2.0)
        mockInput.write(byteArrayOf(0x03))
        mockInput.write(byteArrayOf(0,0,0, 0,0,21, 20, 0,0,0,0))
        mockInput.write(byteArrayOf(0x02, 0x00, 0x07))
        mockInput.write("_result".toByteArray())
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(64, 0, 0, 0, 0, 0, 0, 0)) // 2.0
        mockInput.write(byteArrayOf(0x05))
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(63, -16, 0, 0, 0, 0, 0, 0)) // 1.0

        // Publish response (Transaction 3.0)
        mockInput.write(byteArrayOf(0x03))
        mockInput.write(byteArrayOf(0,0,0, 0,0,29, 20, 0,0,0,0))
        mockInput.write(byteArrayOf(0x02, 0x00, 0x08))
        mockInput.write("onStatus".toByteArray())
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(64, 8, 0, 0, 0, 0, 0, 0)) // 3.0
        mockInput.write(byteArrayOf(0x05, 0x05))
        return mockInput.toByteArray().inputStream()
    }

    @Test
    fun testMediaCodecVideoEncoderLifecycleAndConfiguration() {
        val encoder = MediaCodecVideoEncoder()
        assertFalse("Encoder should be idle initially", encoder.isRunning)

        val configRes = encoder.configureEncoder(width = 1280, height = 720, bitrate = 2500000, fps = 30)
        assertTrue("configureEncoder should succeed", configRes.isSuccess)
        assertTrue("Encoder should be running after configure", encoder.isRunning)
        assertEquals(1280, encoder.currentWidth)
        assertEquals(720, encoder.currentHeight)
        assertEquals(2500000, encoder.currentBitrate)
        assertEquals(30, encoder.currentFps)

        encoder.stopEncoder()
        assertFalse("Encoder should not be running after stop", encoder.isRunning)
    }

    @Test
    fun testMediaCodecVideoEncoderFrameEncodingAndH264Output() {
        val encoder = MediaCodecVideoEncoder()
        val configRes = encoder.configureEncoder(width = 640, height = 360, bitrate = 1000000, fps = 30)
        // Robolectric's ShadowMediaCodec pretends to configure successfully
        assertTrue("configureEncoder should succeed in Robolectric simulation", configRes.isSuccess)
        assertTrue("Encoder should be running", encoder.isRunning)
        
        val bitmap = createTestBitmap(640, 360, Color.RED)
        val encodeRes = encoder.encodeFrame(bitmap, presentationTimeUs = 1000000L)
        assertTrue("encodeFrame succeeds returning Result", encodeRes.isSuccess)
        assertTrue("But encoded output should be empty because shadow codec does not actually compress", encodeRes.getOrNull()!!.isEmpty())
    }

    @Test
    fun testMediaCodecMonotonicTimestamps() {
        val encoder = MediaCodecVideoEncoder()
        encoder.configureEncoder(width = 640, height = 360, bitrate = 1000000, fps = 30)

        val bitmap = createTestBitmap(640, 360, Color.GREEN)

        // 1st frame with PTS 1000
        encoder.encodeFrame(bitmap, presentationTimeUs = 1000L)
        val pts1 = encoder.lastPresentationTimeUs
        assertEquals(1000L, pts1)

        // 2nd frame with backwards/stale PTS 500 (should be automatically adjusted forward)
        encoder.encodeFrame(bitmap, presentationTimeUs = 500L)
        val pts2 = encoder.lastPresentationTimeUs
        assertTrue("PTS should strictly monotonically increase", pts2 > pts1)

        encoder.stopEncoder()
    }

    @Test
    fun testRtmpIngestGatewayFlvTagPacketization() {
        val gateway = RtmpIngestGateway()

        val sampleH264 = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x10, 0x20, 0x30)
        val isKeyFrame = true
        val timestampMs = 12345L

        val flvPacket = gateway.packageAsFlvVideoTag(
            h264Data = sampleH264,
            length = sampleH264.size,
            isKeyFrame = isKeyFrame,
            timestampMs = timestampMs
        )

        assertNotNull(flvPacket)
        // FLV Tag Header: 11 bytes + 5 bytes AVC header + 8 bytes payload + 4 bytes prev tag size = 28 bytes
        assertEquals(28, flvPacket.size)

        // Tag Type: 0x09 (Video)
        assertEquals(0x09.toByte(), flvPacket[0])

        // Frame Type & Codec: 0x17 for keyframe AVC
        assertEquals(0x17.toByte(), flvPacket[11])

        // AVC NALU packet type: 0x01
        assertEquals(0x01.toByte(), flvPacket[12])

        // Verify payload was copied at offset 16
        for (i in sampleH264.indices) {
            assertEquals(sampleH264[i], flvPacket[16 + i])
        }

        // Test non-keyframe (inter frame)
        val interFlv = gateway.packageAsFlvVideoTag(
            h264Data = sampleH264,
            length = sampleH264.size,
            isKeyFrame = false,
            timestampMs = timestampMs
        )
        // Frame Type & Codec: 0x27 for inter frame AVC
        assertEquals(0x27.toByte(), interFlv[11])
    }

    @Test
    fun testRtmpIngestGatewayTransmissionWithMockSocket() {
        
        val recordedOutput = ByteArrayOutputStream()
        
                // Build a mock RTMP input stream to satisfy the handshake and connect
        val mockInput = ByteArrayOutputStream()
        
        // Handshake S0, S1, S2 (1 + 1536 + 1536 bytes)
        mockInput.write(ByteArray(3073) { 0x03.toByte() })
        
        // Mock AMF0 _result for connect (Transaction ID 1.0)
        mockInput.write(byteArrayOf(0x03))
        mockInput.write(byteArrayOf(0,0,0, 0,0,21, 20, 0,0,0,0))
        mockInput.write(byteArrayOf(0x02, 0x00, 0x07))
        mockInput.write("_result".toByteArray())
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(63, -16, 0, 0, 0, 0, 0, 0)) // 1.0
        mockInput.write(byteArrayOf(0x05, 0x05)) // two nulls

        // Mock AMF0 _result for createStream (Transaction ID 2.0)
        mockInput.write(byteArrayOf(0x03))
        mockInput.write(byteArrayOf(0,0,0, 0,0,29, 20, 0,0,0,0))
        mockInput.write(byteArrayOf(0x02, 0x00, 0x07))
        mockInput.write("_result".toByteArray())
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(64, 0, 0, 0, 0, 0, 0, 0)) // 2.0
        mockInput.write(byteArrayOf(0x05)) // null
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(63, -16, 0, 0, 0, 0, 0, 0)) // Stream ID 1.0

        // Mock AMF0 onStatus for publish (Transaction ID 3.0)
        mockInput.write(byteArrayOf(0x03))
        mockInput.write(byteArrayOf(0,0,0, 0,0,22, 20, 0,0,0,0))
        mockInput.write(byteArrayOf(0x02, 0x00, 0x08))
        mockInput.write("onStatus".toByteArray())
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(64, 8, 0, 0, 0, 0, 0, 0)) // 3.0
        mockInput.write(byteArrayOf(0x05, 0x05))

        val mockSocket = object : Socket() {
            override fun getOutputStream(): OutputStream = recordedOutput
            override fun getInputStream(): InputStream = mockInput.toByteArray().inputStream()
            override fun close() {}
        }


        val gateway = RtmpIngestGateway(socketProvider = { _, _ -> mockSocket })
        val connectRes = gateway.connectIngest("rtmp://a.rtmp.youtube.com/live2/test-key-123")
        assertTrue("connectIngest should succeed with mock socket: ${connectRes.exceptionOrNull()?.message}", connectRes.isSuccess)
        assertTrue("Gateway should report connected", gateway.isConnected)

        val sampleH264 = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x0A, 0x0B)
        val transmitRes = gateway.transmitVideoPacket(
            data = sampleH264,
            length = sampleH264.size,
            isKeyFrame = true,
            timestampMs = 500L
        )

        assertTrue("transmitVideoPacket should succeed", transmitRes.isSuccess)
        assertEquals(1L, gateway.totalPacketsSent)
        assertTrue(gateway.totalBytesSent > 0)
        assertTrue("Recorded output should contain bytes", recordedOutput.size() > 0)

        gateway.disconnectIngest()
        assertFalse("Gateway should report disconnected", gateway.isConnected)
    }

    @Test
    fun testFullPipelineComposedFrameToEncoderToTransport() = runBlocking {
        
        val recordedOutput = ByteArrayOutputStream()
        
                // Build a mock RTMP input stream to satisfy the handshake and connect
        val mockInput = ByteArrayOutputStream()
        
        // Handshake S0, S1, S2 (1 + 1536 + 1536 bytes)
        mockInput.write(ByteArray(3073) { 0x03.toByte() })
        
        // Mock AMF0 _result for connect (Transaction ID 1.0)
        mockInput.write(byteArrayOf(0x03))
        mockInput.write(byteArrayOf(0,0,0, 0,0,21, 20, 0,0,0,0))
        mockInput.write(byteArrayOf(0x02, 0x00, 0x07))
        mockInput.write("_result".toByteArray())
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(63, -16, 0, 0, 0, 0, 0, 0)) // 1.0
        mockInput.write(byteArrayOf(0x05, 0x05)) // two nulls

        // Mock AMF0 _result for createStream (Transaction ID 2.0)
        mockInput.write(byteArrayOf(0x03))
        mockInput.write(byteArrayOf(0,0,0, 0,0,29, 20, 0,0,0,0))
        mockInput.write(byteArrayOf(0x02, 0x00, 0x07))
        mockInput.write("_result".toByteArray())
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(64, 0, 0, 0, 0, 0, 0, 0)) // 2.0
        mockInput.write(byteArrayOf(0x05)) // null
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(63, -16, 0, 0, 0, 0, 0, 0)) // Stream ID 1.0

        // Mock AMF0 onStatus for publish (Transaction ID 3.0)
        mockInput.write(byteArrayOf(0x03))
        mockInput.write(byteArrayOf(0,0,0, 0,0,22, 20, 0,0,0,0))
        mockInput.write(byteArrayOf(0x02, 0x00, 0x08))
        mockInput.write("onStatus".toByteArray())
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(64, 8, 0, 0, 0, 0, 0, 0)) // 3.0
        mockInput.write(byteArrayOf(0x05, 0x05))

        val mockSocket = object : Socket() {
            override fun getOutputStream(): OutputStream = recordedOutput
            override fun getInputStream(): InputStream = mockInput.toByteArray().inputStream()
            override fun close() {}
        }


        val encoder = MediaCodecVideoEncoder()
        val gateway = RtmpIngestGateway(socketProvider = { _, _ -> mockSocket })
        val compositor = BroadcastVideoCompositor.getInstance()

        val pipeline = BroadcastStreamingPipeline(
            compositor = compositor,
            encoder = encoder,
            ingestGateway = gateway
        )

        // Start pipeline
        val startRes = pipeline.startPipeline(
            rtmpUrl = "rtmp://a.rtmp.youtube.com/live2/test-stream",
            width = 640,
            height = 360,
            bitrate = 1000000,
            fps = 30
        )
        assertTrue("Pipeline start should succeed: ${startRes.exceptionOrNull()?.message}", startRes.isSuccess)
        assertTrue("Encoder should be running", encoder.isRunning)
        assertTrue("Gateway should be connected", gateway.isConnected)

        // Create a real ComposedBroadcastFrame
        val baseBmp = createTestBitmap(640, 360, Color.BLACK)
        val composedFrame = ComposedBroadcastFrame(
            frameId = 1L,
            timestampMs = 1000L,
            width = 640,
            height = 360,
            bitmap = baseBmp,
            activeLayers = listOf("OVERALL_STANDING", "BOTTOM_TICKER"),
            compositionTimeMs = 5L
        )

        // Process frame through the entire pipeline:
        // ComposedBroadcastFrame -> MediaCodecVideoEncoder -> Encoded Video Frames -> RtmpIngestGateway
        val processRes = pipeline.processFrame(composedFrame)
        assertTrue("processFrame should succeed", processRes.isSuccess)

        val encodedBytes = processRes.getOrNull()!!
        // In Robolectric, ShadowMediaCodec doesn't actually produce output. 
        // We assert that it behaves safely and doesn't crash, but produces empty bytes without fallback.
        assertTrue("Encoded H.264 bytes should be empty in Robolectric simulation", encodedBytes.isEmpty())
        
        // Let's manually inject a frame to test the gateway transport layer
        val sampleH264 = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x0A, 0x0B)
        gateway.transmitVideoPacket(sampleH264, sampleH264.size, true, 100L, false)

        assertTrue("Packets should have been transmitted to transport", gateway.totalPacketsSent > 0)
        assertTrue("Bytes should have been transmitted to transport", gateway.totalBytesSent > 0)
        assertTrue("Socket output stream should receive FLV packet", recordedOutput.size() > 0)

        // Check pipeline metrics
        val metrics = pipeline.pipelineMetrics.value
        assertTrue("Pipeline should be active", metrics.isActive)
        assertTrue("Frames composed count should be >= 1", metrics.framesComposed >= 1L)
        // framesEncoded and bytesEncoded will be 0 because our Robolectric encoder returned 0 bytes
        assertEquals("Frames encoded count should be 0", 0L, metrics.framesEncoded)
        assertEquals("Bytes encoded count should be 0", 0L, metrics.bytesEncoded)
        // Packets transmitted will be 0 because we didn't go through the pipeline flow for the manual transmission
        assertEquals("Packets transmitted should be 0", 0L, metrics.packetsTransmitted)

        // Clean stop
        pipeline.stopPipeline()
        assertFalse("Pipeline should not be active after stop", pipeline.pipelineMetrics.value.isActive)
        assertFalse("Encoder should be stopped", encoder.isRunning)
        assertFalse("Gateway should be disconnected", gateway.isConnected)
    }

    @Test
    fun testPipelineGracefulErrorHandlingOnUnconfiguredFrame() {
        val encoder = MediaCodecVideoEncoder()
        assertFalse(encoder.isRunning)

        val dummyBmp = createTestBitmap(320, 240)
        val res = encoder.encodeFrame(dummyBmp, 0L)
        assertFalse("Encoding without configure should fail gracefully", res.isSuccess)
        assertNotNull("Should contain error message", res.exceptionOrNull())
    }
}
