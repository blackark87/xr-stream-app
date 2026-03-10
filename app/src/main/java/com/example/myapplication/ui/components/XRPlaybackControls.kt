package com.example.myapplication.ui.components

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.myapplication.ui.viewmodel.StereoMode
import com.example.myapplication.ui.viewmodel.VideoFormat
import com.example.myapplication.ui.viewmodel.VideoPlayerState
import com.example.myapplication.ui.viewmodel.VideoPlayerViewModel
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun XRPlaybackControls(
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: VideoPlayerState,
    onNavigateBack: () -> Unit,
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var showFormatMenu by remember { mutableStateOf(false) }

    // Scrubbing state.
    var isScrubbing by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    val onAccent = colors.onPrimary
    val controlText = colors.onSurface
    val mutedText = colors.onSurface.copy(alpha = 0.9f)
    val controlSurface = colors.surface.copy(alpha = 0.96f)
    val controlSurfaceVariant = colors.surfaceVariant.copy(alpha = 0.78f)

    // Update slider position from player state only when NOT scrubbing.
    LaunchedEffect(playerState.currentPosition, playerState.duration) {
        if (!isScrubbing && playerState.duration > 0) {
            sliderPosition = playerState.currentPosition.toFloat() / playerState.duration.toFloat()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        colors.scrim.copy(alpha = 0.92f),
                    ),
                ),
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top row - title and back button.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(controlSurfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = controlText,
                    )
                }

                Text(
                    text = playerState.videoFile?.name ?: "Video Player",
                    style = MaterialTheme.typography.titleLarge,
                    color = controlText,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    maxLines = 1,
                )

                Spacer(modifier = Modifier.width(40.dp))
            }

            // Progress bar.
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
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = mutedText.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(
                            if (isScrubbing) {
                                (sliderPosition * playerState.duration).toLong()
                            } else {
                                playerState.currentPosition
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedText,
                    )
                    Text(
                        text = formatTime(playerState.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedText,
                    )
                }
            }

            // Main controls.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { videoPlayerViewModel.playPreviousVideo() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous Video",
                        modifier = Modifier.size(32.dp),
                        tint = controlText,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = { videoPlayerViewModel.skipBackward() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FastRewind,
                        contentDescription = "Rewind",
                        modifier = Modifier.size(32.dp),
                        tint = controlText,
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                IconButton(
                    onClick = {
                        if (playerState.isPlaying) {
                            videoPlayerViewModel.pause()
                        } else {
                            videoPlayerViewModel.play()
                        }
                    },
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp),
                        tint = controlText,
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                IconButton(
                    onClick = { videoPlayerViewModel.skipForward() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "Fast Forward",
                        modifier = Modifier.size(32.dp),
                        tint = controlText,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = { videoPlayerViewModel.playNextVideo() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next Video",
                        modifier = Modifier.size(32.dp),
                        tint = controlText,
                    )
                }
            }

            // Bottom row - additional controls.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Speed control.
                TextButton(
                    onClick = { showSpeedMenu = !showSpeedMenu },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (showSpeedMenu) controlSurfaceVariant else Color.Transparent),
                ) {
                    Text(
                        text = "Speed: ${playerState.playbackSpeed}x",
                        color = controlText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Volume control.
                TextButton(
                    onClick = { showVolumeSlider = !showVolumeSlider },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (showVolumeSlider) controlSurfaceVariant else Color.Transparent),
                ) {
                    Text(
                        text = "Volume: ${(playerState.volume * 100).roundToInt()}%",
                        color = controlText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Format control.
                TextButton(
                    onClick = { showFormatMenu = !showFormatMenu },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (showFormatMenu) controlSurfaceVariant else Color.Transparent),
                ) {
                    Text(
                        text = "Format",
                        color = controlText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Speed menu.
            if (showSpeedMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(controlSurface)
                        .padding(16.dp),
                ) {
                    Column {
                        Text(
                            text = "Playback Speed",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = controlText,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                Button(
                                    onClick = {
                                        videoPlayerViewModel.setPlaybackSpeed(speed)
                                        showSpeedMenu = false
                                    },
                                    colors = if (playerState.playbackSpeed == speed) {
                                        ButtonDefaults.buttonColors(
                                            containerColor = accent,
                                            contentColor = onAccent,
                                        )
                                    } else {
                                        ButtonDefaults.buttonColors(
                                            containerColor = controlSurfaceVariant,
                                            contentColor = controlText,
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("${speed}x")
                                }
                            }
                        }
                    }
                }
            }

            // Format menu.
            if (showFormatMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(controlSurface)
                        .padding(16.dp),
                ) {
                    Column {
                        Text(
                            text = "Video Format",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = controlText,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VideoFormat.entries.forEach { format ->
                                Button(
                                    onClick = { videoPlayerViewModel.setVideoFormat(format) },
                                    colors = if (playerState.videoFormat == format) {
                                        ButtonDefaults.buttonColors(
                                            containerColor = accent,
                                            contentColor = onAccent,
                                        )
                                    } else {
                                        ButtonDefaults.buttonColors(
                                            containerColor = controlSurfaceVariant,
                                            contentColor = controlText,
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(format.name.replace("Format", ""))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Stereo Mode",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = controlText,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StereoMode.entries.forEach { mode ->
                                Button(
                                    onClick = { videoPlayerViewModel.setStereoMode(mode) },
                                    colors = if (playerState.stereoMode == mode) {
                                        ButtonDefaults.buttonColors(
                                            containerColor = accent,
                                            contentColor = onAccent,
                                        )
                                    } else {
                                        ButtonDefaults.buttonColors(
                                            containerColor = controlSurfaceVariant,
                                            contentColor = controlText,
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(mode.name)
                                }
                            }
                        }
                    }
                }
            }

            // Volume slider.
            if (showVolumeSlider) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(controlSurface)
                        .padding(16.dp),
                ) {
                    Column {
                        Text(
                            text = "Volume",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = controlText,
                        )

                        Slider(
                            value = playerState.volume,
                            onValueChange = { videoPlayerViewModel.setVolume(it) },
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = mutedText.copy(alpha = 0.35f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
fun formatTime(milliseconds: Long): String {
    if (milliseconds < 0) return "00:00:00"

    val totalSeconds = (milliseconds / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
