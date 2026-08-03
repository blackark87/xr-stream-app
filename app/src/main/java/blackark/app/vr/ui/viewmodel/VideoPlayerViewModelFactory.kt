@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class],
)

package blackark.app.vr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import blackark.app.vr.data.repository.VideoDisplaySettingsRepository
import blackark.app.vr.data.repository.VideoRepository

class VideoPlayerViewModelFactory(
    private val videoRepository: VideoRepository,
    private val videoDisplaySettingsRepository: VideoDisplaySettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoPlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VideoPlayerViewModel(
                videoRepository,
                videoDisplaySettingsRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
