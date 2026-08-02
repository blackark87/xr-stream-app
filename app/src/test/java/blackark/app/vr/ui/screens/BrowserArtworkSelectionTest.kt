package blackark.app.vr.ui.screens

import blackark.app.vr.utils.JvrMovieMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserArtworkSelectionTest {

    @Test
    fun `loading metadata keeps a placeholder and does not request extraction`() {
        val selection = selectVideoArtwork(
            metadataState = ArtworkState.Loading,
            posterLoadFailed = false,
            videoPath = "smb://server/share/FRIN-116.mp4",
        )

        assertTrue(selection is VideoArtworkSelection.Placeholder)
    }

    @Test
    fun `resolved NFO poster uses only the poster`() {
        val selection = selectVideoArtwork(
            metadataState = ArtworkState.Resolved(metadata("smb://server/share/FRIN-116-poster.jpg")),
            posterLoadFailed = false,
            videoPath = "smb://server/share/FRIN-116.mp4",
        )

        assertEquals(
            VideoArtworkSelection.Poster("smb://server/share/FRIN-116-poster.jpg"),
            selection,
        )
    }

    @Test
    fun `metadata miss starts frame fallback`() {
        val selection = selectVideoArtwork(
            metadataState = ArtworkState.Missing,
            posterLoadFailed = false,
            videoPath = "smb://server/share/NEW-001.mp4",
        )

        assertEquals(
            VideoArtworkSelection.GeneratedFrame("smb://server/share/NEW-001.mp4"),
            selection,
        )
    }

    @Test
    fun `poster load failure starts frame fallback`() {
        val selection = selectVideoArtwork(
            metadataState = ArtworkState.Resolved(metadata("smb://server/share/missing-poster.jpg")),
            posterLoadFailed = true,
            videoPath = "smb://server/share/FRIN-116.mp4",
        )

        assertEquals(
            VideoArtworkSelection.GeneratedFrame("smb://server/share/FRIN-116.mp4"),
            selection,
        )
    }

    @Test
    fun `resolved metadata without poster starts frame fallback`() {
        val selection = selectVideoArtwork(
            metadataState = ArtworkState.Resolved(metadata(null)),
            posterLoadFailed = false,
            videoPath = "smb://server/share/FRIN-116.mp4",
        )

        assertEquals(
            VideoArtworkSelection.GeneratedFrame("smb://server/share/FRIN-116.mp4"),
            selection,
        )
    }

    private fun metadata(posterUrl: String?) = JvrMovieMetadata(
        code = "FRIN-116",
        title = "FRIN-116",
        posterUrl = posterUrl,
    )
}
