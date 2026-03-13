package blackark.app.vr.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.xr.arcore.ArDevice
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.MovePolicy
import androidx.xr.compose.subspace.SpatialExternalSurface
import androidx.xr.compose.subspace.SpatialExternalSurface180Hemisphere
import androidx.xr.compose.subspace.SpatialExternalSurface360Sphere
import androidx.xr.compose.subspace.SpatialExternalSurfaceScope
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.StereoMode
import androidx.xr.compose.subspace.layout.InteractionPolicy
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.fillMaxSize
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.rotate
import androidx.xr.compose.subspace.layout.rotateToLookAtUser
import androidx.xr.compose.subspace.layout.width
import androidx.xr.runtime.DeviceTrackingMode
import androidx.xr.runtime.SessionConfigureSuccess
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import blackark.app.vr.data.database.AppDatabase
import blackark.app.vr.data.repository.VideoRepository
import blackark.app.vr.ui.components.XRPlaybackControls
import blackark.app.vr.ui.viewmodel.PlayerEvent
import blackark.app.vr.ui.viewmodel.VideoFormat
import blackark.app.vr.ui.viewmodel.VideoPlayerState
import blackark.app.vr.ui.viewmodel.VideoPlayerViewModel
import blackark.app.vr.ui.viewmodel.VideoPlayerViewModelFactory
import kotlinx.coroutines.delay

private const val TAG = "VideoPlayerScreen"

