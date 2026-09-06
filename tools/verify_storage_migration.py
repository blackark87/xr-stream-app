"""Host SQLite checks against the actual Room schemas, migration SQL and DAO publication query.

Run: python tools/verify_storage_migration.py
This does not install or launch the application or replace Android instrumentation tests.
"""
import json
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATABASE = ROOT / "app/src/main/java/blackark/app/vr/data/database"
SCHEMAS = ROOT / "app/schemas/blackark.app.vr.data.database.AppDatabase"


class StorageMigrationTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.execute("PRAGMA foreign_keys = ON")
        old = json.loads((SCHEMAS / "16.json").read_text(encoding="utf-8"))["database"]
        for entity in old["entities"]:
            self.db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
            for index in entity.get("indices", []):
                self.db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        self.db.execute("""INSERT INTO recent_videos
            (id,fileName,filePath,serverAddress,shareName,lastPlayed,lastPosition,duration,thumbnailPath,resolvedTitle)
            VALUES (1,'movie.mp4','smb://nas/videos/movie.mp4','nas','videos',100,5000,60000,'old.jpg','Saved title')""")
        source = (DATABASE / "AppDatabaseMigrations.kt").read_text(encoding="utf-8")
        migration = source.split("val MIGRATION_16_17 =", 1)[1].split("val ALL:", 1)[0]
        statements = re.findall(r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\s*\)', migration, re.S)
        self.assertEqual(3, len(statements), "All migration statements must be exercised")
        for multiline, single in statements:
            self.db.execute(multiline or single)

    def test_schema_and_history_preserved(self):
        new = json.loads((SCHEMAS / "17.json").read_text(encoding="utf-8"))["database"]
        for entity in new["entities"]:
            fields = {row[1]: row for row in self.db.execute(f'PRAGMA table_info("{entity["tableName"]}")')}
            self.assertEqual({field["columnName"] for field in entity["fields"]}, set(fields))
            for field in entity["fields"]:
                actual = fields[field["columnName"]]
                self.assertEqual(field["affinity"], actual[2])
                self.assertEqual(field.get("notNull", False), bool(actual[3]))
            pk = [r[1] for r in sorted(fields.values(), key=lambda row: row[5]) if r[5]]
            self.assertEqual(entity["primaryKey"]["columnNames"], pk)
        self.assertEqual((5000, 'old.jpg', 'Saved title', None, None), self.db.execute(
            'SELECT lastPosition,thumbnailPath,resolvedTitle,resumeThumbnailPath,resumeThumbnailPositionMs FROM recent_videos'
        ).fetchone())
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_stale_thumbnail_cannot_replace_current_position(self):
        source = (DATABASE / "dao/VideoDao.kt").read_text(encoding="utf-8")
        query = re.search(r'@Query\("""(UPDATE recent_videos.*?)"""\)', source.replace('"""\n        UPDATE', '"""UPDATE'), re.S).group(1)
        values = dict(filePath='smb://nas/videos/movie.mp4', positionMs=5000, thumbnailPath='resume-5.jpg')
        self.assertEqual(1, self.db.execute(query, values).rowcount)
        self.db.execute('UPDATE recent_videos SET lastPosition=9000 WHERE id=1')
        self.assertEqual(0, self.db.execute(query, values).rowcount)
        self.assertEqual('resume-5.jpg', self.db.execute('SELECT resumeThumbnailPath FROM recent_videos').fetchone()[0])
        values.update(positionMs=9000, thumbnailPath='resume-9.jpg')
        self.assertEqual(1, self.db.execute(query, values).rowcount)
        self.db.execute('DELETE FROM recent_videos')
        self.assertEqual(0, self.db.execute(query, values).rowcount)

    def test_nfo_absence_and_source_isolation(self):
        for source in ('nas-a', 'nas-b'):
            self.db.execute('INSERT INTO file_nfo_cache VALUES (?, ?, ?, ?, ?)',
                            (source, '/movies/movie.mp4', 'movie.nfo', source + ':key', 100))
        self.db.execute("UPDATE file_nfo_cache SET nfoPath=NULL,metadataCacheKey=NULL,checkedAt=200 WHERE sourceScope='nas-a'")
        self.assertEqual([(None,), ('nas-b:key',)], self.db.execute(
            'SELECT metadataCacheKey FROM file_nfo_cache ORDER BY sourceScope').fetchall())


if __name__ == "__main__":
    unittest.main(verbosity=2)
