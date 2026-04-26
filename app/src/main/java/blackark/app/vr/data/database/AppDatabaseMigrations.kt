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

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_13_14,
    )
}
