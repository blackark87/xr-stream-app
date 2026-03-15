package blackark.app.vr.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import blackark.app.vr.data.database.dao.FavoriteVideoDao
import blackark.app.vr.data.database.dao.ServerDao
import blackark.app.vr.data.database.dao.AvLibraryDao
import blackark.app.vr.data.database.dao.VideoDao
import blackark.app.vr.data.database.dao.VideoDisplaySettingsDao
import blackark.app.vr.data.database.dao.VirtualGroupMetadataDao
import blackark.app.vr.data.database.entity.AvAssetLocation
import blackark.app.vr.data.database.entity.AvLibraryAsset
import blackark.app.vr.data.database.entity.FavoriteVideo
import blackark.app.vr.data.database.entity.JvrPerformerAlias
import blackark.app.vr.data.database.entity.JvrPerformerMergeRule
import blackark.app.vr.data.database.entity.JvrPerformer
import blackark.app.vr.data.database.entity.RecentVideo
import blackark.app.vr.data.database.entity.SavedServer
import blackark.app.vr.data.database.entity.VideoDisplaySettings
import blackark.app.vr.data.database.entity.VirtualGroupMetadata
import blackark.app.vr.data.database.entity.VirtualGroupMetadataGenre
import blackark.app.vr.data.database.entity.VirtualGroupMetadataPerformerCrossRef

@Database(
    entities = [
        SavedServer::class,
        RecentVideo::class,
        FavoriteVideo::class,
        VideoDisplaySettings::class,
        AvLibraryAsset::class,
        AvAssetLocation::class,
        VirtualGroupMetadata::class,
        VirtualGroupMetadataGenre::class,
        JvrPerformer::class,
        JvrPerformerAlias::class,
        JvrPerformerMergeRule::class,
        VirtualGroupMetadataPerformerCrossRef::class,
    ],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun videoDao(): VideoDao
    abstract fun favoriteVideoDao(): FavoriteVideoDao
    abstract fun videoDisplaySettingsDao(): VideoDisplaySettingsDao
    abstract fun avLibraryDao(): AvLibraryDao
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
                    .fallbackToDestructiveMigration(
                        dropAllTables = true
                    ) // For development - recreates DB on schema changes
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
