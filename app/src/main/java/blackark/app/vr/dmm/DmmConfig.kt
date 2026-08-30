package blackark.app.vr.dmm

import android.net.Uri
import blackark.app.vr.BuildConfig

/**
 * DMM OAuth configuration supplied by the app owner at build time.
 *
 * Values deliberately come from secrets.local.properties/environment variables. They must not be
 * copied from another application's repository or committed to this project.
 */
data class DmmConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val jwtSecret: String,
    val libraryUrl: String,
    val apiBaseUrl: String = "https://gw.dmmapis.com",
    val digitalApiBaseUrl: String,
    val digitalApiAuthSecret: String,
    val exploitIdPrefix: String,
    val appName: String,
    val apiAppVersion: String,
    val defaultDownloadQuality: String,
) {
    val isConfigured: Boolean
        get() = clientId.isNotBlank() &&
            clientSecret.isNotBlank() &&
            redirectUri.isNotBlank() &&
            jwtSecret.isNotBlank()

    val canListPurchases: Boolean
        get() = isConfigured &&
            digitalApiBaseUrl.startsWith("https://") &&
            appName.isNotBlank() &&
            apiAppVersion.isNotBlank()

    val canIssuePlayableUrls: Boolean
        get() = canListPurchases && digitalApiAuthSecret.isNotBlank()

    fun loginUrl(): String = Uri.parse("https://accounts.dmm.com/app/service/login/password")
        .buildUpon()
        .appendQueryParameter("client_id", clientId)
        .build()
        .toString()

    fun isRedirect(url: String): Boolean {
        val expected = runCatching { Uri.parse(redirectUri) }.getOrNull() ?: return false
        val actual = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return expected.scheme.equals(actual.scheme, ignoreCase = true) &&
            expected.host.equals(actual.host, ignoreCase = true) &&
            expected.path.orEmpty() == actual.path.orEmpty()
    }

    companion object {
        fun fromBuildConfig(): DmmConfig = DmmConfig(
            clientId = BuildConfig.DMM_CLIENT_ID,
            clientSecret = BuildConfig.DMM_CLIENT_SECRET,
            redirectUri = BuildConfig.DMM_REDIRECT_URI,
            jwtSecret = BuildConfig.DMM_JWT_SECRET,
            libraryUrl = BuildConfig.DMM_LIBRARY_URL,
            digitalApiBaseUrl = BuildConfig.DMM_DIGITAL_API_BASE_URL,
            digitalApiAuthSecret = BuildConfig.DMM_DIGITAL_API_AUTH_SECRET,
            exploitIdPrefix = BuildConfig.DMM_EXPLOIT_ID_PREFIX,
            appName = BuildConfig.DMM_APP_NAME,
            apiAppVersion = BuildConfig.DMM_API_APP_VERSION,
            defaultDownloadQuality = BuildConfig.DMM_DEFAULT_DOWNLOAD_QUALITY,
        )
    }
}
