package blackark.app.vr.data.repository

import android.content.Context
import androidx.room.withTransaction
import blackark.app.vr.data.database.AppDatabase
import blackark.app.vr.data.database.entity.FileNfoCache
import blackark.app.vr.utils.JvrLibraryMetadataProvider
import blackark.app.vr.utils.JvrMovieMetadata
import blackark.app.vr.utils.LocalNfoMetadataResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.security.MessageDigest

/** Uses the existing normalized metadata tables; this index also covers files without work codes. */
class FileNfoRepository(context: Context) {
    private val context = context.applicationContext
    private val db = AppDatabase.getDatabase(this.context)
    private val dao = db.fileNfoCacheDao()

    fun observe(sourceScope: String, filePath: String): Flow<JvrMovieMetadata?> =
        dao.observe(sourceScope, filePath).distinctUntilChanged().map { row -> readMetadata(row) }

    suspend fun get(sourceScope: String, filePath: String): JvrMovieMetadata? =
        readMetadata(dao.get(sourceScope, filePath))

    private suspend fun readMetadata(row: FileNfoCache?): JvrMovieMetadata? =
        row?.metadataCacheKey?.let {
            db.virtualGroupMetadataDao().getByCacheKey(it)?.let { record ->
                JvrLibraryMetadataProvider.toMovieMetadata(record, record.metadata.code)
            }
        }

    suspend fun store(sourceScope: String, filePath: String, result: LocalNfoMetadataResult) {
        if (!result.readSuccessful) return
        val metadata = result.metadata
        val key = metadata?.let { fileNfoCacheKey(sourceScope, filePath) }
        db.withTransaction {
            if (key != null) {
                JvrLibraryMetadataProvider.saveManualMetadata(context, key, "local_nfo", metadata)
            }
            dao.upsert(FileNfoCache(sourceScope, filePath, result.nfoPath, key, System.currentTimeMillis()))
        }
    }
}

internal fun fileNfoCacheKey(sourceScope: String, filePath: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$sourceScope\u0000$filePath".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "local_nfo_file:$digest"
}