private data class HeadFollowPose(
    val rotation: Quaternion,
    val forward: Vector3,
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoFilePath: String,
    videoFileName: String,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val videoRepository = remember { VideoRepository(database.videoDao()) }

    val videoPlayerViewModel: VideoPlayerViewModel = viewModel(
        factory = VideoPlayerViewModelFactory(videoRepository),
    )

    val playerState by videoPlayerViewModel.state.collectAsState()

    LaunchedEffect(videoFilePath, videoFileName) {
        val smbConfig = blackark.app.vr.AppState.smbConfig
        if (videoFilePath.isNotEmpty() && smbConfig != null) {
            val videoFile = blackark.app.vr.network.SMBFileItem(
                name = videoFileName,
                path = videoFilePath,
                isDirectory = false,
                size = 0,
                lastModified = 0,
            )
            videoPlayerViewModel.initializePlayer(context, smbConfig, videoFile)
        }
    }

    LaunchedEffect(videoPlayerViewModel) {
        videoPlayerViewModel.playerEvents.collect { event ->
            when (event) {
                PlayerEvent.NavigateBack -> {
                    videoPlayerViewModel.releasePlayerBeforeNavigateBack()
                    delay(220)
                    onNavigateBack()
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { videoPlayerViewModel.releasePlayerAsync() }
    }

    Subspace {
        SpatialVideoPlayerContent(
            videoPlayerViewModel = videoPlayerViewModel,
            playerState = playerState,
            onNavigateBack = { videoPlayerViewModel.requestNavigateBack() },
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SpatialVideoPlayerContent(
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: VideoPlayerState,
    onNavigateBack: () -> Unit,
) {
    val exoPlayer by videoPlayerViewModel.playerFlow.collectAsState()
    val showControls = playerState.showControls
    val session = LocalSession.current

    BackHandler {
        onNavigateBack()
    }
    val spatialCapabilities = LocalSpatialCapabilities.current

    val immersiveRequested = playerState.videoFormat != VideoFormat.Format2D
    val supportsImmersiveDome = spatialCapabilities.isContent3dEnabled
    val shouldUseImmersiveDome = immersiveRequested && supportsImmersiveDome
    val isSurfaceReady = !playerState.isLoading && playerState.error == null

    val enableHeadFollowIn2D = playerState.videoFormat == VideoFormat.Format2D
    // Disabled for 180 stereo because the custom lock-rotation path can blank hemisphere rendering.
    val enableHeadFollowIn180Stereo = false
    val shouldEnableHeadFollow = enableHeadFollowIn2D

    val headFollowPose by produceState<HeadFollowPose?>(
        initialValue = null,
        session,
        shouldEnableHeadFollow,
    ) {
        value = null
        if (!shouldEnableHeadFollow) return@produceState

        val activeSession = session ?: return@produceState
        val arDevice = runCatching { ArDevice.getInstance(activeSession) }.getOrNull()
        if (arDevice == null) {
            Log.w(TAG, "ArDevice is unavailable; using default orientation")
            return@produceState
        }

        var smoothedRotation: Quaternion? = null
        var smoothedForward: Vector3? = null
        arDevice.state.collect { deviceState ->
            val forward = deviceState.devicePose.forward
            val horizontalForward = Vector3(forward.x, 0f, forward.z)
            if (horizontalForward.lengthSquared < 1e-6f) return@collect

            val normalizedForward = horizontalForward.toNormalized()
            val blendedForward =
                if (smoothedForward == null) {
                    normalizedForward
                } else {
                    Vector3.lerp(smoothedForward!!, normalizedForward, 0.45f)
                }
            if (blendedForward.lengthSquared < 1e-6f) return@collect
            smoothedForward = blendedForward.toNormalized()

            val targetRotation = Quaternion.fromLookTowards(smoothedForward!!, Vector3.Up)
            smoothedRotation =
                if (smoothedRotation == null) {
                    targetRotation
                } else {
                    Quaternion.slerp(smoothedRotation!!, targetRotation, 0.45f)
                }

            value = HeadFollowPose(rotation = smoothedRotation!!, forward = smoothedForward!!)
        }
    }

    // Device tracking must be enabled for head-follow behavior.
    LaunchedEffect(session, shouldEnableHeadFollow) {
        val activeSession = session ?: return@LaunchedEffect
        if (!shouldEnableHeadFollow) return@LaunchedEffect

        val currentConfig = activeSession.config
        if (currentConfig.deviceTracking != DeviceTrackingMode.DISABLED) {
            return@LaunchedEffect
        }

        val updatedConfig =
            currentConfig.copy(deviceTracking = DeviceTrackingMode.LAST_KNOWN)
        val result = activeSession.configure(updatedConfig)
        if (result is SessionConfigureSuccess) {
            Log.d(TAG, "Enabled XR device tracking for requested head-follow modes")
        } else {
            Log.w(TAG, "Failed to enable XR device tracking: ${result::class.java.simpleName}")
        }
    }

    LaunchedEffect(immersiveRequested, spatialCapabilities.isContent3dEnabled) {
        if (immersiveRequested && !supportsImmersiveDome) {
            Log.w(
                TAG,
                "Falling back to flat surface: content3D=${spatialCapabilities.isContent3dEnabled}",
            )
        }
    }

    val xrStereoMode = when (playerState.stereoMode) {
        blackark.app.vr.ui.viewmodel.StereoMode.Mono -> StereoMode.Mono
        blackark.app.vr.ui.viewmodel.StereoMode.SideBySide -> StereoMode.SideBySide
        blackark.app.vr.ui.viewmodel.StereoMode.TopBottom -> StereoMode.TopBottom
    }

    val toggleInteractionPolicy =
        if (isSurfaceReady && !showControls) {
            InteractionPolicy.clickable {
                videoPlayerViewModel.toggleControls()
            }
        } else {
            null
        }

    if (exoPlayer == null) {
        // Avoid panel pop/flicker in immersive startup: keep black background until surface is ready.
        if (playerState.videoFile == null || playerState.videoFormat != VideoFormat.Format2D) {
            return
        }
        PlayerLoadingPanel(errorMessage = playerState.error)
        return
    }

    if (shouldUseImmersiveDome) {
        ImmersivePlayer(
            exoPlayer = exoPlayer,
            videoFormat = playerState.videoFormat,
            stereoMode = xrStereoMode,
            interactionPolicy = toggleInteractionPolicy,
            headLockedRotation180 = if (enableHeadFollowIn180Stereo) headFollowPose?.rotation else null,
        )

        if (showControls && isSurfaceReady) {
            val immersiveControlsModifier =
                if (playerState.stereoMode == blackark.app.vr.ui.viewmodel.StereoMode.Mono) {
                    SubspaceModifier
                        .width(1280.dp)
                        .height(720.dp)
                } else {
                    // Larger panel in stereo to reduce readability strain.
                    SubspaceModifier
                        .width(1520.dp)
                        .height(900.dp)
                        .offset(y = 60.dp)
                }

            SpatialPanel(modifier = immersiveControlsModifier) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            videoPlayerViewModel.toggleControls()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    XRPlaybackControls(
                        videoPlayerViewModel = videoPlayerViewModel,
                        playerState = playerState,
                        onNavigateBack = { videoPlayerViewModel.requestNavigateBack() },
                    )
                }
            }
        }
    } else {
        Standard2DPlayer(
            exoPlayer = exoPlayer,
            stereoMode = xrStereoMode,
            interactionPolicy = toggleInteractionPolicy,
            showControls = showControls,
            isSurfaceReady = isSurfaceReady,
            videoPlayerViewModel = videoPlayerViewModel,
            playerState = playerState,
            onNavigateBack = { videoPlayerViewModel.requestNavigateBack() },
            headFollowPose = if (enableHeadFollowIn2D) headFollowPose else null,
        )
    }
}

@Composable
private fun PlayerLoadingPanel(errorMessage: String?) {
    val colors = MaterialTheme.colorScheme

    SpatialPanel(
        modifier = SubspaceModifier
            .width(1280.dp)
            .height(720.dp),
        dragPolicy = MovePolicy(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.background,
                            colors.surface,
                        ),
                    ),
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.82f))
                    .border(
                        width = 1.dp,
                        color = colors.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(28.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (errorMessage.isNullOrBlank()) {
                    CircularProgressIndicator(color = colors.primary)
                } else {
                    Text(
                        text = errorMessage,
                        color = colors.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 36.dp),
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Standard2DPlayer(
    exoPlayer: ExoPlayer?,
    stereoMode: StereoMode,
    interactionPolicy: InteractionPolicy?,
    showControls: Boolean,
    isSurfaceReady: Boolean,
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: VideoPlayerState,
    onNavigateBack: () -> Unit,
    headFollowPose: HeadFollowPose? = null,
) {
    if (exoPlayer != null) {
        // Recreate the XR surface when stereo layout changes to avoid renderer desync/black frames.
        key(stereoMode) {
            SpatialExternalSurface(
                modifier = buildFlatSurfaceModifier(headFollowPose),
                stereoMode = stereoMode,
                dragPolicy = MovePolicy(),
                interactionPolicy = interactionPolicy,
            ) {
                bindExoPlayerSurface(exoPlayer)

                if (showControls && isSurfaceReady) {
                    SpatialPanel(modifier = SubspaceModifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    videoPlayerViewModel.toggleControls()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            XRPlaybackControls(
                                videoPlayerViewModel = videoPlayerViewModel,
                                playerState = playerState,
                                onNavigateBack = { videoPlayerViewModel.requestNavigateBack() },
                            )
                        }
                    }
                }
            }
        }
    }
}


private fun buildFlatSurfaceModifier(headFollowPose: HeadFollowPose?): SubspaceModifier {
    val baseModifier =
        SubspaceModifier
            .width(1280.dp)
            .height(720.dp)

    if (headFollowPose == null) {
        return baseModifier
    }

    val followDistanceDp = 900f
    val followHeightDp = -40f
    return baseModifier
        .offset(
            x = (headFollowPose.forward.x * followDistanceDp).dp,
            y = followHeightDp.dp,
            z = (headFollowPose.forward.z * followDistanceDp).dp,
        )
        .rotate(headFollowPose.rotation)
}

@Composable
fun ImmersivePlayer(
    exoPlayer: ExoPlayer?,
    videoFormat: VideoFormat,
    stereoMode: StereoMode,
    interactionPolicy: InteractionPolicy?,
    headLockedRotation180: Quaternion? = null,
) {
    if (exoPlayer == null) return

    when (videoFormat) {
        VideoFormat.Format180 -> {
            val hemisphereModifier =
                headLockedRotation180?.let { rotation ->
                    SubspaceModifier.rotate(rotation)
                } ?: SubspaceModifier.rotateToLookAtUser()
            key(stereoMode) {
                SpatialExternalSurface180Hemisphere(
                    modifier = hemisphereModifier,
                    stereoMode = stereoMode,
                    interactionPolicy = interactionPolicy,
                ) {
                    bindExoPlayerSurface(exoPlayer)
                }
            }
        }

        VideoFormat.Format360 -> {
            key(stereoMode) {
                SpatialExternalSurface360Sphere(
                    stereoMode = stereoMode,
                    interactionPolicy = interactionPolicy,
                ) {
                    bindExoPlayerSurface(exoPlayer)
                }
            }
        }

        VideoFormat.Format2D -> Unit
    }
}

private fun SpatialExternalSurfaceScope.bindExoPlayerSurface(exoPlayer: ExoPlayer) {
    onSurfaceCreated { surface ->
        exoPlayer.setVideoSurface(surface)
    }
    onSurfaceDestroyed { surface ->
        // Only clear the surface instance being destroyed; leave any replacement surface intact.
        exoPlayer.clearVideoSurface(surface)
    }
}
