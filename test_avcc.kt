import java.io.ByteArrayOutputStream

fun extractNalus(data: ByteArray, length: Int): List<ByteArray> {
    val nalus = mutableListOf<ByteArray>()
    var i = 0
    var start = -1
    while (i < length - 2) {
        if (data[i] == 0.toByte() && data[i+1] == 0.toByte() && data[i+2] == 1.toByte()) {
            if (start != -1) {
                val naluLen = if (i > 0 && data[i-1] == 0.toByte()) i - 1 - start else i - start
                val nalu = ByteArray(naluLen)
                System.arraycopy(data, start, nalu, 0, naluLen)
                nalus.add(nalu)
            }
            start = i + 3
            i += 3
        } else {
            i++
        }
    }
    if (start != -1 && start < length) {
        val nalu = ByteArray(length - start)
        System.arraycopy(data, start, nalu, 0, length - start)
        nalus.add(nalu)
    }
    return nalus
}

fun main() {
    val sample = byteArrayOf(0, 0, 0, 1, 10, 11, 12, 0, 0, 1, 13, 14, 0, 0, 0, 1, 15)
    val nalus = extractNalus(sample, sample.size)
    for (n in nalus) {
        println(n.joinToString(",") { it.toString() })
    }
}
