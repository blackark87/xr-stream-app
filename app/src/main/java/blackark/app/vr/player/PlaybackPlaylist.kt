package blackark.app.vr.player

import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBFileItem
import blackark.app.vr.utils.extractVirtualGroupKey
import blackark.app.vr.utils.extractVirtualGroupPart

internal data class PlaybackPlaylistSnapshot(
    val files: List<SMBFileItem>,
    val currentIndex: Int,
) {
    val canPlayPrevious: Boolean
        get() = currentIndex > 0 && currentIndex < files.size

    val canPlayNext: Boolean
        get() = currentIndex >= 0 && currentIndex < files.lastIndex
}

internal fun resolvePlaybackPlaylist(
    currentFile: SMBFileItem,
    siblingFiles: List<SMBFileItem>,
): PlaybackPlaylistSnapshot {
    val videoFiles = siblingFiles
        .asSequence()
        .filter { !it.isDirectory && SMBClient.isVideoFile(it.name) }
        .sortedWith(naturalPlaybackFileComparator)
        .toList()
    val currentIndex = resolveCurrentPlaylistIndex(videoFiles, currentFile)
    return PlaybackPlaylistSnapshot(
        files = videoFiles,
        currentIndex = currentIndex,
    )
}

internal fun resolveNextPlaybackTarget(
    playlist: List<SMBFileItem>,
    currentIndex: Int,
): SMBFileItem? {
    if (currentIndex !in playlist.indices || currentIndex >= playlist.lastIndex) return null
    return playlist[currentIndex + 1]
}

internal fun shouldHandlePlaybackEnded(
    listenerGeneration: Long,
    currentGeneration: Long,
    autoAdvanceInProgress: Boolean,
    endedGenerationAlreadyHandled: Boolean,
): Boolean =
    listenerGeneration == currentGeneration &&
        !autoAdvanceInProgress &&
        !endedGenerationAlreadyHandled

internal val naturalPlaybackFileComparator = Comparator<SMBFileItem> { left, right ->
    val leftGroup = extractVirtualGroupKey(left.name)
    val rightGroup = extractVirtualGroupKey(right.name)
    val multipartPartComparison =
        if (
            leftGroup != null &&
                rightGroup != null &&
                leftGroup.equals(rightGroup, ignoreCase = true)
        ) {
            compareValues(
                extractVirtualGroupPart(left.name) ?: Int.MAX_VALUE,
                extractVirtualGroupPart(right.name) ?: Int.MAX_VALUE,
            )
        } else {
            0
        }

    when {
        multipartPartComparison != 0 -> multipartPartComparison
        else -> {
            val nameComparison = compareNaturalText(left.name, right.name)
            when {
                nameComparison != 0 -> nameComparison
                else -> normalizePlaybackPath(left.path).compareTo(normalizePlaybackPath(right.path))
            }
        }
    }
}

internal fun resolveCurrentPlaylistIndex(
    playlist: List<SMBFileItem>,
    currentFile: SMBFileItem,
): Int {
    if (playlist.isEmpty()) return -1

    val normalizedCurrent = normalizePlaybackPath(currentFile.path)
    val byPath = playlist.indexOfFirst {
        normalizePlaybackPath(it.path) == normalizedCurrent
    }
    if (byPath >= 0) return byPath

    return playlist.indexOfFirst { it.name.equals(currentFile.name, ignoreCase = true) }
}

private fun compareNaturalText(left: String, right: String): Int {
    val leftChunks = naturalTextChunkPattern.findAll(left).map { it.value }.toList()
    val rightChunks = naturalTextChunkPattern.findAll(right).map { it.value }.toList()
    val sharedSize = minOf(leftChunks.size, rightChunks.size)

    for (index in 0 until sharedSize) {
        val leftChunk = leftChunks[index]
        val rightChunk = rightChunks[index]
        val comparison =
            if (leftChunk.firstOrNull()?.isDigit() == true && rightChunk.firstOrNull()?.isDigit() == true) {
                compareNumericText(leftChunk, rightChunk)
            } else {
                leftChunk.compareTo(rightChunk, ignoreCase = true)
            }
        if (comparison != 0) return comparison
    }

    return when {
        leftChunks.size != rightChunks.size -> leftChunks.size.compareTo(rightChunks.size)
        else -> left.compareTo(right, ignoreCase = true).takeIf { it != 0 } ?: left.compareTo(right)
    }
}

private fun compareNumericText(left: String, right: String): Int {
    val normalizedLeft = left.trimStart('0').ifEmpty { "0" }
    val normalizedRight = right.trimStart('0').ifEmpty { "0" }
    return when {
        normalizedLeft.length != normalizedRight.length ->
            normalizedLeft.length.compareTo(normalizedRight.length)

        normalizedLeft != normalizedRight -> normalizedLeft.compareTo(normalizedRight)
        else -> left.length.compareTo(right.length)
    }
}

private val naturalTextChunkPattern = Regex("""\d+|\D+""")

private fun normalizePlaybackPath(path: String): String =
    path
        .trim()
        .replace('\\', '/')
        .removeSuffix("/")
        .lowercase()
