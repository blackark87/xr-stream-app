package blackark.app.vr.ui.screens

internal data class PlaybackLayerPolicy(
    val showRevealInputLayer: Boolean,
    val showControlsLayer: Boolean,
    val enableSurfaceRevealInput: Boolean,
    val subtitleAcceptsInput: Boolean = false,
)

internal fun resolvePlaybackLayerPolicy(
    isSurfaceReady: Boolean,
    showControls: Boolean,
    controlsInputLocked: Boolean,
    seekPreviewActive: Boolean,
): PlaybackLayerPolicy {
    val canRevealControls =
        isSurfaceReady &&
                !showControls &&
                !controlsInputLocked &&
                !seekPreviewActive

    return PlaybackLayerPolicy(
        showRevealInputLayer = canRevealControls,
        showControlsLayer = isSurfaceReady && showControls,
        enableSurfaceRevealInput = canRevealControls,
    )
}
