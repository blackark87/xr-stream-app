package com.example.myapplication.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.myapplication.data.database.entity.RecentVideo
import com.example.myapplication.data.repository.VideoRepository
import com.example.myapplication.network.SMBClient
import com.example.myapplication.network.SMBConfig
import com.example.myapplication.network.SMBFileItem
import com.example.myapplication.player.SMBDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

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
    val zoomLevel: Float = 1.0f,
    val playlist: List<SMBFileItem> = emptyList(),
    val currentPlaylistIndex: Int = -1,
    val showControls: Boolean = true
)

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

@UnstableApi
class VideoPlayerViewModel(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VideoPlayerState())
    val state: StateFlow<VideoPlayerState> = _state.asStateFlow()

    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow: StateFlow<ExoPlayer?> = _playerFlow.asStateFlow()

    private val _playerEvents = Channel<PlayerEvent>()
    val playerEvents = _playerEvents.receiveAsFlow()

    private var exoPlayer: ExoPlayer? = null
    private var currentVideoId: Long? = null
    private var pendingSaveJob: Job? = null
    private var positionTrackingJob: Job? = null

    // Store context and config for playlist navigation
    private var appContext: Context? = null
    private var currentSmbConfig: SMBConfig? = null

    private var lastSeekAxisEventTimeMs: Long = 0
    private var lastVolumeAxisEventTimeMs: Long = 0

    private val controllerAxisDeadZone = 0.25f
    private val controllerSeekCooldownMs = 220L
    private val controllerVolumeCooldownMs = 160L
    private val controllerSeekStepMs = 10_000L
    private val controllerVolumeStep = 0.05f
    private var pendingVideoFormatToPersist: VideoFormat? = null
    private var pendingStereoModeToPersist: StereoMode? = null
    private val releaseMutex = Mutex()

    init {
        // Observe global key events.
        viewModelScope.launch {
            com.example.myapplication.AppState.keyEvents.collect { event ->
                handleKeyEvent(event)
            }
        }

        // Observe 6DoF thumbstick axis events.
        viewModelScope.launch {
            com.example.myapplication.AppState.controllerAxisEvents.collect { event ->
                handleControllerAxisEvent(event)
            }
        }
    }

    fun setVideoFormat(format: VideoFormat) {
        _state.value = _state.value.copy(videoFormat = format)
        pendingVideoFormatToPersist = format

        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            persistPendingDisplaySettings()
        }
    }

    fun setStereoMode(mode: StereoMode) {
        _state.value = _state.value.copy(stereoMode = mode)
        pendingStereoModeToPersist = mode

        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            persistPendingDisplaySettings()
        }
    }

    private suspend fun persistPendingDisplaySettings() {
        val videoId = ensureCurrentVideoId()
        if (videoId == null) {
            Log.w(
                "VideoPlayerViewModel",
                "Cannot persist display settings yet: currentVideoId/video path unavailable"
            )
            return
        }

        pendingVideoFormatToPersist?.let { format ->
            try {
                videoRepository.updateVideoFormat(videoId, format.name)
                pendingVideoFormatToPersist = null
                Log.d(
                    "VideoPlayerViewModel",
                    "Saved video format: ${format.name} for video ID: $videoId"
                )
            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Failed to save video format: ${e.message}", e)
            }
        }

        pendingStereoModeToPersist?.let { mode ->
            try {
                videoRepository.updateStereoMode(videoId, mode.name)
                pendingStereoModeToPersist = null
                Log.d(
                    "VideoPlayerViewModel",
                    "Saved stereo mode: ${mode.name} for video ID: $videoId"
                )
            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Failed to save stereo mode: ${e.message}", e)
            }
        }
    }

    private suspend fun ensureCurrentVideoId(): Long? {
        currentVideoId?.let { return it }

        val currentPath = _state.value.videoFile?.path ?: return null
        val existingVideo = videoRepository.getVideoByPath(currentPath) ?: return null
        currentVideoId = existingVideo.id
        return existingVideo.id
    }

    fun toggleControls() {
        _state.value = _state.value.copy(showControls = !_state.value.showControls)
    }

    fun setControlsVisibility(visible: Boolean) {
        _state.value = _state.value.copy(showControls = visible)
    }

    fun requestNavigateBack() {
        viewModelScope.launch {
            _playerEvents.send(PlayerEvent.NavigateBack)
        }
    }

    private fun handleControllerAxisEvent(event: com.example.myapplication.ControllerAxisEvent) {
        val player = exoPlayer ?: return

        val now = event.eventTimeMs
        val x = event.x
        val y = event.y

        if (abs(x) >= controllerAxisDeadZone && now - lastSeekAxisEventTimeMs >= controllerSeekCooldownMs) {
            if (x > 0f) {
                skipForward(controllerSeekStepMs)
            } else {
                skipBackward(controllerSeekStepMs)
            }
            lastSeekAxisEventTimeMs = now
        }

        if (abs(y) >= controllerAxisDeadZone && now - lastVolumeAxisEventTimeMs >= controllerVolumeCooldownMs) {
            val delta = if (y < 0f) controllerVolumeStep else -controllerVolumeStep
            val newVolume = (player.volume + delta).coerceIn(0f, 1f)
            setVolume(newVolume)
            lastVolumeAxisEventTimeMs = now
        }
    }

    private fun revealControlsIfHidden(): Boolean {
        if (!_state.value.showControls) {
            _state.value = _state.value.copy(showControls = true)
            return true
        }
        return false
    }

    private fun handleKeyEvent(event: android.view.KeyEvent) {
        if (event.action != android.view.KeyEvent.ACTION_UP) return

        Log.d(
            "VideoPlayerViewModel",
            "Received KeyEvent: code=${event.keyCode}, name=${
                android.view.KeyEvent.keyCodeToString(
                    event.keyCode
                )
            }"
        )

        when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_A,
            android.view.KeyEvent.KEYCODE_BUTTON_R2,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                toggleControls()
            }

            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (revealControlsIfHidden()) return
                togglePlayPause()
            }

            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (revealControlsIfHidden()) return
                play()
            }

            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (revealControlsIfHidden()) return
                pause()
            }

            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (revealControlsIfHidden()) return
                seekBackward()
            }

            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (revealControlsIfHidden()) return
                seekForward()
            }

            android.view.KeyEvent.KEYCODE_BUTTON_B,
            android.view.KeyEvent.KEYCODE_BACK -> {
                requestNavigateBack()
            }
        }
    }

    fun initializePlayer(
        context: Context,
        smbConfig: SMBConfig,
        videoFile: SMBFileItem
    ) {
        Log.d("VideoPlayerViewModel", "initializePlayer called for ${videoFile.name}")

        this.appContext = context.applicationContext
        this.currentSmbConfig = smbConfig

        viewModelScope.launch {
            try {
                // Release previous player first so its final position is saved to the correct video ID.
                releaseCurrentPlayer(resetUiState = false)
                currentVideoId = null
                pendingVideoFormatToPersist = null
                pendingStereoModeToPersist = null

                // Check for saved video settings
                Log.d("VideoPlayerViewModel", "Looking up video by path: ${videoFile.path}")
                val savedVideo = videoRepository.getVideoByPath(videoFile.path)

                if (savedVideo != null) {
                    Log.d(
                        "VideoPlayerViewModel",
                        "Found saved video - ID: ${savedVideo.id}, stereo: ${savedVideo.stereoMode}, format: ${savedVideo.videoFormat}, favorite: ${savedVideo.isFavorite}"
                    )
                } else {
                    Log.d("VideoPlayerViewModel", "No saved video found, will create new record")
                }

                // Use saved settings if available, otherwise default to 2D/Mono
                val initialStereoMode = if (savedVideo != null) {
                    try {
                        StereoMode.valueOf(savedVideo.stereoMode)
                    } catch (e: Exception) {
                        Log.w(
                            "VideoPlayerViewModel",
                            "Invalid stereo mode in database: ${savedVideo.stereoMode}, defaulting to Mono"
                        )
                        StereoMode.Mono
                    }
                } else {
                    StereoMode.Mono
                }

                val initialVideoFormat = if (savedVideo != null) {
                    try {
                        VideoFormat.valueOf(savedVideo.videoFormat)
                    } catch (e: Exception) {
                        Log.w(
                            "VideoPlayerViewModel",
                            "Invalid video format in database: ${savedVideo.videoFormat}, defaulting to Format2D"
                        )
                        VideoFormat.Format2D
                    }
                } else {
                    VideoFormat.Format2D
                }

                Log.d(
                    "VideoPlayerViewModel",
                    "Initializing player - Stereo: $initialStereoMode, Format: $initialVideoFormat"
                )

                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                    videoFile = videoFile,
                    stereoMode = initialStereoMode,
                    videoFormat = initialVideoFormat,
                    showControls = true
                )

                // Save to recent videos immediately to ensure we have an ID for updates.
                saveToRecentVideos(videoFile, smbConfig, savedVideo)

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
                        // Set up player listener
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                Log.d(
                                    "VideoPlayerViewModel",
                                    "ExoPlayer state changed: $playbackState"
                                )
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
                                    }

                                    Player.STATE_IDLE -> {
                                        _state.value = _state.value.copy(isLoading = false)
                                    }
                                }
                            }

                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                Log.d(
                                    "VideoPlayerViewModel",
                                    "ExoPlayer isPlaying changed: $isPlaying"
                                )
                                _state.value = _state.value.copy(isPlaying = isPlaying)
                            }

                            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                                // Check video track for stereo mode metadata if currently Mono
                                // We don't override if we already detected something from filename
                                if (_state.value.stereoMode != StereoMode.Mono) {
                                    return
                                }

                                // Check video track for stereo mode metadata
                                var detectedMode = StereoMode.Mono
                                for (trackGroup in tracks.groups) {
                                    if (trackGroup.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                                        for (i in 0 until trackGroup.length) {
                                            val format = trackGroup.getTrackFormat(i)
                                            val stereoMode = format.stereoMode
                                            Log.d(
                                                "VideoPlayerViewModel",
                                                "Video track stereoMode: $stereoMode"
                                            )

                                            if (stereoMode == androidx.media3.common.C.STEREO_MODE_LEFT_RIGHT ||
                                                stereoMode == androidx.media3.common.C.STEREO_MODE_STEREO_MESH
                                            ) {
                                                detectedMode = StereoMode.SideBySide
                                                break
                                            } else if (stereoMode == androidx.media3.common.C.STEREO_MODE_TOP_BOTTOM) {
                                                detectedMode = StereoMode.TopBottom
                                                break
                                            }
                                        }
                                    }
                                    if (detectedMode != StereoMode.Mono) break
                                }

                                if (detectedMode != StereoMode.Mono) {
                                    _state.value = _state.value.copy(stereoMode = detectedMode)
                                }
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                Log.e(
                                    "VideoPlayerViewModel",
                                    "ExoPlayer error: ${error.message}",
                                    error
                                )
                                _state.value = _state.value.copy(
                                    isLoading = false,
                                    error = error.message ?: "Playback error occurred"
                                )
                            }
                        })
                    }

                // Create SMB data source
                val dataSourceFactory = SMBDataSource.Factory(smbConfig)

                // videoFile.path is already a complete SMB URL from jcifs (e.g., smb://192.168.1.105:445/downloads/file.mp4)
                val uri = videoFile.path.toUri()
                Log.d("VideoPlayerViewModel", "initializePlayer - Using URI from path: $uri")

                val mediaItem = MediaItem.fromUri(uri)

                // Create progressive media source
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
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
                        Log.d(
                            "VideoPlayerViewModel",
                            "Resuming playback from position: ${resumePosition}ms (${(progressPercent * 100).toInt()}%)"
                        )
                        exoPlayer?.seekTo(resumePosition)
                    } else {
                        Log.d(
                            "VideoPlayerViewModel",
                            "Video was almost finished, starting from beginning"
                        )
                    }
                }

                exoPlayer?.playWhenReady = true

                // Expose player to UI
                _playerFlow.value = exoPlayer

                // Start position tracking
                startPositionTracking()

                // Always refresh playlist for current file to keep index/order in sync with file screen.
                refreshPlaylist(videoFile, smbConfig)

            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Error initializing player: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to initialize player"
                )
            }
        }
    }

    private suspend fun refreshPlaylist(
        currentFile: SMBFileItem,
        smbConfig: SMBConfig,
    ): Pair<List<SMBFileItem>, Int>? {
        return try {
            val relativeParentPath = resolveRelativeParentPath(currentFile.path, smbConfig)
            Log.d("VideoPlayerViewModel", "Fetching playlist for path: $relativeParentPath")

            val client = SMBClient(smbConfig)
            val connectResult = client.connect()
            if (connectResult.isFailure) {
                Log.w(
                    "VideoPlayerViewModel",
                    "Failed to connect while fetching playlist: ${connectResult.exceptionOrNull()?.message}"
                )
                return null
            }

            val listResult = client.listFiles(relativeParentPath)
            client.disconnect()

            if (listResult.isFailure) {
                Log.w(
                    "VideoPlayerViewModel",
                    "Failed to list files for playlist: ${listResult.exceptionOrNull()?.message}"
                )
                return null
            }

            val allFiles = listResult.getOrNull().orEmpty()
            val sortedFiles = allFiles
                .filter { it.isDirectory || SMBClient.isVideoFile(it.name) }
                .sortedWith(
                    compareByDescending<SMBFileItem> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
            val videoFiles =
                sortedFiles.filter { !it.isDirectory && SMBClient.isVideoFile(it.name) }

            val currentIndex = resolveCurrentPlaylistIndex(videoFiles, currentFile)

            _state.value = _state.value.copy(
                playlist = videoFiles,
                currentPlaylistIndex = currentIndex,
            )

            Log.d(
                "VideoPlayerViewModel",
                "Playlist fetched: ${videoFiles.size} videos, current index: $currentIndex"
            )

            videoFiles to currentIndex
        } catch (e: Exception) {
            Log.e("VideoPlayerViewModel", "Failed to fetch playlist: ${e.message}", e)
            null
        }
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

    private fun normalizeSmbPath(path: String): String {
        return path
            .trim()
            .replace('\\', '/')
            .removeSuffix("/")
            .lowercase()
    }

    private fun resolveCurrentPlaylistIndex(
        playlist: List<SMBFileItem>,
        currentFile: SMBFileItem,
    ): Int {
        if (playlist.isEmpty()) return -1

        val normalizedCurrent = normalizeSmbPath(currentFile.path)
        val byPath = playlist.indexOfFirst { normalizeSmbPath(it.path) == normalizedCurrent }
        if (byPath >= 0) return byPath

        return playlist.indexOfFirst { it.name.equals(currentFile.name, ignoreCase = true) }
    }

    private suspend fun ensurePlaylistReady(): Pair<List<SMBFileItem>, Int>? {
        val currentFile = _state.value.videoFile ?: return null
        val smbConfig = currentSmbConfig ?: return null

        val currentPlaylist = _state.value.playlist
        val currentIndex = resolveCurrentPlaylistIndex(currentPlaylist, currentFile)

        if (currentPlaylist.isNotEmpty() && currentIndex >= 0) {
            if (currentIndex != _state.value.currentPlaylistIndex) {
                _state.value = _state.value.copy(currentPlaylistIndex = currentIndex)
            }
            return currentPlaylist to currentIndex
        }

        return refreshPlaylist(currentFile, smbConfig)
    }

    fun playNextVideo() {
        viewModelScope.launch {
            val playlistResult = ensurePlaylistReady() ?: return@launch
            val (playlist, currentIndex) = playlistResult
            if (currentIndex < 0 || currentIndex >= playlist.lastIndex) return@launch

            val ctx = appContext ?: return@launch
            val config = currentSmbConfig ?: return@launch

            val nextIndex = currentIndex + 1
            val nextFile = playlist[nextIndex]

            _state.value = _state.value.copy(currentPlaylistIndex = nextIndex)
            initializePlayer(ctx, config, nextFile)
        }
    }

    fun playPreviousVideo() {
        viewModelScope.launch {
            val playlistResult = ensurePlaylistReady() ?: return@launch
            val (playlist, currentIndex) = playlistResult
            if (currentIndex <= 0 || currentIndex >= playlist.size) return@launch

            val ctx = appContext ?: return@launch
            val config = currentSmbConfig ?: return@launch

            val prevIndex = currentIndex - 1
            val prevFile = playlist[prevIndex]

            _state.value = _state.value.copy(currentPlaylistIndex = prevIndex)
            initializePlayer(ctx, config, prevFile)
        }
    }

    private suspend fun saveToRecentVideos(
        videoFile: SMBFileItem,
        smbConfig: SMBConfig,
        existingVideo: RecentVideo?
    ) {
        if (existingVideo != null) {
            // Update existing video - preserve favorite status, ID, and settings
            // IMPORTANT: Use current state for format/stereo mode to avoid overwriting user changes
            // if this is called after user has already changed settings
            val updatedVideo = existingVideo.copy(
                fileName = videoFile.name,
                lastPlayed = System.currentTimeMillis(),
                videoFormat = _state.value.videoFormat.name,
                stereoMode = _state.value.stereoMode.name
            )
            videoRepository.updateVideo(updatedVideo)
            currentVideoId = existingVideo.id
            Log.d(
                "VideoPlayerViewModel",
                "Updated existing video record (ID: ${existingVideo.id}, isFavorite: ${existingVideo.isFavorite})"
            )
        } else {
            // Create new video entry
            val recentVideo = RecentVideo(
                fileName = videoFile.name,
                filePath = videoFile.path,
                serverAddress = smbConfig.serverAddress,
                shareName = smbConfig.shareName,
                lastPlayed = System.currentTimeMillis(),
                lastPosition = 0,
                duration = 0,
                isFavorite = false,
                videoFormat = _state.value.videoFormat.name,
                stereoMode = _state.value.stereoMode.name
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
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
        _state.value = _state.value.copy(volume = volume)
    }

    fun adjustZoom(delta: Float) {
        val newZoom = (_state.value.zoomLevel + delta).coerceIn(0.5f, 3.0f)
        _state.value = _state.value.copy(zoomLevel = newZoom)
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
        exoPlayer?.let {
            val newPosition = (it.currentPosition + ms).coerceAtMost(it.duration)
            it.seekTo(newPosition)
        }
    }

    fun skipBackward(ms: Long = 10000) {
        exoPlayer?.let {
            val newPosition = (it.currentPosition - ms).coerceAtLeast(0)
            it.seekTo(newPosition)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun retry(context: Context, smbConfig: SMBConfig) {
        val currentFile = _state.value.videoFile
        if (currentFile != null) {
            initializePlayer(context, smbConfig, currentFile)
        }
    }

    private suspend fun releaseCurrentPlayer(resetUiState: Boolean) = releaseMutex.withLock {
        positionTrackingJob?.cancel()
        positionTrackingJob = null

        // Wait for any pending format/stereo mode saves before releasing.
        pendingSaveJob?.join()
        Log.d("VideoPlayerViewModel", "Pending save job completed")

        val player = exoPlayer
        val videoId = currentVideoId
        if (player != null && videoId != null) {
            try {
                val duration = if (player.duration > 0) player.duration else 0L
                videoRepository.updatePlaybackState(
                    videoId = videoId,
                    position = player.currentPosition,
                    duration = duration,
                    timestamp = System.currentTimeMillis()
                )
                Log.d(
                    "VideoPlayerViewModel",
                    "Saved final position: ${player.currentPosition}ms, duration: ${player.duration}ms"
                )
            } catch (e: Exception) {
                Log.e(
                    "VideoPlayerViewModel",
                    "Failed to save final position: ${e.message}",
                    e
                )
            }
        }

        player?.release()
        exoPlayer = null
        _playerFlow.value = null
        pendingSaveJob = null

        if (resetUiState) {
            _state.value = VideoPlayerState()
            currentVideoId = null
            pendingVideoFormatToPersist = null
            pendingStereoModeToPersist = null
            lastSeekAxisEventTimeMs = 0
            lastVolumeAxisEventTimeMs = 0
        }
    }

    suspend fun releasePlayerBeforeNavigateBack() {
        releaseCurrentPlayer(resetUiState = true)
    }

    fun releasePlayerAsync() {
        viewModelScope.launch {
            releaseCurrentPlayer(resetUiState = true)
        }
    }

    fun releasePlayer() {
        // This is safe because releasePlayer() is called from onCleared()/onDispose during teardown.
        runBlocking {
            releaseCurrentPlayer(resetUiState = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}














