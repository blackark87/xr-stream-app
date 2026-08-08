package blackark.app.vr.remote

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedVersionCode: Long) : UpdateState
    data class Available(
        val release: RemoteRelease,
        val required: Boolean,
    ) : UpdateState

    data class Downloading(
        val release: RemoteRelease,
        val progress: Float,
    ) : UpdateState

    data class ReadyToInstall(
        val release: RemoteRelease,
        val apkFile: File,
    ) : UpdateState

    data class Installing(val release: RemoteRelease) : UpdateState
    data class Failed(
        val message: String,
        val release: RemoteRelease? = null,
    ) : UpdateState
}

data class VerifiedApk(
    val file: File,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val signingCertificateSha256: String,
)

internal fun shouldOfferAppUpdate(
    installedVersionCode: Long,
    releaseVersionCode: Long?,
): Boolean = releaseVersionCode != null && releaseVersionCode > installedVersionCode

internal fun verifyDownloadedArtifact(
    expectedPackageName: String,
    expectedRelease: RemoteRelease,
    actualPackageName: String,
    actualVersionCode: Long,
    actualVersionName: String,
    actualSizeBytes: Long,
    actualSha256: String,
    installedSigningCertificateSha256: String,
    archiveSigningCertificateSha256: String,
) {
    require(actualPackageName == expectedPackageName) { "Update package name does not match" }
    require(actualVersionCode == expectedRelease.versionCode) { "Update version code does not match" }
    require(actualVersionName == expectedRelease.versionName) { "Update version name does not match" }
    require(actualSizeBytes == expectedRelease.sizeBytes) { "Update file size does not match" }
    require(actualSha256.equals(expectedRelease.sha256, ignoreCase = true)) {
        "Update SHA-256 does not match"
    }
    require(
        archiveSigningCertificateSha256.equals(
            expectedRelease.signingCertificateSha256,
            ignoreCase = true,
        )
    ) { "Update signing certificate does not match manifest" }
    require(
        archiveSigningCertificateSha256.equals(
            installedSigningCertificateSha256,
            ignoreCase = true,
        )
    ) { "Update signing certificate does not match installed app" }
}

