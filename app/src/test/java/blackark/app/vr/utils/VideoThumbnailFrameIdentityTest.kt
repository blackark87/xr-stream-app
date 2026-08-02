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
}
