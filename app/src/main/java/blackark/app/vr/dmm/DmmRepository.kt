package blackark.app.vr.dmm

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.CookieManager
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
    val configured: Boolean = false,
    val playableApiConfigured: Boolean = false,
    val runtimeProvisioned: Boolean = false,
    val signedInUserId: String? = null,
    val activeUrl: String = "",
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val catalog: List<DmmCatalogItem> = emptyList(),
    val catalogTotalItems: Int = 0,
    val downloads: List<DmmDownloadItem> = emptyList(),
    val preparedMedia: DmmPreparedMedia? = null,
)

/** Coordinates web login, SessionID cookies, WSD rights acquisition, and encrypted downloads. */
class DmmRepository(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val config = DmmConfig.fromBuildConfig()
    private val sessionStore = DmmSessionStore(appContext)
    private val authClient = DmmAuthClient(config)
    private val digitalApiClient = DmmDigitalApiClient(config)
    private val wsdRuntime = DmmWsdRuntime(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val downloadDirectory = File(appContext.filesDir, "dmm-downloads").apply { mkdirs() }

    private val _state = MutableStateFlow(
        DmmUiState(
            configured = config.isConfigured,
            playableApiConfigured = config.canIssuePlayableUrls,
            runtimeProvisioned = wsdRuntime.isProvisioned,
            signedInUserId = sessionStore.load()?.userId,
            activeUrl = config.libraryUrl,
            downloads = scanDownloads(),
        )
    )
    val state: StateFlow<DmmUiState> = _state.asStateFlow()

    private var activeSourceUri: Uri? = null
    private var activeTitle: String = "DMM video"

    fun startLogin() {
        if (!config.isConfigured) {
            fail("DMM OAuth is not configured in secrets.local.properties.")
            return
        }
        clearDmmAuthenticationCookies()
        _state.value = _state.value.copy(
            page = DmmPage.Login,
            activeUrl = config.loginUrl(),
            statusMessage = null,
        )
    }

    fun showLibrary() {
        val stored = sessionStore.load()
        if (stored == null) {
            _state.value = _state.value.copy(
                page = DmmPage.Browser,
                activeUrl = config.libraryUrl,
                statusMessage = null,
            )
            return
        }
        _state.value = _state.value.copy(isBusy = true, statusMessage = "Restoring DMM session…")
        scope.launch {
            runCatching { prepareAuthenticatedWebSession(stored) }
                .onSuccess { session -> showAuthenticatedLibrary(session) }
                .onFailure { error -> fail(error.userMessage()) }
        }
    }

    fun refreshPurchasedContent() {
        val stored = sessionStore.load()
        if (stored == null) {
            fail("Sign in to DMM before loading your purchased library.")
            return
        }
        _state.value = _state.value.copy(isBusy = true, statusMessage = "Loading purchased VR content…")
        scope.launch {
            runCatching {
                val active = authClient.ensureActive(stored)
                sessionStore.save(active)
                active to digitalApiClient.getAllPurchasedVr(active)
            }.onSuccess { (active, page) ->
                _state.value = _state.value.copy(
                    signedInUserId = active.userId,
                    catalog = page.items,
                    catalogTotalItems = page.totalItems,
                    isBusy = false,
                    statusMessage = if (page.items.isEmpty()) {
                        "No purchased VR content was returned for this account."
                    } else {
                        "Loaded ${page.items.size} of ${page.totalItems} purchased titles."
                    },
                )
            }.onFailure { error -> fail(error.userMessage()) }
        }
    }

    fun stream(item: DmmCatalogItem, part: Int = 1) {
        requestPlayable(item, "Issuing DMM stream URL…") { session ->
            digitalApiClient.getStreamVr(session, item, part)
        }
    }

    fun download(item: DmmCatalogItem, part: Int = 1) {
        val stored = sessionStore.load()
        if (stored == null) {
            fail("Sign in to DMM before downloading purchased content.")
            return
        }
        _state.value = _state.value.copy(isBusy = true, statusMessage = "Issuing DMM download URL…")
        scope.launch {
            runCatching {
                val active = authClient.ensureActive(stored)
                sessionStore.save(active)
                val playable = digitalApiClient.getDownloadVr(active, item, part)
                downloadToDevice(playable.url, item.title, active)
            }.onSuccess {
                _state.value = _state.value.copy(
                    isBusy = false,
                    downloads = scanDownloads(),
                    statusMessage = "Download complete. The encrypted WSDCF is ready for offline playback.",
                )
            }.onFailure { error -> fail(error.userMessage()) }
        }
    }

    fun logout() {
        sessionStore.clear()
        clearDmmAuthenticationCookies()
        _state.value = _state.value.copy(
            page = DmmPage.Browser,
            signedInUserId = null,
            catalog = emptyList(),
            catalogTotalItems = 0,
            activeUrl = config.libraryUrl,
            statusMessage = "Signed out and removed DMM authentication cookies.",
        )
    }

    /** Applies the headers/cookie behavior used by DMM's Android auth and session WebViews. */
    fun loadWebPage(view: WebView, url: String) {
        installAppCookie(url)
        view.loadUrl(url, webRequestHeaders())
    }

    fun decorateUserAgent(baseUserAgent: String): String {
        val version = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
            }
        }.getOrNull() ?: "unknown"
        return "$baseUserAgent DMMOpenAuth/$DMM_AUTH_SDK_VERSION ${appContext.packageName}/$version"
    }

    /** Returns true when the WebView navigation was consumed by auth, WSD, or playback. */
    fun handleNavigation(url: String, suggestedTitle: String? = null): Boolean {
        if (config.isRedirect(url)) {
            val code = Uri.parse(url).getQueryParameter("code")
            if (code.isNullOrBlank()) {
                fail("DMM login returned without an authorization code.")
            } else {
                exchangeCode(code)
            }
            return true
        }
        if (_state.value.page == DmmPage.Rights && wsdRuntime.isLicenseUrl(Uri.parse(url))) {
            submitRights(url)
            return true
        }
        val mediaUrl = extractWsdUrl(url) ?: return false
        play(mediaUrl, suggestedTitle ?: mediaTitle(mediaUrl))
        return true
    }

    fun handleDownload(url: String, suggestedTitle: String?, mimeType: String): Boolean {
        if (_state.value.page == DmmPage.Rights && wsdRuntime.isLicenseUrl(Uri.parse(url))) {
            submitRights(url)
            return true
        }
        if (extractWsdUrl(url) == null && !mimeType.contains("wsdrm", ignoreCase = true)) {
            return false
        }
        download(url, suggestedTitle ?: mediaTitle(url))
        return true
    }

    fun play(url: String, title: String = mediaTitle(url)) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null || uri.scheme.isNullOrBlank()) {
            fail("Enter a valid WSDCF URL or choose an offline download.")
            return
        }
        if (!wsdRuntime.isProvisioned) {
            fail("WSD runtime is not provisioned. Run scripts/provision-dmm-runtime.sh first.")
            return
        }
        activeSourceUri = uri
        activeTitle = title.ifBlank { "DMM video" }
        _state.value = _state.value.copy(isBusy = true, statusMessage = "Checking playback rights…")
        wsdRuntime.open(uri, runtimeListener)
    }

    fun playOffline(item: DmmDownloadItem) {
        if (!item.isComplete) return
        play(Uri.fromFile(File(item.path)).toString(), item.title)
    }

    fun importOffline(uri: Uri) {
        scope.launch {
            runCatching { importToDevice(uri) }
                .onSuccess { item ->
                    _state.value = _state.value.copy(
                        downloads = scanDownloads(),
                        statusMessage = "Imported ${item.title}. WSD will use locally stored rights when offline.",
                    )
                }
                .onFailure { error -> fail(error.userMessage()) }
        }
    }

    fun download(url: String, title: String = mediaTitle(url)) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri?.scheme !in setOf("http", "https")) {
            fail("Downloads require an HTTP or HTTPS WSDCF URL.")
            return
        }
        scope.launch {
            runCatching {
                val session = sessionStore.load()?.let { prepareAuthenticatedWebSession(it) }
                downloadToDevice(url, title, session)
            }
                .onSuccess {
                    _state.value = _state.value.copy(
                        downloads = scanDownloads(),
                        statusMessage = "Download complete. The encrypted WSDCF is ready for offline playback.",
                    )
                }
                .onFailure { error -> fail(error.userMessage()) }
        }
    }

    fun consumePreparedMedia() {
        _state.value = _state.value.copy(preparedMedia = null, isBusy = false)
    }

    fun clearStatus() {
        _state.value = _state.value.copy(statusMessage = null)
    }

    override fun close() {
        wsdRuntime.close()
        scope.cancel()
    }

    fun releasePlayback() {
        wsdRuntime.close()
        activeSourceUri = null
    }

    private fun exchangeCode(code: String) {
        _state.value = _state.value.copy(isBusy = true, statusMessage = "Completing DMM login…")
        scope.launch {
            runCatching {
                authClient.exchangeAuthorizationCode(code)
                    .let { prepareAuthenticatedWebSession(it) }
            }
                .onSuccess { session ->
                    showAuthenticatedLibrary(
                        session = session,
                        status = "Signed in. Your password was handled only by the DMM web page.",
                    )
                }
                .onFailure { error -> fail(error.userMessage()) }
        }
    }

    private fun requestPlayable(
        item: DmmCatalogItem,
        status: String,
        request: suspend (DmmSession) -> DmmPlayableResource,
    ) {
        val stored = sessionStore.load()
        if (stored == null) {
            fail("Sign in to DMM before streaming purchased content.")
            return
        }
        _state.value = _state.value.copy(isBusy = true, statusMessage = status)
        scope.launch {
            runCatching {
                val active = authClient.ensureActive(stored)
                sessionStore.save(active)
                request(active)
            }.onSuccess { playable ->
                play(playable.url, item.title)
            }.onFailure { error -> fail(error.userMessage()) }
        }
    }

    private val runtimeListener = object : DmmWsdListener {
        override fun onRightsRequired(request: DmmRightsRequest) {
            val stored = sessionStore.load()
            if (stored == null) {
                _state.value = _state.value.copy(
                    isBusy = false,
                    statusMessage = "Sign in to DMM before acquiring playback rights.",
                )
                return
            }
            _state.value = _state.value.copy(isBusy = true, statusMessage = "Creating DMM license session…")
            scope.launch {
                runCatching {
                    val session = authClient.ensureSessionId(stored)
                    sessionStore.save(session)
                    installSessionCookies(session)
                    session to wsdRuntime.startRights(request)
                }.onSuccess { (session, response) ->
                    _state.value = _state.value.copy(signedInUserId = session.userId)
                    receiveRightsResponse(response)
                }.onFailure { error -> fail(error.userMessage()) }
            }
        }

        override fun onReady(playbackUri: Uri) {
            val source = activeSourceUri ?: return
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
        _state.value = _state.value.copy(isBusy = true, statusMessage = "Installing playback rights…")
        scope.launch {
            runCatching { wsdRuntime.submitLicenseUrl(url) }
                .onSuccess(::receiveRightsResponse)
                .onFailure { error -> fail(error.userMessage()) }
        }
    }

    private fun receiveRightsResponse(response: DmmRightsResponse) {
        if (response.isAcquired) {
            _state.value = _state.value.copy(isBusy = true, statusMessage = "Rights acquired. Starting player…")
            wsdRuntime.resumeAfterRights()
            return
        }
        val location = response.location
        if (location.isNullOrBlank()) {
            fail("DMM/WSD did not return a rights page (HTTP ${response.statusCode}).")
            return
        }
        if (wsdRuntime.isLicenseUrl(Uri.parse(location))) {
            submitRights(location)
            return
        }
        _state.value = _state.value.copy(
            page = DmmPage.Rights,
            activeUrl = location,
            isBusy = false,
            statusMessage = "Confirm playback on the DMM rights page.",
        )
    }

    private suspend fun prepareAuthenticatedWebSession(session: DmmSession): DmmSession {
        val active = authClient.ensureSessionId(session)
        sessionStore.save(active)
        withContext(Dispatchers.Main) { installSessionCookies(active) }
        return active
    }

    private fun showAuthenticatedLibrary(session: DmmSession, status: String? = null) {
        _state.value = _state.value.copy(
            page = DmmPage.Browser,
            activeUrl = sessionRestorableUrl(config.libraryUrl),
            signedInUserId = session.userId,
            isBusy = false,
            statusMessage = status,
        )
        refreshPurchasedContent()
    }

    private fun installSessionCookies(session: DmmSession) {
        val secureId = requireNotNull(session.secureId)
        val uniqueId = requireNotNull(session.uniqueId)
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        listOf("com", "co.jp").forEach { suffix ->
            val origin = "https://www.dmm.$suffix/"
            val domain = ".dmm.$suffix"
            manager.setCookie(origin, "secid=$secureId; Path=/; Domain=$domain; Secure")
            manager.setCookie(origin, "dmm_app_uid=$uniqueId; Path=/; Domain=$domain; Secure")
            manager.setCookie(origin, "dmm_app=1; Path=/; Domain=$domain; Secure")
            manager.setCookie(
                "https://accounts.dmm.$suffix/",
                "secid=$secureId; Path=/; Domain=$domain; Secure",
            )
        }
        manager.flush()
    }

    private fun installAppCookie(url: String) {
        val host = Uri.parse(url).host.orEmpty()
        if (host != "dmm.com" && !host.endsWith(".dmm.com") &&
            host != "dmm.co.jp" && !host.endsWith(".dmm.co.jp")
        ) return
        val suffix = if (host.endsWith("dmm.co.jp")) "co.jp" else "com"
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setCookie(url, "dmm_app=1; Path=/; Domain=.dmm.$suffix; Secure")
            flush()
        }
    }

    private fun webRequestHeaders(session: DmmSession? = sessionStore.load()): Map<String, String> =
        buildMap {
            put("SMARTPHONE_APP", "DMM-APP")
            put("SMARTPHONE-APP", "DMM-APP")
            session?.uniqueId?.takeIf { it.isNotBlank() }?.let {
                put("SMARTPHONE_REQ_INTS", it)
            }
        }

    private fun clearDmmAuthenticationCookies() {
        val manager = CookieManager.getInstance()
        val names = listOf(
            "secid",
            "dmm_app_uid",
            "althash",
            "has_althash",
            "INT_SESID",
            "INT_SESID_SECURE",
            "login_session_id",
            "login_secure_id",
        )
        listOf("com", "co.jp").forEach { suffix ->
            val domain = ".dmm.$suffix"
            listOf("www", "accounts").forEach { host ->
                val origin = "https://$host.dmm.$suffix/"
                names.forEach { name ->
                    manager.setCookie(
                        origin,
                        "$name=; Path=/; Domain=$domain; Max-Age=0; Secure",
                    )
                }
            }
        }
        manager.flush()
    }

    private suspend fun downloadToDevice(
        url: String,
        title: String,
        session: DmmSession?,
    ) = withContext(Dispatchers.IO) {
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
            if (existing > 0L) connection.setRequestProperty("Range", "bytes=$existing-")
            CookieManager.getInstance().getCookie(url)?.let { cookie ->
                connection.setRequestProperty("Cookie", cookie)
            }
            webRequestHeaders(session).forEach(connection::setRequestProperty)
            val status = connection.responseCode
            if (status == 416 && partial.isFile) {
                check(partial.renameTo(destination)) { "Unable to finalize the existing download." }
                return@withContext
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
        } finally {
            connection.disconnect()
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
        .filter { it.isFile }
        .sortedByDescending { it.lastModified() }
        .map { file ->
            DmmDownloadItem(
                title = file.name.removeSuffix(".part").removeSuffix(".wsdcf"),
                path = file.absolutePath,
                bytesDownloaded = file.length(),
                totalBytes = file.length().takeIf { !file.name.endsWith(".part") },
                isComplete = file.name.endsWith(".wsdcf", ignoreCase = true),
            )
        }

    private fun fail(message: String) {
        _state.value = _state.value.copy(isBusy = false, statusMessage = message)
    }

    companion object {
        fun mediaTitle(url: String): String = Uri.parse(url).lastPathSegment
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() }
            ?: "DMM video"

        fun extractWsdUrl(url: String): String? {
            val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
            if (uri.path.orEmpty().endsWith(".wsdcf", ignoreCase = true)) return url
            return listOf("url", "download_url", "content_url", "stream_url")
                .asSequence()
                .mapNotNull(uri::getQueryParameter)
                .firstOrNull { candidate ->
                    Uri.parse(candidate).path.orEmpty().endsWith(".wsdcf", ignoreCase = true)
                }
        }

        private fun sanitizeFileName(value: String): String = value
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .take(120)

        private fun sessionRestorableUrl(url: String): String {
            val uri = Uri.parse(url)
            if (uri.host == "www.dmm.com" && uri.path.orEmpty().startsWith("/my/-/through")) {
                return url
            }
            return Uri.parse("https://www.dmm.com/my/-/through/")
                .buildUpon()
                .appendQueryParameter("path", url)
                .build()
                .toString()
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }

        private fun Throwable.userMessage(): String =
            (this as? java.lang.reflect.InvocationTargetException)?.targetException?.message
                ?: message
                ?: javaClass.simpleName

        private const val DMM_AUTH_SDK_VERSION = "6.3.6"
    }
}
