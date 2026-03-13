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

    @Query("SELECT * FROM recent_videos WHERE serverAddress = :serverAddress AND lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentVideosByServer(serverAddress: String, limit: Int = 20): Flow<List<RecentVideo>>

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

    // Favorites support
    @Query("SELECT * FROM recent_videos WHERE isFavorite = 1 ORDER BY fileName ASC")
    fun getFavoriteVideos(): Flow<List<RecentVideo>>

    @Query("SELECT * FROM recent_videos WHERE isFavorite = 1 AND serverAddress = :serverAddress ORDER BY fileName ASC")
    fun getFavoriteVideosByServer(serverAddress: String): Flow<List<RecentVideo>>

    @Query("UPDATE recent_videos SET isFavorite = :isFavorite WHERE id = :videoId")
    suspend fun updateFavoriteStatus(videoId: Long, isFavorite: Boolean)

    @Query("UPDATE recent_videos SET isFavorite = :isFavorite WHERE filePath = :filePath")
    suspend fun updateFavoriteStatusByPath(filePath: String, isFavorite: Boolean)

    @Query("UPDATE recent_videos SET videoFormat = :format WHERE id = :videoId")
    suspend fun updateVideoFormat(videoId: Long, format: String)

    @Query("UPDATE recent_videos SET stereoMode = :mode WHERE id = :videoId")
    suspend fun updateStereoMode(videoId: Long, mode: String)

    @Query("UPDATE recent_videos SET thumbnailPath = :path WHERE id = :videoId")
    suspend fun updateThumbnailPath(videoId: Long, path: String)

    @Query("UPDATE recent_videos SET thumbnailPath = :path, resolvedTitle = COALESCE(:title, resolvedTitle) WHERE id = :videoId")
    suspend fun updateThumbnailAndTitle(videoId: Long, path: String, title: String?)

    @Query("UPDATE recent_videos SET thumbnailPath = NULL")
    suspend fun clearAllThumbnailPaths()
}
