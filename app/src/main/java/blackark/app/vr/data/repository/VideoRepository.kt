package blackark.app.vr.data.repository

import android.util.Log
import blackark.app.vr.data.database.dao.VideoDao
import blackark.app.vr.data.database.entity.RecentVideo
import kotlinx.coroutines.flow.Flow

class VideoRepository(private val videoDao: VideoDao) {

    fun getRecentVideos(limit: Int = 20): Flow<List<RecentVideo>> {
        return videoDao.getRecentVideos(limit)
    }

    fun getRecentVideosByServer(serverAddress: String, limit: Int = 20): Flow<List<RecentVideo>> {
        return videoDao.getRecentVideosByServer(serverAddress, limit)
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

    suspend fun updateVideoFormat(videoId: Long, format: String) {
        try {
            videoDao.updateVideoFormat(videoId, format)
            Log.d(
                "VideoRepository",
                "Successfully updated video format to $format for video ID: $videoId"
            )
        } catch (e: Exception) {
            Log.e(
                "VideoRepository",
                "Failed to update video format for video $videoId: ${e.message}",
                e
            )
            throw e // Re-throw so caller knows it failed
        }
    }

    suspend fun updateStereoMode(videoId: Long, mode: String) {
        try {
            videoDao.updateStereoMode(videoId, mode)
            Log.d(
                "VideoRepository",
                "Successfully updated stereo mode to $mode for video ID: $videoId"
            )
        } catch (e: Exception) {
            Log.e(
                "VideoRepository",
                "Failed to update stereo mode for video $videoId: ${e.message}",
                e
            )
            throw e // Re-throw so caller knows it failed
        }
    }
}
