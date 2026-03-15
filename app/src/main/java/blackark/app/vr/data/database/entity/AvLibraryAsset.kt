package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "av_library_assets",
    indices = [
        Index("sourceScope"),
        Index(value = ["sourceScope", "normalizedCode"], unique = true),
        Index("metadataCacheKey"),
    ],
)
data class AvLibraryAsset(
    @PrimaryKey
    val assetKey: String,
    val sourceScope: String,
    val normalizedCode: String,
    val metadataCacheKey: String? = null,
    val metadataSource: String? = null,
    val representativePath: String? = null,
    val representativeFileName: String? = null,
    val representativeFolderPath: String? = null,
    val cachedTitle: String? = null,
    val cachedPosterUrl: String? = null,
    val cachedStudio: String? = null,
    val cachedReleaseDateEpochDay: Long? = null,
    val hasMetadata: Boolean = false,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val lastScannedAt: Long = System.currentTimeMillis(),
    val metadataResolvedAt: Long? = null,
)
