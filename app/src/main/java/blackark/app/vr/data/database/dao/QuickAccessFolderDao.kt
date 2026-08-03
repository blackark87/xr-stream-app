package blackark.app.vr.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import blackark.app.vr.data.database.entity.QuickAccessFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickAccessFolderDao {
    @Query("SELECT * FROM quick_access_folders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QuickAccessFolder>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: QuickAccessFolder): Long

    @Query("DELETE FROM quick_access_folders WHERE id = :id")
    suspend fun delete(id: Long)
}
