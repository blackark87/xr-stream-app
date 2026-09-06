package blackark.app.vr.ui.components

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import blackark.app.vr.ui.viewmodel.PlaybackMenu
import blackark.app.vr.ui.viewmodel.StereoMode
import blackark.app.vr.ui.viewmodel.VideoFormat
import blackark.app.vr.ui.viewmodel.VideoPlayerState
import blackark.app.vr.ui.viewmodel.VideoPlayerViewModel
import blackark.app.vr.utils.IMMERSIVE_SUBTITLE_DISTANCE_SLIDER_STEPS
import blackark.app.vr.utils.IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_SLIDER_STEPS
import blackark.app.vr.utils.IMMERSIVE_UI_HORIZONTAL_OFFSET_SLIDER_STEPS
import blackark.app.vr.utils.MAX_IMMERSIVE_SUBTITLE_DISTANCE_METERS
import blackark.app.vr.utils.MAX_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS
import blackark.app.vr.utils.MAX_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS
import blackark.app.vr.utils.MIN_IMMERSIVE_SUBTITLE_DISTANCE_METERS
import blackark.app.vr.utils.MIN_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS
import blackark.app.vr.utils.MIN_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS
import blackark.app.vr.utils.SeekPreviewFrame
import kotlin.math.roundToInt

enum class XRPlaybackControlsContent {
    ControlsWithMenu,
    ControlsOnly,
    MenuOnly,
}

