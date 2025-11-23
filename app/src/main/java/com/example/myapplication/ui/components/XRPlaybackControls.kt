package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.viewmodel.VideoPlayerState
import com.example.myapplication.ui.viewmodel.VideoPlayerViewModel
import kotlin.math.roundToInt

@Composable
fun XRPlaybackControls(
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: VideoPlayerState,
    onNavigateBack: () -> Unit,
    onToggleControls: () -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var showFormatMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top row - title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = playerState.videoFile?.name ?: "Video Player",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Progress bar
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = if (playerState.duration > 0) {
                    playerState.currentPosition.toFloat() / playerState.duration.toFloat()
                } else 0f,
                onValueChange = { progress ->
                    val newPosition = (progress * playerState.duration).toLong()
                    videoPlayerViewModel.seekTo(newPosition)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(playerState.currentPosition),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatTime(playerState.duration),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Main controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rewind button
            IconButton(onClick = { videoPlayerViewModel.skipBackward() }) {
                Text("⏪", style = MaterialTheme.typography.titleLarge)
            }

            // Play/Pause button
            IconButton(
                onClick = {
                    if (playerState.isPlaying) {
                        videoPlayerViewModel.pause()
                    } else {
                        videoPlayerViewModel.play()
                    }
                }
            ) {
                Text(
                    text = if (playerState.isPlaying) "⏸" else "▶",
                    style = MaterialTheme.typography.displaySmall
                )
            }

            // Fast forward button
            IconButton(onClick = { videoPlayerViewModel.skipForward() }) {
                Text("⏩", style = MaterialTheme.typography.titleLarge)
            }
        }

        // Bottom row - additional controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed control
            TextButton(onClick = { showSpeedMenu = !showSpeedMenu }) {
                Text("Speed: ${playerState.playbackSpeed}x")
            }

            // Volume control
            TextButton(onClick = { showVolumeSlider = !showVolumeSlider }) {
                Text("Volume: ${(playerState.volume * 100).roundToInt()}%")
            }
            
            // Format control (New)
            TextButton(onClick = { showFormatMenu = !showFormatMenu }) {
                Text("Format")
            }

            // Hide controls
            TextButton(onClick = onToggleControls) {
                Text("Hide")
            }
        }

        // Speed menu
        if (showSpeedMenu) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Playback Speed",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            Button(
                                onClick = {
                                    videoPlayerViewModel.setPlaybackSpeed(speed)
                                    showSpeedMenu = false
                                },
                                colors = if (playerState.playbackSpeed == speed) {
                                    ButtonDefaults.buttonColors()
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                }
                            ) {
                                Text("${speed}x")
                            }
                        }
                    }
                }
            }
        }
        
        // Format Menu (New)
        if (showFormatMenu) {
             Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Video Format",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Depth 1: Video Type
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.example.myapplication.ui.viewmodel.VideoFormat.values().forEach { format ->
                            Button(
                                onClick = { videoPlayerViewModel.setVideoFormat(format) },
                                colors = if (playerState.videoFormat == format) {
                                    ButtonDefaults.buttonColors()
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(format.name.replace("Format", ""))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Stereo Mode",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Depth 2: Stereo Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.example.myapplication.ui.viewmodel.StereoMode.values().forEach { mode ->
                            Button(
                                onClick = { videoPlayerViewModel.setStereoMode(mode) },
                                colors = if (playerState.stereoMode == mode) {
                                    ButtonDefaults.buttonColors()
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(mode.name)
                            }
                        }
                    }
                }
            }
        }

        // Volume slider
        if (showVolumeSlider) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Volume",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Slider(
                        value = playerState.volume,
                        onValueChange = { videoPlayerViewModel.setVolume(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

fun formatTime(milliseconds: Long): String {
    if (milliseconds < 0) return "00:00"

    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60

    return String.format("%02d:%02d", minutes, remainingSeconds)
}
