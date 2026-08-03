package blackark.app.vr.utils

import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBFileItem
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private fun decodedPathSegments(path: String): List<String> {
    val sanitized = path
        .substringBefore('?')
        .substringBefore('#')
    val decoded = runCatching {
        URLDecoder.decode(sanitized, StandardCharsets.UTF_8.name())
    }.getOrDefault(sanitized)

    return decoded
        .replace('\\', '/')
        .trimEnd('/')
        .split('/')
        .map { segment -> segment.trim() }
        .filter { segment -> segment.isNotBlank() }
}

internal fun isJapanActorFolderPath(path: String): Boolean {
    val segments = decodedPathSegments(path)
    if (segments.size < 2) return false

    return segments[segments.lastIndex - 1]
        .substringAfterLast(':')
        .equals("japan", ignoreCase = true)
}

internal fun resolveParentFolderBaseName(path: String): String? {
    val segments = decodedPathSegments(path)
    if (segments.size < 2) return null

    return segments[segments.lastIndex - 1]
        .substringAfterLast(':')
        .trim()
        .takeIf { name -> name.isNotBlank() }
}

internal fun flattenActorContentDirectories(
    actorFolderChildren: List<SMBFileItem>,
    contentFolderChildrenByPath: Map<String, List<SMBFileItem>>,
): List<SMBFileItem> {
    return actorFolderChildren.flatMap { child ->
        if (!child.isDirectory) {
            return@flatMap listOf(child)
        }

        val contentFolderChildren = contentFolderChildrenByPath[child.path]
            ?: return@flatMap listOf(child)
        val videos = attachTrailerPreviewPaths(contentFolderChildren).filter { nestedChild ->
            !nestedChild.isDirectory &&
                SMBClient.isVideoFile(nestedChild.name) &&
                !isTrailerFile(nestedChild.name)
        }

        videos.ifEmpty { listOf(child) }
    }
}

private val trailerSuffixPattern = Regex(
    """(?i)(?:[-_.](?:trailer|preview))$""",
)

private fun videoStem(fileName: String): String =
    fileName.substringBeforeLast('.', fileName).trim()

private fun previewBaseName(fileName: String): String? {
    val stem = videoStem(fileName)
    if (stem.equals("trailer", ignoreCase = true) || stem.equals("preview", ignoreCase = true)) {
        return null
    }
    return stem.replace(trailerSuffixPattern, "").trim().takeIf(String::isNotBlank)
}

private fun mainVideoBaseName(fileName: String): String =
    extractVirtualGroupKey(fileName) ?: videoStem(fileName)

internal fun resolveTrailerPreviewFile(
    video: SMBFileItem,
    siblings: List<SMBFileItem>,
): SMBFileItem? {
    if (video.isDirectory || !SMBClient.isVideoFile(video.name) || isTrailerFile(video.name)) {
        return null
    }

    val mainBaseName = mainVideoBaseName(video.name)
    val normalizedCode = extractNormalizedCodeFromFileName(video.name)
    val mainBaseNames = siblings
        .asSequence()
        .filter { sibling ->
            !sibling.isDirectory &&
                SMBClient.isVideoFile(sibling.name) &&
                !isTrailerFile(sibling.name)
        }
        .map { sibling -> mainVideoBaseName(sibling.name).lowercase() }
        .distinct()
        .toList()

    return siblings
        .asSequence()
        .filter { sibling ->
            !sibling.isDirectory &&
                SMBClient.isVideoFile(sibling.name) &&
                isTrailerFile(sibling.name)
        }
        .map { trailer ->
            val trailerBaseName = previewBaseName(trailer.name)
            val matchPriority = when {
                trailerBaseName != null &&
                    trailerBaseName.equals(mainBaseName, ignoreCase = true) -> 0

                normalizedCode != null &&
                    extractNormalizedCodeFromFileName(trailer.name) == normalizedCode -> 1

                trailerBaseName == null && mainBaseNames.size == 1 -> 2
                else -> Int.MAX_VALUE
            }
            Triple(matchPriority, trailer.name.lowercase(), trailer)
        }
        .filter { candidate -> candidate.first != Int.MAX_VALUE }
        .sortedWith(compareBy<Triple<Int, String, SMBFileItem>>({ it.first }, { it.second }))
        .map { candidate -> candidate.third }
        .firstOrNull()
}

internal fun attachTrailerPreviewPaths(files: List<SMBFileItem>): List<SMBFileItem> =
    files.map { file ->
        if (file.isDirectory || !SMBClient.isVideoFile(file.name) || isTrailerFile(file.name)) {
            file
        } else {
            file.copy(
                trailerPath = resolveTrailerPreviewFile(file, files)?.path,
            )
        }
    }
