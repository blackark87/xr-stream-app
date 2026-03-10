package com.example.myapplication.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.subspace.layout.lookAtUser
import com.example.myapplication.data.database.AppDatabase
import com.example.myapplication.data.repository.VideoRepository
import com.example.myapplication.ui.components.XRPlaybackControls
import com.example.myapplication.ui.viewmodel.PlayerEvent
import com.example.myapplication.ui.viewmodel.VideoFormat
import com.example.myapplication.ui.viewmodel.VideoPlayerState
import com.example.myapplication.ui.viewmodel.VideoPlayerViewModel
import com.example.myapplication.ui.viewmodel.VideoPlayerViewModelFactory
import kotlinx.coroutines.delay

private const val TAG = "VideoPlayerScreen"

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
        val smbConfig = com.example.myapplication.AppState.smbConfig
        if (videoFilePath.isNotEmpty() && smbConfig != null) {
            val videoFile = com.example.myapplication.network.SMBFileItem(
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

    BackHandler {
        onNavigateBack()
    }
    val spatialCapabilities = LocalSpatialCapabilities.current

    val immersiveRequested = playerState.videoFormat != VideoFormat.Format2D
    val supportsImmersiveDome = spatialCapabilities.isContent3dEnabled
    val shouldUseImmersiveDome = immersiveRequested && supportsImmersiveDome
    val isSurfaceReady = !playerState.isLoading && playerState.error == null

    LaunchedEffect(immersiveRequested, spatialCapabilities.isContent3dEnabled) {
        if (immersiveRequested && !supportsImmersiveDome) {
            Log.w(
                TAG,
                "Falling back to flat surface: content3D=${spatialCapabilities.isContent3dEnabled}",
            )
        }
    }

    val xrStereoMode = when (playerState.stereoMode) {
        com.example.myapplication.ui.viewmodel.StereoMode.Mono -> StereoMode.Mono
        com.example.myapplication.ui.viewmodel.StereoMode.SideBySide -> StereoMode.SideBySide
        com.example.myapplication.ui.viewmodel.StereoMode.TopBottom -> StereoMode.TopBottom
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
        )

        if (showControls && isSurfaceReady) {
            val immersiveControlsModifier =
                if (playerState.stereoMode == com.example.myapplication.ui.viewmodel.StereoMode.Mono) {
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
        )
    }
}

@Composable
private fun PlayerLoadingPanel(errorMessage: String?) {
    SpatialPanel(
        modifier = SubspaceModifier
            .width(1280.dp)
            .height(720.dp),
        dragPolicy = MovePolicy(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (errorMessage.isNullOrBlank()) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text(text = errorMessage, color = Color.White)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun Standard2DPlayer(
    exoPlayer: ExoPlayer?,
    stereoMode: StereoMode,
    interactionPolicy: InteractionPolicy?,
    showControls: Boolean,
    isSurfaceReady: Boolean,
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: VideoPlayerState,
    onNavigateBack: () -> Unit,
) {
    if (exoPlayer != null) {
        SpatialExternalSurface(
            modifier = SubspaceModifier
                .width(1280.dp)
                .height(720.dp),
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

@Composable
fun ImmersivePlayer(
    exoPlayer: ExoPlayer?,
    videoFormat: VideoFormat,
    stereoMode: StereoMode,
    interactionPolicy: InteractionPolicy?,
) {
    if (exoPlayer == null) return

    when (videoFormat) {
        VideoFormat.Format180 -> {
            SpatialExternalSurface180Hemisphere(
                modifier = SubspaceModifier.lookAtUser(),
                stereoMode = stereoMode,
                interactionPolicy = interactionPolicy,
            ) {
                bindExoPlayerSurface(exoPlayer)
            }
        }

        VideoFormat.Format360 -> {
            SpatialExternalSurface360Sphere(
                stereoMode = stereoMode,
                interactionPolicy = interactionPolicy,
            ) {
                bindExoPlayerSurface(exoPlayer)
            }
        }

        VideoFormat.Format2D -> Unit
    }
}

private fun SpatialExternalSurfaceScope.bindExoPlayerSurface(exoPlayer: ExoPlayer) {
    onSurfaceCreated { surface ->
        exoPlayer.setVideoSurface(surface)
    }
    onSurfaceDestroyed {
        exoPlayer.setVideoSurface(null)
    }
}






