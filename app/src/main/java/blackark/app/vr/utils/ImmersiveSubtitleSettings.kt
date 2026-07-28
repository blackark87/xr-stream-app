package blackark.app.vr.utils

const val MIN_IMMERSIVE_SUBTITLE_DISTANCE_METERS = 1.0f
const val MAX_IMMERSIVE_SUBTITLE_DISTANCE_METERS = 5.0f
const val DEFAULT_IMMERSIVE_SUBTITLE_DISTANCE_METERS = 4.0f
const val IMMERSIVE_SUBTITLE_REFERENCE_DISTANCE_METERS = 1.75f
const val IMMERSIVE_SUBTITLE_DISTANCE_SLIDER_STEPS = 39

internal data class ImmersiveSubtitlePlacement(
    val distanceMeters: Float,
    val forwardOffsetMeters: Float,
    val scale: Float,
)

internal fun normalizeImmersiveSubtitleDistanceMeters(distanceMeters: Float): Float {
    if (!distanceMeters.isFinite()) {
        return DEFAULT_IMMERSIVE_SUBTITLE_DISTANCE_METERS
    }
    return distanceMeters.coerceIn(
        MIN_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
        MAX_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
    )
}

internal fun resolveImmersiveSubtitlePlacement(
    distanceMeters: Float,
): ImmersiveSubtitlePlacement {
    val normalizedDistance = normalizeImmersiveSubtitleDistanceMeters(distanceMeters)
    return ImmersiveSubtitlePlacement(
        distanceMeters = normalizedDistance,
        forwardOffsetMeters =
            normalizedDistance - IMMERSIVE_SUBTITLE_REFERENCE_DISTANCE_METERS,
        scale = normalizedDistance / IMMERSIVE_SUBTITLE_REFERENCE_DISTANCE_METERS,
    )
}
