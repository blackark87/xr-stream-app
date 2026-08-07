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
}
