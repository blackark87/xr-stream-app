package com.example.myapplication.utils

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.net.toUri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.example.myapplication.AppState
import okio.Buffer
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream

/**
 * Custom Coil Fetcher for extracting video thumbnails from SMB and local files
 */
class VideoThumbnailFetcher(
    private val data: String,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        return try {
            val uri = data.toUri()

            when (uri.scheme) {
                "smb" -> {
                    // For SMB videos, extract thumbnail using MediaMetadataRetriever with custom data source
                    extractSMBThumbnail(data)
                }
                "file", null -> {
                    // For local files, use standard MediaMetadataRetriever
                    extractLocalThumbnail(data)
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("VideoThumbnailFetcher", "Error fetching thumbnail for $data", e)
            null
        }
    }

    private fun extractLocalThumbnail(path: String): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val bitmap = retriever.getFrameAtTime(
                0, // Get frame at beginning
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null

            // Convert bitmap to Coil-compatible result
            val buffer = Buffer()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, buffer.outputStream())

            SourceResult(
                source = ImageSource(buffer, options.context),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            Log.e("VideoThumbnailFetcher", "Error extracting local thumbnail", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e("VideoThumbnailFetcher", "Error releasing retriever", e)
            }
        }
    }

    private fun extractSMBThumbnail(smbUrl: String): FetchResult? {
        Log.d("VideoThumbnailFetcher", "Extracting SMB thumbnail for: $smbUrl")
        var tempFile: java.io.File? = null
        val retriever = MediaMetadataRetriever()

        return try {
            // Create temp file
            tempFile = java.io.File.createTempFile("smb_thumb_", ".mp4", options.context.cacheDir)
            Log.d("VideoThumbnailFetcher", "Created temp file: ${tempFile.absolutePath}")

            // Download header (first 10MB)
            val smbFile = com.example.myapplication.AppState.smbClient?.getSmbFile(smbUrl)
            if (smbFile == null) {
                Log.e("VideoThumbnailFetcher", "SMB client is null!")
                return null
            }
            Log.d("VideoThumbnailFetcher", "Got SMB file, downloading...")
            var totalBytes = 0L
            smbFile.inputStream.use { input: java.io.InputStream ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    val maxBytes = 10 * 1024 * 1024L // 10MB limit

                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1 && totalBytes < maxBytes) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        bytesRead = input.read(buffer)
                    }
                }
            }

            Log.d("VideoThumbnailFetcher", "Downloaded $totalBytes bytes")

            // Extract frame from temp file
            retriever.setDataSource(tempFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap == null) {
                Log.e("VideoThumbnailFetcher", "Failed to extract frame from video")
                return null
            }
            Log.d("VideoThumbnailFetcher", "Extracted bitmap: ${bitmap.width}x${bitmap.height}")

            // Convert to Coil result
            val buffer = Buffer()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, buffer.outputStream())

            Log.d("VideoThumbnailFetcher", "Successfully created thumbnail")
            SourceResult(
                source = ImageSource(buffer, options.context),
                mimeType = "image/jpeg",
                dataSource = DataSource.NETWORK
            )

        } catch (e: Exception) {
            Log.e("VideoThumbnailFetcher", "Error extracting SMB thumbnail", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e("VideoThumbnailFetcher", "Error releasing retriever", e)
            }
            // Clean up temp file
            try {
                tempFile?.delete()
            } catch (e: Exception) {
                Log.e("VideoThumbnailFetcher", "Error deleting temp file", e)
            }
        }
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Only handle video files
            if (!isVideoFile(data)) return null

            return VideoThumbnailFetcher(data, options)
        }

        private fun isVideoFile(path: String): Boolean {
            val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm", ".m4v")
            return videoExtensions.any { path.lowercase().endsWith(it) }
        }
    }
}
