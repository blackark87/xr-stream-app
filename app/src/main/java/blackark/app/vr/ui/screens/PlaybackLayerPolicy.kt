package blackark.app.vr.ui.screens

import androidx.xr.runtime.math.Vector3
import blackark.app.vr.ui.viewmodel.VideoFormat
import blackark.app.vr.remote.RuntimeConfigRegistry
import kotlin.math.cos

internal data class PlaybackLayerPolicy(
    val retainBackgroundInputLayer: Boolean,
    val showHiddenControlsInputOverlay: Boolean,
    val enableBackgroundToggleInput: Boolean,
    val showControlsLayer: Boolean,
)

internal data class PlaybackFollowPolicy(
    val follow180Video: Boolean,
    val followPlaybackControls: Boolean,
)

internal fun resolvePlaybackFollowPolicy(
    videoFormat: VideoFormat,
    playbackControlsHeadFollowEnabled: Boolean,
): PlaybackFollowPolicy =
    when (videoFormat) {
        VideoFormat.Format2D -> PlaybackFollowPolicy(
            follow180Video = false,
            followPlaybackControls = false,
        )

        VideoFormat.Format180 -> PlaybackFollowPolicy(
            follow180Video = true,
            followPlaybackControls = false,
        )

        VideoFormat.Format360 -> PlaybackFollowPolicy(
            follow180Video = false,
            followPlaybackControls = playbackControlsHeadFollowEnabled,
        )
    }

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
        enableBackgroundToggleInput = isImmersive && canHandleBackgroundToggle,
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

internal fun shouldUpdate180VideoHeadFollow(
    currentForward: Vector3?,
    targetForward: Vector3,
    interactionLocked: Boolean,
): Boolean {
    if (currentForward == null) return true
    if (interactionLocked) return false
    if (currentForward.lengthSquared < 1e-6f || targetForward.lengthSquared < 1e-6f) return false

    val similarity = currentForward.toNormalized() dot targetForward.toNormalized()
    val threshold = cos(
        Math.toRadians(
            RuntimeConfigRegistry.current.headFollow.video180DeadZoneDegrees.toDouble()
        )
    ).toFloat()
    return similarity < threshold
}

internal fun shouldUpdatePlaybackUiHeadFollow(
    currentForward: Vector3?,
    targetForward: Vector3,
    interactionLocked: Boolean,
): Boolean {
    if (currentForward == null) return true
    if (interactionLocked) return false
    if (currentForward.lengthSquared < 1e-6f || targetForward.lengthSquared < 1e-6f) return false

    val similarity = currentForward.toNormalized() dot targetForward.toNormalized()
    val threshold = cos(
        Math.toRadians(
            RuntimeConfigRegistry.current.headFollow.controls360DeadZoneDegrees.toDouble()
        )
    ).toFloat()
    return similarity < threshold
}
