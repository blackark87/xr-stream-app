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
    val duration: Long = 0 // Video duration in milliseconds
)
