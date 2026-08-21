package blackark.app.vr.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleSidecarMetadataTest {

    @Test
    fun `listing metadata attaches the subtitle selected by playback`() {
        val files = attachKoreanSubtitleSidecarPaths(
            listOf(
                file("sample.mp4"),
                file("sample.ko.srt"),
                file("sample.ko.ass"),
            )
        )

        assertEquals(
            "smb://server/share/sample.ko.ass",
            files.first { file -> file.name == "sample.mp4" }.subtitlePath,
        )
    }

    @Test
    fun `listing metadata does not mark videos with unrelated subtitles`() {
        val files = attachKoreanSubtitleSidecarPaths(
            listOf(
                file("sample.mp4"),
                file("sample.en.srt"),
                file("other.ko.ass"),
            )
        )

        assertNull(files.first { file -> file.name == "sample.mp4" }.subtitlePath)
    }

    private fun file(name: String) = SMBFileItem(
        name = name,
        path = "smb://server/share/$name",
        isDirectory = false,
        size = 0L,
        lastModified = 0L,
    )
}
