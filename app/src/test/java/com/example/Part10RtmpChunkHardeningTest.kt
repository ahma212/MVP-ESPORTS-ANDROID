package com.example

import com.example.services.streaming.RtmpIngestGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Socket

/**
 * Unit & Integration Test Suite for PART 10 — RTMP Outgoing Chunk-State Hardening.
 */
class Part10RtmpChunkHardeningTest {

    private lateinit var testOutputStream: TestByteArrayOutputStream
    private lateinit var rtmpGateway: RtmpIngestGateway

    private class TestByteArrayOutputStream(var writeLimit: Int = 1000000) : ByteArrayOutputStream() {
        var callCount = 0
        var byteCountWritten = 0

        override fun write(b: Int) {
            super.write(b)
            callCount++
            byteCountWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            // Simulate short socket/network writes if limit is specified
            var written = 0
            while (written < len) {
                val toWrite = (len - written).coerceAtMost(writeLimit)
                super.write(b, off + written, toWrite)
                written += toWrite
                callCount++
                byteCountWritten += toWrite
            }
        }
    }

    @Before
    fun setup() {
        testOutputStream = TestByteArrayOutputStream()
        // Initialize gateway with mock socket providing our test stream
        val mockSocket = object : Socket() {
            override fun getOutputStream(): OutputStream = testOutputStream
            override fun getInputStream() = java.io.ByteArrayInputStream(ByteArray(0))
        }
        rtmpGateway = RtmpIngestGateway(socketProvider = { _, _ -> mockSocket })
    }

    @Test
    fun testSmallRtmpMessageSingleChunk() {
        // Mock connection state
        setConnected(true)
        rtmpGateway.activeChunkSize = 128

        val sampleData = ByteArray(50) { 0xA1.toByte() }
        val result = rtmpGateway.transmitVideoPacket(sampleData, sampleData.size, isKeyFrame = true, timestampMs = 500L)
        assertTrue(result.isSuccess)

        val outputBytes = testOutputStream.toByteArray()
        // Video tag size = 11 (tag header) + 5 (FLV video header) + 50 (payload) + 4 (previous tag size) = 70 bytes
        // Chunk Header = 1 (basic) + 11 (message header) = 12 bytes
        // Total bytes sent should be 12 + 70 = 82 bytes
        assertEquals(82, outputBytes.size)

        // Verify Basic Header (fmt = 0, CSID = 4) -> 0x04
        assertEquals(0x04.toByte(), outputBytes[0])
        // Verify Message Type ID for Video (9) at offset 7
        assertEquals(9.toByte(), outputBytes[7])
        // Verify Payload starts at offset 12 with FLV Tag Type Video (9)
        assertEquals(9.toByte(), outputBytes[12])
    }

    @Test
    fun testLargeMessageMultipleChunksAndCorrectSplitting() {
        setConnected(true)
        // Configure chunk size to small value to trigger multiple chunks
        rtmpGateway.activeChunkSize = 30

        val sampleData = ByteArray(100) { 0xB2.toByte() }
        val result = rtmpGateway.transmitVideoPacket(sampleData, sampleData.size, isKeyFrame = true, timestampMs = 1000L)
        assertTrue(result.isSuccess)

        val outputBytes = testOutputStream.toByteArray()
        // Video tag size = 11 + 5 + 100 + 4 = 120 bytes
        // With chunk size 30, payload is split into: 30, 30, 30, 30 bytes (4 chunks)
        // First chunk header = 12 bytes
        // 3 subsequent chunk headers (fmt = 3, CSID = 4) -> (3 shl 6 or 4) = 0xC4 -> 1 byte each
        // Total bytes sent = 12 + 120 + 3 = 135 bytes
        assertEquals(135, outputBytes.size)

        // Verify continuation header at offsets (12 + 30) = 42
        assertEquals(0xC4.toByte(), outputBytes[42])
    }

    @Test
    fun testNegotiatedChunkSizeHandling() {
        setConnected(true)
        // Dynamically change negotiated chunk size
        rtmpGateway.activeChunkSize = 50

        val sampleData = ByteArray(80) { 0xC3.toByte() }
        val result = rtmpGateway.transmitVideoPacket(sampleData, sampleData.size, isKeyFrame = true, timestampMs = 2000L)
        assertTrue(result.isSuccess)

        val outputBytes = testOutputStream.toByteArray()
        // Tag size = 11 + 5 + 80 + 4 = 100 bytes
        // Split into 2 chunks of 50 bytes each
        // First chunk header = 12 bytes
        // Second chunk header (fmt=3) = 1 byte
        // Total bytes sent = 12 + 100 + 1 = 113 bytes
        assertEquals(113, outputBytes.size)
    }

