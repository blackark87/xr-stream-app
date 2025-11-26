package com.example.myapplication

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.myapplication.utils.VideoThumbnailFetcher

/**
 * Application class for XR Stream App
 * Sets up Coil ImageLoader with video thumbnail support
 */
class XRStreamApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                // Add video frame decoder for extracting frames from videos
                add(VideoFrameDecoder.Factory())
                // Add custom fetcher for SMB video thumbnails
                add(VideoThumbnailFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Use 25% of app memory for image cache
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // Use 2% of disk space
                    .build()
            }
            .respectCacheHeaders(false) // Don't rely on HTTP cache headers for SMB
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
