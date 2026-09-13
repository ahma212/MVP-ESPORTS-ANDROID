package com.example

import com.example.services.streaming.RtmpIngestGateway
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Socket

class Part14RtmpParserHardeningTest {

    private lateinit var gateway: RtmpIngestGateway

    @Before
    fun setup() {
        // Create an instance of the gateway with a dummy socket
        val mockSocket = object : Socket() {
            override fun getOutputStream() = ByteArrayOutputStream()
            override fun getInputStream() = java.io.ByteArrayInputStream(ByteArray(0))
        }
        gateway = RtmpIngestGateway { _, _ -> mockSocket }
    }

    private fun invokeReadRtmpCommand(inp: InputStream): Any? {
        val method = RtmpIngestGateway::class.java.getDeclaredMethod("readRtmpCommand", InputStream::class.java)
        method.isAccessible = true
        return method.invoke(gateway, inp)
    }

    private fun getCommandName(rtmpCommand: Any): String {
        val field = rtmpCommand::class.java.getDeclaredField("commandName")
        field.isAccessible = true
        return field.get(rtmpCommand) as String
    }

    private fun getTransactionId(rtmpCommand: Any): Double {
        val field = rtmpCommand::class.java.getDeclaredField("transactionId")
        field.isAccessible = true
        return field.get(rtmpCommand) as Double
    }

    private fun getArguments(rtmpCommand: Any): List<*> {
        val field = rtmpCommand::class.java.getDeclaredField("arguments")
        field.isAccessible = true
        return field.get(rtmpCommand) as List<*>
    }

    private fun serializeAmf0CommandPayload(
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
        return payloadStream.toByteArray()
    }

    @Test
    fun testFmt0MessageParsing() {
        val payload = serializeAmf0CommandPayload("_result", 1.0, listOf(null, "success"))
        val baos = ByteArrayOutputStream()

        // Fmt 0 Basic Header for CSID 3 (0x03)
        baos.write(0x03)
        // Timestamp (3 bytes) = 0x000102
        baos.write(0x00); baos.write(0x01); baos.write(0x02)
        // Message Length (3 bytes)
        baos.write((payload.size shr 16) and 0xFF)
        baos.write((payload.size shr 8) and 0xFF)
        baos.write(payload.size and 0xFF)
        // Message Type (1 byte) = 20 (AMF0 command)
        baos.write(20)
        // Message Stream ID (4 bytes, little-endian) = 1
        baos.write(1); baos.write(0); baos.write(0); baos.write(0)

        baos.write(payload)

        val inp = java.io.ByteArrayInputStream(baos.toByteArray())
        val cmd = invokeReadRtmpCommand(inp)

        assertNotNull("Command must be parsed successfully", cmd)
        assertEquals("_result", getCommandName(cmd!!))
        assertEquals(1.0, getTransactionId(cmd), 0.001)
        assertEquals("success", getArguments(cmd)[0])
    }

    @Test
    fun testFmt1MessageParsing() {
        // Set chunk size to 512 so it fits in one chunk
        gateway.activeChunkSize = 512

        // First, prime the state with a fmt 0 message to establish stream ID
        testFmt0MessageParsing()

        val payload = serializeAmf0CommandPayload("onStatus", 0.0, listOf(mapOf("code" to "NetStream.Publish.Start")))
        val baos = ByteArrayOutputStream()

        // Fmt 1 Basic Header for CSID 3 (0x43)
        baos.write(0x43)
        // Timestamp Delta (3 bytes) = 1000
        baos.write(0x00); baos.write(0x03); baos.write(0xE8)
        // Message Length (3 bytes)
        baos.write((payload.size shr 16) and 0xFF)
        baos.write((payload.size shr 8) and 0xFF)
        baos.write(payload.size and 0xFF)
        // Message Type (1 byte) = 20
        baos.write(20)

        baos.write(payload)

        val inp = java.io.ByteArrayInputStream(baos.toByteArray())
        val cmd = invokeReadRtmpCommand(inp)

        assertNotNull(cmd)
        assertEquals("onStatus", getCommandName(cmd!!))
    }

