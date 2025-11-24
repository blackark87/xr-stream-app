package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
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
import androidx.xr.compose.subspace.SpatialExternalSurface
import androidx.xr.compose.subspace.StereoMode
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.subspace.layout.fillMaxSize
import com.example.myapplication.data.database.AppDatabase
import com.example.myapplication.data.repository.VideoRepository
import com.example.myapplication.ui.components.XRPlaybackControls
import com.example.myapplication.ui.viewmodel.VideoPlayerViewModel
import com.example.myapplication.ui.viewmodel.VideoPlayerViewModelFactory
import androidx.xr.compose.subspace.SpatialBox
import androidx.xr.compose.subspace.layout.SpatialAlignment

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
    LaunchedEffect(showControls, playerState.isPlaying) {
        if (showControls && playerState.isPlaying) {
            kotlinx.coroutines.delay(5000)
            showControls = false
        }
    }

// ...

    Subspace {
        // Use SpatialBox to layer content (Video + Controls)
        // Set size to 16:9 aspect ratio (e.g., 1280.dp x 720.dp)
        // Set alignment to BottomCenter so controls appear at the bottom
        SpatialBox(
            modifier = SubspaceModifier
                .width(1280.dp)
                .height(720.dp),
            alignment = SpatialAlignment.BottomCenter
        ) {
            // Video player logic based on format
            SpatialVideoPlayer(
                videoFormat = playerState.videoFormat,
                stereoMode = playerState.stereoMode,
                exoPlayer = exoPlayer,
                modifier = SubspaceModifier.fillMaxSize()
            )

            // Controls panel at bottom (Overlay)
            if (showControls && !playerState.isLoading && playerState.error == null) {
                // Using SpatialPanel as an overlay for controls
                // We remove fixed height to allow expansion for menus (like Format)
                SpatialPanel(
                    modifier = SubspaceModifier
                        .width(800.dp)
                        //.height(400.dp) // Allow dynamic height
                ) {
                    Surface(
                        color = androidx.compose.ui.graphics.Color.Transparent, 
                        modifier = Modifier.fillMaxSize()
                    ) {
                        XRPlaybackControls(
                            videoPlayerViewModel = videoPlayerViewModel,
                            playerState = playerState,
                            onNavigateBack = onNavigateBack,
                            onToggleControls = { showControls = !showControls }
                        )
                    }
                }
            } else if (!showControls && !playerState.isLoading && playerState.error == null) {
                // Small button to show controls again
                // We use SpatialPanel for this too to keep it within the SpatialBox layout
                // Or we can keep using Orbiter if we want it floating independent of the box?
                // User said "try under layer orbital" which implies they want it positioned better.
                // If we put it in the SpatialBox with BottomCenter alignment, it will sit at the bottom of the 16:9 frame.
                SpatialPanel(
                     modifier = SubspaceModifier
                        .width(200.dp)
                        .height(60.dp)
                ) {
                    Surface(
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Button(
                                onClick = { showControls = true }
                            ) {
                                Text("Show Controls")
                            }
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
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    modifier: androidx.xr.compose.subspace.layout.SubspaceModifier = androidx.xr.compose.subspace.layout.SubspaceModifier
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
    modifier: androidx.xr.compose.subspace.layout.SubspaceModifier = androidx.xr.compose.subspace.layout.SubspaceModifier
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
    session: Any, // Using Any to bypass unresolved reference for now
    videoFormat: com.example.myapplication.ui.viewmodel.VideoFormat,
    stereoMode: com.example.myapplication.ui.viewmodel.StereoMode,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?
) {
    DisposableEffect(videoFormat, stereoMode, exoPlayer) {
        println("XR: Creating Immersive SurfaceEntity for $videoFormat / $stereoMode using session: ${session::class.simpleName}")

        try {
            // Define Shape
            // Note: CanvasShape.Vr180Hemisphere / Vr360Sphere might be the API
            // If these are not available, we might need to use a different shape or API

            // Placeholder for actual SurfaceEntity creation
            // Since we don't have the exact API signature confirmed and previous attempts failed,
            // we will log this. In a real implementation, we would call SurfaceEntity.create here.

            // TODO: Implement SurfaceEntity creation when API is confirmed public
            // val entity = SurfaceEntity.create(...)
            // exoPlayer?.setVideoSurface(entity.surface)

            println("XR: Immersive mode requested but SurfaceEntity API is restricted. Falling back to log.")

        } catch (e: Exception) {
            println("XR: Error creating immersive surface: ${e.message}")
        }

        onDispose {
            // Cleanup
            // entity.dispose()
            exoPlayer?.clearVideoSurface()
        }
    }

    // Show a message to the user that immersive mode is experimental/WIP
    Orbiter(
        position = ContentEdge.Bottom,
        offset = 300.dp,
        alignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Immersive Mode (${videoFormat.name}) Selected\n(Rendering implementation pending API access)",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
