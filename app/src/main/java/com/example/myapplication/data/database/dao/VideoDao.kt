package com.example.myapplication.data.database.dao

import androidx.room.*
import com.example.myapplication.data.database.entity.RecentVideo
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM recent_videos ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentVideos(limit: Int = 20): Flow<List<RecentVideo>>

    @Query("SELECT * FROM recent_videos WHERE id = :videoId")
    suspend fun getVideoById(videoId: Long): RecentVideo?

    @Query("SELECT * FROM recent_videos WHERE filePath = :path LIMIT 1")
    suspend fun getVideoByPath(path: String): RecentVideo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: RecentVideo): Long

    @Update
    suspend fun updateVideo(video: RecentVideo)

    @Delete
    suspend fun deleteVideo(video: RecentVideo)

    @Query("DELETE FROM recent_videos WHERE id = :videoId")
    suspend fun deleteVideoById(videoId: Long)

    @Query("UPDATE recent_videos SET lastPlayed = :timestamp, lastPosition = :position WHERE id = :videoId")
    suspend fun updatePlaybackInfo(videoId: Long, timestamp: Long, position: Long)

    @Query("DELETE FROM recent_videos")
    suspend fun clearAllVideos()

    // Favorites support
    @Query("SELECT * FROM recent_videos WHERE isFavorite = 1 ORDER BY fileName ASC")
    fun getFavoriteVideos(): Flow<List<RecentVideo>>

    @Query("UPDATE recent_videos SET isFavorite = :isFavorite WHERE id = :videoId")
    suspend fun updateFavoriteStatus(videoId: Long, isFavorite: Boolean)

    @Query("UPDATE recent_videos SET isFavorite = :isFavorite WHERE filePath = :filePath")
    suspend fun updateFavoriteStatusByPath(filePath: String, isFavorite: Boolean)
}
