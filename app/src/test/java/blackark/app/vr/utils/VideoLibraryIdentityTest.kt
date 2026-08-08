package blackark.app.vr.utils

import blackark.app.vr.network.SMBFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoLibraryIdentityTest {

    @Test
    fun `numeric suffixes remain separate files`() {
        assertNull(extractVirtualGroupKey("CODE-123-01.mp4"))
        assertNull(extractVirtualGroupPart("CODE-123-01.mp4"))
        assertNull(extractVirtualGroupKey("CODE-123-02.mp4"))
        assertNull(extractVirtualGroupPart("CODE-123-02.mp4"))
        assertNull(extractVirtualGroupKey("CODE-123_2.mp4"))
        assertNull(extractVirtualGroupPart("CODE-123_2.mp4"))
    }

    @Test
    fun `extract virtual group key supports explicit multipart markers`() {
        assertEquals("CODE-123", extractVirtualGroupKey("CODE-123 pt02.mp4"))
        assertEquals(2, extractVirtualGroupPart("CODE-123 pt02.mp4"))
        assertEquals("CODE-123", extractVirtualGroupKey("CODE-123-pt-1.mp4"))
        assertEquals(1, extractVirtualGroupPart("CODE-123-pt-1.mp4"))
        assertEquals("CODE-123", extractVirtualGroupKey("CODE-123_PT10.mp4"))
        assertEquals(10, extractVirtualGroupPart("CODE-123_PT10.mp4"))
        assertEquals("CODE-123", extractVirtualGroupKey("CODE-123-cd1.mp4"))
        assertEquals(1, extractVirtualGroupPart("CODE-123-cd1.mp4"))
        assertEquals("CODE-123", extractVirtualGroupKey("CODE-123-cd-2.mp4"))
        assertEquals(2, extractVirtualGroupPart("CODE-123-cd-2.mp4"))
        assertEquals("CODE-123", extractVirtualGroupKey("CODE-123_part3.mp4"))
        assertEquals(3, extractVirtualGroupPart("CODE-123_part3.mp4"))
        assertEquals("CODE-123", extractVirtualGroupKey("CODE-123-part-4.mp4"))
        assertEquals(4, extractVirtualGroupPart("CODE-123-part-4.mp4"))
    }

    @Test
    fun `extract virtual group key ignores non multipart names`() {
        assertNull(extractVirtualGroupKey("CODE-123.mp4"))
        assertNull(extractVirtualGroupPart("CODE-123.mp4"))
        assertNull(extractVirtualGroupKey("feature-film.mp4"))
        assertNull(extractVirtualGroupPart("feature-film.mp4"))
    }

    @Test
    fun `only two or more multipart files become one sorted virtual group`() {
        val groups = groupMultipartVideoFiles(
            listOf(
                video("CODE-123-pt-2.mp4"),
                video("CODE-123-pt-1.mp4"),
                video("SINGLE-001-pt-1.mp4"),
                video("PLAIN-100.mp4"),
                video("SEPARATE-200-01.mp4"),
                video("SEPARATE-200-02.mp4"),
            )
        )

        assertEquals(setOf("CODE-123"), groups.keys)
        assertEquals(
            listOf("CODE-123-pt-1.mp4", "CODE-123-pt-2.mp4"),
            groups.getValue("CODE-123").map(SMBFileItem::name),
        )
    }

    private fun video(name: String) = SMBFileItem(
        name = name,
        path = "smb://server/share/$name",
        isDirectory = false,
        size = 1L,
        lastModified = 0L,
    )
}
