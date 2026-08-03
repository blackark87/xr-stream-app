package blackark.app.vr.data

import android.content.Context
import blackark.app.vr.data.database.AppDatabase
import java.io.File

/** Runs the explicitly requested one-time reset for the secure/server-scope schema epoch. */
object AppDataResetCoordinator {
    private const val EPOCH_PREFERENCES = "app_data_epoch"
    private const val KEY_EPOCH = "epoch"
    private const val CURRENT_EPOCH = 2

    fun resetIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val epochPreferences = appContext.getSharedPreferences(
            EPOCH_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        if (epochPreferences.getInt(KEY_EPOCH, 0) >= CURRENT_EPOCH) return

        appContext.deleteDatabase(AppDatabase.DATABASE_NAME)
        LEGACY_PREFERENCES.forEach { name ->
            appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        CACHE_DIRECTORIES.forEach { name ->
            File(appContext.filesDir, name).deleteRecursively()
            File(appContext.cacheDir, name).deleteRecursively()
        }
        epochPreferences.edit().putInt(KEY_EPOCH, CURRENT_EPOCH).commit()
    }

    private val LEGACY_PREFERENCES = listOf(
        "av_library_settings",
        "server_autofill",
        "encrypted_smb_credentials",
        "image_cache_versions",
    )
    private val CACHE_DIRECTORIES = listOf(
        "thumbnails",
        "group_posters",
        "performer_profiles",
        "image_cache",
        "seek_previews",
    )
}
