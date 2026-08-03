package blackark.app.vr.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import blackark.app.vr.data.database.entity.MetadataScope
import kotlinx.coroutines.flow.Flow

@Dao
interface MetadataScopeDao {
    @Query("SELECT * FROM metadata_scopes ORDER BY serverId, displayPath COLLATE NOCASE")
    fun observeAll(): Flow<List<MetadataScope>>

    @Query("SELECT * FROM metadata_scopes WHERE serverId = :serverId ORDER BY displayPath COLLATE NOCASE")
    fun observeForServer(serverId: Long): Flow<List<MetadataScope>>

    @Query("SELECT * FROM metadata_scopes WHERE enabled = 1 ORDER BY serverId, canonicalPath")
    suspend fun getEnabled(): List<MetadataScope>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(scope: MetadataScope): Long

    @Update
    suspend fun update(scope: MetadataScope)

    @Query("DELETE FROM metadata_scopes WHERE id = :scopeId")
    suspend fun delete(scopeId: Long)

    @Query("DELETE FROM metadata_scopes WHERE serverId = :serverId")
    suspend fun deleteForServer(serverId: Long)
}
