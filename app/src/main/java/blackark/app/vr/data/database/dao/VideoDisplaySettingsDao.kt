package blackark.app.vr.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import blackark.app.vr.data.database.entity.VideoDisplaySettings

@Dao
interface VideoDisplaySettingsDao {
    @Query("SELECT * FROM video_display_settings WHERE filePath = :path LIMIT 1")
    suspend fun getByPath(path: String): VideoDisplaySettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: VideoDisplaySettings)

    @Query("DELETE FROM video_display_settings WHERE filePath = :path")
    suspend fun deleteByPath(path: String)
}
