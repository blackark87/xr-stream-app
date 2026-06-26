package blackark.app.vr.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LocalNfoMetadataResolverTest {

    @Test
    fun `multipart nfo candidates prefer exact part stem before base stem`() {
        assertEquals(
            listOf("CODE-123-cd1.nfo", "CODE-123.nfo", "movie.nfo", "info.nfo"),
            buildNfoCandidateNames("CODE-123-cd1.mp4"),
        )
    }

    @Test
    fun `multipart poster candidates include exact part then base then generic names`() {
        val candidates = buildPosterCandidateNames("CODE-123_part02.mp4")
        assertEquals("CODE-123_part02.jpg", candidates[0])
        assertEquals("CODE-123.jpg", candidates[4])
        assertEquals("poster.jpg", candidates[8])
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
