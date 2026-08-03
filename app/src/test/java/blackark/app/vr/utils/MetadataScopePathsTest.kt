package blackark.app.vr.utils

import blackark.app.vr.data.database.entity.MetadataScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataScopePathsTest {
    private val scope = MetadataScope(
        id = 1,
        serverId = 7,
        canonicalPath = "smb://nas:445/media/a",
        displayPath = "a",
    )

    @Test
    fun `descendant match honors path segment boundary`() {
        assertTrue(MetadataScopePaths.contains(scope, "smb://nas:445/media/a/movie/file.mp4"))
        assertFalse(MetadataScopePaths.contains(scope, "smb://nas:445/media/ab/file.mp4"))
    }

    @Test
    fun `same folder on another server is isolated`() {
        assertFalse(MetadataScopePaths.contains(scope, "smb://other:445/media/a/file.mp4"))
    }

    @Test
    fun `non recursive scope matches only exact folder`() {
        val exact = scope.copy(includeDescendants = false)
        assertTrue(MetadataScopePaths.contains(exact, "smb://nas:445/media/a"))
        assertFalse(MetadataScopePaths.contains(exact, "smb://nas:445/media/a/child"))
    }
}
