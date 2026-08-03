package blackark.app.vr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VideoThumbnailFrameIdentityTest {

    @Test
    fun `multipart parts share one generated frame identity`() {
        assertEquals(
            generatedFrameFileName("smb://server/share/CODE-123-pt-1.mp4", generation = 7),
            generatedFrameFileName("smb://server/share/CODE-123-pt-2.mp4", generation = 7),
        )
    }

    @Test
    fun `different content ids do not share a generated frame identity`() {
        assertNotEquals(
            generatedFrameFileName("smb://server/share/CODE-123.mp4", generation = 7),
            generatedFrameFileName("smb://server/share/CODE-124.mp4", generation = 7),
        )
    }

    @Test
    fun `generated frame identity is explicitly frame only`() {
        assertEquals(
            "thumb-v3:7:frame:smb://server/share/code-123",
            buildGeneratedFrameIdentity(
                path = "smb://server/share/CODE-123.mp4",
                generation = 7,
            ),
        )
    }

    @Test
    fun `resume frame identity includes exact part and stopped position`() {
        assertNotEquals(
            generatedResumeFrameFileName(
                path = "smb://server/share/CODE-123-pt-1.mp4",
                generation = 7,
                positionMs = 61_200L,
            ),
            generatedResumeFrameFileName(
                path = "smb://server/share/CODE-123-pt-2.mp4",
                generation = 7,
                positionMs = 61_200L,
            ),
        )
        assertNotEquals(
            generatedResumeFrameFileName(
                path = "smb://server/share/CODE-123-pt-1.mp4",
                generation = 7,
                positionMs = 61_200L,
            ),
            generatedResumeFrameFileName(
                path = "smb://server/share/CODE-123-pt-1.mp4",
                generation = 7,
                positionMs = 62_200L,
            ),
        )
    }

    @Test
    fun `resume frame time is bucketed and clamped before video end`() {
        assertEquals(61_000L, normalizeResumeFrameTimeMs(61_987L, durationMs = 120_000L))
        assertEquals(119_000L, normalizeResumeFrameTimeMs(120_000L, durationMs = 120_000L))
    }
}
