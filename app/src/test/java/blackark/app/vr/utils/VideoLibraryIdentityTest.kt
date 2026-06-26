package blackark.app.vr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoLibraryIdentityTest {

    @Test
    fun `extract virtual group key supports numeric multipart suffixes`() {
        assertEquals("CODE-123", extractVirtualGroupKey("CODE-123-01.mp4"))
        assertEquals(1, extractVirtualGroupPart("CODE-123-01.mp4"))
        assertEquals("CODE-123", extractVirtualGroupKey("CODE-123_2.mp4"))
        assertEquals(2, extractVirtualGroupPart("CODE-123_2.mp4"))
    }

    @Test
    fun `extract virtual group key supports alias multipart suffixes`() {
        assertEquals("CODE-123", extractVirtualGroupKey("CODE-123-cd1.mp4"))
        assertEquals(1, extractVirtualGroupPart("CODE-123-cd1.mp4"))
        assertEquals("CODE-123", extractVirtualGroupKey("CODE-123 pt02.mp4"))
        assertEquals(2, extractVirtualGroupPart("CODE-123 pt02.mp4"))
        assertEquals("CODE-123", extractVirtualGroupKey("CODE-123_part3.mp4"))
        assertEquals(3, extractVirtualGroupPart("CODE-123_part3.mp4"))
    }

    @Test
    fun `extract virtual group key ignores non multipart names`() {
        assertNull(extractVirtualGroupKey("CODE-123.mp4"))
        assertNull(extractVirtualGroupPart("CODE-123.mp4"))
        assertNull(extractVirtualGroupKey("feature-film.mp4"))
        assertNull(extractVirtualGroupPart("feature-film.mp4"))
    }
}
