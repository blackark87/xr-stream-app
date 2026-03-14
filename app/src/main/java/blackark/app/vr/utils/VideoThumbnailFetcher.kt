package blackark.app.vr.utils

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import blackark.app.vr.data.database.entity.VideoDisplaySettings
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.CancellationException
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.net.URI

/**
 * Custom Coil Fetcher for extracting video thumbnails from SMB and local files
 */
class VideoThumbnailFetcher(
    private val data: String,
    private val allowMetadataPoster: Boolean,
    private val options: Options
) : Fetcher {

    data class Model(
        val path: String,
        val allowMetadataPoster: Boolean = true,
    )

    private val tag = "VideoThumbnailFetcher"
    private val mb = 1024L * 1024L
    private val maxFullThumbnailDownloadBytes = 768L * mb
    private var inferredDisplayProfile: InferredDisplayProfile? = null

    private fun logMetadataTrace(message: String) {
        Log.v(tag, message)
    }

    override suspend fun fetch(): FetchResult? {
        inferredDisplayProfile = null
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
        inferredDisplayProfile = null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            val bitmap = extractBestThumbnailFrame(retriever, durationMs) ?: return null

            // Convert bitmap to Coil-compatible result
            val buffer = Buffer()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, buffer.outputStream())

            SourceFetchResult(
                source = ImageSource(buffer, FileSystem.SYSTEM),
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
        inferredDisplayProfile = null
        Log.d(
            "VideoThumbnailFetcher",
            "Extracting SMB thumbnail for: $smbUrl (allowMetadataPoster=$allowMetadataPoster)"
        )

        // 1. Check for existing local thumbnail based on filename identity.
        val context = options.context
        val cacheDir = java.io.File(context.filesDir, "thumbnails")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val thumbnailIdentityKey = buildThumbnailIdentityKey(smbUrl)
        val fileNameHash = java.security.MessageDigest.getInstance("MD5")
            .digest(thumbnailIdentityKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val localFile = java.io.File(cacheDir, "$fileNameHash.jpg")

        Log.d(tag, "Thumbnail identity key for $smbUrl -> $thumbnailIdentityKey")

        if (localFile.exists()) {
            Log.d("VideoThumbnailFetcher", "Found cached thumbnail: ${localFile.absolutePath}")
            return SourceFetchResult(
                source = ImageSource(file = localFile.toOkioPath(), fileSystem = FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        }
        if (allowMetadataPoster) {
            resolveMetadataPosterThumbnail(
                videoPath = smbUrl,
                targetThumbnailFile = localFile,
            )?.let {
                return it
            }
        } else {
            logMetadataTrace("Skipping metadata poster lookup for extracted thumbnail path=$smbUrl")
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

            val smbClient = blackark.app.vr.AppState.smbClient
            if (smbClient == null) {
                Log.e("VideoThumbnailFetcher", "SMB client is null! Cannot fetch thumbnail.")
                return null
            }

            Log.d("VideoThumbnailFetcher", "Getting SMB file reference...")
            val smbFile = smbClient.getSmbFile(smbUrl)
            // Skip exists() check as we just listed it. It causes extra network roundtrip.

            var bitmap: Bitmap? = extractFrameDirectlyFromSmb(retriever, smbFile)
            var durationMs = 0L
            val smbFileSizeBytes = runCatching { smbFile.length() }.getOrElse { -1L }

            if (bitmap == null) {
                Log.d(
                    "VideoThumbnailFetcher",
                    "Direct extraction failed, downloading SMB prefix for fallback..."
                )
                val tempThumbnailFile = tempFile ?: return createFallbackResult()
                val headerReadAttemptsBytes = buildSmbReadAttempts(smbFileSizeBytes)

                for ((attemptIndex, maxBytes) in headerReadAttemptsBytes.withIndex()) {
                    val downloadedBytes = downloadSmbPrefixToTempFile(
                        smbFile = smbFile,
                        tempFile = tempThumbnailFile,
                        maxBytes = maxBytes,
                    )
                    Log.d(
                        "VideoThumbnailFetcher",
                        "Attempt ${attemptIndex + 1}/${headerReadAttemptsBytes.size}: downloaded $downloadedBytes bytes"
                    )

                    if (downloadedBytes <= 0L) {
                        Log.w(
                            "VideoThumbnailFetcher",
                            "Attempt ${attemptIndex + 1} downloaded no data; retrying with deeper read"
                        )
                        continue
                    }

                    retriever.setDataSource(tempThumbnailFile.absolutePath)
                    durationMs =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: 0L
                    bitmap = extractBestThumbnailFrame(retriever, durationMs)

                    if (bitmap != null) {
                        break
                    }

                    Log.w(
                        "VideoThumbnailFetcher",
                        "Failed to extract frame on attempt ${attemptIndex + 1}; retrying with deeper read"
                    )
                }

                if (
                    bitmap == null &&
                    smbFileSizeBytes > 0L &&
                    smbFileSizeBytes <= maxFullThumbnailDownloadBytes &&
                    headerReadAttemptsBytes.lastOrNull() != smbFileSizeBytes
                ) {
                    Log.d(
                        "VideoThumbnailFetcher",
                        "Retrying thumbnail extraction with full file download (${smbFileSizeBytes} bytes)"
                    )
                    val downloadedBytes = downloadSmbPrefixToTempFile(
                        smbFile = smbFile,
                        tempFile = tempThumbnailFile,
                        maxBytes = smbFileSizeBytes,
                    )
                    if (downloadedBytes > 0L) {
                        retriever.setDataSource(tempThumbnailFile.absolutePath)
                        durationMs =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                ?.toLongOrNull() ?: 0L
                        bitmap = extractBestThumbnailFrame(retriever, durationMs)
                    }
                }
            }

            if (bitmap == null) {
                Log.e(
                    "VideoThumbnailFetcher",
                    "Failed to extract frame from video (bitmap is null)"
                )
                return createFallbackResult()
            }

            Log.d("VideoThumbnailFetcher", "Extracted bitmap: ${bitmap.width}x${bitmap.height}")

            // Save to permanent local file
            localFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            Log.d("VideoThumbnailFetcher", "Saved thumbnail to: ${localFile.absolutePath}")
            updateVideoThumbnailPathInDb(
                videoPath = smbUrl,
                thumbnailPath = localFile.absolutePath,
                inferredDisplayProfile = inferredDisplayProfile,
            )

            SourceFetchResult(
                source = ImageSource(file = localFile.toOkioPath(), fileSystem = FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.NETWORK
            )
        } catch (e: Exception) {
            Log.e("VideoThumbnailFetcher", "Error extracting SMB thumbnail: ${e.message}", e)
            e.printStackTrace()
            return createFallbackResult()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e("VideoThumbnailFetcher", "Error releasing retriever", e)
            }
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

    private suspend fun resolveMetadataPosterThumbnail(
        videoPath: String,
        targetThumbnailFile: java.io.File,
    ): SourceFetchResult? {
        val folderPath = extractFolderPath(videoPath)
        if (!isMetadataLookupEligible(folderPath)) {
            return null
        }

        val code = extractMovieCode(videoPath)
        if (code.isNullOrBlank()) {
            logMetadataTrace("Metadata lookup skipped: no movie code extracted from path=$videoPath")
            return null
        }

        logMetadataTrace("Trying metadata poster lookup for code=$code folder='$folderPath'")
        val metadata = JvrLibraryMetadataProvider.getByCode(
            context = options.context.applicationContext,
            rawCode = code,
            folderPath = folderPath,
        ) ?: run {
            logMetadataTrace("Metadata poster unavailable for code=$code (path=$videoPath)")
            return null
        }

        val localPosterFile = metadata.posterUrl?.let(::resolveLocalPosterFile)
        if (localPosterFile == null) {
            logMetadataTrace(
                "Metadata resolved for code=$code but poster is not a valid local file: ${metadata.posterUrl ?: "<none>"}"
            )
            return null
        }

        try {
            localPosterFile.inputStream().use { input ->
                targetThumbnailFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.w(
                tag,
                "Failed to copy poster thumbnail for code=$code from ${localPosterFile.absolutePath}: ${e.message}"
            )
            return null
        }

        if (!targetThumbnailFile.exists() || targetThumbnailFile.length() <= 0L) {
            Log.w(tag, "Poster copy produced empty file for code=$code path=$videoPath")
            return null
        }

        logMetadataTrace(
            "Using metadata poster thumbnail for code=$code saved=${targetThumbnailFile.absolutePath}"
        )
        updateVideoThumbnailPathInDb(videoPath, targetThumbnailFile.absolutePath, metadata.title)

        return SourceFetchResult(
            source = ImageSource(
                file = targetThumbnailFile.toOkioPath(),
                fileSystem = FileSystem.SYSTEM
            ),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    private suspend fun updateVideoThumbnailPathInDb(
        videoPath: String,
        thumbnailPath: String,
        resolvedTitle: String? = null,
        inferredDisplayProfile: InferredDisplayProfile? = null,
    ) {
        try {
            val db = blackark.app.vr.data.database.AppDatabase.getDatabase(options.context)
            val parsedIdentity = parseVideoIdentity(videoPath)
            val fileName = parsedIdentity?.fileName ?: extractFileName(videoPath)
            if (fileName.isBlank()) {
                Log.d(tag, "No file name extracted for thumbnail update path=$videoPath")
                return
            }

            val videoByPath = parsedIdentity?.let { db.videoDao().getVideoByPath(it.filePath) }
            if (videoByPath != null) {
                db.videoDao().updateThumbnailAndTitle(videoByPath.id, thumbnailPath, resolvedTitle)
                Log.d(
                    tag,
                    "Updated DB thumbnail/title for video ${videoByPath.id} fileName=$fileName path=$thumbnailPath title=${resolvedTitle ?: "<unchanged>"}",
                )
            } else {
                val videoByFileName = db.videoDao().getLatestVideoByFileName(fileName)
                if (videoByFileName != null) {
                    db.videoDao()
                        .updateThumbnailAndTitle(videoByFileName.id, thumbnailPath, resolvedTitle)
                    Log.d(
                        tag,
                        "Updated DB thumbnail/title fallback for video ${videoByFileName.id} fileName=$fileName path=$thumbnailPath title=${resolvedTitle ?: "<unchanged>"}",
                    )
                } else {
                    Log.d(tag, "No history row found for thumbnail update fileName=$fileName")
                }
            }

            val favoritePath = parsedIdentity?.filePath ?: videoPath
            db.favoriteVideoDao().updateThumbnailAndTitleByPath(
                filePath = favoritePath,
                path = thumbnailPath,
                title = resolvedTitle,
            )

            if (parsedIdentity != null) {
                val existingDisplaySettings =
                    db.videoDisplaySettingsDao().getByPath(parsedIdentity.filePath)
                if (shouldApplyInferredDisplayProfile(existingDisplaySettings, inferredDisplayProfile)) {
                    db.videoDisplaySettingsDao().upsert(
                        VideoDisplaySettings(
                            filePath = parsedIdentity.filePath,
                            videoFormat = inferredDisplayProfile!!.videoFormat,
                            stereoMode = inferredDisplayProfile.stereoMode,
                        )
                    )
                    Log.d(
                        tag,
                        "Saved inferred display settings for fileName=$fileName path=${parsedIdentity.filePath}",
                    )
                }
            }
        } catch (e: CancellationException) {
            Log.d(tag, "Skipped DB thumbnail update because job was cancelled")
        } catch (e: Exception) {
            Log.e(tag, "Failed to update DB thumbnail path/title for path=$videoPath", e)
        }
    }

    private fun resolveLocalPosterFile(posterUrl: String): java.io.File? {
        val normalized = posterUrl.trim()
        if (normalized.isBlank()) return null

        val localFile = when {
            normalized.startsWith("file:", ignoreCase = true) -> {
                runCatching { java.io.File(URI(normalized)) }.getOrNull()
            }

            normalized.startsWith("/") -> java.io.File(normalized)
            else -> null
        } ?: return null

        return localFile.takeIf { it.exists() && it.length() > 0L }
    }

    private fun extractFolderPath(path: String): String {
        val sanitized = path
            .substringBefore('?')
            .substringBefore('#')
            .replace('\\', '/')
            .trim()
        return sanitized.substringBeforeLast('/', "")
    }

    private fun extractMovieCode(path: String): String? {
        val fileName = extractFileName(path)
        if (fileName.isBlank()) return null

        val stem = fileName.substringBeforeLast('.', fileName)
        val match = movieCodePattern.find(stem) ?: return null
        val maker = match.groupValues[1].uppercase()
        val serial = match.groupValues[2]
        return "$maker-$serial"
    }


    private fun extractFileName(path: String): String {
        return path
            .substringBefore('?')
            .substringBefore('#')
            .replace('\\', '/')
            .substringAfterLast('/')
            .trim()
    }

    private fun buildThumbnailIdentityKey(path: String): String {
        val modeKey = if (allowMetadataPoster) "poster" else "frame"
        val fileName = extractFileName(path)
        if (fileName.isBlank()) {
            val normalizedPath = path.substringBefore('?').substringBefore('#').trim().lowercase()
            return "$THUMBNAIL_CACHE_VERSION:$modeKey:$normalizedPath"
        }

        val stem = fileName.substringBeforeLast('.', fileName)
        val identity = stem.trim().lowercase().ifBlank { fileName.lowercase() }
        return "$THUMBNAIL_CACHE_VERSION:$modeKey:$identity"
    }

    private fun isMetadataLookupEligible(folderPath: String): Boolean {
        val normalized = folderPath.replace('\\', '/').lowercase()
        return makerYearPathPattern.containsMatchIn(normalized) || avVrPathPattern.containsMatchIn(
            normalized
        )
    }

    private fun extractFrameDirectlyFromSmb(
        retriever: MediaMetadataRetriever,
        smbFile: SmbFile,
    ): Bitmap? {
        var dataSource: SMBMediaDataSource? = null
        return try {
            dataSource = SMBMediaDataSource(smbFile)
            retriever.setDataSource(dataSource)

            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            val bitmap = extractBestThumbnailFrame(retriever, durationMs)
            if (bitmap != null) {
                Log.d(
                    "VideoThumbnailFetcher",
                    "Direct SMB extraction succeeded: ${bitmap.width}x${bitmap.height}",
                )
            } else {
                Log.w(
                    "VideoThumbnailFetcher",
                    "Direct SMB extraction returned null; using progressive fallback",
                )
            }
            bitmap
        } catch (e: Exception) {
            Log.w(
                "VideoThumbnailFetcher",
                "Direct SMB extraction failed: ${e.message}; using progressive fallback",
            )
            null
        } finally {
            try {
                dataSource?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun buildSmbReadAttempts(fileSizeBytes: Long): List<Long> {
        val baseAttempts =
            listOf(
                40L * mb,
                80L * mb,
                160L * mb,
                320L * mb,
                512L * mb,
            )

        val withOptionalFullRead =
            if (fileSizeBytes > 0L && fileSizeBytes <= maxFullThumbnailDownloadBytes) {
                baseAttempts + fileSizeBytes
            } else {
                baseAttempts
            }

        return withOptionalFullRead
            .map { attempt -> if (fileSizeBytes > 0L) minOf(attempt, fileSizeBytes) else attempt }
            .filter { it > 0L }
            .distinct()
            .sorted()
    }

    private fun downloadSmbPrefixToTempFile(
        smbFile: SmbFile,
        tempFile: java.io.File,
        maxBytes: Long,
    ): Long {
        var totalBytes = 0L
        val startTime = System.currentTimeMillis()
        var lastLogTime = startTime

        smbFile.inputStream.use { input: java.io.InputStream ->
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024) // 64KB buffer
                var bytesRead = input.read(buffer)

                while (bytesRead != -1 && totalBytes < maxBytes) {
                    val bytesToWrite = if (totalBytes + bytesRead > maxBytes) {
                        (maxBytes - totalBytes).toInt()
                    } else {
                        bytesRead
                    }
                    output.write(buffer, 0, bytesToWrite)
                    totalBytes += bytesToWrite

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLogTime > 1000) {
                        Log.d("VideoThumbnailFetcher", "Downloading... $totalBytes bytes")
                        lastLogTime = currentTime
                    }

                    if (totalBytes >= maxBytes) {
                        break
                    }
                    bytesRead = input.read(buffer)
                }
            }
        }

        val downloadTime = System.currentTimeMillis() - startTime
        Log.d(
            "VideoThumbnailFetcher",
            "Downloaded $totalBytes bytes (limit=$maxBytes) in ${downloadTime}ms. File size: ${tempFile.length()}",
        )

        return totalBytes
    }

    private fun extractBestThumbnailFrame(
        retriever: MediaMetadataRetriever,
        durationMs: Long,
    ): Bitmap? {
        val candidateTimesUs = buildThumbnailCandidateTimesUs(durationMs)
        val extractionOptions =
            listOf(
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                MediaMetadataRetriever.OPTION_PREVIOUS_SYNC,
                MediaMetadataRetriever.OPTION_NEXT_SYNC,
                MediaMetadataRetriever.OPTION_CLOSEST,
            )

        for (candidateTimeUs in candidateTimesUs) {
            for (option in extractionOptions) {
                try {
                    val bitmap = retriever.getFrameAtTime(candidateTimeUs, option)
                    if (bitmap != null) {
                        val normalizedBitmap = normalizeThumbnailFrame(bitmap)
                        Log.d(
                            "VideoThumbnailFetcher",
                            "Extracted thumbnail at ${candidateTimeUs / 1_000_000.0}s option=$option (duration=${durationMs}ms), frame=${normalizedBitmap.width}x${normalizedBitmap.height}",
                        )
                        if (normalizedBitmap !== bitmap) {
                            bitmap.recycle()
                        }
                        return normalizedBitmap
                    }

                    val scaledBitmap =
                        retriever.getScaledFrameAtTime(candidateTimeUs, option, 640, 360)
                    if (scaledBitmap != null) {
                        val normalizedBitmap = normalizeThumbnailFrame(scaledBitmap)
                        Log.d(
                            "VideoThumbnailFetcher",
                            "Extracted scaled thumbnail at ${candidateTimeUs / 1_000_000.0}s option=$option (duration=${durationMs}ms), frame=${normalizedBitmap.width}x${normalizedBitmap.height}",
                        )
                        if (normalizedBitmap !== scaledBitmap) {
                            scaledBitmap.recycle()
                        }
                        return normalizedBitmap
                    }
                } catch (e: Exception) {
                    Log.w(
                        "VideoThumbnailFetcher",
                        "Failed thumbnail extraction at ${candidateTimeUs / 1_000_000.0}s option=$option: ${e.message}",
                    )
                }
            }
        }

        val frameCount =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull() ?: 0
        if (frameCount > 0) {
            val frameIndexCandidates =
                listOf(frameCount / 5, frameCount / 3, frameCount / 2, 0)
                    .map { it.coerceIn(0, frameCount - 1) }
                    .distinct()

            for (frameIndex in frameIndexCandidates) {
                try {
                    val bitmap = retriever.getFrameAtIndex(frameIndex)
                    if (bitmap != null) {
                        val normalizedBitmap = normalizeThumbnailFrame(bitmap)
                        Log.d(
                            "VideoThumbnailFetcher",
                            "Extracted thumbnail by frame index=$frameIndex, frame=${normalizedBitmap.width}x${normalizedBitmap.height}",
                        )
                        if (normalizedBitmap !== bitmap) {
                            bitmap.recycle()
                        }
                        return normalizedBitmap
                    }
                } catch (e: Exception) {
                    Log.w(
                        "VideoThumbnailFetcher",
                        "Failed frame-index extraction at index=$frameIndex: ${e.message}",
                    )
                }
            }
        }

        return null
    }


    private fun buildThumbnailCandidateTimesUs(durationMs: Long): List<Long> {
        val preferredTimeUs = computePreferredThumbnailTimeUs(durationMs)
        val durationUs = if (durationMs > 0L) durationMs * 1_000L else Long.MAX_VALUE

        val explicitCandidates = listOf(
            preferredTimeUs,
            5_000_000L,
            7_500_000L,
            10_000_000L,
            12_500_000L,
            15_000_000L,
            20_000_000L,
            30_000_000L,
            45_000_000L,
            60_000_000L,
        )

        val percentageCandidates =
            if (durationMs > 0L) {
                listOf(
                    (durationMs * 0.25f).toLong() * 1_000L,
                    (durationMs * 0.35f).toLong() * 1_000L,
                    (durationMs * 0.50f).toLong() * 1_000L,
                )
            } else {
                emptyList()
            }

        return (explicitCandidates + percentageCandidates + listOf(0L))
            .map { candidate ->
                if (durationUs == Long.MAX_VALUE) candidate else candidate.coerceIn(0L, durationUs)
            }
            .distinct()
    }

    private class SMBMediaDataSource(
        smbFile: SmbFile,
    ) : MediaDataSource() {
        private val randomAccessFile = SmbRandomAccessFile(smbFile, "r")
        private val fileSize = randomAccessFile.length()
        private var closed = false

        @Synchronized
        override fun readAt(
            position: Long,
            buffer: ByteArray,
            offset: Int,
            size: Int,
        ): Int {
            if (closed || position < 0L || position >= fileSize) {
                return -1
            }

            val bytesToRead = minOf(size.toLong(), fileSize - position).toInt()
            randomAccessFile.seek(position)
            return randomAccessFile.read(buffer, offset, bytesToRead)
        }

        override fun getSize(): Long = fileSize

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            randomAccessFile.close()
        }
    }

    private fun normalizeThumbnailFrame(bitmap: Bitmap): Bitmap {
        val inferredProfile = inferDisplayProfileFromFrame(bitmap.width, bitmap.height)
        if (inferredProfile != null) {
            inferredDisplayProfile = inferredProfile
        }

        if (inferredProfile == null) {
            return bitmap
        }

        return try {
            val preview = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width / 2, bitmap.height)
            Log.d(
                tag,
                "Cropped stereo panoramic frame ${bitmap.width}x${bitmap.height} to single-eye thumbnail ${preview.width}x${preview.height}",
            )
            preview
        } catch (e: Exception) {
            Log.w(
                tag,
                "Failed to crop stereo panoramic frame to single-eye thumbnail: ${e.message}"
            )
            bitmap
        }
    }

    private fun shouldApplyInferredDisplayProfile(
        settings: VideoDisplaySettings?,
        inferredDisplayProfile: InferredDisplayProfile?,
    ): Boolean {
        return inferredDisplayProfile != null &&
                (
                        settings == null ||
                                (
                                        settings.videoFormat == "Format2D" &&
                                                settings.stereoMode == "Mono"
                                        )
                        )
    }

    private fun computePreferredThumbnailTimeUs(durationMs: Long): Long {
        if (durationMs <= 0L) return 7_500_000L

        val targetMs = (durationMs * 0.20f).toLong().coerceIn(5_000L, 10_000L)
        return targetMs * 1_000L
    }

    private fun createFallbackResult(): SourceFetchResult {
        Log.d("VideoThumbnailFetcher", "Creating fallback thumbnail")
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.DKGRAY)

        val buffer = Buffer()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, buffer.outputStream())

        return SourceFetchResult(
            source = ImageSource(buffer, FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.MEMORY
        )
    }

    class ModelFactory : Fetcher.Factory<Model> {
        override fun create(data: Model, options: Options, imageLoader: ImageLoader): Fetcher? {
            return createFetcherForPath(
                path = data.path,
                allowMetadataPoster = data.allowMetadataPoster,
                options = options,
                imageLoader = imageLoader,
            )
        }
    }

    class StringFactory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            return createFetcherForPath(
                path = data,
                allowMetadataPoster = true,
                options = options,
                imageLoader = imageLoader,
            )
        }
    }

    class UriFactory : Fetcher.Factory<android.net.Uri> {
        override fun create(
            data: android.net.Uri,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            return createFetcherForPath(
                path = data.toString(),
                allowMetadataPoster = true,
                options = options,
                imageLoader = imageLoader,
            )
        }
    }

    class CoilUriFactory : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return createFetcherForPath(
                path = data.toString(),
                allowMetadataPoster = true,
                options = options,
                imageLoader = imageLoader,
            )
        }
    }

    companion object {
        private const val THUMBNAIL_CACHE_VERSION = "thumb-v3"
        private val movieCodePattern = Regex("(?i)([a-z]{2,10})[-_](\\d{2,5})(?!\\d)")
        private val makerYearPathPattern =
            Regex("(^|/)maker/(?:19|20)\\d{2}(/|$)", RegexOption.IGNORE_CASE)
        private val avVrPathPattern = Regex("(^|/)av/vr(/|$)", RegexOption.IGNORE_CASE)

        fun diskCacheKey(path: String, allowMetadataPoster: Boolean = true): String {
            val modeKey = if (allowMetadataPoster) "poster" else "frame"
            return "$THUMBNAIL_CACHE_VERSION:$modeKey:$path"
        }

        private fun createFetcherForPath(
            path: String,
            allowMetadataPoster: Boolean,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            Log.d("VideoThumbnailFetcher", "Factory checking path: $path")

            val isVideo = isVideoFile(path)

            // Check if item exists in disk cache.
            val snapshot =
                imageLoader.diskCache?.openSnapshot(diskCacheKey(path, allowMetadataPoster))
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

            return VideoThumbnailFetcher(path, allowMetadataPoster, options)
        }

        private fun isVideoFile(path: String): Boolean {
            val sanitizedPath = path.substringBefore('?').substringBefore('#')
            val scheme = sanitizedPath.substringBefore("://", "")

            // Always accept smb:// for our custom thumbnail extraction path.
            if (scheme.equals("smb", ignoreCase = true)) {
                return true
            }

            val videoExtensions =
                listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm", ".m4v")
            return videoExtensions.any { sanitizedPath.lowercase().endsWith(it) }
        }
    }
}
