package blackark.app.vr.utils

import android.content.Context
import android.text.Html
import android.util.Log
import blackark.app.vr.data.database.AppDatabase
import blackark.app.vr.data.database.entity.VirtualGroupMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class JvrMovieMetadata(
    val code: String,
    val title: String,
    val posterUrl: String?,
)

object JvrLibraryMetadataProvider {

    private const val TAG = "JvrLibraryMetadata"
    private const val BASE_URL = "https://jvrlibrary.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Android; XRStream) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    private const val SOURCE_JVR = "jvr"
    private const val SOURCE_AV_WIKI = "avwiki"
    private const val SOURCE_NONE = "none"
    private val metadataCache = ConcurrentHashMap<String, JvrMovieMetadata>()
    private val missCache = ConcurrentHashMap.newKeySet<String>()

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


    fun peekCached(rawCode: String): JvrMovieMetadata? {
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) return null
        return metadataCache[buildCacheKey(code, SOURCE_JVR)]
    }

    @Suppress("UNUSED_PARAMETER")
    fun peekCached(context: Context, rawCode: String, folderPath: String = ""): JvrMovieMetadata? {
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) return null

        val source = metadataSourceForPath(folderPath)
        val cacheKey = buildCacheKey(code, source)
        return metadataCache[cacheKey]?.let { cached ->
            val validated = validateLocalPosterPath(cached)
            val normalized = validated.copy(
                title = selectPreferredTitle(validated.code, validated.title)
            )
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

        Log.d(
            TAG,
            "Lookup request code=$code path='$folderPath' source=$source allowJvr=$allowJvrLookup"
        )

        metadataCache[cacheKey]?.let {
            val validated = validateLocalPosterPath(it)
            val normalized = validated.copy(
                title = selectPreferredTitle(validated.code, validated.title)
            )
            metadataCache[cacheKey] = normalized
            if (normalized.title != it.title || normalized.posterUrl != it.posterUrl) {
                savePersistedMetadata(appContext, cacheKey, source, normalized)
            }
            Log.d(
                TAG,
                "Memory cache hit for $cacheKey (poster=${normalized.posterUrl ?: "<none>"})"
            )
            return normalized
        }

        loadPersistedMetadata(appContext, cacheKey)?.let { persisted ->
            if (persisted.isMiss) {
                missCache += cacheKey
                Log.d(TAG, "DB miss-cache hit for $cacheKey; skipping remote lookup")
                return null
            }

            val persistedCode = persisted.code.ifBlank { code }
            val normalizedTitle = selectPreferredTitle(
                persistedCode,
                persisted.title,
            )

            val cachedMetadata = validateLocalPosterPath(
                JvrMovieMetadata(
                    code = persistedCode,
                    title = normalizedTitle,
                    posterUrl = persisted.posterUrl,
                )
            )

            metadataCache[cacheKey] = cachedMetadata
            missCache.remove(cacheKey)

            // Persist normalized path if local file was removed or title normalized.
            if (cachedMetadata.posterUrl != persisted.posterUrl || cachedMetadata.title != persisted.title) {
                savePersistedMetadata(appContext, cacheKey, source, cachedMetadata)
            }

            Log.d(
                TAG,
                "DB cache hit for $cacheKey (poster=${cachedMetadata.posterUrl ?: "<none>"})"
            )
            return cachedMetadata
        }


        loadPersistedMetadataByCode(appContext, code)?.let { persistedByCode ->
            val persistedCode = persistedByCode.code.ifBlank { code }
            val normalizedTitle = selectPreferredTitle(
                persistedCode,
                persistedByCode.title,
            )

            val cachedMetadata = validateLocalPosterPath(
                JvrMovieMetadata(
                    code = persistedCode,
                    title = normalizedTitle,
                    posterUrl = persistedByCode.posterUrl,
                )
            )

            metadataCache[cacheKey] = cachedMetadata
            metadataCache[buildCacheKey(persistedCode, persistedByCode.source)] = cachedMetadata
            missCache.remove(cacheKey)

            if (cachedMetadata.posterUrl != persistedByCode.posterUrl || cachedMetadata.title != persistedByCode.title) {
                savePersistedMetadata(
                    appContext,
                    buildCacheKey(persistedCode, persistedByCode.source),
                    persistedByCode.source,
                    cachedMetadata
                )
            }

            Log.d(
                TAG,
                "DB code-level cache hit for code=$code source=${persistedByCode.source} (poster=${cachedMetadata.posterUrl ?: "<none>"})"
            )
            return cachedMetadata
        }
        if (cacheKey in missCache) {
            Log.d(TAG, "Memory miss-cache hit for $cacheKey; skipping remote lookup")
            return null
        }

        Log.d(
            TAG,
            "Cache miss for $cacheKey; fetching metadata (source=$source, allowJvr=$allowJvrLookup, path='$folderPath')"
        )

        val remoteMetadata = withContext(Dispatchers.IO) {
            when (source) {
                SOURCE_AV_WIKI -> {
                    fetchByCodeFromAvWiki(code) ?: if (allowJvrLookup) fetchByCodeFromJvrLibrary(
                        code
                    ) else null
                }

                SOURCE_JVR -> if (allowJvrLookup) fetchByCodeFromJvrLibrary(code) else null
                else -> null
            }
        }

        if (remoteMetadata == null) {
            missCache += cacheKey
            savePersistedMiss(appContext, cacheKey, code, source)
            if (!allowJvrLookup && source != SOURCE_AV_WIKI) {
                Log.d(
                    TAG,
                    "No metadata resolved for $cacheKey because path is not eligible for JVR lookup; stored DB miss"
                )
            } else {
                Log.d(TAG, "No metadata resolved for $cacheKey; stored DB miss")
            }
            return null
        }

        val localizedMetadata = withContext(Dispatchers.IO) {
            localizePoster(appContext, cacheKey, remoteMetadata)
        }

        metadataCache[cacheKey] = localizedMetadata
        savePersistedMetadata(appContext, cacheKey, source, localizedMetadata)
        missCache.remove(cacheKey)

        Log.d(
            TAG,
            "Resolved metadata for $cacheKey title='${localizedMetadata.title}' poster=${localizedMetadata.posterUrl ?: "<none>"}"
        )

        return localizedMetadata
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

        Log.d(
            TAG,
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
            if (makerSegment.equals(
                    "maker",
                    ignoreCase = true
                ) && yearSegment.matches(Regex("(?:19|20)\\d{2}"))
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

    private fun fetchByCodeFromAvWiki(code: String): JvrMovieMetadata? {
        val avWikiUrl = "https://av-wiki.net/${code.lowercase()}/"
        Log.d(TAG, "Starting AV-Wiki metadata lookup for $code url=$avWikiUrl")
        val html = fetchPageHtml(avWikiUrl) ?: return null

        val thumbnailDivRegex = Regex(
            "<div\\b[^>]*class\\s*=\\s*['\"][^'\"]*\\barticle-thumbnail\\b[^'\"]*['\"][^>]*>(.*?)</div>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val thumbnailHtml = thumbnailDivRegex.find(html)?.groupValues?.getOrNull(1) ?: return null
        val imgAttributes = extractFirstImageAttributes(thumbnailHtml) ?: return null

        val rawSrc = imgAttributes["src"]?.trim().orEmpty()
        if (rawSrc.isBlank()) {
            return null
        }

        val rawAlt = imgAttributes["alt"]?.trim().orEmpty()
        val title = selectPreferredTitle(code, rawAlt)
        val posterUrl = toAbsoluteUrl(rawSrc)

        Log.d(TAG, "AV-Wiki parsed for $code -> title='$title', poster=$posterUrl")

        return JvrMovieMetadata(
            code = code,
            title = title,
            posterUrl = posterUrl,
        )
    }

    private fun fetchByCodeFromJvrLibrary(code: String): JvrMovieMetadata? {
        val encodedCode = URLEncoder.encode(code, Charsets.UTF_8.name())
        val zhUrl = "$BASE_URL/zh/jvr?id=$encodedCode"
        val enUrl = "$BASE_URL/jvr?id=$encodedCode"

        Log.d(TAG, "Starting JVR metadata lookup for $code")
        val zhMetadata = fetchPageHtml(zhUrl)?.let { parseJvrMetadataHtml(code, it) }
        Log.d(
            TAG,
            "JVR ZH parse for $code -> title='${zhMetadata?.title ?: "<none>"}', poster=${zhMetadata?.posterUrl ?: "<none>"}"
        )

        val shouldTryEnglishPage =
            zhMetadata == null ||
                    zhMetadata.title.equals(code, ignoreCase = true) ||
                    isLikelyChinese(zhMetadata.title)

        val enMetadata = if (shouldTryEnglishPage) {
            Log.d(TAG, "Trying JVR EN fallback page for $code")
            fetchPageHtml(enUrl)?.let { parseJvrMetadataHtml(code, it) }
        } else {
            null
        }

        if (shouldTryEnglishPage) {
            Log.d(
                TAG,
                "JVR EN parse for $code -> title='${enMetadata?.title ?: "<none>"}', poster=${enMetadata?.posterUrl ?: "<none>"}"
            )
        }

        if (zhMetadata == null && enMetadata == null) {
            Log.d(TAG, "JVR lookup finished for $code with no metadata")
            return null
        }

        val selectedTitle = selectPreferredTitle(
            code,
            zhMetadata?.title,
            enMetadata?.title,
        )

        val posterUrl = zhMetadata?.posterUrl ?: enMetadata?.posterUrl

        return JvrMovieMetadata(
            code = code,
            title = selectedTitle,
            posterUrl = posterUrl,
        )
    }

    private fun extractFirstImageAttributes(html: String): Map<String, String>? {
        val imgTagRegex = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
        val attributeRegex =
            Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*['\"]([^'\"]*)['\"]")

        val imgTag = imgTagRegex.find(html)?.value ?: return null
        val attrs = mutableMapOf<String, String>()

        for (attribute in attributeRegex.findAll(imgTag)) {
            attrs[attribute.groupValues[1].lowercase()] = attribute.groupValues[2]
        }

        return attrs
    }

    private fun localizePoster(
        context: Context,
        cacheKey: String,
        metadata: JvrMovieMetadata
    ): JvrMovieMetadata {
        val remotePoster = metadata.posterUrl?.trim().orEmpty()
        if (remotePoster.isBlank()) {
            return metadata.copy(posterUrl = null)
        }

        val absolutePoster = toAbsoluteUrl(remotePoster)
        val localPoster = downloadPosterToLocal(context, cacheKey, metadata.code, absolutePoster)

        if (localPoster != null) {
            Log.d(TAG, "Poster localized for $cacheKey: $localPoster")
            return metadata.copy(posterUrl = localPoster)
        }

        // Prevent repeated remote image requests in UI; fall back to generated thumbnail instead.
        Log.w(TAG, "Poster localization failed for $cacheKey. Falling back to generated thumbnail.")
        return metadata.copy(posterUrl = null)
    }

    private fun downloadPosterToLocal(
        context: Context,
        cacheKey: String,
        code: String,
        posterUrl: String,
    ): String? {
        return try {
            val postersDir = File(context.filesDir, "group_posters")
            if (!postersDir.exists()) {
                postersDir.mkdirs()
            }

            val extension = extractImageExtension(posterUrl)
            val safeName = cacheKey.lowercase().replace(':', '_').replace('/', '_')
            val targetFile = File(postersDir, "$safeName.$extension")

            if (targetFile.exists() && targetFile.length() > 0L) {
                return targetFile.toURI().toString()
            }

            val connection = (URL(posterUrl).openConnection() as? HttpURLConnection) ?: return null
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
                        "Poster download failed for $cacheKey ($code) with HTTP $statusCode: $posterUrl"
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
            Log.w(TAG, "Poster download exception for $cacheKey ($code): ${e.message}")
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
    ): VirtualGroupMetadata? {
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
    ): VirtualGroupMetadata? {
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
                AppDatabase.getDatabase(context)
                    .virtualGroupMetadataDao()
                    .upsert(
                        VirtualGroupMetadata(
                            cacheKey = cacheKey,
                            code = metadata.code,
                            source = source,
                            title = metadata.title,
                            posterUrl = metadata.posterUrl,
                            isMiss = false,
                            updatedAt = System.currentTimeMillis(),
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
                    .upsert(
                        VirtualGroupMetadata(
                            cacheKey = cacheKey,
                            code = code,
                            source = source,
                            title = null,
                            posterUrl = null,
                            isMiss = true,
                            updatedAt = System.currentTimeMillis(),
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

    private fun validateLocalPosterPath(metadata: JvrMovieMetadata): JvrMovieMetadata {
        val poster = metadata.posterUrl?.trim().orEmpty()
        if (poster.isBlank()) return metadata.copy(posterUrl = null)

        val localFile = when {
            poster.startsWith("file:", ignoreCase = true) -> {
                runCatching { File(URI(poster)) }.getOrNull()
            }

            poster.startsWith("/") -> File(poster)
            else -> null
        }

        if (localFile != null && (!localFile.exists() || localFile.length() <= 0L)) {
            return metadata.copy(posterUrl = null)
        }

        return metadata
    }

    private fun fetchPageHtml(urlString: String): String? {
        val url = runCatching { URL(urlString) }.getOrNull() ?: return null
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

    private fun parseJvrMetadataHtml(code: String, html: String): JvrMovieMetadata? {
        val ogImage = extractMetaContent(html, "og:image")
        val ogDescription = extractMetaContent(html, "og:description")
        val htmlTitle = extractTitle(html)

        val titleFromHtml = sanitizeTitleCandidate(cleanHtmlTitle(htmlTitle, code), code)
        val titleFromDescription =
            sanitizeTitleCandidate(cleanDescriptionTitle(ogDescription), code)
        val titleFromDiv = sanitizeTitleCandidate(extractTitleFromTitleDiv(html, code), code)

        if (
            ogImage.isNullOrBlank() &&
            ogDescription.isNullOrBlank() &&
            htmlTitle.isNullOrBlank() &&
            titleFromDiv.isNullOrBlank()
        ) {
            Log.d(TAG, "JVR HTML parse had no useful metadata for code=$code")
            return null
        }

        val parsedTitle = selectPreferredTitle(
            code,
            titleFromHtml,
            titleFromDescription,
            titleFromDiv,
        )

        val normalizedImageUrl = resolvePosterImageUrl(code, ogImage, html)
        val absolutePosterUrl = normalizedImageUrl?.let(::toAbsoluteUrl)

        Log.d(
            TAG,
            "JVR parsed HTML for $code -> title='$parsedTitle', poster=${absolutePosterUrl ?: "<none>"}"
        )

        return JvrMovieMetadata(
            code = code,
            title = parsedTitle,
            posterUrl = absolutePosterUrl,
        )
    }

    private fun extractMetaContent(html: String, propertyName: String): String? {
        val metaTagRegex = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        val attributeRegex =
            Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*['\"]([^'\"]*)['\"]")

        for (metaMatch in metaTagRegex.findAll(html)) {
            val attrs = mutableMapOf<String, String>()
            for (attribute in attributeRegex.findAll(metaMatch.value)) {
                attrs[attribute.groupValues[1].lowercase()] = attribute.groupValues[2]
            }

            val property = attrs["property"] ?: continue
            if (!property.equals(propertyName, ignoreCase = true)) continue

            return attrs["content"]?.trim()?.takeIf { it.isNotEmpty() }?.let(::decodeHtml)
        }

        return null
    }

    private fun resolvePosterImageUrl(code: String, ogImage: String?, html: String): String? {
        if (ogImage.isNullOrBlank()) {
            val fallback = extractCoverImageFromImgTag(code, html)
            Log.d(TAG, "og:image missing for $code. <img> fallback=${fallback ?: "<none>"}")
            return fallback
        }

        if (ogImage.startsWith("https://awsimgsrc.dmm.co.jp", ignoreCase = true)) {
            val staticPoster = extractCoverImageFromImgTag(code, html)
            if (!staticPoster.isNullOrBlank()) {
                Log.d(TAG, "og:image is awsimgsrc for $code. Using static fallback=$staticPoster")
                return staticPoster
            }

            Log.d(TAG, "og:image is awsimgsrc for $code, but static cover fallback was not found")
        }

        return ogImage
    }

    private fun extractCoverImageFromImgTag(code: String, html: String): String? {
        val imgTagRegex = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
        val attributeRegex =
            Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*['\"]([^'\"]*)['\"]")

        val preferredAltText = "${code.uppercase()} full cover image"
        var fallbackSrc: String? = null

        for (imgMatch in imgTagRegex.findAll(html)) {
            val attrs = mutableMapOf<String, String>()
            for (attribute in attributeRegex.findAll(imgMatch.value)) {
                attrs[attribute.groupValues[1].lowercase()] = attribute.groupValues[2]
            }

            val alt = attrs["alt"]?.trim() ?: continue
            val src = attrs["src"]?.trim().orEmpty()
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

    private fun extractTitleFromTitleDiv(html: String, code: String): String? {
        val divRegex = Regex(
            "<div\\b[^>]*class\\s*=\\s*['\"][^'\"]*\\btitle\\b[^'\"]*['\"][^>]*>(.*?)</div>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val rawInnerHtml = divRegex.find(html)?.groupValues?.getOrNull(1) ?: return null

        val withoutHiddenSpans = rawInnerHtml
            .replace(
                Regex(
                    "<span\\b[^>]*class\\s*=\\s*['\"][^'\"]*\\bhidden\\b[^'\"]*['\"][^>]*>.*?</span>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ),
                " "
            )
            .replace(
                Regex(
                    "<span\\b[^>]*class\\s*=\\s*['\"][^'\"]*\\bcode\\b[^'\"]*['\"][^>]*>.*?</span>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ),
                " "
            )
            .replace(Regex("<[^>]+>"), " ")

        var normalized = decodeHtml(withoutHiddenSpans)
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return null

        normalized = normalized.replace(
            Regex("^\\s*${Regex.escape(code)}\\s*[:\\-–—]?\\s*", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        normalized = stripLeadingVrTags(normalized)
        normalized = removeVrTagMarkers(normalized)
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun extractTitle(html: String): String? {
        val titleRegex = Regex(
            "<title\\b[^>]*>(.*?)</title>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val raw = titleRegex.find(html)?.groupValues?.getOrNull(1) ?: return null
        return decodeHtml(raw).replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() }
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

    @Suppress("DEPRECATION")
    private fun decodeHtml(raw: String): String {
        return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
    }
}


