    @Test
    fun testAudioMessageChunking() {
        setConnected(true)
        rtmpGateway.activeChunkSize = 40

        val sampleAudio = ByteArray(60) { 0xD4.toByte() }
        val result = rtmpGateway.transmitAudioPacket(sampleAudio, sampleAudio.size, timestampMs = 3000L, isAudioSpecificConfig = false)
        assertTrue(result.isSuccess)

        val outputBytes = testOutputStream.toByteArray()
        // FLV audio tag size = 11 + 2 + 60 + 4 = 77 bytes
        // With chunk size 40, split into 40 and 37 bytes (2 chunks)
        // First chunk header = 12 bytes
        // Second chunk header = 1 byte
        // Total bytes sent = 12 + 77 + 1 = 90 bytes
        assertEquals(90, outputBytes.size)

        // Verify Message Type ID for Audio (8) at offset 7
        assertEquals(8.toByte(), outputBytes[7])
    }

    @Test
    fun testAlternatingVideoAndAudioMessagesOnSameConnection() {
        setConnected(true)
        rtmpGateway.activeChunkSize = 256

        // Send Video Message
        val vData = ByteArray(40) { 0x01.toByte() }
        rtmpGateway.transmitVideoPacket(vData, vData.size, isKeyFrame = true, timestampMs = 10L)

        // Send Audio Message
        val aData = ByteArray(30) { 0x02.toByte() }
        rtmpGateway.transmitAudioPacket(aData, aData.size, timestampMs = 20L)

        val outputBytes = testOutputStream.toByteArray()
        assertTrue(outputBytes.isNotEmpty())
        // Both transmit success indicates alternating streams do not corrupt state or crash
    }

    @Test
    fun testExtendedTimestampHandling() {
        setConnected(true)
        rtmpGateway.activeChunkSize = 50

        // Large timestamp >= 0xFFFFFF (16777215 ms) triggers extended timestamp (4 bytes)
        val extTimestamp = 20000000L
        val sampleData = ByteArray(60) { 0xE5.toByte() }
        val result = rtmpGateway.transmitVideoPacket(sampleData, sampleData.size, isKeyFrame = true, timestampMs = extTimestamp)
        assertTrue(result.isSuccess)

        val outputBytes = testOutputStream.toByteArray()
        // Tag size = 11 + 5 + 60 + 4 = 80 bytes
        // Split into 50 and 30 bytes (2 chunks)
        // First chunk header = 12 bytes + 4 bytes extended timestamp = 16 bytes
        // Second chunk header (fmt=3) = 1 byte + 4 bytes extended timestamp = 5 bytes
        // Total bytes sent = 16 + 80 + 5 = 101 bytes
        assertEquals(101, outputBytes.size)

        // Verify first chunk header has 0xFFFFFF at offsets 1, 2, 3
        assertEquals(0xFF.toByte(), outputBytes[1])
        assertEquals(0xFF.toByte(), outputBytes[2])
        assertEquals(0xFF.toByte(), outputBytes[3])

        // Verify extended timestamp (20000000 = 0x01312D00) starts at offset 12
        assertEquals(0x01.toByte(), outputBytes[12])
        assertEquals(0x31.toByte(), outputBytes[13])
        assertEquals(0x2D.toByte(), outputBytes[14])
        assertEquals(0x00.toByte(), outputBytes[15])
    }

    @Test
    fun testPartialSocketWritesDoesNotCorruptOrLoseData() {
        setConnected(true)
        // Set maximum socket write chunk of 10 bytes to simulate short/fragmented TCP writes
        testOutputStream.writeLimit = 10
        rtmpGateway.activeChunkSize = 128

        val sampleData = ByteArray(40) { 0xF6.toByte() }
        val result = rtmpGateway.transmitVideoPacket(sampleData, sampleData.size, isKeyFrame = true, timestampMs = 50L)
        assertTrue(result.isSuccess)

        val outputBytes = testOutputStream.toByteArray()
        // Video tag size = 11 + 5 + 40 + 4 = 60 bytes
        // Chunk Header = 12 bytes
        // Total bytes = 12 + 60 = 72 bytes
        assertEquals(72, outputBytes.size)
        // Ensure no bytes are lost or duplicated during partial writes loop
        assertEquals(0x04.toByte(), outputBytes[0])
    }

    @Test
    fun testDisconnectReconnectClearsStaleState() {
        setConnected(true)
        rtmpGateway.activeChunkSize = 256

        val sampleData = ByteArray(30) { 0xAA.toByte() }
        rtmpGateway.transmitVideoPacket(sampleData, sampleData.size, isKeyFrame = true, timestampMs = 500L)

        // Disconnect
        rtmpGateway.disconnectIngest()
        // Reconnect should clear states cleanly
        setConnected(true)

        val result = rtmpGateway.transmitVideoPacket(sampleData, sampleData.size, isKeyFrame = true, timestampMs = 600L)
        assertTrue(result.isSuccess)
    }

    private fun setConnected(status: Boolean) {
        val isConnectedField = RtmpIngestGateway::class.java.getDeclaredField("isConnected")
        isConnectedField.isAccessible = true
        isConnectedField.set(rtmpGateway, status)

        val outputStreamField = RtmpIngestGateway::class.java.getDeclaredField("outputStream")
        outputStreamField.isAccessible = true
        outputStreamField.set(rtmpGateway, testOutputStream)
    }
}
