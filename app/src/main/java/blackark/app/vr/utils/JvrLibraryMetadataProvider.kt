package blackark.app.vr.utils

import android.content.Context
import android.util.Log
import blackark.app.vr.data.database.AppDatabase
import blackark.app.vr.data.database.dao.VirtualGroupMetadataDao
import blackark.app.vr.data.database.entity.JvrPerformerAlias
import blackark.app.vr.data.database.entity.JvrPerformerMergeRule
import blackark.app.vr.data.database.entity.JvrPerformer
import blackark.app.vr.data.database.entity.VirtualGroupMetadata
import blackark.app.vr.data.database.entity.VirtualGroupMetadataGenre
import blackark.app.vr.data.database.entity.VirtualGroupMetadataPerformerCrossRef
import blackark.app.vr.data.database.entity.VirtualGroupMetadataRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap

data class JvrCastMetadata(
    val performerId: String,
    val englishName: String,
    val japaneseName: String? = null,
    val profileImageUrl: String? = null,
    val remoteProfileImageUrl: String? = null,
)

data class JvrMovieMetadata(
    val code: String,
    val title: String,
    val posterUrl: String?,
    val releaseDate: LocalDate? = null,
    val studio: String? = null,
    val genres: List<String> = emptyList(),
    val casts: List<JvrCastMetadata> = emptyList(),
)

object JvrLibraryMetadataProvider {

    private const val TAG = "JvrLibraryMetadata"
    private const val BASE_URL = "https://jvrlibrary.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"

    private const val SOURCE_JVR = "jvr"
    private const val SOURCE_AV_WIKI = "avwiki"
    private const val SOURCE_NONE = "none"
    private const val ALIAS_KIND_ENGLISH = "english"
    private const val ALIAS_KIND_JAPANESE = "japanese"
    private const val ALIAS_KIND_LEGACY = "legacy"

    // Minimum delay between metadata requests to the same remote host.
    private const val THROTTLED_REQUEST_INTERVAL_MS = 300L
    private val THROTTLED_HOSTS = setOf(
        "jvrlibrary.com",
        "av-wiki.net",
    )
    private val metadataCache = ConcurrentHashMap<String, JvrMovieMetadata>()
    private val missCache = ConcurrentHashMap.newKeySet<String>()
    private val requestThrottleMutex = Mutex()
    private val lastRequestAtByHost = mutableMapOf<String, Long>()

    private data class PerformerAliasCandidate(
        val aliasKey: String,
        val aliasKind: String,
        val aliasValue: String,
    )

    private val placeholderTitlePatterns = listOf(
        Regex("^\\s*not\\s+found\\s*$", RegexOption.IGNORE_CASE),
        Regex("^\\s*movie\\s+not\\s+found\\s*$", RegexOption.IGNORE_CASE),
        Regex("^\\s*video\\s+not\\s+found\\s*$", RegexOption.IGNORE_CASE),
        Regex("^\\s*404(?:\\s+not\\s+found)?\\s*$", RegexOption.IGNORE_CASE),
        Regex("^\\s*n\\s*/?\\s*a\\s*$", RegexOption.IGNORE_CASE),
        Regex("^\\s*(?:null|none|undefined|error)\\s*$", RegexOption.IGNORE_CASE),
    )
    private const val VR_TOKEN_PATTERN = "(?:VR|8K|8KVR|VR8K)"
    private const val VR_BRACKET_TAG_PATTERN =
        "(?:\\[(?:$VR_TOKEN_PATTERN)(?:\\s*(?:$VR_TOKEN_PATTERN))*]|【(?:$VR_TOKEN_PATTERN)(?:\\s*(?:$VR_TOKEN_PATTERN))*】)"

    private fun logMetadataTrace(message: String) {
        Log.v(TAG, message)
    }

