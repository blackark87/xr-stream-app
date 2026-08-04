package blackark.app.vr.ui.screens

import androidx.xr.runtime.math.Vector3

private const val PLAYBACK_UI_HEAD_FOLLOW_DEAD_ZONE_COSINE = 0.9781476f // 12 degrees

internal data class PlaybackLayerPolicy(
    val retainFallbackRevealLayer: Boolean,
    val showFallbackRevealLayer: Boolean,
    val enableSurfaceToggleInput: Boolean,
    val showControlsLayer: Boolean,
    val subtitleAcceptsInput: Boolean = false,
)

internal fun resolvePlaybackLayerPolicy(
    isSurfaceReady: Boolean,
    isImmersive: Boolean,
    showControls: Boolean,
    controlsInputLocked: Boolean,
    seekPreviewActive: Boolean,
    navigationExitPending: Boolean = false,
): PlaybackLayerPolicy {
    val canHandleBackgroundToggle =
        isSurfaceReady &&
                !controlsInputLocked &&
                !seekPreviewActive &&
                !navigationExitPending
    return PlaybackLayerPolicy(
        retainFallbackRevealLayer = isSurfaceReady && isImmersive,
        showFallbackRevealLayer = isImmersive && canHandleBackgroundToggle && !showControls,
        enableSurfaceToggleInput = isImmersive && canHandleBackgroundToggle,
        showControlsLayer = isSurfaceReady && showControls,
    )
}

internal fun shouldShowImmersiveSubtitlePanel(
    subtitlesPresent: Boolean,
    showControls: Boolean,
): Boolean = subtitlesPresent && !showControls

internal fun shouldRetainImmersiveSubtitlePanel(
    subtitlesPresent: Boolean,
): Boolean = subtitlesPresent

internal fun shouldEnablePlaybackUiHeadFollow(
    isEnabledBySetting: Boolean = true,
    isTwoDimensional: Boolean,
    isImmersive: Boolean,
    showControlsLayer: Boolean,
): Boolean =
    isEnabledBySetting && (isTwoDimensional || (isImmersive && showControlsLayer))

internal fun shouldUpdatePlaybackUiHeadFollow(
    currentForward: Vector3?,
    targetForward: Vector3,
    interactionLocked: Boolean,
): Boolean {
    if (currentForward == null) return true
    if (interactionLocked) return false
    if (currentForward.lengthSquared < 1e-6f || targetForward.lengthSquared < 1e-6f) return false

    val similarity = currentForward.toNormalized() dot targetForward.toNormalized()
    return similarity < PLAYBACK_UI_HEAD_FOLLOW_DEAD_ZONE_COSINE
}
