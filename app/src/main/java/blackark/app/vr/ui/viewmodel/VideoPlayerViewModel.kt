package blackark.app.vr.ui.viewmodel

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import blackark.app.vr.data.database.entity.RecentVideo
import blackark.app.vr.data.repository.VideoDisplaySettingsRepository
import blackark.app.vr.data.repository.VideoRepository
import blackark.app.vr.network.LocalFileClient
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBConfig
import blackark.app.vr.network.SMBFileItem
import blackark.app.vr.player.AssColorSubtitleParserFactory
import blackark.app.vr.player.ExternalSubtitle
import blackark.app.vr.player.PlaybackSource
import blackark.app.vr.player.SMBDataSource
import blackark.app.vr.player.buildKoreanExternalSubtitleCandidates
import blackark.app.vr.player.configureKoreanExternalSubtitle
import blackark.app.vr.player.resolveCurrentPlaylistIndex
import blackark.app.vr.player.resolveKoreanExternalSubtitle
import blackark.app.vr.player.resolveNextPlaybackTarget
import blackark.app.vr.player.resolvePlaybackPlaylist
import blackark.app.vr.player.shouldHandlePlaybackEnded
import blackark.app.vr.utils.AppSettingsStore
import blackark.app.vr.utils.DEFAULT_IMMERSIVE_SUBTITLE_DISTANCE_METERS
import blackark.app.vr.utils.DEFAULT_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS
import blackark.app.vr.utils.DEFAULT_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS
import blackark.app.vr.utils.InferredDisplayProfile
import blackark.app.vr.utils.SubtitleFontCatalog
import blackark.app.vr.utils.ThumbnailImageLoaderProvider
import blackark.app.vr.utils.VideoFramePreviewExtractor
import blackark.app.vr.utils.VideoThumbnailFetcher
import blackark.app.vr.utils.inferDisplayProfileFromFrame
import blackark.app.vr.utils.normalizeImmersiveSubtitleVerticalOffsetMeters
import blackark.app.vr.utils.normalizeImmersiveUiHorizontalOffsetMeters
import blackark.app.vr.utils.snapImmersiveSubtitleDistanceMeters
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PLAYER_LOG_TAG = "VideoPlayerViewModel"
private const val SUBTITLE_LOG_PREFIX = "[SubtitleDebug]"
private const val PLAYBACK_CONTROL_LOG_PREFIX = "[PlaybackControlDebug]"

internal enum class ControlsVisibilityBlockReason(val logValue: String) {
    InputLock("input-lock"),
    SeekPreview("seek-preview"),
    RecentInput("recent-input"),
}

internal fun resolveControlsVisibilityBlockReason(
    targetVisible: Boolean,
    controlsInputLocked: Boolean,
    seekPreviewActive: Boolean,
    recentInputSuppressed: Boolean,
): ControlsVisibilityBlockReason? {
    if (targetVisible) return null
    return when {
        controlsInputLocked -> ControlsVisibilityBlockReason.InputLock
        seekPreviewActive -> ControlsVisibilityBlockReason.SeekPreview
        recentInputSuppressed -> ControlsVisibilityBlockReason.RecentInput
        else -> null
    }
}

private fun describeCueColors(cues: List<Cue>): String {
    val colors = cues
        .asSequence()
        .mapNotNull { it.text as? Spanned }
        .flatMap { text ->
            text.getSpans(0, text.length, ForegroundColorSpan::class.java)
                .asSequence()
        }
        .map { span -> span.foregroundColor }
        .distinct()
        .take(6)
        .map { color -> "#%08X".format(color) }
        .toList()

    return colors.ifEmpty { listOf("none") }.joinToString(",")
}

sealed class PlayerEvent {
    object NavigateBack : PlayerEvent()
}

data class VideoPlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val bufferedPercentage: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isLoading: Boolean = false,
    val error: String? = null,
    val videoFile: SMBFileItem? = null,
    val videoFormat: VideoFormat = VideoFormat.Format2D,
    val stereoMode: StereoMode = StereoMode.Mono,
    val activePlaybackMenu: PlaybackMenu = PlaybackMenu.None,
    val zoomLevel: Float = 1.0f,
    val playlist: List<SMBFileItem> = emptyList(),
    val currentPlaylistIndex: Int = -1,
    val canPlayPrevious: Boolean = false,
    val canPlayNext: Boolean = false,
    val showControls: Boolean = false,
    val controlsInputLocked: Boolean = false,
    val seekPreviewActive: Boolean = false,
    val seekPreviewTargetPositionMs: Long = 0,
    val seekPreviewThumbnailPath: String? = null,
    val subtitlesEnabled: Boolean = true,
    val subtitleFontId: String = SubtitleFontCatalog.DEFAULT_FONT_ID,
    val subtitleTextSize: SubtitleTextSize = SubtitleTextSize.Medium,
    val immersiveSubtitleDistanceMeters: Float = DEFAULT_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
    val immersiveSubtitleVerticalOffsetMeters: Float =
        DEFAULT_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS,
    val immersiveUiHorizontalOffsetMeters: Float =
        DEFAULT_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS,
    val subtitleCues: List<Cue> = emptyList(),
    val externalSubtitleFileName: String? = null,
    val audioTracks: List<PlayerTrackOption> = emptyList(),
    val textTracks: List<PlayerTrackOption> = emptyList(),
)

data class PlayerTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
)

enum class PlaybackMenu {
    None,
    Speed,
    Display,
    Volume,
}

enum class VideoFormat {
    Format2D,
    Format180,
    Format360
}

enum class StereoMode {
    Mono,
    SideBySide,
    TopBottom
}

enum class SubtitleTextSize(val scale: Float) {
    Small(0.8f),
    Medium(1.0f),
    Large(1.25f),
    ExtraLarge(1.5f),
}

