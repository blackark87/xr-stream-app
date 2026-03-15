package blackark.app.vr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoLibraryIdentityTest {

    @Test
    fun `extract normalized code from single file`() {
        assertEquals("IPX-123", extractNormalizedCodeFromFileName("ipx-123.mp4"))
    }

    @Test
    fun `extract normalized code from multipart file`() {
        assertEquals("IPX-123", extractNormalizedCodeFromFileName("IPX-123-2.mkv"))
        assertEquals("IPX-123", extractVirtualGroupKey("IPX-123-2.mkv"))
        assertEquals(2, extractVirtualGroupPart("IPX-123-2.mkv"))
    }

    @Test
    fun `return null when filename has no recognizable code`() {
        assertNull(extractNormalizedCodeFromFileName("holiday_clip.mp4"))
    }

    @Test
    fun `build stable source and asset keys`() {
        val sourceScope = buildSourceScope("local://storage", "")
        assertEquals("local://storage::", sourceScope)
        assertEquals("local://storage::::IPX-123", buildAssetKey(sourceScope, "ipx-123"))
    }
}
