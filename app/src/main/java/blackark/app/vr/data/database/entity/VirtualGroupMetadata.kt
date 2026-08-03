package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "virtual_group_metadata",
    indices = [
        Index("code"),
        Index("releaseDateEpochDay"),
        Index("studio"),
    ],
)
data class VirtualGroupMetadata(
    @PrimaryKey
    val cacheKey: String,
    val code: String,
    val source: String,
    val title: String?,
    val posterUrl: String?,
    val releaseDateEpochDay: Long?,
    val studio: String?,
    val isMiss: Boolean,
    val updatedAt: Long,
    val description: String? = null,
    val posterFallbackUrls: String? = null,
)
