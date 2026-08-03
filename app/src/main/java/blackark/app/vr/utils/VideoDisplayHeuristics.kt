package blackark.app.vr.utils

import blackark.app.vr.network.LocalFileClient
import java.io.File
import java.net.URI
import kotlin.math.abs

data class InferredDisplayProfile(
    val videoFormat: String,
    val stereoMode: String,
)

data class ParsedVideoIdentity(
    val fileName: String,
    val filePath: String,
    val serverAddress: String,
    val shareName: String,
)

private const val STEREO_PANORAMA_MIN_WIDTH = 4096
private const val STEREO_PANORAMA_MIN_HEIGHT = 2048
private const val STEREO_PANORAMA_ASPECT_RATIO = 2f
private const val STEREO_PANORAMA_ASPECT_TOLERANCE = 0.05f

fun inferDisplayProfileFromFrame(width: Int, height: Int): InferredDisplayProfile? {
    if (width <= 0 || height <= 0) {
        return null
    }

    val aspectRatio = width.toFloat() / height.toFloat()
    val isStereoPanorama =
        width >= STEREO_PANORAMA_MIN_WIDTH &&
                height >= STEREO_PANORAMA_MIN_HEIGHT &&
                abs(aspectRatio - STEREO_PANORAMA_ASPECT_RATIO) <= STEREO_PANORAMA_ASPECT_TOLERANCE

    if (!isStereoPanorama) {
        return null
    }

    return InferredDisplayProfile(
        videoFormat = "Format180",
        stereoMode = "SideBySide",
    )
}

fun parseVideoIdentity(path: String): ParsedVideoIdentity? {
    val normalizedPath = normalizeVideoPath(path)
    if (normalizedPath.isBlank()) {
        return null
    }

    val sanitizedPath = normalizedPath.replace('\\', '/')
    val fileName = sanitizedPath.substringAfterLast('/').trim()
    if (fileName.isBlank()) {
        return null
    }

    if (sanitizedPath.startsWith("smb://", ignoreCase = true)) {
        val smbRemainder = sanitizedPath.substringAfter("://", "")
        val authority = smbRemainder.substringBefore('/').substringAfterLast('@')
        val serverAddress = when {
            authority.startsWith('[') -> authority.substringAfter('[').substringBefore(']')
            else -> authority.substringBefore(':')
        }.trim()
        if (serverAddress.isBlank()) return null

        val pathSegments = smbRemainder.substringAfter('/', "")
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }
        val shareName = pathSegments.firstOrNull().orEmpty()

        return ParsedVideoIdentity(
            fileName = fileName,
            filePath = sanitizedPath,
            serverAddress = serverAddress,
            shareName = shareName,
        )
    }

    return ParsedVideoIdentity(
        fileName = fileName,
        filePath = sanitizedPath,
        serverAddress = LocalFileClient.LOCAL_STORAGE_ADDRESS,
        shareName = "",
    )
}

private fun normalizeVideoPath(path: String): String {
    val sanitized = path
        .substringBefore('?')
        .substringBefore('#')
        .trim()
    if (sanitized.isBlank()) {
        return sanitized
    }

    if (sanitized.startsWith("file:", ignoreCase = true)) {
        return runCatching { File(URI(sanitized)).absolutePath }
            .getOrElse { sanitized }
    }

    return sanitized
}
