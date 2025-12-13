package com.example.myapplication.ui.screens

import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.xr.arcore.runtime.Earth
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.MovePolicy
import androidx.xr.compose.subspace.ResizePolicy
import androidx.xr.compose.subspace.SpatialBox
import androidx.xr.compose.subspace.SpatialExternalSurface
import androidx.xr.compose.subspace.SpatialExternalSurface180Hemisphere
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.StereoMode

import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.fillMaxSize
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.scale
import androidx.xr.compose.subspace.layout.width
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.runtime.math.Pose
import androidx.xr.scenecore.SurfaceEntity
import androidx.xr.scenecore.scene
import com.example.myapplication.data.database.AppDatabase
import com.example.myapplication.data.repository.VideoRepository
import com.example.myapplication.ui.components.XRPlaybackControls
import com.example.myapplication.ui.viewmodel.VideoFormat
import com.example.myapplication.ui.viewmodel.VideoPlayerState
import com.example.myapplication.ui.viewmodel.VideoPlayerViewModel
import com.example.myapplication.ui.viewmodel.VideoPlayerViewModelFactory

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoFilePath: String,
    videoFileName: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val videoRepository = remember { VideoRepository(database.videoDao()) }

    val videoPlayerViewModel: VideoPlayerViewModel = viewModel(
        factory = VideoPlayerViewModelFactory(videoRepository)
    )

    val playerState by videoPlayerViewModel.state.collectAsState()

    // Initialize player when video file and SMB config are available
    LaunchedEffect(videoFilePath, videoFileName) {
        val smbConfig = com.example.myapplication.AppState.smbConfig

        if (videoFilePath.isNotEmpty() && smbConfig != null) {
            // Create a temporary SMBFileItem for initialization
            val videoFile = com.example.myapplication.network.SMBFileItem(
                name = videoFileName,
                path = videoFilePath,
                isDirectory = false,
                size = 0, // Unknown size, but not needed for path
                lastModified = 0
            )
            videoPlayerViewModel.initializePlayer(context, smbConfig, videoFile)
        }
    }

    // Handle player events (e.g. navigation)
    LaunchedEffect(videoPlayerViewModel) {
        videoPlayerViewModel.playerEvents.collect { event ->
            when (event) {
                is com.example.myapplication.ui.viewmodel.PlayerEvent.NavigateBack -> {
                    onNavigateBack()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoPlayerViewModel.releasePlayer()
        }
    }

    Subspace {
        SpatialVideoPlayerContent(
            videoPlayerViewModel = videoPlayerViewModel,
            playerState = playerState,
            onNavigateBack = onNavigateBack
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SpatialVideoPlayerContent(
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: VideoPlayerState,
    onNavigateBack: () -> Unit
) {
    val exoPlayer by videoPlayerViewModel.playerFlow.collectAsState()
    val showControls = playerState.showControls
    LocalSession.current

    if (playerState.videoFormat == VideoFormat.Format2D) {
        SpatialBox(
            modifier = SubspaceModifier.height(720.dp)
                .width(1280.dp)
                .offset(0.dp, 0.dp)
        ) {
            Standard2DPlayer(
                exoPlayer = exoPlayer,
                onToggleControls = videoPlayerViewModel::toggleControls
            )
        }
    } else {
        SpatialBox(
            modifier = SubspaceModifier.fillMaxSize()
        ) {
            ImmersivePlayer(
                exoPlayer = exoPlayer,
                onToggleControls = videoPlayerViewModel::toggleControls
            )
        }
    }

    if (showControls && !playerState.isLoading && playerState.error == null) {
        SpatialPanel(
            modifier = SubspaceModifier.height(500.dp).width(500.dp)
        ) {
            Row(
                Modifier
                    .background(color = Color.Black)
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                XRPlaybackControls(
                    videoPlayerViewModel = videoPlayerViewModel,
                    playerState = playerState,
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

@Composable
fun Standard2DPlayer(
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    onToggleControls: () -> Unit
) {
    if (exoPlayer != null) {
        SpatialExternalSurface(
            modifier = SubspaceModifier.fillMaxSize(),
            stereoMode = StereoMode.Mono
        ) {

            onSurfaceCreated { surface ->
                exoPlayer.setVideoSurface(surface)
                exoPlayer.prepare()
                exoPlayer.play()
            }

            onSurfaceDestroyed { exoPlayer.release() }
        }

    }
}

@Composable
fun ImmersivePlayer(
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    onToggleControls: () -> Unit
) {
    if (exoPlayer != null) {
        SpatialExternalSurface180Hemisphere(
            modifier = SubspaceModifier.fillMaxSize(),
            stereoMode = StereoMode.SideBySide
        ) {

            onSurfaceCreated { surface ->
                exoPlayer.setVideoSurface(surface)
                exoPlayer.prepare()
                exoPlayer.play()
            }

            onSurfaceDestroyed { exoPlayer.release() }
        }

    }
}

