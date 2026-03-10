package com.example.myapplication

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.video.VideoFrameDecoder
import com.example.myapplication.utils.VideoThumbnailFetcher
import okio.Path.Companion.toOkioPath

/**
 * Application class for XR Stream App
 * Sets up Coil ImageLoader with video thumbnail support
 */
class XRStreamApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("XRStreamApplication", "Application created")
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        android.util.Log.d(
            "XRStreamApplication",
            "Creating new ImageLoader with VideoThumbnailFetcher"
        )
        return ImageLoader.Builder(this)
            .components {
                // Add video frame decoder for extracting frames from videos
                add(VideoFrameDecoder.Factory())
                // Add custom fetcher for SMB video thumbnails
                add(VideoThumbnailFetcher.Factory())
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


