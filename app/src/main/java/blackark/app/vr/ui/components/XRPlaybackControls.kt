package blackark.app.vr.ui.components

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import blackark.app.vr.ui.viewmodel.PlaybackMenu
import blackark.app.vr.ui.viewmodel.PlaybackPreviewMode
import blackark.app.vr.ui.viewmodel.StereoMode
import blackark.app.vr.ui.viewmodel.VideoFormat
import blackark.app.vr.ui.viewmodel.VideoPlayerState
import blackark.app.vr.ui.viewmodel.VideoPlayerViewModel
import blackark.app.vr.utils.ThumbnailImageLoaderProvider
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun XRPlaybackControls(
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: VideoPlayerState,
    onNavigateBack: () -> Unit,
) {
    // Scrubbing state.
    var isScrubbing by remember { mutableStateOf(false) }
    var isVolumeScrubbing by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var controlsRootLeftPx by remember { mutableFloatStateOf(0f) }
    var controlsRootTopPx by remember { mutableFloatStateOf(0f) }
    var progressSectionLeftPx by remember { mutableIntStateOf(0) }
    var progressSectionTopPx by remember { mutableIntStateOf(0) }
    var progressSectionWidthPx by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val density = LocalDensity.current

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

    DisposableEffect(Unit) {
        onDispose {
            if (isScrubbing) {
                videoPlayerViewModel.endControlsInputLock()
            }
            if (isVolumeScrubbing) {
                videoPlayerViewModel.endControlsInputLock()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 20.dp)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                controlsRootLeftPx = position.x
                controlsRootTopPx = position.y
            },
    ) {
        val previewCardWidthPx = with(density) { SeekPreviewOverlayWidth.roundToPx() }
        val previewCardHeightPx = with(density) { SeekPreviewOverlayEstimatedHeight.roundToPx() }
        val previewCardGapPx = with(density) { SeekPreviewOverlayGap.roundToPx() }
        val previewTrackInsetPx = with(density) { 14.dp.roundToPx() }
        val previewFraction =
            if (playerState.duration > 0L) {
                (playerState.seekPreviewTargetPositionMs.toFloat() / playerState.duration.toFloat())
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
        val previewTrackWidthPx =
            (progressSectionWidthPx - (previewTrackInsetPx * 2)).coerceAtLeast(0)
        val previewThumbCenterPx =
            progressSectionLeftPx + previewTrackInsetPx +
                (previewTrackWidthPx * previewFraction).roundToInt()
        val previewMinX = progressSectionLeftPx
        val previewMaxX = (progressSectionLeftPx + progressSectionWidthPx - previewCardWidthPx)
            .coerceAtLeast(previewMinX)
        val previewOffsetX =
            (previewThumbCenterPx - (previewCardWidthPx / 2)).coerceIn(previewMinX, previewMaxX)
        val previewOffsetY =
            (progressSectionTopPx - previewCardHeightPx - previewCardGapPx).coerceAtLeast(0)

        if (
            playerState.seekPreviewActive &&
            playerState.seekPreviewMode == PlaybackPreviewMode.ThumbnailOverlay
        ) {
            PlaybackSeekPreviewCard(
                targetPositionMs = playerState.seekPreviewTargetPositionMs,
                previewPath = playerState.seekPreviewThumbnailPath,
                containerColor = sectionSurface,
                borderColor = sectionBorder,
                textColor = textStrong,
                secondaryTextColor = textMuted,
                context = context,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(1f)
                    .offset {
                        IntOffset(previewOffsetX, previewOffsetY)
                    },
            )
        }

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
                    onClick = {
                        onNavigateBack()
                    },
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
                            "${playerState.videoFormat.name.removePrefix("Format")} | ${
                                prettySpeed(
                                    playerState.playbackSpeed
                                )
                            }x | ${(playerState.volume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(46.dp))
            }

            // Progress bar.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInRoot()
                        progressSectionLeftPx = (position.x - controlsRootLeftPx).roundToInt()
                        progressSectionTopPx = (position.y - controlsRootTopPx).roundToInt()
                        progressSectionWidthPx = coordinates.size.width
                    },
            ) {
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
                            if (!isScrubbing) {
                                videoPlayerViewModel.beginControlsInputLock()
                                videoPlayerViewModel.beginSeekPreview()
                            }
                            isScrubbing = true
                            sliderPosition = newProgress
                            val newPosition = (newProgress * playerState.duration).toLong()
                            videoPlayerViewModel.updateSeekPreviewPosition(newPosition)
                        },
                        onValueChangeFinished = {
                            val newPosition = (sliderPosition * playerState.duration).toLong()
                            videoPlayerViewModel.commitSeekPreview(newPosition)
                            isScrubbing = false
                            videoPlayerViewModel.endControlsInputLock()
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
                    onClick = {
                        videoPlayerViewModel.playPreviousVideo()
                    },
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous Video",
                    containerColor = sectionSurface,
                    borderColor = sectionBorder,
                    contentColor = textStrong,
                )

                Spacer(modifier = Modifier.width(12.dp))

                PlaybackIconControlButton(
                    onClick = {
                        videoPlayerViewModel.skipBackward()
                    },
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
                    onClick = {
                        videoPlayerViewModel.skipForward()
                    },
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = "Fast Forward",
                    containerColor = sectionSurface,
                    borderColor = sectionBorder,
                    contentColor = textStrong,
                )

                Spacer(modifier = Modifier.width(12.dp))

                PlaybackIconControlButton(
                    onClick = {
                        videoPlayerViewModel.playNextVideo()
                    },
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
                    selected = playerState.activePlaybackMenu == PlaybackMenu.Speed,
                    onClick = {
                        videoPlayerViewModel.togglePlaybackMenu(PlaybackMenu.Speed)
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
                    selected = playerState.activePlaybackMenu == PlaybackMenu.Display,
                    onClick = {
                        videoPlayerViewModel.togglePlaybackMenu(PlaybackMenu.Display)
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
                    selected = playerState.activePlaybackMenu == PlaybackMenu.Volume,
                    onClick = {
                        videoPlayerViewModel.togglePlaybackMenu(PlaybackMenu.Volume)
                    },
                    modifier = Modifier.weight(1f),
                    activeContainerColor = chipActive,
                    inactiveContainerColor = chipIdle,
                    borderColor = sectionBorder,
                    labelColor = textMuted,
                    valueColor = textStrong,
                )
            }

            if (playerState.activePlaybackMenu == PlaybackMenu.Speed) {
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
                                    videoPlayerViewModel.dismissPlaybackMenu()
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

            if (playerState.activePlaybackMenu == PlaybackMenu.Display) {
                PlaybackMenuCard(
                    title = "Video Format",
                    subtitle =
                        if (playerState.videoFormat == VideoFormat.Format2D) {
                            "Switch the projection"
                        } else {
                            "Switch the projection and stereo mode"
                        },
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
                                onClick = {
                                    videoPlayerViewModel.setVideoFormat(format)
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

                    if (playerState.videoFormat != VideoFormat.Format2D) {
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
                                    onClick = {
                                        videoPlayerViewModel.setStereoMode(mode)
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
            }

            if (playerState.activePlaybackMenu == PlaybackMenu.Volume) {
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
                        onValueChange = {
                            if (!isVolumeScrubbing) {
                                videoPlayerViewModel.beginControlsInputLock()
                            }
                            isVolumeScrubbing = true
                            videoPlayerViewModel.setVolume(it)
                        },
                        onValueChangeFinished = {
                            isVolumeScrubbing = false
                            videoPlayerViewModel.endControlsInputLock()
                        },
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
private fun PlaybackSeekPreviewCard(
    targetPositionMs: Long,
    previewPath: String?,
    containerColor: Color,
    borderColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    val imageLoader = remember(context) { ThumbnailImageLoaderProvider.get(context) }
    val previewRequest = remember(previewPath, targetPositionMs, context) {
        previewPath?.let { path ->
            ImageRequest.Builder(context)
                .data(path)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
    }

    Column(
        modifier = modifier
            .width(SeekPreviewOverlayWidth)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(14.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SeekPreviewOverlayThumbnailHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.36f)),
            contentAlignment = Alignment.Center,
        ) {
            if (previewRequest != null) {
                AsyncImage(
                    model = previewRequest,
                    imageLoader = imageLoader,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = "Preview",
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text(
            text = formatTime(targetPositionMs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.96f),
        )
    }
}

private val SeekPreviewOverlayWidth = 228.dp
private val SeekPreviewOverlayThumbnailHeight = 128.dp
private val SeekPreviewOverlayEstimatedHeight = 184.dp
private val SeekPreviewOverlayGap = 12.dp

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
