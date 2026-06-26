package blackark.app.vr.utils

private val videoFileCodePattern = Regex("""(?i)([a-z]{2,10})[-_](\d{2,5})(?!\d)""")
private val multipartVideoPattern = Regex(
    """^(.*?)(?:[ _-](?:(cd|pt|part)?(\d{1,2})))$""",
    RegexOption.IGNORE_CASE,
)

internal data class MultipartVideoMatch(
    val baseName: String,
    val partNumber: Int,
)

internal fun parseMultipartVideoName(fileName: String): MultipartVideoMatch? {
    val stem = fileName.substringBeforeLast('.', fileName).trim()
    val match = multipartVideoPattern.matchEntire(stem) ?: return null
    val baseName = match.groupValues[1].trimEnd('-', '_', ' ')
    val partNumber = match.groupValues[3].toIntOrNull() ?: return null
    return baseName.takeIf { it.isNotBlank() }?.let { normalizedBaseName ->
        MultipartVideoMatch(
            baseName = normalizedBaseName,
            partNumber = partNumber,
        )
    }
}

fun sanitizeNormalizedCode(code: String): String {
    return code.trim().uppercase()
}

fun extractVirtualGroupKey(fileName: String): String? {
    return parseMultipartVideoName(fileName)?.baseName
}

fun extractVirtualGroupPart(fileName: String): Int? {
    return parseMultipartVideoName(fileName)?.partNumber
}

fun extractNormalizedCodeFromFileName(fileName: String): String? {
    val stem = fileName.substringBeforeLast('.', fileName)
    val directMatch = videoFileCodePattern.find(stem)
    if (directMatch != null) {
        return "${directMatch.groupValues[1].uppercase()}-${directMatch.groupValues[2]}"
    }

    val groupKey = extractVirtualGroupKey(fileName) ?: return null
    val groupMatch = videoFileCodePattern.find(groupKey)
    return if (groupMatch != null) {
        "${groupMatch.groupValues[1].uppercase()}-${groupMatch.groupValues[2]}"
    } else {
        groupKey.trim().uppercase().takeIf { it.isNotBlank() }
    }
}

fun extractNormalizedCodeFromPath(path: String): String? {
    val fileName = extractFileName(path)
    if (fileName.isBlank()) return null
    return extractNormalizedCodeFromFileName(fileName)
}

fun extractFileName(path: String): String {
    return path
        .substringBefore('?')
        .substringBefore('#')
        .replace('\\', '/')
        .substringAfterLast('/')
        .trim()
}

fun extractFolderPath(path: String): String {
    val sanitized = path
        .substringBefore('?')
        .substringBefore('#')
        .replace('\\', '/')
        .trim()
    return sanitized.substringBeforeLast('/', "")
}

fun buildSourceScope(serverAddress: String, shareName: String): String {
    return "${serverAddress.trim()}::${shareName.trim()}"
}

fun buildSourceScope(identity: ParsedVideoIdentity): String {
    return buildSourceScope(identity.serverAddress, identity.shareName)
}

fun buildAssetKey(sourceScope: String, normalizedCode: String): String {
    return "${sourceScope.trim()}::${sanitizeNormalizedCode(normalizedCode)}"
}
