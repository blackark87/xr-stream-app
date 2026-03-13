package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "virtual_group_metadata_performers",
    primaryKeys = ["cacheKey", "performerId"],
    foreignKeys = [
        ForeignKey(
            entity = VirtualGroupMetadata::class,
            parentColumns = ["cacheKey"],
            childColumns = ["cacheKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = JvrPerformer::class,
            parentColumns = ["performerId"],
            childColumns = ["performerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("cacheKey"),
        Index("performerId"),
        Index(value = ["cacheKey", "position"], unique = true),
    ],
)
data class VirtualGroupMetadataPerformerCrossRef(
    val cacheKey: String,
    val performerId: String,
    val position: Int,
)
