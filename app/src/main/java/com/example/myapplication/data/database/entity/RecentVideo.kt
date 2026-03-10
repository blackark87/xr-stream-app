package com.example.myapplication.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_videos")
data class RecentVideo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String, // Full SMB path
    val serverAddress: String,
    val shareName: String,
    val lastPlayed: Long = System.currentTimeMillis(),
    val lastPosition: Long = 0, // Last playback position in milliseconds
    val duration: Long = 0, // Video duration in milliseconds
    val isFavorite: Boolean = false, // Mark as favorite for quick access
    val videoFormat: String = "Format2D", // Saved video format (Format2D, Format180, Format360)
    val stereoMode: String = "Mono", // Saved stereo mode (Mono, SideBySide, TopBottom)
    val thumbnailPath: String? = null, // Path to locally cached thumbnail
    val resolvedTitle: String? = null,
)