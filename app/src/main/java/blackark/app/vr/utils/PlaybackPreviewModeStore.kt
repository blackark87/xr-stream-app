package blackark.app.vr.utils

import android.content.Context
import blackark.app.vr.ui.viewmodel.PlaybackPreviewMode

object PlaybackPreviewModeStore {

    private const val PREFS_NAME = "playback_preview_preferences"
    private const val KEY_PREVIEW_MODE = "seek_preview_mode"

    fun load(context: Context): PlaybackPreviewMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedValue = prefs.getString(KEY_PREVIEW_MODE, null).orEmpty()
        return runCatching { PlaybackPreviewMode.valueOf(storedValue) }
            .getOrDefault(PlaybackPreviewMode.PlayerFrame)
    }

    fun save(context: Context, mode: PlaybackPreviewMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PREVIEW_MODE, mode.name)
            .apply()
    }
}
