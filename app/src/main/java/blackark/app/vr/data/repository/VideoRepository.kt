package blackark.app.vr.data.repository

import android.util.Log
import blackark.app.vr.data.database.dao.FavoriteVideoDao
import blackark.app.vr.data.database.dao.VideoDao
import blackark.app.vr.data.database.entity.FavoriteVideo
import blackark.app.vr.data.database.entity.RecentVideo
import kotlinx.coroutines.flow.Flow

class VideoRepository(
    private val videoDao: VideoDao,
    private val favoriteVideoDao: FavoriteVideoDao,
) {

    fun getRecentVideos(limit: Int = 20): Flow<List<RecentVideo>> {
        return videoDao.getRecentVideos(limit)
    }

    fun getRecentVideosBySource(
        serverAddress: String,
        shareName: String,
        limit: Int = 20,
    ): Flow<List<RecentVideo>> {
        return videoDao.getRecentVideosBySource(serverAddress, shareName, limit)
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

    suspend fun removeFromRecent(video: RecentVideo) {
        videoDao.deleteVideoById(video.id)
    }

    suspend fun updatePlaybackInfo(videoId: Long, position: Long) {
        videoDao.updatePlaybackInfo(videoId, System.currentTimeMillis(), position)
    }

    suspend fun clearAllVideos() {
        videoDao.clearAllVideos()
    }

    suspend fun clearRecentHistory() {
        videoDao.clearRecentHistory()
    }

    suspend fun clearRecentHistoryBySource(serverAddress: String, shareName: String) {
        videoDao.clearRecentHistoryBySource(serverAddress, shareName)
    }

    fun getFavoriteVideos(): Flow<List<FavoriteVideo>> {
        return favoriteVideoDao.getFavoriteVideos()
    }

    fun getFavoriteVideosBySource(serverAddress: String, shareName: String): Flow<List<FavoriteVideo>> {
        return favoriteVideoDao.getFavoriteVideosBySource(serverAddress, shareName)
    }

    suspend fun getFavoriteByPath(filePath: String): FavoriteVideo? {
        return favoriteVideoDao.getFavoriteByPath(filePath)
    }

    suspend fun addFavorite(video: FavoriteVideo) {
        favoriteVideoDao.upsertFavorite(video)
    }

    suspend fun removeFavoriteByPath(filePath: String) {
        favoriteVideoDao.deleteFavoriteByPath(filePath)
    }

    suspend fun clearAllFavorites() {
        favoriteVideoDao.clearAllFavorites()
    }

    suspend fun clearFavoritesBySource(serverAddress: String, shareName: String) {
        favoriteVideoDao.clearFavoritesBySource(serverAddress, shareName)
    }

    suspend fun updatePlaybackInfo(videoId: Long, timestamp: Long, position: Long) {
        try {
            videoDao.updatePlaybackInfo(videoId, timestamp, position)
        } catch (e: Exception) {
            Log.e("VideoRepository", "Failed to update playback info", e)
            throw e
        }
    }

    suspend fun updatePlaybackState(
        videoId: Long,
        position: Long,
        duration: Long,
        timestamp: Long
    ) {
        try {
            videoDao.updatePlaybackState(videoId, position, duration, timestamp)
        } catch (e: Exception) {
            Log.e("VideoRepository", "Failed to update playback state", e)
            throw e
        }
    }
}
