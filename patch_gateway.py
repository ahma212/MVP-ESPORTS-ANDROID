import re

with open('app/src/main/java/com/example/services/streaming/RtmpIngestGateway.kt', 'r') as f:
    content = f.read()

# Replace packageAsFlvVideoTag
old_package = """    fun packageAsFlvVideoTag(
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
        packet[13] = 0x00
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
    }"""

new_package = """    fun packageAsFlvVideoTag(
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
    }"""

if old_package in content:
    content = content.replace(old_package, new_package)
else:
    print("Could not find old packageAsFlvVideoTag")

with open('app/src/main/java/com/example/services/streaming/RtmpIngestGateway.kt', 'w') as f:
    f.write(content)
