package com.example.myapplication.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.viewmodel.VideoPlayerState
import com.example.myapplication.ui.viewmodel.VideoPlayerViewModel
import kotlin.math.roundToInt

@Composable
fun XRPlaybackControls(
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: VideoPlayerState,
    onNavigateBack: () -> Unit
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var showFormatMenu by remember { mutableStateOf(false) }

    // Scrubbing state
    var isScrubbing by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    // Update slider position from player state only when NOT scrubbing
    LaunchedEffect(playerState.currentPosition, playerState.duration) {
        if (!isScrubbing && playerState.duration > 0) {
            sliderPosition = playerState.currentPosition.toFloat() / playerState.duration.toFloat()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 1f) // Semi-transparent overlay
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top row - title and back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, // Space between back button and title
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = playerState.videoFile?.name ?: "Video Player",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f), // Let title take available space
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center // Center the title
                )

                // Spacer to balance the row if needed, or just let title center
                Spacer(modifier = Modifier.width(48.dp)) // Balance the back button width
            }

            // Progress bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderPosition,
                    onValueChange = { newProgress ->
                        isScrubbing = true
                        sliderPosition = newProgress
                    },
                    onValueChangeFinished = {
                        val newPosition = (sliderPosition * playerState.duration).toLong()
                        videoPlayerViewModel.seekTo(newPosition)
                        isScrubbing = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(if (isScrubbing) (sliderPosition * playerState.duration).toLong() else playerState.currentPosition),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Text(
                        text = formatTime(playerState.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
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
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
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
                    Text("Speed: ${playerState.playbackSpeed}x", color = Color.White)
                }

                // Volume control
                TextButton(onClick = { showVolumeSlider = !showVolumeSlider }) {
                    Text("Volume: ${(playerState.volume * 100).roundToInt()}%", color = Color.White)
                }

                // Format control (New)
                TextButton(onClick = { showFormatMenu = !showFormatMenu }) {
                    Text("Format", color = Color.White)
                }

            }


            // Speed menu
            if (showSpeedMenu) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Playback Speed",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = Color.White
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
                                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    } else {
                                        ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Video Format",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = Color.White
                        )

                        // Depth 1: Video Type
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            com.example.myapplication.ui.viewmodel.VideoFormat.values()
                                .forEach { format ->
                                    Button(
                                        onClick = { videoPlayerViewModel.setVideoFormat(format) },
                                        colors = if (playerState.videoFormat == format) {
                                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        } else {
                                            ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
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
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = Color.White
                        )

                        // Depth 2: Stereo Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            com.example.myapplication.ui.viewmodel.StereoMode.values()
                                .forEach { mode ->
                                    Button(
                                        onClick = { videoPlayerViewModel.setStereoMode(mode) },
                                        colors = if (playerState.stereoMode == mode) {
                                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        } else {
                                            ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
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
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Volume",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = Color.White
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
}

fun formatTime(milliseconds: Long): String {
    if (milliseconds < 0) return "00:00"

    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60

    return String.format("%02d:%02d", minutes, remainingSeconds)
}
