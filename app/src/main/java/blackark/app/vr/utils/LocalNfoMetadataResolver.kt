package blackark.app.vr.utils

import android.text.Html
import android.util.Log
import blackark.app.vr.network.LocalFileClient
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private const val LOCAL_NFO_SOURCE = "local_nfo"
private const val TAG = "LocalNfoMetadata"
private val localNfoImageExtensions = listOf("jpg", "jpeg", "png", "webp")

data class LocalNfoMetadataResult(
    val metadata: JvrMovieMetadata?,
    val posterUrl: String?,
    val nfoPath: String?,
)

internal fun buildNfoCandidateNames(videoName: String): List<String> {
    val stem = videoName.substringBeforeLast('.', videoName)
    val multipartBaseStem = extractVirtualGroupKey(videoName)
    return buildList {
        add("$stem.nfo")
        if (!multipartBaseStem.isNullOrBlank() && !multipartBaseStem.equals(stem, ignoreCase = true)) {
            add("$multipartBaseStem.nfo")
        }
        add("movie.nfo")
        add("info.nfo")
    }.distinctBy { it.lowercase(Locale.US) }
}

internal fun buildPosterCandidateNames(videoName: String): List<String> {
    val stem = videoName.substringBeforeLast('.', videoName)
    val multipartBaseStem = extractVirtualGroupKey(videoName)
    return buildList {
        for (extension in localNfoImageExtensions) {
            add("$stem.$extension")
            add("$stem-poster.$extension")
        }
        if (!multipartBaseStem.isNullOrBlank() && !multipartBaseStem.equals(stem, ignoreCase = true)) {
            for (extension in localNfoImageExtensions) {
                add("$multipartBaseStem.$extension")
                add("$multipartBaseStem-poster.$extension")
            }
        }
        addAll(
            listOf(
                "poster.jpg",
                "poster.png",
                "poster.webp",
                "fanart.jpg",
                "fanart.png",
                "fanart.webp",
                "cover.jpg",
                "cover.png",
                "cover.webp",
                "folder.jpg",
                "folder.png",
                "folder.webp",
            )
        )
    }.distinctBy { it.lowercase(Locale.US) }
}

class LocalNfoMetadataResolver(
    private val smbClient: SMBClient?,
    private val localClient: LocalFileClient?,
    private val localRootTreeUri: String?,
) {
    suspend fun resolve(video: SMBFileItem, fallbackCode: String): LocalNfoMetadataResult =
        withContext(Dispatchers.IO) {
            val siblingFiles = listSiblingFiles(video).getOrElse { error ->
                Log.d(TAG, "Failed to list siblings for ${video.path}: ${error.message}")
                emptyList()
            }
            val nfoItem = findNfoCandidate(video.name, siblingFiles)
            val posterItem = findPosterCandidate(video.name, siblingFiles)
            val posterUrl = posterItem?.path
            val metadata = nfoItem?.let { item ->
                readText(item.path)
                    .getOrElse { error ->
                        Log.w(TAG, "Failed to read NFO ${item.path}: ${error.message}")
                        null
                    }
                    ?.let { text -> parseNfo(text, fallbackCode, posterUrl) }
            }

            LocalNfoMetadataResult(
                metadata = metadata,
                posterUrl = posterUrl,
                nfoPath = nfoItem?.path,
            )
        }

    private suspend fun listSiblingFiles(video: SMBFileItem): Result<List<SMBFileItem>> {
        val local = localClient
        val smb = smbClient
        return when {
            local != null -> {
                val rootTreeUri = localRootTreeUri.orEmpty()
                val parentUri = LocalFileClient.resolveParentDirectoryUri(rootTreeUri, video.path)
                    ?: return Result.success(emptyList())
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
        } catch (utf8Error: Exception) {
            try {
                val fallbackText = openInputStream(path)
                    .bufferedReader(Charsets.ISO_8859_1)
                    .use { it.readText() }
                Log.d(TAG, "Read NFO with ISO-8859-1 fallback for $path: ${utf8Error.message}")
                Result.success(fallbackText)
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

    private fun findNfoCandidate(videoName: String, siblings: List<SMBFileItem>): SMBFileItem? {
        return findFirstNamedCandidate(
            candidates = buildNfoCandidateNames(videoName),
            siblings = siblings,
        ) { it.endsWith(".nfo", ignoreCase = true) }
    }

    private fun findPosterCandidate(videoName: String, siblings: List<SMBFileItem>): SMBFileItem? {
        return findFirstNamedCandidate(
            candidates = buildPosterCandidateNames(videoName),
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
    return copy(
        title = title.takeIf { it.isNotBlank() } ?: remote.title,
        posterUrl = posterUrl?.takeIf { it.isNotBlank() } ?: remote.posterUrl,
        releaseDate = releaseDate ?: remote.releaseDate,
        studio = studio?.takeIf { it.isNotBlank() } ?: remote.studio,
        genres = genres.takeIf { it.isNotEmpty() } ?: remote.genres,
        casts = casts.takeIf { it.isNotEmpty() } ?: remote.casts,
        description = description?.takeIf { it.isNotBlank() } ?: remote.description,
    )
}
