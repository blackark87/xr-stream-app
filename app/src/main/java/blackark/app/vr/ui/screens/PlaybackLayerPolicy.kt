package blackark.app.vr.ui.screens

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
    val canHandleBackgroundToggle =
        isSurfaceReady &&
            !controlsInputLocked &&
            !seekPreviewActive &&
            !navigationExitPending
    return PlaybackLayerPolicy(
        // Keep the entity parented through ExoPlayer BUFFERING transitions. Input is disabled
        // below while the surface is not ready, but queued XR events can still finish safely.
        retainBackgroundInputLayer = isImmersive,
        showHiddenControlsInputOverlay =
            isImmersive && canHandleBackgroundToggle && !showControls,
        enableBackgroundToggleInput =
            isImmersive && canHandleBackgroundToggle && !showControls,
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
