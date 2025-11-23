package com.example.myapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.data.repository.ServerRepository
import com.example.myapplication.data.repository.VideoRepository

class MainDashboardViewModelFactory(
    private val serverRepository: ServerRepository,
    private val videoRepository: VideoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainDashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainDashboardViewModel(serverRepository, videoRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
