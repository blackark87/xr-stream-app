package blackark.app.vr.unity

import android.content.Context
import android.net.Uri
import blackark.app.vr.network.SMBConfig
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.Closeable
import java.io.FileInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

interface MediaReader : Closeable {
    val size: Long
    fun seek(position: Long)
    fun read(buffer: ByteArray, length: Int): Int
}

fun localMediaReader(context: Context, uri: String): MediaReader {
    val descriptor = requireNotNull(context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r"))
    try {
        val input = FileInputStream(descriptor.fileDescriptor)
        val offset = descriptor.startOffset
        val length = if (descriptor.length >= 0) descriptor.length else input.channel.size() - offset
        require(length >= 0) { "Document has no seekable length" }
        input.channel.position(offset)
        return object : MediaReader {
            override val size = length
            override fun seek(position: Long) { input.channel.position(offset + position) }
            override fun read(buffer: ByteArray, length: Int) = input.read(buffer, 0, length)
            override fun close() { try { input.close() } finally { descriptor.close() } }
        }
    } catch (error: Exception) { descriptor.close(); throw error }
}

fun smbMediaReader(config: SMBConfig, path: String): MediaReader {
    val properties = Properties().apply {
        setProperty("jcifs.smb.client.minVersion", "SMB202")
        setProperty("jcifs.smb.client.maxVersion", "SMB311")
        setProperty("jcifs.resolveOrder", "DNS")
        setProperty("jcifs.smb.client.snd_buf_size", "1048576")
        setProperty("jcifs.smb.client.rcv_buf_size", "1048576")
        setProperty("jcifs.smb.client.responseTimeout", "15000")
    }
    val base = BaseContext(PropertyConfiguration(properties))
    val context = base.withCredentials(NtlmPasswordAuthenticator(config.domain.ifEmpty { null }, config.username, config.password))
    var source: SmbFile? = null
    var file: SmbRandomAccessFile? = null
    try {
        source = SmbFile(path, context)
        file = SmbRandomAccessFile(source, "r")
        val reader = file
        val owner = source
        val length = reader.length()
        return object : MediaReader {
            override val size = length
            override fun seek(position: Long) = reader.seek(position)
            override fun read(buffer: ByteArray, length: Int) = reader.read(buffer, 0, length)
            override fun close() {
                try { reader.close() } finally {
                    try { owner.close() } finally { context.close(); base.close() }
                }
            }
        }
    } catch (error: Exception) {
        runCatching { file?.close() }; runCatching { source?.close() }
        runCatching { context.close() }; runCatching { base.close() }
        throw error
    }
}

/** Loopback-only byte-range transport; no full-movie download or CPU-frame upload. */
class RangeMediaServer(
    private val onTrace: (String) -> Unit = {},
    private val onError: (Exception) -> Unit = UnityBridge::postTransportError,
) : Closeable {
    private data class Source(val size: Long, val open: () -> MediaReader)
    private val sources = ConcurrentHashMap<String, Source>()
    private val clients = ConcurrentHashMap<Socket, String>()
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val workers = ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16))
    private val requestIds = AtomicLong()
    @Volatile private var running = true
    init {
        Thread({
            while (running) try {
                val socket = server.accept()
                socket.soTimeout = 15000
                clients[socket] = ""
                try { workers.execute { serve(socket) } }
                catch (error: Exception) { clients.remove(socket); socket.close(); if (running) onError(error) }
            } catch (error: Exception) { if (running) onError(error) }
        }, "UnityMediaAccept").apply { isDaemon = true; start() }
    }
    fun register(open: () -> MediaReader): Pair<String, String> {
        val size = open().use { it.size }
        require(size > 0) { "Video source is empty" }
        val token = UUID.randomUUID().toString()
        sources[token] = Source(size, open)
        return token to "http://127.0.0.1:${server.localPort}/$token/media"
    }
    fun release(token: String) {
        sources.remove(token)
        clients.entries.filter { it.value == token }.forEach { runCatching { it.key.close() } }
    }
    private fun serve(socket: Socket) {
        socket.use {
            var headersSent = false
            val requestId = requestIds.incrementAndGet()
            val started = System.nanoTime()
            var receivedRequest = false
            var transferred = 0L
            var readNanos = 0L
            var writeNanos = 0L
            var outcome = "complete"
            try {
                val input = socket.getInputStream()
                val header = StringBuilder()
                while (header.length < 16384 && !header.endsWith("\r\n\r\n")) {
                    val value = input.read()
                    if (value < 0) return
                    header.append(value.toChar())
                }
                if (!header.endsWith("\r\n\r\n")) return
                val lines = header.toString().split("\r\n")
                val request = lines[0].split(' ')
                if (request.size != 3 || request[0] !in listOf("GET", "HEAD")) {
                    respond(socket, "405 Method Not Allowed", "Content-Length: 0\r\n"); return
                }
                val token = request[1].split('/').getOrNull(1).orEmpty()
                clients[socket] = token
                val source = sources[token]
                if (source == null) { respond(socket, "404 Not Found", "Content-Length: 0\r\n"); return }
                val rangeHeader = lines.firstOrNull { it.startsWith("Range:", true) }?.substringAfter(':')?.trim()
                val range = try { HttpByteRange.parse(rangeHeader, source.size) } catch (_: IllegalArgumentException) {
                    respond(socket, "416 Range Not Satisfiable", "Content-Range: bytes */${source.size}\r\nContent-Length: 0\r\n"); return
                }
                val start = range?.start ?: 0
                var remaining = range?.length ?: source.size
                receivedRequest = true
                onTrace("request=$requestId method=${request[0]} start=$start length=$remaining workers=${workers.activeCount} queued=${workers.queue.size}")
                val fields = buildString {
                    append("Accept-Ranges: bytes\r\nContent-Type: application/octet-stream\r\nContent-Length: $remaining\r\n")
                    if (range != null) append("Content-Range: bytes ${range.start}-${range.endInclusive}/${source.size}\r\n")
                }
                if (request[0] == "HEAD") {
                    respond(socket, if (range == null) "200 OK" else "206 Partial Content", fields); return
                }
                source.open().use { reader ->
                    reader.seek(start)
                    onTrace("ready=$requestId elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
                    respond(socket, if (range == null) "200 OK" else "206 Partial Content", fields)
                    headersSent = true
                    val output = socket.getOutputStream()
                    val buffer = ByteArray(1024 * 1024)
                    while (remaining > 0 && sources.containsKey(token) && running) {
                        var sampleStarted = System.nanoTime()
                        val read = reader.read(buffer, minOf(remaining, buffer.size.toLong()).toInt())
                        readNanos += System.nanoTime() - sampleStarted
                        if (read < 0) break
                        if (read == 0) throw java.io.IOException("Source returned an empty read")
                        sampleStarted = System.nanoTime()
                        output.write(buffer, 0, read)
                        writeNanos += System.nanoTime() - sampleStarted
                        transferred += read
                        remaining -= read
                    }
                }
            } catch (error: Exception) {
                outcome = error.javaClass.simpleName
                val token = clients[socket]
                if (!socket.isClosed && token != null && sources.containsKey(token)) {
                    // Socket disconnects during seeks are expected; source failures are surfaced.
                    if (error !is java.net.SocketException) onError(error)
                    if (!headersSent) runCatching { respond(socket, "500 Internal Server Error", "Content-Length: 0\r\n") }
                }
            } finally {
                if (receivedRequest) onTrace("finish=$requestId bytes=$transferred readMs=${readNanos / 1_000_000} writeMs=${writeNanos / 1_000_000} elapsedMs=${(System.nanoTime() - started) / 1_000_000} outcome=$outcome")
                clients.remove(socket)
                // Send FIN, then consume the peer's close before disposing the input side.
                // Immediate full close can reset an otherwise complete short response on Windows.
                runCatching { socket.shutdownOutput() }
                runCatching {
                    socket.soTimeout = 250
                    val drain = ByteArray(1024)
                    var remaining = 16384
                    while (remaining > 0) {
                        val read = socket.getInputStream().read(drain, 0, minOf(remaining, drain.size))
                        if (read <= 0) break
                        remaining -= read
                    }
                }
            }
        }
    }
    private fun respond(socket: Socket, status: String, fields: String) {
        socket.getOutputStream().write("HTTP/1.1 $status\r\nConnection: close\r\n$fields\r\n".toByteArray(Charsets.US_ASCII))
    }
    override fun close() {
        running = false; sources.clear()
        runCatching { server.close() }
        clients.keys.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }
}
