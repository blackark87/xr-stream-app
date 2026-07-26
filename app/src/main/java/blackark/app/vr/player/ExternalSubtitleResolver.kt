package blackark.app.vr.player

import androidx.media3.common.MimeTypes
import blackark.app.vr.network.SMBFileItem

internal data class ExternalSubtitle(
    val file: SMBFileItem,
    val mimeType: String,
)

internal fun resolveKoreanExternalSubtitle(
    videoFileName: String,
    siblingFiles: List<SMBFileItem>,
): ExternalSubtitle? {
    val baseName = videoFileName.substringBeforeLast('.', missingDelimiterValue = videoFileName)
    if (baseName.isBlank()) return null

    val candidates = listOf(
        "$baseName.ko.ass" to MimeTypes.TEXT_SSA,
        "$baseName.ko.srt" to MimeTypes.APPLICATION_SUBRIP,
    )

    return candidates.firstNotNullOfOrNull { (candidateName, mimeType) ->
        siblingFiles
            .firstOrNull { file ->
                !file.isDirectory && file.name.equals(candidateName, ignoreCase = true)
            }
            ?.let { file -> ExternalSubtitle(file = file, mimeType = mimeType) }
    }
}
