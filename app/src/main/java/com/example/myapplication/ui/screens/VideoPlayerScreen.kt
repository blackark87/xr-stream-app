package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialBox
import androidx.xr.compose.subspace.SpatialExternalSurface
import androidx.xr.compose.subspace.StereoMode
import androidx.xr.compose.subspace.layout.SpatialAlignment
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.fillMaxSize
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.width
import androidx.xr.runtime.Config
import androidx.xr.runtime.Session
import androidx.xr.scenecore.SurfaceEntity
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.scene
import com.example.myapplication.data.database.AppDatabase
import com.example.myapplication.data.repository.VideoRepository
import com.example.myapplication.ui.components.XRPlaybackControls
import com.example.myapplication.ui.viewmodel.VideoPlayerViewModel
import com.example.myapplication.ui.viewmodel.VideoPlayerViewModelFactory

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

    DisposableEffect(Unit) {
        onDispose {
            videoPlayerViewModel.releasePlayer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
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

@Composable
fun SpatialVideoPlayerContent(
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: com.example.myapplication.ui.viewmodel.VideoPlayerState,
    onNavigateBack: () -> Unit
) {
    println("XR: SpatialVideoPlayerContent composed. Format: ${playerState.videoFormat}")
    val exoPlayer by videoPlayerViewModel.playerFlow.collectAsState()
    var showControls by remember { mutableStateOf(true) }  // Start with controls visible

    // Auto-hide controls after 5 seconds
    // Auto-hide controls logic removed as per user request
    // Controls will remain visible until explicitly hidden


// ...

    Subspace {
        // Use SpatialColumn to stack Video
        androidx.xr.compose.subspace.SpatialColumn(
            modifier = SubspaceModifier.fillMaxSize(),
            alignment = SpatialAlignment.Center
        ) {
            // Video Player Container (16:9)
            SpatialBox(
                modifier = SubspaceModifier
                    .width(1280.dp)
                    .height(720.dp)
            ) {
                // Video player logic based on format
                SpatialVideoPlayer(
                    videoFormat = playerState.videoFormat,
                    stereoMode = playerState.stereoMode,
                    exoPlayer = exoPlayer,
                    modifier = SubspaceModifier.fillMaxSize()
                )
            }
        }

        // Controls Orbiter
        if (showControls && !playerState.isLoading && playerState.error == null) {
            Orbiter(
                position = ContentEdge.Bottom,
                offset = 24.dp,
                alignment = Alignment.CenterHorizontally
            ) {
                // Controls panel
                Surface(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.width(600.dp) // Fixed width for controls
                ) {
                    XRPlaybackControls(
                        videoPlayerViewModel = videoPlayerViewModel,
                        playerState = playerState,
                        onNavigateBack = onNavigateBack,
                        onToggleControls = { showControls = !showControls }
                    )
                }
            }
        }

        // Show Controls Button (Orbiter) - Visible when controls are hidden
        if (!showControls && !playerState.isLoading && playerState.error == null) {
            Orbiter(
                position = ContentEdge.Bottom,
                offset = 24.dp,
                alignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { showControls = true },
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
                ) {
                    Text("Show Controls")
                }
            }
        }

    }
}

@Composable
fun SpatialVideoPlayer(
    videoFormat: com.example.myapplication.ui.viewmodel.VideoFormat,
    stereoMode: com.example.myapplication.ui.viewmodel.StereoMode,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    modifier: SubspaceModifier = SubspaceModifier
) {
    if (videoFormat == com.example.myapplication.ui.viewmodel.VideoFormat.Format2D) {
        // 2D Flat Mode (Orbiter)
        VideoPlayerOrbiter(
            isStereo = stereoMode != com.example.myapplication.ui.viewmodel.StereoMode.Mono,
            exoPlayer = exoPlayer,
            modifier = modifier
        )
    } else {
        // Immersive Mode (180/360)
        val session = LocalSession.current
        if (session != null) {
            ImmersiveVideoPlayer(
                session = session,
                videoFormat = videoFormat,
                stereoMode = stereoMode,
                exoPlayer = exoPlayer
            )
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
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    modifier: SubspaceModifier = SubspaceModifier
) {
    println("XR: VideoPlayerSurface composed. isStereo: $isStereo, Player: ${exoPlayer?.hashCode()}")

    // Only create surface when player is ready
    if (exoPlayer != null) {
        // Use SpatialExternalSurface - the official XR API for video rendering
        SpatialExternalSurface(
            stereoMode = if (isStereo) StereoMode.SideBySide else StereoMode.Mono,
            modifier = modifier
        ) {
            // onSurfaceCreated is called when the Surface is ready
            onSurfaceCreated { surface ->
                println("XR: SpatialExternalSurface created, attaching to ExoPlayer")
                exoPlayer.setVideoSurface(surface)
            }

            // onSurfaceDestroyed is called when the Surface is destroyed
            onSurfaceDestroyed {
                println("XR: SpatialExternalSurface destroyed, clearing ExoPlayer surface")
                exoPlayer.clearVideoSurface()
            }
        }
    } else {
        println("XR: Waiting for ExoPlayer to be ready...")
    }
}

@Composable
fun ImmersiveVideoPlayer(
    session: Session,
    videoFormat: com.example.myapplication.ui.viewmodel.VideoFormat,
    stereoMode: com.example.myapplication.ui.viewmodel.StereoMode,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?
) {
    DisposableEffect(videoFormat, stereoMode, exoPlayer) {
        println("XR: Creating Immersive SurfaceEntity for $videoFormat / $stereoMode")

        var entity: SurfaceEntity? = null

        try {
            val shape = when (videoFormat) {
                com.example.myapplication.ui.viewmodel.VideoFormat.Format180 -> SurfaceEntity.Shape.Hemisphere(1.0f)
                com.example.myapplication.ui.viewmodel.VideoFormat.Format360 -> SurfaceEntity.Shape.Sphere(1.0f)
                else -> SurfaceEntity.Shape.Quad(FloatSize2d(1.5f, 1.5f))
            }

            val xrStereoMode = when (stereoMode) {
                com.example.myapplication.ui.viewmodel.StereoMode.SideBySide -> SurfaceEntity.StereoMode.STEREO_MODE_SIDE_BY_SIDE
                com.example.myapplication.ui.viewmodel.StereoMode.TopBottom -> SurfaceEntity.StereoMode.STEREO_MODE_TOP_BOTTOM
                else -> SurfaceEntity.StereoMode.STEREO_MODE_MONO
            }

            val newConfig = session.config.copy(
                headTracking = Config.HeadTrackingMode.LAST_KNOWN,
            )

            session.configure(newConfig);

            // Create the SurfaceEntity
            // We use Identity pose in ActivitySpace for now. 
            // Ideally, we might want to center it on the user's head, but Identity is a safe start.
            entity = SurfaceEntity.create(
                session = session,
                shape = shape,
                stereoMode = xrStereoMode,
                pose = session.scene.spatialUser.head?.transformPoseTo(
                    Pose.Identity,
                    session.scene.activitySpace
                )!!,
            )

            println("XR: SurfaceEntity created successfully. Attaching to player.")
            exoPlayer?.setVideoSurface(entity.getSurface())

        } catch (e: Exception) {
            println("XR: Error creating immersive surface: ${e.message}")
            e.printStackTrace()
        }

        onDispose {
            println("XR: Disposing SurfaceEntity")
            entity?.dispose()
            exoPlayer?.clearVideoSurface()
        }
    }

    // Informational text (Orbiter)
    Orbiter(
        position = ContentEdge.Bottom,
        offset = 300.dp,
        alignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            shape = MaterialTheme.shapes.medium
        ) {
        }
    }
}