@UnstableApi
class VideoPlayerViewModel(
    private val videoRepository: VideoRepository,
    private val videoDisplaySettingsRepository: VideoDisplaySettingsRepository,
) : ViewModel() {
    private enum class ControllerAxisMode {
        None,
        Horizontal,
        Vertical,
    }

    private val _state = MutableStateFlow(VideoPlayerState())
    val state: StateFlow<VideoPlayerState> = _state.asStateFlow()

    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow: StateFlow<ExoPlayer?> = _playerFlow.asStateFlow()

    private val _playerEvents = Channel<PlayerEvent>()
    val playerEvents = _playerEvents.receiveAsFlow()
    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var exoPlayer: ExoPlayer? = null
    private var currentVideoId: Long? = null
    private var pendingSaveJob: Job? = null
    private var positionTrackingJob: Job? = null
    private var audioManager: AudioManager? = null
    private var isVolumeObserverRegistered = false
    private val volumeObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                syncVolumeStateFromSystem(reason = "settings-observer")
            }
        }

    // Store context and config for playlist navigation
    private var appContext: Context? = null
    private var currentPlaybackSource: PlaybackSource? = null

    private var controllerAxisMode = ControllerAxisMode.None
    private var seekDirection = 0
    private var volumeDirection = 0
    private var seekRepeatJob: Job? = null
    private var volumeRepeatJob: Job? = null
    private var thumbnailPreviewJob: Job? = null
    private var autoDisplayInferencePending = false
    private var controlsInputLockCount = 0
    private var seekPreviewResumePlayback = false
    private var seekPreviewShowControls = false
    private var pendingThumbnailPreviewPositionMs: Long? = null
    private var renderedThumbnailPreviewPositionMs: Long? = null
    private var controllerAxisAwaitingNeutralReset = false

    private val controllerAxisEngageThreshold = 0.45f
    private val controllerAxisReleaseThreshold = 0.25f
    private val controllerAxisDominanceMargin = 0.10f
    private val controllerSeekStepMs = 7_500L
    private val controllerSeekInitialRepeatMs = 260L
    private val controllerSeekRepeatMs = 140L
    private val controllerVolumeStep = 0.04f
    private val controllerVolumeInitialRepeatMs = 220L
    private val controllerVolumeRepeatMs = 130L
    private val playbackScrollMagnitudeThreshold = 1.0f
    private val playbackScrollSeekInitialRepeatMs = 260L
    private val playbackScrollSeekRepeatMs = 140L
    private val playbackScrollHoldTimeoutMs = 240L
    private val thumbnailPreviewDebounceMs = 60L
    private val thumbnailPreviewFrameIntervalMs = 1_000L
    private var lastPlaybackHorizontalScrollAtMs = 0L
    private var lastPlaybackVerticalScrollAtMs = 0L
    private var lastPlaybackHorizontalDirection = 0
    private var lastPlaybackVerticalDirection = 0
    private var playbackHorizontalRepeatJob: Job? = null
    private var controlsAutoHideJob: Job? = null
    private var immersiveUiHorizontalOffsetApplyJob: Job? = null
    private var suppressControlsToggleUntilMs = 0L
    private var pendingInitializationPath: String? = null
    private var playerGeneration = 0L
    private var autoAdvanceInProgress = false
    private var autoAdvanceTargetPath: String? = null
    private var handledEndedGeneration: Long? = null
    private var pendingVideoFormatToPersist: VideoFormat? = null
    private var pendingStereoModeToPersist: StereoMode? = null
    private var lastHandledKeyEventTimeMs = Long.MIN_VALUE
    private var lastHandledKeyCode = Int.MIN_VALUE
    private var hasDispatchedNavigateBack = false
    private val releaseMutex = Mutex()
    // XR controllers need additional time to acquire small spatial button targets.
    private val controlsAutoHideDelayMs = 10_000L
    private val controlsToggleSuppressAfterInputMs = 300L
    private val immersiveUiHorizontalOffsetApplyDelayMs = 220L

    init {
        // Observe global key events.
        viewModelScope.launch {
            blackark.app.vr.AppState.keyEvents.collect { event ->
                dispatchPlaybackKeyEvent(event)
            }
        }

        // Observe 6DoF thumbstick axis events.
        viewModelScope.launch {
            blackark.app.vr.AppState.controllerAxisEvents.collect { event ->
                handleControllerAxisEvent(event)
            }
        }
    }

    fun setVideoFormat(format: VideoFormat) {
        val nextStereoMode =
            if (format == VideoFormat.Format2D) StereoMode.Mono else _state.value.stereoMode
        _state.value = _state.value.copy(
            videoFormat = format,
            stereoMode = nextStereoMode,
        )
        pendingVideoFormatToPersist = format
        if (format == VideoFormat.Format2D) {
            pendingStereoModeToPersist = StereoMode.Mono
        }

        autoDisplayInferencePending = false
        scheduleDisplaySettingsPersistence()
        scheduleControlsAutoHideIfNeeded()
    }

    fun setStereoMode(mode: StereoMode) {
        _state.value = _state.value.copy(stereoMode = mode)
        pendingStereoModeToPersist = mode

        autoDisplayInferencePending = false
        scheduleDisplaySettingsPersistence()
        scheduleControlsAutoHideIfNeeded()
    }

    fun toggleSubtitles() {
        val currentState = _state.value
        val nextEnabled = !currentState.subtitlesEnabled
        val player = exoPlayer

        _state.value = currentState.copy(
            subtitlesEnabled = nextEnabled,
            subtitleCues = if (nextEnabled) currentState.subtitleCues else emptyList(),
        )
        if (player != null) {
            player.trackSelectionParameters = configureKoreanExternalSubtitle(
                parameters = player.trackSelectionParameters,
                enabled = nextEnabled,
            )
        }

        Log.i(
            PLAYER_LOG_TAG,
            "$SUBTITLE_LOG_PREFIX toggled enabled=$nextEnabled " +
                    "external=${currentState.externalSubtitleFileName ?: "none"}",
        )
        player?.currentTracks?.let(::logSubtitleTrackState)
        scheduleControlsAutoHideIfNeeded()
    }

    fun recenterView() {
        blackark.app.vr.AppState.resetDashboardPanelPlacement()
        _state.value = _state.value.copy(
            zoomLevel = 1.0f,
            immersiveUiHorizontalOffsetMeters = 0.0f,
        )
        appContext?.let { context ->
            AppSettingsStore.setImmersiveUiHorizontalOffsetMeters(context, 0.0f)
        }
        Log.i(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX recenter headFollow=true horizontalOffset=0.0m",
        )
        scheduleControlsAutoHideIfNeeded()
    }

    fun selectAudioTrack(option: PlayerTrackOption) {
        selectTrack(C.TRACK_TYPE_AUDIO, option)
    }

    fun selectTextTrack(option: PlayerTrackOption) {
        selectTrack(C.TRACK_TYPE_TEXT, option)
        if (!_state.value.subtitlesEnabled) toggleSubtitles()
    }

    private fun selectTrack(trackType: Int, option: PlayerTrackOption) {
        val player = exoPlayer ?: return
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        if (group.type != trackType || option.trackIndex !in 0 until group.length) return
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(option.trackIndex))
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
        updateTrackOptions(player.currentTracks)
        scheduleControlsAutoHideIfNeeded()
    }

    private fun updateTrackOptions(tracks: Tracks) {
        fun optionsFor(type: Int): List<PlayerTrackOption> = buildList {
            tracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type != type) return@forEachIndexed
                for (trackIndex in 0 until group.length) {
                    if (!group.isTrackSupported(trackIndex)) continue
                    val format = group.getTrackFormat(trackIndex)
                    val label = format.label?.takeIf(String::isNotBlank)
                        ?: format.language?.takeIf(String::isNotBlank)
                        ?: if (type == C.TRACK_TYPE_AUDIO) {
                            "Audio ${size + 1}"
                        } else {
                            "Subtitle ${size + 1}"
                        }
                    add(
                        PlayerTrackOption(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = label,
                            selected = group.isTrackSelected(trackIndex),
                        )
                    )
                }
            }
        }
        _state.value = _state.value.copy(
            audioTracks = optionsFor(C.TRACK_TYPE_AUDIO),
            textTracks = optionsFor(C.TRACK_TYPE_TEXT),
        )
    }

    fun setSubtitleTextSize(textSize: SubtitleTextSize) {
        if (_state.value.subtitleTextSize == textSize) return

        _state.value = _state.value.copy(subtitleTextSize = textSize)
        appContext?.let { context ->
            AppSettingsStore.setSubtitleTextSize(context, textSize.name)
        }
        Log.i(
            PLAYER_LOG_TAG,
            "$SUBTITLE_LOG_PREFIX text size=${textSize.name} scale=${textSize.scale}",
        )
    }

    fun setImmersiveSubtitleDistanceMeters(distanceMeters: Float) {
        val normalizedDistance = snapImmersiveSubtitleDistanceMeters(distanceMeters)
        if (_state.value.immersiveSubtitleDistanceMeters == normalizedDistance) return

        _state.value = _state.value.copy(
            immersiveSubtitleDistanceMeters = normalizedDistance,
        )
    }

    fun persistImmersiveSubtitleDistanceMeters() {
        val distanceMeters = _state.value.immersiveSubtitleDistanceMeters
        appContext?.let { context ->
            AppSettingsStore.setImmersiveSubtitleDistanceMeters(context, distanceMeters)
        }
        Log.i(
            PLAYER_LOG_TAG,
            "$SUBTITLE_LOG_PREFIX immersive distance=${distanceMeters}m",
        )
    }

    fun setImmersiveSubtitleVerticalOffsetMeters(offsetMeters: Float) {
        val normalizedOffset = normalizeImmersiveSubtitleVerticalOffsetMeters(offsetMeters)
        if (_state.value.immersiveSubtitleVerticalOffsetMeters == normalizedOffset) return

        _state.value = _state.value.copy(
            immersiveSubtitleVerticalOffsetMeters = normalizedOffset,
        )
    }

    fun persistImmersiveSubtitleVerticalOffsetMeters() {
        val offsetMeters = _state.value.immersiveSubtitleVerticalOffsetMeters
        appContext?.let { context ->
            AppSettingsStore.setImmersiveSubtitleVerticalOffsetMeters(context, offsetMeters)
        }
        Log.i(
            PLAYER_LOG_TAG,
            "$SUBTITLE_LOG_PREFIX immersive vertical offset=${offsetMeters}m",
        )
    }

    fun setImmersiveUiHorizontalOffsetMeters(offsetMeters: Float) {
        val normalizedOffset = normalizeImmersiveUiHorizontalOffsetMeters(offsetMeters)
        if (_state.value.immersiveUiHorizontalOffsetMeters == normalizedOffset) return

        _state.value = _state.value.copy(
            immersiveUiHorizontalOffsetMeters = normalizedOffset,
        )
    }

    fun persistImmersiveUiHorizontalOffsetMeters() {
        val offsetMeters = _state.value.immersiveUiHorizontalOffsetMeters
        appContext?.let { context ->
            AppSettingsStore.setImmersiveUiHorizontalOffsetMeters(context, offsetMeters)
        }
        Log.i(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX immersive horizontal offset=${offsetMeters}m",
        )
    }

    fun applyImmersiveUiHorizontalOffsetMetersAfterInteraction(offsetMeters: Float) {
        val normalizedOffset = normalizeImmersiveUiHorizontalOffsetMeters(offsetMeters)
        immersiveUiHorizontalOffsetApplyJob?.cancel()
        immersiveUiHorizontalOffsetApplyJob =
            viewModelScope.launch {
                delay(immersiveUiHorizontalOffsetApplyDelayMs)
                setImmersiveUiHorizontalOffsetMeters(normalizedOffset)
                persistImmersiveUiHorizontalOffsetMeters()
            }.also { job ->
                job.invokeOnCompletion {
                    if (immersiveUiHorizontalOffsetApplyJob === job) {
                        immersiveUiHorizontalOffsetApplyJob = null
                    }
                }
            }
    }

    fun togglePlaybackMenu(menu: PlaybackMenu) {
        val currentState = _state.value
        val nextMenu = if (currentState.activePlaybackMenu == menu) PlaybackMenu.None else menu
        Log.i(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX menu request=$menu " +
                "from=${currentState.activePlaybackMenu} to=$nextMenu " +
                "controls=${currentState.showControls}->true",
        )
        _state.value = currentState.copy(
            showControls = true,
            activePlaybackMenu = nextMenu,
        )
        if (nextMenu == PlaybackMenu.None) {
            scheduleControlsAutoHideIfNeeded()
        } else {
            cancelControlsAutoHide()
        }
    }

    fun dismissPlaybackMenu() {
        if (_state.value.activePlaybackMenu == PlaybackMenu.None) {
            return
        }
        Log.i(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX menu dismiss=${_state.value.activePlaybackMenu}",
        )
        _state.value = _state.value.copy(activePlaybackMenu = PlaybackMenu.None)
        scheduleControlsAutoHideIfNeeded()
    }

    fun beginControlsInputLock() {
        controlsInputLockCount += 1
        Log.d(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX input-lock begin count=$controlsInputLockCount",
        )
        cancelControlsAutoHide()

        val currentState = _state.value
        if (currentState.controlsInputLocked && currentState.showControls) {
            return
        }

        _state.value = currentState.copy(
            showControls = true,
            controlsInputLocked = true,
        )
    }

    fun endControlsInputLock() {
        controlsInputLockCount = (controlsInputLockCount - 1).coerceAtLeast(0)
        val stillLocked = controlsInputLockCount > 0
        Log.d(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX input-lock end count=$controlsInputLockCount",
        )
        if (_state.value.controlsInputLocked == stillLocked) {
            if (!stillLocked) {
                suppressControlsToggleUntilMs =
                    SystemClock.elapsedRealtime() + controlsToggleSuppressAfterInputMs
                scheduleControlsAutoHideIfNeeded()
            }
            return
        }

        _state.value = _state.value.copy(controlsInputLocked = stillLocked)
        if (!stillLocked) {
            suppressControlsToggleUntilMs =
                SystemClock.elapsedRealtime() + controlsToggleSuppressAfterInputMs
            scheduleControlsAutoHideIfNeeded()
        }
    }

    private suspend fun persistPendingDisplaySettings() {
        if (pendingVideoFormatToPersist == null && pendingStereoModeToPersist == null) {
            return
        }

        val currentPath = _state.value.videoFile?.path
        if (currentPath.isNullOrBlank()) {
            Log.w(
                "VideoPlayerViewModel",
                "Cannot persist display settings yet: video path unavailable"
            )
            return
        }

        try {
            videoDisplaySettingsRepository.saveDisplaySettings(
                filePath = currentPath,
                videoFormat = _state.value.videoFormat.name,
                stereoMode = _state.value.stereoMode.name,
            )
            pendingVideoFormatToPersist = null
            pendingStereoModeToPersist = null
            Log.d(
                "VideoPlayerViewModel",
                "Saved display settings for path=$currentPath format=${_state.value.videoFormat.name} stereo=${_state.value.stereoMode.name}"
            )
        } catch (e: Exception) {
            Log.e("VideoPlayerViewModel", "Failed to save display settings: ${e.message}", e)
        }
    }

    fun toggleControls() {
        val currentState = _state.value
        if (currentState.controlsInputLocked || currentState.seekPreviewActive) {
            Log.i(
                PLAYER_LOG_TAG,
                "$PLAYBACK_CONTROL_LOG_PREFIX toggle blocked source=key-or-controller " +
                    "reason=input-lock-or-seek",
            )
            return
        }
        if (
            currentState.showControls &&
            SystemClock.elapsedRealtime() < suppressControlsToggleUntilMs
        ) {
            Log.i(
                PLAYER_LOG_TAG,
                "$PLAYBACK_CONTROL_LOG_PREFIX toggle blocked source=key-or-controller " +
                    "reason=recent-input",
            )
            return
        }
        setControlsVisibility(
            visible = !currentState.showControls,
            source = "key-or-controller",
        )
    }

    fun setControlsVisibility(
        visible: Boolean,
        source: String = "direct",
    ) {
        val currentState = _state.value
        val recentInputSuppressed =
            currentState.showControls &&
                SystemClock.elapsedRealtime() < suppressControlsToggleUntilMs
        val blockReason = resolveControlsVisibilityBlockReason(
            targetVisible = visible,
            controlsInputLocked = currentState.controlsInputLocked,
            seekPreviewActive = currentState.seekPreviewActive,
            recentInputSuppressed = recentInputSuppressed,
        )
        Log.i(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX toggle source=$source target=$visible " +
                "current=${currentState.showControls} menu=${currentState.activePlaybackMenu} " +
                "lock=${currentState.controlsInputLocked} seek=${currentState.seekPreviewActive}",
        )
        if (blockReason != null) {
            Log.i(
                PLAYER_LOG_TAG,
                "$PLAYBACK_CONTROL_LOG_PREFIX toggle blocked source=$source " +
                    "reason=${blockReason.logValue} target=$visible",
            )
            return
        }
        _state.value = currentState.copy(
            showControls = visible,
            activePlaybackMenu = if (visible) currentState.activePlaybackMenu else PlaybackMenu.None,
        )
        if (visible) {
            scheduleControlsAutoHideIfNeeded()
        } else {
            cancelControlsAutoHide()
        }
        Log.i(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX toggle applied source=$source visible=$visible " +
                "menu=${_state.value.activePlaybackMenu}",
        )
    }

    private fun resetControlsInputLock() {
        controlsInputLockCount = 0
        suppressControlsToggleUntilMs = 0L
        if (_state.value.controlsInputLocked) {
            _state.value = _state.value.copy(controlsInputLocked = false)
        }
    }

    private fun clampSeekPosition(positionMs: Long): Long {
        val player = exoPlayer
        val duration = player?.duration ?: 0L
        val upperBound = if (duration > 0L) duration else Long.MAX_VALUE
        return positionMs.coerceIn(0L, upperBound)
    }

    private fun beginSeekPreviewSessionIfNeeded() {
        val player = exoPlayer ?: return
        val currentState = _state.value
        if (currentState.seekPreviewActive) {
            return
        }

        seekPreviewResumePlayback = currentState.isPlaying
        seekPreviewShowControls = currentState.showControls
        renderedThumbnailPreviewPositionMs = null

        player.pause()

        val startPosition = clampSeekPosition(player.currentPosition.coerceAtLeast(0L))
        _state.value = currentState.copy(
            showControls = true,
            seekPreviewActive = true,
            seekPreviewTargetPositionMs = startPosition,
            seekPreviewThumbnailPath = null,
        )
        cancelControlsAutoHide()
        enqueueThumbnailPreview(startPosition)
    }

    private fun enqueueThumbnailPreview(targetPositionMs: Long) {
        val context = appContext ?: return
        val videoPath = _state.value.videoFile?.path ?: return
        val normalizedTargetPositionMs = normalizeThumbnailPreviewPosition(targetPositionMs)

        if (
            normalizedTargetPositionMs == renderedThumbnailPreviewPositionMs &&
            !_state.value.seekPreviewThumbnailPath.isNullOrBlank()
        ) {
            return
        }

        pendingThumbnailPreviewPositionMs = normalizedTargetPositionMs
        if (thumbnailPreviewJob?.isActive == true) {
            return
        }

        thumbnailPreviewJob = viewModelScope.launch {
            while (isActive) {
                val queuedTarget = pendingThumbnailPreviewPositionMs ?: break
                pendingThumbnailPreviewPositionMs = null
                delay(thumbnailPreviewDebounceMs)

                val nextTarget = pendingThumbnailPreviewPositionMs ?: queuedTarget
                pendingThumbnailPreviewPositionMs = null

                val previewPath =
                    VideoFramePreviewExtractor.extractPreviewFrame(
                        context = context,
                        videoPath = videoPath,
                        targetPositionMs = nextTarget,
                        preserveVrProjection = _state.value.videoFormat != VideoFormat.Format2D,
                    )

                if (!isActive) {
                    break
                }

                val latestState = _state.value
                if (
                    !latestState.seekPreviewActive
                ) {
                    break
                }

                if (previewPath != null) {
                    renderedThumbnailPreviewPositionMs = nextTarget
                    _state.value = latestState.copy(seekPreviewThumbnailPath = previewPath)
                }
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (thumbnailPreviewJob === job) {
                    thumbnailPreviewJob = null
                }
            }
        }
    }

    private fun cancelThumbnailPreview() {
        pendingThumbnailPreviewPositionMs = null
        renderedThumbnailPreviewPositionMs = null
        thumbnailPreviewJob?.cancel()
        thumbnailPreviewJob = null
    }

    private fun normalizeThumbnailPreviewPosition(positionMs: Long): Long {
        val clampedPosition = clampSeekPosition(positionMs)
        val intervalMs = thumbnailPreviewFrameIntervalMs
        if (intervalMs <= 0L) {
            return clampedPosition
        }

        val roundedPosition =
            ((clampedPosition + (intervalMs / 2)) / intervalMs) * intervalMs
        return clampSeekPosition(roundedPosition)
    }

    private fun updateSeekPreviewTargetByStep(direction: Int) {
        beginSeekPreviewSessionIfNeeded()
        val currentState = _state.value
        if (!currentState.seekPreviewActive) {
            return
        }

        val delta = if (direction > 0) controllerSeekStepMs else -controllerSeekStepMs
        val newTarget = clampSeekPosition(currentState.seekPreviewTargetPositionMs + delta)
        if (newTarget == currentState.seekPreviewTargetPositionMs) {
            return
        }

        _state.value = currentState.copy(
            seekPreviewTargetPositionMs = newTarget,
            showControls = true,
        )
        enqueueThumbnailPreview(newTarget)
        cancelControlsAutoHide()
    }

    private fun updateSeekPreviewTargetPosition(positionMs: Long) {
        beginSeekPreviewSessionIfNeeded()
        val currentState = _state.value
        if (!currentState.seekPreviewActive) {
            return
        }

        val newTarget = clampSeekPosition(positionMs)
        if (newTarget == currentState.seekPreviewTargetPositionMs) {
            return
        }

        _state.value = currentState.copy(
            seekPreviewTargetPositionMs = newTarget,
            showControls = true,
        )
        enqueueThumbnailPreview(newTarget)
        cancelControlsAutoHide()
    }

    private fun finishSeekPreviewSession(
        commit: Boolean,
        restorePlayback: Boolean,
    ): Boolean? {
        val currentState = _state.value
        if (!currentState.seekPreviewActive) {
            return null
        }

        val player = exoPlayer
        val targetPosition = clampSeekPosition(currentState.seekPreviewTargetPositionMs)
        val resumePlayback = seekPreviewResumePlayback

        cancelSeekRepeat()
        cancelThumbnailPreview()

        if (commit && player != null) {
            player.seekTo(targetPosition)
        }

        if (restorePlayback && player != null) {
            if (resumePlayback) {
                player.play()
            } else {
                player.pause()
            }
        }

        _state.value = currentState.copy(
            showControls = seekPreviewShowControls,
            seekPreviewActive = false,
            seekPreviewTargetPositionMs = 0L,
            seekPreviewThumbnailPath = null,
        )
        seekPreviewResumePlayback = false
        seekPreviewShowControls = false

        scheduleControlsAutoHideIfNeeded()
        return resumePlayback
    }

    fun handlePlaybackHorizontalScroll(delta: Float) {
        if (
            exoPlayer == null ||
            abs(delta) < playbackScrollMagnitudeThreshold ||
            _state.value.controlsInputLocked ||
            _state.value.seekPreviewActive
        ) {
            return
        }

        if (revealControlsIfHidden()) {
            return
        }

        val direction = if (delta > 0f) 1 else -1
        lastPlaybackHorizontalScrollAtMs = SystemClock.elapsedRealtime()

        if (direction != lastPlaybackHorizontalDirection) {
            lastPlaybackHorizontalDirection = direction
            applyPlaybackHorizontalScroll(direction)
            startPlaybackHorizontalRepeat(direction)
            return
        }

        if (playbackHorizontalRepeatJob?.isActive != true) {
            startPlaybackHorizontalRepeat(direction)
        }
    }

    fun handlePlaybackVerticalScroll(delta: Float) {
        if (_state.value.controlsInputLocked || _state.value.seekPreviewActive) {
            return
        }
        if (revealControlsIfHidden()) {
            return
        }
        handlePlaybackScrollDelta(
            delta = delta,
            repeatWindowMs = controllerVolumeRepeatMs,
            previousDirection = lastPlaybackVerticalDirection,
            previousTimestampMs = lastPlaybackVerticalScrollAtMs,
            apply = { direction ->
                lastPlaybackVerticalDirection = direction
                lastPlaybackVerticalScrollAtMs = SystemClock.elapsedRealtime()
                if (direction > 0) {
                    stepVolume(direction = 1)
                } else {
                    stepVolume(direction = -1)
                }
            },
        )
    }

    fun requestNavigateBack() {
        if (hasDispatchedNavigateBack) {
            return
        }
        finishSeekPreviewSession(commit = false, restorePlayback = false)
        hasDispatchedNavigateBack = true
        viewModelScope.launch {
            _playerEvents.send(PlayerEvent.NavigateBack)
        }
    }

    private fun handlePlaybackScrollDelta(
        delta: Float,
        repeatWindowMs: Long,
        previousDirection: Int,
        previousTimestampMs: Long,
        apply: (Int) -> Unit,
    ) {
        if (exoPlayer == null) {
            return
        }

        if (abs(delta) < playbackScrollMagnitudeThreshold) {
            return
        }

        val direction = if (delta > 0f) 1 else -1
        val now = SystemClock.elapsedRealtime()
        val canApply =
            direction != previousDirection || now - previousTimestampMs >= repeatWindowMs

        if (!canApply) {
            return
        }

        apply(direction)
    }

    private fun applyPlaybackHorizontalScroll(direction: Int) {
        if (direction > 0) {
            seekBackward()
        } else {
            seekForward()
        }
    }

    private fun startPlaybackHorizontalRepeat(direction: Int) {
        cancelPlaybackHorizontalRepeat()
        playbackHorizontalRepeatJob = viewModelScope.launch {
            delay(playbackScrollSeekInitialRepeatMs)
            while (isActive && lastPlaybackHorizontalDirection == direction) {
                val elapsedSinceLastScroll =
                    SystemClock.elapsedRealtime() - lastPlaybackHorizontalScrollAtMs
                if (elapsedSinceLastScroll > playbackScrollHoldTimeoutMs) {
                    break
                }

                applyPlaybackHorizontalScroll(direction)
                delay(playbackScrollSeekRepeatMs)
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (playbackHorizontalRepeatJob === job) {
                    playbackHorizontalRepeatJob = null
                }
            }
        }
    }

    private fun cancelPlaybackHorizontalRepeat() {
        playbackHorizontalRepeatJob?.cancel()
        playbackHorizontalRepeatJob = null
    }

    private fun handleControllerAxisEvent(event: blackark.app.vr.ControllerAxisEvent) {
        if (exoPlayer == null) {
            return
        }

        val x = event.x.coerceIn(-1f, 1f)
        val y = event.y.coerceIn(-1f, 1f)
        val isNeutral =
            abs(x) <= controllerAxisReleaseThreshold && abs(y) <= controllerAxisReleaseThreshold

        if (controllerAxisAwaitingNeutralReset) {
            if (isNeutral) {
                controllerAxisAwaitingNeutralReset = false
                resetControllerAxisState()
            }
            return
        }

        if (!_state.value.showControls) {
            val shouldRevealControls =
                abs(x) >= controllerAxisEngageThreshold || abs(y) >= controllerAxisEngageThreshold
            if (shouldRevealControls) {
                revealControlsIfHidden()
                resetControllerAxisState()
                controllerAxisAwaitingNeutralReset = true
            }
            return
        }

        val absX = abs(x)
        val absY = abs(y)
        val previousAxisMode = controllerAxisMode

        controllerAxisMode = when {
            absX >= controllerAxisEngageThreshold &&
                    absX >= absY + controllerAxisDominanceMargin -> ControllerAxisMode.Horizontal

            absY >= controllerAxisEngageThreshold &&
                    absY >= absX + controllerAxisDominanceMargin -> ControllerAxisMode.Vertical

            absX <= controllerAxisReleaseThreshold &&
                    absY <= controllerAxisReleaseThreshold -> ControllerAxisMode.None

            else -> controllerAxisMode
        }

        when (controllerAxisMode) {
            ControllerAxisMode.Horizontal -> {
                cancelVolumeRepeat()
                volumeDirection = 0
                handleSeekFromAxis(x)
            }

            ControllerAxisMode.Vertical -> {
                if (previousAxisMode == ControllerAxisMode.Horizontal) {
                    finishSeekPreviewSession(commit = true, restorePlayback = true)
                }
                cancelSeekRepeat()
                seekDirection = 0
                handleVolumeFromAxis(y)
            }

            ControllerAxisMode.None -> {
                if (previousAxisMode == ControllerAxisMode.Horizontal) {
                    finishSeekPreviewSession(commit = true, restorePlayback = true)
                }
                resetControllerAxisState()
            }
        }
    }

    private fun handleSeekFromAxis(xAxis: Float) {
        val desiredDirection = when {
            xAxis >= controllerAxisEngageThreshold -> 1
            xAxis <= -controllerAxisEngageThreshold -> -1
            abs(xAxis) <= controllerAxisReleaseThreshold -> 0
            else -> seekDirection
        }

        if (desiredDirection == 0) {
            seekDirection = 0
            cancelSeekRepeat()
            finishSeekPreviewSession(commit = true, restorePlayback = true)
            return
        }

        if (seekDirection != 0 && desiredDirection != seekDirection) {
            finishSeekPreviewSession(commit = true, restorePlayback = false)
        }

        if (desiredDirection != seekDirection) {
            seekDirection = desiredDirection
            applySeekStep(desiredDirection)
            startSeekRepeat(desiredDirection)
            return
        }

        if (seekRepeatJob?.isActive != true) {
            startSeekRepeat(seekDirection)
        }
    }

    private fun applySeekStep(direction: Int) {
        updateSeekPreviewTargetByStep(direction)
    }

    fun beginSeekPreview() {
        beginSeekPreviewSessionIfNeeded()
    }

    fun updateSeekPreviewPosition(positionMs: Long) {
        updateSeekPreviewTargetPosition(positionMs)
    }

    fun commitSeekPreview(positionMs: Long? = null) {
        positionMs?.let { updateSeekPreviewTargetPosition(it) }
        finishSeekPreviewSession(commit = true, restorePlayback = true)
    }

    private fun startSeekRepeat(direction: Int) {
        cancelSeekRepeat()
        seekRepeatJob = viewModelScope.launch {
            delay(controllerSeekInitialRepeatMs)
            while (isActive &&
                controllerAxisMode == ControllerAxisMode.Horizontal &&
                seekDirection == direction
            ) {
                applySeekStep(direction)
                delay(controllerSeekRepeatMs)
            }
        }
    }

    private fun cancelSeekRepeat() {
        seekRepeatJob?.cancel()
        seekRepeatJob = null
    }

    private fun handleVolumeFromAxis(yAxis: Float) {
        val desiredDirection = when {
            yAxis <= -controllerAxisEngageThreshold -> 1
            yAxis >= controllerAxisEngageThreshold -> -1
            abs(yAxis) <= controllerAxisReleaseThreshold -> 0
            else -> volumeDirection
        }

        if (desiredDirection == 0) {
            volumeDirection = 0
            cancelVolumeRepeat()
            return
        }

        if (desiredDirection != volumeDirection) {
            volumeDirection = desiredDirection
            applyVolumeStep(desiredDirection)
            startVolumeRepeat(desiredDirection)
            return
        }

        if (volumeRepeatJob?.isActive != true) {
            startVolumeRepeat(volumeDirection)
        }
    }

    private fun startVolumeRepeat(direction: Int) {
        cancelVolumeRepeat()
        volumeRepeatJob = viewModelScope.launch {
            delay(controllerVolumeInitialRepeatMs)
            while (isActive &&
                controllerAxisMode == ControllerAxisMode.Vertical &&
                volumeDirection == direction
            ) {
                applyVolumeStep(direction)
                delay(controllerVolumeRepeatMs)
            }
        }
    }

    private fun cancelVolumeRepeat() {
        volumeRepeatJob?.cancel()
        volumeRepeatJob = null
    }

    private fun applyVolumeStep(direction: Int) {
        if (exoPlayer == null) return
        val delta = if (direction > 0) controllerVolumeStep else -controllerVolumeStep
        val newVolume = (_state.value.volume + delta).coerceIn(0f, 1f)
        setVolume(newVolume)
    }

    private fun stepVolume(direction: Int, step: Float = 0.05f) {
        if (exoPlayer == null) return
        val delta = if (direction > 0) step else -step
        val newVolume = (_state.value.volume + delta).coerceIn(0f, 1f)
        setVolume(newVolume)
    }

    private fun resetControllerAxisState() {
        controllerAxisMode = ControllerAxisMode.None
        seekDirection = 0
        volumeDirection = 0
        controllerAxisAwaitingNeutralReset = false
        cancelSeekRepeat()
        cancelVolumeRepeat()
        cancelThumbnailPreview()
    }

    private fun revealControlsIfHidden(): Boolean {
        if (!_state.value.showControls) {
            _state.value = _state.value.copy(showControls = true)
            scheduleControlsAutoHideIfNeeded()
            return true
        }
        scheduleControlsAutoHideIfNeeded()
        return false
    }

    private fun cancelControlsAutoHide() {
        controlsAutoHideJob?.cancel()
        controlsAutoHideJob = null
    }

    private fun keepControlsVisibleWhileNotPlaying() {
        cancelControlsAutoHide()
        val currentState = _state.value
        if (!currentState.showControls) {
            _state.value = currentState.copy(showControls = true)
        }
    }

    private fun scheduleControlsAutoHideIfNeeded() {
        cancelControlsAutoHide()

        val currentState = _state.value
        if (
            !currentState.showControls ||
            !currentState.isPlaying ||
            currentState.activePlaybackMenu != PlaybackMenu.None ||
            currentState.controlsInputLocked ||
            currentState.seekPreviewActive
        ) {
            return
        }

        Log.d(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX auto-hide scheduled delayMs=$controlsAutoHideDelayMs",
        )
        controlsAutoHideJob = viewModelScope.launch {
            delay(controlsAutoHideDelayMs)
            val latestState = _state.value
            if (latestState.showControls && latestState.activePlaybackMenu == PlaybackMenu.None) {
                _state.value = latestState.copy(showControls = false)
                Log.i(
                    PLAYER_LOG_TAG,
                    "$PLAYBACK_CONTROL_LOG_PREFIX auto-hide applied visible=false",
                )
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (controlsAutoHideJob === job) {
                    controlsAutoHideJob = null
                }
            }
        }
    }

    fun dispatchPlaybackKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action != android.view.KeyEvent.ACTION_UP) {
            return false
        }
        if (lastHandledKeyEventTimeMs == event.eventTime && lastHandledKeyCode == event.keyCode) {
            return true
        }

        val handled = when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_A,
            android.view.KeyEvent.KEYCODE_BUTTON_R2,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                toggleControls()
                true
            }

            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (!revealControlsIfHidden()) {
                    togglePlayPause()
                }
                true
            }

            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (!revealControlsIfHidden()) {
                    play()
                }
                true
            }

            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (!revealControlsIfHidden()) {
                    pause()
                }
                true
            }

            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
                if (revealControlsIfHidden()) {
                    true
                } else {
                    seekBackward()
                    true
                }
            }

            android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
                if (revealControlsIfHidden()) {
                    true
                } else {
                    seekForward()
                    true
                }
            }

            android.view.KeyEvent.KEYCODE_DPAD_UP,
            android.view.KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> {
                if (revealControlsIfHidden()) {
                    true
                } else {
                    stepVolume(direction = 1)
                    true
                }
            }

            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> {
                if (revealControlsIfHidden()) {
                    true
                } else {
                    stepVolume(direction = -1)
                    true
                }
            }

            android.view.KeyEvent.KEYCODE_BUTTON_B,
            android.view.KeyEvent.KEYCODE_BACK -> {
                requestNavigateBack()
                true
            }

            else -> false
        }

        if (handled) {
            lastHandledKeyEventTimeMs = event.eventTime
            lastHandledKeyCode = event.keyCode
        }

        return handled
    }

    fun initializePlayer(
        context: Context,
        playbackSource: PlaybackSource,
        videoFile: SMBFileItem
    ): Boolean {
        val requestedPath = videoFile.path
        val currentState = _state.value
        val isSameVideoAlreadyActive =
            currentState.videoFile?.path == requestedPath &&
                    (currentState.isLoading || exoPlayer != null || _playerFlow.value != null)
        if (pendingInitializationPath == requestedPath || isSameVideoAlreadyActive) {
            Log.d(
                PLAYER_LOG_TAG,
                "$PLAYBACK_CONTROL_LOG_PREFIX initialization ignored file=${videoFile.name}",
            )
            return false
        }

        pendingInitializationPath = requestedPath
        val initializationGeneration = ++playerGeneration
        Log.i(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX initialization generation=$initializationGeneration " +
                "file=${videoFile.name}",
        )
        this.appContext = context.applicationContext
        this.currentPlaybackSource = playbackSource
        audioManager =
            context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        registerVolumeObserverIfNeeded()
        syncVolumeStateFromSystem(reason = "initializePlayer-start")

        viewModelScope.launch {
            try {
                // Release previous player first so its final position is saved to the correct video ID.
                releaseCurrentPlayer(resetUiState = false)
                if (initializationGeneration != playerGeneration) {
                    Log.i(
                        PLAYER_LOG_TAG,
                        "$PLAYBACK_CONTROL_LOG_PREFIX initialization stale " +
                            "generation=$initializationGeneration active=$playerGeneration",
                    )
                    return@launch
                }
                pendingInitializationPath = requestedPath
                currentVideoId = null
                pendingVideoFormatToPersist = null
                pendingStereoModeToPersist = null
                autoDisplayInferencePending = false

                // Check for saved video settings
                val savedVideo = videoRepository.getVideoByPath(videoFile.path)
                val savedDisplaySettings = videoDisplaySettingsRepository.getByPath(videoFile.path)

                // Use saved settings if available, otherwise default to 2D/Mono
                val initialStereoMode = if (savedDisplaySettings != null) {
                    try {
                        StereoMode.valueOf(savedDisplaySettings.stereoMode)
                    } catch (e: Exception) {
                        StereoMode.Mono
                    }
                } else {
                    StereoMode.Mono
                }

                val initialVideoFormat = if (savedDisplaySettings != null) {
                    try {
                        VideoFormat.valueOf(savedDisplaySettings.videoFormat)
                    } catch (e: Exception) {
                        VideoFormat.Format2D
                    }
                } else {
                    VideoFormat.Format2D
                }

                val initialSubtitleFontId = SubtitleFontCatalog.normalizePersistedId(
                    AppSettingsStore.getSubtitleFontId(context)
                )
                val initialSubtitleTextSize = AppSettingsStore.getSubtitleTextSize(context)
                    ?.let { savedName ->
                        SubtitleTextSize.entries.firstOrNull { it.name == savedName }
                    }
                    ?: SubtitleTextSize.Medium
                val initialImmersiveSubtitleDistanceMeters =
                    AppSettingsStore.getImmersiveSubtitleDistanceMeters(context)
                val initialImmersiveSubtitleVerticalOffsetMeters =
                    AppSettingsStore.getImmersiveSubtitleVerticalOffsetMeters(context)
                val initialImmersiveUiHorizontalOffsetMeters =
                    AppSettingsStore.getImmersiveUiHorizontalOffsetMeters(context)

                autoDisplayInferencePending =
                    savedDisplaySettings == null ||
                            (
                                    savedDisplaySettings.videoFormat == VideoFormat.Format2D.name &&
                                            savedDisplaySettings.stereoMode == StereoMode.Mono.name
                                    )

                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                    videoFile = videoFile,
                    stereoMode = initialStereoMode,
                    videoFormat = initialVideoFormat,
                    activePlaybackMenu = PlaybackMenu.None,
                    playlist = emptyList(),
                    currentPlaylistIndex = -1,
                    canPlayPrevious = false,
                    canPlayNext = false,
                    showControls = false,
                    controlsInputLocked = false,
                    seekPreviewActive = false,
                    seekPreviewTargetPositionMs = 0L,
                    seekPreviewThumbnailPath = null,
                    subtitlesEnabled = true,
                    subtitleFontId = initialSubtitleFontId,
                    subtitleTextSize = initialSubtitleTextSize,
                    immersiveSubtitleDistanceMeters = initialImmersiveSubtitleDistanceMeters,
                    immersiveSubtitleVerticalOffsetMeters =
                        initialImmersiveSubtitleVerticalOffsetMeters,
                    immersiveUiHorizontalOffsetMeters = initialImmersiveUiHorizontalOffsetMeters,
                    subtitleCues = emptyList(),
                    externalSubtitleFileName = null,
                    audioTracks = emptyList(),
                    textTracks = emptyList(),
                )
                viewModelScope.launch {
                    VideoFramePreviewExtractor.prepareVideo(
                        context = context.applicationContext,
                        videoPath = videoFile.path,
                    )
                }

                // Save to recent videos immediately to ensure we have an ID for updates.
                saveToRecentVideos(videoFile, playbackSource, savedVideo)

                val siblingFiles = loadSiblingFiles(videoFile, playbackSource)
                val listedExternalSubtitle = siblingFiles
                    ?.let { files ->
                        resolveKoreanExternalSubtitle(
                            videoFileName = videoFile.name,
                            siblingFiles = files,
                        )
                    }
                val externalSubtitle = listedExternalSubtitle
                    ?: when (playbackSource) {
                        is PlaybackSource.Smb -> loadSmbExternalSubtitleFallback(
                            videoFile = videoFile,
                            smbConfig = playbackSource.config,
                        )

                        is PlaybackSource.Local -> loadLocalExternalSubtitleFallback(
                            videoFile = videoFile,
                            playbackSource = playbackSource,
                        )
                    }
                _state.value = _state.value.copy(
                    externalSubtitleFileName = externalSubtitle?.file?.name,
                )
                val siblingSubtitleNames = siblingFiles.orEmpty()
                    .asSequence()
                    .filter { !it.isDirectory }
                    .map { it.name }
                    .filter { name ->
                        name.endsWith(".srt", ignoreCase = true) ||
                                name.endsWith(".ass", ignoreCase = true)
                    }
                    .toList()
                Log.i(
                    PLAYER_LOG_TAG,
                    "$SUBTITLE_LOG_PREFIX discovery video=${videoFile.name} " +
                            "siblingListing=${siblingFiles != null} " +
                            "subtitleFiles=${siblingSubtitleNames.ifEmpty { listOf("none") }}",
                )
                if (externalSubtitle != null) {
                    Log.i(
                        PLAYER_LOG_TAG,
                        "$SUBTITLE_LOG_PREFIX selected " +
                                "source=${if (listedExternalSubtitle != null) "listing" else "direct-probe"} " +
                                "file=${externalSubtitle.file.name} " +
                                "mime=${externalSubtitle.mimeType} " +
                                "scheme=${externalSubtitle.file.path.toUri().scheme}",
                    )
                } else {
                    val baseName = videoFile.name.substringBeforeLast('.', videoFile.name)
                    Log.w(
                        PLAYER_LOG_TAG,
                        "$SUBTITLE_LOG_PREFIX no matching sidecar; expected=" +
                                "$baseName.ko.ass or $baseName.ko.srt",
                    )
                }

                // Create ExoPlayer instance with larger buffer for SMB streaming
                // Use 1MB allocation size to match SMB buffer
                val allocator = androidx.media3.exoplayer.upstream.DefaultAllocator(
                    /* trimOnReset= */ true,
                    /* individualAllocationSize= */ 1024 * 1024 * 10  // 10MB chunks
                )

                val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setAllocator(allocator)
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 15000,  // 15 seconds minimum buffer
                        /* maxBufferMs = */
                        50000, // 50 seconds maximum buffer
                        /* bufferForPlaybackMs = */
                        2500,  // Start playback after 2.5 seconds
                        /* bufferForPlaybackAfterRebufferMs = */
                        5000  // Resume after 5 seconds on rebuffer
                    )
                    .build()

                exoPlayer = ExoPlayer.Builder(context)
                    .setLoadControl(loadControl)
                    .build().apply {
                        volume = 1.0f
                        if (externalSubtitle != null) {
                            trackSelectionParameters = configureKoreanExternalSubtitle(
                                parameters = trackSelectionParameters,
                                enabled = true,
                            )
                        }
                        // Set up player listener
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (initializationGeneration != playerGeneration) return
                                when (playbackState) {
                                    Player.STATE_READY -> {
                                        _state.value = _state.value.copy(
                                            isLoading = false,
                                            duration = this@apply.duration
                                        )
                                    }

                                    Player.STATE_BUFFERING -> {
                                        _state.value = _state.value.copy(isLoading = true)
                                    }

                                    Player.STATE_ENDED -> {
                                        _state.value = _state.value.copy(isPlaying = false)
                                        handlePlaybackEnded(
                                            listenerGeneration = initializationGeneration,
                                            endedFile = videoFile,
                                        )
                                    }

                                    Player.STATE_IDLE -> {
                                        _state.value = _state.value.copy(isLoading = false)
                                    }
                                }
                            }

                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                if (initializationGeneration != playerGeneration) return
                                _state.value = _state.value.copy(isPlaying = isPlaying)
                                if (isPlaying) {
                                    scheduleControlsAutoHideIfNeeded()
                                } else if (
                                    !autoAdvanceInProgress &&
                                        this@apply.playbackState != Player.STATE_ENDED
                                ) {
                                    keepControlsVisibleWhileNotPlaying()
                                }
                            }

                            override fun onCues(cueGroup: CueGroup) {
                                if (initializationGeneration != playerGeneration) return
                                val subtitlesEnabled = _state.value.subtitlesEnabled
                                Log.d(
                                    PLAYER_LOG_TAG,
                                    "$SUBTITLE_LOG_PREFIX cues count=${cueGroup.cues.size} " +
                                            "timeUs=${cueGroup.presentationTimeUs} " +
                                            "enabled=$subtitlesEnabled " +
                                            "colors=${describeCueColors(cueGroup.cues)}",
                                )
                                _state.value = _state.value.copy(
                                    subtitleCues =
                                        if (subtitlesEnabled) cueGroup.cues else emptyList(),
                                )
                            }

                            override fun onTracksChanged(tracks: Tracks) {
                                if (initializationGeneration != playerGeneration) return
                                logSubtitleTrackState(tracks)
                                updateTrackOptions(tracks)
                                if (!autoDisplayInferencePending) {
                                    return
                                }

                                var detectedTrackStereoMode: StereoMode? = null
                                var inferredDisplayProfile: InferredDisplayProfile? = null
                                for (trackGroup in tracks.groups) {
                                    if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                                        for (i in 0 until trackGroup.length) {
                                            val format = trackGroup.getTrackFormat(i)
                                            val stereoMode = format.stereoMode

                                            when (stereoMode) {
                                                C.STEREO_MODE_LEFT_RIGHT,
                                                C.STEREO_MODE_STEREO_MESH -> {
                                                    detectedTrackStereoMode = StereoMode.SideBySide
                                                }

                                                C.STEREO_MODE_TOP_BOTTOM -> {
                                                    detectedTrackStereoMode = StereoMode.TopBottom
                                                }

                                                C.STEREO_MODE_MONO -> {
                                                    detectedTrackStereoMode = StereoMode.Mono
                                                }
                                            }

                                            if (inferredDisplayProfile == null) {
                                                inferredDisplayProfile =
                                                    inferDisplayProfileFromFrame(
                                                        width = format.width,
                                                        height = format.height,
                                                    )
                                            }

                                            if (detectedTrackStereoMode != null &&
                                                inferredDisplayProfile != null
                                            ) {
                                                break
                                            }
                                        }
                                    }
                                    if (detectedTrackStereoMode != null &&
                                        inferredDisplayProfile != null
                                    ) {
                                        break
                                    }
                                }

                                val currentState = _state.value
                                var nextVideoFormat = currentState.videoFormat
                                var nextStereoMode = currentState.stereoMode

                                inferredDisplayProfile?.toVideoFormatOrNull()
                                    ?.let { inferredFormat ->
                                        if (nextVideoFormat == VideoFormat.Format2D &&
                                            nextStereoMode == StereoMode.Mono
                                        ) {
                                            nextVideoFormat = inferredFormat
                                        }
                                    }

                                if (detectedTrackStereoMode != null) {
                                    nextStereoMode = detectedTrackStereoMode
                                } else {
                                    inferredDisplayProfile?.toStereoModeOrNull()
                                        ?.let { inferredStereoMode ->
                                            if (nextVideoFormat == VideoFormat.Format180 &&
                                                nextStereoMode == StereoMode.Mono
                                            ) {
                                                nextStereoMode = inferredStereoMode
                                            }
                                        }
                                }

                                if (nextVideoFormat != currentState.videoFormat ||
                                    nextStereoMode != currentState.stereoMode
                                ) {
                                    applyAutoDetectedDisplaySettings(
                                        videoFormat = nextVideoFormat,
                                        stereoMode = nextStereoMode,
                                    )
                                }
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                if (initializationGeneration != playerGeneration) return
                                Log.e(
                                    PLAYER_LOG_TAG,
                                    "$SUBTITLE_LOG_PREFIX player error code=${error.errorCodeName}",
                                    error,
                                )
                                _state.value = _state.value.copy(
                                    isLoading = false,
                                    error = error.message ?: "Playback error occurred",
                                    showControls = true,
                                    activePlaybackMenu = PlaybackMenu.None,
                                )
                            }
                        })
                    }
                syncVolumeStateFromSystem(reason = "initializePlayer-player-created")

                val dataSourceFactory = when (playbackSource) {
                    is PlaybackSource.Smb -> SMBDataSource.Factory(playbackSource.config)
                    is PlaybackSource.Local -> DefaultDataSource.Factory(context)
                }

                // videoFile.path is already a complete SMB URL from jcifs (e.g., smb://192.168.1.105:445/downloads/file.mp4)
                val uri = videoFile.path.toUri()

                val mediaItem = buildMediaItem(
                    videoUri = uri,
                    externalSubtitle = externalSubtitle,
                )

                val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
                    .setSubtitleParserFactory(AssColorSubtitleParserFactory())
                    .createMediaSource(mediaItem)

                // Set media source and prepare
                exoPlayer?.setMediaSource(mediaSource)
                exoPlayer?.prepare()

                // Check for saved progress and resume from last position
                // savedVideo is already fetched above
                val resumePosition = savedVideo?.lastPosition ?: 0L

                if (resumePosition > 0 && savedVideo != null && savedVideo.duration > 0) {
                    // Only resume if we're not near the end (within 5% of duration)
                    val progressPercent = (resumePosition.toFloat() / savedVideo.duration.toFloat())
                    if (progressPercent < 0.95f) {
                        exoPlayer?.seekTo(resumePosition)
                    }
                }

                exoPlayer?.playWhenReady = true

                // Expose player to UI
                _playerFlow.value = exoPlayer

                // Start position tracking
                startPositionTracking()

                // Reuse the sibling listing used for subtitle discovery to avoid a second network query.
                if (siblingFiles != null) {
                    updatePlaylistFromSiblingFiles(videoFile, siblingFiles)
                }

            } catch (e: Exception) {
                Log.e(
                    PLAYER_LOG_TAG,
                    "$SUBTITLE_LOG_PREFIX player initialization failed",
                    e,
                )
                if (initializationGeneration == playerGeneration) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to initialize player",
                        showControls = true,
                        activePlaybackMenu = PlaybackMenu.None,
                    )
                }
            } finally {
                if (pendingInitializationPath == requestedPath) {
                    pendingInitializationPath = null
                }
                if (autoAdvanceTargetPath == requestedPath) {
                    autoAdvanceTargetPath = null
                    autoAdvanceInProgress = false
                    Log.i(
                        PLAYER_LOG_TAG,
                        "$PLAYBACK_CONTROL_LOG_PREFIX auto-advance guard released " +
                            "target=${videoFile.name}",
                    )
                }
            }
        }
        return true
    }

    private fun buildMediaItem(
        videoUri: android.net.Uri,
        externalSubtitle: ExternalSubtitle?,
    ): MediaItem {
        val builder = MediaItem.Builder().setUri(videoUri)
        if (externalSubtitle != null) {
            Log.i(
                PLAYER_LOG_TAG,
                "$SUBTITLE_LOG_PREFIX attaching MediaItem sidecar " +
                        "file=${externalSubtitle.file.name} mime=${externalSubtitle.mimeType}",
            )
            val subtitleConfiguration =
                MediaItem.SubtitleConfiguration.Builder(externalSubtitle.file.path.toUri())
                    .setMimeType(externalSubtitle.mimeType)
                    .setLanguage("ko")
                    .setLabel("한국어")
                    .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            builder.setSubtitleConfigurations(listOf(subtitleConfiguration))
        }
        return builder.build()
    }

    private fun logSubtitleTrackState(tracks: Tracks) {
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (textGroups.isEmpty()) {
            Log.w(
                PLAYER_LOG_TAG,
                "$SUBTITLE_LOG_PREFIX tracks changed: no text track groups",
            )
            return
        }

        textGroups.forEachIndexed { groupIndex, group ->
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                Log.i(
                    PLAYER_LOG_TAG,
                    "$SUBTITLE_LOG_PREFIX track group=$groupIndex index=$trackIndex " +
                            "selected=${group.isTrackSelected(trackIndex)} " +
                            "supported=${group.isTrackSupported(trackIndex)} " +
                            "id=${format.id ?: "none"} language=${format.language ?: "none"} " +
                            "label=${format.label ?: "none"} mime=${format.sampleMimeType ?: "none"} " +
                            "selectionFlags=${format.selectionFlags} roleFlags=${format.roleFlags}",
                )
            }
        }
    }

    private fun applyAutoDetectedDisplaySettings(
        videoFormat: VideoFormat,
        stereoMode: StereoMode,
    ) {
        val currentState = _state.value
        autoDisplayInferencePending = false

        if (currentState.videoFormat == videoFormat && currentState.stereoMode == stereoMode) {
            return
        }

        _state.value = currentState.copy(
            videoFormat = videoFormat,
            stereoMode = stereoMode,
        )

        if (currentState.videoFormat != videoFormat) {
            pendingVideoFormatToPersist = videoFormat
        }
        if (currentState.stereoMode != stereoMode) {
            pendingStereoModeToPersist = stereoMode
        }

        scheduleDisplaySettingsPersistence()
    }

    private fun scheduleDisplaySettingsPersistence() {
        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            persistPendingDisplaySettings()
        }
    }

    private suspend fun refreshPlaylist(
        currentFile: SMBFileItem,
        playbackSource: PlaybackSource,
    ): Pair<List<SMBFileItem>, Int>? {
        val siblingFiles = loadSiblingFiles(currentFile, playbackSource) ?: return null
        return updatePlaylistFromSiblingFiles(currentFile, siblingFiles)
    }

    private suspend fun loadSiblingFiles(
        currentFile: SMBFileItem,
        playbackSource: PlaybackSource,
    ): List<SMBFileItem>? {
        return when (playbackSource) {
            is PlaybackSource.Smb -> loadSmbSiblingFiles(currentFile, playbackSource.config)
            is PlaybackSource.Local -> loadLocalSiblingFiles(currentFile, playbackSource)
        }
    }

    private suspend fun loadSmbSiblingFiles(
        currentFile: SMBFileItem,
        smbConfig: SMBConfig,
    ): List<SMBFileItem>? {
        return try {
            val relativeParentPath = resolveRelativeParentPath(currentFile.path, smbConfig)
            Log.d(PLAYER_LOG_TAG, "Fetching sibling files for path: $relativeParentPath")

            val client = SMBClient(smbConfig)
            val connectResult = client.connect(verifyRootAccess = false)
            if (connectResult.isFailure) {
                Log.w(
                    PLAYER_LOG_TAG,
                    "$SUBTITLE_LOG_PREFIX sibling connect failed: " +
                            connectResult.exceptionOrNull()?.message,
                )
                return null
            }

            try {
                val listResult = client.listFiles(relativeParentPath)
                if (listResult.isFailure) {
                    Log.w(
                        PLAYER_LOG_TAG,
                        "$PLAYBACK_CONTROL_LOG_PREFIX sibling listing failed " +
                                "path=$relativeParentPath: " +
                                listResult.exceptionOrNull()?.message,
                    )
                    return null
                }

                listResult.getOrNull().orEmpty()
            } finally {
                client.disconnect()
            }
        } catch (e: Exception) {
            Log.e(
                PLAYER_LOG_TAG,
                "$SUBTITLE_LOG_PREFIX sibling listing threw an exception",
                e,
            )
            null
        }
    }

    private suspend fun loadSmbExternalSubtitleFallback(
        videoFile: SMBFileItem,
        smbConfig: SMBConfig,
    ): ExternalSubtitle? {
        val candidates = buildKoreanExternalSubtitleCandidates(videoFile)
        if (candidates.isEmpty()) return null

        Log.i(
            PLAYER_LOG_TAG,
            "$SUBTITLE_LOG_PREFIX direct probe candidates=${candidates.map { it.file.name }}",
        )
        val client = SMBClient(smbConfig)
        val connectResult = client.connect(verifyRootAccess = false)
        if (connectResult.isFailure) {
            Log.w(
                PLAYER_LOG_TAG,
                "$SUBTITLE_LOG_PREFIX direct probe connect failed: " +
                        connectResult.exceptionOrNull()?.message,
            )
            return null
        }

        try {
            for (candidate in candidates) {
                val fileResult = client.getFileInfo(candidate.file.path)
                val resolvedFile = fileResult.getOrNull()
                if (resolvedFile != null && !resolvedFile.isDirectory) {
                    Log.i(
                        PLAYER_LOG_TAG,
                        "$SUBTITLE_LOG_PREFIX direct probe found file=${resolvedFile.name}",
                    )
                    return candidate.copy(file = resolvedFile)
                }

                Log.d(
                    PLAYER_LOG_TAG,
                    "$SUBTITLE_LOG_PREFIX direct probe unavailable file=${candidate.file.name} " +
                            "reason=${fileResult.exceptionOrNull()?.message ?: "not a file"}",
                )
            }
        } finally {
            client.disconnect()
        }

        Log.w(
            PLAYER_LOG_TAG,
            "$SUBTITLE_LOG_PREFIX direct probe found no matching file",
        )
        return null
    }

    private suspend fun loadLocalExternalSubtitleFallback(
        videoFile: SMBFileItem,
        playbackSource: PlaybackSource.Local,
    ): ExternalSubtitle? {
        val context = appContext ?: return null
        val rootTreeUri = playbackSource.rootTreeUri
            ?: AppSettingsStore.getLocalStorageTreeUri(context)
            ?: return null
        val candidates = buildKoreanExternalSubtitleCandidates(videoFile)
        if (candidates.isEmpty()) return null

        Log.i(
            PLAYER_LOG_TAG,
            "$SUBTITLE_LOG_PREFIX local direct probe " +
                    "candidates=${candidates.map { it.file.name }}",
        )
        val client = LocalFileClient(context, rootTreeUri)
        val connectResult = client.connect()
        if (connectResult.isFailure) {
            Log.w(
                PLAYER_LOG_TAG,
                "$SUBTITLE_LOG_PREFIX local direct probe connect failed: " +
                        connectResult.exceptionOrNull()?.message,
            )
            return null
        }

        for (candidate in candidates) {
            val fileResult = client.getFileInfo(candidate.file.path)
            val resolvedFile = fileResult.getOrNull()
            if (resolvedFile != null && !resolvedFile.isDirectory) {
                Log.i(
                    PLAYER_LOG_TAG,
                    "$SUBTITLE_LOG_PREFIX local direct probe found file=${resolvedFile.name}",
                )
                client.disconnect()
                return candidate.copy(file = resolvedFile)
            }

            Log.d(
                PLAYER_LOG_TAG,
                "$SUBTITLE_LOG_PREFIX local direct probe unavailable " +
                        "file=${candidate.file.name} " +
                        "reason=${fileResult.exceptionOrNull()?.message ?: "not a file"}",
            )
        }

        client.disconnect()
        Log.w(
            PLAYER_LOG_TAG,
            "$SUBTITLE_LOG_PREFIX local direct probe found no matching file",
        )
        return null
    }

    private suspend fun loadLocalSiblingFiles(
        currentFile: SMBFileItem,
        playbackSource: PlaybackSource.Local,
    ): List<SMBFileItem>? {
        return try {
            val context = appContext ?: return null
            val rootTreeUri = playbackSource.rootTreeUri
                ?: AppSettingsStore.getLocalStorageTreeUri(context)
                ?: return null

            val parentPath = LocalFileClient.resolveParentDirectoryUri(
                rootTreeUri = rootTreeUri,
                childDocumentUri = currentFile.path,
            ) ?: ""

            val client = LocalFileClient(context, rootTreeUri)
            val connectResult = client.connect()
            if (connectResult.isFailure) {
                Log.w(
                    "VideoPlayerViewModel",
                    "Failed to connect local storage while fetching sibling files: ${connectResult.exceptionOrNull()?.message}"
                )
                return null
            }

            val listResult = client.listFiles(parentPath)
            client.disconnect()

            if (listResult.isFailure) {
                Log.w(
                    "VideoPlayerViewModel",
                    "Failed to list local sibling files: ${listResult.exceptionOrNull()?.message}"
                )
                return null
            }

            listResult.getOrNull().orEmpty()
        } catch (e: Exception) {
            Log.e("VideoPlayerViewModel", "Failed to fetch local sibling files: ${e.message}", e)
            null
        }
    }

    private fun updatePlaylistFromSiblingFiles(
        currentFile: SMBFileItem,
        siblingFiles: List<SMBFileItem>,
    ): Pair<List<SMBFileItem>, Int> {
        val snapshot = resolvePlaybackPlaylist(currentFile, siblingFiles)

        _state.value = _state.value.copy(
            playlist = snapshot.files,
            currentPlaylistIndex = snapshot.currentIndex,
            canPlayPrevious = snapshot.canPlayPrevious,
            canPlayNext = snapshot.canPlayNext,
        )

        Log.i(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX playlist size=${snapshot.files.size} " +
                    "currentIndex=${snapshot.currentIndex} " +
                    "previous=${snapshot.canPlayPrevious} next=${snapshot.canPlayNext}",
        )

        return snapshot.files to snapshot.currentIndex
    }

    private fun resolveRelativeParentPath(currentPath: String, smbConfig: SMBConfig): String {
        return try {
            val uriPath = java.net.URI(currentPath).path.orEmpty().trimStart('/')
            val withoutShare = if (smbConfig.shareName.isNotEmpty()) {
                when {
                    uriPath.equals(smbConfig.shareName, ignoreCase = true) -> ""
                    uriPath.startsWith("${smbConfig.shareName}/", ignoreCase = true) ->
                        uriPath.substring(smbConfig.shareName.length + 1)

                    else -> uriPath
                }
            } else {
                uriPath
            }

            withoutShare.substringBeforeLast('/', "")
        } catch (_: Exception) {
            // Fallback path parsing for unusual SMB URLs.
            val parentPath = currentPath.substringBeforeLast('/', "")
            parentPath
                .replace(
                    "smb://${smbConfig.serverAddress}:${smbConfig.port}/${smbConfig.shareName}/",
                    ""
                )
                .replace("smb://${smbConfig.serverAddress}/${smbConfig.shareName}/", "")
                .removePrefix("/")
        }
    }

    private suspend fun ensurePlaylistReady(): Pair<List<SMBFileItem>, Int>? {
        val currentFile = _state.value.videoFile ?: return null
        val playbackSource = currentPlaybackSource ?: return null

        val currentPlaylist = _state.value.playlist
        val currentIndex = resolveCurrentPlaylistIndex(currentPlaylist, currentFile)

        if (currentPlaylist.isNotEmpty() && currentIndex >= 0) {
            if (currentIndex != _state.value.currentPlaylistIndex) {
                _state.value = _state.value.copy(
                    currentPlaylistIndex = currentIndex,
                    canPlayPrevious = currentIndex > 0,
                    canPlayNext = currentIndex < currentPlaylist.lastIndex,
                )
            }
            return currentPlaylist to currentIndex
        }

        return refreshPlaylist(currentFile, playbackSource)
    }

    private fun handlePlaybackEnded(
        listenerGeneration: Long,
        endedFile: SMBFileItem,
    ) {
        if (
            !shouldHandlePlaybackEnded(
                listenerGeneration = listenerGeneration,
                currentGeneration = playerGeneration,
                autoAdvanceInProgress = autoAdvanceInProgress,
                endedGenerationAlreadyHandled = handledEndedGeneration == listenerGeneration,
            )
        ) {
            Log.i(
                PLAYER_LOG_TAG,
                "$PLAYBACK_CONTROL_LOG_PREFIX auto-advance ignored " +
                    "listenerGeneration=$listenerGeneration activeGeneration=$playerGeneration " +
                    "inProgress=$autoAdvanceInProgress source=${endedFile.name}",
            )
            return
        }

        handledEndedGeneration = listenerGeneration
        autoAdvanceInProgress = true
        viewModelScope.launch {
            val playlistResult = ensurePlaylistReady()
            if (
                listenerGeneration != playerGeneration ||
                    _state.value.videoFile?.path != endedFile.path
            ) {
                finishAutoAdvanceWithoutTransition(
                    reason = "stale-ended-callback",
                    showControls = false,
                )
                return@launch
            }

            if (playlistResult == null) {
                finishAutoAdvanceWithoutTransition(reason = "playlist-unavailable")
                return@launch
            }

            val (playlist, currentIndex) = playlistResult
            val nextFile = resolveNextPlaybackTarget(playlist, currentIndex)
            if (nextFile == null) {
                Log.i(
                    PLAYER_LOG_TAG,
                    "$PLAYBACK_CONTROL_LOG_PREFIX auto-advance boundary " +
                        "source=${endedFile.name} index=$currentIndex size=${playlist.size}",
                )
                finishAutoAdvanceWithoutTransition(reason = "last-item")
                return@launch
            }

            val started = transitionToPlaylistTarget(
                playlist = playlist,
                currentIndex = currentIndex,
                targetIndex = currentIndex + 1,
                reason = "auto-ended",
                isAutoAdvance = true,
            )
            if (!started) {
                finishAutoAdvanceWithoutTransition(reason = "initialization-rejected")
            }
        }
    }

    private fun finishAutoAdvanceWithoutTransition(
        reason: String,
        showControls: Boolean = true,
    ) {
        autoAdvanceTargetPath = null
        autoAdvanceInProgress = false
        Log.i(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX auto-advance finished reason=$reason",
        )
        if (showControls) {
            keepControlsVisibleWhileNotPlaying()
        }
    }

    private fun transitionToPlaylistTarget(
        playlist: List<SMBFileItem>,
        currentIndex: Int,
        targetIndex: Int,
        reason: String,
        isAutoAdvance: Boolean = false,
    ): Boolean {
        val targetFile = playlist.getOrNull(targetIndex) ?: return false
        val context = appContext ?: return false
        val playbackSource = currentPlaybackSource ?: return false
        val sourceFile = playlist.getOrNull(currentIndex) ?: _state.value.videoFile

        Log.i(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX transition reason=$reason " +
                "source=${sourceFile?.name ?: "unknown"} target=${targetFile.name} " +
                "from=$currentIndex to=$targetIndex",
        )
        _state.value = _state.value.copy(currentPlaylistIndex = targetIndex)
        if (isAutoAdvance) {
            autoAdvanceTargetPath = targetFile.path
        }

        val started = initializePlayer(context, playbackSource, targetFile)
        if (!started && isAutoAdvance) {
            autoAdvanceTargetPath = null
            autoAdvanceInProgress = false
        }
        return started
    }

    fun playNextVideo() {
        viewModelScope.launch {
            val playlistResult = ensurePlaylistReady()
            if (playlistResult == null) {
                Log.w(PLAYER_LOG_TAG, "$PLAYBACK_CONTROL_LOG_PREFIX next unavailable playlist")
                return@launch
            }
            val (playlist, currentIndex) = playlistResult
            if (currentIndex < 0 || currentIndex >= playlist.lastIndex) {
                Log.i(
                    PLAYER_LOG_TAG,
                    "$PLAYBACK_CONTROL_LOG_PREFIX next boundary index=$currentIndex size=${playlist.size}",
                )
                return@launch
            }

            val nextIndex = currentIndex + 1
            transitionToPlaylistTarget(
                playlist = playlist,
                currentIndex = currentIndex,
                targetIndex = nextIndex,
                reason = "manual-next",
            )
        }
    }

    fun playPreviousVideo() {
        viewModelScope.launch {
            val playlistResult = ensurePlaylistReady()
            if (playlistResult == null) {
                Log.w(PLAYER_LOG_TAG, "$PLAYBACK_CONTROL_LOG_PREFIX previous unavailable playlist")
                return@launch
            }
            val (playlist, currentIndex) = playlistResult
            if (currentIndex <= 0 || currentIndex >= playlist.size) {
                Log.i(
                    PLAYER_LOG_TAG,
                    "$PLAYBACK_CONTROL_LOG_PREFIX previous boundary " +
                            "index=$currentIndex size=${playlist.size}",
                )
                return@launch
            }

            val prevIndex = currentIndex - 1
            transitionToPlaylistTarget(
                playlist = playlist,
                currentIndex = currentIndex,
                targetIndex = prevIndex,
                reason = "manual-previous",
            )
        }
    }

    private suspend fun saveToRecentVideos(
        videoFile: SMBFileItem,
        playbackSource: PlaybackSource,
        existingVideo: RecentVideo?
    ) {
        val serverAddress = when (playbackSource) {
            is PlaybackSource.Smb -> playbackSource.config.serverAddress
            is PlaybackSource.Local -> LocalFileClient.LOCAL_STORAGE_ADDRESS
        }
        val shareName = when (playbackSource) {
            is PlaybackSource.Smb -> playbackSource.config.shareName
            is PlaybackSource.Local -> playbackSource.rootTreeUri.orEmpty()
        }

        if (existingVideo != null) {
            // Update existing history row while preserving resume state.
            val updatedVideo = existingVideo.copy(
                fileName = videoFile.name,
                serverAddress = serverAddress,
                shareName = shareName,
                lastPlayed = System.currentTimeMillis(),
            )
            videoRepository.updateVideo(updatedVideo)
            currentVideoId = existingVideo.id
            Log.d(
                "VideoPlayerViewModel",
                "Updated existing video record (ID: ${existingVideo.id})"
            )
        } else {
            // Create new video entry
            val recentVideo = RecentVideo(
                fileName = videoFile.name,
                filePath = videoFile.path,
                serverAddress = serverAddress,
                shareName = shareName,
                lastPlayed = System.currentTimeMillis(),
                lastPosition = 0,
                duration = 0,
            )
            currentVideoId = videoRepository.insertVideo(recentVideo)
            Log.d("VideoPlayerViewModel", "Created new video record (ID: $currentVideoId)")
        }
    }

    private fun startPositionTracking() {
        positionTrackingJob?.cancel()
        positionTrackingJob = viewModelScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _state.value = _state.value.copy(
                        currentPosition = player.currentPosition,
                        duration = player.duration,
                        bufferedPercentage = player.bufferedPercentage
                    )

                    // Save position and duration every 5 seconds
                    currentVideoId?.let { videoId ->
                        if (player.currentPosition % 5000 < 500) {
                            // Update only playback state to avoid overwriting other fields (like format/stereo)
                            val duration = if (player.duration > 0) player.duration else 0L
                            videoRepository.updatePlaybackState(
                                videoId = videoId,
                                position = player.currentPosition,
                                duration = duration,
                                timestamp = System.currentTimeMillis()
                            )
                        }
                    }
                }
                delay(500)
            }
        }
    }

    fun getExoPlayer(): ExoPlayer? = exoPlayer

    fun play() {
        finishSeekPreviewSession(commit = false, restorePlayback = false)
        exoPlayer?.play()
        scheduleControlsAutoHideIfNeeded()
    }

    fun pause() {
        finishSeekPreviewSession(commit = false, restorePlayback = false)
        keepControlsVisibleWhileNotPlaying()
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        val resumePlayback = finishSeekPreviewSession(commit = false, restorePlayback = false)
        exoPlayer?.seekTo(positionMs)
        when (resumePlayback) {
            true -> exoPlayer?.play()
            false -> exoPlayer?.pause()
            null -> Unit
        }
        scheduleControlsAutoHideIfNeeded()
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
        scheduleControlsAutoHideIfNeeded()
    }

    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)

        val manager = resolveAudioManager()
        val maxVolume = manager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

        if (manager == null || maxVolume <= 0) {
            exoPlayer?.volume = clampedVolume
            _state.value = _state.value.copy(volume = clampedVolume)
            scheduleControlsAutoHideIfNeeded()
            return
        }

        val targetVolume = (clampedVolume * maxVolume.toFloat()).roundToInt().coerceIn(0, maxVolume)
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        exoPlayer?.volume = 1.0f
        syncVolumeStateFromSystem(reason = "setVolume")
        scheduleControlsAutoHideIfNeeded()
    }

    fun setZoomLevel(zoomLevel: Float) {
        _state.value = _state.value.copy(zoomLevel = zoomLevel.coerceIn(0.5f, 3.0f))
    }

    fun adjustZoom(delta: Float) {
        setZoomLevel(_state.value.zoomLevel + delta)
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekForward() {
        skipForward()
    }

    fun seekBackward() {
        skipBackward()
    }

    fun skipForward(ms: Long = 10000) {
        val resumePlayback = finishSeekPreviewSession(commit = false, restorePlayback = false)
        exoPlayer?.let { player ->
            val duration = player.duration
            val upperBound = if (duration > 0) duration else Long.MAX_VALUE
            val currentPosition = player.currentPosition.coerceAtLeast(0L)
            val newPosition = (currentPosition + ms).coerceAtMost(upperBound)
            player.seekTo(newPosition)
        }
        when (resumePlayback) {
            true -> exoPlayer?.play()
            false -> exoPlayer?.pause()
            null -> Unit
        }
        scheduleControlsAutoHideIfNeeded()
    }

    fun skipBackward(ms: Long = 10000) {
        val resumePlayback = finishSeekPreviewSession(commit = false, restorePlayback = false)
        exoPlayer?.let { player ->
            val currentPosition = player.currentPosition.coerceAtLeast(0L)
            val newPosition = (currentPosition - ms).coerceAtLeast(0L)
            player.seekTo(newPosition)
        }
        when (resumePlayback) {
            true -> exoPlayer?.play()
            false -> exoPlayer?.pause()
            null -> Unit
        }
        scheduleControlsAutoHideIfNeeded()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun retry(context: Context) {
        val currentFile = _state.value.videoFile
        val playbackSource = currentPlaybackSource ?: return
        if (currentFile != null) {
            initializePlayer(context, playbackSource, currentFile)
        }
    }

    private suspend fun releaseCurrentPlayer(resetUiState: Boolean) = releaseMutex.withLock {
        pendingInitializationPath = null
        positionTrackingJob?.cancel()
        positionTrackingJob = null
        finishSeekPreviewSession(commit = false, restorePlayback = false)
        val previewVideoPath = _state.value.videoFile?.path

        // Wait for any pending format/stereo mode saves before releasing.
        pendingSaveJob?.join()

        val player = exoPlayer
        val videoId = currentVideoId
        val resumeVideoPath = _state.value.videoFile?.path
        var resumePositionMs = 0L
        var resumeDurationMs = 0L
        if (player != null && videoId != null) {
            val currentPosition = player.currentPosition
            val duration = if (player.duration > 0) player.duration else 0L
            resumePositionMs = currentPosition
            resumeDurationMs = duration
            try {
                videoRepository.updatePlaybackState(
                    videoId = videoId,
                    position = currentPosition,
                    duration = duration,
                    timestamp = System.currentTimeMillis()
                )
            } catch (_: Exception) {
            }
        }

        player?.release()
        scheduleResumeFrameThumbnail(
            videoPath = resumeVideoPath,
            positionMs = resumePositionMs,
            durationMs = resumeDurationMs,
        )
        exoPlayer = null
        _playerFlow.value = null
        _state.value = _state.value.copy(
            subtitlesEnabled = true,
            subtitleCues = emptyList(),
            externalSubtitleFileName = null,
        )
        pendingSaveJob = null
        cancelSeekRepeat()
        cancelVolumeRepeat()
        cancelPlaybackHorizontalRepeat()
        cancelThumbnailPreview()
        cancelControlsAutoHide()
        resetControlsInputLock()
        VideoFramePreviewExtractor.clearPreparedVideo(previewVideoPath)
        lastPlaybackHorizontalDirection = 0
        lastPlaybackHorizontalScrollAtMs = 0L

        if (resetUiState) {
            unregisterVolumeObserver()
            audioManager = null
            _state.value = VideoPlayerState()
            currentVideoId = null
            currentPlaybackSource = null
            pendingVideoFormatToPersist = null
            pendingStereoModeToPersist = null
            autoDisplayInferencePending = false
            hasDispatchedNavigateBack = false
            seekPreviewResumePlayback = false
            seekPreviewShowControls = false
            resetControllerAxisState()
        }
    }

    suspend fun releasePlayerBeforeNavigateBack() {
        invalidatePlaybackGeneration(reason = "navigate-back")
        releaseCurrentPlayer(resetUiState = true)
    }

    fun releasePlayerAsync() {
        invalidatePlaybackGeneration(reason = "release-async")
        viewModelScope.launch {
            releaseCurrentPlayer(resetUiState = true)
        }
    }

    override fun onCleared() {
        releasePlayerOnCleared()
        super.onCleared()
    }

    private fun releasePlayerOnCleared() {
        invalidatePlaybackGeneration(reason = "view-model-cleared")
        pendingInitializationPath = null
        positionTrackingJob?.cancel()
        positionTrackingJob = null
        pendingSaveJob?.cancel()
        pendingSaveJob = null

        val player = exoPlayer
        val videoId = currentVideoId
        val previewVideoPath = _state.value.videoFile?.path
        val currentPosition = player?.currentPosition ?: 0L
        val duration = player?.duration?.takeIf { it > 0L } ?: 0L

        exoPlayer = null
        _playerFlow.value = null
        cancelSeekRepeat()
        cancelVolumeRepeat()
        cancelPlaybackHorizontalRepeat()
        cancelThumbnailPreview()
        cancelControlsAutoHide()
        resetControlsInputLock()
        unregisterVolumeObserver()
        audioManager = null

        _state.value = VideoPlayerState()
        currentVideoId = null
        currentPlaybackSource = null
        pendingVideoFormatToPersist = null
        pendingStereoModeToPersist = null
        autoDisplayInferencePending = false
        hasDispatchedNavigateBack = false
        seekPreviewResumePlayback = false
        seekPreviewShowControls = false
        resetControllerAxisState()

        runCatching { player?.release() }

        teardownScope.launch {
            if (player != null && videoId != null) {
                runCatching {
                    videoRepository.updatePlaybackState(
                        videoId = videoId,
                        position = currentPosition,
                        duration = duration,
                        timestamp = System.currentTimeMillis(),
                    )
                }
                scheduleResumeFrameThumbnail(
                    videoPath = previewVideoPath,
                    positionMs = currentPosition,
                    durationMs = duration,
                )
            }
            runCatching { VideoFramePreviewExtractor.clearPreparedVideo(previewVideoPath) }
        }
    }

    private fun scheduleResumeFrameThumbnail(
        videoPath: String?,
        positionMs: Long,
        durationMs: Long,
    ) {
        val context = appContext ?: return
        val path = videoPath?.takeIf { it.isNotBlank() } ?: return
        if (positionMs <= 0L) return
        if (durationMs > 0L && positionMs >= durationMs * 95L / 100L) return

        val request = ImageRequest.Builder(context)
            .data(
                VideoThumbnailFetcher.Model(
                    path = path,
                    frameTimeMs = positionMs,
                    durationMs = durationMs,
                )
            )
            .diskCacheKey(
                VideoThumbnailFetcher.diskCacheKey(
                    path = path,
                    frameTimeMs = positionMs,
                    durationMs = durationMs,
                )
            )
            .build()

        teardownScope.launch {
            runCatching {
                ThumbnailImageLoaderProvider.get(context).execute(request)
            }.onSuccess { result ->
                Log.d(
                    PLAYER_LOG_TAG,
                    "Resume thumbnail request completed path=$path positionMs=$positionMs " +
                            "result=${result::class.java.simpleName}",
                )
            }.onFailure { error ->
                Log.w(
                    PLAYER_LOG_TAG,
                    "Resume thumbnail request failed path=$path positionMs=$positionMs",
                    error,
                )
            }
        }
    }

    private fun invalidatePlaybackGeneration(reason: String) {
        playerGeneration += 1L
        autoAdvanceInProgress = false
        autoAdvanceTargetPath = null
        handledEndedGeneration = null
        Log.d(
            PLAYER_LOG_TAG,
            "$PLAYBACK_CONTROL_LOG_PREFIX generation invalidated " +
                "reason=$reason active=$playerGeneration",
        )
    }

    private fun resolveAudioManager(): AudioManager? {
        audioManager?.let { return it }

        val context = appContext ?: return null
        return (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.also { audioManager = it }
    }

    private fun registerVolumeObserverIfNeeded() {
        val context = appContext ?: return
        if (isVolumeObserverRegistered) return

        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver,
            )
            isVolumeObserverRegistered = true
        }
    }

    private fun unregisterVolumeObserver() {
        val context = appContext ?: return
        if (!isVolumeObserverRegistered) return

        runCatching {
            context.contentResolver.unregisterContentObserver(volumeObserver)
        }
        isVolumeObserverRegistered = false
    }

    private fun syncVolumeStateFromSystem(reason: String) {
        val manager = resolveAudioManager()
        val maxVolume = manager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
        val currentVolume = manager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

        if (manager == null || maxVolume <= 0) {
            return
        }

        val normalizedVolume = currentVolume.toFloat() / maxVolume.toFloat()
        exoPlayer?.volume = 1.0f
        _state.value = _state.value.copy(volume = normalizedVolume)
    }

    private fun InferredDisplayProfile.toVideoFormatOrNull(): VideoFormat? {
        return runCatching { VideoFormat.valueOf(videoFormat) }.getOrNull()
    }

    private fun InferredDisplayProfile.toStereoModeOrNull(): StereoMode? {
        return runCatching { StereoMode.valueOf(stereoMode) }.getOrNull()
    }
}
