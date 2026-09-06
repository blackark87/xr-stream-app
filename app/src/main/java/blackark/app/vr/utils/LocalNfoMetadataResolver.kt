package blackark.app.vr.utils

import android.text.Html
import android.util.Log
import blackark.app.vr.network.LocalFileClient
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBFileItem
import blackark.app.vr.remote.RuntimeConfigRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private const val LOCAL_NFO_SOURCE = "local_nfo"
private const val TAG = "LocalNfoMetadata"
private val localNfoImageExtensions: List<String>
    get() = RuntimeConfigRegistry.current.metadata.imageExtensions
private val absoluteArtworkScheme =
    Regex("^(?:smb|content|file|https?)://", RegexOption.IGNORE_CASE)

data class LocalNfoMetadataResult(
    val metadata: JvrMovieMetadata?,
    val posterUrl: String?,
    val nfoPath: String?,
    val readSuccessful: Boolean = true,
)

internal data class NfoArtworkReferences(
    val poster: List<String>,
    val thumb: List<String>,
    val fanart: List<String>,
)

private data class NfoTagMatch(
    val attributes: String,
    val content: String,
    val range: IntRange,
)

internal fun buildNfoCandidateNames(
    videoName: String,
    preferredBaseName: String? = null,
): List<String> {
    val stem = videoName.substringBeforeLast('.', videoName)
    val multipartBaseStem = extractVirtualGroupKey(videoName)
    return buildList {
        preferredBaseName
            ?.substringBeforeLast('.', preferredBaseName)
            ?.takeIf { it.isNotBlank() }
            ?.let { add("$it.nfo") }
        add("$stem.nfo")
        if (!multipartBaseStem.isNullOrBlank() && !multipartBaseStem.equals(stem, ignoreCase = true)) {
            add("$multipartBaseStem.nfo")
        }
        add("movie.nfo")
        add("info.nfo")
    }.distinctBy { it.lowercase(Locale.US) }
}

internal fun buildPosterCandidateNames(
    videoName: String,
    preferredBaseName: String? = null,
): List<String> {
    val stem = videoName.substringBeforeLast('.', videoName)
    val multipartBaseStem = extractVirtualGroupKey(videoName)
    val contentStems = buildList {
        preferredBaseName
            ?.substringBeforeLast('.', preferredBaseName)
            ?.takeIf { it.isNotBlank() }
            ?.let(::add)
        add(stem)
        if (!multipartBaseStem.isNullOrBlank()) add(multipartBaseStem)
    }.distinctBy { it.lowercase(Locale.US) }
    return buildList {
        for (contentStem in contentStems) {
            for (extension in localNfoImageExtensions) {
                add("$contentStem-poster.$extension")
            }
        }
        for (contentStem in contentStems) {
            for (extension in localNfoImageExtensions) {
                add("$contentStem.$extension")
            }
        }
        for (extension in localNfoImageExtensions) add("poster.$extension")
        for (extension in localNfoImageExtensions) add("fanart.$extension")
        for (extension in localNfoImageExtensions) add("cover.$extension")
        for (extension in localNfoImageExtensions) add("folder.$extension")
    }.distinctBy { it.lowercase(Locale.US) }
}

internal fun extractNfoArtworkReferences(text: String): NfoArtworkReferences {
    val posterTags = findNfoTags(text, "poster")
    val thumbTags = findNfoTags(text, "thumb")
    val fanartRanges = findNfoTags(text, "fanart")
    val actorRanges = (findNfoTags(text, "actor") + findNfoTags(text, "performer"))
        .map(NfoTagMatch::range)
    val posterThumbs = thumbTags.filter { tag ->
        Regex("(?i)\\baspect\\s*=\\s*[\\\"']?poster(?:[\\\"'\\s]|$)")
            .containsMatchIn(tag.attributes)
    }
    val fanartThumbs = thumbTags.filter { tag ->
        fanartRanges.any { fanart -> tag.range.first >= fanart.range.first && tag.range.last <= fanart.range.last }
    }
    val genericThumbs = thumbTags.filterNot { tag ->
        tag in posterThumbs ||
            tag in fanartThumbs ||
            actorRanges.any { range -> tag.range.first >= range.first && tag.range.last <= range.last }
    }
    val directFanart = fanartRanges.mapNotNull { cleanNfoArtworkReference(it.content) }

    return NfoArtworkReferences(
        poster = (posterTags + posterThumbs)
            .mapNotNull { cleanNfoArtworkReference(it.content) }
            .distinct(),
        thumb = genericThumbs
            .mapNotNull { cleanNfoArtworkReference(it.content) }
            .distinct(),
        fanart = (fanartThumbs.mapNotNull { cleanNfoArtworkReference(it.content) } + directFanart)
            .distinct(),
    )
}

