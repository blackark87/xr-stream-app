package com.example.myapplication.ui.components

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    val accentStrong = colors.primary
    val accentSoft = colors.secondary.copy(alpha = 0.24f)
    val onAccent = colors.onPrimary
    val textStrong = colors.onBackground
    val textMuted = colors.onBackground.copy(alpha = 0.72f)
    val panelTop = colors.background.copy(alpha = 0.98f)
    val panelBottom = colors.surface.copy(alpha = 0.98f)
    val sectionSurface = colors.surfaceVariant.copy(alpha = 0.94f)
    val sectionBorder = colors.primary.copy(alpha = 0.36f)
    val chipActive = colors.primary.copy(alpha = 0.16f)
    val chipIdle = colors.surfaceVariant.copy(alpha = 0.88f)

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
                        colors.scrim.copy(alpha = 0.56f),
                    ),
                ),
            )
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(panelTop, panelBottom),
                    ),
                )
                .border(1.dp, sectionBorder, RoundedCornerShape(30.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(sectionSurface)
                        .border(1.dp, sectionBorder, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textStrong,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = playerState.videoFile?.name ?: "Video Player",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = textStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            "${playerState.videoFormat.name.removePrefix("Format")} | ${prettySpeed(playerState.playbackSpeed)}x | ${(playerState.volume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(46.dp))
            }

            // Progress bar.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(sectionSurface)
                    .border(1.dp, sectionBorder.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
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
                        thumbColor = accentStrong,
                        activeTrackColor = accentStrong,
                        inactiveTrackColor = accentSoft,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
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
                        style = MaterialTheme.typography.labelLarge,
                        color = textStrong,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = formatTime(playerState.duration),
                        style = MaterialTheme.typography.labelLarge,
                        color = textMuted,
                    )
                }
            }

            // Main controls.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaybackIconControlButton(
                    onClick = { videoPlayerViewModel.playPreviousVideo() },
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous Video",
                    containerColor = sectionSurface,
                    borderColor = sectionBorder,
                    contentColor = textStrong,
                )

                Spacer(modifier = Modifier.width(12.dp))

                PlaybackIconControlButton(
                    onClick = { videoPlayerViewModel.skipBackward() },
                    imageVector = Icons.Filled.FastRewind,
                    contentDescription = "Rewind",
                    containerColor = sectionSurface,
                    borderColor = sectionBorder,
                    contentColor = textStrong,
                )

                Spacer(modifier = Modifier.width(18.dp))

                PlaybackIconControlButton(
                    onClick = {
                        if (playerState.isPlaying) {
                            videoPlayerViewModel.pause()
                        } else {
                            videoPlayerViewModel.play()
                        }
                    },
                    imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    containerColor = accentStrong,
                    borderColor = accentStrong.copy(alpha = 0.8f),
                    contentColor = onAccent,
                    buttonSize = 78.dp,
                    iconSize = 44.dp,
                )

                Spacer(modifier = Modifier.width(18.dp))

                PlaybackIconControlButton(
                    onClick = { videoPlayerViewModel.skipForward() },
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = "Fast Forward",
                    containerColor = sectionSurface,
                    borderColor = sectionBorder,
                    contentColor = textStrong,
                )

                Spacer(modifier = Modifier.width(12.dp))

                PlaybackIconControlButton(
                    onClick = { videoPlayerViewModel.playNextVideo() },
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next Video",
                    containerColor = sectionSurface,
                    borderColor = sectionBorder,
                    contentColor = textStrong,
                )
            }

            // Bottom row - always-visible secondary controls.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaybackInfoChip(
                    label = "Speed",
                    value = "${prettySpeed(playerState.playbackSpeed)}x",
                    selected = showSpeedMenu,
                    onClick = {
                        val nextState = !showSpeedMenu
                        showSpeedMenu = nextState
                        if (nextState) {
                            showVolumeSlider = false
                            showFormatMenu = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    activeContainerColor = chipActive,
                    inactiveContainerColor = chipIdle,
                    borderColor = sectionBorder,
                    labelColor = textMuted,
                    valueColor = textStrong,
                )

                PlaybackInfoChip(
                    label = "Format",
                    value = playerState.videoFormat.name.removePrefix("Format"),
                    selected = showFormatMenu,
                    onClick = {
                        val nextState = !showFormatMenu
                        showFormatMenu = nextState
                        if (nextState) {
                            showSpeedMenu = false
                            showVolumeSlider = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    activeContainerColor = chipActive,
                    inactiveContainerColor = chipIdle,
                    borderColor = sectionBorder,
                    labelColor = textMuted,
                    valueColor = textStrong,
                )

                PlaybackInfoChip(
                    label = "Volume",
                    value = "${(playerState.volume * 100).roundToInt()}%",
                    selected = showVolumeSlider,
                    onClick = {
                        val nextState = !showVolumeSlider
                        showVolumeSlider = nextState
                        if (nextState) {
                            showSpeedMenu = false
                            showFormatMenu = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    activeContainerColor = chipActive,
                    inactiveContainerColor = chipIdle,
                    borderColor = sectionBorder,
                    labelColor = textMuted,
                    valueColor = textStrong,
                )
            }

            if (showSpeedMenu) {
                PlaybackMenuCard(
                    title = "Playback Speed",
                    subtitle = "Adjust how fast the video moves",
                    containerColor = panelBottom,
                    borderColor = sectionBorder,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            PlaybackOptionButton(
                                text = "${prettySpeed(speed)}x",
                                selected = playerState.playbackSpeed == speed,
                                onClick = {
                                    videoPlayerViewModel.setPlaybackSpeed(speed)
                                    showSpeedMenu = false
                                },
                                modifier = Modifier.weight(1f),
                                selectedContainerColor = accentStrong,
                                selectedContentColor = onAccent,
                                idleContainerColor = chipIdle,
                                idleContentColor = textStrong,
                                borderColor = sectionBorder,
                            )
                        }
                    }
                }
            }

            if (showFormatMenu) {
                PlaybackMenuCard(
                    title = "Video Format",
                    subtitle = "Switch the projection and stereo mode",
                    containerColor = panelBottom,
                    borderColor = sectionBorder,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoFormat.entries.forEach { format ->
                            PlaybackOptionButton(
                                text = format.name.removePrefix("Format"),
                                selected = playerState.videoFormat == format,
                                onClick = { videoPlayerViewModel.setVideoFormat(format) },
                                modifier = Modifier.weight(1f),
                                selectedContainerColor = accentStrong,
                                selectedContentColor = onAccent,
                                idleContainerColor = chipIdle,
                                idleContentColor = textStrong,
                                borderColor = sectionBorder,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Stereo Mode",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = textMuted,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StereoMode.entries.forEach { mode ->
                            PlaybackOptionButton(
                                text = prettyStereoMode(mode),
                                selected = playerState.stereoMode == mode,
                                onClick = { videoPlayerViewModel.setStereoMode(mode) },
                                modifier = Modifier.weight(1f),
                                selectedContainerColor = accentStrong,
                                selectedContentColor = onAccent,
                                idleContainerColor = chipIdle,
                                idleContentColor = textStrong,
                                borderColor = sectionBorder,
                            )
                        }
                    }
                }
            }

            if (showVolumeSlider) {
                PlaybackMenuCard(
                    title = "Volume",
                    subtitle = "Output level for current playback",
                    containerColor = panelBottom,
                    borderColor = sectionBorder,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Current Level",
                            color = textMuted,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "${(playerState.volume * 100).roundToInt()}%",
                            color = textStrong,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = playerState.volume,
                        onValueChange = { videoPlayerViewModel.setVolume(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = accentStrong,
                            activeTrackColor = accentStrong,
                            inactiveTrackColor = accentSoft,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackIconControlButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 62.dp,
    iconSize: Dp = 34.dp,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(containerColor)
            .border(1.dp, borderColor, CircleShape),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = contentColor,
        )
    }
}

@Composable
private fun PlaybackInfoChip(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    activeContainerColor: Color,
    inactiveContainerColor: Color,
    borderColor: Color,
    labelColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 68.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) activeContainerColor else inactiveContainerColor,
            contentColor = valueColor,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaybackMenuCard(
    title: String,
    subtitle: String,
    containerColor: Color,
    borderColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        content()
    }
}

@Composable
private fun PlaybackOptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    idleContainerColor: Color,
    idleContentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 46.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = if (selected) {
            ButtonDefaults.buttonColors(
                containerColor = selectedContainerColor,
                contentColor = selectedContentColor,
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = idleContainerColor,
                contentColor = idleContentColor,
            )
        },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Suppress("MagicNumber")
private fun prettySpeed(speed: Float): String {
    val rounded = ((speed * 100).roundToInt() / 100f)
    val text = rounded.toString()
    return if (text.endsWith(".0")) text.dropLast(2) else text
}

private fun prettyStereoMode(mode: StereoMode): String = when (mode) {
    StereoMode.Mono -> "Mono"
    StereoMode.SideBySide -> "Side-by-side"
    StereoMode.TopBottom -> "Top-bottom"
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
