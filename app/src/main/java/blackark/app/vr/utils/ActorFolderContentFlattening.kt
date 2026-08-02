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
        val videos = contentFolderChildren.filter { nestedChild ->
            !nestedChild.isDirectory &&
                SMBClient.isVideoFile(nestedChild.name) &&
                !isTrailerFile(nestedChild.name)
        }

        videos.ifEmpty { listOf(child) }
    }
}