class AppUpdateManager(
    context: Context,
    private val credentialStore: GitHubCredentialStore,
) {
    private val appContext = context.applicationContext
    private val updateDirectory = File(appContext.filesDir, UPDATE_DIRECTORY_NAME)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun check(snapshot: RuntimeConfigSnapshot): UpdateState {
        if (!snapshot.config.features.remoteUpdatesEnabled) {
            return UpdateState.Idle.also { _state.value = it }
        }
        val installedVersionCode = installedPackageInfo().longVersionCode
        val release = snapshot.manifest?.release
        val next = if (shouldOfferAppUpdate(installedVersionCode, release?.versionCode)) {
            UpdateState.Available(
                release = requireNotNull(release),
                required = installedVersionCode <
                    (snapshot.manifest?.minimumAppVersionCode ?: 1L),
            )
        } else {
            UpdateState.UpToDate(installedVersionCode)
        }
        if (_state.value !is UpdateState.Downloading &&
            _state.value !is UpdateState.ReadyToInstall &&
            _state.value !is UpdateState.Installing
        ) {
            _state.value = next
        }
        return _state.value
    }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading && _state.value !is UpdateState.Installing) {
            _state.value = UpdateState.Idle
        }
    }

    suspend fun downloadAndVerify(release: RemoteRelease): Result<VerifiedApk> {
        val token = credentialStore.getToken()
            ?: return fail(release, "GitHub token is not configured")
        _state.value = UpdateState.Downloading(release, 0f)
        return withContext(Dispatchers.IO) {
            runCatching {
                updateDirectory.mkdirs()
                updateDirectory.listFiles()
                    ?.filter { it.isFile && it.name != updateFileName(release) }
                    ?.forEach(File::delete)
                val partial = File(updateDirectory, "${updateFileName(release)}.part")
                val target = File(updateDirectory, updateFileName(release))
                partial.delete()
                target.delete()
                val sha256 = downloadAsset(release, token, partial)
                require(partial.length() == release.sizeBytes) { "Downloaded APK size does not match" }
                require(sha256.equals(release.sha256, ignoreCase = true)) {
                    "Downloaded APK SHA-256 does not match"
                }
                require(partial.renameTo(target) || run {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                    true
                })
                val verified = verifyArchive(target, release, sha256)
                _state.value = UpdateState.ReadyToInstall(release, target)
                verified
            }.onFailure { error ->
                if (error is CancellationException) {
                    _state.value = UpdateState.Failed("Update download was cancelled", release)
                    throw error
                }
                Log.w(TAG, "Update download or verification failed", error)
                _state.value = UpdateState.Failed(
                    message = error.message ?: "Update verification failed",
                    release = release,
                )
            }
        }
    }

    fun requestInstall(): Boolean {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return false
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(permissionIntent)
            return false
        }

        var sessionId: Int? = null
        return runCatching {
            val installer = appContext.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(appContext.packageName)
                setSize(ready.apkFile.length())
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            sessionId = installer.createSession(params)
            installer.openSession(requireNotNull(sessionId)).use { session ->
                ready.apkFile.inputStream().use { input ->
                    session.openWrite("base.apk", 0L, ready.apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val statusIntent = Intent(appContext, AppUpdateStatusReceiver::class.java).apply {
                    action = AppUpdateStatusReceiver.ACTION_INSTALL_STATUS
                    putExtra(AppUpdateStatusReceiver.EXTRA_VERSION_CODE, ready.release.versionCode)
                }
                val statusSender = PendingIntent.getBroadcast(
                    appContext,
                    ready.release.versionCode.hashCode(),
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ).intentSender
                _state.value = UpdateState.Installing(ready.release)
                session.commit(statusSender)
            }
            true
        }.getOrElse { error ->
            sessionId?.let { id ->
                runCatching { appContext.packageManager.packageInstaller.abandonSession(id) }
            }
            Log.w(TAG, "Could not start package installer", error)
            _state.value = UpdateState.Failed(
                message = error.message ?: "Could not start Android installer",
                release = ready.release,
            )
            false
        }
    }

    internal fun handleInstallStatus(intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmation != null) {
                    appContext.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    _state.value = UpdateState.Failed("Android installer confirmation is unavailable")
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                _state.value = UpdateState.Idle
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Android installer failed with status $status"
                _state.value = UpdateState.Failed(message)
            }
        }
    }

    private fun downloadAsset(
        release: RemoteRelease,
        token: String,
        destination: File,
    ): String {
        val apiUrl = URL(
            "https://api.github.com/repos/${GitHubRemoteConfigClient.REPOSITORY_OWNER}/" +
                "${GitHubRemoteConfigClient.REPOSITORY_NAME}/releases/assets/${release.assetId}"
        )
        val initial = openAssetConnection(apiUrl, token)
        val connection = when (initial.responseCode) {
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308 -> {
                val location = initial.getHeaderField("Location")
                    ?: throw IOException("GitHub release redirect is missing")
                initial.disconnect()
                val redirectUri = URI(location)
                require(isAllowedAssetUri(redirectUri)) { "GitHub release redirect was rejected" }
                (redirectUri.toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = NETWORK_TIMEOUT_MS
                    readTimeout = DOWNLOAD_TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                }
            }

            else -> initial
        }

        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "GitHub release download returned HTTP ${connection.responseCode}"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            connection.inputStream.use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        require(written <= release.sizeBytes) { "Downloaded APK is larger than manifest" }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        _state.value = UpdateState.Downloading(
                            release = release,
                            progress = if (release.sizeBytes > 0L) {
                                (written.toFloat() / release.sizeBytes).coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                        )
                    }
                }
            }
            return digest.digest().toHexString()
        } finally {
            connection.disconnect()
        }
    }

    private fun openAssetConnection(url: URL, token: String): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = DOWNLOAD_TIMEOUT_MS
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun verifyArchive(
        apkFile: File,
        release: RemoteRelease,
        actualSha256: String,
    ): VerifiedApk {
        val flags = PackageManager.PackageInfoFlags.of(
            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        )
        val archive = appContext.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: error("Downloaded file is not a valid APK")
        val local = installedPackageInfo(flags)
        val archiveCertificate = archive.signingInfo?.apkContentsSigners
            ?.firstOrNull()
            ?.toByteArray()
            ?.sha256Hex()
            ?: error("Downloaded APK signing certificate is unavailable")
        val installedCertificate = local.signingInfo?.apkContentsSigners
            ?.firstOrNull()
            ?.toByteArray()
            ?.sha256Hex()
            ?: error("Installed app signing certificate is unavailable")
        verifyDownloadedArtifact(
            expectedPackageName = appContext.packageName,
            expectedRelease = release,
            actualPackageName = archive.packageName,
            actualVersionCode = archive.longVersionCode,
            actualVersionName = archive.versionName.orEmpty(),
            actualSizeBytes = apkFile.length(),
            actualSha256 = actualSha256,
            installedSigningCertificateSha256 = installedCertificate,
            archiveSigningCertificateSha256 = archiveCertificate,
        )
        return VerifiedApk(
            file = apkFile,
            packageName = archive.packageName,
            versionCode = archive.longVersionCode,
            versionName = archive.versionName.orEmpty(),
            signingCertificateSha256 = archiveCertificate,
        )
    }

    private fun installedPackageInfo(
        flags: PackageManager.PackageInfoFlags = PackageManager.PackageInfoFlags.of(0L),
    ) = appContext.packageManager.getPackageInfo(appContext.packageName, flags)

    private fun fail(release: RemoteRelease?, message: String): Result<VerifiedApk> {
        _state.value = UpdateState.Failed(message, release)
        return Result.failure(IllegalStateException(message))
    }

    private fun updateFileName(release: RemoteRelease): String =
        "xr-stream-app-internal-${release.versionCode}.apk"

    private fun isAllowedAssetUri(uri: URI): Boolean {
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host == "github.com" ||
            host.endsWith(".githubusercontent.com") ||
            host.endsWith(".github.com")
    }

    companion object {
        private const val TAG = "AppUpdateManager"
        private const val UPDATE_DIRECTORY_NAME = "app-updates"
        private const val NETWORK_TIMEOUT_MS = 10_000
        private const val DOWNLOAD_TIMEOUT_MS = 120_000
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val USER_AGENT = "xr-stream-app-internal-updater"
    }
}

private fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256").digest(this).toHexString()

private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