    @Test
    fun testFmt2MessageParsing() {
        gateway.activeChunkSize = 512
        // Prime state with a fmt 0 message to establish previous length/type context
        val payload = serializeAmf0CommandPayload("_result", 1.0, listOf(null, "success"))
        
        val primeBaos = ByteArrayOutputStream()
        primeBaos.write(0x03)
        primeBaos.write(0); primeBaos.write(0); primeBaos.write(0)
        primeBaos.write((payload.size shr 16) and 0xFF)
        primeBaos.write((payload.size shr 8) and 0xFF)
        primeBaos.write(payload.size and 0xFF)
        primeBaos.write(20)
        primeBaos.write(1); primeBaos.write(0); primeBaos.write(0); primeBaos.write(0)
        primeBaos.write(payload)
        
        val primeInp = java.io.ByteArrayInputStream(primeBaos.toByteArray())
        assertNotNull(invokeReadRtmpCommand(primeInp))

        val baos = ByteArrayOutputStream()

        // Fmt 2 Basic Header for CSID 3 (0x83)
        baos.write(0x83)
        // Timestamp Delta (3 bytes) = 500
        baos.write(0x00); baos.write(0x01); baos.write(0xF4)

        // Must write a payload of the exact same size because fmt 2 reuses messageLength
        baos.write(payload)

        val inp = java.io.ByteArrayInputStream(baos.toByteArray())
        val cmd = invokeReadRtmpCommand(inp)

        assertNotNull(cmd)
        assertEquals("_result", getCommandName(cmd!!))
    }

    @Test
    fun testFmt3ContinuationAndMultiChunkMessageReconstruction() {
        val payload = serializeAmf0CommandPayload("_result", 2.0, listOf(null, 42.0))
        
        // Force small chunk size to trigger multi-chunking
        gateway.activeChunkSize = 10

        val baos = ByteArrayOutputStream()

        // Chunk 1: Fmt 0
        baos.write(0x03)
        // Timestamp
        baos.write(0); baos.write(0); baos.write(0)
        // Message Length
        baos.write((payload.size shr 16) and 0xFF)
        baos.write((payload.size shr 8) and 0xFF)
        baos.write(payload.size and 0xFF)
        // Message Type
        baos.write(20)
        // Stream ID
        baos.write(0); baos.write(0); baos.write(0); baos.write(0)

        // Write first 10 bytes of payload
        baos.write(payload, 0, 10)

        // Sub-chunks: Fmt 3
        var written = 10
        while (written < payload.size) {
            baos.write(0xC3) // Fmt 3
            val toWrite = Math.min(10, payload.size - written)
            baos.write(payload, written, toWrite)
            written += toWrite
        }

        val inp = java.io.ByteArrayInputStream(baos.toByteArray())
        val cmd = invokeReadRtmpCommand(inp)

        assertNotNull("Reconstructed command must not be null", cmd)
        assertEquals("_result", getCommandName(cmd!!))
        assertEquals(2.0, getTransactionId(cmd), 0.001)
        assertEquals(42.0, getArguments(cmd)[0])
    }

    @Test
    fun testPartialSocketReadsAreSafe() {
        val payload = serializeAmf0CommandPayload("_result", 1.0, listOf())
        val baos = ByteArrayOutputStream()

        // Fmt 0 header
        baos.write(0x03)
        baos.write(0); baos.write(0); baos.write(0)
        baos.write((payload.size shr 16) and 0xFF)
        baos.write((payload.size shr 8) and 0xFF)
        baos.write(payload.size and 0xFF)
        baos.write(20)
        baos.write(0); baos.write(0); baos.write(0); baos.write(0)

        // Write only partial payload bytes
        baos.write(payload, 0, payload.size / 2)

        val inp = java.io.ByteArrayInputStream(baos.toByteArray())
        val cmd = invokeReadRtmpCommand(inp)

        assertNull("Partial reads must return null and fail safely", cmd)
    }

    @Test
    fun testExtendedTimestampHandling() {
        gateway.activeChunkSize = 512
        val payload = serializeAmf0CommandPayload("_result", 3.0, listOf())
        val baos = ByteArrayOutputStream()

        // Fmt 0 basic header
        baos.write(0x03)
        // Trigger extended timestamp by writing 0xFFFFFF
        baos.write(0xFF); baos.write(0xFF); baos.write(0xFF)
        baos.write((payload.size shr 16) and 0xFF)
        baos.write((payload.size shr 8) and 0xFF)
        baos.write(payload.size and 0xFF)
        baos.write(20)
        baos.write(0); baos.write(0); baos.write(0); baos.write(0)

        // Write 4-byte extended timestamp (e.g. 2000000)
        val extVal = 2000000L
        baos.write(((extVal shr 24) and 0xFF).toInt())
        baos.write(((extVal shr 16) and 0xFF).toInt())
        baos.write(((extVal shr 8) and 0xFF).toInt())
        baos.write((extVal and 0xFF).toInt())

        baos.write(payload)

        val inp = java.io.ByteArrayInputStream(baos.toByteArray())
        val cmd = invokeReadRtmpCommand(inp)

        assertNotNull(cmd)
        assertEquals("_result", getCommandName(cmd!!))
    }

