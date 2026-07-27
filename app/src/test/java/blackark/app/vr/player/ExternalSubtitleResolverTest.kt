package blackark.app.vr.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import blackark.app.vr.network.SMBFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSubtitleResolverTest {

    @Test
    fun `ass is preferred when both Korean subtitle formats exist`() {
        val subtitle = resolveKoreanExternalSubtitle(
            videoFileName = "sample.180.mp4",
            siblingFiles = listOf(
                file("sample.180.ko.srt"),
                file("sample.180.ko.ass"),
            ),
        )

        assertEquals("sample.180.ko.ass", subtitle?.file?.name)
        assertEquals(MimeTypes.TEXT_SSA, subtitle?.mimeType)
    }

    @Test
    fun `srt is used when ass is absent`() {
        val subtitle = resolveKoreanExternalSubtitle(
            videoFileName = "sample.mkv",
            siblingFiles = listOf(file("sample.ko.srt")),
        )

        assertEquals("sample.ko.srt", subtitle?.file?.name)
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitle?.mimeType)
    }

    @Test
    fun `matching is case insensitive but requires the complete base name`() {
        val subtitle = resolveKoreanExternalSubtitle(
            videoFileName = "Feature.Part.1.MKV",
            siblingFiles = listOf(
                file("Feature.ko.ass"),
                file("feature.part.1.KO.ASS"),
            ),
        )

        assertEquals("feature.part.1.KO.ASS", subtitle?.file?.name)
    }

    @Test
    fun `directories and unrelated subtitle names are ignored`() {
        val subtitle = resolveKoreanExternalSubtitle(
            videoFileName = "sample.mp4",
            siblingFiles = listOf(
                file("sample.ko.ass", isDirectory = true),
                file("sample.en.srt"),
                file("other.ko.ass"),
            ),
        )

        assertNull(subtitle)
    }

    @Test
    fun `direct candidates preserve the video URL and prefer ass`() {
        val candidates = buildKoreanExternalSubtitleCandidates(
            SMBFileItem(
                name = "SAVR-420-pt1.mp4",
                path = "smb://server/share/folder.with.dot/SAVR-420-pt1.mp4",
                isDirectory = false,
                size = 100L,
                lastModified = 200L,
            ),
        )

        assertEquals("SAVR-420-pt1.ko.ass", candidates[0].file.name)
        assertEquals(
            "smb://server/share/folder.with.dot/SAVR-420-pt1.ko.ass",
            candidates[0].file.path,
        )
        assertEquals(MimeTypes.TEXT_SSA, candidates[0].mimeType)
        assertEquals("SAVR-420-pt1.ko.srt", candidates[1].file.name)
    }

    @Test
    fun `Korean external subtitles are explicitly selected`() {
        val parameters = configureKoreanExternalSubtitle(
            parameters = TrackSelectionParameters.DEFAULT,
            enabled = true,
        )

        assertTrue(parameters.selectTextByDefault)
        assertEquals(listOf("ko"), parameters.preferredTextLanguages)
        assertTrue(parameters.selectUndeterminedTextLanguage)
        assertFalse(parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }

    @Test
    fun `Korean external subtitles can be disabled`() {
        val parameters = configureKoreanExternalSubtitle(
            parameters = TrackSelectionParameters.DEFAULT,
            enabled = false,
        )

        assertFalse(parameters.selectTextByDefault)
        assertTrue(parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }

    private fun file(
        name: String,
        isDirectory: Boolean = false,
    ) = SMBFileItem(
        name = name,
        path = "smb://server/share/$name",
        isDirectory = isDirectory,
        size = 0L,
        lastModified = 0L,
    )
}
