package blackark.app.vr.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import blackark.app.vr.network.SMBFileItem
import blackark.app.vr.network.resolveKoreanSubtitleSidecar

internal data class ExternalSubtitle(
    val file: SMBFileItem,
    val mimeType: String,
)

internal fun resolveKoreanExternalSubtitle(
    videoFileName: String,
    siblingFiles: List<SMBFileItem>,
): ExternalSubtitle? {
    val file = resolveKoreanSubtitleSidecar(
        videoFileName = videoFileName,
        siblingFiles = siblingFiles,
    ) ?: return null
    val mimeType = if (file.name.endsWith(".ass", ignoreCase = true)) {
        MimeTypes.TEXT_SSA
    } else {
        MimeTypes.APPLICATION_SUBRIP
    }
    return ExternalSubtitle(file = file, mimeType = mimeType)
}

internal fun buildKoreanExternalSubtitleCandidates(
    videoFile: SMBFileItem,
): List<ExternalSubtitle> {
    val baseName = videoFile.name.substringBeforeLast('.', missingDelimiterValue = videoFile.name)
    if (baseName.isBlank()) return emptyList()

    val basePath = videoFile.path.substringBeforeLast(
        delimiter = '.',
        missingDelimiterValue = videoFile.path,
    )
    return listOf(
        ExternalSubtitle(
            file = videoFile.copy(
                name = "$baseName.ko.ass",
                path = "$basePath.ko.ass",
            ),
            mimeType = MimeTypes.TEXT_SSA,
        ),
        ExternalSubtitle(
            file = videoFile.copy(
                name = "$baseName.ko.srt",
                path = "$basePath.ko.srt",
            ),
            mimeType = MimeTypes.APPLICATION_SUBRIP,
        ),
    )
}

internal fun configureKoreanExternalSubtitle(
    parameters: TrackSelectionParameters,
    enabled: Boolean,
): TrackSelectionParameters {
    val builder = parameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
        .setSelectTextByDefault(enabled)

    if (enabled) {
        builder
            .setPreferredTextLanguage("ko")
            .setSelectUndeterminedTextLanguage(true)
    }

    return builder.build()
}
