import re

with open('app/src/test/java/com/example/PartBStreamingPipelineTest.kt', 'r') as f:
    content = f.read()

# Replace the mock input for connect and createStream
new_mock = """        // Build a mock RTMP input stream to satisfy the handshake and connect
        val mockInput = ByteArrayOutputStream()
        
        // Handshake S0, S1, S2 (1 + 1536 + 1536 bytes)
        mockInput.write(ByteArray(3073) { 0x03.toByte() })
        
        // Mock AMF0 _result for connect (Transaction ID 1.0)
        mockInput.write(byteArrayOf(0x03))
        mockInput.write(byteArrayOf(0,0,0, 0,0,21, 20, 0,0,0,0))
        mockInput.write(byteArrayOf(0x02, 0x00, 0x07))
        mockInput.write("_result".toByteArray())
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(63, -16, 0, 0, 0, 0, 0, 0)) // 1.0
        mockInput.write(byteArrayOf(0x05, 0x05)) // two nulls

        // Mock AMF0 _result for createStream (Transaction ID 2.0)
        mockInput.write(byteArrayOf(0x03))
        mockInput.write(byteArrayOf(0,0,0, 0,0,29, 20, 0,0,0,0))
        mockInput.write(byteArrayOf(0x02, 0x00, 0x07))
        mockInput.write("_result".toByteArray())
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(64, 0, 0, 0, 0, 0, 0, 0)) // 2.0
        mockInput.write(byteArrayOf(0x05)) // null
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(63, -16, 0, 0, 0, 0, 0, 0)) // Stream ID 1.0

        // Mock AMF0 onStatus for publish (Transaction ID 3.0)
        mockInput.write(byteArrayOf(0x03))
        mockInput.write(byteArrayOf(0,0,0, 0,0,22, 20, 0,0,0,0))
        mockInput.write(byteArrayOf(0x02, 0x00, 0x08))
        mockInput.write("onStatus".toByteArray())
        mockInput.write(byteArrayOf(0x00))
        mockInput.write(byteArrayOf(64, 8, 0, 0, 0, 0, 0, 0)) // 3.0
        mockInput.write(byteArrayOf(0x05, 0x05))"""

# find existing mockInput blocks and replace them.
import re
content = re.sub(r'// Build a mock RTMP input stream to satisfy the handshake and connect\s+val mockInput = ByteArrayOutputStream\(\).*?(?=\s+val mockSocket =)', new_mock, content, flags=re.DOTALL)

with open('app/src/test/java/com/example/PartBStreamingPipelineTest.kt', 'w') as f:
    f.write(content)
