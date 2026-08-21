@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class],
)

package blackark.app.vr.player

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import blackark.app.vr.network.SMBConfig
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.IOException
import java.util.Properties

private const val TAG = "SMBDataSource"
private const val SMB_IO_BUFFER_SIZE = 1024 * 1024

class SMBDataSource(
    private val smbConfig: SMBConfig
) : BaseDataSource(true) {

    init {
        Log.d(TAG, "Created instance ${this.hashCode()}")
    }

    private var cifsContext: CIFSContext? = null
    private var smbFile: SmbFile? = null
    private var randomAccessFile: SmbRandomAccessFile? = null
    private var bytesRemaining: Long = 0
    private var opened = false
    private var currentUri: Uri? = null
    private val readAheadBuffer = ByteArray(SMB_IO_BUFFER_SIZE)
    private var readAheadOffset = 0
    private var readAheadLength = 0
    private var remoteReadCount = 0L
    private var remoteBytesRead = 0L
    private var remoteReadElapsedMs = 0L
    private var lastRemoteStatsLogTimeMs = 0L

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        Log.d(
            TAG,
            "open() called for ${dataSpec.uri}, position: ${dataSpec.position}, length: ${dataSpec.length}"
        )
        currentUri = dataSpec.uri
        val requestName = dataSpec.uri.lastPathSegment.orEmpty()
        val isSubtitleRequest =
            requestName.endsWith(".srt", ignoreCase = true) ||
                    requestName.endsWith(".ass", ignoreCase = true)
        if (isSubtitleRequest) {
            Log.i(
                TAG,
                "[SubtitleDebug] SMB open requested file=$requestName position=${dataSpec.position}",
            )
        }
        try {
            // Initialize CIFS context if not already done
            if (cifsContext == null) {
                val props = Properties().apply {
                    setProperty("jcifs.smb.client.minVersion", "SMB202")
                    setProperty("jcifs.smb.client.maxVersion", "SMB311")
                    setProperty("jcifs.resolveOrder", "DNS")
                    // SmbRandomAccessFile derives each SMB read request from these negotiated
                    // send/receive sizes. "jcifs.smb.client.bufferSize" is not an I/O buffer
                    // setting in jcifs-ng 2.1.10 and left reads at the 65,535-byte default.
                    setProperty("jcifs.smb.client.snd_buf_size", SMB_IO_BUFFER_SIZE.toString())
                    setProperty("jcifs.smb.client.rcv_buf_size", SMB_IO_BUFFER_SIZE.toString())
                }

                val baseContext = BaseContext(PropertyConfiguration(props))

                val auth = NtlmPasswordAuthenticator(
                    smbConfig.domain.ifEmpty { null },
                    smbConfig.username,
                    smbConfig.password
                )

                cifsContext = baseContext.withCredentials(auth)
            }

            // Parse the file path from the URI
            dataSpec.uri.path ?: throw IOException("Invalid URI path")

            // Use the URI directly as it's already correctly formatted by the ViewModel
            val smbUrl = dataSpec.uri.toString()
            Log.d(TAG, "Opening file: $smbUrl")

            // Open SMB file
            smbFile = SmbFile(smbUrl, cifsContext)

            if (!smbFile!!.exists()) {
                throw IOException("SMB file does not exist: $smbUrl")
            }

            if (smbFile!!.isDirectory) {
                throw IOException("SMB path is a directory, not a file: $smbUrl")
            }

            // Open RandomAccessFile for reading
            randomAccessFile = SmbRandomAccessFile(smbFile, "r")

            val fileLength = randomAccessFile!!.length()
            Log.d(TAG, "File length: ${fileLength / 1024 / 1024}MB")
            if (isSubtitleRequest) {
                Log.i(
                    TAG,
                    "[SubtitleDebug] SMB subtitle opened file=$requestName " +
                            "length=$fileLength",
                )
            }

            // Handle range request (seek)
            if (dataSpec.position > 0) {
                Log.d(TAG, "Seeking to position: ${dataSpec.position / 1024}KB")
                randomAccessFile!!.seek(dataSpec.position)
            }

            // Calculate bytes remaining
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                fileLength - dataSpec.position
            }
            readAheadOffset = 0
            readAheadLength = 0

            opened = true
            transferStarted(dataSpec)

            println("SMBDataSource: Stream opened, bytes remaining: ${bytesRemaining / 1024}KB")
            return if (bytesRemaining == 0L) C.LENGTH_UNSET.toLong() else bytesRemaining
        } catch (e: Exception) {
            if (isSubtitleRequest) {
                Log.e(
                    TAG,
                    "[SubtitleDebug] SMB subtitle open failed file=$requestName",
                    e,
                )
            }
            Log.e(TAG, "Error opening: ${e.message}", e)
            runCatching { randomAccessFile?.close() }
            randomAccessFile = null
            smbFile = null
            runCatching { cifsContext?.close() }
            cifsContext = null
            throw IOException("Failed to open SMB data source", e)
        }
    }

    private var totalBytesRead = 0L
    private var lastLogTime = 0L
    private var readCallCount = 0

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        readCallCount++
        if (readCallCount <= 10) {
            Log.d(TAG, "read() call #$readCallCount - Requesting $length bytes, Offset: $offset")
        }

        if (length == 0) {
            return 0
        }

        val bytesRead = try {
            if (readAheadOffset >= readAheadLength) {
                if (bytesRemaining == 0L) {
                    return C.RESULT_END_OF_INPUT
                }
                readAheadLength = fillReadAheadBuffer()
                readAheadOffset = 0
                if (readAheadLength <= 0) {
                    return C.RESULT_END_OF_INPUT
                }
            }

            val bufferedBytes = readAheadLength - readAheadOffset
            val bytesToCopy = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                minOf(length, bufferedBytes)
            } else {
                minOf(length.toLong(), bufferedBytes.toLong(), bytesRemaining).toInt()
            }
            readAheadBuffer.copyInto(
                destination = buffer,
                destinationOffset = offset,
                startIndex = readAheadOffset,
                endIndex = readAheadOffset + bytesToCopy,
            )
            readAheadOffset += bytesToCopy
            bytesToCopy
        } catch (e: InterruptedException) {
            // Player is being closed, return end of input gracefully
            Log.d(TAG, "Read interrupted (player closing)")
            return C.RESULT_END_OF_INPUT
        } catch (e: jcifs.smb.SmbException) {
            // Check if it's an interrupted exception wrapped in SmbException
            if (e.cause is InterruptedException || e.message?.contains("InterruptedException") == true) {
                Log.d(TAG, "Read interrupted via SmbException (player closing)")
                return C.RESULT_END_OF_INPUT
            }
            Log.e(TAG, "SMB error reading stream: ${e.message}", e)
            invalidateConnectionAfterReadFailure()
            throw IOException("SMB error reading stream", e)
        } catch (e: IOException) {
            // Check if it's an interrupted exception wrapped in IOException
            if (e.cause is InterruptedException) {
                Log.d(TAG, "Read interrupted via IOException (player closing)")
                return C.RESULT_END_OF_INPUT
            }
            Log.e(TAG, "Error reading from SMB stream: ${e.message}", e)
            invalidateConnectionAfterReadFailure()
            throw IOException("Error reading from SMB stream", e)
        }

        if (readCallCount <= 10) {
            Log.d(TAG, "read() call #$readCallCount - Read $bytesRead bytes")
        }

        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead.toLong()
        }

        totalBytesRead += bytesRead

        // Log every 1MB to show streaming progress
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLogTime > 1000) { // Log every second
            Log.d(
                TAG,
                "Streaming... Read ${totalBytesRead / 1024}KB total (chunk: ${bytesRead / 1024}KB)"
            )
            lastLogTime = currentTime
        }

        bytesTransferred(bytesRead)
        return bytesRead
    }

    private fun fillReadAheadBuffer(): Int {
        val file = randomAccessFile ?: return C.RESULT_END_OF_INPUT
        val targetLength = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            readAheadBuffer.size
        } else {
            minOf(bytesRemaining, readAheadBuffer.size.toLong()).toInt()
        }
        var filled = 0
        var requestCount = 0
        val startedAtMs = SystemClock.elapsedRealtime()
        while (filled < targetLength) {
            val count = file.read(readAheadBuffer, filled, targetLength - filled)
            if (count <= 0) break
            filled += count
            requestCount++
        }
        val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        remoteReadCount += requestCount
        remoteBytesRead += filled
        remoteReadElapsedMs += elapsedMs
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastRemoteStatsLogTimeMs >= 1_000L) {
            val effectiveMbps =
                if (remoteReadElapsedMs == 0L) {
                    0.0
                } else {
                    remoteBytesRead * 8.0 / remoteReadElapsedMs / 1_000.0
                }
            Log.d(
                TAG,
                "Read-ahead stats remoteReads=$remoteReadCount " +
                    "remoteBytes=$remoteBytesRead remoteElapsedMs=$remoteReadElapsedMs " +
                    "effectiveMbps=${"%.2f".format(java.util.Locale.US, effectiveMbps)} " +
                    "lastFillBytes=$filled lastFillRequests=$requestCount lastFillMs=$elapsedMs",
            )
            lastRemoteStatsLogTimeMs = nowMs
        }
        return if (filled == 0) C.RESULT_END_OF_INPUT else filled
    }

    private fun invalidateConnectionAfterReadFailure() {
        runCatching { randomAccessFile?.close() }
        randomAccessFile = null
        smbFile = null
        runCatching { cifsContext?.close() }
        cifsContext = null
        readAheadOffset = 0
        readAheadLength = 0
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    override fun getUri(): Uri? {
        return currentUri
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            randomAccessFile?.close()
        } catch (e: jcifs.smb.SmbException) {
            // Ignore interruption exceptions during close - they're expected
            if (e.cause !is InterruptedException && e.message?.contains("InterruptedException") != true) {
                throw IOException("Error closing SMB random access file", e)
            } else {
                Log.d(TAG, "Close interrupted (expected during shutdown)")
            }
        } catch (e: IOException) {
            // Ignore interruption exceptions during close - they're expected
            if (e.cause !is InterruptedException) {
                throw IOException("Error closing SMB random access file", e)
            } else {
                Log.d(TAG, "Close interrupted (expected during shutdown)")
            }
        } finally {
            randomAccessFile = null
            smbFile = null
            runCatching { cifsContext?.close() }
            cifsContext = null
            bytesRemaining = 0
            currentUri = null
            readAheadOffset = 0
            readAheadLength = 0

            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(
        private val smbConfig: SMBConfig
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            Log.d(TAG, "Factory.createDataSource() called")
            return SMBDataSource(smbConfig)
        }
    }
}
