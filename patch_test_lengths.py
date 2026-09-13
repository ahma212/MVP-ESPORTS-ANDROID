import re

with open('app/src/test/java/com/example/PartBStreamingPipelineTest.kt', 'r') as f:
    content = f.read()

# Fix connect message length: 30 -> 21
content = content.replace("byteArrayOf(0,0,0, 0,0,30, 20, 0,0,0,0)", "byteArrayOf(0,0,0, 0,0,21, 20, 0,0,0,0)", 1)
content = content.replace("byteArrayOf(0,0,0, 0,0,30, 20, 0,0,0,0)", "byteArrayOf(0,0,0, 0,0,21, 20, 0,0,0,0)", 1)

# Fix createStream message length: 30 -> 29
content = content.replace("byteArrayOf(0,0,0, 0,0,30, 20, 0,0,0,0)", "byteArrayOf(0,0,0, 0,0,29, 20, 0,0,0,0)", 1)
content = content.replace("byteArrayOf(0,0,0, 0,0,30, 20, 0,0,0,0)", "byteArrayOf(0,0,0, 0,0,29, 20, 0,0,0,0)", 1)

# Fix publish message length: 30 -> 22
content = content.replace("byteArrayOf(0,0,0, 0,0,30, 20, 0,0,0,0)", "byteArrayOf(0,0,0, 0,0,22, 20, 0,0,0,0)")

with open('app/src/test/java/com/example/PartBStreamingPipelineTest.kt', 'w') as f:
    f.write(content)
