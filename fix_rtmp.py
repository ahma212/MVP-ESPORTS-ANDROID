import re

with open('app/src/main/java/com/example/services/streaming/RtmpIngestGateway.kt', 'r') as f:
    content = f.read()

parser_code = """
    private fun waitForRtmpResponse(inp: InputStream, expectedCommandName: String, transactionId: Double): Boolean {
        try {
            val response = readRtmpCommand(inp)
            if (response == null) {
                Log.e(TAG, "Failed to read RTMP response for $expectedCommandName")
                return false
            }
            Log.d(TAG, "Received RTMP response: ${response.commandName} for txn: ${response.transactionId}")
            if (response.commandName == "_error") {
                Log.e(TAG, "Server returned _error for $expectedCommandName")
                return false
            }
            if (response.commandName == "_result" || response.commandName == "onStatus" || response.commandName == "onFCPublish") {
                // Good enough for this check
                return true
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error waiting for RTMP response: ${e.message}")
            return false
        }
    }

    private fun waitForCreateStreamResponse(inp: InputStream, transactionId: Double): Int {
        try {
            val response = readRtmpCommand(inp)
            if (response == null || response.commandName == "_error") {
                Log.e(TAG, "CreateStream failed or returned _error")
                return -1
            }
            // Stream ID is typically the second argument (index 1) in the AMF0 response after the null object
            if (response.commandName == "_result") {
                if (response.arguments.size >= 2 && response.arguments[1] is Double) {
                    val sid = (response.arguments[1] as Double).toInt()
                    Log.d(TAG, "Parsed server stream ID: $sid")
                    return sid
                } else if (response.arguments.isNotEmpty() && response.arguments[0] is Double) {
                    val sid = (response.arguments[0] as Double).toInt()
                    Log.d(TAG, "Parsed server stream ID: $sid")
                    return sid
                }
            }
            return 1 // Fallback if parsed but weird format
        } catch (e: Exception) {
            Log.e(TAG, "Error waiting for createStream response: ${e.message}")
            return -1
        }
    }

    private data class RtmpCommand(val commandName: String, val transactionId: Double, val arguments: List<Any>)

    private var currentChunkSize = 128

    private fun readRtmpCommand(inp: InputStream): RtmpCommand? {
        // Very basic RTMP Message parser
        // Reads until it parses an AMF0 command message (Type 20)
        while (true) {
            val basicHeader = inp.read()
            if (basicHeader < 0) return null
            val fmt = (basicHeader shr 6) and 0x03
            val csid = basicHeader and 0x3F
            var chunkStreamId = csid
            if (csid == 0) {
                chunkStreamId = inp.read() + 64
            } else if (csid == 1) {
                chunkStreamId = inp.read() + (inp.read() shl 8) + 64
            }

            var messageLength = 0
            var messageType = 0
            when (fmt) {
                0 -> {
                    // 11 bytes
                    inp.read(); inp.read(); inp.read() // timestamp
                    messageLength = (inp.read() shl 16) or (inp.read() shl 8) or inp.read()
                    messageType = inp.read()
                    inp.read(); inp.read(); inp.read(); inp.read() // stream id
                }
                1 -> {
                    // 7 bytes
                    inp.read(); inp.read(); inp.read() // timestamp delta
                    messageLength = (inp.read() shl 16) or (inp.read() shl 8) or inp.read()
                    messageType = inp.read()
                }
                2 -> {
                    // 3 bytes
                    inp.read(); inp.read(); inp.read() // timestamp delta
                }
                3 -> {
                    // 0 bytes
                }
            }

            // We only support unfragmented messages for now, or just read the first chunk of a command
            var toRead = if (messageLength > 0) messageLength else currentChunkSize
            if (toRead > currentChunkSize) toRead = currentChunkSize

            val payload = ByteArray(toRead)
            var read = 0
            while (read < toRead) {
                val r = inp.read(payload, read, toRead - read)
                if (r < 0) return null
                read += r
            }

            if (messageType == 1) {
                // Set Chunk Size
                if (payload.size >= 4) {
                    currentChunkSize = ((payload[0].toInt() and 0x7F) shl 24) or ((payload[1].toInt() and 0xFF) shl 16) or ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
                }
            } else if (messageType == 20) {
                // AMF0 Command
                return parseAmf0Command(payload)
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
"""

# Replace the dummy methods
old_waitForRtmpResponse = """    private fun waitForRtmpResponse(inp: InputStream, command: String): Boolean {
        // Simplified response parsing - checking for _result or status
        // A full AMF0 parser would be needed for production validation
        return true // Assuming success for now
    }
    
    private fun waitForCreateStreamResponse(inp: InputStream): Int {
        // Simplified - return a dynamic ID
        return 1 // In reality, parse AMF0 response
    }"""
content = content.replace(old_waitForRtmpResponse, parser_code)

# Replace the connect calls to use the new method signatures
old_connect = """            // 2. Connect
            sendRtmpConnectCommand(outputStream, appName, rtmpUrl)
            if (!waitForRtmpResponse(inputStream!!, "connect")) throw Exception("RTMP Connect failed")

            // 3. CreateStream
            sendRtmpCreateStreamCommand(outputStream)
            val streamId = waitForCreateStreamResponse(inputStream!!)
            if (streamId <= 0) throw Exception("RTMP CreateStream failed")
            serverStreamId = streamId

            // 4. Publish
            sendRtmpPublishCommand(outputStream, streamKey)
            if (!waitForRtmpResponse(inputStream!!, "publish")) throw Exception("RTMP Publish failed")"""

new_connect = """            // 2. Connect
            sendRtmpConnectCommand(outputStream, appName, rtmpUrl)
            if (!waitForRtmpResponse(inputStream!!, "connect", 1.0)) throw Exception("RTMP Connect failed or rejected")

            // 3. CreateStream
            sendRtmpCreateStreamCommand(outputStream)
            val streamId = waitForCreateStreamResponse(inputStream!!, 2.0)
            if (streamId <= 0) throw Exception("RTMP CreateStream failed")
            serverStreamId = streamId

            // 4. Publish
            sendRtmpPublishCommand(outputStream, streamKey)
            if (!waitForRtmpResponse(inputStream!!, "publish", 3.0)) throw Exception("RTMP Publish failed")"""

content = content.replace(old_connect, new_connect)

# Also ensure we reset currentChunkSize on connect
connect_ingest = "override fun connectIngest(rtmpUrl: String): Result<Unit> {"
content = content.replace(connect_ingest, connect_ingest + "\n        currentChunkSize = 128")

with open('app/src/main/java/com/example/services/streaming/RtmpIngestGateway.kt', 'w') as f:
    f.write(content)

