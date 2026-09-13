package com.example

import com.example.services.streaming.RtmpIngestGateway
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Socket

class Part12RtmpCreateStreamValidationTest {

    private lateinit var testOutputStream: ByteArrayOutputStream

    @Before
    fun setup() {
        testOutputStream = ByteArrayOutputStream()
    }

    private fun serializeRtmpCommand(
        commandName: String,
        transactionId: Double,
        arguments: List<Any?>
    ): ByteArray {
        val payloadStream = ByteArrayOutputStream()
        
        // 1. Write command name (AMF0 String: Type 0x02)
        payloadStream.write(0x02)
        val nameBytes = commandName.toByteArray(Charsets.UTF_8)
        payloadStream.write((nameBytes.size shr 8) and 0xFF)
        payloadStream.write(nameBytes.size and 0xFF)
        payloadStream.write(nameBytes)

        // 2. Write transactionId (AMF0 Number: Type 0x00)
        payloadStream.write(0x00)
        val doubleLong = java.lang.Double.doubleToLongBits(transactionId)
        for (i in 7 downTo 0) {
            payloadStream.write(((doubleLong shr (i * 8)) and 0xFF).toInt())
        }

        // 3. Write arguments
        for (arg in arguments) {
            if (arg == null) {
                payloadStream.write(0x05) // Null
            } else if (arg is Double) {
                payloadStream.write(0x00)
                val dl = java.lang.Double.doubleToLongBits(arg)
                for (i in 7 downTo 0) {
                    payloadStream.write(((dl shr (i * 8)) and 0xFF).toInt())
                }
            } else if (arg is String) {
                payloadStream.write(0x02)
                val bytes = arg.toByteArray(Charsets.UTF_8)
                payloadStream.write((bytes.size shr 8) and 0xFF)
                payloadStream.write(bytes.size and 0xFF)
                payloadStream.write(bytes)
            } else if (arg is Map<*, *>) {
                payloadStream.write(0x03) // Object
                for ((k, v) in arg) {
                    val kStr = k.toString()
                    val kBytes = kStr.toByteArray(Charsets.UTF_8)
                    payloadStream.write((kBytes.size shr 8) and 0xFF)
                    payloadStream.write(kBytes.size and 0xFF)
                    payloadStream.write(kBytes)
                    if (v == null) {
                        payloadStream.write(0x05)
                    } else if (v is String) {
                        payloadStream.write(0x02)
                        val b = v.toByteArray(Charsets.UTF_8)
                        payloadStream.write((b.size shr 8) and 0xFF)
                        payloadStream.write(b.size and 0xFF)
                        payloadStream.write(b)
                    } else if (v is Double) {
                        payloadStream.write(0x00)
                        val dl = java.lang.Double.doubleToLongBits(v)
                        for (i in 7 downTo 0) {
                            payloadStream.write(((dl shr (i * 8)) and 0xFF).toInt())
                        }
                    }
                }
                payloadStream.write(0x00)
                payloadStream.write(0x00)
                payloadStream.write(0x09)
            } else {
                payloadStream.write(0x05)
            }
        }

        val payload = payloadStream.toByteArray()
        val chunkStream = ByteArrayOutputStream()
        // RTMP Basic Header: fmt = 0, CSID = 3 -> 0x03
        chunkStream.write(0x03)
        // Message Header:
        // timestamp: 3 bytes -> 0, 0, 0
        chunkStream.write(0)
        chunkStream.write(0)
        chunkStream.write(0)
        // message length: 3 bytes
        chunkStream.write((payload.size shr 16) and 0xFF)
        chunkStream.write((payload.size shr 8) and 0xFF)
        chunkStream.write(payload.size and 0xFF)
        // message type: 1 byte -> 20 (AMF0 command)
        chunkStream.write(20)
        // stream ID: 4 bytes (little-endian) -> 0, 0, 0, 0
        chunkStream.write(0)
        chunkStream.write(0)
        chunkStream.write(0)
        chunkStream.write(0)

        chunkStream.write(payload)

        return chunkStream.toByteArray()
    }

