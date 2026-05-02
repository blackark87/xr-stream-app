package blackark.app.vr.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.net.toUri
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object VideoFramePreviewExtractor {

    private const val TAG = "VideoFramePreview"
    private const val PREVIEW_FRAME_WIDTH = 480
    private const val PREVIEW_FRAME_HEIGHT = 270
    private val sessionMutex = Mutex()
    private var preparedSession: PreparedSession? = null

    private data class PreparedSession(
        val videoPath: String,
        val retriever: MediaMetadataRetriever,
        val durationMs: Long,
        val closeDataSource: (() -> Unit)? = null,
    )

    suspend fun prepareVideo(
        context: Context,
        videoPath: String,
    ): Unit = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            obtainPreparedSessionLocked(context, videoPath)
        }
    }

    suspend fun clearPreparedVideo(videoPath: String? = null) = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            val currentSession = preparedSession ?: return@withLock
            if (videoPath == null || currentSession.videoPath == videoPath) {
                releasePreparedSessionLocked()
            }
        }
    }

    suspend fun extractPreviewFrame(
        context: Context,
        videoPath: String,
        targetPositionMs: Long,
    ): String? = withContext(Dispatchers.IO) {
        val previewDir = File(context.cacheDir, "seek_previews").apply { mkdirs() }
        val cacheKey = videoPath.toByteArray()
        val fileHash =
            MessageDigest.getInstance("MD5")
                .digest(cacheKey)
                .joinToString("") { "%02x".format(it) }
        val safeTargetPositionMs = targetPositionMs.coerceAtLeast(0L)
        val previewFile = File(previewDir, "seek_preview_${fileHash}_$safeTargetPositionMs.jpg")
        val tempFile = File(previewDir, "seek_preview_${fileHash}_$safeTargetPositionMs.tmp")

        if (previewFile.exists() && previewFile.length() > 0L) {
            return@withContext previewFile.absolutePath
        }

        try {
            sessionMutex.withLock {
                val session =
                    obtainPreparedSessionLocked(context, videoPath) ?: return@withContext null
                val clampedTargetMs =
                    if (session.durationMs > 0L) {
                        targetPositionMs.coerceIn(0L, session.durationMs)
                    } else {
                        targetPositionMs.coerceAtLeast(0L)
                    }

                val previewBitmap =
                    extractPreviewBitmap(
                        retriever = session.retriever,
                        durationMs = session.durationMs,
                        targetPositionMs = clampedTargetMs,
                    ) ?: return@withContext null

                tempFile.outputStream().use { output ->
                    previewBitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                }
                if (tempFile != previewFile) {
                    if (previewFile.exists()) {
                        previewFile.delete()
                    }
                    tempFile.copyTo(previewFile, overwrite = true)
                    tempFile.delete()
                }
                if (!previewBitmap.isRecycled) {
                    previewBitmap.recycle()
                }
            }

            previewFile.absolutePath
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(
                TAG,
                "Failed to extract seek preview for path=$videoPath at ${targetPositionMs}ms: ${error.message}",
                error,
            )
            null
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun obtainPreparedSessionLocked(
        context: Context,
        videoPath: String,
    ): PreparedSession? {
        preparedSession?.takeIf { it.videoPath == videoPath }?.let { return it }

        releasePreparedSessionLocked()

        val retriever = MediaMetadataRetriever()
        var closeDataSource: (() -> Unit)? = null

        try {
            val uri = videoPath.toUri()
            when (uri.scheme) {
                "smb" -> {
                    val smbClient = blackark.app.vr.AppState.smbClient ?: return null
                    val smbFile = smbClient.getSmbFile(videoPath)
                    val dataSource = SmbMediaDataSource(smbFile)
                    closeDataSource = { runCatching { dataSource.close() } }
                    retriever.setDataSource(dataSource)
                }

                "file" -> {
                    retriever.setDataSource(uri.path ?: videoPath)
                }

                "content" -> {
                    retriever.setDataSource(context, uri)
                }

                else -> {
                    retriever.setDataSource(videoPath)
                }
            }

            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L

            return PreparedSession(
                videoPath = videoPath,
                retriever = retriever,
                durationMs = durationMs,
                closeDataSource = closeDataSource,
            ).also { session ->
                preparedSession = session
                Log.d(TAG, "Prepared reusable preview session for path=$videoPath")
            }
        } catch (error: Exception) {
            runCatching { retriever.release() }
            closeDataSource?.invoke()
            Log.w(
                TAG,
                "Failed to prepare preview session for path=$videoPath: ${error.message}",
                error
            )
            return null
        }
    }

    private fun releasePreparedSessionLocked() {
        val currentSession = preparedSession ?: return
        runCatching { currentSession.retriever.release() }
        currentSession.closeDataSource?.invoke()
        preparedSession = null
    }

    private fun extractPreviewBitmap(
        retriever: MediaMetadataRetriever,
        durationMs: Long,
        targetPositionMs: Long,
    ): Bitmap? {
        val candidateTimesUs = buildCandidateTimesUs(durationMs, targetPositionMs)
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
                    val bitmap =
                        retriever.getScaledFrameAtTime(
                            candidateTimeUs,
                            option,
                            PREVIEW_FRAME_WIDTH,
                            PREVIEW_FRAME_HEIGHT,
                        ) ?: retriever.getFrameAtTime(candidateTimeUs, option)
                    if (bitmap != null) {
                        return normalizePreviewFrame(bitmap)
                    }
                } catch (error: Exception) {
                    Log.v(
                        TAG,
                        "Seek preview extraction failed at ${candidateTimeUs / 1_000_000.0}s option=$option: ${error.message}",
                    )
                }
            }
        }

        return null
    }

    private fun buildCandidateTimesUs(
        durationMs: Long,
        targetPositionMs: Long,
    ): List<Long> {
        val durationUs = if (durationMs > 0L) durationMs * 1_000L else Long.MAX_VALUE
        val targetUs = targetPositionMs * 1_000L
        val offsetsUs =
            listOf(
                0L,
                -750_000L,
                750_000L,
                -1_500_000L,
                1_500_000L,
                -3_000_000L,
                3_000_000L,
            )

        return offsetsUs
            .map { offsetUs ->
                val candidate = targetUs + offsetUs
                if (durationUs == Long.MAX_VALUE) {
                    candidate.coerceAtLeast(0L)
                } else {
                    candidate.coerceIn(0L, durationUs)
                }
            }
            .distinct()
    }

    private fun normalizePreviewFrame(bitmap: Bitmap): Bitmap {
        val inferredProfile =
            inferDisplayProfileFromFrame(bitmap.width, bitmap.height) ?: return bitmap
        return try {
            when (inferredProfile.stereoMode) {
                "SideBySide" -> Bitmap.createBitmap(bitmap, 0, 0, bitmap.width / 2, bitmap.height)
                "TopBottom" -> Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height / 2)
                else -> bitmap
            }.also { normalized ->
                if (normalized !== bitmap && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        } catch (_: Exception) {
            bitmap
        }
    }

    private class SmbMediaDataSource(
        smbFile: SmbFile,
    ) : MediaDataSource() {
        private val randomAccessFile = SmbRandomAccessFile(smbFile, "r")
        private val fileSize = runCatching { randomAccessFile.length() }.getOrDefault(-1L)

        override fun getSize(): Long = fileSize

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size == 0) {
                return 0
            }
            if (position < 0L || offset < 0 || size < 0 || offset + size > buffer.size) {
                return -1
            }
            if (fileSize >= 0L && position >= fileSize) {
                return -1
            }

            return try {
                synchronized(randomAccessFile) {
                    randomAccessFile.seek(position)
                    randomAccessFile.read(buffer, offset, size)
                }
            } catch (_: Exception) {
                -1
            }
        }

        override fun close() {
            randomAccessFile.close()
        }
    }
}
