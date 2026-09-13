import re

with open('app/src/main/java/com/example/services/streaming/MediaCodecVideoEncoder.kt', 'r') as f:
    content = f.read()

# Replace the fallback block in configureEncoder
old_config = """            } catch (e: Exception) {
                // Graceful fallback for environments lacking native OMX/C2 codecs (e.g. Robolectric test runner)
                Log.w(TAG, "Hardware MediaCodec not available on current runtime: ${e.message}. Using AVC stream packetizer fallback.")
                mediaCodec = null
                isHardwareCodec = false
            }"""
new_config = """            } catch (e: Exception) {
                Log.e(TAG, "Hardware MediaCodec initialization failed: ${e.message}", e)
                mediaCodec = null
                isHardwareCodec = false
                isRunning = false
                return Result.failure(e)
            }"""
content = content.replace(old_config, new_config)

# Replace the fallback block in encodeFrame
old_encode = """            val encodedBytes: ByteArray = if (isHardwareCodec && mediaCodec != null) {
                try {
                    val hwBytes = encodeHardwareFrame(bitmap, effectivePtsUs)
                    if (hwBytes.isNotEmpty()) {
                        hwBytes
                    } else {
                        // In virtualized / shadow environments without native DSP, use standard AVC packetizer fallback
                        encodeFallbackFrame(effectivePtsUs)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Hardware encoding exception: ${e.message}. Using AVC packetizer fallback.")
                    encodeFallbackFrame(effectivePtsUs)
                }
            } else {
                encodeFallbackFrame(effectivePtsUs)
            }"""
new_encode = """            val encodedBytes: ByteArray = if (isHardwareCodec && mediaCodec != null) {
                try {
                    encodeHardwareFrame(bitmap, effectivePtsUs)
                } catch (e: Exception) {
                    Log.e(TAG, "Hardware encoding exception: ${e.message}", e)
                    ByteArray(0)
                }
            } else {
                ByteArray(0)
            }"""
content = content.replace(old_encode, new_encode)

# Fix duplicate `return resultBytes` which I might have introduced
content = content.replace("        return resultBytes\n    }\n        return resultBytes\n    }", "        return resultBytes\n    }")

with open('app/src/main/java/com/example/services/streaming/MediaCodecVideoEncoder.kt', 'w') as f:
    f.write(content)
