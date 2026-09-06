package blackark.app.vr.ui.screens

internal fun isPlaybackSurfaceAvailable(
    hasPresentedSurface: Boolean,
    isLoading: Boolean,
    hasError: Boolean,
): Boolean = !hasError && (hasPresentedSurface || !isLoading)

internal data class PlaybackLayerPolicy(
    val retainBackgroundInputLayer: Boolean,
    val showHiddenControlsInputOverlay: Boolean,
    val enableBackgroundToggleInput: Boolean,
    val showControlsLayer: Boolean,
)

internal fun resolvePlaybackLayerPolicy(
    isSurfaceReady: Boolean,
    isImmersive: Boolean,
    showControls: Boolean,
    controlsInputLocked: Boolean,
    seekPreviewActive: Boolean,
    navigationExitPending: Boolean = false,
): PlaybackLayerPolicy {
    val needsBackgroundPointerLayer = isImmersive
    val canHandleBackgroundToggle =
        isSurfaceReady &&
            needsBackgroundPointerLayer &&
            !controlsInputLocked &&
            !seekPreviewActive &&
            !navigationExitPending
    return PlaybackLayerPolicy(
        // Retain one stable hit plane for screen clicks/touches throughout immersive playback.
        // The controls panel is placed in front of it, so buttons still receive input first.
        retainBackgroundInputLayer = needsBackgroundPointerLayer,
        showHiddenControlsInputOverlay =
            canHandleBackgroundToggle && !showControls,
        enableBackgroundToggleInput = canHandleBackgroundToggle,
        showControlsLayer = isSurfaceReady && showControls && !navigationExitPending,
    )
}

internal fun shouldShowImmersiveSubtitlePanel(
    subtitlesPresent: Boolean,
    showControls: Boolean,
): Boolean = subtitlesPresent && !showControls

internal fun shouldRetainImmersiveSubtitlePanel(
    subtitlesPresent: Boolean,
): Boolean = subtitlesPresent
