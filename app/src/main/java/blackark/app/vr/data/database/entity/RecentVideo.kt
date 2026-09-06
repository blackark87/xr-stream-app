package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import blackark.app.vr.data.model.LibraryVideoItem

@Entity(tableName = "recent_videos")
data class RecentVideo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    override val fileName: String,
    override val filePath: String, // Full SMB path
    override val serverAddress: String,
    override val shareName: String,
    val lastPlayed: Long = System.currentTimeMillis(),
    override val lastPosition: Long = 0, // Last playback position in milliseconds
    override val duration: Long = 0, // Video duration in milliseconds
    override val thumbnailPath: String? = null, // Path to locally cached thumbnail
    override val resolvedTitle: String? = null,
    val resumeThumbnailPath: String? = null,
    val resumeThumbnailPositionMs: Long? = null,
) : LibraryVideoItem
