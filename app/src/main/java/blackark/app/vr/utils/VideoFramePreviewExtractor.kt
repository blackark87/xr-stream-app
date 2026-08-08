@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package blackark.app.vr.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.SeekParameters
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.inspector.frame.FrameExtractor
import blackark.app.vr.AppState
import blackark.app.vr.player.SMBDataSource
import blackark.app.vr.remote.RuntimeConfigRegistry
import com.google.common.util.concurrent.ListenableFuture
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SeekPreviewFrame(
    val requestedPositionMs: Long,
    val presentationPositionMs: Long,
    val bitmap: Bitmap,
)

object VideoFramePreviewExtractor {

    private const val TAG = "VideoFramePreview"
    private val directExecutor = Executor { command -> command.run() }
    private val sessionMutex = Mutex()
    private val previewDispatcher =
        Executors.newSingleThreadExecutor { task ->
            Thread(
                {
                    android.os.Process.setThreadPriority(
                        android.os.Process.THREAD_PRIORITY_BACKGROUND,
                    )
                    task.run()
                },
                "SeekPreviewDecoder",
            ).apply { isDaemon = true }
        }.asCoroutineDispatcher()
    private var preparedSession: PreparedSession? = null

    private data class FrameCacheKey(
        val targetPositionMs: Long,
        val preserveFullFrame: Boolean,
    )

    private class FrameMemoryCache(private val maximumEntries: Int) {
        private val entries = LinkedHashMap<FrameCacheKey, SeekPreviewFrame>(
            maximumEntries,
            0.75f,
            true,
        )

        operator fun get(key: FrameCacheKey): SeekPreviewFrame? = entries[key]

        fun put(key: FrameCacheKey, frame: SeekPreviewFrame) {
            entries[key] = frame
            while (entries.size > maximumEntries) {
                val eldestKey = entries.entries.iterator().next().key
                entries.remove(eldestKey)
            }
        }

        fun clear() {
            entries.clear()
        }
    }

    private data class PreparedSession(
        val videoPath: String,
        val extractor: FrameExtractor,
        val cache: FrameMemoryCache,
    )

    suspend fun prepare(
        context: Context,
        videoPath: String,
    ): Unit = withContext(previewDispatcher) {
        sessionMutex.withLock {
            obtainPreparedSessionLocked(context.applicationContext, videoPath)
        }
    }

    suspend fun release(videoPath: String? = null) = withContext(previewDispatcher) {
        sessionMutex.withLock {
            val currentSession = preparedSession ?: return@withLock
            if (videoPath == null || currentSession.videoPath == videoPath) {
                releasePreparedSessionLocked()
            }
        }
    }

    suspend fun requestFrame(
        context: Context,
        videoPath: String,
        targetPositionMs: Long,
        preserveFullFrame: Boolean = false,
    ): SeekPreviewFrame? = withContext(previewDispatcher) {
        val startedAtMs = SystemClock.elapsedRealtime()
        val safeTargetPositionMs = targetPositionMs.coerceAtLeast(0L)
        val cacheKey = FrameCacheKey(safeTargetPositionMs, preserveFullFrame)

        try {
            sessionMutex.lock()
            try {
                val session = obtainPreparedSessionLocked(context.applicationContext, videoPath)
                session?.cache?.get(cacheKey)?.let { cached ->
                    Log.d(TAG, "Seek preview cache hit targetMs=$safeTargetPositionMs")
                    return@withContext cached
                }

                val extractedFrame =
                    if (session != null) {
                        extractWithFrameExtractor(
                            session = session,
                            requestedPositionMs = safeTargetPositionMs,
                            preserveFullFrame = preserveFullFrame,
                        )
                    } else {
                        null
                    }

                val resolvedFrame = extractedFrame ?: extractFallbackFrame(
                    context = context.applicationContext,
                    videoPath = videoPath,
                    requestedPositionMs = safeTargetPositionMs,
                    preserveFullFrame = preserveFullFrame,
                )
                resolvedFrame?.also { frame ->
                    session?.cache?.put(cacheKey, frame)
                    Log.d(
                        TAG,
                        "Seek preview ready requestedMs=$safeTargetPositionMs " +
                            "presentationMs=${frame.presentationPositionMs} " +
                            "elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs} " +
                        "fullFrame=$preserveFullFrame",
                    )
                }
            } finally {
                sessionMutex.unlock()
            }
        } catch (cancelled: CancellationException) {
            Log.d(TAG, "Seek preview cancelled targetMs=$safeTargetPositionMs")
            throw cancelled
        } catch (error: Exception) {
            Log.w(
                TAG,
                "Failed to extract seek preview for path=$videoPath at ${targetPositionMs}ms",
                error,
            )
            null
        }
    }

