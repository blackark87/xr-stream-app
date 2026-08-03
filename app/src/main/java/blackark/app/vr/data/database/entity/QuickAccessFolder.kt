package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quick_access_folders",
    foreignKeys = [
        ForeignKey(
            entity = SavedServer::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("serverId"),
        Index(value = ["serverId", "path"], unique = true),
    ],
)
data class QuickAccessFolder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverId: Long,
    val path: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis(),
)
