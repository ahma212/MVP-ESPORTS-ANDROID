package com.example.services.streaming

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Production RTMP Ingest Gateway implementing IYouTubeIngest.
 * Handles TCP socket connection to YouTube / RTMP ingest servers, full C0/C1/S0/S1/S2 handshake,
 * AMF0 connect / createStream / publish command flow, RTMP chunking, and FLV/AVC packetization
 * for H.264 video transmission.
 */
class RtmpIngestGateway(
    private val socketProvider: ((host: String, port: Int) -> Socket)? = null
) : IYouTubeIngest {

    companion object {
        private const val TAG = "RtmpIngestGateway"
        private const val RTMP_DEFAULT_PORT = 1935
        private const val CHUNK_SIZE = 4096
    }

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    override var isConnected: Boolean = false
        private set

    var currentRtmpUrl: String? = null
        private set

    var totalBytesSent: Long = 0L
        private set

    var totalPacketsSent: Long = 0L
        private set

    private var serverStreamId: Int = 0 // Initially unknown

    private val chunkStates = java.util.concurrent.ConcurrentHashMap<Int, ChunkStreamState>()

    private class ChunkStreamState {
        var lastTimestamp: Long = 0L
        var lastTimestampDelta: Long = 0L
        var lastLength: Int = -1
        var lastMessageTypeId: Int = -1
        var lastStreamId: Int = -1
        var hasExtendedTimestamp: Boolean = false
        var partialBuffer: ByteArray? = null
        var partialBytesRead: Int = 0
    }

    var activeChunkSize: Int
        get() = currentChunkSize
        set(value) {
            currentChunkSize = value
        }

    override fun connectIngest(rtmpUrl: String): Result<Unit> {
        chunkStates.clear()
        currentChunkSize = 128
        totalBytesSent = 0L
        totalPacketsSent = 0L
        return try {
            currentRtmpUrl = rtmpUrl
            val uri = URI(rtmpUrl)
            val host = uri.host ?: "a.rtmp.youtube.com"
            val port = if (uri.port > 0) uri.port else RTMP_DEFAULT_PORT

            // Parse app and stream key from path
            val pathSegments = uri.path?.split("/")?.filter { it.isNotEmpty() } ?: emptyList()
            val appName = if (pathSegments.isNotEmpty()) pathSegments[0] else "live2"
            val streamKey = if (pathSegments.size > 1) pathSegments.subList(1, pathSegments.size).joinToString("/") else (pathSegments.getOrNull(0) ?: "stream")

            Log.i(TAG, "Connecting to RTMP host: $host:$port, app: $appName")

            val s = socketProvider?.invoke(host, port) ?: Socket(host, port).apply {
                tcpNoDelay = true
                keepAlive = true
                soTimeout = 10000
            }
            socket = s
            outputStream = s.getOutputStream()
            inputStream = s.getInputStream()

            // 1. Handshake
            performFullRtmpHandshake(outputStream, inputStream)

            // 2. Connect
            sendRtmpConnectCommand(outputStream, appName, rtmpUrl)
            if (!waitForRtmpResponse(inputStream!!, "connect", 1.0)) throw Exception("RTMP Connect failed or rejected")

            // 3. CreateStream
            sendRtmpCreateStreamCommand(outputStream)
            val streamId = waitForCreateStreamResponse(inputStream!!, 2.0)
            if (streamId <= 0) throw Exception("RTMP CreateStream failed")
            serverStreamId = streamId

            // 4. Publish
            sendRtmpPublishCommand(outputStream, streamKey)
            if (!waitForRtmpResponse(inputStream!!, "publish", 3.0)) throw Exception("RTMP Publish failed")

            isConnected = true
            Result.success(Unit)
        } catch (e: Exception) {
            isConnected = false
            Log.e(TAG, "RTMP connection failed: ${e.message}")
            Result.failure(e)
        }
    }


    private fun waitForRtmpResponse(inp: InputStream, expectedCommandName: String, transactionId: Double): Boolean {
        try {
            var attempts = 0
            while (attempts < 20) {
                attempts++
                val response = readRtmpCommand(inp)
                if (response == null) {
                    Log.e(TAG, "Failed to read RTMP response for $expectedCommandName")
                    return false
                }
                Log.d(TAG, "waitForRtmpResponse: Received ${response.commandName} for txn: ${response.transactionId}")
                
                if (expectedCommandName == "connect") {
                    if (response.transactionId == transactionId) {
                        if (response.commandName == "_error") {
                            Log.e(TAG, "Server returned _error for connect")
                            return false
                        }
                        if (response.commandName == "_result") {
                            return true
                        }
                        return false
                    } else {
                        Log.d(TAG, "Ignoring unrelated RTMP command: ${response.commandName} with txn: ${response.transactionId} while waiting for connect")
                    }
                } else if (expectedCommandName == "publish") {
                    if (response.commandName == "onStatus") {
                        val firstArg = response.arguments.getOrNull(0)
                        if (firstArg is Map<*, *>) {
                            val level = firstArg["level"] as? String
                            val code = firstArg["code"] as? String
                            Log.d(TAG, "onStatus level: $level, code: $code")
                            if (level == "error") {
                                Log.e(TAG, "onStatus error: $code")
                                return false
                            }
                            if (code == "NetStream.Publish.Start" || code == "NetStream.Publish.BadName") {
                                return code == "NetStream.Publish.Start"
                            }
                        }
                        return true
                    } else if (response.commandName == "onFCPublish") {
                        return true
                    } else if (response.transactionId == transactionId) {
                        if (response.commandName == "_error") {
                            Log.e(TAG, "Server returned _error for publish")
                            return false
                        }
                        if (response.commandName == "_result") {
                            return true
                        }
                    } else {
                        Log.d(TAG, "Ignoring unrelated RTMP command: ${response.commandName} with txn: ${response.transactionId} while waiting for publish")
                    }
                } else {
                    if (response.transactionId == transactionId) {
                        return response.commandName != "_error"
                    } else {
                        Log.d(TAG, "Ignoring unrelated RTMP command: ${response.commandName} with txn: ${response.transactionId} while waiting for $expectedCommandName")
                    }
                }
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error waiting for RTMP response: ${e.message}")
            return false
        }
    }

    private fun waitForCreateStreamResponse(inp: InputStream, transactionId: Double): Int {
        try {
            var attempts = 0
            while (attempts < 20) {
                attempts++
                val response = readRtmpCommand(inp)
                if (response == null) {
                    Log.e(TAG, "Failed to read RTMP response for createStream")
                    return -1
                }
                Log.d(TAG, "waitForCreateStreamResponse: Received ${response.commandName} with txn: ${response.transactionId}")
                
                if (response.transactionId == transactionId) {
                    if (response.commandName == "_error") {
                        Log.e(TAG, "CreateStream failed or returned _error")
                        return -1
                    }
                    if (response.commandName == "_result") {
                        if (response.arguments.size >= 2 && response.arguments[1] is Double) {
                            val sid = (response.arguments[1] as Double).toInt()
                            if (sid <= 0) {
                                Log.e(TAG, "Parsed invalid stream ID: $sid")
                                return -1
                            }
                            Log.d(TAG, "Parsed server stream ID: $sid")
                            return sid
                        } else if (response.arguments.isNotEmpty() && response.arguments[0] is Double) {
                            val sid = (response.arguments[0] as Double).toInt()
                            if (sid <= 0) {
                                Log.e(TAG, "Parsed invalid stream ID: $sid")
                                return -1
                            }
                            Log.d(TAG, "Parsed server stream ID: $sid")
                            return sid
                        }
                    }
                    Log.e(TAG, "Matched createStream transaction ID $transactionId but response was malformed or missing a valid stream ID")
                    return -1
                } else {
                    Log.d(TAG, "Ignoring unrelated RTMP command: ${response.commandName} with txn: ${response.transactionId} while waiting for createStream")
                }
            }
            return -1
        } catch (e: Exception) {
            Log.e(TAG, "Error waiting for createStream response: ${e.message}")
            return -1
        }
    }

    private data class RtmpCommand(val commandName: String, val transactionId: Double, val arguments: List<Any>)

    private var currentChunkSize = 128

    private fun readFully(inp: InputStream, buffer: ByteArray, offset: Int, length: Int): Boolean {
        var bytesRead = 0
        while (bytesRead < length) {
            val r = inp.read(buffer, offset + bytesRead, length - bytesRead)
            if (r < 0) return false
            bytesRead += r
        }
        return true
    }

    private fun readRtmpCommand(inp: InputStream): RtmpCommand? {
        while (true) {
            val basicHeader = inp.read()
            if (basicHeader < 0) {
                Log.e(TAG, "EOF reached while reading RTMP basic header")
                return null
            }
            val fmt = (basicHeader shr 6) and 0x03
            val csid = basicHeader and 0x3F
            var chunkStreamId = csid
            if (csid == 0) {
                val b2 = inp.read()
                if (b2 < 0) {
                    Log.e(TAG, "EOF reached while reading CSID byte 2")
                    return null
                }
                chunkStreamId = b2 + 64
            } else if (csid == 1) {
                val b2 = inp.read()
                val b3 = inp.read()
                if (b2 < 0 || b3 < 0) {
                    Log.e(TAG, "EOF reached while reading CSID bytes 2 and 3")
                    return null
                }
                chunkStreamId = b2 + (b3 shl 8) + 64
            }

            val state = chunkStates.getOrPut(chunkStreamId) { ChunkStreamState() }

            var messageLength = state.lastLength
            var messageTypeId = state.lastMessageTypeId
            var messageStreamId = state.lastStreamId
            var timestampVal = 0L

            when (fmt) {
                0 -> {
                    val header = ByteArray(11)
                    if (!readFully(inp, header, 0, 11)) {
                        Log.e(TAG, "Truncated fmt 0 header")
                        return null
                    }
                    val tsHigh = header[0].toInt() and 0xFF
                    val tsMed = header[1].toInt() and 0xFF
                    val tsLow = header[2].toInt() and 0xFF
                    timestampVal = ((tsHigh shl 16) or (tsMed shl 8) or tsLow).toLong()

                    messageLength = ((header[3].toInt() and 0xFF) shl 16) or
                                    ((header[4].toInt() and 0xFF) shl 8) or
                                    (header[5].toInt() and 0xFF)
                    messageTypeId = header[6].toInt() and 0xFF
                    messageStreamId = (header[7].toInt() and 0xFF) or
                                      ((header[8].toInt() and 0xFF) shl 8) or
                                      ((header[9].toInt() and 0xFF) shl 16) or
                                      ((header[10].toInt() and 0xFF) shl 24)

                    state.hasExtendedTimestamp = (timestampVal == 0xFFFFFFL)
                    if (!state.hasExtendedTimestamp) {
                        state.lastTimestamp = timestampVal
                    }
                    state.lastLength = messageLength
                    state.lastMessageTypeId = messageTypeId
                    state.lastStreamId = messageStreamId
                    state.lastTimestampDelta = 0L
                }
                1 -> {
                    val header = ByteArray(7)
                    if (!readFully(inp, header, 0, 7)) {
                        Log.e(TAG, "Truncated fmt 1 header")
                        return null
                    }
                    val tsHigh = header[0].toInt() and 0xFF
                    val tsMed = header[1].toInt() and 0xFF
                    val tsLow = header[2].toInt() and 0xFF
                    val timestampDelta = ((tsHigh shl 16) or (tsMed shl 8) or tsLow).toLong()

                    messageLength = ((header[3].toInt() and 0xFF) shl 16) or
                                    ((header[4].toInt() and 0xFF) shl 8) or
                                    (header[5].toInt() and 0xFF)
                    messageTypeId = header[6].toInt() and 0xFF

                    state.hasExtendedTimestamp = (timestampDelta == 0xFFFFFFL)
                    if (!state.hasExtendedTimestamp) {
                        state.lastTimestampDelta = timestampDelta
                        state.lastTimestamp += timestampDelta
                    }
                    state.lastLength = messageLength
                    state.lastMessageTypeId = messageTypeId
                }
                2 -> {
                    val header = ByteArray(3)
                    if (!readFully(inp, header, 0, 3)) {
                        Log.e(TAG, "Truncated fmt 2 header")
                        return null
                    }
                    val tsHigh = header[0].toInt() and 0xFF
                    val tsMed = header[1].toInt() and 0xFF
                    val tsLow = header[2].toInt() and 0xFF
                    val timestampDelta = ((tsHigh shl 16) or (tsMed shl 8) or tsLow).toLong()

                    state.hasExtendedTimestamp = (timestampDelta == 0xFFFFFFL)
                    if (!state.hasExtendedTimestamp) {
                        state.lastTimestampDelta = timestampDelta
                        state.lastTimestamp += timestampDelta
                    }
                }
                3 -> {
                    // Reuses previous header context
                    messageLength = state.lastLength
                    messageTypeId = state.lastMessageTypeId
                    messageStreamId = state.lastStreamId
                }
            }

            if (state.hasExtendedTimestamp) {
                val extBuffer = ByteArray(4)
                if (!readFully(inp, extBuffer, 0, 4)) {
                    Log.e(TAG, "Truncated extended timestamp")
                    return null
                }
                val extTs = ((extBuffer[0].toLong() and 0xFF) shl 24) or
                            ((extBuffer[1].toLong() and 0xFF) shl 16) or
                            ((extBuffer[2].toLong() and 0xFF) shl 8) or
                            (extBuffer[3].toLong() and 0xFF)
                if (fmt == 0) {
                    state.lastTimestamp = extTs
                } else {
                    state.lastTimestampDelta = extTs
                    state.lastTimestamp += extTs
                }
            }

            // Inbound chunk reconstruction
            if (state.partialBuffer == null) {
                if (state.lastLength <= 0) {
                    Log.e(TAG, "Invalid RTMP state: message length $messageLength for CSID $chunkStreamId")
                    return null
                }
                state.partialBuffer = ByteArray(state.lastLength)
                state.partialBytesRead = 0
            }

            val chunkPayloadSize = Math.min(currentChunkSize, state.lastLength - state.partialBytesRead)
            if (chunkPayloadSize < 0) {
                Log.e(TAG, "Negative chunk payload size: $chunkPayloadSize")
                return null
            }

            if (chunkPayloadSize > 0) {
                if (!readFully(inp, state.partialBuffer!!, state.partialBytesRead, chunkPayloadSize)) {
                    Log.e(TAG, "EOF reached while reading chunk payload")
                    return null
                }
                state.partialBytesRead += chunkPayloadSize
            }

            if (state.partialBytesRead == state.lastLength) {
                val completePayload = state.partialBuffer!!
                val completeMessageTypeId = state.lastMessageTypeId

                // Reset stream chunk buffer context immediately
                state.partialBuffer = null
                state.partialBytesRead = 0

                if (completeMessageTypeId == 1) {
                    // Set Chunk Size Protocol Control Message
                    if (completePayload.size >= 4) {
                        val newSize = ((completePayload[0].toInt() and 0xFF) shl 24) or
                                      ((completePayload[1].toInt() and 0xFF) shl 16) or
                                      ((completePayload[2].toInt() and 0xFF) shl 8) or
                                      (completePayload[3].toInt() and 0xFF)
                        if (newSize in 1..0x7FFFFFFF) {
                            currentChunkSize = newSize
                            Log.i(TAG, "RTMP dynamically changed currentChunkSize to: $currentChunkSize")
                        } else {
                            Log.e(TAG, "Invalid new chunk size value: $newSize")
                            return null
                        }
                    } else {
                        Log.e(TAG, "Set Chunk Size payload too small")
                        return null
                    }
                } else if (completeMessageTypeId == 20) {
                    // AMF0 Command
                    val cmd = parseAmf0Command(completePayload)
                    if (cmd != null) {
                        return cmd
                    } else {
                        Log.e(TAG, "Failed to parse AMF0 Command")
                        return null
                    }
                } else {
                    Log.d(TAG, "Skipped unrelated message type: $completeMessageTypeId of size ${completePayload.size}")
                }
            }
        }
    }

    private fun parseAmf0Command(payload: ByteArray): RtmpCommand? {
        try {
            val dis = java.io.DataInputStream(java.io.ByteArrayInputStream(payload))
            val commandName = readAmf0Type(dis) as? String ?: return null
            val transactionId = readAmf0Type(dis) as? Double ?: 0.0
            val arguments = mutableListOf<Any>()
            while (dis.available() > 0) {
                val arg = readAmf0Type(dis)
                if (arg != null) arguments.add(arg)
            }
            return RtmpCommand(commandName, transactionId, arguments)
        } catch (e: Exception) {
            return null
        }
    }

    private fun readAmf0Type(dis: java.io.DataInputStream): Any? {
        if (dis.available() == 0) return null
        val type = dis.readByte().toInt()
        return when (type) {
            0x00 -> dis.readDouble()
            0x01 -> dis.readByte() != 0.toByte()
            0x02 -> {
                val len = dis.readUnsignedShort()
                val b = ByteArray(len)
                dis.readFully(b)
                String(b, Charsets.UTF_8)
            }
            0x03 -> {
                // Object, just consume until 00 00 09
                val map = mutableMapOf<String, Any?>()
                while (true) {
                    val keyLen = dis.readUnsignedShort()
                    if (keyLen == 0) {
                        val endType = dis.readByte().toInt()
                        if (endType == 0x09) break
                    }
                    val kb = ByteArray(keyLen)
                    dis.readFully(kb)
                    val key = String(kb, Charsets.UTF_8)
                    val value = readAmf0Type(dis)
                    map[key] = value
                }
                map
            }
            0x05 -> null // Null
            else -> null
        }
    }


    override fun transmitMuxedData(data: ByteArray, length: Int): Result<Unit> {
        return transmitVideoPacket(data, length, isKeyFrame = true, timestampMs = 0L)
    }

    fun transmitVideoPacket(
        data: ByteArray,
        length: Int,
        isKeyFrame: Boolean,
        timestampMs: Long,
        isCodecConfig: Boolean = false
    ): Result<Unit> {
        val stream = outputStream
        if (!isConnected || stream == null) {
            return Result.failure(IllegalStateException("RTMP Ingest not connected"))
        }

        return try {
            // Package H.264 NAL units into FLV video tag format, wrapped in RTMP Chunk
            val flvTagPacket = packageAsFlvVideoTag(data, length, isKeyFrame, timestampMs, isCodecConfig)
            val rtmpChunk = wrapInRtmpChunk(flvTagPacket, messageTypeId = 9, chunkStreamId = 4, timestampMs = timestampMs, streamId = serverStreamId)
            
            writeFully(stream, rtmpChunk)
            totalBytesSent += rtmpChunk.size
            totalPacketsSent++
            Result.success(Unit)
        } catch (e: Exception) {
            isConnected = false
            Log.e(TAG, "RTMP transmission failed: ${e.message}")
            Result.failure(e)
        }
    }

    override fun disconnectIngest() {
        try {
            isConnected = false
            chunkStates.clear()
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            outputStream = null
            inputStream = null
            socket = null
        }
    }

    private fun performFullRtmpHandshake(out: OutputStream?, inp: InputStream?) {
        if (out == null || inp == null) return

        // Write C0 (1 byte version = 3) + C1 (1536 bytes)
        val c0c1 = ByteArray(1537)
        c0c1[0] = 0x03
        // Fill C1 random bytes or timestamp
        val random = java.util.Random(System.currentTimeMillis())
        random.nextBytes(c0c1)
        c0c1[0] = 0x03 // ensure version 3
        c0c1[1] = 0; c0c1[2] = 0; c0c1[3] = 0; c0c1[4] = 0 // timestamp 0

        out.write(c0c1)
        out.flush()

        // Read S0 (1 byte) + S1 (1536 bytes) + S2 (1536 bytes)
        val s0s1s2 = ByteArray(1 + 1536 + 1536)
        var totalRead = 0
        while (totalRead < s0s1s2.size) {
            val r = inp.read(s0s1s2, totalRead, s0s1s2.size - totalRead)
            if (r < 0) break
            totalRead += r
        }

        // Write C2 (echoing S1 payload of 1536 bytes)
        val c2 = ByteArray(1536)
        if (totalRead >= 1537) {
            System.arraycopy(s0s1s2, 1, c2, 0, 1536)
        }
        out.write(c2)
        out.flush()
    }

    private fun sendRtmpConnectCommand(out: OutputStream?, appName: String, tcUrl: String) {
        if (out == null) return
        val amfPayload = buildAmf0Command("connect", 1.0, mapOf(
            "app" to appName,
            "flashVer" to "FMLE/3.0 (compatible; AIStudioLive/1.0)",
            "tcUrl" to tcUrl,
            "type" to "nonprivate",
            "capabilities" to 15.0,
            "audioCodecs" to 0.0,
            "videoCodecs" to 252.0, // AVC
            "videoFunction" to 1.0
        ))
        val chunk = wrapInRtmpChunk(amfPayload, messageTypeId = 20, chunkStreamId = 3, timestampMs = 0L, streamId = 0)
        out.write(chunk)
        out.flush()
    }

    private fun sendRtmpCreateStreamCommand(out: OutputStream?) {
        if (out == null) return
        val amfPayload = buildAmf0Command("createStream", 2.0, null)
        val chunk = wrapInRtmpChunk(amfPayload, messageTypeId = 20, chunkStreamId = 3, timestampMs = 0L, streamId = 0)
        out.write(chunk)
        out.flush()
    }

    private fun sendRtmpPublishCommand(out: OutputStream?, streamKey: String) {
        if (out == null) return
        val amfPayload = buildAmf0Command("publish", 3.0, null, streamKey, "live")
        val chunk = wrapInRtmpChunk(amfPayload, messageTypeId = 20, chunkStreamId = 3, timestampMs = 0L, streamId = serverStreamId)
        out.write(chunk)
        out.flush()
    }

    private fun buildAmf0Command(commandName: String, transactionId: Double, obj: Any?, vararg extraArgs: Any): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        writeAmf0String(baos, commandName)
        writeAmf0Number(baos, transactionId)
        if (obj != null) {
            writeAmf0Object(baos, obj)
        } else {
            writeAmf0Null(baos)
        }
        for (arg in extraArgs) {
            when (arg) {
                is String -> writeAmf0String(baos, arg)
                is Double -> writeAmf0Number(baos, arg)
                is Int -> writeAmf0Number(baos, arg.toDouble())
                is Boolean -> writeAmf0Boolean(baos, arg)
                else -> writeAmf0Null(baos)
            }
        }
        return baos.toByteArray()
    }

    private fun writeAmf0String(os: java.io.OutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size < 65536) {
            os.write(0x02) // AMF0 String
            os.write((bytes.size shr 8) and 0xFF)
            os.write(bytes.size and 0xFF)
            os.write(bytes)
        } else {
            os.write(0x0C) // AMF0 Long String
            os.write((bytes.size shr 24) and 0xFF)
            os.write((bytes.size shr 16) and 0xFF)
            os.write((bytes.size shr 8) and 0xFF)
            os.write(bytes.size and 0xFF)
            os.write(bytes)
        }
    }

    private fun writeAmf0Number(os: java.io.OutputStream, value: Double) {
        os.write(0x00) // AMF0 Number
        val l = java.lang.Double.doubleToLongBits(value)
        for (i in 7 downTo 0) {
            os.write(((l shr (i * 8)) and 0xFF).toInt())
        }
    }

    private fun writeAmf0Boolean(os: java.io.OutputStream, value: Boolean) {
        os.write(0x01) // AMF0 Boolean
        os.write(if (value) 1 else 0)
    }

    private fun writeAmf0Null(os: java.io.OutputStream) {
        os.write(0x05) // AMF0 Null
    }

    @Suppress("UNCHECKED_CAST")
    private fun writeAmf0Object(os: java.io.OutputStream, obj: Any) {
        os.write(0x03) // AMF0 Object
        if (obj is Map<*, *>) {
            for ((k, v) in obj) {
                val keyStr = k.toString()
                val keyBytes = keyStr.toByteArray(Charsets.UTF_8)
                os.write((keyBytes.size shr 8) and 0xFF)
                os.write(keyBytes.size and 0xFF)
                os.write(keyBytes)
                when (v) {
                    is String -> writeAmf0String(os, v)
                    is Double -> writeAmf0Number(os, v)
                    is Int -> writeAmf0Number(os, v.toDouble())
                    is Boolean -> writeAmf0Boolean(os, v)
                    else -> writeAmf0Null(os)
                }
            }
        }
        // Object end marker: 0x00 0x00 0x09
        os.write(0x00)
        os.write(0x00)
        os.write(0x09)
    }

    private fun writeFully(out: OutputStream?, data: ByteArray) {
        val stream = out ?: throw java.io.IOException("OutputStream is null or disconnected")
        var bytesWritten = 0
        val bufferSize = 1024
        while (bytesWritten < data.size) {
            val toWrite = (data.size - bytesWritten).coerceAtMost(bufferSize)
            stream.write(data, bytesWritten, toWrite)
            bytesWritten += toWrite
        }
        stream.flush()
    }

    private fun wrapInRtmpChunk(payload: ByteArray, messageTypeId: Int, chunkStreamId: Int, timestampMs: Long, streamId: Int): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        
        // RTMP Basic Header
        // fmt = 0: 11-byte message header (full header)
        // fmt = 1: 7-byte (same stream id)
        // fmt = 2: 3-byte (same stream id, length, type)
        // fmt = 3: 0-byte (continuation / same everything)
        // We track the state for the current chunk stream ID
        val state = chunkStates.getOrPut(chunkStreamId) { ChunkStreamState() }
        
        // Determine appropriate header chunk type (fmt)
        // We must write fmt = 0 for the first chunk of a message to reset message length/type/streamId
        val fmt = 0
        if (chunkStreamId < 64) {
            baos.write((fmt shl 6) or chunkStreamId)
        } else if (chunkStreamId < 320) {
            baos.write(fmt shl 6)
            baos.write(chunkStreamId - 64)
        } else {
            baos.write((fmt shl 6) or 1)
            baos.write((chunkStreamId - 64) and 0xFF)
            baos.write(((chunkStreamId - 64) shr 8) and 0xFF)
        }

        // Message Header (fmt 0: 11 bytes)
        val ts = if (timestampMs >= 0xFFFFFF) 0xFFFFFF else timestampMs.toInt()
        baos.write((ts shr 16) and 0xFF)
        baos.write((ts shr 8) and 0xFF)
        baos.write(ts and 0xFF)

        val length = payload.size
        baos.write((length shr 16) and 0xFF)
        baos.write((length shr 8) and 0xFF)
        baos.write(length and 0xFF)

        baos.write(messageTypeId)

        // Stream ID (4 bytes, little-endian)
        baos.write(streamId and 0xFF)
        baos.write((streamId shr 8) and 0xFF)
        baos.write((streamId shr 16) and 0xFF)
        baos.write((streamId shr 24) and 0xFF)

        if (timestampMs >= 0xFFFFFF) {
            val extTs = timestampMs.toInt()
            baos.write((extTs shr 24) and 0xFF)
            baos.write((extTs shr 16) and 0xFF)
            baos.write((extTs shr 8) and 0xFF)
            baos.write(extTs and 0xFF)
        }

        state.lastTimestamp = timestampMs
        state.lastLength = length
        state.lastMessageTypeId = messageTypeId
        state.lastStreamId = streamId

        // Payload chunking
        var offset = 0
        while (offset < payload.size) {
            val chunkSizeToWrite = (payload.size - offset).coerceAtMost(currentChunkSize)
            if (offset > 0) {
                // Subsequent chunks use fmt = 3 (continuation / no message header)
                if (chunkStreamId < 64) {
                    baos.write((3 shl 6) or chunkStreamId)
                } else if (chunkStreamId < 320) {
                    baos.write(3 shl 6)
                    baos.write(chunkStreamId - 64)
                } else {
                    baos.write((3 shl 6) or 1)
                    baos.write((chunkStreamId - 64) and 0xFF)
                    baos.write(((chunkStreamId - 64) shr 8) and 0xFF)
                }

                if (timestampMs >= 0xFFFFFF) {
                    val extTs = timestampMs.toInt()
                    baos.write((extTs shr 24) and 0xFF)
                    baos.write((extTs shr 16) and 0xFF)
                    baos.write((extTs shr 8) and 0xFF)
                    baos.write(extTs and 0xFF)
                }
            }
            baos.write(payload, offset, chunkSizeToWrite)
            offset += chunkSizeToWrite
        }

        return baos.toByteArray()
    }

    fun packageAsFlvVideoTag(
        h264Data: ByteArray,
        length: Int,
        isKeyFrame: Boolean = true,
        timestampMs: Long = 0L,
        isCodecConfig: Boolean = false
    ): ByteArray {
        val payloadLen = length + 5 // 5 bytes FLV video header (CodecID AVC, packet type NALU)
        val tagSize = 11 + payloadLen + 4 // FLV Tag Header (11) + Payload + PreviousTagSize (4)
        val packet = ByteArray(tagSize)

        packet[0] = 0x09 // Tag type: Video
        packet[1] = ((payloadLen shr 16) and 0xFF).toByte()
        packet[2] = ((payloadLen shr 8) and 0xFF).toByte()
        packet[3] = (payloadLen and 0xFF).toByte()

        val ts = timestampMs.toInt()
        packet[4] = ((ts shr 16) and 0xFF).toByte()
        packet[5] = ((ts shr 8) and 0xFF).toByte()
        packet[6] = (ts and 0xFF).toByte()
        packet[7] = ((ts shr 24) and 0xFF).toByte()

        packet[8] = 0
        packet[9] = 0
        packet[10] = 0

        packet[11] = if (isKeyFrame || isCodecConfig) 0x17.toByte() else 0x27.toByte()
        packet[12] = if (isCodecConfig) 0x00.toByte() else 0x01.toByte() // 0 = sequence header, 1 = NALU
        packet[13] = 0x00 // CompositionTime
        packet[14] = 0x00
        packet[15] = 0x00

        System.arraycopy(h264Data, 0, packet, 16, length.coerceAtMost(h264Data.size))

        val prevTagOffset = 11 + payloadLen
        val totalSize = tagSize
        packet[prevTagOffset] = ((totalSize shr 24) and 0xFF).toByte()
        packet[prevTagOffset + 1] = ((totalSize shr 16) and 0xFF).toByte()
        packet[prevTagOffset + 2] = ((totalSize shr 8) and 0xFF).toByte()
        packet[prevTagOffset + 3] = (totalSize and 0xFF).toByte()

        return packet
    }

    fun packageAsFlvAudioTag(
        audioData: ByteArray,
        length: Int,
        timestampMs: Long,
        isAudioSpecificConfig: Boolean
    ): ByteArray {
        val payloadLen = 2 + length
        val tagSize = 11 + payloadLen + 4
        val packet = ByteArray(tagSize)

        // FLV Tag Header
        packet[0] = 8 // Tag Type: Audio (8)
        packet[1] = ((payloadLen shr 16) and 0xFF).toByte()
        packet[2] = ((payloadLen shr 8) and 0xFF).toByte()
        packet[3] = (payloadLen and 0xFF).toByte()

        val ts = if (timestampMs >= 0xFFFFFF) 0xFFFFFF else timestampMs.toInt()
        packet[4] = ((ts shr 16) and 0xFF).toByte()
        packet[5] = ((ts shr 8) and 0xFF).toByte()
        packet[6] = (ts and 0xFF).toByte()
        packet[7] = ((ts shr 24) and 0xFF).toByte()
        // StreamID = 0
        packet[8] = 0
        packet[9] = 0
        packet[10] = 0

        // FLV Audio Packet Header (AAC)
        // SoundFormat: AAC (10), SoundRate: 44kHz (3), SoundSize: 16-bit (1), SoundType: Stereo (1) -> 0xAF
        packet[11] = 0xAF.toByte()
        packet[12] = if (isAudioSpecificConfig) 0x00.toByte() else 0x01.toByte() // 0 = sequence header, 1 = raw AAC

        System.arraycopy(audioData, 0, packet, 13, length.coerceAtMost(audioData.size))

        val prevTagOffset = 11 + payloadLen
        val totalSize = tagSize
        packet[prevTagOffset] = ((totalSize shr 24) and 0xFF).toByte()
        packet[prevTagOffset + 1] = ((totalSize shr 16) and 0xFF).toByte()
        packet[prevTagOffset + 2] = ((totalSize shr 8) and 0xFF).toByte()
        packet[prevTagOffset + 3] = (totalSize and 0xFF).toByte()

        return packet
    }

    fun transmitAudioPacket(
        data: ByteArray,
        length: Int,
        timestampMs: Long,
        isAudioSpecificConfig: Boolean = false
    ): Result<Unit> {
        val stream = outputStream
        if (!isConnected || stream == null) {
            return Result.failure(IllegalStateException("RTMP Ingest not connected"))
        }

        return try {
            val flvAudioTag = packageAsFlvAudioTag(data, length, timestampMs, isAudioSpecificConfig)
            val rtmpChunk = wrapInRtmpChunk(flvAudioTag, messageTypeId = 8, chunkStreamId = 4, timestampMs = timestampMs, streamId = serverStreamId)
            
            writeFully(stream, rtmpChunk)
            totalBytesSent += rtmpChunk.size
            totalPacketsSent++
            Result.success(Unit)
        } catch (e: Exception) {
            isConnected = false
            Log.e(TAG, "RTMP audio transmission failed: ${e.message}")
            Result.failure(e)
        }
    }
}