    fun peekCached(rawCode: String): JvrMovieMetadata? {
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) return null
        return metadataCache[buildCacheKey(code, SOURCE_JVR)]
    }

    fun buildMetadataCacheKey(rawCode: String, source: String): String {
        return buildCacheKey(rawCode.trim().uppercase(), source.trim())
    }

    fun resolveMetadataSource(folderPath: String): String? {
        return metadataSourceForPath(folderPath).takeUnless { it == SOURCE_NONE }
    }

    fun peekCachedByCacheKey(cacheKey: String): JvrMovieMetadata? {
        return metadataCache[cacheKey.trim()]
    }

    fun clearMemoryCaches() {
        metadataCache.clear()
        missCache.clear()
        Log.d(TAG, "Cleared in-memory metadata caches")
    }

    @Suppress("UNUSED_PARAMETER")
    fun peekCached(context: Context, rawCode: String, folderPath: String = ""): JvrMovieMetadata? {
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) return null

        val source = metadataSourceForPath(folderPath)
        val cacheKey = buildCacheKey(code, source)
        return metadataCache[cacheKey]?.let { cached ->
            val normalized = normalizeCachedMetadata(cached)
            metadataCache[cacheKey] = normalized
            normalized
        }
    }

    suspend fun getByCode(
        context: Context,
        rawCode: String,
        folderPath: String = ""
    ): JvrMovieMetadata? {
        val appContext = context.applicationContext
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) return null

        val source = metadataSourceForPath(folderPath)
        val cacheKey = buildCacheKey(code, source)
        val allowJvrLookup = shouldLookupJvrForPath(folderPath)

        logMetadataTrace("Lookup request code=$code path='$folderPath' source=$source allowJvr=$allowJvrLookup")

        metadataCache[cacheKey]?.let { cached ->
            val normalized = normalizeCachedMetadata(cached)
            metadataCache[cacheKey] = normalized
            if (normalized != cached) {
                savePersistedMetadata(appContext, cacheKey, source, normalized)
            }
            logMetadataTrace("Memory cache hit for $cacheKey (poster=${normalized.posterUrl ?: "<none>"})")
            return normalized
        }

        loadPersistedMetadata(appContext, cacheKey)?.let { persisted ->
            if (persisted.metadata.isMiss) {
                missCache += cacheKey
                logMetadataTrace("DB miss-cache hit for $cacheKey; skipping remote lookup")
                return null
            }

            val persistedMetadata = toMovieMetadata(
                record = persisted,
                fallbackCode = code,
            )
            val cachedMetadata = normalizeCachedMetadata(persistedMetadata)

            metadataCache[cacheKey] = cachedMetadata
            missCache.remove(cacheKey)

            if (cachedMetadata != persistedMetadata) {
                savePersistedMetadata(appContext, cacheKey, source, cachedMetadata)
            }

            logMetadataTrace("DB cache hit for $cacheKey (poster=${cachedMetadata.posterUrl ?: "<none>"})")
            return cachedMetadata
        }

        loadPersistedMetadataByCode(appContext, code)?.let { persistedByCode ->
            val persistedMetadata = toMovieMetadata(
                record = persistedByCode,
                fallbackCode = code,
            )
            val cachedMetadata = normalizeCachedMetadata(persistedMetadata)

            metadataCache[cacheKey] = cachedMetadata
            metadataCache[buildCacheKey(cachedMetadata.code, persistedByCode.metadata.source)] =
                cachedMetadata
            missCache.remove(cacheKey)

            if (cachedMetadata != persistedMetadata) {
                savePersistedMetadata(
                    appContext,
                    buildCacheKey(cachedMetadata.code, persistedByCode.metadata.source),
                    persistedByCode.metadata.source,
                    cachedMetadata,
                )
            }

            logMetadataTrace("DB code-level cache hit for code=$code source=${persistedByCode.metadata.source} (poster=${cachedMetadata.posterUrl ?: "<none>"})")
            return cachedMetadata
        }

        if (cacheKey in missCache) {
            logMetadataTrace("Memory miss-cache hit for $cacheKey; skipping remote lookup")
            return null
        }

        logMetadataTrace("Cache miss for $cacheKey; fetching metadata (source=$source, allowJvr=$allowJvrLookup, path='$folderPath')")

        val remoteMetadata = fetchRemoteMetadataForSource(
            code = code,
            source = source,
            allowJvrLookup = allowJvrLookup,
        )

        if (remoteMetadata == null) {
            missCache += cacheKey
            savePersistedMiss(appContext, cacheKey, code, source)
            if (!allowJvrLookup && source != SOURCE_AV_WIKI) {
                logMetadataTrace("No metadata resolved for $cacheKey because path is not eligible for JVR lookup; stored DB miss")
            } else {
                logMetadataTrace("No metadata resolved for $cacheKey; stored DB miss")
            }
            return null
        }

        val localizedMetadata = withContext(Dispatchers.IO) {
            localizeMediaAssets(appContext, cacheKey, remoteMetadata)
        }

        metadataCache[cacheKey] = localizedMetadata
        savePersistedMetadata(appContext, cacheKey, source, localizedMetadata)
        missCache.remove(cacheKey)

        logMetadataTrace("Resolved metadata for $cacheKey title='${localizedMetadata.title}' poster=${localizedMetadata.posterUrl ?: "<none>"}")

        return localizedMetadata
    }

    suspend fun getByCacheKey(
        context: Context,
        cacheKey: String,
    ): JvrMovieMetadata? {
        val normalizedCacheKey = cacheKey.trim()
        if (normalizedCacheKey.isBlank()) return null

        metadataCache[normalizedCacheKey]?.let { cached ->
            val normalized = normalizeCachedMetadata(cached)
            metadataCache[normalizedCacheKey] = normalized
            return normalized
        }

        val separatorIndex = normalizedCacheKey.indexOf(':')
        if (separatorIndex <= 0 || separatorIndex == normalizedCacheKey.lastIndex) {
            return null
        }

        val source = normalizedCacheKey.substring(0, separatorIndex)
        val code = normalizedCacheKey.substring(separatorIndex + 1).uppercase()

        loadPersistedMetadata(context.applicationContext, normalizedCacheKey)?.let { persisted ->
            if (persisted.metadata.isMiss) {
                missCache += normalizedCacheKey
                return null
            }

            val persistedMetadata = toMovieMetadata(
                record = persisted,
                fallbackCode = code,
            )
            val cachedMetadata = normalizeCachedMetadata(persistedMetadata)
            metadataCache[normalizedCacheKey] = cachedMetadata
            return cachedMetadata
        }

        if (normalizedCacheKey in missCache) {
            return null
        }

        val remoteMetadata = fetchRemoteMetadataForSource(
            code = code,
            source = source,
            allowJvrLookup = true,
        )

        if (remoteMetadata == null) {
            missCache += normalizedCacheKey
            savePersistedMiss(context.applicationContext, normalizedCacheKey, code, source)
            return null
        }

        val localizedMetadata = withContext(Dispatchers.IO) {
            localizeMediaAssets(context.applicationContext, normalizedCacheKey, remoteMetadata)
        }
        metadataCache[normalizedCacheKey] = localizedMetadata
        savePersistedMetadata(context.applicationContext, normalizedCacheKey, source, localizedMetadata)
        missCache.remove(normalizedCacheKey)
        return localizedMetadata
    }

    suspend fun refreshByCacheKey(
        context: Context,
        cacheKey: String,
        fallbackFolderPath: String = "",
    ): JvrMovieMetadata? {
        val normalizedCacheKey = cacheKey.trim()
        if (normalizedCacheKey.isBlank()) return null

        val separatorIndex = normalizedCacheKey.indexOf(':')
        if (separatorIndex <= 0 || separatorIndex == normalizedCacheKey.lastIndex) {
            return null
        }

        val source = normalizedCacheKey.substring(0, separatorIndex)
        val code = normalizedCacheKey.substring(separatorIndex + 1).uppercase()
        val allowJvrLookup = source == SOURCE_JVR || shouldLookupJvrForPath(fallbackFolderPath)

        metadataCache.remove(normalizedCacheKey)
        missCache.remove(normalizedCacheKey)

        val remoteMetadata = fetchRemoteMetadataForSource(
            code = code,
            source = source,
            allowJvrLookup = allowJvrLookup,
        ) ?: return null

        val localizedMetadata = withContext(Dispatchers.IO) {
            localizeMediaAssets(context.applicationContext, normalizedCacheKey, remoteMetadata)
        }

        metadataCache[normalizedCacheKey] = localizedMetadata
        savePersistedMetadata(context.applicationContext, normalizedCacheKey, source, localizedMetadata)
        missCache.remove(normalizedCacheKey)
        return localizedMetadata
    }

    suspend fun mergePerformersManually(
        context: Context,
        sourcePerformerId: String,
        targetPerformerId: String,
    ) {
        withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(context.applicationContext)
                .virtualGroupMetadataDao()
                .mergePerformerInto(
                    sourcePerformerId = sourcePerformerId,
                    targetPerformerId = targetPerformerId,
                    isManual = true,
                )
        }
    }

    suspend fun addPerformerAliasesManually(
        context: Context,
        performerId: String,
        englishName: String?,
        japaneseName: String?,
    ) {
        withContext(Dispatchers.IO) {
            val metadataDao = AppDatabase.getDatabase(context.applicationContext)
                .virtualGroupMetadataDao()
            val canonicalPerformerId = resolveCanonicalPerformerId(metadataDao, performerId)
            val candidates = buildPerformerAliasCandidates(
                legacyPerformerId = null,
                englishName = normalizeText(englishName),
                japaneseName = normalizeText(japaneseName),
            )

            if (candidates.isEmpty()) {
                return@withContext
            }

            val matchedCanonicalIds = metadataDao.getAliasesByKeys(candidates.map(PerformerAliasCandidate::aliasKey))
                .map { alias -> resolveCanonicalPerformerId(metadataDao, alias.canonicalPerformerId) }
                .filter { it.isNotBlank() && it != canonicalPerformerId }
                .distinct()

            matchedCanonicalIds.forEach { sourcePerformerId ->
                metadataDao.mergePerformerInto(
                    sourcePerformerId = sourcePerformerId,
                    targetPerformerId = canonicalPerformerId,
                    isManual = true,
                )
            }

            val existingCanonical = metadataDao.getPerformerById(canonicalPerformerId)
            val mergedCanonical = mergeCanonicalPerformer(
                canonicalPerformerId = canonicalPerformerId,
                existing = existingCanonical,
                cast = JvrCastMetadata(
                    performerId = canonicalPerformerId,
                    englishName = englishName.orEmpty(),
                    japaneseName = japaneseName,
                ),
            )
            metadataDao.upsertPerformers(listOf(mergedCanonical))
            metadataDao.upsertAliases(
                candidates.map { candidate ->
                    JvrPerformerAlias(
                        aliasKey = candidate.aliasKey,
                        canonicalPerformerId = canonicalPerformerId,
                        aliasKind = candidate.aliasKind,
                        aliasValue = candidate.aliasValue,
                        source = "manual",
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            )
        }
    }

    suspend fun saveManualMetadata(
        context: Context,
        cacheKey: String,
        source: String,
        metadata: JvrMovieMetadata,
    ) {
        val normalizedCacheKey = cacheKey.trim()
        if (normalizedCacheKey.isBlank()) return

        metadataCache.remove(normalizedCacheKey)
        missCache.remove(normalizedCacheKey)
        savePersistedMetadata(
            context = context.applicationContext,
            cacheKey = normalizedCacheKey,
            source = source,
            metadata = metadata,
        )
    }

    internal fun toPersistedRecord(
        cacheKey: String,
        source: String,
        metadata: JvrMovieMetadata,
        isMiss: Boolean = false,
        updatedAt: Long = System.currentTimeMillis(),
    ): VirtualGroupMetadataRecord {
        val genres = normalizeGenres(metadata.genres)
        val casts = normalizeCasts(metadata.casts)
        val performers = casts.map { cast ->
            JvrPerformer(
                performerId = cast.performerId,
                englishName = cast.englishName,
                japaneseName = cast.japaneseName,
                remoteProfileImageUrl = cast.remoteProfileImageUrl
                    ?: cast.profileImageUrl?.takeUnless(::isLocalImageUrl),
                localProfileImageUrl = cast.profileImageUrl?.let(::validateLocalImageUrl),
                updatedAt = updatedAt,
            )
        }.distinctBy { it.performerId }

        return VirtualGroupMetadataRecord(
            metadata = VirtualGroupMetadata(
                cacheKey = cacheKey,
                code = metadata.code,
                source = source,
                title = metadata.title,
                posterUrl = metadata.posterUrl,
                releaseDateEpochDay = metadata.releaseDate?.toEpochDay(),
                studio = metadata.studio?.trim()?.takeIf { it.isNotBlank() },
                isMiss = isMiss,
                updatedAt = updatedAt,
            ),
            genres = genres.mapIndexed { index, genre ->
                VirtualGroupMetadataGenre(
                    cacheKey = cacheKey,
                    position = index,
                    genre = genre,
                )
            },
            performerRefs = casts.mapIndexed { index, cast ->
                VirtualGroupMetadataPerformerCrossRef(
                    cacheKey = cacheKey,
                    performerId = cast.performerId,
                    position = index,
                )
            },
            performers = performers,
        )
    }

    internal fun toMovieMetadata(
        record: VirtualGroupMetadataRecord,
        fallbackCode: String? = null,
    ): JvrMovieMetadata {
        val code = record.metadata.code
            .ifBlank { fallbackCode.orEmpty() }
            .ifBlank { record.metadata.cacheKey.substringAfter(':', SOURCE_JVR) }
            .ifBlank { SOURCE_JVR }
        val performersById = record.performers.associateBy { it.performerId }
        return JvrMovieMetadata(
            code = code,
            title = record.metadata.title?.takeIf { it.isNotBlank() } ?: code,
            posterUrl = record.metadata.posterUrl,
            releaseDate = record.metadata.releaseDateEpochDay?.let(LocalDate::ofEpochDay),
            studio = record.metadata.studio?.trim()?.takeIf { it.isNotBlank() },
            genres = record.genres
                .sortedBy { it.position }
                .map { it.genre }
                .let(::normalizeGenres),
            casts = record.performerRefs
                .sortedBy { it.position }
                .mapNotNull { ref ->
                    performersById[ref.performerId]?.let { performer ->
                        JvrCastMetadata(
                            performerId = performer.performerId,
                            englishName = performer.englishName,
                            japaneseName = performer.japaneseName,
                            profileImageUrl = resolveStoredPerformerImageUrl(performer),
                            remoteProfileImageUrl = performer.remoteProfileImageUrl,
                        )
                    }
                }
                .let(::normalizeCasts),
        )
    }

    private fun metadataSourceForPath(folderPath: String): String {
        val normalized = normalizeFolderPath(folderPath)
        val hasMakerYearPattern = hasMakerYearPath(normalized)
        val hasAvVrPattern = shouldLookupJvrForPath(normalized)

        val source = when {
            hasMakerYearPattern -> SOURCE_AV_WIKI
            hasAvVrPattern -> SOURCE_JVR
            else -> SOURCE_NONE
        }

        logMetadataTrace(
            "Metadata source routing path='$folderPath' normalized='$normalized' makerYear=$hasMakerYearPattern avVr=$hasAvVrPattern -> source=$source"
        )

        return source
    }

    private fun hasMakerYearPath(folderPath: String): Boolean {
        val segments = folderPath
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (segments.size < 2) return false

        for (index in 0 until segments.size - 1) {
            val makerSegment = segments[index]
            val yearSegment = segments[index + 1]
            if (
                makerSegment.equals("maker", ignoreCase = true) &&
                yearSegment.matches(Regex("(?:19|20)\\d{2}"))
            ) {
                return true
            }
        }

        return false
    }

    private fun shouldLookupJvrForPath(folderPath: String): Boolean {
        val normalized = normalizeFolderPath(folderPath)
        return Regex("(^|/)av/vr(/|$)", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
    }

    private fun normalizeFolderPath(folderPath: String): String {
        return folderPath.replace('\\', '/').trim()
    }

    private fun buildCacheKey(code: String, source: String): String {
        return "$source:$code"
    }

    private suspend fun fetchRemoteMetadataForSource(
        code: String,
        source: String,
        allowJvrLookup: Boolean,
    ): JvrMovieMetadata? {
        return withContext(Dispatchers.IO) {
            when (source) {
                SOURCE_AV_WIKI -> {
                    fetchByCodeFromAvWiki(code) ?: if (allowJvrLookup) {
                        fetchByCodeFromJvrLibrary(code)
                    } else {
                        null
                    }
                }

                SOURCE_JVR -> if (allowJvrLookup) fetchByCodeFromJvrLibrary(code) else null
                else -> null
            }
        }
    }

    private suspend fun fetchByCodeFromAvWiki(code: String): JvrMovieMetadata? {
        val avWikiUrl = "https://av-wiki.net/${code.lowercase()}/"
        logMetadataTrace("Starting AV-Wiki metadata lookup for $code url=$avWikiUrl")
        val html = fetchPageHtml(avWikiUrl) ?: return null
        return parseAvWikiMetadataHtml(code = code, html = html, baseUrl = avWikiUrl)
    }

    private suspend fun fetchByCodeFromJvrLibrary(code: String): JvrMovieMetadata? {
        val encodedCode = URLEncoder.encode(code, Charsets.UTF_8.name())
        val zhUrl = "$BASE_URL/zh/jvr?id=$encodedCode"
        val enUrl = "$BASE_URL/jvr?id=$encodedCode"

        logMetadataTrace("Starting JVR metadata lookup for $code")

        val zhMetadata = fetchPageHtml(zhUrl)?.let { parseJvrMetadataHtml(code, it) }
        logMetadataTrace("JVR ZH parse for $code -> title='${zhMetadata?.title ?: "<none>"}', poster=${zhMetadata?.posterUrl ?: "<none>"}")

        val enMetadata = fetchPageHtml(enUrl)?.let { parseJvrMetadataHtml(code, it) }
        logMetadataTrace("JVR EN parse for $code -> title='${enMetadata?.title ?: "<none>"}', poster=${enMetadata?.posterUrl ?: "<none>"}")

        if (zhMetadata == null && enMetadata == null) {
            logMetadataTrace("JVR lookup finished for $code with no metadata")
            return null
        }

        return mergeJvrMetadata(
            code = code,
            preferredTitleMetadata = zhMetadata,
            englishMetadata = enMetadata,
        )
    }

    private fun mergeJvrMetadata(
        code: String,
        preferredTitleMetadata: JvrMovieMetadata?,
        englishMetadata: JvrMovieMetadata?,
    ): JvrMovieMetadata {
        return JvrMovieMetadata(
            code = code,
            title = selectPreferredTitle(
                code,
                preferredTitleMetadata?.title,
                englishMetadata?.title,
            ),
            posterUrl = preferredTitleMetadata?.posterUrl ?: englishMetadata?.posterUrl,
            releaseDate = englishMetadata?.releaseDate ?: preferredTitleMetadata?.releaseDate,
            studio = englishMetadata?.studio ?: preferredTitleMetadata?.studio,
            genres = if (!englishMetadata?.genres.isNullOrEmpty()) {
                englishMetadata.genres
            } else {
                preferredTitleMetadata?.genres.orEmpty()
            },
            casts = if (!englishMetadata?.casts.isNullOrEmpty()) {
                englishMetadata.casts
            } else {
                preferredTitleMetadata?.casts.orEmpty()
            },
        )
    }

    private suspend fun localizeMediaAssets(
        context: Context,
        cacheKey: String,
        metadata: JvrMovieMetadata,
    ): JvrMovieMetadata {
        val localizedPoster = localizePoster(
            context = context,
            cacheKey = cacheKey,
            metadata = metadata,
        )
        val localizedCasts = localizeCastImages(
            context = context,
            casts = localizedPoster.casts,
        )
        return localizedPoster.copy(casts = localizedCasts)
    }

    private suspend fun localizePoster(
        context: Context,
        cacheKey: String,
        metadata: JvrMovieMetadata
    ): JvrMovieMetadata {
        val remotePoster = metadata.posterUrl?.trim().orEmpty()
        if (remotePoster.isBlank()) {
            return metadata.copy(posterUrl = null)
        }

        val absolutePoster = toAbsoluteUrl(remotePoster)
        val localPoster = downloadImageToLocal(
            context = context,
            directoryName = "group_posters",
            fileKey = cacheKey.lowercase().replace(':', '_').replace('/', '_'),
            imageUrl = absolutePoster,
        )

        if (localPoster != null) {
            Log.d(TAG, "Poster localized for $cacheKey: $localPoster")
            return metadata.copy(posterUrl = localPoster)
        }

        Log.w(TAG, "Poster localization failed for $cacheKey. Falling back to generated thumbnail.")
        return metadata.copy(posterUrl = null)
    }

    private suspend fun localizeCastImages(
        context: Context,
        casts: List<JvrCastMetadata>,
    ): List<JvrCastMetadata> {
        return casts.map { cast ->
            val remoteImageUrl = cast.remoteProfileImageUrl
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: cast.profileImageUrl?.takeUnless(::isLocalImageUrl)

            if (remoteImageUrl.isNullOrBlank()) {
                return@map cast
            }

            val absoluteRemoteUrl = toAbsoluteUrl(remoteImageUrl)
            val localImageUrl = downloadImageToLocal(
                context = context,
                directoryName = "performer_profiles",
                fileKey = cast.performerId.lowercase().replace(':', '_').replace('/', '_'),
                imageUrl = absoluteRemoteUrl,
            )

            if (localImageUrl != null) {
                cast.copy(
                    profileImageUrl = localImageUrl,
                    remoteProfileImageUrl = absoluteRemoteUrl,
                )
            } else {
                cast.copy(
                    profileImageUrl = cast.profileImageUrl ?: absoluteRemoteUrl,
                    remoteProfileImageUrl = absoluteRemoteUrl,
                )
            }
        }
    }

    private suspend fun downloadImageToLocal(
        context: Context,
        directoryName: String,
        fileKey: String,
        imageUrl: String,
    ): String? {
        return try {
            val imageDir = File(context.filesDir, directoryName)
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }

            val extension = extractImageExtension(imageUrl)
            val targetFile = File(imageDir, "$fileKey.$extension")

            if (targetFile.exists() && targetFile.length() > 0L) {
                return targetFile.toURI().toString()
            }

            awaitRequestSlot(imageUrl)
            val connection = (URL(imageUrl).openConnection() as? HttpURLConnection) ?: return null
            connection.connectTimeout = 7000
            connection.readTimeout = 9000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)

            try {
                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    Log.w(
                        TAG,
                        "Image download failed for key=$fileKey with HTTP $statusCode: $imageUrl"
                    )
                    return null
                }

                connection.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (!targetFile.exists() || targetFile.length() <= 0L) {
                runCatching { targetFile.delete() }
                return null
            }

            targetFile.toURI().toString()
        } catch (e: Exception) {
            Log.w(TAG, "Image download exception for key=$fileKey: ${e.message}")
            null
        }
    }

    private fun extractImageExtension(url: String): String {
        val path = runCatching { URL(url).path.lowercase() }.getOrDefault(url.lowercase())
        return when {
            path.endsWith(".png") -> "png"
            path.endsWith(".webp") -> "webp"
            path.endsWith(".jpeg") -> "jpeg"
            else -> "jpg"
        }
    }

    private suspend fun loadPersistedMetadataByCode(
        context: Context,
        code: String
    ): VirtualGroupMetadataRecord? {
        return withContext(Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(context)
                    .virtualGroupMetadataDao()
                    .getLatestHitByCode(code)
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Failed to load virtual group metadata by code from DB for $code: ${e.message}"
                )
                null
            }
        }
    }

    private suspend fun loadPersistedMetadata(
        context: Context,
        cacheKey: String
    ): VirtualGroupMetadataRecord? {
        return withContext(Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(context)
                    .virtualGroupMetadataDao()
                    .getByCacheKey(cacheKey)
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Failed to load virtual group metadata from DB for $cacheKey: ${e.message}"
                )
                null
            }
        }
    }

    private suspend fun savePersistedMetadata(
        context: Context,
        cacheKey: String,
        source: String,
        metadata: JvrMovieMetadata,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(context)
                val metadataDao = database.virtualGroupMetadataDao()
                val resolvedMetadata = resolveCanonicalCastMetadata(
                    metadataDao = metadataDao,
                    source = source,
                    metadata = metadata,
                )

                metadataDao.replace(
                    toPersistedRecord(
                        cacheKey = cacheKey,
                        source = source,
                        metadata = resolvedMetadata,
                    )
                )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Failed to save virtual group metadata to DB for $cacheKey: ${e.message}"
                )
            }
        }
    }

    private suspend fun savePersistedMiss(
        context: Context,
        cacheKey: String,
        code: String,
        source: String,
    ) {
        withContext(Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(context)
                    .virtualGroupMetadataDao()
                    .replace(
                        toPersistedRecord(
                            cacheKey = cacheKey,
                            source = source,
                            metadata = JvrMovieMetadata(
                                code = code,
                                title = code,
                                posterUrl = null,
                            ),
                            isMiss = true,
                        )
                    )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Failed to save virtual group miss marker to DB for $cacheKey: ${e.message}"
                )
            }
        }
    }

    private fun normalizeCachedMetadata(metadata: JvrMovieMetadata): JvrMovieMetadata {
        val validated = validateLocalPosterPath(metadata)
        return validated.copy(
            title = selectPreferredTitle(validated.code, validated.title),
            studio = validated.studio?.trim()?.takeIf { it.isNotBlank() },
            genres = normalizeGenres(validated.genres),
            casts = normalizeCasts(validateLocalCastImagePaths(validated.casts)),
        )
    }

    private fun validateLocalPosterPath(metadata: JvrMovieMetadata): JvrMovieMetadata {
        val poster = metadata.posterUrl?.trim().orEmpty()
        if (poster.isBlank()) return metadata.copy(posterUrl = null)

        val resolvedLocalPoster = validateLocalImageUrl(poster)
        if (resolvedLocalPoster != null) {
            return metadata.copy(posterUrl = resolvedLocalPoster)
        }

        if (isLocalImageUrl(poster)) {
            return metadata.copy(posterUrl = null)
        }

        return metadata
    }

    private fun validateLocalCastImagePaths(casts: List<JvrCastMetadata>): List<JvrCastMetadata> {
        return casts.map { cast ->
            val localImageUrl = cast.profileImageUrl?.let(::validateLocalImageUrl)
            when {
                localImageUrl != null -> cast.copy(profileImageUrl = localImageUrl)
                cast.profileImageUrl?.let(::isLocalImageUrl) == true -> cast.copy(
                    profileImageUrl = cast.remoteProfileImageUrl
                )

                else -> cast
            }
        }
    }

    private fun validateLocalImageUrl(url: String): String? {
        val normalized = url.trim()
        if (!isLocalImageUrl(normalized)) return null

        val localFile = when {
            normalized.startsWith("file:", ignoreCase = true) -> {
                runCatching { File(URI(normalized)) }.getOrNull()
            }

            normalized.startsWith("/") -> File(normalized)
            else -> null
        } ?: return null

        return normalized.takeIf { localFile.exists() && localFile.length() > 0L }
    }

    private fun isLocalImageUrl(url: String): Boolean {
        return url.startsWith("file:", ignoreCase = true) || url.startsWith("/")
    }

    private suspend fun fetchPageHtml(urlString: String): String? {
        val url = runCatching { URL(urlString) }.getOrNull() ?: return null
        awaitRequestSlot(urlString)
        val connection = (url.openConnection() as? HttpURLConnection) ?: return null

        connection.connectTimeout = 7000
        connection.readTimeout = 9000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

        return try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                Log.w(TAG, "Metadata lookup failed for $urlString with HTTP $statusCode")
                return null
            }

            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Metadata lookup exception for $urlString: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun awaitRequestSlot(urlString: String) {
        val host = runCatching { URL(urlString).host.lowercase() }
            .getOrNull()
            ?.removePrefix("www.")
            ?: return
        if (host !in THROTTLED_HOSTS) {
            return
        }

        while (true) {
            val waitMs = requestThrottleMutex.withLock {
                val now = System.currentTimeMillis()
                val lastRequestAt = lastRequestAtByHost[host]
                if (lastRequestAt == null) {
                    lastRequestAtByHost[host] = now
                    0L
                } else {
                    val elapsed = now - lastRequestAt
                    val remaining = THROTTLED_REQUEST_INTERVAL_MS - elapsed
                    if (remaining <= 0L) {
                        lastRequestAtByHost[host] = now
                        0L
                    } else {
                        remaining
                    }
                }
            }

            if (waitMs <= 0L) {
                return
            }

            logMetadataTrace("Throttling $host request for ${waitMs}ms")
            delay(waitMs)
        }
    }

    internal fun parseJvrMetadataHtml(code: String, html: String): JvrMovieMetadata? {
        val document = Jsoup.parse(html, BASE_URL)
        val ogImage = extractMetaContent(document, "og:image")
        val ogDescription = extractMetaContent(document, "og:description")
        val htmlTitle = document.title().takeIf { it.isNotBlank() }

        val titleFromHtml = sanitizeTitleCandidate(cleanHtmlTitle(htmlTitle, code), code)
        val titleFromDescription =
            sanitizeTitleCandidate(cleanDescriptionTitle(ogDescription), code)
        val titleFromDiv = sanitizeTitleCandidate(extractTitleFromTitleDiv(document, code), code)

        val detailValues = extractDetailsValueElements(document)
        val releaseDate = parseReleaseDate(detailValues["Release Date"]?.text())
        val studio = normalizeText(detailValues["Studio"]?.text())
        val genres = extractGenres(detailValues["Genre"])
        val casts = extractCasts(detailValues["Casts"])

        val hasStructuredMetadata =
            releaseDate != null || !studio.isNullOrBlank() || genres.isNotEmpty() || casts.isNotEmpty()

        if (
            ogImage.isNullOrBlank() &&
            ogDescription.isNullOrBlank() &&
            htmlTitle.isNullOrBlank() &&
            titleFromDiv.isNullOrBlank() &&
            !hasStructuredMetadata
        ) {
            logMetadataTrace("JVR HTML parse had no useful metadata for code=$code")
            return null
        }

        val parsedTitle = selectPreferredTitle(
            code,
            titleFromHtml,
            titleFromDescription,
            titleFromDiv,
        )

        val normalizedImageUrl = resolvePosterImageUrl(code, ogImage, document)
        val absolutePosterUrl = normalizedImageUrl?.let(::toAbsoluteUrl)

        logMetadataTrace("JVR parsed HTML for $code -> title='$parsedTitle', poster=${absolutePosterUrl ?: "<none>"}, releaseDate=${releaseDate ?: "<none>"}, studio=${studio ?: "<none>"}, casts=${casts.size}, genres=${genres.size}")

        return JvrMovieMetadata(
            code = code,
            title = parsedTitle,
            posterUrl = absolutePosterUrl,
            releaseDate = releaseDate,
            studio = studio,
            genres = genres,
            casts = casts,
        )
    }

    internal fun parseAvWikiMetadataHtml(
        code: String,
        html: String,
        baseUrl: String = "https://av-wiki.net/${code.lowercase()}/",
    ): JvrMovieMetadata? {
        val document = Jsoup.parse(html, baseUrl)
        val thumbnailImage = document.selectFirst("div.article-thumbnail img[src]")
        val rawSrc = thumbnailImage?.attr("src")?.trim().orEmpty()
        val rawAlt = normalizeText(thumbnailImage?.attr("alt"))
        val articleTitle = normalizeText(
            document.selectFirst("h1.entry-title, h1.article-title, article h1")?.text()
        )
        val detailValues = extractAvWikiDetailValues(document)
        val studio = normalizeText(detailValues["メーカー"]?.text())
        val releaseDate = parseReleaseDate(detailValues["配信開始日"]?.text())
        val casts = extractAvWikiCasts(detailValues["AV女優名"])

        if (
            rawSrc.isBlank() &&
            rawAlt.isNullOrBlank() &&
            articleTitle.isNullOrBlank() &&
            studio.isNullOrBlank() &&
            releaseDate == null &&
            casts.isEmpty()
        ) {
            logMetadataTrace("AV-Wiki HTML parse had no useful metadata for code=$code")
            return null
        }

        val title = selectPreferredTitle(
            code,
            articleTitle,
            rawAlt,
            normalizeText(document.title()),
        )
        val posterUrl = rawSrc.takeIf { it.isNotBlank() }?.let(::toAbsoluteUrl)

        logMetadataTrace("AV-Wiki parsed HTML for $code -> title='$title', poster=${posterUrl ?: "<none>"}, releaseDate=${releaseDate ?: "<none>"}, studio=${studio ?: "<none>"}, casts=${casts.size}")

        return JvrMovieMetadata(
            code = code,
            title = title,
            posterUrl = posterUrl,
            releaseDate = releaseDate,
            studio = studio,
            casts = casts,
        )
    }

    private fun extractMetaContent(document: Document, propertyName: String): String? {
        return document.select("meta")
            .firstOrNull { it.attr("property").equals(propertyName, ignoreCase = true) }
            ?.attr("content")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::decodeHtml)
    }

    private fun resolvePosterImageUrl(code: String, ogImage: String?, document: Document): String? {
        if (ogImage.isNullOrBlank()) {
            val fallback = extractCoverImageFromImgTag(code, document)
            logMetadataTrace("og:image missing for $code. <img> fallback=${fallback ?: "<none>"}")
            return fallback
        }

        if (ogImage.startsWith("https://awsimgsrc.dmm.co.jp", ignoreCase = true)) {
            val staticPoster = extractCoverImageFromImgTag(code, document)
            if (!staticPoster.isNullOrBlank()) {
                logMetadataTrace("og:image is awsimgsrc for $code. Using static fallback=$staticPoster")
                return staticPoster
            }

            logMetadataTrace("og:image is awsimgsrc for $code, but static cover fallback was not found")
        }

        return ogImage
    }

    private fun extractCoverImageFromImgTag(code: String, document: Document): String? {
        val preferredAltText = "${code.uppercase()} full cover image"
        var fallbackSrc: String? = null

        for (image in document.select("img[src][alt]")) {
            val alt = normalizeText(image.attr("alt")) ?: continue
            val src = image.attr("src").trim()
            if (src.isBlank()) continue

            if (alt.equals(preferredAltText, ignoreCase = true)) {
                return src
            }

            if (fallbackSrc == null && alt.contains("full cover image", ignoreCase = true)) {
                fallbackSrc = src
            }
        }

        return fallbackSrc
    }

    private fun extractTitleFromTitleDiv(document: Document, code: String): String? {
        val titleElement = document.selectFirst("div.title") ?: return null
        val sanitizedElement = titleElement.clone().apply {
            select("span.hidden, span.code").remove()
        }

        var normalized = normalizeText(sanitizedElement.text()) ?: return null

        normalized = normalized.replace(
            Regex("^\\s*${Regex.escape(code)}\\s*[:\\-–—]?\\s*", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        normalized = stripLeadingVrTags(normalized)
        normalized = removeVrTagMarkers(normalized)
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun extractDetailsValueElements(document: Document): Map<String, Element> {
        val detailsContent = document.select("div.content")
            .firstOrNull { content ->
                content.children()
                    .firstOrNull { it.hasClass("label") }
                    ?.text()
                    ?.trim()
                    ?.equals("Details", ignoreCase = true) == true
            } ?: return emptyMap()

        val detailsSection = detailsContent.children()
            .firstOrNull { it.hasClass("section") } ?: return emptyMap()

        val values = linkedMapOf<String, Element>()
        for (line in detailsSection.children().filter { it.hasClass("line") }) {
            val label = normalizeDetailsLabel(
                line.children().firstOrNull { it.hasClass("label") }?.text()
            ) ?: continue
            val valueElement = line.children().firstOrNull { it.hasClass("value") } ?: continue
            values[label] = valueElement
        }
        return values
    }

    private fun extractAvWikiDetailValues(document: Document): Map<String, Element> {
        val definitionList = document.selectFirst("dl.dltable") ?: return emptyMap()
        val items = definitionList.children()
        val values = linkedMapOf<String, Element>()
        var currentLabel: String? = null

        for (item in items) {
            when (item.tagName().lowercase()) {
                "dt" -> currentLabel = normalizeText(item.text())
                "dd" -> {
                    val label = currentLabel ?: continue
                    values[label] = item
                    currentLabel = null
                }
            }
        }

        return values
    }

    private fun normalizeDetailsLabel(raw: String?): String? {
        return normalizeText(raw)
            ?.substringBefore(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun parseReleaseDate(raw: String?): LocalDate? {
        val normalized = normalizeText(raw) ?: return null
        return try {
            LocalDate.parse(normalized)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun extractGenres(valueElement: Element?): List<String> {
        if (valueElement == null) return emptyList()

        val linkedGenres = linkedSetOf<String>()
        val elements = valueElement.select("a")
        if (elements.isNotEmpty()) {
            elements.mapNotNullTo(linkedGenres) { normalizeText(it.text()) }
        } else {
            normalizeText(valueElement.text())?.let(linkedGenres::add)
        }
        return linkedGenres.toList()
    }

    private fun extractAvWikiCasts(valueElement: Element?): List<JvrCastMetadata> {
        if (valueElement == null) return emptyList()

        val linkedNames = linkedSetOf<String>()
        val anchors = valueElement.select("a")
        if (anchors.isNotEmpty()) {
            anchors.mapNotNullTo(linkedNames) { normalizeText(it.text()) }
        } else {
            normalizeText(valueElement.text())
                ?.split(Regex("\\s*[、,/]\\s*"))
                ?.mapNotNull(::normalizeText)
                ?.forEach(linkedNames::add)
        }

        return normalizeCasts(
            linkedNames.map { name ->
                JvrCastMetadata(
                    performerId = buildCanonicalPerformerId(
                        englishName = name,
                        japaneseName = name,
                    ),
                    englishName = name,
                    japaneseName = name,
                )
            }
        )
    }

    private fun extractCasts(valueElement: Element?): List<JvrCastMetadata> {
        if (valueElement == null) return emptyList()

        val castElements = valueElement.children()
            .filter { child -> child.tagName().equals("a", ignoreCase = true) }
            .ifEmpty { valueElement.select("a") }

        return normalizeCasts(castElements.mapNotNull(::parseCastMetadata))
    }

    private fun parseCastMetadata(castElement: Element): JvrCastMetadata? {
        val imageUrl = castElement.selectFirst("img[src]")
            ?.attr("src")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::toAbsoluteUrl)

        val textContainer = castElement.children()
            .firstOrNull { it.tagName().equals("div", ignoreCase = true) }

        val nameCandidates = if (textContainer != null && textContainer.children().isNotEmpty()) {
            textContainer.children().mapNotNull { normalizeText(it.text()) }
        } else {
            listOfNotNull(
                normalizeText(castElement.ownText()),
                normalizeText(castElement.text()),
            ).distinct()
        }

        val englishName = nameCandidates.firstOrNull(::containsEnglishLetters)
            ?: nameCandidates.firstOrNull()
        val japaneseName = nameCandidates.firstOrNull(::containsJapaneseScript)
            ?.takeUnless { it.equals(englishName, ignoreCase = true) }

        if (englishName.isNullOrBlank() && japaneseName.isNullOrBlank()) {
            return null
        }

        val performerId = buildCanonicalPerformerId(
            englishName = englishName,
            japaneseName = japaneseName,
        )

        return JvrCastMetadata(
            performerId = performerId,
            englishName = englishName ?: japaneseName.orEmpty(),
            japaneseName = japaneseName,
            profileImageUrl = imageUrl,
            remoteProfileImageUrl = imageUrl,
        )
    }

    private fun cleanDescriptionTitle(description: String?): String? {
        if (description.isNullOrBlank()) return null
        val normalized = stripLeadingVrTags(description)
            .replace(Regex("\\s+"), " ")
            .trim()
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun cleanHtmlTitle(title: String?, code: String): String? {
        if (title.isNullOrBlank()) return null

        var normalized = title.replace(Regex("\\s+"), " ").trim()
        normalized = normalized.replace(
            Regex(
                "^\\s*${Regex.escape(code)}\\s*-\\s*Online Streaming And Download\\s*-\\s*",
                RegexOption.IGNORE_CASE
            ),
            ""
        ).trim()

        val vrTagPattern = VR_BRACKET_TAG_PATTERN
        val marker = Regex(
            "$vrTagPattern(?:\\s*$vrTagPattern)*|\\b(?:8KVR|VR8K)\\b",
            RegexOption.IGNORE_CASE
        ).find(normalized)
        if (marker != null) {
            normalized = normalized.substring(marker.range.last + 1).trim()
        }

        normalized = stripLeadingVrTags(normalized)
        normalized = removeVrTagMarkers(normalized)
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun stripLeadingVrTags(text: String): String {
        val vrTagPattern = VR_BRACKET_TAG_PATTERN
        return text.replace(
            Regex(
                "^\\s*(?:$vrTagPattern|(?:8KVR|VR8K))(?:\\s*(?:$vrTagPattern|(?:8KVR|VR8K)))*\\s*",
                RegexOption.IGNORE_CASE
            ),
            ""
        ).trim()
    }

    private fun removeVrTagMarkers(text: String): String {
        val vrTagPattern = VR_BRACKET_TAG_PATTERN
        return text
            .replace(Regex(vrTagPattern, RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\b(?:8KVR|VR8K)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun sanitizeTitleCandidate(candidate: String?, code: String): String? {
        if (candidate.isNullOrBlank()) return null

        var normalized = decodeHtml(candidate)
            .replace(Regex("\\s+"), " ")
            .trim()

        normalized = normalized.replace(
            Regex("^\\s*${Regex.escape(code)}\\s*[:\\-–—]?\\s*", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        normalized = stripLeadingVrTags(normalized)
        normalized = removeVrTagMarkers(normalized)

        if (normalized.isBlank()) return null
        if (normalized.equals(code, ignoreCase = true)) return null
        if (placeholderTitlePatterns.any { it.matches(normalized) }) return null

        return normalized
    }

    private fun selectPreferredTitle(code: String, vararg candidates: String?): String {
        val normalizedCandidates = candidates
            .mapNotNull { sanitizeTitleCandidate(it, code) }
            .distinct()

        normalizedCandidates.firstOrNull(::containsJapaneseScript)?.let { return it }
        normalizedCandidates.firstOrNull(::isPreferredEnglishTitle)?.let { return it }
        normalizedCandidates.firstOrNull { !isLikelyChinese(it) }?.let { return it }

        return code
    }

    private fun normalizeGenres(genres: List<String>): List<String> {
        return genres
            .mapNotNull(::normalizeText)
            .distinct()
    }

    private suspend fun resolveCanonicalCastMetadata(
        metadataDao: VirtualGroupMetadataDao,
        source: String,
        metadata: JvrMovieMetadata,
    ): JvrMovieMetadata {
        if (metadata.casts.isEmpty()) return metadata

        val resolvedCasts = metadata.casts.mapNotNull { cast ->
            resolveCanonicalCast(
                metadataDao = metadataDao,
                source = source,
                cast = cast,
            )
        }.distinctBy { it.performerId }

        return metadata.copy(casts = resolvedCasts)
    }

    private suspend fun resolveCanonicalCast(
        metadataDao: VirtualGroupMetadataDao,
        source: String,
        cast: JvrCastMetadata,
    ): JvrCastMetadata? {
        val englishName = normalizeText(cast.englishName)
        val japaneseName = normalizeText(cast.japaneseName)
            ?.takeUnless {
                it.equals(englishName, ignoreCase = true) &&
                    !englishName.isNullOrBlank() &&
                    containsEnglishLetters(englishName)
            }
        val candidates = buildPerformerAliasCandidates(
            legacyPerformerId = cast.performerId,
            englishName = englishName,
            japaneseName = japaneseName,
        )

        val matchedCanonicalIds = candidates
            .takeIf { it.isNotEmpty() }
            ?.let { metadataDao.getAliasesByKeys(it.map(PerformerAliasCandidate::aliasKey)) }
            .orEmpty()
            .map { alias -> resolveCanonicalPerformerId(metadataDao, alias.canonicalPerformerId) }
            .filter { it.isNotBlank() }
            .distinct()

        val canonicalPerformerId = when {
            matchedCanonicalIds.isEmpty() -> {
                buildCanonicalPerformerId(
                    englishName = englishName,
                    japaneseName = japaneseName,
                    fallbackId = cast.performerId,
                )
            }

            matchedCanonicalIds.size == 1 -> matchedCanonicalIds.first()

            else -> {
                val targetPerformerId = choosePreferredPerformerId(
                    metadataDao = metadataDao,
                    performerIds = matchedCanonicalIds,
                    englishName = englishName,
                    japaneseName = japaneseName,
                )
                matchedCanonicalIds
                    .filter { it != targetPerformerId }
                    .forEach { sourcePerformerId ->
                        metadataDao.mergePerformerInto(
                            sourcePerformerId = sourcePerformerId,
                            targetPerformerId = targetPerformerId,
                            isManual = false,
                        )
                    }
                targetPerformerId
            }
        }

        val existingCanonicalPerformer = metadataDao.getPerformerById(canonicalPerformerId)
        val mergedCanonicalPerformer = mergeCanonicalPerformer(
            canonicalPerformerId = canonicalPerformerId,
            existing = existingCanonicalPerformer,
            cast = cast.copy(
                englishName = englishName ?: cast.englishName,
                japaneseName = japaneseName,
            ),
        )

        metadataDao.upsertPerformers(listOf(mergedCanonicalPerformer))

        val aliases = candidates.map { candidate ->
            JvrPerformerAlias(
                aliasKey = candidate.aliasKey,
                canonicalPerformerId = canonicalPerformerId,
                aliasKind = candidate.aliasKind,
                aliasValue = candidate.aliasValue,
                source = source,
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (aliases.isNotEmpty()) {
            metadataDao.upsertAliases(aliases)
        }

        return JvrCastMetadata(
            performerId = canonicalPerformerId,
            englishName = mergedCanonicalPerformer.englishName,
            japaneseName = mergedCanonicalPerformer.japaneseName,
            profileImageUrl = resolveStoredPerformerImageUrl(mergedCanonicalPerformer),
            remoteProfileImageUrl = mergedCanonicalPerformer.remoteProfileImageUrl,
        )
    }

    private suspend fun resolveCanonicalPerformerId(
        metadataDao: VirtualGroupMetadataDao,
        performerId: String,
    ): String {
        var resolved = performerId.trim()
        val visited = linkedSetOf<String>()

        while (resolved.isNotBlank() && visited.add(resolved)) {
            val next = metadataDao.getMergeRuleBySource(resolved)?.targetPerformerId
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != resolved }
                ?: break
            resolved = next
        }

        return resolved
    }

    private suspend fun choosePreferredPerformerId(
        metadataDao: VirtualGroupMetadataDao,
        performerIds: List<String>,
        englishName: String?,
        japaneseName: String?,
    ): String {
        val performersById = metadataDao.getPerformersByIds(performerIds.distinct())
            .associateBy { it.performerId }

        return performerIds.distinct()
            .maxWithOrNull(
                compareBy<String> { performerId ->
                    scorePerformerCandidate(
                        performer = performersById[performerId],
                        englishName = englishName,
                        japaneseName = japaneseName,
                    )
                }.thenByDescending { it.length }
            )
            ?: performerIds.first()
    }

    private fun scorePerformerCandidate(
        performer: JvrPerformer?,
        englishName: String?,
        japaneseName: String?,
    ): Int {
        if (performer == null) return 0

        var score = 0
        if (!performer.japaneseName.isNullOrBlank()) score += 6
        if (performer.englishName.isNotBlank()) score += 5
        if (!performer.localProfileImageUrl.isNullOrBlank()) score += 4
        if (!performer.remoteProfileImageUrl.isNullOrBlank()) score += 3
        if (
            !japaneseName.isNullOrBlank() &&
            performer.japaneseName.equals(japaneseName, ignoreCase = true)
        ) {
            score += 12
        }
        if (
            !englishName.isNullOrBlank() &&
            performer.englishName.equals(englishName, ignoreCase = true)
        ) {
            score += 10
        }
        return score
    }

    private fun mergeCanonicalPerformer(
        canonicalPerformerId: String,
        existing: JvrPerformer?,
        cast: JvrCastMetadata,
    ): JvrPerformer {
        val englishName = normalizeText(cast.englishName)
        val japaneseName = normalizeText(cast.japaneseName)
        val remoteProfileImageUrl = cast.remoteProfileImageUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless(::isLocalImageUrl)
            ?.let(::toAbsoluteUrl)
            ?: cast.profileImageUrl
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.takeUnless(::isLocalImageUrl)
                ?.let(::toAbsoluteUrl)
        val localProfileImageUrl = cast.profileImageUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::validateLocalImageUrl)

        val incoming = JvrPerformer(
            performerId = canonicalPerformerId,
            englishName = englishName ?: japaneseName.orEmpty(),
            japaneseName = japaneseName,
            remoteProfileImageUrl = remoteProfileImageUrl,
            localProfileImageUrl = localProfileImageUrl,
            updatedAt = System.currentTimeMillis(),
        )

        if (existing == null) return incoming

        val incomingHasDistinctEnglishName =
            incoming.englishName.isNotBlank() &&
                !incoming.englishName.equals(incoming.japaneseName, ignoreCase = true)

        return incoming.copy(
            englishName = when {
                incomingHasDistinctEnglishName -> incoming.englishName
                existing.englishName.isNotBlank() -> existing.englishName
                else -> incoming.englishName
            },
            japaneseName = incoming.japaneseName ?: existing.japaneseName,
            remoteProfileImageUrl = incoming.remoteProfileImageUrl
                ?: existing.remoteProfileImageUrl,
            localProfileImageUrl = incoming.localProfileImageUrl ?: existing.localProfileImageUrl,
            updatedAt = maxOf(existing.updatedAt, incoming.updatedAt),
        )
    }

    private fun buildPerformerAliasCandidates(
        legacyPerformerId: String?,
        englishName: String?,
        japaneseName: String?,
    ): List<PerformerAliasCandidate> {
        val candidates = linkedMapOf<String, PerformerAliasCandidate>()

        japaneseName
            ?.let(::normalizePerformerAliasValue)
            ?.takeIf { it.isNotBlank() }
            ?.let { normalized ->
                candidates["jp:$normalized"] = PerformerAliasCandidate(
                    aliasKey = "jp:$normalized",
                    aliasKind = ALIAS_KIND_JAPANESE,
                    aliasValue = normalized,
                )
            }

        englishName
            ?.let(::normalizePerformerAliasValue)
            ?.takeIf { it.isNotBlank() }
            ?.let { normalized ->
                candidates["en:$normalized"] = PerformerAliasCandidate(
                    aliasKey = "en:$normalized",
                    aliasKind = ALIAS_KIND_ENGLISH,
                    aliasValue = normalized,
                )
            }

        legacyPerformerId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { normalized ->
                candidates["legacy:$normalized"] = PerformerAliasCandidate(
                    aliasKey = "legacy:$normalized",
                    aliasKind = ALIAS_KIND_LEGACY,
                    aliasValue = normalized,
                )
            }

        return candidates.values.toList()
    }

    private fun normalizePerformerAliasValue(value: String): String {
        return value
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeCasts(casts: List<JvrCastMetadata>): List<JvrCastMetadata> {
        return casts.mapNotNull { cast ->
            val englishName = normalizeText(cast.englishName)
            val japaneseName = normalizeText(cast.japaneseName)
                ?.takeUnless {
                    it.equals(englishName, ignoreCase = true) &&
                            !englishName.isNullOrBlank() &&
                            containsEnglishLetters(englishName)
                }
            val performerId = buildCanonicalPerformerId(
                englishName = englishName,
                japaneseName = japaneseName,
                fallbackId = cast.performerId,
            )
            val remoteProfileImageUrl = cast.remoteProfileImageUrl
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.takeUnless(::isLocalImageUrl)
                ?.let(::toAbsoluteUrl)
                ?: cast.profileImageUrl
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.takeUnless(::isLocalImageUrl)
                    ?.let(::toAbsoluteUrl)
            val localProfileImageUrl = cast.profileImageUrl
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(::validateLocalImageUrl)

            val resolvedEnglishName = englishName ?: japaneseName
            if (resolvedEnglishName.isNullOrBlank()) {
                null
            } else {
                JvrCastMetadata(
                    performerId = performerId,
                    englishName = resolvedEnglishName,
                    japaneseName = japaneseName,
                    profileImageUrl = localProfileImageUrl ?: remoteProfileImageUrl,
                    remoteProfileImageUrl = remoteProfileImageUrl,
                )
            }
        }.distinctBy { it.performerId }
    }

    private fun buildCanonicalPerformerId(
        englishName: String?,
        japaneseName: String?,
        fallbackId: String? = null,
    ): String {
        val canonicalName = normalizeText(japaneseName)
            ?: normalizeText(englishName)
            ?: fallbackId?.trim()?.takeIf { it.isNotBlank() }
            ?: "unknown-performer"
        val slug = canonicalName.lowercase()
            .replace(Regex("[^a-z0-9\\u3040-\\u30ff\\u31f0-\\u31ff\\u4e00-\\u9fff]+"), "_")
            .trim('_')
            .ifBlank { canonicalName.hashCode().toUInt().toString(16) }
        return "performer:$slug"
    }

    private fun resolveStoredPerformerImageUrl(performer: JvrPerformer): String? {
        return performer.localProfileImageUrl
            ?.let(::validateLocalImageUrl)
            ?: performer.remoteProfileImageUrl
    }

    private fun normalizeText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return decodeHtml(raw)
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun containsJapaneseScript(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return Regex("[\\u3040-\\u30FF\\u31F0-\\u31FF\\uFF66-\\uFF9D]").containsMatchIn(text)
    }

    private fun isPreferredEnglishTitle(text: String): Boolean {
        return containsEnglishLetters(text) && !isLikelyChinese(text)
    }

    private fun containsEnglishLetters(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return Regex("[A-Za-z]").containsMatchIn(text)
    }

    private fun isLikelyChinese(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val hasHan = Regex("[\\u4E00-\\u9FFF]").containsMatchIn(text)
        if (!hasHan) return false
        return !containsJapaneseScript(text)
    }

    private fun toAbsoluteUrl(raw: String): String {
        val value = raw.trim()
        return when {
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("http://", ignoreCase = true) -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "$BASE_URL$value"
            else -> "$BASE_URL/$value"
        }
    }

    private fun decodeHtml(raw: String): String {
        return Jsoup.parse(raw).text()
    }
}
