package com.example.myapplication.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_servers")
data class SavedServer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverName: String,
    val serverAddress: String,
    val port: Int = 445,
    val shareName: String = "", // Optional - empty for root/all shares
    val username: String,
    val password: String, // Note: In production, consider encrypting this
    val domain: String = "",
    val lastConnected: Long = System.currentTimeMillis()
)
