package blackark.app.vr.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FileNfoCacheIdentityTest {
    @Test fun `uncoded files with the same name in different folders do not share NFO`() {
        assertNotEquals(fileNfoCacheKey("nas", "/a/movie.mp4"), fileNfoCacheKey("nas", "/b/movie.mp4"))
        assertNotEquals(fileNfoCacheKey("nas", "/a/movie.mp4"), fileNfoCacheKey("other", "/a/movie.mp4"))
    }
    @Test fun `file keys preserve case sensitive paths and are stable`() {
        assertNotEquals(fileNfoCacheKey("nas", "/A/movie.mp4"), fileNfoCacheKey("nas", "/a/movie.mp4"))
        assertEquals(fileNfoCacheKey("nas", "/a/movie.mp4"), fileNfoCacheKey("nas", "/a/movie.mp4"))
    }
}
