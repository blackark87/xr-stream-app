package com.example.myapplication.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.Subspace
import androidx.xr.scenecore.SurfaceEntity
import androidx.xr.runtime.math.Pose
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
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This app requires an XR-capable device",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateBack) {
                    Text("Go Back")
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
    var showControls by remember { mutableStateOf(false) }  // Start with controls hidden

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showControls, playerState.isPlaying) {
        if (showControls && playerState.isPlaying) {
            kotlinx.coroutines.delay(5000)
            showControls = false
        }
    }

    Subspace {
        // Video player logic based on format
        // We extract this to a separate composable to avoid recomposition on every playerState change
        SpatialVideoPlayer(
            videoFormat = playerState.videoFormat,
            stereoMode = playerState.stereoMode,
            exoPlayer = exoPlayer
        )

        // Top controls: Close button only (Stereo toggle moved to bottom menu)
        Orbiter(
            position = ContentEdge.Top,
            offset = 20.dp,
            alignment = Alignment.CenterHorizontally
        ) {
            // Close button
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Text("✕", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        
        // Log format for debugging
        LaunchedEffect(playerState.videoFormat, playerState.stereoMode) {
            println("XR: Format: ${playerState.videoFormat}, Stereo: ${playerState.stereoMode}")
        }

        // Loading overlay (centered in space via Orbiter)
        if (playerState.isLoading) {
            Orbiter(
                position = ContentEdge.Bottom,
                offset = 200.dp,
                alignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading...", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        // Error overlay (centered in space via Orbiter)
        if (playerState.error != null) {
            Orbiter(
                position = ContentEdge.Bottom,
                offset = 200.dp,
                alignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.width(400.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Playback Error",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = playerState.error!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = onNavigateBack) {
                                    Text("Back to Files")
                                }
                                val context = LocalContext.current
                                Button(
                                    onClick = {
                                        val smbConfig = com.example.myapplication.AppState.smbConfig
                                        if (smbConfig != null) {
                                            videoPlayerViewModel.retry(context, smbConfig)
                                        }
                                    }
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Controls panel at bottom
        if (showControls && !playerState.isLoading && playerState.error == null) {
            Orbiter(
                position = ContentEdge.Bottom,
                offset = 100.dp,
                alignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium
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
            Orbiter(
                position = ContentEdge.Bottom,
                offset = 50.dp,
                alignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { showControls = true }
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
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?
) {
    if (videoFormat == com.example.myapplication.ui.viewmodel.VideoFormat.Format2D) {
        // 2D Flat Mode (Orbiter)
        VideoPlayerOrbiter(
            isStereo = stereoMode != com.example.myapplication.ui.viewmodel.StereoMode.Mono,
            exoPlayer = exoPlayer
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
            Text("No XR Session")
        }
    }
}

@Composable
fun VideoPlayerOrbiter(
    isStereo: Boolean,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?
) {
    println("XR: VideoPlayerOrbiter composed. isStereo: $isStereo, Player: ${exoPlayer.hashCode()}")
    // Main video display using Orbiter
    Orbiter(
        position = ContentEdge.Bottom,
        offset = 200.dp,
        alignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .width(if (isStereo) 800.dp else 600.dp)
                .height(if (isStereo) 450.dp else 338.dp),
            shape = MaterialTheme.shapes.medium,
            color = androidx.compose.ui.graphics.Color.Black, 
            tonalElevation = 8.dp
        ) {
            // Display video using AndroidView with TextureView
            AndroidView(
                factory = { context ->
                    android.view.TextureView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        keepScreenOn = true
                    }
                },
                update = { textureView ->
                    if (exoPlayer != null) {
                        exoPlayer.setVideoTextureView(textureView)
                    }
                },
                onRelease = { textureView ->
                    exoPlayer?.clearVideoTextureView(textureView)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
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
