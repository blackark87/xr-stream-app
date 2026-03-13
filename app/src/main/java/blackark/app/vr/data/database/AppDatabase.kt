package blackark.app.vr.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import blackark.app.vr.data.database.dao.ServerDao
import blackark.app.vr.data.database.dao.VideoDao
import blackark.app.vr.data.database.dao.VirtualGroupMetadataDao
import blackark.app.vr.data.database.entity.RecentVideo
import blackark.app.vr.data.database.entity.SavedServer
import blackark.app.vr.data.database.entity.VirtualGroupMetadata

@Database(
    entities = [SavedServer::class, RecentVideo::class, VirtualGroupMetadata::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun videoDao(): VideoDao
    abstract fun virtualGroupMetadataDao(): VirtualGroupMetadataDao

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
