package blackark.app.vr.remote

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface RemoteManifestFetchResult {
    data class Modified(
        val json: String,
        val manifestSha: String,
        val etag: String?,
    ) : RemoteManifestFetchResult

    data object NotModified : RemoteManifestFetchResult

    data class Failure(
        val statusCode: Int?,
        val message: String,
    ) : RemoteManifestFetchResult
}

interface RemoteManifestClient {
    suspend fun fetch(token: String, etag: String?): RemoteManifestFetchResult
}

internal enum class GitHubManifestResponseDisposition {
    Content,
    NotModified,
    Unauthorized,
    Forbidden,
    NotFound,
    Failure,
}

internal fun classifyGitHubManifestResponse(statusCode: Int): GitHubManifestResponseDisposition =
    when (statusCode) {
        HttpURLConnection.HTTP_OK -> GitHubManifestResponseDisposition.Content
        HttpURLConnection.HTTP_NOT_MODIFIED -> GitHubManifestResponseDisposition.NotModified
        HttpURLConnection.HTTP_UNAUTHORIZED -> GitHubManifestResponseDisposition.Unauthorized
        HttpURLConnection.HTTP_FORBIDDEN -> GitHubManifestResponseDisposition.Forbidden
        HttpURLConnection.HTTP_NOT_FOUND -> GitHubManifestResponseDisposition.NotFound
        else -> GitHubManifestResponseDisposition.Failure
    }

class GitHubRemoteConfigClient : RemoteManifestClient {
    override suspend fun fetch(
        token: String,
        etag: String?,
    ): RemoteManifestFetchResult = withContext(Dispatchers.IO) {
        val connection = (URL(MANIFEST_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer ${token.trim()}")
            setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
            setRequestProperty("User-Agent", USER_AGENT)
            etag?.takeIf(String::isNotBlank)?.let { setRequestProperty("If-None-Match", it) }
        }
        try {
            val statusCode = connection.responseCode
            when (classifyGitHubManifestResponse(statusCode)) {
                GitHubManifestResponseDisposition.NotModified ->
                    RemoteManifestFetchResult.NotModified

                GitHubManifestResponseDisposition.Content -> {
                    val response = connection.inputStream.bufferedReader().use { reader ->
                        reader.readText().also { require(it.length <= MAX_MANIFEST_RESPONSE_CHARS) }
                    }
                    val envelope = JSONObject(response)
                    val encoding = envelope.optString("encoding")
                    require(encoding.equals("base64", ignoreCase = true)) {
                        "Unsupported GitHub content encoding"
                    }
                    val content = envelope.getString("content")
                    val decoded = Base64.decode(content, Base64.DEFAULT).decodeToString()
                    require(decoded.length <= MAX_MANIFEST_CONTENT_CHARS) {
                        "Remote manifest is too large"
                    }
                    RemoteManifestFetchResult.Modified(
                        json = decoded,
                        manifestSha = envelope.getString("sha"),
                        etag = connection.getHeaderField("ETag"),
                    )
                }

                GitHubManifestResponseDisposition.Unauthorized,
                GitHubManifestResponseDisposition.Forbidden,
                GitHubManifestResponseDisposition.NotFound,
                GitHubManifestResponseDisposition.Failure -> RemoteManifestFetchResult.Failure(
                    statusCode = statusCode,
                    message = when (classifyGitHubManifestResponse(statusCode)) {
                        GitHubManifestResponseDisposition.Unauthorized -> "GitHub token was rejected"
                        GitHubManifestResponseDisposition.Forbidden ->
                            "GitHub token lacks Contents read access"
                        GitHubManifestResponseDisposition.NotFound -> "Remote manifest was not found"
                        else -> "GitHub returned HTTP $statusCode"
                    },
                )
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            RemoteManifestFetchResult.Failure(
                statusCode = null,
                message = when (error) {
                    is IOException -> "Could not reach GitHub"
                    else -> error.message ?: "Remote manifest validation failed"
                },
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val REPOSITORY_OWNER = "blackark87"
        private const val REPOSITORY_NAME = "xr-stream-app"
        private const val MANIFEST_PATH = "remote-config/internal/manifest.json"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val USER_AGENT = "xr-stream-app-runtime-config"
        private const val NETWORK_TIMEOUT_MS = 2_000
        private const val MAX_MANIFEST_RESPONSE_CHARS = 2_000_000
        private const val MAX_MANIFEST_CONTENT_CHARS = 1_000_000
        private const val MANIFEST_API_URL =
            "https://api.github.com/repos/$REPOSITORY_OWNER/$REPOSITORY_NAME/contents/$MANIFEST_PATH?ref=main"
    }
}
