package blackark.app.vr.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbEndpointParserTest {
    @Test
    fun `full SMB URL infers host port and share`() {
        val endpoint = SmbEndpointParser.parse("smb://nas.local:1445/videos/folder").getOrThrow()

        assertEquals("nas.local", endpoint.address)
        assertEquals(1445, endpoint.port)
        assertEquals("videos", endpoint.shareName)
    }

    @Test
    fun `plain hostname uses SMB default port`() {
        val endpoint = SmbEndpointParser.parse("media-server").getOrThrow()

        assertEquals("media-server", endpoint.address)
        assertEquals(445, endpoint.port)
        assertEquals("", endpoint.shareName)
    }

    @Test
    fun `credentials embedded in address are rejected`() {
        assertTrue(SmbEndpointParser.parse("smb://user@server/share").isFailure)
    }
}
