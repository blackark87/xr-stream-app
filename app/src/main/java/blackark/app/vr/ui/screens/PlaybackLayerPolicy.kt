package blackark.app.vr.ui.screens

internal data class PlaybackLayerPolicy(
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
        showFallbackRevealLayer = isImmersive && canHandleBackgroundToggle && !showControls,
        enableSurfaceToggleInput = isImmersive && canHandleBackgroundToggle,
        showControlsLayer = isSurfaceReady && showControls,
    )
}

internal fun shouldShowImmersiveSubtitlePanel(
    subtitlesPresent: Boolean,
    showControls: Boolean,
): Boolean = subtitlesPresent && !showControls
