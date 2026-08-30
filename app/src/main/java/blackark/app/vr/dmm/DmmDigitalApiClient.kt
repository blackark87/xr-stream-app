package blackark.app.vr.dmm

import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class DmmCatalogItem(
    val contentId: String,
    val myLibraryId: Int,
    val title: String,
    val packageImageUrl: String?,
    val purchasedQualityGroup: String,
    val contentType: String,
    val expiresAt: String?,
)

data class DmmCatalogPage(
    val items: List<DmmCatalogItem>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Int,
)

data class DmmApiCookie(
    val name: String,
    val value: String,
    val expires: String?,
    val path: String?,
    val domain: String?,
)

data class DmmPlayableResource(
    val url: String,
    val cookie: DmmApiCookie?,
    val recommendedViewingType: String?,
)

/**
 * Native client for the three DMM Digital API calls used by the original VR application.
 *
 * The endpoint/header shape and signature parameter order are interoperable protocol details.
 * Credential values remain build-local and must be issued or otherwise authorized for this app.
 */
class DmmDigitalApiClient(
    private val config: DmmConfig,
) {
    suspend fun getAllPurchasedVr(
        session: DmmSession,
        pageSize: Int = 40,
    ): DmmCatalogPage = withContext(Dispatchers.IO) {
        val first = getPurchasedVr(session = session, page = 1, limit = pageSize)
        if (first.totalPages <= 1) return@withContext first
        val lastPage = first.totalPages.coerceAtMost(MAX_LIBRARY_PAGES)
        val allItems = first.items.toMutableList()
        for (page in 2..lastPage) {
            allItems += getPurchasedVr(session = session, page = page, limit = pageSize).items
        }
        first.copy(items = allItems.distinctBy(DmmCatalogItem::myLibraryId))
    }

    suspend fun getPurchasedVr(
        session: DmmSession,
        page: Int = 1,
        limit: Int = 40,
        order: String = "new",
    ): DmmCatalogPage = withContext(Dispatchers.IO) {
        check(config.canListPurchases) {
            "DMM Digital API is not configured for this application."
        }
        val root = get(
            path = "/purchase/list/vr",
            session = session,
            query = mapOf(
                "limit" to limit.toString(),
                "order" to order,
                "page" to page.toString(),
            ),
        )
        requireSuccess(root)
        val content = root.optJSONObject("content") ?: JSONObject()
        val list = content.optJSONArray("list")
        val items = buildList {
            if (list != null) {
                for (index in 0 until list.length()) {
                    val item = list.optJSONObject(index) ?: continue
                    val libraryId = item.optInt("mylibrary_id", 0)
                    if (libraryId <= 0) continue
                    add(
                        DmmCatalogItem(
                            contentId = item.optString("content_id"),
                            myLibraryId = libraryId,
                            title = item.optString("title").ifBlank {
                                item.optString("content_id", "DMM video")
                            },
                            packageImageUrl = item.optString("package_image_url")
                                .takeIf(String::isNotBlank),
                            purchasedQualityGroup = item.optString("purchased_quality_group"),
                            contentType = item.optString("content_type"),
                            expiresAt = item.optString("expire").takeIf(String::isNotBlank),
                        )
                    )
                }
            }
        }
        DmmCatalogPage(
            items = items,
            page = page,
            totalPages = content.optInt("total_page", page),
            totalItems = content.optInt("total_contents_size", items.size),
        )
    }

    suspend fun getStreamVr(
        session: DmmSession,
        item: DmmCatalogItem,
        part: Int = 1,
    ): DmmPlayableResource = withContext(Dispatchers.IO) {
        requirePlayableConfiguration()
        val qualityGroup = item.purchasedQualityGroup.ifBlank { "hq" }
        val exploitId = exploitId(session)
        val userAgent = nativeUserAgent()
        val signature = hmacSha256(
            authorization(session),
            item.myLibraryId.toString(),
            exploitId,
            userAgent,
            qualityGroup,
            part.toString(),
        )
        parsePlayable(
            get(
                path = "/playableprovider/stream/vr",
                session = session,
                query = mapOf(
                    "mylibrary_id" to item.myLibraryId.toString(),
                    "part" to part.toString(),
                    "quality_group" to qualityGroup,
                ),
                extraHeaders = mapOf(
                    "x-user-agent" to userAgent,
                    "x-api-auth-code" to signature,
                ),
            ),
            mode = PlayableMode.Stream,
        )
    }

    suspend fun getDownloadVr(
        session: DmmSession,
        item: DmmCatalogItem,
        part: Int = 1,
        quality: String = config.defaultDownloadQuality,
    ): DmmPlayableResource = withContext(Dispatchers.IO) {
        requirePlayableConfiguration()
        val normalizedQuality = quality.trim().lowercase(Locale.ROOT)
        require(normalizedQuality in DOWNLOAD_QUALITIES) {
            "Unsupported DMM download quality: $quality"
        }
        val exploitId = exploitId(session)
        val userAgent = nativeUserAgent()
        val signature = hmacSha256(
            userAgent,
            normalizedQuality,
            authorization(session),
            part.toString(),
            item.myLibraryId.toString(),
            exploitId,
        )
        parsePlayable(
            get(
                path = "/playableprovider/download/vr",
                session = session,
                query = mapOf(
                    "mylibrary_id" to item.myLibraryId.toString(),
                    "part" to part.toString(),
                    "quality" to normalizedQuality,
                ),
                extraHeaders = mapOf(
                    "x-user-agent" to userAgent,
                    "x-api-auth-code" to signature,
                ),
            ),
            mode = PlayableMode.Download,
        )
    }

    private fun get(
        path: String,
        session: DmmSession,
        query: Map<String, String>,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JSONObject {
        val uri = Uri.parse(config.digitalApiBaseUrl.trimEnd('/') + path).buildUpon().apply {
            query.forEach { (name, value) -> appendQueryParameter(name, value) }
        }.build()
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("x-authorization", authorization(session))
            connection.setRequestProperty("x-exploit-id", exploitId(session))
            connection.setRequestProperty("x-app-name", config.appName)
            connection.setRequestProperty("x-app-ver", config.apiAppVersion)
            extraHeaders.forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw DmmApiException(status, responseText.take(512))
            }
            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePlayable(
        root: JSONObject,
        mode: PlayableMode,
    ): DmmPlayableResource {
        requireSuccess(root)
        val content = root.optJSONObject("content_info")
            ?: error("DMM playable-provider response did not contain content_info.")
        val redirect = content.optString("redirect")
        check(redirect.startsWith("https://") || redirect.startsWith("http://")) {
            "DMM playable-provider response did not contain a valid redirect URL."
        }
        val cookieJson = root.optJSONObject("cookie_info")
            ?: error("DMM playable-provider response did not contain cookie_info.")
        val licenseValue = cookieJson.optString("value")
        check(licenseValue.isNotBlank()) {
            "DMM playable-provider response did not contain a license value."
        }
        val cookie = cookieJson.let { json ->
            json.optString("name").takeIf(String::isNotBlank)?.let { name ->
                DmmApiCookie(
                    name = name,
                    value = json.optString("value"),
                    expires = json.optString("expire").takeIf(String::isNotBlank),
                    path = json.optString("path").takeIf(String::isNotBlank),
                    domain = json.optString("domain").takeIf(String::isNotBlank),
                )
            }
        }
        return DmmPlayableResource(
            // The original client passes this decorated URL to the WSD runtime/downloader. These
            // are protocol parameters, not browser cookies: stream and download use different
            // suffixes and the returned value is concatenated without URL re-encoding.
            url = when (mode) {
                PlayableMode.Stream ->
                    "$redirect&licenseUID=$licenseValue&smartphone_access=1"
                PlayableMode.Download -> "$redirect?uid=$licenseValue"
            },
            cookie = cookie,
            recommendedViewingType = content.optString("recommended_viewing_type")
                .takeIf(String::isNotBlank),
        )
    }

    private fun requireSuccess(root: JSONObject) {
        val status = root.optJSONObject("status") ?: return
        val code = status.optInt("code", 0)
        if (code != 0) {
            throw DmmApiException(code, root.toString().take(512))
        }
    }

    private fun exploitId(session: DmmSession): String = config.exploitIdPrefix + session.userId

    private fun authorization(session: DmmSession): String = "Bearer ${session.accessToken}"

    private fun nativeUserAgent(): String = String.format(
        Locale.US,
        "ANDROIDSTORE_DMMVRPLAY %s (%s; Android %s; %s; %s)",
        config.apiAppVersion,
        Build.MODEL,
        Build.VERSION.RELEASE,
        Locale.getDefault().toString(),
        Build.PRODUCT,
    )

    private fun hmacSha256(vararg parameters: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(
                SecretKeySpec(
                    config.digitalApiAuthSecret.toByteArray(StandardCharsets.UTF_8),
                    "HmacSHA256",
                )
            )
        }
        return mac.doFinal(parameters.joinToString(separator = "").toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun requirePlayableConfiguration() {
        check(config.canIssuePlayableUrls) {
            "DMM playable-provider signing is not configured. Add an authorized " +
                "DMM_DIGITAL_API_AUTH_SECRET to secrets.local.properties."
        }
    }

    private enum class PlayableMode {
        Stream,
        Download,
    }

    companion object {
        private const val MAX_LIBRARY_PAGES = 100
        private val DOWNLOAD_QUALITIES =
            setOf("lite", "low", "middle", "high", "hq", "shq", "uhq", "8k")
    }
}
