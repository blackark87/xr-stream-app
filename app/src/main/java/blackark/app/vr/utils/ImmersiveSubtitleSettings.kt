package blackark.app.vr.utils

import androidx.xr.runtime.math.Vector3

const val MIN_IMMERSIVE_SUBTITLE_DISTANCE_METERS = 0.75f
const val MAX_IMMERSIVE_SUBTITLE_DISTANCE_METERS = 2.0f
const val DEFAULT_IMMERSIVE_SUBTITLE_DISTANCE_METERS = 1.5f
const val IMMERSIVE_SUBTITLE_REFERENCE_DISTANCE_METERS = 1.75f
const val IMMERSIVE_SUBTITLE_DISTANCE_SLIDER_STEPS = 24
const val MIN_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS = -0.30f
const val MAX_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS = 0.30f
const val DEFAULT_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS = -0.10f
const val IMMERSIVE_UI_HORIZONTAL_OFFSET_SLIDER_STEPS = 11

internal data class ImmersiveSubtitlePlacement(
    val distanceMeters: Float,
    val forwardOffsetMeters: Float,
    val scale: Float,
)

internal data class ImmersivePanelOffsetDp(
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

internal fun normalizeImmersiveUiHorizontalOffsetMeters(offsetMeters: Float): Float {
    if (!offsetMeters.isFinite()) {
        return DEFAULT_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS
    }
    return offsetMeters.coerceIn(
        MIN_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS,
        MAX_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS,
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
    worldRight: Vector3,
    placement: ImmersiveSubtitlePlacement,
    horizontalOffsetMeters: Float,
    pixelsPerDp: Float,
    dpPerMeter: Float,
): ImmersivePanelOffsetDp {
    val safePixelsPerDp = pixelsPerDp.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeDpPerMeter = dpPerMeter.takeIf { it.isFinite() && it > 0f } ?: 0f
    val depthOffsetDp = placement.forwardOffsetMeters * safeDpPerMeter
    val horizontalOffsetDp =
        normalizeImmersiveUiHorizontalOffsetMeters(horizontalOffsetMeters) * safeDpPerMeter

    return ImmersivePanelOffsetDp(
        x =
            (baseTranslationPixels.x / safePixelsPerDp) +
                    (worldForward.x * depthOffsetDp) +
                    (worldRight.x * horizontalOffsetDp),
        y =
            (baseTranslationPixels.y / safePixelsPerDp) +
                    (worldForward.y * depthOffsetDp) +
                    (worldRight.y * horizontalOffsetDp),
        z =
            (baseTranslationPixels.z / safePixelsPerDp) +
                    (worldForward.z * depthOffsetDp) +
                    (worldRight.z * horizontalOffsetDp),
    )
}

internal fun resolveImmersiveUiHorizontalOffsetDp(
    worldRight: Vector3,
    horizontalOffsetMeters: Float,
    dpPerMeter: Float,
): ImmersivePanelOffsetDp {
    val safeDpPerMeter = dpPerMeter.takeIf { it.isFinite() && it > 0f } ?: 0f
    val offsetDp =
        normalizeImmersiveUiHorizontalOffsetMeters(horizontalOffsetMeters) * safeDpPerMeter
    return ImmersivePanelOffsetDp(
        x = worldRight.x * offsetDp,
        y = worldRight.y * offsetDp,
        z = worldRight.z * offsetDp,
    )
}
