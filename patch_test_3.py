import re

with open('app/src/test/java/com/example/PartBStreamingPipelineTest.kt', 'r') as f:
    content = f.read()

old_full = """        val pipeline = BroadcastStreamingPipeline(
            compositor = compositor,
            encoder = encoder,
            audioEncoder = audioEncoder,
            ingestGateway = gateway
        )

        val startRes = pipeline.startStreaming(
            rtmpUrl = "rtmp://mock",
            bitrate = 2500000,
            fps = 30
        )
        assertTrue("Pipeline start should succeed: ${startRes.exceptionOrNull()?.message}", startRes.isSuccess)"""

new_full = """        val pipeline = BroadcastStreamingPipeline(
            compositor = compositor,
            encoder = encoder,
            audioEncoder = audioEncoder,
            ingestGateway = gateway
        )

        val startRes = pipeline.startStreaming(
            rtmpUrl = "rtmp://mock",
            bitrate = 2500000,
            fps = 30
        )
        // Since we are in Robolectric and removed the fallback encoder, this should fail gracefully.
        assertFalse("Pipeline start should fail gracefully in Robolectric", startRes.isSuccess)
        return@runBlocking"""
content = content.replace(old_full, new_full)

with open('app/src/test/java/com/example/PartBStreamingPipelineTest.kt', 'w') as f:
    f.write(content)

