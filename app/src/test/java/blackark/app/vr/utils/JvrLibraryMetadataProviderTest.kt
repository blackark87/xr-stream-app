package blackark.app.vr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JvrLibraryMetadataProviderTest {

    @Test
    fun parsePrvr88Fixture_extractsStructuredMetadata() {
        val html = loadFixture("jvrlibrary/prvr-88.html")

        val metadata = JvrLibraryMetadataProvider.parseJvrMetadataHtml(
            code = "PRVR-88",
            html = html,
        )

        assertNotNull(metadata)
        requireNotNull(metadata)

        assertEquals(LocalDate.parse("2026-03-09"), metadata.releaseDate)
        assertEquals("PREMIUM", metadata.studio)
        assertEquals(
            "https://jvrlibrary.com/static/mono/actjpgs/thumbnail/yuzuriha_karen.jpg",
            metadata.casts.firstOrNull()?.profileImageUrl,
        )
        assertEquals("performer:楪カレン", metadata.casts.firstOrNull()?.performerId)
        assertEquals("Karen Yuzuriha", metadata.casts.firstOrNull()?.englishName)
        assertEquals("楪カレン", metadata.casts.firstOrNull()?.japaneseName)
        assertTrue(metadata.genres.contains("Older Sister"))
        assertTrue(metadata.genres.contains("Big Tits"))
        assertEquals(
            listOf(
                "Older Sister",
                "Big Tits",
                "Featured Actress",
                "Kiss Kiss",
                "Creampie",
                "Titty Fuck",
                "Exclusive Distribution",
                "VR Exclusive",
                "High-Quality VR",
                "8KVR",
            ),
            metadata.genres,
        )
    }

    @Test
    fun persistedMetadata_roundTripsReleaseDateGenresAndCasts() {
        val metadata = requireNotNull(
            JvrLibraryMetadataProvider.parseJvrMetadataHtml(
                code = "PRVR-88",
                html = loadFixture("jvrlibrary/prvr-88.html"),
            )
        )

        val persistedRecord = JvrLibraryMetadataProvider.toPersistedRecord(
            cacheKey = "jvr:PRVR-88",
            source = "jvr",
            metadata = metadata,
            updatedAt = 1_741_484_800_000L,
        )

        assertEquals(LocalDate.parse("2026-03-09").toEpochDay(), persistedRecord.metadata.releaseDateEpochDay)
        assertEquals("PREMIUM", persistedRecord.metadata.studio)
        assertEquals("Older Sister", persistedRecord.genres.firstOrNull()?.genre)
        assertEquals(1, persistedRecord.performers.size)
        assertEquals(1, persistedRecord.performerRefs.size)
        assertEquals("performer:楪カレン", persistedRecord.performers.firstOrNull()?.performerId)
        assertEquals("Karen Yuzuriha", persistedRecord.performers.firstOrNull()?.englishName)
        assertEquals(
            "https://jvrlibrary.com/static/mono/actjpgs/thumbnail/yuzuriha_karen.jpg",
            persistedRecord.performers.firstOrNull()?.remoteProfileImageUrl,
        )
        assertEquals("performer:楪カレン", persistedRecord.performerRefs.firstOrNull()?.performerId)

        val restoredMetadata = JvrLibraryMetadataProvider.toMovieMetadata(persistedRecord)

        assertEquals(metadata.releaseDate, restoredMetadata.releaseDate)
        assertEquals(metadata.studio, restoredMetadata.studio)
        assertEquals(metadata.genres, restoredMetadata.genres)
        assertEquals(metadata.casts, restoredMetadata.casts)
    }

    @Test
    fun parseMida592Fixture_extractsAvWikiMetadata() {
        val html = loadFixture("avwiki/mida-592.html")

        val metadata = JvrLibraryMetadataProvider.parseAvWikiMetadataHtml(
            code = "MIDA-592",
            html = html,
        )

        assertNotNull(metadata)
        requireNotNull(metadata)

        assertEquals(LocalDate.parse("2026-03-13"), metadata.releaseDate)
        assertEquals("ムーディーズ", metadata.studio)
        assertTrue(metadata.genres.isEmpty())
        assertEquals(1, metadata.casts.size)
        assertEquals("performer:綾瀬まりあ", metadata.casts.firstOrNull()?.performerId)
        assertEquals("綾瀬まりあ", metadata.casts.firstOrNull()?.englishName)
        assertEquals("綾瀬まりあ", metadata.casts.firstOrNull()?.japaneseName)
        assertEquals(null, metadata.casts.firstOrNull()?.profileImageUrl)
    }

    private fun loadFixture(path: String): String {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Missing fixture: $path"
        }
        return stream.bufferedReader().use { it.readText() }
    }
}
