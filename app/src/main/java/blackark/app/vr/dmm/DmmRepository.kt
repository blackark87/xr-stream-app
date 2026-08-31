package blackark.app.vr.dmm

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val DMM_LIBRARY_URL =
    "https://www.dmm.co.jp/digital/videoa/-/mylibrary/"

enum class DmmPage {
    Browser,
    Login,
    Rights,
}

data class DmmDownloadItem(
    val title: String,
    val path: String,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val isComplete: Boolean,
) {
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0L }
            ?.let { (bytesDownloaded.toDouble() / it.toDouble()).toFloat().coerceIn(0f, 1f) }
}

data class DmmPreparedMedia(
    val playbackUri: String,
    val historyKey: String,
    val title: String,
)

data class DmmUiState(
    val page: DmmPage = DmmPage.Browser,
    val runtimeProvisioned: Boolean = false,
    val webSessionDetected: Boolean = false,
    val activeUrl: String = DMM_LIBRARY_URL,
    val navigationId: Long = 0L,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val downloads: List<DmmDownloadItem> = emptyList(),
    val preparedMedia: DmmPreparedMedia? = null,
)

/**
 * Coordinates a normal DMM WebView session, authenticated downloads, WSD rights, and playback.
 *
 * User credentials stay inside WebView. This path does not exchange OAuth codes, validate JWTs,
 * call the native purchased-content API, or require application secrets in BuildConfig.
 */
