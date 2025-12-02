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
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath

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

            // Process bitmap for SBS 3D if needed
            val processedBitmap = processBitmap(bitmap)

            // Convert bitmap to Coil-compatible result
            val buffer = Buffer()
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, buffer.outputStream())

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

    private suspend fun extractSMBThumbnail(smbUrl: String): FetchResult? {
        Log.d("VideoThumbnailFetcher", "Extracting SMB thumbnail for: $smbUrl")
        
        // 1. Check for existing local thumbnail based on hash
        val context = options.context
        val cacheDir = java.io.File(context.filesDir, "thumbnails")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val fileNameHash = java.security.MessageDigest.getInstance("MD5")
            .digest(smbUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val localFile = java.io.File(cacheDir, "$fileNameHash.jpg")
        
        if (localFile.exists()) {
            Log.d("VideoThumbnailFetcher", "Found cached thumbnail: ${localFile.absolutePath}")
            return SourceResult(
                source = ImageSource(file = localFile.toOkioPath(), fileSystem = FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        }

        var tempFile: java.io.File? = null
        val retriever = MediaMetadataRetriever()

        return try {
            // Create temp file with unique name
            val uniqueId = System.currentTimeMillis()
            tempFile = java.io.File.createTempFile(
                "smb_thumb_${uniqueId}_",
                ".mp4",
                options.context.cacheDir
            )
            Log.d("VideoThumbnailFetcher", "Created temp file: ${tempFile.absolutePath}")

            // Download header (first 20MB)
            val smbClient = com.example.myapplication.AppState.smbClient
            if (smbClient == null) {
                Log.e("VideoThumbnailFetcher", "SMB client is null! Cannot fetch thumbnail.")
                return null
            }

            Log.d("VideoThumbnailFetcher", "Getting SMB file reference...")
            val smbFile = smbClient.getSmbFile(smbUrl)
            // Skip exists() check as we just listed it. It causes extra network roundtrip.

            Log.d("VideoThumbnailFetcher", "Got SMB file, downloading header...")
            var totalBytes = 0L
            val startTime = System.currentTimeMillis()
            var lastLogTime = startTime

            smbFile.inputStream.use { input: java.io.InputStream ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024) // 64KB buffer
                    val maxBytes = 20 * 1024 * 1024L // 20MB limit (reduced from 150MB for speed)

                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1 && totalBytes < maxBytes) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead

                        // Log progress every 5MB or 1 second
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastLogTime > 1000) {
                            Log.d("VideoThumbnailFetcher", "Downloading... $totalBytes bytes")
                            lastLogTime = currentTime
                        }

                        bytesRead = input.read(buffer)
                    }
                }
            }

            val downloadTime = System.currentTimeMillis() - startTime
            Log.d(
                "VideoThumbnailFetcher",
                "Downloaded $totalBytes bytes in ${downloadTime}ms. File size: ${tempFile.length()}"
            )

            // Extract frame from temp file
            Log.d("VideoThumbnailFetcher", "Setting data source for MediaMetadataRetriever...")
            retriever.setDataSource(tempFile.absolutePath)

            // Target: 10% of video or 30 seconds, whichever is smaller
            // But at least 2 seconds if possible
            val targetTimeUs = 2000000L // Default to 2s

            var bitmap =
                retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            if (bitmap == null) {
                Log.w(
                    "VideoThumbnailFetcher",
                    "Failed to extract frame at ${targetTimeUs}us, trying at 5s..."
                )
                bitmap =
                    retriever.getFrameAtTime(5000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }

            if (bitmap == null) {
                Log.w("VideoThumbnailFetcher", "Failed to extract frame at 5s, trying at 0s...")
                bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }

            if (bitmap == null) {
                Log.e(
                    "VideoThumbnailFetcher",
                    "Failed to extract frame from video (bitmap is null)"
                )
                // Return fallback image to prevent re-downloading
                return createFallbackResult()
            }

            // Process bitmap for SBS 3D if needed
            val processedBitmap = processBitmap(bitmap)

            Log.d("VideoThumbnailFetcher", "Extracted bitmap: ${processedBitmap.width}x${processedBitmap.height}")

            // Save to permanent local file
            localFile.outputStream().use { out ->
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            Log.d("VideoThumbnailFetcher", "Saved thumbnail to: ${localFile.absolutePath}")

            // Update DB if record exists
            try {
                val db = com.example.myapplication.data.database.AppDatabase.getDatabase(context)
                val video = db.videoDao().getVideoByPath(smbUrl)
                if (video != null) {
                    db.videoDao().updateThumbnailPath(video.id, localFile.absolutePath)
                    Log.d("VideoThumbnailFetcher", "Updated DB thumbnail path for video ${video.id}")
                }
            } catch (e: Exception) {
                Log.e("VideoThumbnailFetcher", "Failed to update DB", e)
            }

            // Convert to Coil result from the SAVED file
            SourceResult(
                source = ImageSource(file = localFile.toOkioPath(), fileSystem = FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.NETWORK
            )

        } catch (e: Exception) {
            Log.e("VideoThumbnailFetcher", "Error extracting SMB thumbnail: ${e.message}", e)
            e.printStackTrace()
            // Return fallback image to prevent re-downloading
            return createFallbackResult()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e("VideoThumbnailFetcher", "Error releasing retriever", e)
            }
            // Clean up temp file
            try {
                if (tempFile?.exists() == true) {
                    val deleted = tempFile.delete()
                    Log.d("VideoThumbnailFetcher", "Temp file deleted: $deleted")
                }
            } catch (e: Exception) {
                Log.e("VideoThumbnailFetcher", "Error deleting temp file", e)
            }
        }
    }

    private fun processBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        Log.d("VideoThumbnailFetcher", "Processing bitmap: ${width}x${height}")

        // Check for specific SBS resolutions: 4096x2048 or 8192x4096
        if ((width == 4096 && height == 2048) || (width == 8192 && height == 4096)) {
            Log.d("VideoThumbnailFetcher", "Detected 3D SBS video. Cropping thumbnail.")
            // Crop to left half
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width / 2, height)
            if (croppedBitmap != bitmap) {
                bitmap.recycle()
            }
            return croppedBitmap
        }

        // FHD (1920x1080) and 4K UHD (3840x2160) are explicitly kept as is.
        // Other resolutions are also kept by default.
        return bitmap
    }

    private fun createFallbackResult(): SourceResult {
        Log.d("VideoThumbnailFetcher", "Creating fallback thumbnail")
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.DKGRAY)

        val buffer = Buffer()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, buffer.outputStream())

        return SourceResult(
            source = ImageSource(source = buffer, context = options.context),
            mimeType = "image/jpeg",
            dataSource = DataSource.MEMORY
        )
    }

    class Factory : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            Log.d(
                "VideoThumbnailFetcher",
                "Factory.create called with data type: ${data::class.java.name}"
            )

            val path = when (data) {
                is String -> data
                is android.net.Uri -> data.toString()
                else -> {
                    Log.d(
                        "VideoThumbnailFetcher",
                        "Factory rejected: Unsupported type ${data::class.java.name}"
                    )
                    return null
                }
            }

            val isVideo = isVideoFile(path)

            // Check if item exists in disk cache
            val cacheKey = path
            val snapshot = imageLoader.diskCache?.openSnapshot(cacheKey)
            val isCached = snapshot != null
            snapshot?.close()

            Log.d(
                "VideoThumbnailFetcher",
                "Factory checking: $path, isVideo: $isVideo, Cached: $isCached"
            )

            if (!isVideo) {
                Log.d("VideoThumbnailFetcher", "Factory rejected: Not a video file")
                return null
            }

            Log.d(
                "VideoThumbnailFetcher",
                "Creating fetcher with options: Disk=${options.diskCachePolicy}, Mem=${options.memoryCachePolicy}, Net=${options.networkCachePolicy}"
            )

            return VideoThumbnailFetcher(path, options)
        }

        private fun isVideoFile(path: String): Boolean {
            val videoExtensions =
                listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm", ".m4v")
            return videoExtensions.any { path.lowercase().endsWith(it) }
        }
    }
}
