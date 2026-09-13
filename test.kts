import java.io.ByteArrayOutputStream
import java.net.Socket
import java.io.OutputStream
import java.io.InputStream
import java.net.URI

val mockSocket = object : Socket() {
    override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
    override fun getInputStream(): InputStream = ByteArray(0).inputStream()
    override fun close() {}
}

val s = mockSocket
println("Success!")