class DmmRepository(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val wsdRuntime = DmmWsdRuntime(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val downloadDirectory = (
        appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: appContext.filesDir
        ).resolve("dmm-downloads").apply { mkdirs() }

    private val _state = MutableStateFlow(
        DmmUiState(
            runtimeProvisioned = wsdRuntime.isProvisioned,
            downloads = scanDownloads(),
        )
    )
    val state: StateFlow<DmmUiState> = _state.asStateFlow()

    private var activeSourceUri: Uri? = null
    private var activeTitle: String = "DMM video"
    private var lastFinishedPageKey: String? = null
    private var lastFinishedAtMs: Long = 0L
    private var rapidSamePageFinishCount: Int = 0
    private var blockedReloadPageKey: String? = null

    fun startLogin() {
        Log.i(TAG, "event=web_login_open")
        navigate(
            page = DmmPage.Login,
            url = DMM_LIBRARY_URL,
            status = "Sign in on the DMM web page. The app does not read your password.",
        )
    }

    fun showLibrary() {
        Log.i(TAG, "event=library_open")
        navigate(page = DmmPage.Browser, url = DMM_LIBRARY_URL, status = null)
    }

    fun logout() {
        clearDmmAuthenticationCookies()
        Log.i(TAG, "event=web_logout")
        _state.value = _state.value.copy(webSessionDetected = false)
        navigate(
            page = DmmPage.Login,
            url = DMM_LIBRARY_URL,
            status = "DMM web-session cookies were cleared. Sign in again if the site requests it.",
        )
    }

    fun loadWebPage(view: WebView, url: String) {
        view.loadUrl(url)
    }

    fun onWebPageFinished(url: String): Boolean {
        if (!isDmmUrl(url)) return false
        val detected = hasDmmWebSession(url)
        val previous = _state.value
        val page = if (detected && previous.page == DmmPage.Login) DmmPage.Browser else previous.page
        val reloadBlocked = recordFinishedPage(url, detected)
        _state.value = previous.copy(
            page = page,
            webSessionDetected = detected,
            activeUrl = url,
            statusMessage = when {
                reloadBlocked -> {
                    "DMM repeatedly refreshed the purchased library, so automatic reloads were paused. " +
                        "Use Reload to try again or check the connection region in DMM's guidance."
                }
                detected && !previous.webSessionDetected -> {
                    "DMM web session detected. Open purchased content and choose stream or download."
                }
                else -> previous.statusMessage
            },
        )
        Log.i(
            TAG,
            "event=web_page_finished target=${sanitizedUrl(url)} " +
                "sessionDetected=$detected reloadBlocked=$reloadBlocked",
        )
        return reloadBlocked
    }

    fun shouldBlockWebNavigation(url: String): Boolean =
        blockedReloadPageKey != null && blockedReloadPageKey == reloadComparisonKey(url)

    fun allowWebReload() {
        resetReloadGuard()
        _state.value = _state.value.copy(statusMessage = null)
    }

    /** Returns true when navigation was consumed by WSD rights or protected playback. */
    fun handleNavigation(url: String, suggestedTitle: String? = null): Boolean {
        if (_state.value.page == DmmPage.Rights && wsdRuntime.isLicenseUrl(Uri.parse(url))) {
            submitRights(url)
            return true
        }
        val mediaUrl = extractWsdUrl(url) ?: return false
        Log.i(TAG, "event=stream_link_intercepted target=${sanitizedUrl(mediaUrl)}")
        play(mediaUrl, suggestedTitle ?: mediaTitle(mediaUrl))
        return true
    }

    fun handleDownload(
        url: String,
        suggestedTitle: String?,
        mimeType: String,
        userAgent: String?,
    ): Boolean {
        if (_state.value.page == DmmPage.Rights && wsdRuntime.isLicenseUrl(Uri.parse(url))) {
            submitRights(url)
            return true
        }
        val mediaUrl = extractWsdUrl(url)
        val hasWsdFileName = suggestedTitle.orEmpty()
            .substringBefore('?')
            .endsWith(".wsdcf", ignoreCase = true)
        if (mediaUrl == null &&
            !hasWsdFileName &&
            !mimeType.contains("wsdrm", ignoreCase = true)
        ) {
            Log.i(
                TAG,
                "event=non_wsd_download_ignored target=${sanitizedUrl(url)} mime=${mimeType.take(80)}",
            )
            return false
        }
        val targetUrl = mediaUrl ?: url
        Log.i(
            TAG,
            "event=download_intercepted target=${sanitizedUrl(targetUrl)} mime=${mimeType.take(80)}",
        )
        download(
            url = targetUrl,
            title = suggestedTitle ?: mediaTitle(targetUrl),
            userAgent = userAgent,
        )
        return true
    }

    fun play(url: String, title: String = mediaTitle(url)) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null || uri.scheme.isNullOrBlank()) {
            fail("Enter a valid WSDCF URL or choose an offline download.")
            return
        }
        if (!wsdRuntime.isProvisioned) {
            fail("This build does not contain the WSD runtime.")
            return
        }
        activeSourceUri = uri
        activeTitle = title.ifBlank { "DMM video" }
        _state.value = _state.value.copy(isBusy = true, statusMessage = "Checking WSD playback rights…")
        Log.i(TAG, "event=wsd_open source=${sanitizedUrl(url)}")
        wsdRuntime.open(uri, runtimeListener)
    }

    fun playOffline(item: DmmDownloadItem) {
        if (!item.isComplete) return
        play(Uri.fromFile(File(item.path)).toString(), item.title)
    }

    fun importOffline(uri: Uri) {
        scope.launch {
            _state.value = _state.value.copy(isBusy = true, statusMessage = "Importing WSDCF…")
            runCatching { importToDevice(uri) }
                .onSuccess { item ->
                    Log.i(TAG, "event=import_complete bytes=${item.bytesDownloaded}")
                    _state.value = _state.value.copy(
                        isBusy = false,
                        downloads = scanDownloads(),
                        statusMessage = "Imported ${item.title}. WSD will check locally stored rights.",
                    )
                }
                .onFailure { error -> fail(error.userMessage()) }
        }
    }

    fun download(
        url: String,
        title: String = mediaTitle(url),
        userAgent: String? = null,
    ) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri?.scheme !in setOf("http", "https")) {
            fail("Downloads require an HTTP or HTTPS WSDCF URL.")
            return
        }
        val referer = _state.value.activeUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        _state.value = _state.value.copy(isBusy = true, statusMessage = "Downloading encrypted WSDCF…")
        scope.launch {
            runCatching {
                downloadToDevice(
                    url = url,
                    title = title,
                    userAgent = userAgent ?: WebSettings.getDefaultUserAgent(appContext),
                    referer = referer,
                )
            }.onSuccess { bytes ->
                Log.i(TAG, "event=download_complete bytes=$bytes")
                _state.value = _state.value.copy(
                    isBusy = false,
                    downloads = scanDownloads(),
                    statusMessage = "Download complete. The encrypted WSDCF is stored on this device.",
                )
            }.onFailure { error -> fail(error.userMessage()) }
        }
    }

    fun consumePreparedMedia() {
        _state.value = _state.value.copy(preparedMedia = null, isBusy = false)
    }

    fun clearStatus() {
        _state.value = _state.value.copy(statusMessage = null)
    }

    fun releasePlayback() {
        wsdRuntime.close()
        activeSourceUri = null
        if (_state.value.page == DmmPage.Rights) {
            navigate(page = DmmPage.Browser, url = DMM_LIBRARY_URL, status = null)
        }
    }

    override fun close() {
        wsdRuntime.close()
        scope.cancel()
    }

    private val runtimeListener = object : DmmWsdListener {
        override fun onRightsRequired(request: DmmRightsRequest) {
            _state.value = _state.value.copy(
                isBusy = true,
                statusMessage = "Opening the WSD rights page with the current web session…",
            )
            Log.i(TAG, "event=rights_required issuer=${sanitizedUrl(request.rightsIssuer)}")
            scope.launch {
                runCatching { wsdRuntime.startRights(request) }
                    .onSuccess(::receiveRightsResponse)
                    .onFailure { error -> fail(error.userMessage()) }
            }
        }

        override fun onReady(playbackUri: Uri) {
            val source = activeSourceUri ?: return
            Log.i(TAG, "event=wsd_ready output=${sanitizedUrl(playbackUri.toString())}")
            _state.value = _state.value.copy(
                isBusy = false,
                statusMessage = null,
                preparedMedia = DmmPreparedMedia(
                    playbackUri = playbackUri.toString(),
                    historyKey = "dmm://${sha256(source.toString()).take(24)}",
                    title = activeTitle,
                ),
            )
        }

        override fun onError(error: Throwable) {
            fail(error.userMessage())
        }
    }

    private fun submitRights(url: String) {
        _state.value = _state.value.copy(isBusy = true, statusMessage = "Installing WSD playback rights…")
        Log.i(TAG, "event=license_url_intercepted target=${sanitizedUrl(url)}")
        scope.launch {
            runCatching { wsdRuntime.submitLicenseUrl(url) }
                .onSuccess(::receiveRightsResponse)
                .onFailure { error -> fail(error.userMessage()) }
        }
    }

    private fun receiveRightsResponse(response: DmmRightsResponse) {
        Log.i(
            TAG,
            "event=rights_response status=${response.statusCode} hasLocation=${!response.location.isNullOrBlank()}",
        )
        if (response.isAcquired) {
            _state.value = _state.value.copy(
                isBusy = true,
                statusMessage = "Rights acquired. Starting player…",
            )
            wsdRuntime.resumeAfterRights()
            return
        }
        val location = response.location
        if (location.isNullOrBlank()) {
            fail("WSD did not return a rights page (HTTP ${response.statusCode}).")
            return
        }
        if (wsdRuntime.isLicenseUrl(Uri.parse(location))) {
            submitRights(location)
            return
        }
        navigate(
            page = DmmPage.Rights,
            url = location,
            status = "Complete the rights page using the DMM web session.",
        )
    }

    private suspend fun downloadToDevice(
        url: String,
        title: String,
        userAgent: String,
        referer: String?,
    ): Long {
        val cookieHeader = withContext(Dispatchers.Main) {
            CookieManager.getInstance().getCookie(url)
        }
        return withContext(Dispatchers.IO) {
            val baseName = sanitizeFileName(title).ifBlank { "dmm-${sha256(url).take(12)}" }
            val fileName = if (baseName.endsWith(".wsdcf", true)) baseName else "$baseName.wsdcf"
            val destination = File(downloadDirectory, fileName)
            val partial = File(downloadDirectory, "$fileName.part")
            val existing = partial.length()
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", userAgent)
                cookieHeader?.takeIf(String::isNotBlank)?.let {
                    connection.setRequestProperty("Cookie", it)
                }
                referer?.let { connection.setRequestProperty("Referer", it) }
                if (existing > 0L) connection.setRequestProperty("Range", "bytes=$existing-")
                val status = connection.responseCode
                if (status == 416 && partial.isFile) {
                    check(partial.renameTo(destination)) { "Unable to finalize the existing download." }
                    return@withContext destination.length()
                }
                check(status in 200..299) { "Download failed with HTTP $status." }
                val append = status == HttpURLConnection.HTTP_PARTIAL && existing > 0L
                val initialBytes = if (append) existing else 0L
                val responseLength = connection.contentLengthLong.takeIf { it >= 0L }
                val total = responseLength?.plus(initialBytes)
                FileOutputStream(partial, append).use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(256 * 1024)
                        var downloaded = initialBytes
                        var lastPublished = downloaded
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (downloaded - lastPublished >= 1024 * 1024) {
                                lastPublished = downloaded
                                publishDownload(title, partial, downloaded, total, false)
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (destination.exists()) check(destination.delete())
                check(partial.renameTo(destination)) { "Unable to finalize the download." }
                destination.length()
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun importToDevice(uri: Uri): DmmDownloadItem = withContext(Dispatchers.IO) {
        var displayName = "imported-${System.currentTimeMillis()}.wsdcf"
        var totalBytes: Long? = null
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                displayName = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: displayName
                totalBytes = cursor.getLong(1).takeIf { it >= 0L }
            }
        }
        val safeName = sanitizeFileName(displayName).let { name ->
            if (name.endsWith(".wsdcf", true)) name else "$name.wsdcf"
        }
        val destination = File(downloadDirectory, safeName)
        val partial = File(downloadDirectory, "$safeName.part")
        val input = requireNotNull(appContext.contentResolver.openInputStream(uri)) {
            "Unable to open the selected WSDCF file."
        }
        input.use { source ->
            FileOutputStream(partial, false).use { output ->
                val buffer = ByteArray(256 * 1024)
                var copied = 0L
                var lastPublished = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    copied += count
                    if (copied - lastPublished >= 1024 * 1024) {
                        lastPublished = copied
                        publishDownload(displayName, partial, copied, totalBytes, false)
                    }
                }
                output.fd.sync()
            }
        }
        if (destination.exists()) check(destination.delete())
        check(partial.renameTo(destination)) { "Unable to finalize the imported WSDCF file." }
        DmmDownloadItem(
            title = displayName,
            path = destination.absolutePath,
            bytesDownloaded = destination.length(),
            totalBytes = destination.length(),
            isComplete = true,
        )
    }

    private fun publishDownload(
        title: String,
        file: File,
        downloaded: Long,
        total: Long?,
        complete: Boolean,
    ) {
        scope.launch {
            val active = DmmDownloadItem(title, file.absolutePath, downloaded, total, complete)
            _state.value = _state.value.copy(
                downloads = _state.value.downloads.filterNot { it.path == file.absolutePath } + active,
            )
        }
    }

    private fun scanDownloads(): List<DmmDownloadItem> = downloadDirectory
        .listFiles()
        .orEmpty()
        .filter(File::isFile)
        .sortedByDescending(File::lastModified)
        .map { file ->
            DmmDownloadItem(
                title = file.name.removeSuffix(".part").removeSuffix(".wsdcf"),
                path = file.absolutePath,
                bytesDownloaded = file.length(),
                totalBytes = file.length().takeIf { !file.name.endsWith(".part") },
                isComplete = file.name.endsWith(".wsdcf", ignoreCase = true),
            )
        }

    private fun navigate(page: DmmPage, url: String, status: String?) {
        resetReloadGuard()
        _state.value = _state.value.copy(
            page = page,
            activeUrl = url,
            navigationId = _state.value.navigationId + 1L,
            isBusy = false,
            statusMessage = status,
        )
    }

    private fun recordFinishedPage(url: String, sessionDetected: Boolean): Boolean {
        val pageKey = reloadComparisonKey(url)
        val now = SystemClock.elapsedRealtime()
        val repeatedRapidly = pageKey == lastFinishedPageKey &&
            now - lastFinishedAtMs in 0..RAPID_RELOAD_WINDOW_MS

        rapidSamePageFinishCount = if (repeatedRapidly) {
            rapidSamePageFinishCount + 1
        } else {
            1
        }
        lastFinishedPageKey = pageKey
        lastFinishedAtMs = now

        if (sessionDetected &&
            isPurchasedLibraryUrl(url) &&
            rapidSamePageFinishCount >= RAPID_RELOAD_LIMIT
        ) {
            if (blockedReloadPageKey != pageKey) {
                Log.w(
                    TAG,
                    "event=rapid_library_reload_blocked target=${sanitizedUrl(url)} " +
                        "count=$rapidSamePageFinishCount",
                )
            }
            blockedReloadPageKey = pageKey
        }
        return blockedReloadPageKey == pageKey
    }

    private fun resetReloadGuard() {
        lastFinishedPageKey = null
        lastFinishedAtMs = 0L
        rapidSamePageFinishCount = 0
        blockedReloadPageKey = null
    }

    private fun hasDmmWebSession(currentUrl: String): Boolean {
        val manager = CookieManager.getInstance()
        val cookies = listOf(
            currentUrl,
            "https://www.dmm.co.jp/",
            "https://accounts.dmm.com/",
            "https://www.dmm.com/",
        ).asSequence()
            .mapNotNull { manager.getCookie(it) }
            .joinToString(separator = ";")
        return SESSION_COOKIE_NAMES.any { name ->
            Regex("(?:^|;\\s*)${Regex.escape(name)}=").containsMatchIn(cookies)
        }
    }

    private fun clearDmmAuthenticationCookies() {
        val manager = CookieManager.getInstance()
        listOf("com", "co.jp").forEach { suffix ->
            val domain = ".dmm.$suffix"
            listOf("www", "accounts").forEach { host ->
                val origin = "https://$host.dmm.$suffix/"
                SESSION_COOKIE_NAMES.forEach { name ->
                    manager.setCookie(origin, "$name=; Path=/; Domain=$domain; Max-Age=0; Secure")
                }
            }
        }
        manager.flush()
    }

    private fun fail(message: String) {
        Log.e(TAG, "event=flow_error type=${message.substringBefore(':').take(80)}")
        _state.value = _state.value.copy(isBusy = false, statusMessage = message)
    }

    companion object {
        private const val TAG = "DmmWebFlow"
        private const val RAPID_RELOAD_LIMIT = 4
        private const val RAPID_RELOAD_WINDOW_MS = 5_000L
        private val SESSION_COOKIE_NAMES = listOf(
            "secid",
            "dmm_app_uid",
            "althash",
            "has_althash",
            "INT_SESID",
            "INT_SESID_SECURE",
            "login_session_id",
            "login_secure_id",
        )

        fun mediaTitle(url: String): String = Uri.parse(url).lastPathSegment
            ?.substringBefore('?')
            ?.takeIf(String::isNotBlank)
            ?: "DMM video"

        fun extractWsdUrl(url: String): String? {
            val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
            if (uri.path.orEmpty().endsWith(".wsdcf", ignoreCase = true)) return url
            return runCatching {
                listOf("url", "download_url", "content_url", "stream_url")
                    .asSequence()
                    .mapNotNull(uri::getQueryParameter)
                    .firstOrNull { candidate ->
                        Uri.parse(candidate).path.orEmpty().endsWith(".wsdcf", ignoreCase = true)
                    }
            }.getOrNull()
        }

        private fun isDmmUrl(url: String): Boolean {
            val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
            return host == "dmm.com" || host.endsWith(".dmm.com") ||
                host == "dmm.co.jp" || host.endsWith(".dmm.co.jp")
        }

        private fun isPurchasedLibraryUrl(url: String): Boolean {
            if (!isDmmUrl(url)) return false
            return runCatching { Uri.parse(url).path.orEmpty() }
                .getOrDefault("")
                .contains("/mylibrary", ignoreCase = true)
        }

        private fun reloadComparisonKey(url: String): String = runCatching {
            Uri.parse(url).buildUpon().clearQuery().fragment(null).build().toString()
                .trimEnd('/')
                .lowercase()
        }.getOrDefault(url.substringBefore('#').substringBefore('?').trimEnd('/').lowercase())

        private fun sanitizedUrl(url: String): String = runCatching {
            Uri.parse(url).buildUpon().clearQuery().fragment(null).build().toString()
        }.getOrDefault("invalid-url")

        private fun sanitizeFileName(value: String): String = value
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .take(120)

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }

        private fun Throwable.userMessage(): String =
            (this as? java.lang.reflect.InvocationTargetException)?.targetException?.message
                ?: message
                ?: javaClass.simpleName
    }
}
