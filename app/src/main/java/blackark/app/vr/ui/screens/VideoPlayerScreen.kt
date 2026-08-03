@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class],
)

package blackark.app.vr.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.util.Log
import android.view.Surface
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.xr.arcore.ArDevice
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialExternalSurface
import androidx.xr.compose.subspace.SpatialExternalSurfaceHemisphere
import androidx.xr.compose.subspace.SpatialExternalSurfaceSphere
import androidx.xr.compose.subspace.SpatialExternalSurfaceScope
import androidx.xr.compose.subspace.SpatialMainPanel
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.StereoMode
import androidx.xr.compose.subspace.draw.scale
import androidx.xr.compose.subspace.layout.InteractionPolicy
import androidx.xr.compose.subspace.layout.ResizePolicy
import androidx.xr.compose.subspace.layout.SpatialInputEvent
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.fillMaxSize
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.layout.rotate
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.unit.DpVolumeSize
import androidx.xr.compose.unit.Meter.Companion.meters
import androidx.xr.runtime.DeviceTrackingMode
import androidx.xr.runtime.Config
import androidx.xr.runtime.SessionConfigureSuccess
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.InputEvent
import blackark.app.vr.XRStreamApplication
import blackark.app.vr.player.PlaybackSource
import blackark.app.vr.ui.ApplyHandTrackingPreference
import blackark.app.vr.ui.components.XRPlaybackControls
import blackark.app.vr.ui.components.XRPlaybackControlsContent
import blackark.app.vr.ui.components.playbackMenuEstimatedHeight
import blackark.app.vr.ui.components.playbackMenuWidth
import blackark.app.vr.ui.isSpatialInputSourceAllowed
import blackark.app.vr.ui.viewmodel.PlaybackMenu
import blackark.app.vr.ui.viewmodel.PlayerEvent
import blackark.app.vr.ui.viewmodel.SubtitleTextSize
import blackark.app.vr.ui.viewmodel.VideoFormat
import blackark.app.vr.ui.viewmodel.VideoPlayerState
import blackark.app.vr.ui.viewmodel.VideoPlayerViewModel
import blackark.app.vr.ui.viewmodel.VideoPlayerViewModelFactory
import blackark.app.vr.utils.AppSettingsStore
import blackark.app.vr.utils.SubtitleFontCatalog
import blackark.app.vr.utils.VrSeekPreviewRenderer
import blackark.app.vr.utils.resolveImmersiveSubtitlePanelOffsetDp
import blackark.app.vr.utils.resolveImmersiveSubtitlePlacement
import blackark.app.vr.utils.resolveImmersiveUiHorizontalOffsetDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2

private const val TAG = "VideoPlayerScreen"
private val HIDDEN_MAIN_PANEL_OFFSET = 4000.dp
private val HIDDEN_MAIN_PANEL_ANCHOR_SIZE = 2.dp
private val IMMERSIVE_CONTROLS_PANEL_HEIGHT = 360.dp
private val IMMERSIVE_CONTROLS_PANEL_WIDTH_MONO = 1260.dp
private val IMMERSIVE_CONTROLS_PANEL_WIDTH_STEREO = 1460.dp
private val IMMERSIVE_CONTROLS_FOLLOW_DISTANCE = 900.dp
private const val IMMERSIVE_CONTROLS_FRONT_FACTOR = 0.84f
// Subspace uses positive Y upward, so a negative offset places controls lower.
private val IMMERSIVE_CONTROLS_DOWN_OFFSET = (-320).dp
private val IMMERSIVE_MENU_PANEL_PADDING = 96.dp
private val IMMERSIVE_MENU_CONTROLS_GAP = 20.dp
private val IMMERSIVE_DISPLAY_PANEL_HEIGHT = 620.dp
private val IMMERSIVE_DISPLAY_CONTENT_MAX_HEIGHT = 440.dp
private val IMMERSIVE_MENU_FRONT_OFFSET = 16.dp
private val IMMERSIVE_REVEAL_PANEL_WIDTH = 2400.dp
private val IMMERSIVE_REVEAL_PANEL_HEIGHT = 1400.dp
private val IMMERSIVE_REVEAL_FOLLOW_DISTANCE = 480.dp
// Used only while controls are hidden; the panel follows the viewer's current field of view.
private const val IMMERSIVE_REVEAL_FRONT_FACTOR = 0.86f
private val IMMERSIVE_SUBTITLE_PANEL_WIDTH = 1280.dp
private val IMMERSIVE_SUBTITLE_PANEL_HEIGHT = 720.dp
// Keep the subtitle baseline low enough that the minimum user offset can sit near
// the bottom of the immersive field of view without changing saved preferences.
private val IMMERSIVE_SUBTITLE_BASE_UP_OFFSET = 180.dp
private val IMMERSIVE_SEEK_PREVIEW_WIDTH = 520.dp
private val IMMERSIVE_SEEK_PREVIEW_HEIGHT = 292.dp
private val IMMERSIVE_SEEK_PREVIEW_UP_OFFSET = 330.dp
private const val SUBTITLE_DEFAULT_BOTTOM_PADDING_FRACTION = 0.08f
private const val SUBTITLE_CONTROLS_VISIBLE_BOTTOM_PADDING_FRACTION = 0.44f

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun clickInteractionPolicy(
    isEnabled: Boolean = true,
    isHandTrackingEnabled: Boolean = true,
    onClick: (() -> Unit)? = null,
): InteractionPolicy =
    object : InteractionPolicy {
        override val isEnabled: Boolean = isEnabled

        override fun onInputEvent(event: SpatialInputEvent) {
            if (!isSpatialInputSourceAllowed(isHandTrackingEnabled, event.source)) {
                return
            }
            if (onClick != null && isSpatialRevealClick(event.action)) {
                onClick()
            }
        }
    }

internal fun isSpatialRevealClick(action: InputEvent.Action): Boolean =
    action == InputEvent.Action.UP

@Composable
private fun rememberPlaybackScrollState(
    onDelta: (Float) -> Unit = {},
): androidx.compose.foundation.gestures.ScrollableState {
    val currentOnDelta = rememberUpdatedState(onDelta)
    return rememberScrollableState { delta ->
        currentOnDelta.value(delta)
        delta
    }
}

