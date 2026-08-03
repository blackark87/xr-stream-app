package blackark.app.vr.ui.screens

import blackark.app.vr.utils.JvrMovieMetadata
import blackark.app.vr.utils.BrowserFolderArtworkResolution

internal sealed interface ArtworkState<out T> {
    data object Loading : ArtworkState<Nothing>

    data class Resolved<T>(val value: T) : ArtworkState<T>

    data object Missing : ArtworkState<Nothing>
}

internal sealed interface VideoArtworkSelection {
    data object Placeholder : VideoArtworkSelection

    data class Poster(val url: String) : VideoArtworkSelection

    data class GeneratedFrame(val videoPath: String) : VideoArtworkSelection
}

internal enum class VideoArtworkPolicy {
    MetadataPreferred,
    GeneratedFrameOnly,
}

internal sealed interface FolderArtworkSelection {
    data object Placeholder : FolderArtworkSelection

    data class Poster(val url: String) : FolderArtworkSelection

    data class ActorImage(val url: String) : FolderArtworkSelection

    data class GeneratedFrame(val videoPath: String) : FolderArtworkSelection

    data object FolderIcon : FolderArtworkSelection
}

internal fun selectVideoArtwork(
    metadataState: ArtworkState<JvrMovieMetadata>,
    posterFailureCount: Int,
    videoPath: String?,
): VideoArtworkSelection {
    if (metadataState is ArtworkState.Loading) {
        return VideoArtworkSelection.Placeholder
    }

    val metadata = (metadataState as? ArtworkState.Resolved)?.value
    val posterUrl = buildList {
        metadata?.posterUrl?.takeIf(String::isNotBlank)?.let(::add)
        addAll(metadata?.posterFallbackUrls.orEmpty().filter(String::isNotBlank))
    }.distinct().getOrNull(posterFailureCount.coerceAtLeast(0))

    return when {
        posterUrl != null -> VideoArtworkSelection.Poster(posterUrl)
        !videoPath.isNullOrBlank() -> VideoArtworkSelection.GeneratedFrame(videoPath)
        else -> VideoArtworkSelection.Placeholder
    }
}

internal fun selectVideoArtwork(
    policy: VideoArtworkPolicy,
    metadataState: ArtworkState<JvrMovieMetadata>,
    posterFailureCount: Int,
    videoPath: String?,
): VideoArtworkSelection = when (policy) {
    VideoArtworkPolicy.MetadataPreferred -> selectVideoArtwork(
        metadataState = metadataState,
        posterFailureCount = posterFailureCount,
        videoPath = videoPath,
    )

    VideoArtworkPolicy.GeneratedFrameOnly -> videoPath
        ?.takeIf(String::isNotBlank)
        ?.let(VideoArtworkSelection::GeneratedFrame)
        ?: VideoArtworkSelection.Placeholder
}

/** Compatibility helper for call sites that only have a single poster candidate. */
internal fun selectVideoArtwork(
    metadataState: ArtworkState<JvrMovieMetadata>,
    posterLoadFailed: Boolean,
    videoPath: String?,
): VideoArtworkSelection = selectVideoArtwork(
    metadataState = metadataState,
    posterFailureCount = if (posterLoadFailed) Int.MAX_VALUE else 0,
    videoPath = videoPath,
)

internal fun <T> ArtworkState<T>.resolvedValueOrNull(): T? {
    return (this as? ArtworkState.Resolved)?.value
}

internal fun selectFolderArtwork(
    artworkState: ArtworkState<BrowserFolderArtworkResolution>,
    imageFailureCount: Int,
): FolderArtworkSelection {
    if (artworkState is ArtworkState.Loading) {
        return FolderArtworkSelection.Placeholder
    }

    val resolution = artworkState.resolvedValueOrNull()
        ?: return FolderArtworkSelection.FolderIcon
    val representativeVideoPath = resolution.representativeVideo?.path
    val posterCandidates = buildList {
        resolution.metadata?.posterUrl?.takeIf(String::isNotBlank)?.let(::add)
        addAll(resolution.metadata?.posterFallbackUrls.orEmpty().filter(String::isNotBlank))
    }.distinct()
    val posterUrl = posterCandidates.getOrNull(imageFailureCount.coerceAtLeast(0))

    return when {
        posterUrl != null -> FolderArtworkSelection.Poster(posterUrl)
        !representativeVideoPath.isNullOrBlank() -> {
            FolderArtworkSelection.GeneratedFrame(representativeVideoPath)
        }

        !resolution.actorArtworkUrl.isNullOrBlank() && imageFailureCount == posterCandidates.size -> {
            FolderArtworkSelection.ActorImage(resolution.actorArtworkUrl)
        }

        else -> FolderArtworkSelection.FolderIcon
    }
}

internal fun selectFolderArtwork(
    artworkState: ArtworkState<BrowserFolderArtworkResolution>,
    imageLoadFailed: Boolean,
): FolderArtworkSelection = selectFolderArtwork(
    artworkState = artworkState,
    imageFailureCount = if (imageLoadFailed) Int.MAX_VALUE else 0,
)