    private suspend fun extractWithFrameExtractor(
        session: PreparedSession,
        requestedPositionMs: Long,
        preserveFullFrame: Boolean,
    ): SeekPreviewFrame? {
        return try {
            val extracted = session.extractor.getFrame(requestedPositionMs).awaitCancellable()
            val normalizedBitmap =
                if (preserveFullFrame) extracted.bitmap else normalizePreviewFrame(extracted.bitmap)
            SeekPreviewFrame(
                requestedPositionMs = requestedPositionMs,
                presentationPositionMs = extracted.presentationTimeMs,
                bitmap = normalizedBitmap,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(
                TAG,
                "FrameExtractor failed at ${requestedPositionMs}ms; using sync-frame fallback",
                error,
            )
            null
        }
    }

    private fun obtainPreparedSessionLocked(
        context: Context,
        videoPath: String,
    ): PreparedSession? {
        preparedSession?.takeIf { it.videoPath == videoPath }?.let { return it }

        releasePreparedSessionLocked()

        return try {
            val mediaSourceFactory =
                if (videoPath.startsWith("smb://", ignoreCase = true)) {
                    val config = AppState.smbConfig ?: return null
                    DefaultMediaSourceFactory(SMBDataSource.Factory(config))
                } else {
                    DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
                }
            val seekConfig = RuntimeConfigRegistry.current.seekPreview
            val extractor = FrameExtractor.Builder(context, MediaItem.fromUri(videoPath))
                .setMediaSourceFactory(mediaSourceFactory)
                .setMediaCodecSelector(MediaCodecSelector.DEFAULT)
                .setSeekParameters(SeekParameters.CLOSEST_SYNC)
                .setEffects(listOf(Presentation.createForHeight(seekConfig.frameHeight)))
                .build()

            PreparedSession(
                videoPath = videoPath,
                extractor = extractor,
                cache = FrameMemoryCache(seekConfig.memoryEntries),
            ).also { session ->
                preparedSession = session
                Log.d(TAG, "Prepared Media3 frame extractor for path=$videoPath")
            }
        } catch (error: Exception) {
            Log.w(TAG, "Failed to prepare Media3 frame extractor for path=$videoPath", error)
            null
        }
    }

    private fun releasePreparedSessionLocked() {
        val currentSession = preparedSession ?: return
        currentSession.cache.clear()
        runCatching { currentSession.extractor.close() }
        preparedSession = null
    }

    private fun extractFallbackFrame(
        context: Context,
        videoPath: String,
        requestedPositionMs: Long,
        preserveFullFrame: Boolean,
    ): SeekPreviewFrame? {
        val retriever = MediaMetadataRetriever()
        var closeDataSource: (() -> Unit)? = null
        return try {
            val uri = videoPath.toUri()
            when (uri.scheme) {
                "smb" -> {
                    val smbClient = AppState.smbClient ?: return null
                    val dataSource = SmbMediaDataSource(smbClient.getSmbFile(videoPath))
                    closeDataSource = { runCatching { dataSource.close() } }
                    retriever.setDataSource(dataSource)
                }

                "file" -> retriever.setDataSource(uri.path ?: videoPath)
                "content" -> retriever.setDataSource(context, uri)
                else -> retriever.setDataSource(videoPath)
            }

            val seekConfig = RuntimeConfigRegistry.current.seekPreview
            val bitmap = retriever.getScaledFrameAtTime(
                requestedPositionMs * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                seekConfig.fallbackFrameWidth,
                seekConfig.frameHeight,
            ) ?: return null
            SeekPreviewFrame(
                requestedPositionMs = requestedPositionMs,
                presentationPositionMs = requestedPositionMs,
                bitmap = if (preserveFullFrame) bitmap else normalizePreviewFrame(bitmap),
            )
        } catch (error: Exception) {
            Log.w(TAG, "Sync-frame fallback failed at ${requestedPositionMs}ms", error)
            null
        } finally {
            runCatching { retriever.release() }
            closeDataSource?.invoke()
        }
    }

    private fun normalizePreviewFrame(bitmap: Bitmap): Bitmap {
        val inferredProfile = inferDisplayProfileFromFrame(bitmap.width, bitmap.height) ?: return bitmap
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

    private suspend fun <T> ListenableFuture<T>.awaitCancellable(): T =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel(true) }
            addListener(
                {
                    if (!continuation.isActive) return@addListener
                    try {
                        continuation.resume(get())
                    } catch (error: ExecutionException) {
                        continuation.resumeWithException(error.cause ?: error)
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                },
                directExecutor,
            )
        }

    private class SmbMediaDataSource(
        smbFile: SmbFile,
    ) : MediaDataSource() {
        private val randomAccessFile = SmbRandomAccessFile(smbFile, "r")
        private val fileSize = runCatching { randomAccessFile.length() }.getOrDefault(-1L)

        override fun getSize(): Long = fileSize

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size == 0) return 0
            if (position < 0L || offset < 0 || size < 0 || offset + size > buffer.size) return -1
            if (fileSize >= 0L && position >= fileSize) return -1

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
