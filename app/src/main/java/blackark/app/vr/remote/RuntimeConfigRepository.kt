package blackark.app.vr.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface RemoteConfigStatus {
    data object Idle : RemoteConfigStatus
    data object Checking : RemoteConfigStatus
    data object AuthenticationRequired : RemoteConfigStatus
    data class Ready(val changed: Boolean) : RemoteConfigStatus
    data class Failed(val message: String) : RemoteConfigStatus
}

internal fun firstValidRemoteManifest(candidates: Sequence<String>): RemoteManifest? =
    candidates.mapNotNull { json ->
        runCatching { RemoteManifestParser.parse(json) }.getOrNull()
    }.firstOrNull()

class RuntimeConfigRepository(
    context: Context,
    private val credentialStore: GitHubCredentialStore,
    private val client: RemoteManifestClient = GitHubRemoteConfigClient(),
) {
    private val appContext = context.applicationContext
    private val cacheDirectory = File(appContext.filesDir, CACHE_DIRECTORY_NAME)
    private val currentFile = File(cacheDirectory, CURRENT_FILE_NAME)
    private val previousFile = File(cacheDirectory, PREVIOUS_FILE_NAME)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _snapshot = MutableStateFlow(loadBestAvailableSnapshot())
    val snapshot: StateFlow<RuntimeConfigSnapshot> = _snapshot.asStateFlow()
    private val _status = MutableStateFlow<RemoteConfigStatus>(RemoteConfigStatus.Idle)
    val status: StateFlow<RemoteConfigStatus> = _status.asStateFlow()

    init {
        RuntimeConfigRegistry.update(_snapshot.value)
    }

    fun loadCached(): RuntimeConfigSnapshot = _snapshot.value

    suspend fun refresh(tokenOverride: String? = null): RemoteConfigStatus {
        val token = tokenOverride?.trim()?.takeIf(String::isNotBlank) ?: credentialStore.getToken()
        if (token == null) {
            return RemoteConfigStatus.AuthenticationRequired.also { _status.value = it }
        }
        _status.value = RemoteConfigStatus.Checking
        val result = withTimeoutOrNull(REMOTE_REFRESH_TIMEOUT_MS) {
            client.fetch(token, cachedEtag())
        } ?: RemoteManifestFetchResult.Failure(
            statusCode = null,
            message = "GitHub manifest check timed out",
        )
        return when (result) {
            RemoteManifestFetchResult.NotModified -> {
                val ready = RemoteConfigStatus.Ready(changed = false)
                preferences.edit().putLong(KEY_LAST_CHECKED_AT, System.currentTimeMillis()).apply()
                _status.value = ready
                ready
            }

            is RemoteManifestFetchResult.Failure -> {
                val failed = RemoteConfigStatus.Failed(result.message)
                _status.value = failed
                failed
            }

            is RemoteManifestFetchResult.Modified -> applyModifiedManifest(result)
        }
    }

    suspend fun testToken(token: String): Result<Unit> {
        val normalized = token.trim()
        if (normalized.isBlank()) return Result.failure(IllegalArgumentException("GitHub token is empty"))
        val result = withTimeoutOrNull(REMOTE_REFRESH_TIMEOUT_MS) {
            client.fetch(normalized, etag = null)
        } ?: RemoteManifestFetchResult.Failure(
            statusCode = null,
            message = "GitHub manifest check timed out",
        )
        return when (result) {
            is RemoteManifestFetchResult.Modified,
            RemoteManifestFetchResult.NotModified -> Result.success(Unit)

            is RemoteManifestFetchResult.Failure -> Result.failure(
                IllegalStateException(result.message)
            )
        }
    }

    fun lastCheckedAt(): Long? = preferences.getLong(KEY_LAST_CHECKED_AT, 0L).takeIf { it > 0L }

    private fun cachedEtag(): String? {
        val hasValidCurrentManifest = currentFile.isFile &&
            runCatching { RemoteManifestParser.parse(currentFile.readText()) }.isSuccess
        return preferences.getString(KEY_ETAG, null).takeIf { hasValidCurrentManifest }
    }

    private suspend fun applyModifiedManifest(
        result: RemoteManifestFetchResult.Modified,
    ): RemoteConfigStatus = withContext(Dispatchers.IO) {
        val parsed = runCatching { RemoteManifestParser.parse(result.json) }
            .getOrElse { error ->
                Log.w(TAG, "Rejected remote manifest", error)
                return@withContext RemoteConfigStatus.Failed(
                    error.message ?: "Remote manifest is invalid"
                ).also { _status.value = it }
            }
        val now = System.currentTimeMillis()
        runCatching { writeLastKnownGood(result.json) }
            .onFailure { error -> Log.w(TAG, "Could not persist remote manifest", error) }

        val nextSnapshot = RuntimeConfigSnapshot(
            source = RuntimeConfigSource.Remote,
            manifestSha = result.manifestSha,
            fetchedAt = now,
            config = parsed.runtime,
        )
        _snapshot.value = nextSnapshot
        RuntimeConfigRegistry.update(nextSnapshot)
        preferences.edit()
            .putString(KEY_ETAG, result.etag)
            .putString(KEY_MANIFEST_SHA, result.manifestSha)
            .putLong(KEY_FETCHED_AT, now)
            .putLong(KEY_LAST_CHECKED_AT, now)
            .apply()
        RemoteConfigStatus.Ready(changed = true).also { _status.value = it }
    }

    private fun loadBestAvailableSnapshot(): RuntimeConfigSnapshot {
        val bundledJson = appContext.assets.open(BUNDLED_MANIFEST_ASSET).bufferedReader().use { it.readText() }
        val bundledManifest = runCatching { RemoteManifestParser.parse(bundledJson) }
            .getOrElse { RemoteManifest(1, "internal", RuntimeConfig()) }
        val bundledSnapshot = RuntimeConfigSnapshot(
            source = RuntimeConfigSource.Bundled,
            manifestSha = null,
            fetchedAt = null,
            config = bundledManifest.runtime,
        )

        val currentManifest = currentFile.takeIf(File::isFile)?.let { file ->
            runCatching { RemoteManifestParser.parse(file.readText()) }.getOrNull()
        }
        val previousManifest = previousFile.takeIf(File::isFile)?.let { file ->
            runCatching { RemoteManifestParser.parse(file.readText()) }.getOrNull()
        }
        val cached = currentManifest ?: previousManifest ?: return bundledSnapshot
        return RuntimeConfigSnapshot(
            source = RuntimeConfigSource.Cache,
            manifestSha = preferences.getString(KEY_MANIFEST_SHA, null)
                .takeIf { currentManifest != null },
            fetchedAt = preferences.getLong(KEY_FETCHED_AT, 0L).takeIf { it > 0L },
            config = cached.runtime,
        )
    }

    private fun writeLastKnownGood(json: String) {
        cacheDirectory.mkdirs()
        if (currentFile.isFile) {
            val previousTemporary = File(cacheDirectory, "$PREVIOUS_FILE_NAME.tmp")
            currentFile.copyTo(previousTemporary, overwrite = true)
            replaceAtomically(previousTemporary, previousFile)
        }
        val temporary = File(cacheDirectory, "$CURRENT_FILE_NAME.tmp")
        temporary.writeText(json)
        replaceAtomically(temporary, currentFile)
    }

    private fun replaceAtomically(source: File, target: File) {
        runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    companion object {
        private const val TAG = "RuntimeConfigRepository"
        private const val CACHE_DIRECTORY_NAME = "remote-config"
        private const val CURRENT_FILE_NAME = "current.json"
        private const val PREVIOUS_FILE_NAME = "previous.json"
        private const val BUNDLED_MANIFEST_ASSET = "runtime-config-default.json"
        private const val PREFERENCES_NAME = "remote_config_metadata"
        private const val KEY_ETAG = "etag"
        private const val KEY_MANIFEST_SHA = "manifest_sha"
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val KEY_LAST_CHECKED_AT = "last_checked_at"
        private const val REMOTE_REFRESH_TIMEOUT_MS = 2_000L
    }
}
