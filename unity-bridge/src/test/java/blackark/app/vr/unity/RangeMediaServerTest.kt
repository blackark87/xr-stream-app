package blackark.app.vr.unity

import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class RangeMediaServerTest {
    @Test fun servesFullPartialSuffixAndHeadWithoutLeakingReaders() {
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val openReaders = AtomicInteger()
        val opens = AtomicInteger()
        val errors = CopyOnWriteArrayList<Exception>()
        RangeMediaServer(onError = { errors.add(it) }).use { server ->
            val (token, url) = server.register {
                openReaders.incrementAndGet(); opens.incrementAndGet()
                object : MediaReader {
                    var position = 0
                    override val size = bytes.size.toLong()
                    override fun seek(position: Long) { this.position = position.toInt() }
                    override fun read(buffer: ByteArray, length: Int): Int {
                        if (position >= bytes.size) return -1
                        val count = minOf(length, bytes.size - position)
                        bytes.copyInto(buffer, 0, position, position + count); position += count
                        return count
                    }
                    override fun close() { openReaders.decrementAndGet() }
                }
            }
            val full = request(url)
            assertTrue(full.header.startsWith("HTTP/1.1 200"))
            assertArrayEquals(bytes, full.body)
            val partial = request(url, "bytes=100-299")
            assertTrue(partial.header.contains("Content-Range: bytes 100-299/4096"))
            assertTrue(partial.header.startsWith("HTTP/1.1 206"))
            assertArrayEquals(bytes.copyOfRange(100, 300), partial.body)
            assertArrayEquals(bytes.copyOfRange(4080, 4096), request(url, "bytes=-16").body)
            val beforeHead = opens.get()
            val head = request(url, method = "HEAD")
            assertTrue(head.header.contains("Content-Length: 4096"))
            assertEquals(0, head.body.size)
            assertEquals(beforeHead, opens.get())
            assertTrue(request(url, "bytes=4096-").header.startsWith("HTTP/1.1 416"))
            server.release(token)
            repeat(20) { assertTrue(request(url).header.startsWith("HTTP/1.1 404")) }
        }
        assertEquals(0, openReaders.get())
        assertTrue(errors.toString(), errors.isEmpty())
    }
    private data class Response(val header: String, val body: ByteArray)
    private fun request(url: String, range: String? = null, method: String = "GET"): Response {
        val uri = URI(url)
        return Socket(uri.host, uri.port).use { socket ->
            socket.soTimeout = 3000
            val rangeHeader = range?.let { "Range: $it\r\n" }.orEmpty()
            socket.getOutputStream().write("$method ${uri.path} HTTP/1.1\r\nHost: 127.0.0.1\r\n${rangeHeader}Connection: close\r\n\r\n".toByteArray())
            val input = socket.getInputStream()
            val header = StringBuilder()
            while (!header.endsWith("\r\n\r\n")) {
                val next = input.read()
                assertTrue("Missing HTTP header", next >= 0)
                header.append(next.toChar())
            }
            val contentLength = header.toString().split("\r\n").first { it.startsWith("Content-Length:") }
                .substringAfter(':').trim().toInt()
            val expectedLength = if (method == "HEAD") 0 else contentLength
            val body = input.readNBytes(expectedLength)
            assertEquals("Truncated response body", expectedLength, body.size)
            Response(header.toString().trimEnd(), body)
        }
    }
}
