package blackark.app.vr.remote

import blackark.app.vr.BuildConfig

internal fun resolveBundledGitHubToken(bundledToken: String?): String? =
    bundledToken?.trim()?.takeIf(String::isNotBlank)

class GitHubCredentialStore(
    private val bundledToken: String = BuildConfig.BUNDLED_GITHUB_PAT,
) {
    fun getToken(): String? = resolveBundledGitHubToken(bundledToken)

    fun hasToken(): Boolean = getToken() != null
}
