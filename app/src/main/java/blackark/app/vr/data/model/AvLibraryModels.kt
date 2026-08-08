package blackark.app.vr.data.model

import blackark.app.vr.data.database.entity.AvAssetLocation
import blackark.app.vr.data.database.entity.AvLibraryAsset
import blackark.app.vr.utils.JvrCastMetadata
import blackark.app.vr.utils.JvrMovieMetadata
import blackark.app.vr.utils.extractVirtualGroupPart
import java.time.LocalDate

const val AV_CAST_FILTER_NONE_ID = "__none__"
private val avVrPathPattern = Regex("(^|/)av/vr(/|$)", RegexOption.IGNORE_CASE)
private val avVrStereoPattern = Regex(
    "(?i)(?:\\bVR\\b|\\b8KVR\\b|\\bVR8K\\b|(?:^|[ _.\\-])SBS(?:$|[ _.\\-])|(?:^|[ _.\\-])TB(?:$|[ _.\\-]))"
)
private val avVrContentIdPattern = Regex(
    "(?i)^[a-z0-9]*vr[a-z0-9]*[-_ ]?\\d{2,6}$"
)
private val avVrGenrePattern = Regex(
    "(?i)(?:^|[^a-z0-9])(?:8KVR|VR8K|VR)(?:$|[^a-z0-9])|バーチャルリアリティ"
)

internal fun isVrLibraryContent(
    normalizedCode: String,
    genres: List<String>,
    representativePath: String?,
    representativeFileName: String?,
): Boolean {
    val normalizedRepresentativePath = representativePath.orEmpty().replace('\\', '/')
    val normalizedRepresentativeFileName = representativeFileName.orEmpty()

    return genres.any { genre -> avVrGenrePattern.containsMatchIn(genre.trim()) } ||
            avVrContentIdPattern.matches(normalizedCode.trim()) ||
            avVrPathPattern.containsMatchIn(normalizedRepresentativePath) ||
            avVrStereoPattern.containsMatchIn(normalizedRepresentativeFileName)
}

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

    val displayPosterUrls: List<String>
        get() = buildList {
            metadata?.posterUrl?.takeIf(String::isNotBlank)?.let(::add)
            addAll(metadata?.posterFallbackUrls.orEmpty().filter(String::isNotBlank))
            asset.cachedPosterUrl?.takeIf(String::isNotBlank)?.let(::add)
        }.distinct()

    val displayPosterUrl: String?
        get() = displayPosterUrls.firstOrNull()

    val fallbackThumbnailPath: String?
        get() = representativePath

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

    val isVrContent: Boolean
        get() = isVrLibraryContent(
            normalizedCode = asset.normalizedCode,
            genres = metadata?.genres.orEmpty(),
            representativePath = representativePath,
            representativeFileName = representativeFileName,
        )
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
