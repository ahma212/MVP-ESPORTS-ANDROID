import re

with open('app/src/main/java/com/example/services/streaming/BroadcastStreamingPipeline.kt', 'r') as f:
    content = f.read()

# Remove the RTMP transmit from processComposedFrameInternal
old_process = """                // 2. Deliver Encoded Video Frames to Existing Streaming Pipeline (RTMP Gateway)
                if (ingestGateway.isConnected) {
                    val transmitRes = if (ingestGateway is RtmpIngestGateway) {
                        val isKey = (framesEncodedCount.get() % 30L == 1L)
                        ingestGateway.transmitVideoPacket(
                            encodedBytes,
                            encodedBytes.size,
                            isKeyFrame = isKey,
                            timestampMs = composedFrame.timestampMs
                        )
                    } else {
                        ingestGateway.transmitMuxedData(encodedBytes, encodedBytes.size)
                    }

                    if (transmitRes.isSuccess) {
                        packetsTransmittedCount.incrementAndGet()
                        bytesTransmittedCount.addAndGet(encodedBytes.size.toLong())
                        lastRtmpSendTimestamp.set(System.currentTimeMillis())
                    } else {
                        lastErrorState.value = transmitRes.exceptionOrNull()?.message ?: "RTMP transmission failed"
                    }
                }"""

new_process = """                // The actual RTMP transmission is now handled asynchronously in the encodedFrames collector
                // to ensure we get proper isCodecConfig and isKeyFrame flags."""
content = content.replace(old_process, new_process)

# Update the collector to send to RTMP
old_collect = """            // Forward encoder packets to pipeline encoded flow if supported
            encoder.encodedFrames?.let { encoderFlow ->
                pipelineScope.launch {
                    encoderFlow.collect { encodedFrame ->
                        _encodedFrames.tryEmit(encodedFrame)
                    }
                }
            }"""

new_collect = """            // Forward encoder packets to pipeline encoded flow if supported and transmit to RTMP
            encoder.encodedFrames?.let { encoderFlow ->
                pipelineScope.launch {
                    encoderFlow.collect { encodedFrame ->
                        _encodedFrames.tryEmit(encodedFrame)
                        
                        if (ingestGateway.isConnected) {
                            val transmitRes = if (ingestGateway is RtmpIngestGateway) {
                                ingestGateway.transmitVideoPacket(
                                    encodedFrame.data,
                                    encodedFrame.data.size,
                                    isKeyFrame = encodedFrame.isKeyFrame,
                                    timestampMs = encodedFrame.presentationTimeUs / 1000L,
                                    isCodecConfig = encodedFrame.isCodecConfig
                                )
                            } else {
                                ingestGateway.transmitMuxedData(encodedFrame.data, encodedFrame.data.size)
                            }

                            if (transmitRes.isSuccess) {
                                packetsTransmittedCount.incrementAndGet()
                                bytesTransmittedCount.addAndGet(encodedFrame.data.size.toLong())
                                lastRtmpSendTimestamp.set(System.currentTimeMillis())
                            } else {
                                lastErrorState.value = transmitRes.exceptionOrNull()?.message ?: "RTMP transmission failed"
                            }
                        }
                    }
                }
            }"""
content = content.replace(old_collect, new_collect)

with open('app/src/main/java/com/example/services/streaming/BroadcastStreamingPipeline.kt', 'w') as f:
    f.write(content)

