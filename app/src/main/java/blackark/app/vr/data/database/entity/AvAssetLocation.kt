package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "av_asset_locations",
    primaryKeys = ["filePath"],
    foreignKeys = [
        ForeignKey(
            entity = AvLibraryAsset::class,
            parentColumns = ["assetKey"],
            childColumns = ["assetKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("assetKey"),
        Index("sourceScope"),
        Index("contentFingerprint"),
        Index(value = ["sourceScope", "isPresent"]),
        Index(value = ["sourceScope", "size"]),
    ],
)
data class AvAssetLocation(
    val filePath: String,
    val assetKey: String,
    val sourceScope: String,
    val fileName: String,
    val partNumber: Int?,
    val size: Long,
    val lastModified: Long,
    val contentFingerprint: String? = null,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val isPresent: Boolean = true,
)