    @Test
    fun testDynamicServerChunkSizeChange() {
        val baos = ByteArrayOutputStream()

        // 1. Write Set Chunk Size message (Type 1)
        // Fmt 0 Basic Header (CSID 2 is standard for protocol messages)
        baos.write(0x02)
        // Timestamp
        baos.write(0); baos.write(0); baos.write(0)
        // Length of payload = 4
        baos.write(0); baos.write(0); baos.write(4)
        // Message Type ID = 1
        baos.write(1)
        // Stream ID
        baos.write(0); baos.write(0); baos.write(0); baos.write(0)
        // Payload: new chunk size = 64
        baos.write(0); baos.write(0); baos.write(0); baos.write(64)

        // 2. Write subsequent AMF0 Command using the new chunk size
        val cmdPayload = serializeAmf0CommandPayload("publish", 3.0, listOf())
        // Fmt 0 Basic Header for CSID 3 (0x03)
        baos.write(0x03)
        baos.write(0); baos.write(0); baos.write(0)
        baos.write((cmdPayload.size shr 16) and 0xFF)
        baos.write((cmdPayload.size shr 8) and 0xFF)
        baos.write(cmdPayload.size and 0xFF)
        baos.write(20)
        baos.write(0); baos.write(0); baos.write(0); baos.write(0)

        // Write payload split by 64 bytes
        var written = 0
        while (written < cmdPayload.size) {
            val toWrite = Math.min(64, cmdPayload.size - written)
            baos.write(cmdPayload, written, toWrite)
            written += toWrite
            if (written < cmdPayload.size) {
                baos.write(0xC3) // Fmt 3 header for continuation
            }
        }

        val inp = java.io.ByteArrayInputStream(baos.toByteArray())
        val cmd = invokeReadRtmpCommand(inp)

        assertNotNull("Subsequent command must be parsed under the new chunk size", cmd)
        assertEquals("publish", getCommandName(cmd!!))
        assertEquals(64, gateway.activeChunkSize)
    }

    @Test
    fun testMultipleCsidsWithIndependentState() {
        gateway.activeChunkSize = 10
        val payloadA = serializeAmf0CommandPayload("cmdA", 1.0, listOf())
        val payloadB = serializeAmf0CommandPayload("cmdB", 2.0, listOf())

        val baos = ByteArrayOutputStream()

        // Interleave Chunk 1 of Stream A (CSID 3)
        baos.write(0x03)
        baos.write(0); baos.write(0); baos.write(0)
        baos.write((payloadA.size shr 16) and 0xFF)
        baos.write((payloadA.size shr 8) and 0xFF)
        baos.write(payloadA.size and 0xFF)
        baos.write(20)
        baos.write(0); baos.write(0); baos.write(0); baos.write(0)
        baos.write(payloadA, 0, 10)

        // Interleave Chunk 1 of Stream B (CSID 4)
        baos.write(0x04)
        baos.write(0); baos.write(0); baos.write(0)
        baos.write((payloadB.size shr 16) and 0xFF)
        baos.write((payloadB.size shr 8) and 0xFF)
        baos.write(payloadB.size and 0xFF)
        baos.write(20)
        baos.write(0); baos.write(0); baos.write(0); baos.write(0)
        baos.write(payloadB, 0, 10)

        // Continuation Chunk 2 of Stream A
        baos.write(0xC3)
        baos.write(payloadA, 10, payloadA.size - 10)

        // Continuation Chunk 2 of Stream B
        baos.write(0xC4)
        baos.write(payloadB, 10, payloadB.size - 10)

        val inp = java.io.ByteArrayInputStream(baos.toByteArray())
        
        // First read should return completed cmdA
        val cmd1 = invokeReadRtmpCommand(inp)
        assertNotNull(cmd1)
        assertEquals("cmdA", getCommandName(cmd1!!))

        // Second read should return completed cmdB
        val cmd2 = invokeReadRtmpCommand(inp)
        assertNotNull(cmd2)
        assertEquals("cmdB", getCommandName(cmd2!!))
    }

    @Test
    fun testMalformedTruncatedChunkRejection() {
        val baos = ByteArrayOutputStream()
        // Truncated basic header (empty stream)
        val inp1 = java.io.ByteArrayInputStream(baos.toByteArray())
        assertNull(invokeReadRtmpCommand(inp1))

        // Incomplete FMT 0 header (only 5 bytes instead of 11)
        baos.write(0x03)
        baos.write(0); baos.write(1); baos.write(2); baos.write(3)
        val inp2 = java.io.ByteArrayInputStream(baos.toByteArray())
        assertNull(invokeReadRtmpCommand(inp2))
    }
}
