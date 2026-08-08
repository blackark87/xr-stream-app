package blackark.app.vr

import android.app.Application
import blackark.app.vr.data.AppDataResetCoordinator
import blackark.app.vr.di.AppContainer
import blackark.app.vr.utils.ImageCacheVersionStore
import blackark.app.vr.utils.MetadataScopeRegistry
import blackark.app.vr.utils.VideoThumbnailFetcher
import blackark.app.vr.remote.RuntimeConfigRegistry
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
import kotlinx.coroutines.launch

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
        applicationScope.launch {
            container.runtimeConfigRepository.refresh()
            container.appUpdateManager.check(container.runtimeConfigRepository.snapshot.value)
        }
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
                    .maxSizePercent(
                        context,
                        RuntimeConfigRegistry.current.cache.imageMemoryPercent,
                    )
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
                    .maxSizeBytes(RuntimeConfigRegistry.current.cache.imageDiskBytes)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
