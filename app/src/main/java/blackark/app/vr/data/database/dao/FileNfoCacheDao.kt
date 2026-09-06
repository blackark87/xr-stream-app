package blackark.app.vr.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import blackark.app.vr.data.database.entity.FileNfoCache
import kotlinx.coroutines.flow.Flow

@Dao
interface FileNfoCacheDao {
    @Query("SELECT * FROM file_nfo_cache WHERE sourceScope = :sourceScope AND filePath = :filePath")
    suspend fun get(sourceScope: String, filePath: String): FileNfoCache?

    @Query("SELECT * FROM file_nfo_cache WHERE sourceScope = :sourceScope AND filePath = :filePath")
    fun observe(sourceScope: String, filePath: String): Flow<FileNfoCache?>

    @Upsert
    suspend fun upsert(value: FileNfoCache)
}
