package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "virtual_group_metadata")
data class VirtualGroupMetadata(
    @PrimaryKey
    val cacheKey: String,
    val code: String,
    val source: String,
    val title: String?,
    val posterUrl: String?,
    val isMiss: Boolean,
    val updatedAt: Long,
)