@Composable
private fun PlaybackScrollInputOverlay(
    showControls: Boolean,
    inputEnabled: Boolean = !showControls,
    backgroundToggleEnabled: Boolean = true,
    controlsInputLocked: Boolean,
    seekPreviewActive: Boolean,
    onKeyUp: ((android.view.KeyEvent) -> Boolean)? = null,
    onToggleControls: () -> Unit = {},
    onHorizontalScrollDelta: (Float) -> Unit = {},
    onVerticalScrollDelta: (Float) -> Unit = {},
    controlsAlignment: Alignment = Alignment.Center,
    controlsPadding: PaddingValues = PaddingValues(0.dp),
    controlsModifier: Modifier = Modifier,
    controlsContent: @Composable (() -> Unit)? = null,
) {
    val inputLocked = controlsInputLocked || seekPreviewActive
    val verticalScrollState = rememberPlaybackScrollState(onDelta = onVerticalScrollDelta)
    val horizontalScrollState = rememberPlaybackScrollState(onDelta = onHorizontalScrollDelta)
    val focusRequester = remember { FocusRequester() }
    val revealInteractionSource = remember { MutableInteractionSource() }
    var hasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(inputEnabled) {
        if (!inputEnabled) return@LaunchedEffect
        delay(80)
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(hasFocus, inputEnabled) {
        if (inputEnabled && !hasFocus) {
            delay(120)
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                interactionSource = revealInteractionSource,
                indication = null,
                enabled = !inputLocked && backgroundToggleEnabled,
                onClick = onToggleControls,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                hasFocus = focusState.hasFocus
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyUp) {
                    return@onPreviewKeyEvent false
                }
                onKeyUp?.invoke(keyEvent.nativeKeyEvent) ?: false
            }
            .focusable(enabled = inputEnabled)
            .scrollable(
                state = verticalScrollState,
                orientation = Orientation.Vertical,
                enabled = !inputLocked && inputEnabled,
            )
            .scrollable(
                state = horizontalScrollState,
                orientation = Orientation.Horizontal,
                enabled = !inputLocked && inputEnabled,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showControls && controlsContent != null) {
            Box(
                modifier = controlsModifier
                    .align(controlsAlignment)
                    .padding(controlsPadding),
            ) {
                controlsContent()
            }
        }
    }
}

private val DEFAULT_FLAT_PANEL_WIDTH = 1280.dp
private val DEFAULT_FLAT_PANEL_HEIGHT = 720.dp
private val MIN_FLAT_PANEL_SIZE =
    DpVolumeSize(
        width = 960.dp,
        height = 540.dp,
        depth = 0.dp,
    )
private val MAX_FLAT_PANEL_SIZE =
    DpVolumeSize(
        width = 2560.dp,
        height = 1440.dp,
        depth = 0.dp,
    )

