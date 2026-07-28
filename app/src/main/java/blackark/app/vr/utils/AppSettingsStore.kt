package blackark.app.vr.utils

import android.content.Context

object AppSettingsStore {

    private const val PREFS_NAME = "av_library_settings"
    private const val KEY_BACKGROUND_INDEXING_ENABLED = "background_indexing_enabled"
    private const val KEY_HAND_TRACKING_ENABLED = "hand_tracking_enabled"
    private const val KEY_CONTROLLER_HAND_TRACKING_PROMPT_HANDLED =
        "controller_hand_tracking_prompt_handled"
    private const val KEY_LOCAL_STORAGE_TREE_URI = "local_storage_tree_uri"
    private const val KEY_SUBTITLE_FONT_FAMILY = "subtitle_font_family"
    private const val KEY_SUBTITLE_TEXT_SIZE = "subtitle_text_size"
    private const val KEY_IMMERSIVE_SUBTITLE_DISTANCE_METERS =
        "immersive_subtitle_distance_meters"

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
        return prefs(context).getBoolean(KEY_HAND_TRACKING_ENABLED, true)
    }

    fun setHandTrackingEnabled(context: Context, enabled: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_HAND_TRACKING_ENABLED, enabled)
            .apply()
    }

    fun isControllerHandTrackingPromptHandled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_CONTROLLER_HAND_TRACKING_PROMPT_HANDLED, false)
    }

    fun setControllerHandTrackingPromptHandled(context: Context, handled: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_CONTROLLER_HAND_TRACKING_PROMPT_HANDLED, handled)
            .apply()
    }

    fun getLocalStorageTreeUri(context: Context): String? {
        return prefs(context).getString(KEY_LOCAL_STORAGE_TREE_URI, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setLocalStorageTreeUri(context: Context, treeUri: String) {
        prefs(context)
            .edit()
            .putString(KEY_LOCAL_STORAGE_TREE_URI, treeUri)
            .apply()
    }

    fun clearLocalStorageTreeUri(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_LOCAL_STORAGE_TREE_URI)
            .apply()
    }

    fun getSubtitleFontId(context: Context): String? {
        return prefs(context).getString(KEY_SUBTITLE_FONT_FAMILY, null)
    }

    fun setSubtitleFontId(context: Context, fontId: String) {
        prefs(context)
            .edit()
            .putString(KEY_SUBTITLE_FONT_FAMILY, fontId)
            .apply()
    }

    fun getSubtitleTextSize(context: Context): String? {
        return prefs(context).getString(KEY_SUBTITLE_TEXT_SIZE, null)
    }

    fun setSubtitleTextSize(context: Context, textSize: String) {
        prefs(context)
            .edit()
            .putString(KEY_SUBTITLE_TEXT_SIZE, textSize)
            .apply()
    }

    fun getImmersiveSubtitleDistanceMeters(context: Context): Float {
        return normalizeImmersiveSubtitleDistanceMeters(
            prefs(context).getFloat(
                KEY_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
                DEFAULT_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
            )
        )
    }

    fun setImmersiveSubtitleDistanceMeters(context: Context, distanceMeters: Float) {
        prefs(context)
            .edit()
            .putFloat(
                KEY_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
                normalizeImmersiveSubtitleDistanceMeters(distanceMeters),
            )
            .apply()
    }
}