    private fun createNormalServerMockInputStream(streamId: Double = 42.0): InputStream {
        val baos = ByteArrayOutputStream()
        // Handshake: S0 (1) + S1 (1536) + S2 (1536)
        baos.write(ByteArray(3073) { 0x05.toByte() })
        // Connect response: transaction 1.0
        baos.write(serializeRtmpCommand("_result", 1.0, listOf(null, mapOf("code" to "NetConnection.Connect.Success"))))
        // CreateStream response: transaction 2.0
        baos.write(serializeRtmpCommand("_result", 2.0, listOf(null, streamId)))
        // Publish response: transaction 0.0 or 3.0
        baos.write(serializeRtmpCommand("onStatus", 0.0, listOf(mapOf("level" to "status", "code" to "NetStream.Publish.Start"))))
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    @Test
    fun testNormalValidPublishFlowSucceeds() {
        val mockIn = createNormalServerMockInputStream(42.0)
        val mockSocket = object : Socket() {
            override fun getOutputStream() = testOutputStream
            override fun getInputStream() = mockIn
        }

        val gateway = RtmpIngestGateway { _, _ -> mockSocket }
        val result = gateway.connectIngest("rtmp://a.rtmp.youtube.com/live2/my-stream-key")

        assertTrue("Expected connection to succeed", result.isSuccess)
        assertTrue("Expected isConnected to be true", gateway.isConnected)

        // Retrieve server stream ID via reflection to verify it matches
        val field = RtmpIngestGateway::class.java.getDeclaredField("serverStreamId")
        field.isAccessible = true
        val sid = field.get(gateway) as Int
        assertEquals(42, sid)
    }

    @Test
    fun testValidCreateStreamResponseReturnsActualServerStreamId() {
        val mockIn = createNormalServerMockInputStream(99.0)
        val mockSocket = object : Socket() {
            override fun getOutputStream() = testOutputStream
            override fun getInputStream() = mockIn
        }

        val gateway = RtmpIngestGateway { _, _ -> mockSocket }
        val result = gateway.connectIngest("rtmp://a.rtmp.youtube.com/live2/my-stream-key")

        assertTrue("Expected connection to succeed", result.isSuccess)
        val field = RtmpIngestGateway::class.java.getDeclaredField("serverStreamId")
        field.isAccessible = true
        val sid = field.get(gateway) as Int
        assertEquals(99, sid)
    }

    @Test
    fun testMissingResponseFails() {
        val baos = ByteArrayOutputStream()
        // Handshake
        baos.write(ByteArray(3073) { 0x05.toByte() })
        // Connect response
        baos.write(serializeRtmpCommand("_result", 1.0, listOf(null, mapOf("code" to "NetConnection.Connect.Success"))))
        // Missing createStream response (EOF)
        val mockIn = java.io.ByteArrayInputStream(baos.toByteArray())

        val mockSocket = object : Socket() {
            override fun getOutputStream() = testOutputStream
            override fun getInputStream() = mockIn
        }

        val gateway = RtmpIngestGateway { _, _ -> mockSocket }
        val result = gateway.connectIngest("rtmp://a.rtmp.youtube.com/live2/my-stream-key")

        assertTrue("Expected connection to fail when createStream response is missing", result.isFailure)
        assertFalse("Expected isConnected to be false", gateway.isConnected)
    }

    @Test
    fun testMalformedResponseFails() {
        val baos = ByteArrayOutputStream()
        baos.write(ByteArray(3073) { 0x05.toByte() })
        baos.write(serializeRtmpCommand("_result", 1.0, listOf(null, mapOf("code" to "NetConnection.Connect.Success"))))
        // Write completely malformed packet for createStream (not valid AMF command bytes)
        baos.write(byteArrayOf(0x03, 0, 0, 0, 0, 0, 10, 20, 0, 0, 0, 0, 9, 9, 9)) // Invalid body length/type
        val mockIn = java.io.ByteArrayInputStream(baos.toByteArray())

        val mockSocket = object : Socket() {
            override fun getOutputStream() = testOutputStream
            override fun getInputStream() = mockIn
        }

        val gateway = RtmpIngestGateway { _, _ -> mockSocket }
        val result = gateway.connectIngest("rtmp://a.rtmp.youtube.com/live2/my-stream-key")

        assertTrue("Expected connection to fail on malformed createStream response", result.isFailure)
    }

    @Test
    fun testInvalidOrMissingStreamIdFails() {
        val baos = ByteArrayOutputStream()
        baos.write(ByteArray(3073) { 0x05.toByte() })
        baos.write(serializeRtmpCommand("_result", 1.0, listOf(null, mapOf("code" to "NetConnection.Connect.Success"))))
        // CreateStream response with an invalid stream ID (e.g. negative)
        baos.write(serializeRtmpCommand("_result", 2.0, listOf(null, -1.0)))
        val mockIn = java.io.ByteArrayInputStream(baos.toByteArray())

        val mockSocket = object : Socket() {
            override fun getOutputStream() = testOutputStream
            override fun getInputStream() = mockIn
        }

        val gateway = RtmpIngestGateway { _, _ -> mockSocket }
        val result = gateway.connectIngest("rtmp://a.rtmp.youtube.com/live2/my-stream-key")

        assertTrue("Expected connection to fail when stream ID is invalid/negative", result.isFailure)
    }

    @Test
    fun testWrongTransactionIdIsRejected() {
        val baos = ByteArrayOutputStream()
        baos.write(ByteArray(3073) { 0x05.toByte() })
        baos.write(serializeRtmpCommand("_result", 1.0, listOf(null, mapOf("code" to "NetConnection.Connect.Success"))))
        // CreateStream response has transaction ID 99.0 instead of 2.0
        baos.write(serializeRtmpCommand("_result", 99.0, listOf(null, 42.0)))
        val mockIn = java.io.ByteArrayInputStream(baos.toByteArray())

        val mockSocket = object : Socket() {
            override fun getOutputStream() = testOutputStream
            override fun getInputStream() = mockIn
        }

        val gateway = RtmpIngestGateway { _, _ -> mockSocket }
        val result = gateway.connectIngest("rtmp://a.rtmp.youtube.com/live2/my-stream-key")

        assertTrue("Expected connection to fail when createStream response has wrong transaction ID", result.isFailure)
    }

    @Test
    fun testNoFallbackStreamIdIsEverGenerated() {
        val baos = ByteArrayOutputStream()
        baos.write(ByteArray(3073) { 0x05.toByte() })
        baos.write(serializeRtmpCommand("_result", 1.0, listOf(null, mapOf("code" to "NetConnection.Connect.Success"))))
        // CreateStream response returns an error
        baos.write(serializeRtmpCommand("_error", 2.0, listOf(null, null)))
        val mockIn = java.io.ByteArrayInputStream(baos.toByteArray())

        val mockSocket = object : Socket() {
            override fun getOutputStream() = testOutputStream
            override fun getInputStream() = mockIn
        }

        val gateway = RtmpIngestGateway { _, _ -> mockSocket }
        val result = gateway.connectIngest("rtmp://a.rtmp.youtube.com/live2/my-stream-key")

        assertTrue("Expected connection to fail on _error response", result.isFailure)
        val field = RtmpIngestGateway::class.java.getDeclaredField("serverStreamId")
        field.isAccessible = true
        val sid = field.get(gateway) as Int
        // Verify it was NOT assigned 1 (the old fallback stream ID)
        assertNotEquals("Should not use default fallback stream ID 1", 1, sid)
    }
}
