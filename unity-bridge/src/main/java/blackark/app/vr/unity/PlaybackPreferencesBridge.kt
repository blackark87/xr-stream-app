package blackark.app.vr.unity

import android.content.Context
import blackark.app.vr.remote.RuntimeConfigRegistry
import org.json.JSONObject
import kotlin.math.round

/** Uses the original app's preference keys without creating a Compose/XR session. */
internal fun loadPlaybackPreferences(context: Context): JSONObject {
    val prefs = context.getSharedPreferences("av_library_settings", Context.MODE_PRIVATE)
    val config = RuntimeConfigRegistry.current.subtitles
    return normalizePlaybackPreferences(JSONObject()
        .put("subtitleSize", prefs.getString("subtitle_text_size", "Medium"))
        .put("subtitleDistance", prefs.getFloat("immersive_subtitle_distance_meters", config.defaultDistanceMeters))
        .put("subtitleVertical", prefs.getFloat("immersive_subtitle_vertical_offset_meters", config.defaultVerticalOffsetMeters))
        .put("uiHorizontal", prefs.getFloat("immersive_ui_horizontal_offset_meters", config.defaultHorizontalOffsetMeters)))
}

internal fun savePlaybackPreferences(context: Context, value: JSONObject) {
    val normalized = normalizePlaybackPreferences(value)
    context.getSharedPreferences("av_library_settings", Context.MODE_PRIVATE).edit()
        .putString("subtitle_text_size", normalized.getString("subtitleSize"))
        .putFloat("immersive_subtitle_distance_meters", normalized.getDouble("subtitleDistance").toFloat())
        .putFloat("immersive_subtitle_vertical_offset_meters", normalized.getDouble("subtitleVertical").toFloat())
        .putFloat("immersive_ui_horizontal_offset_meters", normalized.getDouble("uiHorizontal").toFloat())
        .apply()
}

private fun normalizePlaybackPreferences(value: JSONObject): JSONObject {
    val config = RuntimeConfigRegistry.current.subtitles
    fun normalized(key: String, min: Float, max: Float, fallback: Float): Double =
        value.optDouble(key, fallback.toDouble()).takeIf { it.isFinite() }
            ?.coerceIn(min.toDouble(), max.toDouble()) ?: fallback.toDouble()
    val distance = normalized("subtitleDistance", config.minDistanceMeters, config.maxDistanceMeters, config.defaultDistanceMeters)
    return JSONObject()
        .put("subtitleSize", value.optString("subtitleSize", "Medium")
            .takeIf { it in setOf("Small", "Medium", "Large", "ExtraLarge") } ?: "Medium")
        .put("subtitleDistance", (round(distance * 10) / 10).coerceIn(config.minDistanceMeters.toDouble(), config.maxDistanceMeters.toDouble()))
        .put("subtitleVertical", normalized("subtitleVertical", config.minVerticalOffsetMeters, config.maxVerticalOffsetMeters, config.defaultVerticalOffsetMeters))
        .put("uiHorizontal", normalized("uiHorizontal", config.minHorizontalOffsetMeters, config.maxHorizontalOffsetMeters, config.defaultHorizontalOffsetMeters))
        .put("minDistance", config.minDistanceMeters).put("maxDistance", config.maxDistanceMeters).put("defaultDistance", config.defaultDistanceMeters)
        .put("minVertical", config.minVerticalOffsetMeters).put("maxVertical", config.maxVerticalOffsetMeters).put("defaultVertical", config.defaultVerticalOffsetMeters)
        .put("minHorizontal", config.minHorizontalOffsetMeters).put("maxHorizontal", config.maxHorizontalOffsetMeters).put("defaultHorizontal", config.defaultHorizontalOffsetMeters)
        .put("controlsAutoHideSeconds", RuntimeConfigRegistry.current.playback.controlsAutoHideMs / 1000.0)
}

/** Original player resumes only when a known duration is less than 95% complete. */
internal fun playbackResumePositionMs(positionMs: Long, durationMs: Long): Long =
    if (positionMs > 0 && durationMs > 0 && positionMs.toDouble() / durationMs < .95) positionMs else 0
