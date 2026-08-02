package blackark.app.vr.ui.screens

import blackark.app.vr.utils.JvrMovieMetadata
import blackark.app.vr.network.SMBFileItem
import blackark.app.vr.utils.BrowserFolderArtworkResolution
import blackark.app.vr.utils.BrowserFolderArtworkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserArtworkSelectionTest {

    @Test
    fun `only japan children are resolved as actor folders`() {
        assertEquals(
            BrowserFolderArtworkKind.ACTOR,
            resolveChildFolderArtworkKind("smb://server/Videos/AV/japan/"),
        )
        assertEquals(
            BrowserFolderArtworkKind.CONTENT,
            resolveChildFolderArtworkKind("smb://server/Videos/AV/japan/모리 히나코"),
        )
        assertEquals(
            BrowserFolderArtworkKind.CONTENT,
            resolveChildFolderArtworkKind("smb://server/Videos/AV/japan/모리 히나코/MIKR-109"),
        )
        assertEquals(
            BrowserFolderArtworkKind.ACTOR,
            resolveChildFolderArtworkKind(
                "content://provider/tree/volume/document/volume%3AVideos%2FAV%2Fjapan"
            ),
        )
    }

    @Test
    fun `content folder base name is decoded from SMB and SAF paths`() {
        assertEquals(
            "MIKR-109",
            resolveCurrentFolderBaseName("Videos/AV/japan/모리 히나코/MIKR-109"),
        )
        assertEquals(
            "MIKR-109",
            resolveCurrentFolderBaseName(
                "content://provider/tree/volume/document/volume%3AVideos%2FAV%2Fjapan%2FMIKR-109"
            ),
        )
    }

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

    @Test
    fun `work folder uses its NFO poster instead of actor image`() {
        val selection = selectFolderArtwork(
            artworkState = ArtworkState.Resolved(
                BrowserFolderArtworkResolution(
                    metadata = metadata("smb://server/share/MIKR-109/MIKR-109-poster.jpg"),
                    representativeVideo = video("MIKR-109.mp4"),
                    actorArtworkUrl = "smb://server/share/MIKR-109/.actors/모리 히나코.jpg",
                )
            ),
            imageLoadFailed = false,
        )

        assertEquals(
            FolderArtworkSelection.Poster(
                "smb://server/share/MIKR-109/MIKR-109-poster.jpg"
            ),
            selection,
        )
    }

    @Test
    fun `loading work folder metadata does not request its representative frame`() {
        val selection = selectFolderArtwork(
            artworkState = ArtworkState.Loading,
            imageLoadFailed = false,
        )

        assertTrue(selection is FolderArtworkSelection.Placeholder)
    }

    @Test
    fun `work folder without a poster extracts its representative video instead of using actor image`() {
        val selection = selectFolderArtwork(
            artworkState = ArtworkState.Resolved(
                BrowserFolderArtworkResolution(
                    metadata = metadata(null),
                    representativeVideo = video("MIKR-109.mp4"),
                    actorArtworkUrl = "smb://server/share/MIKR-109/.actors/모리 히나코.jpg",
                )
            ),
            imageLoadFailed = false,
        )

        assertEquals(
            FolderArtworkSelection.GeneratedFrame("smb://server/share/MIKR-109.mp4"),
            selection,
        )
    }

    @Test
    fun `performer folder without a direct video uses actor image`() {
        val selection = selectFolderArtwork(
            artworkState = ArtworkState.Resolved(
                BrowserFolderArtworkResolution(
                    actorArtworkUrl = "smb://server/share/모리 히나코/.actors/모리 히나코.jpg",
                )
            ),
            imageLoadFailed = false,
        )

        assertEquals(
            FolderArtworkSelection.ActorImage(
                "smb://server/share/모리 히나코/.actors/모리 히나코.jpg"
            ),
            selection,
        )
    }

    @Test
    fun `failed actor image returns a folder icon and never a video frame`() {
        val selection = selectFolderArtwork(
            artworkState = ArtworkState.Resolved(
                BrowserFolderArtworkResolution(
                    actorArtworkUrl = "smb://server/share/모리 히나코/.actors/모리 히나코.jpg",
                )
            ),
            imageLoadFailed = true,
        )

        assertTrue(selection is FolderArtworkSelection.FolderIcon)
    }

    private fun metadata(posterUrl: String?) = JvrMovieMetadata(
        code = "FRIN-116",
        title = "FRIN-116",
        posterUrl = posterUrl,
    )

    private fun video(name: String) = SMBFileItem(
        name = name,
        path = "smb://server/share/$name",
        isDirectory = false,
        size = 1L,
        lastModified = 0L,
    )
}
