package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import blackark.app.vr.data.model.LibraryVideoItem

@Entity(tableName = "favorite_videos")
data class FavoriteVideo(
    @PrimaryKey
    override val filePath: String,
    override val fileName: String,
    override val serverAddress: String,
    override val shareName: String,
    val addedAt: Long = System.currentTimeMillis(),
    override val thumbnailPath: String? = null,
    override val resolvedTitle: String? = null,
) : LibraryVideoItem
