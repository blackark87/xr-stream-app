package blackark.app.vr.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import blackark.app.vr.data.database.entity.RecentVideo
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM recent_videos WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentVideos(limit: Int = 20): Flow<List<RecentVideo>>

    @Query(
        """
        SELECT * FROM recent_videos
        WHERE serverAddress = :serverAddress AND shareName = :shareName AND lastPlayed > 0
        ORDER BY lastPlayed DESC
        LIMIT :limit
        """
    )
    fun getRecentVideosBySource(
        serverAddress: String,
        shareName: String,
        limit: Int = 20,
    ): Flow<List<RecentVideo>>

    @Query("SELECT * FROM recent_videos WHERE id = :videoId")
    suspend fun getVideoById(videoId: Long): RecentVideo?

    @Query("SELECT * FROM recent_videos WHERE filePath = :path LIMIT 1")
    suspend fun getVideoByPath(path: String): RecentVideo?

    @Query("SELECT * FROM recent_videos WHERE fileName = :fileName ORDER BY lastPlayed DESC LIMIT 1")
    suspend fun getLatestVideoByFileName(fileName: String): RecentVideo?

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

    @Query("UPDATE recent_videos SET lastPlayed = :timestamp, lastPosition = :position, duration = :duration WHERE id = :videoId")
    suspend fun updatePlaybackState(videoId: Long, position: Long, duration: Long, timestamp: Long)

    @Query("DELETE FROM recent_videos")
    suspend fun clearAllVideos()

    @Query("DELETE FROM recent_videos WHERE lastPlayed > 0")
    suspend fun clearRecentHistory()

    @Query(
        """
        DELETE FROM recent_videos
        WHERE serverAddress = :serverAddress AND shareName = :shareName AND lastPlayed > 0
        """
    )
    suspend fun clearRecentHistoryBySource(serverAddress: String, shareName: String)

    @Query("UPDATE recent_videos SET thumbnailPath = :path WHERE id = :videoId")
    suspend fun updateThumbnailPath(videoId: Long, path: String)

    @Query("UPDATE recent_videos SET thumbnailPath = :path, resolvedTitle = COALESCE(:title, resolvedTitle) WHERE id = :videoId")
    suspend fun updateThumbnailAndTitle(videoId: Long, path: String, title: String?)

    @Query("UPDATE recent_videos SET thumbnailPath = NULL")
    suspend fun clearAllThumbnailPaths()
}
