package com.example.myapplication.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
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

data class VideoPlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val bufferedPercentage: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isLoading: Boolean = false,
    val error: String? = null,
    val videoFile: SMBFileItem? = null
)

class VideoPlayerViewModel(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VideoPlayerState())
    val state: StateFlow<VideoPlayerState> = _state.asStateFlow()

    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow: StateFlow<ExoPlayer?> = _playerFlow.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var currentVideoId: Long? = null

    fun initializePlayer(
        context: Context,
        smbConfig: SMBConfig,
        videoFile: SMBFileItem
    ) {
        Log.d("VideoPlayerViewModel", "initializePlayer called for ${videoFile.name}")
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                    videoFile = videoFile
                )

                // Release existing player if any
                releasePlayer()

                // Create ExoPlayer instance
                exoPlayer = ExoPlayer.Builder(context).build().apply {
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

                // Extract file path for URI
                val filePath = videoFile.path
                    .substringAfter("/${smbConfig.shareName}")
                val uri = Uri.parse("smb://${smbConfig.serverAddress}:${smbConfig.port}/${smbConfig.shareName}$filePath")
            Log.d("VideoPlayerViewModel", "initializePlayer - Constructed URI: $uri")
            
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
        exoPlayer?.setPlaybackParameters(
            PlaybackParameters(speed)
        )
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
