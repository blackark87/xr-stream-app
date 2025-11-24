package com.example.myapplication.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
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
import com.example.myapplication.network.SMBConfig
import com.example.myapplication.network.SMBFileItem
import com.example.myapplication.player.SMBDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.core.net.toUri

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
    val stereoMode: StereoMode = StereoMode.Mono
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

    private var exoPlayer: ExoPlayer? = null
    private var currentVideoId: Long? = null

    /**
     * Detect if video is SBS/stereo based on filename patterns
     */
    private fun detectStereoFromFilename(filename: String): StereoMode {
        val lowerFilename = filename.lowercase()
        
        // Check for Top-Bottom patterns
        val tbPatterns = listOf(
            "_tb", ".tb", "tb.", "_topbottom", "top-bottom", "top_bottom", "over-under"
        )
        if (tbPatterns.any { pattern -> lowerFilename.contains(pattern) }) {
            return StereoMode.TopBottom
        }

        // Check for Side-by-Side patterns
        val sbsPatterns = listOf(
            "_lr", "_sbs", ".sbs", "lr.", "sbs.",
            "_3d", ".3d", "3d.", "_sidebyside",
            "[lr]", "[sbs]", "(lr)", "(sbs)",
            "side-by-side", "side_by_side"
        )
        if (sbsPatterns.any { pattern -> lowerFilename.contains(pattern) }) {
            return StereoMode.SideBySide
        }
        
        // Check for 180/360 patterns (defaults to Mono unless stereo is also detected)
        // Ideally we'd detect format too, but for now let's stick to stereo mode detection
        
        return StereoMode.Mono
    }

    /**
     * Detect video format from filename
     */
    private fun detectFormatFromFilename(filename: String): VideoFormat {
        val lowerFilename = filename.lowercase()
        if (lowerFilename.contains("180")) return VideoFormat.Format180
        if (lowerFilename.contains("360")) return VideoFormat.Format360
        return VideoFormat.Format2D
    }

    fun setVideoFormat(format: VideoFormat) {
        _state.value = _state.value.copy(videoFormat = format)
    }

    fun setStereoMode(mode: StereoMode) {
        _state.value = _state.value.copy(stereoMode = mode)
    }

    fun initializePlayer(
        context: Context,
        smbConfig: SMBConfig,
        videoFile: SMBFileItem
    ) {
        Log.d("VideoPlayerViewModel", "initializePlayer called for ${videoFile.name}")
        
        viewModelScope.launch {
            try {
                // Default to 2D/Mono as requested ("play first, then toggle")
                // We no longer use filename detection for initial state
                val initialStereoMode = StereoMode.Mono
                val initialVideoFormat = VideoFormat.Format2D
                Log.d("VideoPlayerViewModel", "Initializing with defaults - Stereo: $initialStereoMode, Format: $initialVideoFormat")

                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                    videoFile = videoFile,
                    stereoMode = initialStereoMode,
                    videoFormat = initialVideoFormat
                )

                // Release existing player if any
                releasePlayer()

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
                        /* maxBufferMs = */ 50000, // 50 seconds maximum buffer
                        /* bufferForPlaybackMs = */ 2500,  // Start playback after 2.5 seconds
                        /* bufferForPlaybackAfterRebufferMs = */ 5000  // Resume after 5 seconds on rebuffer
                    )
                    .build()

                exoPlayer = ExoPlayer.Builder(context)
                    .setLoadControl(loadControl)
                    .build().apply {
                    // Set up player listener
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            Log.d("VideoPlayerViewModel", "ExoPlayer state changed: $playbackState")
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
                            Log.d("VideoPlayerViewModel", "ExoPlayer isPlaying changed: $isPlaying")
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
                                        Log.d("VideoPlayerViewModel", "Video track stereoMode: $stereoMode")

                                        if (stereoMode == androidx.media3.common.C.STEREO_MODE_LEFT_RIGHT ||
                                            stereoMode == androidx.media3.common.C.STEREO_MODE_STEREO_MESH) {
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
                            Log.e("VideoPlayerViewModel", "ExoPlayer error: ${error.message}", error)
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
                exoPlayer?.playWhenReady = true

                // Expose player to UI
                _playerFlow.value = exoPlayer

                // Save to recent videos
                saveToRecentVideos(videoFile, smbConfig)

                // Start position tracking
                startPositionTracking()

            } catch (e: Exception) {
                Log.e("VideoPlayerViewModel", "Error initializing player: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to initialize player"
                )
            }
        }
    }

    private suspend fun saveToRecentVideos(videoFile: SMBFileItem, smbConfig: SMBConfig) {
        val recentVideo = RecentVideo(
            fileName = videoFile.name,
            filePath = videoFile.path,
            serverAddress = smbConfig.serverAddress,
            shareName = smbConfig.shareName,
            lastPlayed = System.currentTimeMillis(),
            lastPosition = 0,
            duration = 0
        )

        currentVideoId = videoRepository.insertVideo(recentVideo)
    }

    private fun startPositionTracking() {
        viewModelScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _state.value = _state.value.copy(
                        currentPosition = player.currentPosition,
                        duration = player.duration,
                        bufferedPercentage = player.bufferedPercentage
                    )

                    // Save position every 5 seconds
                    currentVideoId?.let { videoId ->
                        if (player.currentPosition % 5000 < 500) {
                            videoRepository.updatePlaybackInfo(videoId, player.currentPosition)
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

    fun releasePlayer() {
        // Save final position
        viewModelScope.launch {
            currentVideoId?.let { videoId ->
                exoPlayer?.let { player ->
                    videoRepository.updatePlaybackInfo(videoId, player.currentPosition)
                }
            }
        }

        exoPlayer?.release()
        exoPlayer = null
        _playerFlow.value = null
        _state.value = VideoPlayerState()
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
