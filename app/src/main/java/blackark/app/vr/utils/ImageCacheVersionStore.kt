package blackark.app.vr.utils

import android.content.Context

/**
 * Tracks cache generations so artwork and thumbnail caches can be invalidated independently.
 */
object ImageCacheVersionStore {

    private const val PREFS_NAME = "image_cache_versions"
    private const val KEY_THUMBNAIL_GENERATION = "thumbnail_generation"
    private const val KEY_ARTWORK_GENERATION = "artwork_generation"

    @Volatile
    private var initialized = false

    @Volatile
    private var cachedThumbnailGeneration = 0

    @Volatile
    private var cachedArtworkGeneration = 0

    fun initialize(context: Context) {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            val preferences = preferences(context)
            cachedThumbnailGeneration = preferences.getInt(KEY_THUMBNAIL_GENERATION, 0)
            cachedArtworkGeneration = preferences.getInt(KEY_ARTWORK_GENERATION, 0)
            initialized = true
        }
    }

    fun thumbnailGeneration(context: Context? = null): Int {
        if (!initialized && context != null) {
            initialize(context)
        }
        return cachedThumbnailGeneration
    }

    fun artworkGeneration(context: Context? = null): Int {
        if (!initialized && context != null) {
            initialize(context)
        }
        return cachedArtworkGeneration
    }

    fun bumpThumbnailGeneration(context: Context): Int {
        initialize(context)
        return synchronized(this) {
            val nextGeneration = cachedThumbnailGeneration + 1
            preferences(context)
                .edit()
                .putInt(KEY_THUMBNAIL_GENERATION, nextGeneration)
                .apply()
            cachedThumbnailGeneration = nextGeneration
            nextGeneration
        }
    }

    fun bumpArtworkGeneration(context: Context): Int {
        initialize(context)
        return synchronized(this) {
            val nextGeneration = cachedArtworkGeneration + 1
            preferences(context)
                .edit()
                .putInt(KEY_ARTWORK_GENERATION, nextGeneration)
                .apply()
            cachedArtworkGeneration = nextGeneration
            nextGeneration
        }
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )
}
