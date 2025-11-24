package com.example.myapplication.data.repository

import com.example.myapplication.data.database.dao.VideoDao
import com.example.myapplication.data.database.entity.RecentVideo
import kotlinx.coroutines.flow.Flow

class VideoRepository(private val videoDao: VideoDao) {

    fun getRecentVideos(limit: Int = 20): Flow<List<RecentVideo>> {
        return videoDao.getRecentVideos(limit)
    }

    suspend fun getVideoById(videoId: Long): RecentVideo? {
        return videoDao.getVideoById(videoId)
    }

    suspend fun getVideoByPath(path: String): RecentVideo? {
        return videoDao.getVideoByPath(path)
    }

    suspend fun insertVideo(video: RecentVideo): Long {
        return videoDao.insertVideo(video)
    }

    suspend fun updateVideo(video: RecentVideo) {
        videoDao.updateVideo(video)
    }

    suspend fun deleteVideo(video: RecentVideo) {
        videoDao.deleteVideo(video)
    }

    suspend fun deleteVideoById(videoId: Long) {
        videoDao.deleteVideoById(videoId)
    }

    suspend fun updatePlaybackInfo(videoId: Long, position: Long) {
        videoDao.updatePlaybackInfo(videoId, System.currentTimeMillis(), position)
    }

    suspend fun clearAllVideos() {
        videoDao.clearAllVideos()
    }

    // Favorites support
    fun getFavoriteVideos(): Flow<List<RecentVideo>> {
        return videoDao.getFavoriteVideos()
    }

    fun getFavoriteVideosByServer(serverAddress: String): Flow<List<RecentVideo>> {
        return videoDao.getFavoriteVideosByServer(serverAddress)
    }

    suspend fun toggleFavorite(videoId: Long, isFavorite: Boolean) {
        videoDao.updateFavoriteStatus(videoId, isFavorite)
    }

    suspend fun toggleFavoriteByPath(filePath: String, isFavorite: Boolean) {
        videoDao.updateFavoriteStatusByPath(filePath, isFavorite)
    }
}
