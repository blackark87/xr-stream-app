package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A server-owned folder in which online JAV metadata providers may run.
 *
 * [canonicalPath] always includes the source identity (SMB host/share or local tree URI), so two
 * servers with the same relative folder remain isolated.
 */
@Entity(
    tableName = "metadata_scopes",
    foreignKeys = [
        ForeignKey(
            entity = SavedServer::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["serverId", "canonicalPath"], unique = true),
    ],
)
data class MetadataScope(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverId: Long,
    val canonicalPath: String,
    val displayPath: String,
    val includeDescendants: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
