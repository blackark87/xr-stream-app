package blackark.app.vr.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import blackark.app.vr.data.database.entity.SavedServer
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM saved_servers ORDER BY lastConnected DESC")
    fun getAllServers(): Flow<List<SavedServer>>

    @Query("SELECT * FROM saved_servers WHERE id = :serverId")
    suspend fun getServerById(serverId: Long): SavedServer?

    @Query("SELECT * FROM saved_servers WHERE serverAddress = :address AND shareName = :share LIMIT 1")
    suspend fun getServerByAddress(address: String, share: String): SavedServer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: SavedServer): Long

    @Update
    suspend fun updateServer(server: SavedServer)

    @Delete
    suspend fun deleteServer(server: SavedServer)

    @Query("DELETE FROM saved_servers WHERE id = :serverId")
    suspend fun deleteServerById(serverId: Long)

    @Query("UPDATE saved_servers SET lastConnected = :timestamp WHERE id = :serverId")
    suspend fun updateLastConnected(serverId: Long, timestamp: Long)
}
