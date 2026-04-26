package blackark.app.vr.data.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import blackark.app.vr.data.database.entity.FavoriteVideo
import blackark.app.vr.data.database.entity.RecentVideo
import blackark.app.vr.data.database.entity.SavedServer
import blackark.app.vr.data.database.entity.VideoDisplaySettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val testDatabaseName = "app-database-migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(testDatabaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(testDatabaseName)
    }

    @Test
    fun migrate13To14_preservesCoreAndAvLibraryData() = runBlocking {
        val preMigrationDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, testDatabaseName)
                .allowMainThreadQueries()
                .build()

        val serverId =
            preMigrationDatabase.serverDao().insertServer(
                SavedServer(
                    serverName = "Test Server",
                    serverAddress = "192.168.0.10",
                    port = 445,
                    shareName = "videos",
                    username = "tester",
                    password = "secret",
                    domain = "workgroup",
                    lastConnected = 1_710_000_000_000,
                    isLocalStorage = false,
                ),
            )

        preMigrationDatabase.videoDao().insertVideo(
            RecentVideo(
                fileName = "sample-180-sbs.mp4",
                filePath = "smb://192.168.0.10/videos/sample-180-sbs.mp4",
                serverAddress = "192.168.0.10",
                shareName = "videos",
                lastPlayed = 1_710_000_100_000,
                lastPosition = 90_000,
                duration = 600_000,
                thumbnailPath = "/data/user/0/blackark.app.vr/files/thumbnails/sample.jpg",
                resolvedTitle = "Sample 180 SBS",
            ),
        )

        preMigrationDatabase.favoriteVideoDao().upsertFavorite(
            FavoriteVideo(
                filePath = "smb://192.168.0.10/videos/sample-180-sbs.mp4",
                fileName = "sample-180-sbs.mp4",
                serverAddress = "192.168.0.10",
                shareName = "videos",
                addedAt = 1_710_000_200_000,
                thumbnailPath = "/data/user/0/blackark.app.vr/files/thumbnails/sample.jpg",
                resolvedTitle = "Sample 180 SBS",
            ),
        )

        preMigrationDatabase.videoDisplaySettingsDao().upsert(
            VideoDisplaySettings(
                filePath = "smb://192.168.0.10/videos/sample-180-sbs.mp4",
                videoFormat = "Format180",
                stereoMode = "SideBySide",
                updatedAt = 1_710_000_300_000,
            ),
        )

        preMigrationDatabase.openHelper.writableDatabase.apply {
            execSQL(
                """
                INSERT INTO av_library_assets (
                    assetKey, sourceScope, normalizedCode, metadataCacheKey, metadataSource,
                    representativePath, representativeFileName, representativeFolderPath,
                    cachedTitle, cachedPosterUrl, cachedStudio, cachedReleaseDateEpochDay,
                    hasMetadata, lastSeenAt, lastScannedAt, metadataResolvedAt
                ) VALUES (
                    'asset-1', '192.168.0.10::videos', 'SAMPLE-001', 'cache-1', 'jvr',
                    'smb://192.168.0.10/videos/sample-180-sbs.mp4', 'sample-180-sbs.mp4', '/videos',
                    'Sample 180 SBS', '/posters/sample.jpg', 'Studio A', 20000,
                    1, 1710000400000, 1710000400000, 1710000400000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO av_asset_locations (
                    filePath, assetKey, sourceScope, fileName, partNumber, size, lastModified,
                    contentFingerprint, lastSeenAt, isPresent
                ) VALUES (
                    'smb://192.168.0.10/videos/sample-180-sbs.mp4', 'asset-1',
                    '192.168.0.10::videos', 'sample-180-sbs.mp4', 1, 123456789, 1710000300000,
                    'fingerprint-1', 1710000400000, 1
                )
                """.trimIndent(),
            )
        }

        preMigrationDatabase.close()

        val databaseFile = context.getDatabasePath(testDatabaseName)
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.version = 13
        }

        val migratedDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, testDatabaseName)
                .allowMainThreadQueries()
                .addMigrations(*AppDatabaseMigrations.ALL)
                .build()

        assertEquals(14, migratedDatabase.openHelper.readableDatabase.version)
        assertEquals(serverId, migratedDatabase.serverDao().getServerById(serverId)?.id)

        val migratedRecent =
            migratedDatabase.videoDao().getVideoByPath(
                "smb://192.168.0.10/videos/sample-180-sbs.mp4",
            )
        assertNotNull(migratedRecent)
        assertEquals(90_000L, migratedRecent?.lastPosition)
        assertEquals(600_000L, migratedRecent?.duration)
        assertEquals("Sample 180 SBS", migratedRecent?.resolvedTitle)

        val migratedFavorite =
            migratedDatabase.favoriteVideoDao().getFavoriteByPath(
                "smb://192.168.0.10/videos/sample-180-sbs.mp4",
            )
        assertNotNull(migratedFavorite)
        assertEquals("Sample 180 SBS", migratedFavorite?.resolvedTitle)

        val migratedDisplaySettings =
            migratedDatabase.videoDisplaySettingsDao().getByPath(
                "smb://192.168.0.10/videos/sample-180-sbs.mp4",
            )
        assertNotNull(migratedDisplaySettings)
        assertEquals("Format180", migratedDisplaySettings?.videoFormat)
        assertEquals("SideBySide", migratedDisplaySettings?.stereoMode)

        migratedDatabase.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM av_library_assets WHERE assetKey = 'asset-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }

        migratedDatabase.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM av_asset_locations WHERE assetKey = 'asset-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }

        migratedDatabase.close()
    }
}
