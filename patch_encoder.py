import re

with open('app/src/main/java/com/example/services/streaming/MediaCodecVideoEncoder.kt', 'r') as f:
    content = f.read()

# find INFO_OUTPUT_FORMAT_CHANGED
old_encoder_loop = """        while (outputBufferIndex >= 0 || outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                lastOutputFormat = newFormat
                val csd0 = newFormat.getByteBuffer("csd-0")
                val csd1 = newFormat.getByteBuffer("csd-1")
                if (csd0 != null && csd1 != null) {
                    val sps = ByteArray(csd0.remaining()).also { csd0.get(it); csd0.rewind() }
                    val pps = ByteArray(csd1.remaining()).also { csd1.get(it); csd1.rewind() }
                    spsPpsHeader = sps + pps
                }
            } else {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(chunk)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        spsPpsHeader = chunk
                        isConfig = true
                    }
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                        isKeyFrame = true
                        val header = spsPpsHeader
                        if (header != null && !startsWithSps(chunk)) {
                            outputStream.write(header)
                        }
                    }
                    outputStream.write(chunk)
                }
                codec.releaseOutputBuffer(outputBufferIndex, false)
            }
            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
        }"""

new_encoder_loop = """        while (outputBufferIndex >= 0 || outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                lastOutputFormat = newFormat
                val csd0 = newFormat.getByteBuffer("csd-0")
                val csd1 = newFormat.getByteBuffer("csd-1")
                if (csd0 != null && csd1 != null) {
                    val sps = ByteArray(csd0.remaining()).also { csd0.get(it); csd0.rewind() }
                    val pps = ByteArray(csd1.remaining()).also { csd1.get(it); csd1.rewind() }
                    
                    val spsNalu = extractNalu(sps)
                    val ppsNalu = extractNalu(pps)
                    
                    if (spsNalu.size > 3) {
                        val record = java.io.ByteArrayOutputStream()
                        record.write(1) // version
                        record.write(spsNalu[1].toInt()) // profile
                        record.write(spsNalu[2].toInt()) // compatibility
                        record.write(spsNalu[3].toInt()) // level
                        record.write(0xFF) // 6 bits reserved, 2 bits lengthSizeMinusOne (3)
                        record.write(0xE1) // 3 bits reserved, 5 bits numOfSequenceParameterSets (1)
                        record.write((spsNalu.size shr 8) and 0xFF)
                        record.write(spsNalu.size and 0xFF)
                        record.write(spsNalu)
                        record.write(1) // numOfPictureParameterSets
                        record.write((ppsNalu.size shr 8) and 0xFF)
                        record.write(ppsNalu.size and 0xFF)
                        record.write(ppsNalu)
                        spsPpsHeader = record.toByteArray()
                    } else {
                        spsPpsHeader = sps + pps
                    }
                }
            } else {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(chunk)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // Sometimes config is emitted in buffer instead of INFO_OUTPUT_FORMAT_CHANGED
                        val nalus = convertAnnexBToAvcc(chunk)
                        if (nalus.isNotEmpty()) {
                             isConfig = true
                             outputStream.write(chunk) // We will format this differently or let caller handle it, but wait, if it's config we should make AVCDCR
                        }
                    } else {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                            isKeyFrame = true
                            val header = spsPpsHeader
                            // If we already have a generated sequence header, we might want to emit it if it's the very first frame
                            // Actually, FLV expects the sequence header as a separate packet (isCodecConfig=true) BEFORE the keyframe.
                        }
                        
                        val avccChunk = convertAnnexBToAvcc(chunk)
                        outputStream.write(avccChunk)
                    }
                }
                codec.releaseOutputBuffer(outputBufferIndex, false)
            }
            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
        }"""

new_encoder_methods = """
    private fun extractNalu(data: ByteArray): ByteArray {
        var start = 0
        while (start < data.size - 2) {
            if (data[start] == 0.toByte() && data[start+1] == 0.toByte() && data[start+2] == 1.toByte()) {
                val offset = if (start > 0 && data[start-1] == 0.toByte()) start - 1 else start
                // Found start code, find next
                start += 3
                var end = start
                while (end < data.size - 2) {
                    if (data[end] == 0.toByte() && data[end+1] == 0.toByte() && data[end+2] == 1.toByte()) {
                        val nextOffset = if (data[end-1] == 0.toByte()) end - 1 else end
                        val nalu = ByteArray(nextOffset - start)
                        System.arraycopy(data, start, nalu, 0, nalu.size)
                        return nalu
                    }
                    end++
                }
                val nalu = ByteArray(data.size - start)
                System.arraycopy(data, start, nalu, 0, nalu.size)
                return nalu
            }
            start++
        }
        return data
    }

    private fun convertAnnexBToAvcc(data: ByteArray): ByteArray {
        val nalus = mutableListOf<ByteArray>()
        var i = 0
        var start = -1
        while (i < data.size - 2) {
            if (data[i] == 0.toByte() && data[i+1] == 0.toByte() && data[i+2] == 1.toByte()) {
                if (start != -1) {
                    val naluLen = if (i > 0 && data[i-1] == 0.toByte()) i - 1 - start else i - start
                    if (naluLen > 0) {
                        val nalu = ByteArray(naluLen)
                        System.arraycopy(data, start, nalu, 0, naluLen)
                        nalus.add(nalu)
                    }
                }
                start = i + 3
                i += 3
            } else {
                i++
            }
        }
        if (start != -1 && start < data.size) {
            val naluLen = data.size - start
            val nalu = ByteArray(naluLen)
            System.arraycopy(data, start, nalu, 0, naluLen)
            nalus.add(nalu)
        }
        
        if (nalus.isEmpty()) {
            return data // Possibly already AVCC or raw data
        }
        
        val out = java.io.ByteArrayOutputStream()
        for (nalu in nalus) {
            val len = nalu.size
            out.write((len shr 24) and 0xFF)
            out.write((len shr 16) and 0xFF)
            out.write((len shr 8) and 0xFF)
            out.write(len and 0xFF)
            out.write(nalu)
        }
        return out.toByteArray()
    }
"""

if old_encoder_loop in content:
    content = content.replace(old_encoder_loop, new_encoder_loop)
    # Add methods to the bottom
    content = content.replace("}", new_encoder_methods + "\n}", 1) # This might replace the wrong bracket, let's be careful.
else:
    print("Could not find old encoder loop")

with open('patch_encoder.py.tmp', 'w') as f:
    f.write(content)
