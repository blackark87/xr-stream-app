package blackark.app.vr

import android.app.Application
import blackark.app.vr.data.AppDataResetCoordinator
import blackark.app.vr.di.AppContainer
import blackark.app.vr.utils.ImageCacheVersionStore
import blackark.app.vr.utils.MetadataScopeRegistry
import blackark.app.vr.utils.VideoThumbnailFetcher
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.video.VideoFrameDecoder
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application class for XR Stream App
 * Sets up Coil ImageLoader with video thumbnail support
 */
class XRStreamApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppDataResetCoordinator.resetIfNeeded(this)
        container = AppContainer(this)
        MetadataScopeRegistry.initialize(container.metadataScopeRepository, applicationScope)
        android.util.Log.d("XRStreamApplication", "Application created")
        ImageCacheVersionStore.initialize(this)

        // Ensure Compose AsyncImage always uses our loader with SMB thumbnail fetchers.
        SingletonImageLoader.setSafe { context ->
            buildImageLoader(context)
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return buildImageLoader(context)
    }

    private fun buildImageLoader(context: android.content.Context): ImageLoader {
        android.util.Log.d(
            "XRStreamApplication",
            "Creating new ImageLoader with VideoThumbnailFetcher"
        )
        return ImageLoader.Builder(context)
            .components {
                // Add video frame decoder for extracting frames from videos.
                add(VideoFrameDecoder.Factory())
                // Add custom fetchers for video thumbnails and SMB resources.
                add(VideoThumbnailFetcher.ModelFactory())
                add(VideoThumbnailFetcher.StringFactory())
                add(VideoThumbnailFetcher.UriFactory())
                add(VideoThumbnailFetcher.CoilUriFactory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25) // Use 25% of app memory for image cache
                    .build()
            }
            .diskCache {
                val cacheDir = cacheDir.resolve("image_cache")
                android.util.Log.d(
                    "XRStreamApplication",
                    "Configuring disk cache at: ${cacheDir.absolutePath}"
                )
                DiskCache.Builder()
                    .directory(cacheDir.toOkioPath())
                    .maxSizeBytes(500L * 1024 * 1024) // 500MB fixed size
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}

