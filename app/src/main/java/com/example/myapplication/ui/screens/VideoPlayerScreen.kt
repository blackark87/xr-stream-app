package com.example.myapplication.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialBox
import androidx.xr.compose.subspace.SpatialExternalSurface
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.StereoMode
import androidx.xr.compose.subspace.layout.SpatialAlignment
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.fillMaxSize
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.scale
import androidx.xr.compose.subspace.layout.width
import androidx.xr.runtime.Config
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.SurfaceEntity
import androidx.xr.scenecore.scene

import com.example.myapplication.data.database.AppDatabase
import com.example.myapplication.data.repository.VideoRepository
import com.example.myapplication.ui.components.XRPlaybackControls
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

    // Check if XR feature is available on this device
    val hasXrFeature = remember {
        context.packageManager.hasSystemFeature("android.software.xr.api.spatial")
    }

    println("XR: VideoPlayerScreen composed. HasXR: $hasXrFeature, File: $videoFileName")

    // Initialize player when video file and SMB config are available
    LaunchedEffect(videoFilePath, videoFileName) {
        val smbConfig = com.example.myapplication.AppState.smbConfig

        println("XR: Checking init params - File: $videoFileName, Config: ${smbConfig != null}")

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
        } else {
            println("XR: Skipping player init - Missing file or config")
        }
    }

    // Handle player events (e.g. navigation)
    LaunchedEffect(videoPlayerViewModel) {
        videoPlayerViewModel.playerEvents.collect { event ->
            when (event) {
                is com.example.myapplication.ui.viewmodel.PlayerEvent.NavigateBack -> {
                    println("XR: Received NavigateBack event")
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (hasXrFeature) androidx.compose.ui.graphics.Color.Transparent else androidx.compose.ui.graphics.Color.Black)
    ) {
        if (hasXrFeature) {
            // XR device detected - render spatial video content
            // LocalSession will be available inside the Subspace
            println("XR: Rendering SpatialVideoPlayerContent")
            SpatialVideoPlayerContent(
                videoPlayerViewModel = videoPlayerViewModel,
                playerState = playerState,
                onNavigateBack = onNavigateBack
            )
        } else {
            // Non-XR device - show error message
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "XR Mode Required",
                        style = MaterialTheme.typography.headlineMedium,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "This app requires an XR-capable device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SpatialVideoPlayerContent(
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: com.example.myapplication.ui.viewmodel.VideoPlayerState,
    onNavigateBack: () -> Unit
) {
    println("XR: SpatialVideoPlayerContent composed. Format: ${playerState.videoFormat}")
    val exoPlayer by videoPlayerViewModel.playerFlow.collectAsState()
    val showControls = playerState.showControls
    var recenterTrigger by remember { mutableIntStateOf(0) }
    val session = LocalSession.current

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            kotlinx.coroutines.delay(10000)
            videoPlayerViewModel.setControlsVisibility(false)
        }
    }



    Subspace {
        SpatialBox(modifier = SubspaceModifier.fillMaxSize()) {
            // For 2D mode: Use SpatialPanel with Standard2DPlayer to make it movable
            if (playerState.videoFormat == com.example.myapplication.ui.viewmodel.VideoFormat.Format2D) {
                // Render 2D video using standard Android PlayerView inside a SpatialPanel
                // This allows the user to move/drag the screen
                SpatialPanel(
                    modifier = SubspaceModifier
                        .width(1280.dp)
                        .height(720.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Standard2DPlayer(
                            exoPlayer = exoPlayer,
                            onToggleControls = videoPlayerViewModel::toggleControls
                        )

                        // Overlay controls if visible
                        if (showControls && !playerState.isLoading && playerState.error == null) {
                            XRPlaybackControls(
                                videoPlayerViewModel = videoPlayerViewModel,
                                playerState = playerState,
                                onNavigateBack = onNavigateBack
                            )
                        }
                    }
                }
            } else {
                // For immersive modes (180/360), use SpatialBox as it shouldn't be movable
                androidx.xr.compose.subspace.SpatialColumn(
                    modifier = SubspaceModifier.fillMaxSize(),
                    alignment = SpatialAlignment.Center
                ) {
                    SpatialBox(
                        modifier = SubspaceModifier
                            .width(1280.dp)
                            .height(720.dp)
                    ) {
                        // Video player logic based on format
                        SpatialVideoPlayer(
                            videoFormat = playerState.videoFormat,
                            stereoMode = playerState.stereoMode,
                            zoomLevel = playerState.zoomLevel,
                            exoPlayer = exoPlayer,
                            onToggleControls = videoPlayerViewModel::toggleControls,
                            recenterTrigger = recenterTrigger,

                            modifier = SubspaceModifier.fillMaxSize()
                        )
                    }
                }
            }

            // Controls Panel positioned at center (For 3D modes only)
            // Placed after video to ensure it's on top (Z-order)
            // Added Z-offset to bring it closer to the user and ensure it captures clicks
            if (playerState.videoFormat != com.example.myapplication.ui.viewmodel.VideoFormat.Format2D &&
                showControls && !playerState.isLoading && playerState.error == null) {
                androidx.xr.compose.subspace.SpatialColumn(
                    modifier = SubspaceModifier.fillMaxSize(),
                    alignment = SpatialAlignment.Center
                ) {
                    SpatialPanel(
                        modifier = SubspaceModifier
                            .width(600.dp)
                            .height(640.dp)
                            .offset(z = 100.dp) // Bring controls forward
                    ) {
                        // XRPlaybackControls has its own background
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.0f)),
                            contentAlignment = Alignment.BottomCenter
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
        }
    }
}

@Composable
fun SpatialVideoPlayer(
    videoFormat: com.example.myapplication.ui.viewmodel.VideoFormat,
    stereoMode: com.example.myapplication.ui.viewmodel.StereoMode,
    zoomLevel: Float,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    onToggleControls: () -> Unit,
    recenterTrigger: Int = 0,

    modifier: SubspaceModifier = SubspaceModifier
) {
    if (videoFormat == com.example.myapplication.ui.viewmodel.VideoFormat.Format2D) {
        // 2D Flat Mode (Orbiter)
        VideoPlayerOrbiter(
            isStereo = stereoMode != com.example.myapplication.ui.viewmodel.StereoMode.Mono,
            zoomLevel = zoomLevel,
            exoPlayer = exoPlayer,
            onToggleControls = onToggleControls,
            modifier = modifier
        )
    } else {
        // Immersive Mode (180/360)
        val session = LocalSession.current
        if (session != null) {
            // Wrap in SpatialBox to add click overlay
            SpatialBox(modifier = modifier.fillMaxSize()) {
                ImmersiveVideoPlayer(
                    session = session,
                    videoFormat = videoFormat,
                    stereoMode = stereoMode,
                    zoomLevel = zoomLevel,
                    exoPlayer = exoPlayer,
                    recenterTrigger = recenterTrigger,

                )
            }
        } else {
            // Fallback if no session (shouldn't happen in XR)
            Orbiter(
                position = ContentEdge.Bottom,
                offset = 0.dp
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "No XR Session Available",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPlayerOrbiter(
    isStereo: Boolean,
    zoomLevel: Float,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    onToggleControls: () -> Unit,
    modifier: SubspaceModifier = SubspaceModifier
) {
    println("XR: VideoPlayerOrbiter composed. Player: ${exoPlayer?.hashCode()}")

    // Wrap in SpatialBox to allow layering the click overlay on top of the video
    SpatialBox(modifier = modifier.scale(zoomLevel)) {
        // 1. The Video Surface (Layer 0)
        if (exoPlayer != null) {
            SpatialExternalSurface(
                stereoMode = if (isStereo) StereoMode.SideBySide else StereoMode.Mono,
                modifier = SubspaceModifier.fillMaxSize()
            ) {
                onSurfaceCreated { surface ->
                    println("XR: SpatialExternalSurface created, attaching to ExoPlayer")
                    exoPlayer.setVideoSurface(surface)
                }
                onSurfaceDestroyed {
                    println("XR: SpatialExternalSurface destroyed, clearing ExoPlayer surface")
                    exoPlayer.clearVideoSurface()
                }
            }
        } else {
            println("XR: Waiting for ExoPlayer to be ready...")
        }

        // 2. Click Overlay (Layer 1)
        // Transparent panel to capture clicks
        // Use a very low alpha instead of Transparent to ensure hit-testing works
        SpatialPanel(modifier = SubspaceModifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable {
                        println("XR: Video overlay clicked! Toggling controls.")
                        onToggleControls()
                    }
            )
        }
    }
}

@Composable
fun ImmersiveVideoPlayer(
    session: Session,
    videoFormat: com.example.myapplication.ui.viewmodel.VideoFormat,
    stereoMode: com.example.myapplication.ui.viewmodel.StereoMode,
    zoomLevel: Float,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    recenterTrigger: Int,

) {

    val context = LocalContext.current
    DisposableEffect(videoFormat, stereoMode, exoPlayer, recenterTrigger) {
        println("XR: Creating Immersive SurfaceEntity for $videoFormat / $stereoMode (recenter: $recenterTrigger)")

        var entity: SurfaceEntity? = null

        try {
            val shape = when (videoFormat) {
                com.example.myapplication.ui.viewmodel.VideoFormat.Format180 -> SurfaceEntity.Shape.Hemisphere(
                    1.0f * zoomLevel
                )

                com.example.myapplication.ui.viewmodel.VideoFormat.Format360 -> SurfaceEntity.Shape.Sphere(
                    1.0f * zoomLevel
                )

                else -> SurfaceEntity.Shape.Quad(FloatSize2d(1.5f * zoomLevel, 1.5f * zoomLevel))
            }

            val xrStereoMode = when (stereoMode) {
                com.example.myapplication.ui.viewmodel.StereoMode.SideBySide -> SurfaceEntity.StereoMode.STEREO_MODE_SIDE_BY_SIDE
                com.example.myapplication.ui.viewmodel.StereoMode.TopBottom -> SurfaceEntity.StereoMode.STEREO_MODE_TOP_BOTTOM
                else -> SurfaceEntity.StereoMode.STEREO_MODE_MONO
            }

            val newConfig = session.config.copy(
                headTracking = Config.HeadTrackingMode.LAST_KNOWN,

                )

            session.configure(newConfig)

            // Position sphere/hemisphere centered on user's current head position
            // Use head position for translation, but keep rotation identity so video faces forward
            // TODO: Fix head pose retrieval. Currently defaulting to session origin (0,0,0).
            val headPosition = Vector3(0f, 0f, 0f)

            // Create pose: centered at head position, facing forward (identity rotation)
            val entityPose = Pose(
                translation = headPosition,
                rotation = androidx.xr.runtime.math.Quaternion.Identity  // Face forward
            )

            entity = SurfaceEntity.create(
                session = session,
                shape = shape,
                stereoMode = xrStereoMode,
                pose = session.scene.spatialUser.head?.transformPoseTo(
                    Pose.Identity,
                    session.scene.activitySpace
                )!!,
            )

            println("XR: SurfaceEntity created at position: $headPosition, facing forward. Attaching to player.")
            exoPlayer?.setVideoSurface(entity.getSurface())



        } catch (e: Exception) {
            println("XR: Error creating immersive surface: ${e.message}")
            e.printStackTrace()
        }

        onDispose {
            println("XR: Disposing SurfaceEntity")
            exoPlayer?.clearVideoSurface()
            entity?.dispose()
        }
    }

    // Recenter button removed - moved to playback controls

}

@OptIn(UnstableApi::class)
@Composable
fun Standard2DPlayer(
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    onToggleControls: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (exoPlayer != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Click overlay to toggle controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggleControls() }
        )
    }
}