private data class HeadFollowPose(
    val rotation: Quaternion,
    val forward: Vector3,
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoFilePath: String,
    videoFileName: String,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as XRStreamApplication).container
    }

    val videoPlayerViewModel: VideoPlayerViewModel = viewModel(
        factory = VideoPlayerViewModelFactory(
            container.videoRepository,
            container.videoDisplaySettingsRepository,
        ),
    )
    val isHandTrackingEnabled = remember(context) {
        AppSettingsStore.isHandTrackingEnabled(context.applicationContext)
    }
    val hasHandTrackingPermission = remember(context) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context.applicationContext,
            "android.permission.HAND_TRACKING"
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val playerState by videoPlayerViewModel.state.collectAsState()
    val dashboardPanelPose by blackark.app.vr.AppState.dashboardPanelPose.collectAsState()
    val dashboardPanelSize by blackark.app.vr.AppState.dashboardPanelSize.collectAsState()
    val requestNavigateBackState =
        rememberUpdatedState { videoPlayerViewModel.requestNavigateBack() }

    DisposableEffect(Unit) {
        blackark.app.vr.AppState.setConsumePlaybackBackKeyEvents(true)
        onDispose {
            blackark.app.vr.AppState.setConsumePlaybackBackKeyEvents(false)
        }
    }

    DisposableEffect(context) {
        val activity = context.findActivity()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || activity == null) {
            onDispose {}
        } else {
            val callback = OnBackInvokedCallback {
                requestNavigateBackState.value.invoke()
            }
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback,
            )
            onDispose {
                activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
            }
        }
    }

    LaunchedEffect(videoFilePath, videoFileName) {
        val playbackSource =
            if (videoFilePath.startsWith("smb://", ignoreCase = true)) {
                blackark.app.vr.AppState.smbConfig?.let { PlaybackSource.Smb(it) }
            } else {
                PlaybackSource.Local(
                    rootTreeUri = AppSettingsStore.getLocalStorageTreeUri(context.applicationContext)
                )
            }

        if (videoFilePath.isNotEmpty() && playbackSource != null) {
            val videoFile = blackark.app.vr.network.SMBFileItem(
                name = videoFileName,
                path = videoFilePath,
                isDirectory = false,
                size = 0,
                lastModified = 0,
            )
            videoPlayerViewModel.initializePlayer(context, playbackSource, videoFile)
        }
    }

    LaunchedEffect(videoPlayerViewModel) {
        videoPlayerViewModel.playerEvents.collect { event ->
            when (event) {
                PlayerEvent.NavigateBack -> {
                    onNavigateBack()
                    videoPlayerViewModel.releasePlayerAsync()
                }
            }
        }
    }
    Subspace {
        SpatialVideoPlayerContent(
            videoPlayerViewModel = videoPlayerViewModel,
            playerState = playerState,
            isHandTrackingEnabled = isHandTrackingEnabled,
            hasHandTrackingPermission = hasHandTrackingPermission,
            dashboardPanelPose = dashboardPanelPose,
            dashboardPanelWidth = dashboardPanelSize.widthDp.dp,
            dashboardPanelHeight = dashboardPanelSize.heightDp.dp,
            onNavigateBack = { videoPlayerViewModel.requestNavigateBack() },
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SpatialVideoPlayerContent(
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: VideoPlayerState,
    isHandTrackingEnabled: Boolean,
    hasHandTrackingPermission: Boolean,
    dashboardPanelPose: Pose?,
    dashboardPanelWidth: Dp,
    dashboardPanelHeight: Dp,
    onNavigateBack: () -> Unit,
) {
    val exoPlayer by videoPlayerViewModel.playerFlow.collectAsState()
    val showControls = playerState.showControls
    val session = LocalSession.current
    val density = LocalDensity.current

    ApplyHandTrackingPreference(
        isHandTrackingEnabled = isHandTrackingEnabled,
        hasHandTrackingPermission = hasHandTrackingPermission,
        logTag = TAG,
    )

    var flatPanelWidth by remember { mutableStateOf(DEFAULT_FLAT_PANEL_WIDTH) }
    var flatPanelHeight by remember { mutableStateOf(DEFAULT_FLAT_PANEL_HEIGHT) }
    val flatPanelResizePolicy =
        ResizePolicy.custom { event ->
            val newSize = event.size
            if (newSize.width > 0 && newSize.height > 0) {
                flatPanelWidth = with(density) { newSize.width.toDp() }
                flatPanelHeight = with(density) { newSize.height.toDp() }
            }
        }

    BackHandler {
        onNavigateBack()
    }
    val spatialCapabilities = LocalSpatialCapabilities.current

    val immersiveRequested = playerState.videoFormat != VideoFormat.Format2D
    val supportsImmersiveDome = spatialCapabilities.isContent3dEnabled
    val shouldUseImmersiveDome = immersiveRequested && supportsImmersiveDome
    val isSurfaceReady = !playerState.isLoading && playerState.error == null
    val playbackLayerPolicy = resolvePlaybackLayerPolicy(
        isSurfaceReady = isSurfaceReady,
        isImmersive = shouldUseImmersiveDome,
        showControls = showControls,
        controlsInputLocked = playerState.controlsInputLocked,
        seekPreviewActive = playerState.seekPreviewActive,
    )
    val subtitlesPresent =
        playerState.subtitlesEnabled &&
            (
                playerState.externalSubtitleFileName != null ||
                    playerState.subtitleCues.isNotEmpty()
            )

    // Keep the Activity main panel alive while this screen is a pure Subspace composition.
    // The anchor stays off-screen so the dashboard panel does not cover video playback.
    SpatialMainPanel(
        modifier = buildHiddenMainPanelAnchorModifier(),
    )

    val enableHeadFollowIn2D = playerState.videoFormat == VideoFormat.Format2D
    val enablePlaybackUiHeadFollow = shouldEnablePlaybackUiHeadFollow(
        isTwoDimensional = enableHeadFollowIn2D,
        isImmersive = shouldUseImmersiveDome,
        showControlsLayer = playbackLayerPolicy.showControlsLayer,
    )
    val enableImmersiveRevealHeadFollow =
        shouldUseImmersiveDome && playbackLayerPolicy.showFallbackRevealLayer
    val enableImmersiveSeekPreviewHeadFollow =
        shouldUseImmersiveDome && playerState.seekPreviewActive
    val enableImmersiveHeadFollow =
        enableImmersiveRevealHeadFollow || enableImmersiveSeekPreviewHeadFollow
    // Disabled for 180 stereo because the custom lock-rotation path can blank hemisphere rendering.
    val enableHeadFollowIn180Stereo = false

    val headFollowPose by produceState<HeadFollowPose?>(
        initialValue = null,
        session,
        enablePlaybackUiHeadFollow,
    ) {
        value = null
        if (!enablePlaybackUiHeadFollow) return@produceState

        val activeSession = session ?: return@produceState
        val arDevice = runCatching { ArDevice.getInstance(activeSession) }.getOrNull()
        if (arDevice == null) {
            Log.w(TAG, "ArDevice is unavailable; using default orientation")
            return@produceState
        }

        var smoothedRotation: Quaternion? = null
        var smoothedForward: Vector3? = null
        arDevice.state.collect { deviceState ->
            val forward = deviceState.devicePose.forward
            val horizontalForward = Vector3(forward.x, 0f, forward.z)
            if (horizontalForward.lengthSquared < 1e-6f) return@collect

            val normalizedForward = horizontalForward.toNormalized()
            val blendedForward =
                if (smoothedForward == null) {
                    normalizedForward
                } else {
                    Vector3.lerp(smoothedForward!!, normalizedForward, 0.45f)
                }
            if (blendedForward.lengthSquared < 1e-6f) return@collect
            smoothedForward = blendedForward.toNormalized()

            val targetRotation = Quaternion.fromLookTowards(smoothedForward!!, Vector3.Up)
            smoothedRotation =
                if (smoothedRotation == null) {
                    targetRotation
                } else {
                    Quaternion.slerp(smoothedRotation!!, targetRotation, 0.45f)
                }

            value = HeadFollowPose(rotation = smoothedRotation!!, forward = smoothedForward!!)
        }
    }

    val immersiveRevealHeadPose by produceState<HeadFollowPose?>(
        initialValue = null,
        session,
        enableImmersiveHeadFollow,
    ) {
        value = null
        if (!enableImmersiveHeadFollow) return@produceState

        val activeSession = session ?: return@produceState
        val arDevice = runCatching { ArDevice.getInstance(activeSession) }.getOrNull()
        if (arDevice == null) {
            Log.w(TAG, "ArDevice is unavailable; using the saved reveal input pose")
            return@produceState
        }

        var smoothedForward: Vector3? = null
        var smoothedRotation: Quaternion? = null
        arDevice.state.collect { deviceState ->
            val forward = deviceState.devicePose.forward
            if (forward.lengthSquared < 1e-6f) return@collect

            val normalizedForward = forward.toNormalized()
            val blendedForward =
                if (smoothedForward == null) {
                    normalizedForward
                } else {
                    Vector3.lerp(smoothedForward!!, normalizedForward, 0.55f)
                }
            if (blendedForward.lengthSquared < 1e-6f) return@collect
            smoothedForward = blendedForward.toNormalized()
            val targetRotation = deviceState.devicePose.rotation
            smoothedRotation =
                if (smoothedRotation == null) {
                    targetRotation
                } else {
                    Quaternion.slerp(smoothedRotation!!, targetRotation, 0.55f)
                }

            value = HeadFollowPose(
                rotation = smoothedRotation!!,
                forward = smoothedForward!!,
            )
        }
    }

    // Device tracking must be enabled for head-follow behavior.
    LaunchedEffect(session, enablePlaybackUiHeadFollow, enableImmersiveHeadFollow) {
        val activeSession = session ?: return@LaunchedEffect
        if (!enablePlaybackUiHeadFollow && !enableImmersiveHeadFollow) return@LaunchedEffect

        val currentConfig = activeSession.config
        if (currentConfig.deviceTracking != DeviceTrackingMode.DISABLED) {
            return@LaunchedEffect
        }

        val updatedConfig = Config.Builder(currentConfig)
            .setDeviceTracking(DeviceTrackingMode.SPATIAL)
            .build()
        val result = activeSession.configure(updatedConfig)
        if (result !is SessionConfigureSuccess) {
            Log.w(TAG, "Failed to enable XR device tracking: ${result::class.java.simpleName}")
        }
    }

    val xrStereoMode = when (playerState.stereoMode) {
        blackark.app.vr.ui.viewmodel.StereoMode.Mono -> StereoMode.Mono
        blackark.app.vr.ui.viewmodel.StereoMode.SideBySide -> StereoMode.SideBySide
        blackark.app.vr.ui.viewmodel.StereoMode.TopBottom -> StereoMode.TopBottom
    }

    val targetControlsVisible = !showControls
    val surfaceControlsInteractionPolicy =
        when {
            playbackLayerPolicy.enableSurfaceToggleInput ->
                clickInteractionPolicy(isHandTrackingEnabled = isHandTrackingEnabled) {
                    videoPlayerViewModel.setControlsVisibility(
                        visible = targetControlsVisible,
                        source = "video-surface",
                    )
                }

            else -> null
        }

    if (exoPlayer == null) {
        // Avoid panel pop/flicker in immersive startup: keep black background until surface is ready.
        if (playerState.videoFile == null || playerState.videoFormat != VideoFormat.Format2D) {
            return
        }
        PlayerLoadingPanel(
            errorMessage = playerState.error,
            dashboardPanelPose = dashboardPanelPose,
            panelWidth = flatPanelWidth,
            panelHeight = flatPanelHeight,
            resizePolicy = flatPanelResizePolicy,
        )
        return
    }

    if (shouldUseImmersiveDome) {
        ImmersivePlayer(
            exoPlayer = exoPlayer,
            videoFormat = playerState.videoFormat,
            stereoMode = xrStereoMode,
            interactionPolicy = surfaceControlsInteractionPolicy,
            zoomLevel = playerState.zoomLevel,
            headLockedRotation180 =
                when {
                    enableHeadFollowIn180Stereo -> headFollowPose?.rotation
                    else -> dashboardPanelPose?.rotation
                },
        )

        if (isSurfaceReady) {
            if (shouldShowImmersiveSubtitlePanel(subtitlesPresent, showControls)) {
                SpatialPanel(
                    modifier = buildImmersiveSubtitleModifier(
                        dashboardPanelPose = dashboardPanelPose,
                        density = density,
                        distanceMeters = playerState.immersiveSubtitleDistanceMeters,
                        horizontalOffsetMeters = playerState.immersiveUiHorizontalOffsetMeters,
                        verticalOffsetMeters =
                            playerState.immersiveSubtitleVerticalOffsetMeters,
                    ),
                    interactionPolicy = clickInteractionPolicy(
                        isHandTrackingEnabled = isHandTrackingEnabled,
                    ) {
                        videoPlayerViewModel.setControlsVisibility(
                            visible = targetControlsVisible,
                            source = "immersive-subtitle-panel",
                        )
                    },
                ) {
                    SubtitleCueOverlay(
                        cues = playerState.subtitleCues,
                        bottomPaddingFraction = SUBTITLE_DEFAULT_BOTTOM_PADDING_FRACTION,
                        fontId = playerState.subtitleFontId,
                        textSize = playerState.subtitleTextSize,
                    )
                }
            }

            if (playbackLayerPolicy.showFallbackRevealLayer) {
                SpatialPanel(
                    modifier = buildImmersiveRevealInputModifier(
                        dashboardPanelPose = dashboardPanelPose,
                        headFollowPose = immersiveRevealHeadPose,
                        density = density,
                    ),
                    interactionPolicy = clickInteractionPolicy(
                        isHandTrackingEnabled = isHandTrackingEnabled,
                    ) {
                        videoPlayerViewModel.setControlsVisibility(
                            visible = targetControlsVisible,
                            source = "immersive-fallback-panel",
                        )
                    },
                ) {
                    PlaybackScrollInputOverlay(
                        showControls = false,
                        inputEnabled = true,
                        backgroundToggleEnabled = false,
                        onKeyUp = { event ->
                            videoPlayerViewModel.dispatchPlaybackKeyEvent(event)
                        },
                        onToggleControls = {
                            videoPlayerViewModel.setControlsVisibility(
                                visible = targetControlsVisible,
                                source = "immersive-fallback-overlay",
                            )
                        },
                        controlsInputLocked = playerState.controlsInputLocked,
                        seekPreviewActive = playerState.seekPreviewActive,
                        onHorizontalScrollDelta = { delta ->
                            videoPlayerViewModel.handlePlaybackHorizontalScroll(delta)
                        },
                        onVerticalScrollDelta = { delta ->
                            videoPlayerViewModel.handlePlaybackVerticalScroll(delta)
                        },
                    )
                }
            }

            if (playerState.seekPreviewActive) {
                VrSeekPreviewSurface(
                    previewPath = playerState.seekPreviewThumbnailPath,
                    videoFormat = playerState.videoFormat,
                    stereoMode = playerState.stereoMode,
                    headPose = immersiveRevealHeadPose,
                    dashboardPanelPose = dashboardPanelPose,
                    density = density,
                )
            }

            if (playbackLayerPolicy.showControlsLayer) {
                SpatialPanel(
                    modifier = buildImmersiveControlsModifier(
                        dashboardPanelPose = dashboardPanelPose,
                        headFollowPose = headFollowPose,
                        density = density,
                        stereoMode = playerState.stereoMode,
                        horizontalOffsetMeters = playerState.immersiveUiHorizontalOffsetMeters,
                    ),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        XRPlaybackControls(
                            videoPlayerViewModel = videoPlayerViewModel,
                            playerState = playerState,
                            onNavigateBack = { videoPlayerViewModel.requestNavigateBack() },
                            content = XRPlaybackControlsContent.ControlsOnly,
                            onPanelBackgroundClick = {
                                videoPlayerViewModel.setControlsVisibility(
                                    visible = false,
                                    source = "immersive-controls-panel-background",
                                )
                            },
                        )
                    }
                }
            }

            if (
                playbackLayerPolicy.showControlsLayer &&
                    playerState.activePlaybackMenu != PlaybackMenu.None
            ) {
                key(playerState.activePlaybackMenu) {
                    SpatialPanel(
                        modifier = buildImmersivePlaybackMenuModifier(
                            dashboardPanelPose = dashboardPanelPose,
                            headFollowPose = headFollowPose,
                            density = density,
                            activeMenu = playerState.activePlaybackMenu,
                            videoFormat = playerState.videoFormat,
                            stereoMode = playerState.stereoMode,
                            horizontalOffsetMeters = playerState.immersiveUiHorizontalOffsetMeters,
                        ),
                    ) {
                        XRPlaybackControls(
                            videoPlayerViewModel = videoPlayerViewModel,
                            playerState = playerState,
                            onNavigateBack = { videoPlayerViewModel.requestNavigateBack() },
                            content = XRPlaybackControlsContent.MenuOnly,
                            menuContentMaxHeight =
                                if (playerState.activePlaybackMenu == PlaybackMenu.Display) {
                                    IMMERSIVE_DISPLAY_CONTENT_MAX_HEIGHT
                                } else {
                                    null
                                },
                        )
                    }
                }
            }
        }
    } else {
        Standard2DPlayer(
            exoPlayer = exoPlayer,
            stereoMode = xrStereoMode,
            interactionPolicy = surfaceControlsInteractionPolicy,
            showControls = showControls,
            isSurfaceReady = isSurfaceReady,
            videoPlayerViewModel = videoPlayerViewModel,
            playerState = playerState,
            isHandTrackingEnabled = isHandTrackingEnabled,
            onNavigateBack = { videoPlayerViewModel.requestNavigateBack() },
            dashboardPanelPose = dashboardPanelPose,
            headFollowPose = if (enableHeadFollowIn2D && dashboardPanelPose == null) headFollowPose else null,
            panelWidth = flatPanelWidth,
            panelHeight = flatPanelHeight,
            resizePolicy = flatPanelResizePolicy,
        )
    }
}

@Composable
private fun PlayerLoadingPanel(
    errorMessage: String?,
    dashboardPanelPose: Pose?,
    panelWidth: Dp,
    panelHeight: Dp,
    resizePolicy: ResizePolicy,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current

    SpatialPanel(
        modifier = buildPanelModifierFromSavedPose(
            dashboardPanelPose = dashboardPanelPose,
            density = density,
            panelWidth = panelWidth,
            panelHeight = panelHeight,
        )
            .movable()
            .resizable(
                minimumSize = MIN_FLAT_PANEL_SIZE,
                maximumSize = MAX_FLAT_PANEL_SIZE,
                maintainAspectRatio = true,
                resizePolicy = resizePolicy,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.background,
                            colors.surface,
                        ),
                    ),
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.82f))
                    .border(
                        width = 1.dp,
                        color = colors.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(28.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (errorMessage.isNullOrBlank()) {
                    CircularProgressIndicator(color = colors.primary)
                } else {
                    Text(
                        text = errorMessage,
                        color = colors.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 36.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VrSeekPreviewSurface(
    previewPath: String?,
    videoFormat: VideoFormat,
    stereoMode: blackark.app.vr.ui.viewmodel.StereoMode,
    headPose: HeadFollowPose?,
    dashboardPanelPose: Pose?,
    density: Density,
) {
    var surface by remember { mutableStateOf<Surface?>(null) }
    var activationForward by remember { mutableStateOf<Vector3?>(null) }

    LaunchedEffect(headPose?.forward) {
        if (activationForward == null && headPose != null) {
            activationForward = headPose.forward
        }
    }

    LaunchedEffect(
        previewPath,
        headPose?.forward,
        activationForward,
        videoFormat,
        stereoMode,
        surface,
    ) {
        val path = previewPath ?: return@LaunchedEffect
        val targetSurface = surface ?: return@LaunchedEffect
        val anchor = activationForward ?: headPose?.forward ?: Vector3.Forward
        val current = headPose?.forward ?: anchor
        val yaw = normalizeRadians(viewYaw(current) - viewYaw(anchor))
        val pitch = (viewPitch(current) - viewPitch(anchor)).coerceIn(-0.75f, 0.75f)
        val bitmap = withContext(Dispatchers.Default) {
            VrSeekPreviewRenderer.render(
                sourcePath = path,
                videoFormat = videoFormat,
                stereoMode = stereoMode,
                relativeYawRadians = yaw,
                relativePitchRadians = pitch,
            )
        } ?: return@LaunchedEffect

        runCatching {
            val canvas = targetSurface.lockCanvas(null)
            try {
                canvas.drawColor(AndroidColor.BLACK)
                canvas.drawBitmap(
                    bitmap,
                    null,
                    Rect(0, 0, canvas.width, canvas.height),
                    android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG or
                            android.graphics.Paint.FILTER_BITMAP_FLAG
                    ),
                )
            } finally {
                targetSurface.unlockCanvasAndPost(canvas)
            }
        }.onFailure { error ->
            Log.v(TAG, "Failed to draw VR seek preview: ${error.message}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            surface = null
            VrSeekPreviewRenderer.clear()
        }
    }

    SpatialExternalSurface(
        modifier = buildImmersiveSeekPreviewModifier(
            dashboardPanelPose = dashboardPanelPose,
            density = density,
        ),
        stereoMode = StereoMode.SideBySide,
    ) {
        onSurfaceCreated { created -> surface = created }
        onSurfaceDestroyed { destroyed ->
            if (surface === destroyed) surface = null
        }
    }
}

private fun viewYaw(forward: Vector3): Float = atan2(forward.x, -forward.z)

private fun viewPitch(forward: Vector3): Float =
    asin(forward.y.coerceIn(-1f, 1f))

private fun normalizeRadians(value: Float): Float {
    val period = (2.0 * PI).toFloat()
    var normalized = value
    while (normalized > PI.toFloat()) normalized -= period
    while (normalized < -PI.toFloat()) normalized += period
    return normalized
}

@OptIn(UnstableApi::class)
@Composable
private fun Standard2DPlayer(
    exoPlayer: ExoPlayer?,
    stereoMode: StereoMode,
    interactionPolicy: InteractionPolicy?,
    showControls: Boolean,
    isSurfaceReady: Boolean,
    videoPlayerViewModel: VideoPlayerViewModel,
    playerState: VideoPlayerState,
    isHandTrackingEnabled: Boolean,
    onNavigateBack: () -> Unit,
    dashboardPanelPose: Pose?,
    headFollowPose: HeadFollowPose? = null,
    panelWidth: Dp,
    panelHeight: Dp,
    resizePolicy: ResizePolicy,
) {
    val density = LocalDensity.current

    if (exoPlayer != null) {
        // Recreate the XR surface when stereo layout changes to avoid renderer desync/black frames.
        key(stereoMode) {
            SpatialExternalSurface(
                modifier = buildFlatSurfaceModifier(
                    dashboardPanelPose = dashboardPanelPose,
                    headFollowPose = headFollowPose,
                    density = density,
                    panelWidth = panelWidth,
                    panelHeight = panelHeight,
                )
                    .movable()
                    .resizable(
                        minimumSize = MIN_FLAT_PANEL_SIZE,
                        maximumSize = MAX_FLAT_PANEL_SIZE,
                        maintainAspectRatio = true,
                        resizePolicy = resizePolicy,
                    ),
                stereoMode = stereoMode,
                interactionPolicy = interactionPolicy,
            ) {
                bindExoPlayerSurface(exoPlayer)

                if (isSurfaceReady) {
                    SpatialPanel(
                        modifier = SubspaceModifier.fillMaxSize(),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (playerState.subtitleCues.isNotEmpty()) {
                                SubtitleCueOverlay(
                                    cues = playerState.subtitleCues,
                                    bottomPaddingFraction =
                                        if (showControls) {
                                            SUBTITLE_CONTROLS_VISIBLE_BOTTOM_PADDING_FRACTION
                                        } else {
                                            SUBTITLE_DEFAULT_BOTTOM_PADDING_FRACTION
                                        },
                                    fontId = playerState.subtitleFontId,
                                    textSize = playerState.subtitleTextSize,
                                )
                            }

                            PlaybackScrollInputOverlay(
                                showControls = showControls,
                                inputEnabled = !showControls,
                                backgroundToggleEnabled = true,
                                controlsInputLocked = playerState.controlsInputLocked,
                                seekPreviewActive = playerState.seekPreviewActive,
                                onKeyUp = { event ->
                                    videoPlayerViewModel.dispatchPlaybackKeyEvent(event)
                                },
                                onToggleControls = {
                                    videoPlayerViewModel.setControlsVisibility(
                                        visible = !showControls,
                                        source = "2d-background-overlay",
                                    )
                                },
                                controlsAlignment = Alignment.BottomCenter,
                                controlsPadding = PaddingValues(
                                    start = 20.dp,
                                    end = 20.dp,
                                    bottom = 28.dp
                                ),
                                controlsModifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .widthIn(max = 1080.dp),
                                onHorizontalScrollDelta = { delta ->
                                    videoPlayerViewModel.handlePlaybackHorizontalScroll(delta)
                                },
                                onVerticalScrollDelta = { delta ->
                                    videoPlayerViewModel.handlePlaybackVerticalScroll(delta)
                                },
                            ) {
                                XRPlaybackControls(
                                    videoPlayerViewModel = videoPlayerViewModel,
                                    playerState = playerState,
                                    onNavigateBack = { videoPlayerViewModel.requestNavigateBack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleCueOverlay(
    cues: List<Cue>,
    bottomPaddingFraction: Float,
    fontId: String,
    textSize: SubtitleTextSize,
) {
    val fontOption = remember(fontId) { SubtitleFontCatalog.resolveOption(fontId) }
    val typeface = remember(fontOption.id) { SubtitleFontCatalog.resolveTypeface(fontOption) }
    val fallbackStyle = remember(typeface) {
        CaptionStyleCompat(
            android.graphics.Color.WHITE,
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            android.graphics.Color.BLACK,
            typeface,
        )
    }
    val renderedCues = remember(cues, fontId) {
        cues.map(::removeEmbeddedTypeface)
    }
    val textSizeFraction = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * textSize.scale

    AndroidView(
        factory = { context ->
            SubtitleView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setApplyEmbeddedStyles(true)
                setApplyEmbeddedFontSizes(false)
                setStyle(fallbackStyle)
                setFractionalTextSize(textSizeFraction)
                setBottomPaddingFraction(bottomPaddingFraction)
                isClickable = false
                isFocusable = false
            }
        },
        update = { subtitleView ->
            subtitleView.setStyle(fallbackStyle)
            subtitleView.setFractionalTextSize(textSizeFraction)
            subtitleView.setBottomPaddingFraction(bottomPaddingFraction)
            subtitleView.setCues(renderedCues)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun removeEmbeddedTypeface(cue: Cue): Cue {
    val text = cue.text ?: return cue
    if (text !is Spanned) return cue

    val styledText = SpannableString(text)
    val typefaceSpans = styledText.getSpans(
        0,
        styledText.length,
        TypefaceSpan::class.java,
    )
    if (typefaceSpans.isEmpty()) return cue

    typefaceSpans.forEach(styledText::removeSpan)
    return cue.buildUpon()
        .setText(styledText)
        .build()
}

private fun buildFlatSurfaceModifier(
    dashboardPanelPose: Pose?,
    headFollowPose: HeadFollowPose?,
    density: Density,
    panelWidth: Dp,
    panelHeight: Dp,
): SubspaceModifier {
    val baseModifier = buildPanelModifierFromSavedPose(
        dashboardPanelPose = dashboardPanelPose,
        density = density,
        panelWidth = panelWidth,
        panelHeight = panelHeight,
    )

    if (dashboardPanelPose != null) {
        return baseModifier
    }

    if (headFollowPose == null) {
        return baseModifier
    }

    val followDistanceDp = 900f
    val followHeightDp = -40f
    return baseModifier
        .offset(
            x = (headFollowPose.forward.x * followDistanceDp).dp,
            y = followHeightDp.dp,
            z = (headFollowPose.forward.z * followDistanceDp).dp,
        )
        .rotate(headFollowPose.rotation)
}

private fun buildHiddenMainPanelAnchorModifier(
): SubspaceModifier =
    SubspaceModifier
        .width(HIDDEN_MAIN_PANEL_ANCHOR_SIZE)
        .height(HIDDEN_MAIN_PANEL_ANCHOR_SIZE)
        .offset(
            x = HIDDEN_MAIN_PANEL_OFFSET,
            y = HIDDEN_MAIN_PANEL_OFFSET,
        )

private fun buildImmersiveRevealInputModifier(
    dashboardPanelPose: Pose?,
    headFollowPose: HeadFollowPose?,
    density: Density,
): SubspaceModifier {
    val baseModifier =
        SubspaceModifier
            .width(IMMERSIVE_REVEAL_PANEL_WIDTH)
            .height(IMMERSIVE_REVEAL_PANEL_HEIGHT)

    if (headFollowPose != null) {
        return baseModifier
            .offset(
                x = IMMERSIVE_REVEAL_FOLLOW_DISTANCE * headFollowPose.forward.x,
                y = IMMERSIVE_REVEAL_FOLLOW_DISTANCE * headFollowPose.forward.y,
                z = IMMERSIVE_REVEAL_FOLLOW_DISTANCE * headFollowPose.forward.z,
            )
            .rotate(headFollowPose.rotation)
    }

    if (dashboardPanelPose == null) {
        return baseModifier
    }

    return baseModifier
        .offset(
            x = with(density) {
                dashboardPanelPose.translation.x.toDp()
            } * IMMERSIVE_REVEAL_FRONT_FACTOR,
            y = with(density) { dashboardPanelPose.translation.y.toDp() },
            z = with(density) {
                dashboardPanelPose.translation.z.toDp()
            } * IMMERSIVE_REVEAL_FRONT_FACTOR,
        )
        .rotate(dashboardPanelPose.rotation)
}

private fun buildImmersiveSubtitleModifier(
    dashboardPanelPose: Pose?,
    density: Density,
    distanceMeters: Float,
    horizontalOffsetMeters: Float,
    verticalOffsetMeters: Float,
): SubspaceModifier {
    val placement = resolveImmersiveSubtitlePlacement(distanceMeters)
    val baseUpOffset = IMMERSIVE_SUBTITLE_BASE_UP_OFFSET * placement.scale
    val rotation = dashboardPanelPose?.rotation ?: Quaternion.Identity
    val baseTranslation = dashboardPanelPose?.translation ?: Vector3.Zero
    // Subspace poses report translation in pixels; user-controlled offsets are stored in meters.
    val panelOffsetDp =
        resolveImmersiveSubtitlePanelOffsetDp(
            baseTranslationPixels = baseTranslation,
            worldForward = rotation * Vector3.Forward,
            worldRight = rotation * Vector3.Right,
            worldUp = rotation * Vector3.Up,
            placement = placement,
            horizontalOffsetMeters = horizontalOffsetMeters,
            verticalOffsetMeters = verticalOffsetMeters,
            pixelsPerDp = density.density,
            dpPerMeter = 1.meters.toDp().value,
        )
    val baseModifier =
        SubspaceModifier
            .width(IMMERSIVE_SUBTITLE_PANEL_WIDTH)
            .height(IMMERSIVE_SUBTITLE_PANEL_HEIGHT)
            .scale(placement.scale)

    return baseModifier
        .offset(
            x = panelOffsetDp.x.dp,
            y = panelOffsetDp.y.dp + baseUpOffset,
            z = panelOffsetDp.z.dp,
        )
        .rotate(rotation)
}

private fun buildImmersiveControlsModifier(
    dashboardPanelPose: Pose?,
    headFollowPose: HeadFollowPose?,
    density: Density,
    stereoMode: blackark.app.vr.ui.viewmodel.StereoMode,
    horizontalOffsetMeters: Float,
): SubspaceModifier {
    val panelWidth =
        if (stereoMode == blackark.app.vr.ui.viewmodel.StereoMode.Mono) {
            IMMERSIVE_CONTROLS_PANEL_WIDTH_MONO
        } else {
            IMMERSIVE_CONTROLS_PANEL_WIDTH_STEREO
        }

    val baseModifier =
        SubspaceModifier
            .width(panelWidth)
            .height(IMMERSIVE_CONTROLS_PANEL_HEIGHT)
    val rotation = headFollowPose?.rotation ?: dashboardPanelPose?.rotation ?: Quaternion.Identity
    val horizontalOffsetDp =
        resolveImmersiveUiHorizontalOffsetDp(
            worldRight = rotation * Vector3.Right,
            horizontalOffsetMeters = horizontalOffsetMeters,
            dpPerMeter = 1.meters.toDp().value,
        )
    if (headFollowPose != null) {
        val worldUp = rotation * Vector3.Up
        return baseModifier.offset(
            x = (headFollowPose.forward.x * IMMERSIVE_CONTROLS_FOLLOW_DISTANCE.value).dp +
                (worldUp.x * IMMERSIVE_CONTROLS_DOWN_OFFSET.value).dp +
                horizontalOffsetDp.x.dp,
            y = (headFollowPose.forward.y * IMMERSIVE_CONTROLS_FOLLOW_DISTANCE.value).dp +
                (worldUp.y * IMMERSIVE_CONTROLS_DOWN_OFFSET.value).dp +
                horizontalOffsetDp.y.dp,
            z = (headFollowPose.forward.z * IMMERSIVE_CONTROLS_FOLLOW_DISTANCE.value).dp +
                (worldUp.z * IMMERSIVE_CONTROLS_DOWN_OFFSET.value).dp +
                horizontalOffsetDp.z.dp,
        ).rotate(rotation)
    }

    if (dashboardPanelPose == null) {
        return baseModifier.offset(
            x = horizontalOffsetDp.x.dp,
            y = IMMERSIVE_CONTROLS_DOWN_OFFSET + horizontalOffsetDp.y.dp,
            z = horizontalOffsetDp.z.dp,
        )
    }

    val anchoredX =
        with(density) { dashboardPanelPose.translation.x.toDp() } * IMMERSIVE_CONTROLS_FRONT_FACTOR
    val anchoredY =
        with(density) { dashboardPanelPose.translation.y.toDp() } + IMMERSIVE_CONTROLS_DOWN_OFFSET
    val anchoredZ =
        with(density) { dashboardPanelPose.translation.z.toDp() } * IMMERSIVE_CONTROLS_FRONT_FACTOR

    return baseModifier
        .offset(
            x = anchoredX + horizontalOffsetDp.x.dp,
            y = anchoredY + horizontalOffsetDp.y.dp,
            z = anchoredZ + horizontalOffsetDp.z.dp,
        )
        .rotate(rotation)
}

private fun buildImmersiveSeekPreviewModifier(
    dashboardPanelPose: Pose?,
    density: Density,
): SubspaceModifier {
    val base = SubspaceModifier
        .width(IMMERSIVE_SEEK_PREVIEW_WIDTH)
        .height(IMMERSIVE_SEEK_PREVIEW_HEIGHT)
    val rotation = dashboardPanelPose?.rotation ?: Quaternion.Identity
    val worldUp = rotation * Vector3.Up
    val upX = (worldUp.x * IMMERSIVE_SEEK_PREVIEW_UP_OFFSET.value).dp
    val upY = (worldUp.y * IMMERSIVE_SEEK_PREVIEW_UP_OFFSET.value).dp
    val upZ = (worldUp.z * IMMERSIVE_SEEK_PREVIEW_UP_OFFSET.value).dp

    if (dashboardPanelPose == null) {
        return base.offset(x = upX, y = upY, z = upZ)
    }
    return base
        .offset(
            x = with(density) { dashboardPanelPose.translation.x.toDp() } + upX,
            y = with(density) { dashboardPanelPose.translation.y.toDp() } + upY,
            z = with(density) { dashboardPanelPose.translation.z.toDp() } + upZ,
        )
        .rotate(rotation)
}

private fun buildImmersivePlaybackMenuModifier(
    dashboardPanelPose: Pose?,
    headFollowPose: HeadFollowPose?,
    density: Density,
    activeMenu: PlaybackMenu,
    videoFormat: VideoFormat,
    stereoMode: blackark.app.vr.ui.viewmodel.StereoMode,
    horizontalOffsetMeters: Float,
): SubspaceModifier {
    val menuContentWidth = activeMenu.playbackMenuWidth(videoFormat)
    val panelWidth =
        if (activeMenu == PlaybackMenu.Display) {
            menuContentWidth
        } else {
            menuContentWidth + IMMERSIVE_MENU_PANEL_PADDING
        }
    val panelHeight = resolveImmersivePlaybackMenuPanelHeight(activeMenu, videoFormat)
    val menuUpOffset =
        (IMMERSIVE_CONTROLS_PANEL_HEIGHT + panelHeight) / 2f +
            IMMERSIVE_MENU_CONTROLS_GAP
    val controlsPanelWidth =
        if (stereoMode == blackark.app.vr.ui.viewmodel.StereoMode.Mono) {
            IMMERSIVE_CONTROLS_PANEL_WIDTH_MONO
        } else {
            IMMERSIVE_CONTROLS_PANEL_WIDTH_STEREO
        }
    val menuRightOffset =
        resolveImmersivePlaybackMenuRightOffset(
            activeMenu = activeMenu,
            controlsPanelWidth = controlsPanelWidth,
            menuPanelWidth = panelWidth,
        )
    val baseModifier =
        SubspaceModifier
            .width(panelWidth)
            .height(panelHeight)
    val rotation = headFollowPose?.rotation ?: dashboardPanelPose?.rotation ?: Quaternion.Identity
    val horizontalOffsetDp =
        resolveImmersiveUiHorizontalOffsetDp(
            worldRight = rotation * Vector3.Right,
            horizontalOffsetMeters = horizontalOffsetMeters,
            dpPerMeter = 1.meters.toDp().value,
        )
    val worldUp = rotation * Vector3.Up
    val worldRight = rotation * Vector3.Right
    val worldForward = rotation * Vector3.Forward
    val menuRightX = (worldRight.x * menuRightOffset.value).dp
    val menuRightY = (worldRight.y * menuRightOffset.value).dp
    val menuRightZ = (worldRight.z * menuRightOffset.value).dp
    val menuUpX = (worldUp.x * menuUpOffset.value).dp
    val menuUpY = (worldUp.y * menuUpOffset.value).dp
    val menuUpZ = (worldUp.z * menuUpOffset.value).dp
    val menuFrontX = (-worldForward.x * IMMERSIVE_MENU_FRONT_OFFSET.value).dp
    val menuFrontY = (-worldForward.y * IMMERSIVE_MENU_FRONT_OFFSET.value).dp
    val menuFrontZ = (-worldForward.z * IMMERSIVE_MENU_FRONT_OFFSET.value).dp

    if (headFollowPose != null) {
        val controlsX =
            (headFollowPose.forward.x * IMMERSIVE_CONTROLS_FOLLOW_DISTANCE.value).dp +
                (worldUp.x * IMMERSIVE_CONTROLS_DOWN_OFFSET.value).dp
        val controlsY =
            (headFollowPose.forward.y * IMMERSIVE_CONTROLS_FOLLOW_DISTANCE.value).dp +
                (worldUp.y * IMMERSIVE_CONTROLS_DOWN_OFFSET.value).dp
        val controlsZ =
            (headFollowPose.forward.z * IMMERSIVE_CONTROLS_FOLLOW_DISTANCE.value).dp +
                (worldUp.z * IMMERSIVE_CONTROLS_DOWN_OFFSET.value).dp
        return baseModifier
            .offset(
                x = controlsX + horizontalOffsetDp.x.dp + menuRightX + menuUpX + menuFrontX,
                y = controlsY + horizontalOffsetDp.y.dp + menuRightY + menuUpY + menuFrontY,
                z = controlsZ + horizontalOffsetDp.z.dp + menuRightZ + menuUpZ + menuFrontZ,
            )
            .rotate(rotation)
    }

    if (dashboardPanelPose == null) {
        return baseModifier.offset(
            x = horizontalOffsetDp.x.dp + menuRightX + menuUpX + menuFrontX,
            y = IMMERSIVE_CONTROLS_DOWN_OFFSET + horizontalOffsetDp.y.dp +
                menuRightY + menuUpY + menuFrontY,
            z = horizontalOffsetDp.z.dp + menuRightZ + menuUpZ + menuFrontZ,
        )
    }

    val anchoredX =
        with(density) { dashboardPanelPose.translation.x.toDp() } *
            IMMERSIVE_CONTROLS_FRONT_FACTOR
    val anchoredY =
        with(density) { dashboardPanelPose.translation.y.toDp() } +
            IMMERSIVE_CONTROLS_DOWN_OFFSET
    val anchoredZ =
        with(density) { dashboardPanelPose.translation.z.toDp() } *
            IMMERSIVE_CONTROLS_FRONT_FACTOR

    return baseModifier
        .offset(
            x = anchoredX + horizontalOffsetDp.x.dp + menuRightX + menuUpX + menuFrontX,
            y = anchoredY + horizontalOffsetDp.y.dp + menuRightY + menuUpY + menuFrontY,
            z = anchoredZ + horizontalOffsetDp.z.dp + menuRightZ + menuUpZ + menuFrontZ,
        )
        .rotate(rotation)
}

internal fun resolveImmersivePlaybackMenuRightOffset(
    activeMenu: PlaybackMenu,
    controlsPanelWidth: Dp,
    menuPanelWidth: Dp,
): Dp =
    if (activeMenu == PlaybackMenu.Display) {
        ((controlsPanelWidth - menuPanelWidth) / 2f).coerceAtLeast(0.dp)
    } else {
        0.dp
    }

internal fun resolveImmersivePlaybackMenuPanelHeight(
    activeMenu: PlaybackMenu,
    videoFormat: VideoFormat,
): Dp =
    if (activeMenu == PlaybackMenu.Display && videoFormat != VideoFormat.Format2D) {
        IMMERSIVE_DISPLAY_PANEL_HEIGHT
    } else {
        activeMenu.playbackMenuEstimatedHeight(videoFormat) + IMMERSIVE_MENU_PANEL_PADDING
    }

private fun buildPanelModifierFromSavedPose(
    dashboardPanelPose: Pose?,
    density: Density,
    panelWidth: Dp,
    panelHeight: Dp,
): SubspaceModifier {
    val baseModifier =
        SubspaceModifier
            .width(panelWidth)
            .height(panelHeight)

    if (dashboardPanelPose == null) {
        return baseModifier
    }

    return baseModifier
        .offset(
            x = with(density) { dashboardPanelPose.translation.x.toDp() },
            y = with(density) { dashboardPanelPose.translation.y.toDp() },
            z = with(density) { dashboardPanelPose.translation.z.toDp() },
        )
        .rotate(dashboardPanelPose.rotation)
}

@Composable
fun ImmersivePlayer(
    exoPlayer: ExoPlayer?,
    videoFormat: VideoFormat,
    stereoMode: StereoMode,
    interactionPolicy: InteractionPolicy?,
    zoomLevel: Float = 1f,
    headLockedRotation180: Quaternion? = null,
) {
    if (exoPlayer == null) return

    when (videoFormat) {
        VideoFormat.Format180 -> {
            val hemisphereModifier =
                headLockedRotation180?.let { rotation ->
                    SubspaceModifier.rotate(rotation)
                } ?: SubspaceModifier
            key(stereoMode) {
                SpatialExternalSurfaceHemisphere(
                    modifier = hemisphereModifier.scale(zoomLevel.coerceIn(0.5f, 3f)),
                    stereoMode = stereoMode,
                    interactionPolicy = interactionPolicy,
                ) {
                    bindExoPlayerSurface(exoPlayer)
                }
            }
        }

        VideoFormat.Format360 -> {
            key(stereoMode) {
                SpatialExternalSurfaceSphere(
                    modifier = SubspaceModifier.scale(zoomLevel.coerceIn(0.5f, 3f)),
                    stereoMode = stereoMode,
                    interactionPolicy = interactionPolicy,
                ) {
                    bindExoPlayerSurface(exoPlayer)
                }
            }
        }

        VideoFormat.Format2D -> Unit
    }
}

private fun SpatialExternalSurfaceScope.bindExoPlayerSurface(exoPlayer: ExoPlayer) {
    onSurfaceCreated { surface ->
        exoPlayer.setVideoSurface(surface)
    }
    onSurfaceDestroyed { surface ->
        // Only clear the surface instance being destroyed; leave any replacement surface intact.
        exoPlayer.clearVideoSurface(surface)
    }
}