private fun findNfoTags(text: String, tag: String): List<NfoTagMatch> {
    val regex = Regex(
        "<\\s*$tag\\b([^>]*)>(.*?)<\\s*/\\s*$tag\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    return regex.findAll(text).map { match ->
        NfoTagMatch(
            attributes = match.groupValues[1],
            content = match.groupValues[2],
            range = match.range,
        )
    }.toList()
}

private fun cleanNfoArtworkReference(value: String): String? {
    val cleaned = value
        .replace(Regex("(?is)<!\\[CDATA\\[(.*?)]]>")) { it.groupValues[1] }
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .trim()
    return cleaned.takeIf { it.isNotBlank() }
}

internal fun resolveSmbNfoArtworkReference(
    videoPath: String,
    reference: String,
): String? {
    val cleaned = reference.trim().replace('\\', '/')
    if (cleaned.isBlank()) return null
    if (absoluteArtworkScheme.containsMatchIn(cleaned)) return cleaned
    if (!videoPath.startsWith("smb://", ignoreCase = true)) return null
    val parent = videoPath.substringBeforeLast('/', "").trimEnd('/') + "/"
    return runCatching { URI(parent).resolve(cleaned).normalize().toString() }
        .getOrElse { parent + cleaned.trimStart('/') }
}

class LocalNfoMetadataResolver(
    private val smbClient: SMBClient?,
    private val localClient: LocalFileClient?,
    private val localRootTreeUri: String?,
) {
    private val siblingCache = mutableMapOf<String, Result<List<SMBFileItem>>>()
    private val textCache = mutableMapOf<String, Result<String>>()

    suspend fun resolve(
        video: SMBFileItem,
        fallbackCode: String,
        preferredBaseName: String? = null,
        strict: Boolean = false,
    ): LocalNfoMetadataResult =
        withContext(Dispatchers.IO) {
            val siblingKey = if (localClient != null) {
                LocalFileClient.resolveParentDirectoryUri(localRootTreeUri.orEmpty(), video.path)
            } else video.path.substringBeforeLast('/', "")
            val siblingResult = siblingCache.getOrPut(siblingKey ?: video.path) { listSiblingFiles(video, strict) }
            var readSuccessful = true
            val siblingFiles = siblingResult.getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (strict) throw error
                readSuccessful = false
                Log.d(TAG, "Failed to list siblings for ${video.path}: ${error.message}")
                emptyList()
            }
            val nfoItem = findNfoCandidate(video.name, siblingFiles, preferredBaseName)
            val nfoText = nfoItem?.let { item ->
                textCache.getOrPut(item.path) { readText(item.path) }.getOrElse { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    if (strict) throw error
                    readSuccessful = false
                    Log.w(TAG, "Failed to read NFO ${item.path}: ${error.message}")
                    null
                }
            }
            if (strict && nfoText != null) {
                val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    isXIncludeAware = false
                    isExpandEntityReferences = false
                }
                factory.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(nfoText)))
            }
            val nfoArtwork = nfoText?.let(::extractNfoArtworkReferences)
                ?: NfoArtworkReferences(emptyList(), emptyList(), emptyList())
            val sidecarCandidates = buildPosterCandidateNames(video.name, preferredBaseName)
            val fanartIndex = sidecarCandidates.indexOfFirst {
                it.startsWith("fanart.", ignoreCase = true)
            }.let { if (it < 0) sidecarCandidates.size else it }
            val coverIndex = sidecarCandidates.indexOfFirst {
                it.startsWith("cover.", ignoreCase = true) ||
                    it.startsWith("folder.", ignoreCase = true)
            }.let { if (it < 0) sidecarCandidates.size else it }
            val primarySidecars = sidecarCandidates.subList(0, fanartIndex)
            val fanartSidecars = sidecarCandidates.subList(fanartIndex, coverIndex)
            val coverSidecars = sidecarCandidates.subList(coverIndex, sidecarCandidates.size)
                .filter { it.startsWith("cover.", ignoreCase = true) }
            val folderSidecars = sidecarCandidates.subList(coverIndex, sidecarCandidates.size)
                .filter { it.startsWith("folder.", ignoreCase = true) }
            var posterUrl: String? = null
            for (source in RuntimeConfigRegistry.current.metadata.artworkPriority) {
                posterUrl = when (source) {
                    "nfoPoster" -> firstResolvableArtworkReference(
                        references = nfoArtwork.poster,
                        video = video,
                        siblings = siblingFiles,
                    )
                    "nfoThumb" -> firstResolvableArtworkReference(
                        references = nfoArtwork.thumb,
                        video = video,
                        siblings = siblingFiles,
                    )
                    "sidecarPoster" -> findPosterCandidate(primarySidecars, siblingFiles)?.path
                    "fanart" -> firstResolvableArtworkReference(
                        references = nfoArtwork.fanart,
                        video = video,
                        siblings = siblingFiles,
                    ) ?: findPosterCandidate(fanartSidecars, siblingFiles)?.path
                    "cover" -> findPosterCandidate(coverSidecars, siblingFiles)?.path
                    "folder" -> findPosterCandidate(folderSidecars, siblingFiles)?.path
                    else -> null
                }
                if (posterUrl != null) break
            }
            val metadata = nfoText?.let { text -> parseNfo(text, fallbackCode, posterUrl) }
                ?: posterUrl?.let { artworkUrl ->
                    JvrMovieMetadata(
                        code = fallbackCode.uppercase(Locale.US),
                        title = "",
                        posterUrl = artworkUrl,
                    )
                }

            LocalNfoMetadataResult(
                metadata = metadata,
                posterUrl = posterUrl,
                nfoPath = nfoItem?.path,
                readSuccessful = readSuccessful,
            )
        }

    private suspend fun listSiblingFiles(video: SMBFileItem, strict: Boolean): Result<List<SMBFileItem>> {
        val local = localClient
        val smb = smbClient
        return when {
            local != null -> {
                val rootTreeUri = localRootTreeUri.orEmpty()
                val parentUri = LocalFileClient.resolveParentDirectoryUri(rootTreeUri, video.path)
                    ?: return Result.failure(IllegalStateException("Cannot resolve NFO parent"))
                local.listFiles(parentUri)
            }

            smb != null -> {
                val parentUrl = video.path.substringBeforeLast('/', missingDelimiterValue = "")
                if (parentUrl.isBlank()) {
                    Result.success(emptyList())
                } else {
                    runCatching {
                        val directoryUrl = parentUrl.trimEnd('/') + "/"
                        smb.getSmbFile(directoryUrl).listFiles()?.mapNotNull { file ->
                            try {
                                SMBFileItem(
                                    name = file.name.removeSuffix("/"),
                                    path = file.path,
                                    isDirectory = file.isDirectory,
                                    size = if (file.isDirectory) 0L else file.length(),
                                    lastModified = file.lastModified(),
                                )
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException || strict) throw e
                                Log.d(TAG, "Skipping sibling ${file.path}: ${e.message}")
                                null
                            }
                        }.orEmpty()
                    }
                }
            }

            else -> Result.success(emptyList())
        }
    }

    private suspend fun readText(path: String): Result<String> {
        return try {
            Result.success(openInputStream(path).bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (utf8Error: Exception) {
            try {
                val fallbackText = openInputStream(path)
                    .bufferedReader(Charsets.ISO_8859_1)
                    .use { it.readText() }
                Log.d(TAG, "Read NFO with ISO-8859-1 fallback for $path: ${utf8Error.message}")
                Result.success(fallbackText)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (fallbackError: Exception) {
                Result.failure(fallbackError)
            }
        }
    }

    private suspend fun openInputStream(path: String): InputStream {
        val local = localClient
        val smb = smbClient
        return when {
            local != null -> local.getInputStream(path).getOrThrow()
            smb != null -> smb.getSmbFile(path).inputStream
            else -> error("No active file client")
        }
    }

    private fun findNfoCandidate(
        videoName: String,
        siblings: List<SMBFileItem>,
        preferredBaseName: String?,
    ): SMBFileItem? {
        return findFirstNamedCandidate(
            candidates = buildNfoCandidateNames(videoName, preferredBaseName),
            siblings = siblings,
        ) { it.endsWith(".nfo", ignoreCase = true) }
    }

    private fun findPosterCandidate(
        candidates: List<String>,
        siblings: List<SMBFileItem>,
    ): SMBFileItem? {
        return findFirstNamedCandidate(
            candidates = candidates,
            siblings = siblings,
        ) { name ->
            localNfoImageExtensions.any { name.endsWith(".$it", ignoreCase = true) }
        }
    }

    private fun findFirstNamedCandidate(
        candidates: List<String>,
        siblings: List<SMBFileItem>,
        extensionFilter: (String) -> Boolean,
    ): SMBFileItem? {
        val filesByLowerName = siblings
            .asSequence()
            .filter { !it.isDirectory && extensionFilter(it.name) }
            .associateBy { it.name.lowercase(Locale.US) }
        return candidates.asSequence()
            .map { it.lowercase(Locale.US) }
            .mapNotNull(filesByLowerName::get)
            .firstOrNull()
    }

    private suspend fun firstResolvableArtworkReference(
        references: List<String>,
        video: SMBFileItem,
        siblings: List<SMBFileItem>,
    ): String? {
        for (reference in references) {
            resolveArtworkReference(reference, video, siblings)?.let { return it }
        }
        return null
    }

    private suspend fun resolveArtworkReference(
        reference: String,
        video: SMBFileItem,
        siblings: List<SMBFileItem>,
    ): String? {
        val cleaned = reference.trim().replace('\\', '/')
        if (cleaned.isBlank()) return null
        if (absoluteArtworkScheme.containsMatchIn(cleaned)) return cleaned

        val segments = cleaned.split('/').filter { it.isNotBlank() && it != "." }
        if (segments.isEmpty()) return null
        if (segments.none { it == ".." }) {
            siblings.firstOrNull { sibling ->
                !sibling.isDirectory && sibling.name.equals(segments.last(), ignoreCase = true)
            }?.let { return it.path }
        }

        if (video.path.startsWith("smb://", ignoreCase = true)) {
            return resolveSmbNfoArtworkReference(video.path, cleaned)
        }

        val local = localClient ?: return null
        val rootTreeUri = localRootTreeUri.orEmpty()
        var currentUri = LocalFileClient.resolveParentDirectoryUri(rootTreeUri, video.path)
            ?: return null
        var currentChildren = siblings
        for ((index, segment) in segments.withIndex()) {
            if (segment == "..") {
                currentUri = LocalFileClient.resolveParentDirectoryUri(rootTreeUri, currentUri)
                    ?: return null
                currentChildren = local.listFiles(currentUri, includeHidden = true)
                    .getOrNull()
                    .orEmpty()
                continue
            }

            val child = currentChildren.firstOrNull { it.name.equals(segment, ignoreCase = true) }
                ?: return null
            if (index == segments.lastIndex) return child.path.takeIf { !child.isDirectory }
            if (!child.isDirectory) return null
            currentUri = child.path
            currentChildren = local.listFiles(currentUri, includeHidden = true)
                .getOrNull()
                .orEmpty()
        }
        return null
    }

    private fun parseNfo(text: String, fallbackCode: String, posterUrl: String?): JvrMovieMetadata? {
        val code = firstNonBlank(
            tagValue(text, "id"),
            tagValue(text, "code"),
            tagValue(text, "num"),
            tagValue(text, "productid"),
            tagValue(text, "uniqueid"),
            fallbackCode,
        )?.uppercase(Locale.US) ?: return null
        val title = firstNonBlank(
            tagValue(text, "title"),
            tagValue(text, "originaltitle"),
            keyValue(text, "title"),
        ).orEmpty()
        val studio = firstNonBlank(
            tagValue(text, "studio"),
            tagValue(text, "maker"),
            tagValue(text, "manufacturer"),
            keyValue(text, "studio"),
            keyValue(text, "maker"),
        )
        val description = firstNonBlank(
            tagValue(text, "plot"),
            tagValue(text, "outline"),
            tagValue(text, "description"),
            keyValue(text, "plot"),
            keyValue(text, "description"),
        )
        val releaseDate = firstNonBlank(
            tagValue(text, "premiered"),
            tagValue(text, "releasedate"),
            tagValue(text, "release"),
            tagValue(text, "date"),
            keyValue(text, "date"),
        )?.let(::parseDate)
        val genres = tagValues(text, "genre") + tagValues(text, "tag") + keyValues(text, "genre")
        val casts = parseActors(text)

        return JvrMovieMetadata(
            code = code,
            title = title,
            posterUrl = posterUrl,
            releaseDate = releaseDate,
            studio = studio,
            genres = genres.flatMap { it.split(',', '/', '|') }.mapNotNull { cleanText(it) }.distinct(),
            casts = casts,
            description = description,
        )
    }

    private fun parseActors(text: String): List<JvrCastMetadata> {
        val actorRegex = Regex("<(?:actor|performer)>(.*?)</(?:actor|performer)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val actors = actorRegex.findAll(text).mapNotNull { match ->
            val content = match.groupValues[1]
            val name = tagValue(content, "name") ?: return@mapNotNull null
            val thumb = tagValue(content, "thumb")

            JvrCastMetadata(
                performerId = "local:${name.lowercase(Locale.US).replace(Regex("[^a-z0-9가-힣ぁ-んァ-ン一-龥]+"), "_").trim('_')}",
                englishName = name,
                profileImageUrl = thumb,
                remoteProfileImageUrl = thumb,
            )
        }.toList()

        if (actors.isNotEmpty()) return actors

        // Fallback for simple NFOs where actors are just a list of names
        return (tagValues(text, "actor") + tagValues(text, "name") + keyValues(text, "actor") + keyValues(text, "actors"))
            .flatMap { splitPeople(it) }
            .distinctBy { it.lowercase(Locale.US) }
            .map { actor ->
                JvrCastMetadata(
                    performerId = "local:${actor.lowercase(Locale.US).replace(Regex("[^a-z0-9가-힣ぁ-んァ-ン一-龥]+"), "_").trim('_')}",
                    englishName = actor,
                )
            }
    }

    private fun tagValue(text: String, tag: String): String? = tagValues(text, tag).firstOrNull()

    private fun tagValues(text: String, tag: String): List<String> {
        val regex = Regex("<\\s*$tag(?:\\s+[^>]*)?>(.*?)<\\s*/\\s*$tag\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return regex.findAll(text).mapNotNull { cleanText(it.groupValues[1].stripXmlTags()) }.toList()
    }

    private fun keyValue(text: String, key: String): String? = keyValues(text, key).firstOrNull()

    private fun keyValues(text: String, key: String): List<String> {
        val regex = Regex("(?im)^\\s*${Regex.escape(key)}\\s*[:=]\\s*(.+?)\\s*$")
        return regex.findAll(text).mapNotNull { cleanText(it.groupValues[1]) }.toList()
    }

    private fun splitPeople(value: String): List<String> = value
        .split(',', '/', '|', ';')
        .mapNotNull { cleanText(it) }

    private fun cleanText(value: String?): String? {
        val cleaned = Html.fromHtml(value.orEmpty(), Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun String.stripXmlTags(): String = replace(Regex("<[^>]+>"), " ")

    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

    private fun parseDate(value: String): LocalDate? {
        val trimmed = value.trim()
        val candidates = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
        )
        for (formatter in candidates) {
            try {
                return LocalDate.parse(trimmed.take(10), formatter)
            } catch (_: DateTimeParseException) {
            }
        }
        return Regex("(?:19|20)\\d{2}").find(trimmed)?.value?.toIntOrNull()?.let { year ->
            LocalDate.of(year, 1, 1)
        }
    }

    companion object {
        const val SOURCE = LOCAL_NFO_SOURCE
    }
}

fun JvrMovieMetadata.mergeLocalFirst(remote: JvrMovieMetadata?): JvrMovieMetadata {
    if (remote == null) return this
    val orderedPosters = buildList {
        posterUrl?.takeIf(String::isNotBlank)?.let(::add)
        addAll(posterFallbackUrls.filter(String::isNotBlank))
        remote.posterUrl?.takeIf(String::isNotBlank)?.let(::add)
        addAll(remote.posterFallbackUrls.filter(String::isNotBlank))
    }.distinct()
    return copy(
        title = title.takeIf { it.isNotBlank() } ?: remote.title,
        posterUrl = orderedPosters.firstOrNull(),
        posterFallbackUrls = orderedPosters.drop(1),
        releaseDate = releaseDate ?: remote.releaseDate,
        studio = studio?.takeIf { it.isNotBlank() } ?: remote.studio,
        genres = genres.takeIf { it.isNotEmpty() } ?: remote.genres,
        casts = casts.takeIf { it.isNotEmpty() } ?: remote.casts,
        description = description?.takeIf { it.isNotBlank() } ?: remote.description,
    )
}
