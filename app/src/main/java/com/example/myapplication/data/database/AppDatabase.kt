package com.example.myapplication.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.myapplication.data.database.dao.ServerDao
import com.example.myapplication.data.database.dao.VideoDao
import com.example.myapplication.data.database.entity.RecentVideo
import com.example.myapplication.data.database.entity.SavedServer

@Database(
    entities = [SavedServer::class, RecentVideo::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun videoDao(): VideoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xr_video_player_database"
                )
                    .fallbackToDestructiveMigration() // For development - recreates DB on schema changes
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
