package blackark.app.vr.ui.screens

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
): PlaybackLayerPolicy {
    val canHandleBackgroundToggle =
        isSurfaceReady &&
                !controlsInputLocked &&
                !seekPreviewActive
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
    isTwoDimensional: Boolean,
    isImmersive: Boolean,
    showControlsLayer: Boolean,
): Boolean = isTwoDimensional || (isImmersive && showControlsLayer)
