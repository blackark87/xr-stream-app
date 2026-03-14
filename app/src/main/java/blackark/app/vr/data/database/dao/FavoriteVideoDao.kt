package blackark.app.vr.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import blackark.app.vr.data.database.entity.FavoriteVideo
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteVideoDao {
    @Query("SELECT * FROM favorite_videos ORDER BY fileName ASC")
    fun getFavoriteVideos(): Flow<List<FavoriteVideo>>

    @Query(
        """
        SELECT * FROM favorite_videos
        WHERE serverAddress = :serverAddress AND shareName = :shareName
        ORDER BY fileName ASC
        """
    )
    fun getFavoriteVideosBySource(serverAddress: String, shareName: String): Flow<List<FavoriteVideo>>

    @Query("SELECT * FROM favorite_videos WHERE filePath = :filePath LIMIT 1")
    suspend fun getFavoriteByPath(filePath: String): FavoriteVideo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(video: FavoriteVideo)

    @Query("DELETE FROM favorite_videos WHERE filePath = :filePath")
    suspend fun deleteFavoriteByPath(filePath: String)

    @Query("DELETE FROM favorite_videos")
    suspend fun clearAllFavorites()

    @Query("DELETE FROM favorite_videos WHERE serverAddress = :serverAddress AND shareName = :shareName")
    suspend fun clearFavoritesBySource(serverAddress: String, shareName: String)

    @Query("UPDATE favorite_videos SET thumbnailPath = :path, resolvedTitle = COALESCE(:title, resolvedTitle) WHERE filePath = :filePath")
    suspend fun updateThumbnailAndTitleByPath(filePath: String, path: String, title: String?)

    @Query("UPDATE favorite_videos SET thumbnailPath = NULL")
    suspend fun clearAllThumbnailPaths()
}
