package blackark.app.vr.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {

    val MIGRATION_13_14 =
        object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema is unchanged in v14. This migration formalizes the forward-compatible
                // migration path and preserves existing user data instead of dropping the DB.
            }
        }

    val MIGRATION_14_15 =
        object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE virtual_group_metadata ADD COLUMN description TEXT")
            }
        }

    /**
     * Removes legacy plaintext SMB columns and introduces server-owned metadata folders. The app
     * also performs the product-requested one-time reset before opening this schema; this migration
     * is retained as a defensive path for databases opened outside the normal Application flow.
     */
    val MIGRATION_15_16 =
        object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE virtual_group_metadata " +
                        "ADD COLUMN posterFallbackUrls TEXT"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS saved_servers_secure (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        serverName TEXT NOT NULL,
                        serverAddress TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        shareName TEXT NOT NULL,
                        credentialAlias TEXT NOT NULL,
                        lastConnected INTEGER NOT NULL,
                        isLocalStorage INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO saved_servers_secure (
                        id, serverName, serverAddress, port, shareName, credentialAlias,
                        lastConnected, isLocalStorage
                    )
                    SELECT id, serverName, serverAddress, port, shareName, '',
                           lastConnected, isLocalStorage
                    FROM saved_servers
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE saved_servers")
                db.execSQL("ALTER TABLE saved_servers_secure RENAME TO saved_servers")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS metadata_scopes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        serverId INTEGER NOT NULL,
                        canonicalPath TEXT NOT NULL,
                        displayPath TEXT NOT NULL,
                        includeDescendants INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(serverId) REFERENCES saved_servers(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_metadata_scopes_serverId " +
                        "ON metadata_scopes(serverId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_metadata_scopes_serverId_canonicalPath " +
                        "ON metadata_scopes(serverId, canonicalPath)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quick_access_folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        serverId INTEGER NOT NULL,
                        path TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(serverId) REFERENCES saved_servers(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_quick_access_folders_serverId " +
                        "ON quick_access_folders(serverId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_quick_access_folders_serverId_path " +
                        "ON quick_access_folders(serverId, path)"
                )
            }
        }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
    )
}
