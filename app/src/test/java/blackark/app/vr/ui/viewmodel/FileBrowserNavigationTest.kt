package blackark.app.vr.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileBrowserNavigationTest {

    @Test
    fun `restored relative path can derive its parent without history`() {
        assertEquals(
            "japan/모리 히나코",
            resolveRelativeBrowserParentPath("japan/모리 히나코/MIKR-109"),
        )
        assertEquals("japan", resolveRelativeBrowserParentPath("japan/모리 히나코"))
        assertEquals("", resolveRelativeBrowserParentPath("japan"))
    }

    @Test
    fun `root relative path has no parent`() {
        assertNull(resolveRelativeBrowserParentPath(""))
        assertNull(resolveRelativeBrowserParentPath("/"))
    }

    @Test
    fun `ordinary SMB connection always starts at root`() {
        assertEquals(
            "",
            resolveBrowserConnectionStartPath(
                isLocalStorage = false,
                requestedPath = null,
                lastFolder = "japan/모리 히나코",
            ),
        )
    }

    @Test
    fun `explicit quick access path is honored for SMB connection`() {
        assertEquals(
            "japan/모리 히나코",
            resolveBrowserConnectionStartPath(
                isLocalStorage = false,
                requestedPath = "japan/모리 히나코",
                lastFolder = "ignored/last/folder",
            ),
        )
    }

    @Test
    fun `local storage may restore its last folder`() {
        assertEquals(
            "content://local/last-folder",
            resolveBrowserConnectionStartPath(
                isLocalStorage = true,
                requestedPath = null,
                lastFolder = "content://local/last-folder",
            ),
        )
    }
}
