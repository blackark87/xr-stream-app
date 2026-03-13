package blackark.app.vr.utils

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import blackark.app.vr.XRStreamApplication

/**
 * Provides a shared ImageLoader that includes SMB thumbnail fetchers.
 */
object ThumbnailImageLoaderProvider {

    @Volatile
    private var cached: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        val appContext = context.applicationContext

        cached?.let { return it }

        return synchronized(this) {
            cached?.let { return@synchronized it }

            val loader = if (appContext is XRStreamApplication) {
                appContext.newImageLoader(appContext)
            } else {
                SingletonImageLoader.get(appContext)
            }

            cached = loader
            loader
        }
    }
}
