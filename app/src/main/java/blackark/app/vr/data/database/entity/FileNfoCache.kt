package blackark.app.vr.data.database.entity

import androidx.room.Entity

/** A checked absence is stored too. Failure never replaces a previous successful lookup. */
@Entity(tableName = "file_nfo_cache", primaryKeys = ["sourceScope", "filePath"])
data class FileNfoCache(
    val sourceScope: String,
    val filePath: String,
    val nfoPath: String?,
    val metadataCacheKey: String?,
    val checkedAt: Long,
)
