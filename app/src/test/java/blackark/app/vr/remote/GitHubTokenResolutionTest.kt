package blackark.app.vr.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubTokenResolutionTest {

    @Test
    fun bundledTokenIsTrimmed() {
        val result = resolveBundledGitHubToken(" bundled ")

        assertEquals("bundled", result)
    }

    @Test
    fun blankBundledTokenResolvesToMissing() {
        val result = resolveBundledGitHubToken(" ")

        assertNull(result)
    }
}
