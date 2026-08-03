package blackark.app.vr.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDisplayHeuristicsTest {

    @Test
    fun `raw unicode smb path keeps database identity`() {
        val path = "smb://192.168.1.105:445/Videos/AV/japan/모리 히나코/GDRD-043/GDRD-043.mp4"

        val identity = requireNotNull(parseVideoIdentity(path))

        assertEquals(path, identity.filePath)
        assertEquals("192.168.1.105", identity.serverAddress)
        assertEquals("Videos", identity.shareName)
        assertEquals("GDRD-043.mp4", identity.fileName)
    }

    @Test
    fun `ipv6 smb authority keeps host without brackets or port`() {
        val identity = requireNotNull(
            parseVideoIdentity("smb://[2001:db8::1]:445/Videos/movie.mp4")
        )

        assertEquals("2001:db8::1", identity.serverAddress)
        assertEquals("Videos", identity.shareName)
    }
}
