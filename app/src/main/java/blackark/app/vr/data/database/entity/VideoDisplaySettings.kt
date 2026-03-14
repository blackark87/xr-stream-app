package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_display_settings")
data class VideoDisplaySettings(
    @PrimaryKey
    val filePath: String,
    val videoFormat: String = "Format2D",
    val stereoMode: String = "Mono",
    val updatedAt: Long = System.currentTimeMillis(),
)
