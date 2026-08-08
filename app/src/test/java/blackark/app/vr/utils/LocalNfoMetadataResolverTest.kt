package blackark.app.vr.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LocalNfoMetadataResolverTest {

    @Test
    fun `multipart nfo candidates prefer exact part stem before base stem`() {
        assertEquals(
            listOf("CODE-123-pt1.nfo", "CODE-123.nfo", "movie.nfo", "info.nfo"),
            buildNfoCandidateNames("CODE-123-pt1.mp4"),
        )
    }

    @Test
    fun `multipart poster candidates prioritize content poster then content image and generic poster`() {
        val candidates = buildPosterCandidateNames("CODE-123_pt02.mp4")
        assertEquals("CODE-123_pt02-poster.jpg", candidates[0])
        assertEquals("CODE-123-poster.jpg", candidates[4])
        assertEquals("CODE-123_pt02.jpg", candidates[8])
        assertEquals("CODE-123.jpg", candidates[12])
        assertEquals("poster.jpg", candidates[16])
        assertEquals("fanart.jpg", candidates[20])
        assertEquals("cover.jpg", candidates[24])
        assertEquals("folder.jpg", candidates[28])
    }

    @Test
    fun `preferred group base puts the shared NFO and poster first`() {
        assertEquals(
            "CODE-123.nfo",
            buildNfoCandidateNames("CODE-123-pt-1.mp4", "CODE-123").first(),
        )
        assertEquals(
            "CODE-123-poster.jpg",
            buildPosterCandidateNames("CODE-123-pt-1.mp4", "CODE-123").first(),
        )
    }

    @Test
    fun `NFO artwork references separate poster generic thumb and fanart`() {
        val references = extractNfoArtworkReferences(
            """
                <movie>
                  <poster>images/CODE-123-poster.jpg</poster>
                  <thumb aspect="poster">https://example.com/poster.jpg</thumb>
                  <thumb>CODE-123-thumb.png</thumb>
                  <fanart><thumb>fanart.webp</thumb></fanart>
                  <actor><name>Actor</name><thumb>actor.jpg</thumb></actor>
                </movie>
            """.trimIndent(),
        )

        assertEquals(
            listOf("images/CODE-123-poster.jpg", "https://example.com/poster.jpg"),
            references.poster,
        )
        assertEquals(listOf("CODE-123-thumb.png"), references.thumb)
        assertEquals(listOf("fanart.webp"), references.fanart)
    }

    @Test
    fun `SMB NFO artwork references support folder relative and absolute URLs`() {
        val videoPath = "smb://server/share/MIKR-109/MIKR-109.mp4"

        assertEquals(
            "smb://server/share/MIKR-109/images/poster.jpg",
            resolveSmbNfoArtworkReference(videoPath, "images/poster.jpg"),
        )
        assertEquals(
            "https://example.com/poster.jpg",
            resolveSmbNfoArtworkReference(videoPath, "https://example.com/poster.jpg"),
        )
        assertEquals(
            "content://provider/poster",
            resolveSmbNfoArtworkReference(videoPath, "content://provider/poster"),
        )
    }

    @Test
    fun `single part candidates do not duplicate base stem names`() {
        assertEquals(
            listOf("CODE-123.nfo", "movie.nfo", "info.nfo"),
            buildNfoCandidateNames("CODE-123.mp4"),
        )
    }

    @Test
    fun `merge local first keeps local fields and fills missing remote fields`() {
        val local = JvrMovieMetadata(
            code = "CODE-123",
            title = "",
            posterUrl = null,
            releaseDate = null,
            studio = "Local Studio",
            genres = emptyList(),
            casts = emptyList(),
            description = null,
        )
        val remote = JvrMovieMetadata(
            code = "CODE-123",
            title = "Remote Title",
            posterUrl = "poster.jpg",
            releaseDate = LocalDate.of(2024, 3, 14),
            studio = "Remote Studio",
            genres = listOf("VR"),
            casts = listOf(JvrCastMetadata(performerId = "a", englishName = "Actor")),
            description = "Remote Description",
        )

        val merged = local.mergeLocalFirst(remote)

        assertEquals("Remote Title", merged.title)
        assertEquals("poster.jpg", merged.posterUrl)
        assertEquals(LocalDate.of(2024, 3, 14), merged.releaseDate)
        assertEquals("Local Studio", merged.studio)
        assertEquals(listOf("VR"), merged.genres)
        assertEquals("Remote Description", merged.description)
        assertEquals(1, merged.casts.size)
    }
}
