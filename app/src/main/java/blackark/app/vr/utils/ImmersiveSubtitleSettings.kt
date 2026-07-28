package blackark.app.vr.utils

import androidx.xr.runtime.math.Vector3

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

internal data class ImmersiveSubtitlePanelOffsetDp(
    val x: Float,
    val y: Float,
    val z: Float,
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

internal fun resolveImmersiveSubtitlePanelOffsetDp(
    baseTranslationPixels: Vector3,
    worldForward: Vector3,
    placement: ImmersiveSubtitlePlacement,
    pixelsPerDp: Float,
    dpPerMeter: Float,
): ImmersiveSubtitlePanelOffsetDp {
    val safePixelsPerDp = pixelsPerDp.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeDpPerMeter = dpPerMeter.takeIf { it.isFinite() && it > 0f } ?: 0f
    val depthOffsetDp = placement.forwardOffsetMeters * safeDpPerMeter

    return ImmersiveSubtitlePanelOffsetDp(
        x = (baseTranslationPixels.x / safePixelsPerDp) + (worldForward.x * depthOffsetDp),
        y = (baseTranslationPixels.y / safePixelsPerDp) + (worldForward.y * depthOffsetDp),
        z = (baseTranslationPixels.z / safePixelsPerDp) + (worldForward.z * depthOffsetDp),
    )
}
