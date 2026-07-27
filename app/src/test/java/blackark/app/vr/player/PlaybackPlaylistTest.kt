package blackark.app.vr.player

import blackark.app.vr.network.SMBFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPlaylistTest {

    @Test
    fun `playlist includes videos only and resolves current path case insensitively`() {
        val current = file("B.MP4", "smb://server/share/B.MP4")
        val snapshot = resolvePlaybackPlaylist(
            currentFile = current,
            siblingFiles = listOf(
                file("notes.txt"),
                file("c.mkv"),
                file("B.MP4", "SMB://SERVER/SHARE/B.MP4/"),
                file("a.mp4"),
                file("folder", isDirectory = true),
            ),
        )

        assertEquals(listOf("a.mp4", "B.MP4", "c.mkv"), snapshot.files.map { it.name })
        assertEquals(1, snapshot.currentIndex)
        assertTrue(snapshot.canPlayPrevious)
        assertTrue(snapshot.canPlayNext)
    }

    @Test
    fun `first item disables previous and last item disables next`() {
        val siblings = listOf(file("a.mp4"), file("b.mp4"), file("c.mp4"))

        val first = resolvePlaybackPlaylist(siblings.first(), siblings)
        val last = resolvePlaybackPlaylist(siblings.last(), siblings)

        assertFalse(first.canPlayPrevious)
        assertTrue(first.canPlayNext)
        assertTrue(last.canPlayPrevious)
        assertFalse(last.canPlayNext)
    }

    @Test
    fun `missing current item disables both navigation directions`() {
        val snapshot = resolvePlaybackPlaylist(
            currentFile = file("missing.mp4"),
            siblingFiles = listOf(file("a.mp4"), file("b.mp4")),
        )

        assertEquals(-1, snapshot.currentIndex)
        assertFalse(snapshot.canPlayPrevious)
        assertFalse(snapshot.canPlayNext)
    }

    private fun file(
        name: String,
        path: String = "smb://server/share/$name",
        isDirectory: Boolean = false,
    ) = SMBFileItem(
        name = name,
        path = path,
        isDirectory = isDirectory,
        size = 0L,
        lastModified = 0L,
    )
}
