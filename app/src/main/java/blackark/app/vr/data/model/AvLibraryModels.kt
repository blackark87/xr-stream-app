package blackark.app.vr.data.model

import blackark.app.vr.data.database.entity.AvAssetLocation
import blackark.app.vr.data.database.entity.AvLibraryAsset
import blackark.app.vr.utils.JvrCastMetadata
import blackark.app.vr.utils.JvrMovieMetadata
import blackark.app.vr.utils.extractVirtualGroupPart
import java.time.LocalDate

const val AV_CAST_FILTER_NONE_ID = "__none__"

data class AvLibraryWork(
    val asset: AvLibraryAsset,
    val metadata: JvrMovieMetadata?,
    val parts: List<AvAssetLocation>,
) {
    val assetKey: String
        get() = asset.assetKey

    val displayTitle: String
        get() = metadata?.title?.takeIf { it.isNotBlank() }
            ?: asset.cachedTitle?.takeIf { it.isNotBlank() }
            ?: asset.normalizedCode

    val displayPosterUrl: String?
        get() = metadata?.posterUrl ?: asset.cachedPosterUrl

    val fallbackThumbnailPath: String?
        get() = representativePath?.takeIf {
            displayPosterUrl.isNullOrBlank() && parts.size > 1
        }

    val studio: String?
        get() = metadata?.studio ?: asset.cachedStudio

    val releaseDate: LocalDate?
        get() = metadata?.releaseDate ?: asset.cachedReleaseDateEpochDay?.let(LocalDate::ofEpochDay)

    val casts: List<JvrCastMetadata>
        get() = metadata?.casts.orEmpty()

    val sortedParts: List<AvAssetLocation>
        get() = parts.sortedWith(
            compareBy<AvAssetLocation>(
                { it.partNumber ?: extractVirtualGroupPart(it.fileName) ?: Int.MAX_VALUE },
                { it.fileName.lowercase() }
            )
        )

    private val representativePart: AvAssetLocation?
        get() = sortedParts.firstOrNull()

    val representativePath: String?
        get() = representativePart?.filePath ?: asset.representativePath

    val representativeFileName: String?
        get() = representativePart?.fileName ?: asset.representativeFileName
}

data class AvStudioFilterOption(
    val studio: String,
    val itemCount: Int,
)

data class AvCastFilterOption(
    val performerId: String,
    val englishName: String,
    val japaneseName: String?,
    val profileImageUrl: String?,
    val itemCount: Int,
) {
    val isNoneOption: Boolean
        get() = performerId == AV_CAST_FILTER_NONE_ID
}

data class AvReleaseDateCount(
    val date: LocalDate,
    val itemCount: Int,
)

data class AvLibrarySnapshot(
    val works: List<AvLibraryWork> = emptyList(),
    val studioOptions: List<AvStudioFilterOption> = emptyList(),
    val castOptions: List<AvCastFilterOption> = emptyList(),
    val releaseDateCounts: List<AvReleaseDateCount> = emptyList(),
)