@OptIn(UnstableApi::class)
@Composable
fun XRPlaybackControls(
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: VideoPlayerState,
    onNavigateBack: () -> Unit,
    content: XRPlaybackControlsContent = XRPlaybackControlsContent.ControlsWithMenu,
    menuContentMaxHeight: Dp? = null,
    onPanelBackgroundClick: (() -> Unit)? = null,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var isVolumeScrubbing by remember { mutableStateOf(false) }
    var isFovScrubbing by remember { mutableStateOf(false) }
    var isSubtitleDepthScrubbing by remember { mutableStateOf(false) }
    var isSubtitleVerticalScrubbing by remember { mutableStateOf(false) }
    var isImmersiveUiHorizontalScrubbing by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var immersiveUiHorizontalSliderPosition by remember {
        mutableFloatStateOf(playerState.immersiveUiHorizontalOffsetMeters)
    }
    var controlsRootLeftPx by remember { mutableFloatStateOf(0f) }
    var controlsRootTopPx by remember { mutableFloatStateOf(0f) }
    var controlsRootWidthPx by remember { mutableIntStateOf(0) }
    var speedAnchorCenterXPx by remember { mutableIntStateOf(0) }
    var speedAnchorTopPx by remember { mutableIntStateOf(0) }
    var displayAnchorCenterXPx by remember { mutableIntStateOf(0) }
    var displayAnchorTopPx by remember { mutableIntStateOf(0) }
    var volumeAnchorCenterXPx by remember { mutableIntStateOf(0) }
    var volumeAnchorTopPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val accentStrong = colors.primary
    val accentSoft = colors.primary.copy(alpha = 0.18f)
    val onAccent = colors.onPrimary
    val textStrong = colors.onSurface
    val textMuted = colors.onSurface.copy(alpha = 0.72f)
    val panelTop = colors.background
    val panelBottom = colors.surface
    val sectionSurface = colors.surfaceVariant
    val sectionBorder = colors.outline.copy(alpha = 0.28f)
    val menuSurface = colors.surface
    val displayMenuSurface = colors.surface
    val chipActive = colors.primary.copy(alpha = 0.18f)
    val chipIdle = colors.surfaceVariant
    val controlsCardInteractionSource = remember { MutableInteractionSource() }
    val panelBackgroundInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(playerState.currentPosition, playerState.duration) {
        if (!isScrubbing && playerState.duration > 0) {
            sliderPosition = playerState.currentPosition.toFloat() / playerState.duration.toFloat()
        }
    }

    LaunchedEffect(playerState.immersiveUiHorizontalOffsetMeters) {
        if (!isImmersiveUiHorizontalScrubbing) {
            immersiveUiHorizontalSliderPosition =
                playerState.immersiveUiHorizontalOffsetMeters
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
            if (isFovScrubbing) {
                videoPlayerViewModel.endControlsInputLock()
            }
            if (isSubtitleDepthScrubbing) {
                videoPlayerViewModel.persistImmersiveSubtitleDistanceMeters()
                videoPlayerViewModel.endControlsInputLock()
            }
            if (isSubtitleVerticalScrubbing) {
                videoPlayerViewModel.persistImmersiveSubtitleVerticalOffsetMeters()
                videoPlayerViewModel.endControlsInputLock()
            }
            if (isImmersiveUiHorizontalScrubbing) {
                videoPlayerViewModel.applyImmersiveUiHorizontalOffsetMetersAfterInteraction(
                    immersiveUiHorizontalSliderPosition
                )
                videoPlayerViewModel.endControlsInputLock()
            }
        }
    }

    val controlsHostModifier =
        if (content == XRPlaybackControlsContent.MenuOnly) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth()
        }

    val interactiveControlsHostModifier =
        if (onPanelBackgroundClick != null) {
            controlsHostModifier.clickable(
                interactionSource = panelBackgroundInteractionSource,
                indication = null,
                onClick = onPanelBackgroundClick,
            )
        } else {
            controlsHostModifier
        }

    Box(
        modifier = interactiveControlsHostModifier
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                controlsRootLeftPx = position.x
                controlsRootTopPx = position.y
                controlsRootWidthPx = coordinates.size.width
            },
    ) {
        val activeMenu =
            if (content == XRPlaybackControlsContent.ControlsOnly) {
                PlaybackMenu.None
            } else {
                playerState.activePlaybackMenu
            }
        val menuWidth = activeMenu.playbackMenuWidth(playerState.videoFormat)
        val menuHeight = activeMenu.playbackMenuEstimatedHeight(playerState.videoFormat)
        val menuWidthPx = with(density) { menuWidth.roundToPx() }
        var measuredMenuHeightPx by remember(activeMenu, playerState.videoFormat) {
            mutableIntStateOf(0)
        }
        val menuHeightPx =
            measuredMenuHeightPx.takeIf { it > 0 }
                ?: with(density) { menuHeight.roundToPx() }
        val menuGapPx = with(density) { 10.dp.roundToPx() }
        val activeMenuAnchor = when (activeMenu) {
            PlaybackMenu.Speed -> IntOffset(speedAnchorCenterXPx, speedAnchorTopPx)
            PlaybackMenu.Display -> IntOffset(displayAnchorCenterXPx, displayAnchorTopPx)
            PlaybackMenu.Volume -> IntOffset(volumeAnchorCenterXPx, volumeAnchorTopPx)
            PlaybackMenu.None -> null
        }
        val activeMenuOffset =
            if (
                content == XRPlaybackControlsContent.ControlsWithMenu &&
                    activeMenu != PlaybackMenu.None &&
                    activeMenuAnchor != null
            ) {
                calculatePlaybackMenuOffset(
                    controlsWidthPx = controlsRootWidthPx,
                    anchorCenterXPx = activeMenuAnchor.x,
                    anchorTopYPx = activeMenuAnchor.y,
                    menuWidthPx = menuWidthPx,
                    menuHeightPx = menuHeightPx,
                    gapPx = menuGapPx,
                )
            } else {
                null
            }

        if (
            activeMenu != PlaybackMenu.None &&
                (content == XRPlaybackControlsContent.MenuOnly || activeMenuOffset != null)
        ) {
            Box(
                modifier =
                    if (content == XRPlaybackControlsContent.MenuOnly) {
                        Modifier
                            .align(Alignment.Center)
                            .zIndex(2f)
                    } else {
                        Modifier
                            .align(Alignment.TopStart)
                            .zIndex(2f)
                            .onSizeChanged { measuredMenuHeightPx = it.height }
                            .offset { checkNotNull(activeMenuOffset) }
                    },
            ) {
                when (activeMenu) {
                    PlaybackMenu.Speed -> {
                        PlaybackFloatingMenuCard(
                            title = "Playback Speed",
                            subtitle = "Adjust how fast playback moves",
                            width = menuWidth,
                            containerColor = menuSurface,
                            borderColor = sectionBorder,
                        ) {
                            playbackSpeedRows().forEach { speedRow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    speedRow.forEach { speed ->
                                        PlaybackOptionButton(
                                            text = "${prettySpeed(speed)}x",
                                            selected = playerState.playbackSpeed == speed,
                                            onClick = {
                                                videoPlayerViewModel.setPlaybackSpeed(speed)
                                                videoPlayerViewModel.dismissPlaybackMenu()
                                            },
                                            selectedContainerColor = accentStrong,
                                            selectedContentColor = onAccent,
                                            idleContainerColor = chipIdle,
                                            idleContentColor = textStrong,
                                            borderColor = sectionBorder,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(3 - speedRow.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    PlaybackMenu.Display -> {
                        PlaybackFloatingMenuCard(
                            title = "Display",
                            subtitle = "Projection, stereo, and immersive UI",
                            width = menuWidth,
                            // Subtitles remain visible while this menu is open. Keep the settings
                            // surface opaque so cue text cannot obscure the adjustment controls.
                            containerColor = displayMenuSurface,
                            borderColor = sectionBorder,
                            contentMaxHeight = menuContentMaxHeight,
                        ) {
                            Text(
                                text = "Projection",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = textMuted,
                            )
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
                                            videoPlayerViewModel.dismissPlaybackMenu()
                                        },
                                        selectedContainerColor = accentStrong,
                                        selectedContentColor = onAccent,
                                        idleContainerColor = chipIdle,
                                        idleContentColor = textStrong,
                                        borderColor = sectionBorder,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            if (playerState.audioTracks.isNotEmpty()) {
                                Text(
                                    text = "Audio track",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textMuted,
                                )
                                playerState.audioTracks.chunked(2).forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        row.forEach { track ->
                                            PlaybackOptionButton(
                                                text = track.label,
                                                selected = track.selected,
                                                onClick = {
                                                    videoPlayerViewModel.selectAudioTrack(track)
                                                },
                                                selectedContainerColor = accentStrong,
                                                selectedContentColor = onAccent,
                                                idleContainerColor = chipIdle,
                                                idleContentColor = textStrong,
                                                borderColor = sectionBorder,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }

                            if (playerState.textTracks.isNotEmpty()) {
                                Text(
                                    text = "Subtitle track",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textMuted,
                                )
                                playerState.textTracks.chunked(2).forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        row.forEach { track ->
                                            PlaybackOptionButton(
                                                text = track.label,
                                                selected = track.selected,
                                                onClick = {
                                                    videoPlayerViewModel.selectTextTrack(track)
                                                },
                                                selectedContainerColor = accentStrong,
                                                selectedContentColor = onAccent,
                                                idleContainerColor = chipIdle,
                                                idleContentColor = textStrong,
                                                borderColor = sectionBorder,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }

                            if (playerState.videoFormat != VideoFormat.Format2D) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "FOV",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textMuted,
                                    )
                                    Text(
                                        text = "${(playerState.zoomLevel * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textStrong,
                                    )
                                }
                                Slider(
                                    value = playerState.zoomLevel,
                                    onValueChange = { zoomLevel ->
                                        if (!isFovScrubbing) {
                                            videoPlayerViewModel.beginControlsInputLock()
                                        }
                                        isFovScrubbing = true
                                        videoPlayerViewModel.setZoomLevel(zoomLevel)
                                    },
                                    onValueChangeFinished = {
                                        isFovScrubbing = false
                                        videoPlayerViewModel.endControlsInputLock()
                                    },
                                    valueRange = 0.5f..3.0f,
                                    steps = 24,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentStrong,
                                        activeTrackColor = accentStrong,
                                        inactiveTrackColor = accentSoft,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = "Stereo",
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
                                                videoPlayerViewModel.dismissPlaybackMenu()
                                            },
                                            selectedContainerColor = accentStrong,
                                            selectedContentColor = onAccent,
                                            idleContainerColor = chipIdle,
                                            idleContentColor = textStrong,
                                            borderColor = sectionBorder,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Subtitle eye overlap / depth",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textMuted,
                                    )
                                    Text(
                                        text = prettySubtitleDistance(
                                            playerState.immersiveSubtitleDistanceMeters
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textStrong,
                                    )
                                }
                                Slider(
                                    value = playerState.immersiveSubtitleDistanceMeters,
                                    onValueChange = { distanceMeters ->
                                        if (!isSubtitleDepthScrubbing) {
                                            videoPlayerViewModel.beginControlsInputLock()
                                        }
                                        isSubtitleDepthScrubbing = true
                                        videoPlayerViewModel.setImmersiveSubtitleDistanceMeters(
                                            distanceMeters
                                        )
                                    },
                                    onValueChangeFinished = {
                                        isSubtitleDepthScrubbing = false
                                        videoPlayerViewModel.persistImmersiveSubtitleDistanceMeters()
                                        videoPlayerViewModel.endControlsInputLock()
                                    },
                                    valueRange =
                                        MIN_IMMERSIVE_SUBTITLE_DISTANCE_METERS..
                                                MAX_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
                                    steps = IMMERSIVE_SUBTITLE_DISTANCE_SLIDER_STEPS,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentStrong,
                                        activeTrackColor = accentStrong,
                                        inactiveTrackColor = accentSoft,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = "Adjust in 0.1 m steps until both eyes see one subtitle.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textMuted,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Subtitle vertical position",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textMuted,
                                    )
                                    Text(
                                        text = prettySubtitleVerticalOffset(
                                            playerState.immersiveSubtitleVerticalOffsetMeters
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textStrong,
                                    )
                                }
                                Slider(
                                    value = playerState.immersiveSubtitleVerticalOffsetMeters,
                                    onValueChange = { offsetMeters ->
                                        if (!isSubtitleVerticalScrubbing) {
                                            videoPlayerViewModel.beginControlsInputLock()
                                        }
                                        isSubtitleVerticalScrubbing = true
                                        videoPlayerViewModel
                                            .setImmersiveSubtitleVerticalOffsetMeters(offsetMeters)
                                    },
                                    onValueChangeFinished = {
                                        isSubtitleVerticalScrubbing = false
                                        videoPlayerViewModel
                                            .persistImmersiveSubtitleVerticalOffsetMeters()
                                        videoPlayerViewModel.endControlsInputLock()
                                    },
                                    valueRange =
                                        MIN_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS..
                                                MAX_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS,
                                    steps = IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_SLIDER_STEPS,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentStrong,
                                        activeTrackColor = accentStrong,
                                        inactiveTrackColor = accentSoft,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = "Moves subtitles only. Playback controls stay fixed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textMuted,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "UI horizontal position",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textMuted,
                                    )
                                    Text(
                                        text = prettyImmersiveUiHorizontalOffset(
                                            immersiveUiHorizontalSliderPosition
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textStrong,
                                    )
                                }
                                Slider(
                                    value = immersiveUiHorizontalSliderPosition,
                                    onValueChange = { offsetMeters ->
                                        if (!isImmersiveUiHorizontalScrubbing) {
                                            videoPlayerViewModel.beginControlsInputLock()
                                        }
                                        isImmersiveUiHorizontalScrubbing = true
                                        immersiveUiHorizontalSliderPosition = offsetMeters
                                    },
                                    onValueChangeFinished = {
                                        isImmersiveUiHorizontalScrubbing = false
                                        videoPlayerViewModel
                                            .applyImmersiveUiHorizontalOffsetMetersAfterInteraction(
                                                immersiveUiHorizontalSliderPosition
                                            )
                                        videoPlayerViewModel.endControlsInputLock()
                                    },
                                    valueRange =
                                        MIN_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS..
                                                MAX_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS,
                                    steps = IMMERSIVE_UI_HORIZONTAL_OFFSET_SLIDER_STEPS,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentStrong,
                                        activeTrackColor = accentStrong,
                                        inactiveTrackColor = accentSoft,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = "Moves subtitles and playback controls together.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textMuted,
                                )
                            }
                        }
                    }

                    PlaybackMenu.Volume -> {
                        PlaybackFloatingMenuCard(
                            title = "Volume",
                            subtitle = "Output level for the current session",
                            width = menuWidth,
                            containerColor = menuSurface,
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

                    PlaybackMenu.None -> Unit
                }
            }
        }

        if (content == XRPlaybackControlsContent.MenuOnly) return@Box

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = controlsCardInteractionSource,
                    indication = null,
                    onClick = {},
                )
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(28.dp),
                    clip = false,
                )
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(panelTop, panelBottom),
                    ),
                )
                .border(1.dp, sectionBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(42.dp)
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
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = playerState.videoFile?.name ?: "Video Player",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = textStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildPlaybackSummary(playerState),
                            style = MaterialTheme.typography.bodySmall,
                            color = textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = videoPlayerViewModel::toggleSubtitles,
                        modifier = Modifier.height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                if (playerState.subtitlesEnabled) accentStrong else sectionSurface,
                            contentColor =
                                if (playerState.subtitlesEnabled) onAccent else textStrong,
                        ),
                        border = BorderStroke(
                            width = if (playerState.subtitlesEnabled) 1.2.dp else 1.dp,
                            color = sectionBorder,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = if (playerState.subtitlesEnabled) "SUB ON" else "SUB OFF",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }

                    PlaybackMenuIconButton(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "Playback Speed",
                        selected = playerState.activePlaybackMenu == PlaybackMenu.Speed,
                        containerColor = if (playerState.activePlaybackMenu == PlaybackMenu.Speed) {
                            chipActive
                        } else {
                            sectionSurface
                        },
                        borderColor = sectionBorder,
                        contentColor = textStrong,
                        onClick = {
                            videoPlayerViewModel.togglePlaybackMenu(PlaybackMenu.Speed)
                        },
                        onPositioned = { centerX, topY ->
                            speedAnchorCenterXPx = (centerX - controlsRootLeftPx).roundToInt()
                            speedAnchorTopPx = (topY - controlsRootTopPx).roundToInt()
                        },
                    )

                    PlaybackMenuIconButton(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = "Display Settings",
                        selected = playerState.activePlaybackMenu == PlaybackMenu.Display,
                        containerColor = if (playerState.activePlaybackMenu == PlaybackMenu.Display) {
                            chipActive
                        } else {
                            sectionSurface
                        },
                        borderColor = sectionBorder,
                        contentColor = textStrong,
                        onClick = {
                            videoPlayerViewModel.togglePlaybackMenu(PlaybackMenu.Display)
                        },
                        onPositioned = { centerX, topY ->
                            displayAnchorCenterXPx = (centerX - controlsRootLeftPx).roundToInt()
                            displayAnchorTopPx = (topY - controlsRootTopPx).roundToInt()
                        },
                    )

                    PlaybackMenuIconButton(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        selected = playerState.activePlaybackMenu == PlaybackMenu.Volume,
                        containerColor = if (playerState.activePlaybackMenu == PlaybackMenu.Volume) {
                            chipActive
                        } else {
                            sectionSurface
                        },
                        borderColor = sectionBorder,
                        contentColor = textStrong,
                        onClick = {
                            videoPlayerViewModel.togglePlaybackMenu(PlaybackMenu.Volume)
                        },
                        onPositioned = { centerX, topY ->
                            volumeAnchorCenterXPx = (centerX - controlsRootLeftPx).roundToInt()
                            volumeAnchorTopPx = (topY - controlsRootTopPx).roundToInt()
                        },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(sectionSurface)
                        .border(1.dp, sectionBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        modifier = Modifier.fillMaxWidth(),
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        // Keep this fallback until Galaxy XR exposes a verified, independent
                        // Grip/Trigger input path. SceneCore currently reports only primary action.
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Button(
                            onClick = videoPlayerViewModel::recenterView,
                            enabled = playerState.recenterCountdownSeconds == 0,
                        ) {
                            Text(
                                if (playerState.recenterCountdownSeconds > 0) {
                                    "Look forward · ${playerState.recenterCountdownSeconds}"
                                } else "Recenter in 3s"
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.defaultMinSize(minHeight = 68.dp),
                ) {
                    PlaybackIconControlButton(
                        onClick = { videoPlayerViewModel.playPreviousVideo() },
                        enabled = playerState.canPlayPrevious,
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous Video",
                        containerColor = sectionSurface,
                        borderColor = sectionBorder,
                        contentColor = textStrong,
                        buttonSize = 52.dp,
                        iconSize = 28.dp,
                    )
                    PlaybackIconControlButton(
                        onClick = { videoPlayerViewModel.skipBackward() },
                        imageVector = Icons.Filled.FastRewind,
                        contentDescription = "Rewind",
                        containerColor = sectionSurface,
                        borderColor = sectionBorder,
                        contentColor = textStrong,
                        buttonSize = 52.dp,
                        iconSize = 28.dp,
                    )
                    PlaybackIconControlButton(
                        onClick = {
                            if (playerState.isPlaying) {
                                videoPlayerViewModel.pause()
                            } else {
                                videoPlayerViewModel.play()
                            }
                        },
                        imageVector = if (playerState.isPlaying) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        containerColor = accentStrong,
                        borderColor = accentStrong.copy(alpha = 0.8f),
                        contentColor = onAccent,
                        buttonSize = 68.dp,
                        iconSize = 38.dp,
                    )
                    PlaybackIconControlButton(
                        onClick = { videoPlayerViewModel.skipForward() },
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "Fast Forward",
                        containerColor = sectionSurface,
                        borderColor = sectionBorder,
                        contentColor = textStrong,
                        buttonSize = 52.dp,
                        iconSize = 28.dp,
                    )
                    PlaybackIconControlButton(
                        onClick = { videoPlayerViewModel.playNextVideo() },
                        enabled = playerState.canPlayNext,
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next Video",
                        containerColor = sectionSurface,
                        borderColor = sectionBorder,
                        contentColor = textStrong,
                        buttonSize = 52.dp,
                        iconSize = 28.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackIconControlButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    imageVector: ImageVector,
    contentDescription: String,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 56.dp,
    iconSize: Dp = 32.dp,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.35f))
            .border(1.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.35f), CircleShape),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (enabled) contentColor else contentColor.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun PlaybackMenuIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onPositioned: (centerX: Int, topY: Int) -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                onPositioned(
                    position.x.roundToInt() + (coordinates.size.width / 2),
                    position.y.roundToInt(),
                )
            }
            .clip(CircleShape)
            .background(containerColor)
            .border(
                width = if (selected) 1.2.dp else 1.dp,
                color = borderColor,
                shape = CircleShape,
            ),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = contentColor,
        )
    }
}

@Composable
private fun PlaybackFloatingMenuCard(
    title: String,
    subtitle: String,
    width: Dp,
    containerColor: Color,
    borderColor: Color,
    contentMaxHeight: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val menuInputInteractionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(width)
            .clickable(
                interactionSource = menuInputInteractionSource,
                indication = null,
                onClick = {},
            )
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(18.dp),
                clip = false,
            )
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
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        val contentModifier =
            if (contentMaxHeight != null) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = contentMaxHeight)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun PlaybackSeekPreviewCard(
    targetPositionMs: Long,
    previewFrame: SeekPreviewFrame?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.84f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center,
        ) {
            if (previewFrame != null) {
                Image(
                    bitmap = previewFrame.bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = "Preview",
                    color = Color.White.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-12).dp, y = (-12).dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                text = formatTime(targetPositionMs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.96f),
            )
        }
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
        modifier = modifier.defaultMinSize(minHeight = 42.dp),
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

private fun playbackSpeedRows(): List<List<Float>> =
    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).chunked(3)

