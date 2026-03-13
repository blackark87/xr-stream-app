package blackark.app.vr.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import blackark.app.vr.data.repository.ServerRepository
import blackark.app.vr.data.repository.VideoRepository

class MainDashboardViewModelFactory(
    private val context: Context,
    private val serverRepository: ServerRepository,
    private val videoRepository: VideoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainDashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainDashboardViewModel(context, serverRepository, videoRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
