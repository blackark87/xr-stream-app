package com.example.myapplication.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.example.myapplication.network.SMBConfig
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.IOException
import java.util.Properties

private const val TAG = "SMBDataSource"

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

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        Log.d(TAG, "open() called for ${dataSpec.uri}, position: ${dataSpec.position}, length: ${dataSpec.length}")
        currentUri = dataSpec.uri
        try {
            // Initialize CIFS context if not already done
            if (cifsContext == null) {
                val props = Properties().apply {
                    setProperty("jcifs.smb.client.minVersion", "SMB202")
                    setProperty("jcifs.smb.client.maxVersion", "SMB311")
                    setProperty("jcifs.resolveOrder", "DNS")
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
            val filePath = dataSpec.uri.path ?: throw IOException("Invalid URI path")

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

            opened = true
            transferStarted(dataSpec)

            println("SMBDataSource: Stream opened, bytes remaining: ${bytesRemaining / 1024}KB")
            return if (bytesRemaining == 0L) C.LENGTH_UNSET.toLong() else bytesRemaining
        } catch (e: Exception) {
            Log.e(TAG, "Error opening: ${e.message}", e)
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

        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(bytesRemaining, length.toLong()).toInt()
        }

        val bytesRead = try {
            randomAccessFile?.read(buffer, offset, bytesToRead) ?: C.RESULT_END_OF_INPUT
        } catch (e: IOException) {
            Log.e(TAG, "Error reading from SMB stream: ${e.message}", e)
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
            Log.d(TAG, "Streaming... Read ${totalBytesRead / 1024}KB total (chunk: ${bytesRead / 1024}KB)")
            lastLogTime = currentTime
        }

        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? {
        return currentUri
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            randomAccessFile?.close()
        } catch (e: IOException) {
            throw IOException("Error closing SMB random access file", e)
        } finally {
            randomAccessFile = null
            smbFile = null
            bytesRemaining = 0
            currentUri = null

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
