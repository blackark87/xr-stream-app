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
        // Video player using Orbiter for spatial positioning
        // We extract this to a separate composable to avoid recomposition on every playerState change
        VideoPlayerOrbiter(
            isStereoVideo = playerState.isStereoVideo,
            exoPlayer = exoPlayer
        )

        // Top controls: Close button and Stereo toggle
        Orbiter(
            position = ContentEdge.Top,
            offset = 20.dp,
            alignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
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

                // Stereo mode toggle button
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (playerState.isStereoVideo) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { videoPlayerViewModel.toggleStereoMode() }) {
                            Text(
                                text = if (playerState.isStereoVideo) "3D" else "2D",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (playerState.isStereoVideo) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        Text(
                            text = when (playerState.userStereoOverride) {
                                true -> "Forced"
                                false -> "Disabled"
                                null -> "Auto"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (playerState.isStereoVideo) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
        
        // Log stereo mode for debugging
        LaunchedEffect(playerState.isStereoVideo) {
            if (playerState.isStereoVideo) {
                println("XR: Stereo/SBS video detected")
            } else {
                println("XR: Regular 2D video")
            }
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
fun VideoPlayerOrbiter(
    isStereoVideo: Boolean,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?
) {
    println("XR: Composing VideoPlayerOrbiter - Stereo: $isStereoVideo")
    
    // Main video display using Orbiter
    Orbiter(
        position = ContentEdge.Bottom,
        offset = 200.dp,
        alignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .width(if (isStereoVideo) 800.dp else 600.dp)
                .height(if (isStereoVideo) 450.dp else 338.dp),
            shape = MaterialTheme.shapes.medium,
            color = androidx.compose.ui.graphics.Color.Red, // DEBUG: Red background to check visibility
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
                        
                        surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                println("XR: TextureView Surface available: ${width}x${height}")
                            }
                            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                println("XR: TextureView Surface resized: ${width}x${height}")
                            }
                            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                                println("XR: TextureView Surface destroyed")
                                return true
                            }
                            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {
                                // Too noisy to log every frame
                            }
                        }
                    }
                },
                update = { textureView ->
                    if (exoPlayer != null) {
                        if (textureView.surfaceTexture != null) {
                             // Ensure we set the texture view only when available or just set it (ExoPlayer handles null checks)
                             println("XR: Setting ExoPlayer VideoTextureView. Video Format: ${exoPlayer.videoFormat}")
                             exoPlayer.setVideoTextureView(textureView)
                        } else {
                             println("XR: TextureView surfaceTexture is null during update")
                             exoPlayer.setVideoTextureView(textureView) // Set it anyway, ExoPlayer waits for surface
                        }
                    }
                },
                onRelease = { textureView ->
                    println("XR: Releasing TextureView")
                    exoPlayer?.clearVideoTextureView(textureView)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