internal fun PlaybackMenu.playbackMenuWidth(videoFormat: VideoFormat): Dp = when (this) {
    PlaybackMenu.Speed -> 276.dp
    PlaybackMenu.Display -> if (videoFormat == VideoFormat.Format2D) 360.dp else 420.dp
    PlaybackMenu.Volume -> 264.dp
    PlaybackMenu.None -> 0.dp
}

internal fun PlaybackMenu.playbackMenuEstimatedHeight(videoFormat: VideoFormat): Dp = when (this) {
    PlaybackMenu.Speed -> 170.dp
    PlaybackMenu.Display -> if (videoFormat == VideoFormat.Format2D) 170.dp else 640.dp
    PlaybackMenu.Volume -> 156.dp
    PlaybackMenu.None -> 0.dp
}

internal fun calculatePlaybackMenuOffset(
    controlsWidthPx: Int,
    anchorCenterXPx: Int,
    anchorTopYPx: Int,
    menuWidthPx: Int,
    menuHeightPx: Int,
    gapPx: Int,
): IntOffset {
    val maxOffsetX = (controlsWidthPx - menuWidthPx).coerceAtLeast(0)
    val desiredOffsetY = anchorTopYPx - menuHeightPx - gapPx
    return IntOffset(
        x = (anchorCenterXPx - (menuWidthPx / 2)).coerceIn(0, maxOffsetX),
        y = desiredOffsetY,
    )
}

