package blackark.app.vr.utils

import android.content.Context
import blackark.app.vr.data.database.AppDatabase
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes frame file writers, then publishes only a still-current playback position. */
object ResumeThumbnailStore {
    private val mutex = Mutex()

    suspend fun generate(context: Context, path: String, positionMs: Long, durationMs: Long) = mutex.withLock {
        val dao = AppDatabase.getDatabase(context).videoDao()
        val before = dao.getVideoByPath(path) ?: return@withLock
        if (before.lastPosition != positionMs) return@withLock
        var frame = VideoThumbnailFetcher.currentGeneratedFramePath(
            context, path, before.resumeThumbnailPath, positionMs, durationMs,
        )
        if (frame == null) {
            val request = ImageRequest.Builder(context)
                .data(VideoThumbnailFetcher.Model(path, positionMs, durationMs))
                // A Coil bitmap alone cannot restore an evicted persistent frame file.
                // The explicit file-cache check above is the cache for this operation.
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
            ThumbnailImageLoaderProvider.get(context).execute(request)
            currentCoroutineContext().ensureActive()
            // A fallback image or Coil memory hit is not proof of a persisted resume frame.
            frame = VideoThumbnailFetcher.currentGeneratedFramePath(context, path, null, positionMs, durationMs)
        }
        if (frame != null) {
            currentCoroutineContext().ensureActive()
            if (dao.publishResumeThumbnail(path, positionMs, frame) > 0) {
                // Keep the previous image too: the returning card may still be decoding it.
                val keep = setOf(frame, before.resumeThumbnailPath, before.thumbnailPath)
                val prefix = generatedResumeFrameFilePrefix(path, ImageCacheVersionStore.thumbnailGeneration(context))
                java.io.File(context.filesDir, "thumbnails").listFiles()?.forEach { candidate ->
                    if (candidate.isFile && candidate.name.startsWith(prefix) && candidate.name.endsWith(".jpg") &&
                        candidate.absolutePath !in keep
                    ) candidate.delete()
                }
            }
        }
    }
}
