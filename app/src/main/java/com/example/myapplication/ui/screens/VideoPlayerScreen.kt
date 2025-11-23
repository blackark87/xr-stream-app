package com.example.myapplication.ui.screens

import android.content.pm.PackageManager
import android.view.SurfaceView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.subspace.layout.height
import com.example.myapplication.data.database.AppDatabase
import com.example.myapplication.data.repository.VideoRepository
import com.example.myapplication.ui.components.XRPlaybackControls
import com.example.myapplication.ui.viewmodel.FileBrowserViewModel
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

    // Only access XR composables if feature is available
    val session = if (hasXrFeature) LocalSession.current else null
    val spatialCapabilities = if (hasXrFeature) LocalSpatialCapabilities.current else null

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

    if (hasXrFeature && spatialCapabilities?.isSpatialUiEnabled == true && session != null) {
        // XR Immersive Mode - Side-by-side stereo video
        SpatialVideoPlayerContent(
            videoPlayerViewModel = videoPlayerViewModel,
            playerState = playerState,
            onNavigateBack = onNavigateBack
        )
    } else {
        // Fallback error message
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
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    var showControls by remember { mutableStateOf(true) }

    // Attach ExoPlayer surface when both are ready
    LaunchedEffect(surfaceView, exoPlayer) {
        val player = exoPlayer
        val surface = surfaceView
        if (player != null && surface != null) {
            println("XR: Attaching ExoPlayer to SurfaceView")
            player.setVideoSurfaceView(surface)
        }
    }

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showControls, playerState.isPlaying) {
        if (showControls && playerState.isPlaying) {
            kotlinx.coroutines.delay(5000)
            showControls = false
        }
    }

    Subspace {
        // Close button at top
        Orbiter(
            position = ContentEdge.Top,
            offset = 20.dp,
            alignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Text("✕", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        // Main video panel - Large immersive video surface for side-by-side stereo
        SpatialPanel(
            modifier = SubspaceModifier
                .width(2560.dp)  // Large width for immersive experience
                .height(1440.dp)  // 16:9 aspect ratio
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Video surface for side-by-side stereo playback
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).also {
                            surfaceView = it
                            println("XR: SurfaceView created")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Loading overlay
                if (playerState.isLoading) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading video...", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                // Error overlay
                if (playerState.error != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
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
