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

internal sealed interface FolderArtworkSelection {
    data object Placeholder : FolderArtworkSelection

    data class Poster(val url: String) : FolderArtworkSelection

    data class ActorImage(val url: String) : FolderArtworkSelection

    data class GeneratedFrame(val videoPath: String) : FolderArtworkSelection

    data object FolderIcon : FolderArtworkSelection
}

internal fun selectVideoArtwork(
    metadataState: ArtworkState<JvrMovieMetadata>,
    posterLoadFailed: Boolean,
    videoPath: String?,
): VideoArtworkSelection {
    if (metadataState is ArtworkState.Loading) {
        return VideoArtworkSelection.Placeholder
    }

    val posterUrl = (metadataState as? ArtworkState.Resolved)
        ?.value
        ?.posterUrl
        ?.takeIf { it.isNotBlank() }

    return when {
        posterUrl != null && !posterLoadFailed -> VideoArtworkSelection.Poster(posterUrl)
        !videoPath.isNullOrBlank() -> VideoArtworkSelection.GeneratedFrame(videoPath)
        else -> VideoArtworkSelection.Placeholder
    }
}

internal fun <T> ArtworkState<T>.resolvedValueOrNull(): T? {
    return (this as? ArtworkState.Resolved)?.value
}

internal fun selectFolderArtwork(
    artworkState: ArtworkState<BrowserFolderArtworkResolution>,
    imageLoadFailed: Boolean,
): FolderArtworkSelection {
    if (artworkState is ArtworkState.Loading) {
        return FolderArtworkSelection.Placeholder
    }

    val resolution = artworkState.resolvedValueOrNull()
        ?: return FolderArtworkSelection.FolderIcon
    val representativeVideoPath = resolution.representativeVideo?.path
    val posterUrl = resolution.metadata?.posterUrl?.takeIf { it.isNotBlank() }

    return when {
        posterUrl != null && !imageLoadFailed -> FolderArtworkSelection.Poster(posterUrl)
        !representativeVideoPath.isNullOrBlank() -> {
            FolderArtworkSelection.GeneratedFrame(representativeVideoPath)
        }

        !resolution.actorArtworkUrl.isNullOrBlank() && !imageLoadFailed -> {
            FolderArtworkSelection.ActorImage(resolution.actorArtworkUrl)
        }

        else -> FolderArtworkSelection.FolderIcon
    }
}
