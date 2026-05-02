package blackark.app.vr.utils

import android.content.Context
import blackark.app.vr.data.database.AppDatabase
import blackark.app.vr.data.repository.AvLibraryRepository

suspend fun resolveLinkedMetadataForVideoPath(
    context: Context,
    videoPath: String,
): JvrMovieMetadata? {
    val parsedIdentity = parseVideoIdentity(videoPath)
    val sourceScope = parsedIdentity?.let(::buildSourceScope)
    val normalizedCode = extractNormalizedCodeFromPath(videoPath)
    val folderPath = extractFolderPath(videoPath)
    val database = AppDatabase.getDatabase(context.applicationContext)
    val avLibraryRepository = AvLibraryRepository(
        avLibraryDao = database.avLibraryDao(),
        virtualGroupMetadataDao = database.virtualGroupMetadataDao(),
    )
    val asset = if (sourceScope != null) {
        avLibraryRepository.findAssetForPathOrCode(
            filePath = parsedIdentity.filePath,
            sourceScope = sourceScope,
            normalizedCode = normalizedCode,
        )
    } else {
        null
    }

    return when {
        !asset?.metadataCacheKey.isNullOrBlank() -> {
            JvrLibraryMetadataProvider.getByCacheKey(
                context = context.applicationContext,
                cacheKey = asset.metadataCacheKey,
            )
        }

        !normalizedCode.isNullOrBlank() -> {
            JvrLibraryMetadataProvider.getByCode(
                context = context.applicationContext,
                rawCode = normalizedCode,
                folderPath = folderPath,
            )
        }

        else -> null
    }
}

suspend fun resolveLinkedMetadataForGroup(
    context: Context,
    sourceScope: String?,
    normalizedCode: String,
    folderPath: String,
): JvrMovieMetadata? {
    val database = AppDatabase.getDatabase(context.applicationContext)
    val avLibraryRepository = AvLibraryRepository(
        avLibraryDao = database.avLibraryDao(),
        virtualGroupMetadataDao = database.virtualGroupMetadataDao(),
    )
    val asset = if (sourceScope != null) {
        avLibraryRepository.getAssetBySourceAndCode(
            sourceScope,
            sanitizeNormalizedCode(normalizedCode)
        )
    } else {
        null
    }

    return when {
        !asset?.metadataCacheKey.isNullOrBlank() -> {
            JvrLibraryMetadataProvider.getByCacheKey(
                context = context.applicationContext,
                cacheKey = asset.metadataCacheKey,
            )
        }

        else -> {
            JvrLibraryMetadataProvider.getByCode(
                context = context.applicationContext,
                rawCode = normalizedCode,
                folderPath = folderPath,
            )
        }
    }
}
