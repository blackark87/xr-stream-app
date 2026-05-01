package blackark.app.vr.utils

import android.content.Context

object AppSettingsStore {

    private const val PREFS_NAME = "av_library_settings"
    private const val KEY_BACKGROUND_INDEXING_ENABLED = "background_indexing_enabled"
    private const val KEY_HAND_TRACKING_ENABLED = "hand_tracking_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isBackgroundIndexingEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_BACKGROUND_INDEXING_ENABLED, false)
    }

    fun setBackgroundIndexingEnabled(context: Context, enabled: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_BACKGROUND_INDEXING_ENABLED, enabled)
            .apply()
    }

    fun isHandTrackingEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_HAND_TRACKING_ENABLED, false)
    }

    fun setHandTrackingEnabled(context: Context, enabled: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_HAND_TRACKING_ENABLED, enabled)
            .apply()
    }
}
