package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "virtual_group_metadata_genres",
    primaryKeys = ["cacheKey", "position"],
    foreignKeys = [
        ForeignKey(
            entity = VirtualGroupMetadata::class,
            parentColumns = ["cacheKey"],
            childColumns = ["cacheKey"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("cacheKey"),
        Index("genre"),
    ],
)
data class VirtualGroupMetadataGenre(
    val cacheKey: String,
    val position: Int,
    val genre: String,
)
