package blackark.app.vr.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubTokenResolutionTest {

    @Test
    fun savedOverrideWinsOverBundledToken() {
        val result = resolveGitHubToken(" saved ", "bundled")

        assertEquals("saved", result.token)
        assertEquals(GitHubTokenSource.SavedOverride, result.source)
    }

    @Test
    fun bundledTokenIsUsedWhenSavedTokenIsMissing() {
        val result = resolveGitHubToken(null, " bundled ")

        assertEquals("bundled", result.token)
        assertEquals(GitHubTokenSource.Bundled, result.source)
    }

    @Test
    fun blankValuesResolveToMissing() {
        val result = resolveGitHubToken(" ", "")

        assertNull(result.token)
        assertEquals(GitHubTokenSource.Missing, result.source)
    }
}
