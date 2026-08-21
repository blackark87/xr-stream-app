package blackark.app.vr.network

import java.util.Locale

private val koreanSubtitleSuffixes = listOf(".ko.ass", ".ko.srt")

private fun subtitleCandidateNames(videoFileName: String): List<String> {
    val baseName = videoFileName.substringBeforeLast('.', missingDelimiterValue = videoFileName)
    if (baseName.isBlank()) return emptyList()
    return koreanSubtitleSuffixes.map { suffix -> (baseName + suffix).lowercase(Locale.ROOT) }
}

internal fun resolveKoreanSubtitleSidecar(
    videoFileName: String,
    siblingFiles: List<SMBFileItem>,
): SMBFileItem? {
    val filesByName = siblingFiles
        .asSequence()
        .filter { file -> !file.isDirectory }
        .associateBy { file -> file.name.lowercase(Locale.ROOT) }

    return subtitleCandidateNames(videoFileName).firstNotNullOfOrNull { candidateName ->
        filesByName[candidateName]
    }
}

internal fun attachKoreanSubtitleSidecarPaths(
    files: List<SMBFileItem>,
): List<SMBFileItem> {
    val filesByName = files
        .asSequence()
        .filter { file -> !file.isDirectory }
        .associateBy { file -> file.name.lowercase(Locale.ROOT) }

    return files.map { file ->
        if (file.isDirectory || !SMBClient.isVideoFile(file.name)) {
            file
        } else {
            val subtitle = subtitleCandidateNames(file.name)
                .firstNotNullOfOrNull { candidateName -> filesByName[candidateName] }
            file.copy(subtitlePath = subtitle?.path)
        }
    }
}
