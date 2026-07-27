package blackark.app.vr.player

import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBFileItem

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
        .sortedWith(Comparator { left, right -> left.name.compareTo(right.name, ignoreCase = true) })
        .toList()
    val currentIndex = resolveCurrentPlaylistIndex(videoFiles, currentFile)
    return PlaybackPlaylistSnapshot(
        files = videoFiles,
        currentIndex = currentIndex,
    )
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

private fun normalizePlaybackPath(path: String): String =
    path
        .trim()
        .replace('\\', '/')
        .removeSuffix("/")
        .lowercase()