private fun buildPlaybackSummary(playerState: VideoPlayerState): String {
    val projection = playerState.videoFormat.name.removePrefix("Format")
    val stereo =
        if (playerState.videoFormat == VideoFormat.Format2D) {
            null
        } else {
            prettyStereoMode(playerState.stereoMode)
        }
    val speed = "${prettySpeed(playerState.playbackSpeed)}x"
    val volume = "${(playerState.volume * 100).roundToInt()}%"

    return listOfNotNull(projection, stereo, speed, volume).joinToString("  •  ")
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

private fun prettySubtitleDistance(distanceMeters: Float): String {
    val tenths = (distanceMeters * 10f).roundToInt()
    return "${tenths / 10}.${tenths % 10} m"
}

private fun prettyImmersiveUiHorizontalOffset(offsetMeters: Float): String {
    val centimeters = (offsetMeters * 100f).roundToInt()
    return when {
        centimeters < 0 -> "${-centimeters} cm left"
        centimeters > 0 -> "$centimeters cm right"
        else -> "Center"
    }
}

private fun prettySubtitleVerticalOffset(offsetMeters: Float): String {
    val centimeters = (offsetMeters * 100f).roundToInt()
    return when {
        centimeters < 0 -> "${-centimeters} cm down"
        centimeters > 0 -> "$centimeters cm up"
        else -> "Center"
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
