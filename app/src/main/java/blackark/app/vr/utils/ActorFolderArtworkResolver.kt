package blackark.app.vr.utils

import blackark.app.vr.network.LocalFileClient
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

private val actorArtworkExtensions = listOf("jpg", "jpeg", "png", "webp")

internal fun normalizeActorArtworkName(value: String): String {
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun selectActorFolderArtwork(
    folderName: String,
    files: List<SMBFileItem>,
): SMBFileItem? {
    val extensionRank = actorArtworkExtensions.withIndex().associate { (index, value) ->
        value to index
    }
    val supportedImages = files
        .asSequence()
        .filterNot(SMBFileItem::isDirectory)
        .mapNotNull { file ->
            val extension = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            extensionRank[extension]?.let { rank -> Triple(file, extension, rank) }
        }
        .toList()

    if (supportedImages.isEmpty()) return null

    val normalizedFolderName = normalizeActorArtworkName(folderName)
    val exactMatches = supportedImages.filter { (file, _, _) ->
        normalizeActorArtworkName(file.name.substringBeforeLast('.', file.name)) ==
            normalizedFolderName
    }
    val comparator = if (exactMatches.isNotEmpty()) {
        compareBy<Triple<SMBFileItem, String, Int>>(
            { it.third },
            { it.first.name.lowercase(Locale.ROOT) },
            { it.first.name },
        )
    } else {
        compareBy<Triple<SMBFileItem, String, Int>>(
            { it.first.name.lowercase(Locale.ROOT) },
            { it.first.name },
        )
    }

    return (exactMatches.ifEmpty { supportedImages })
        .minWithOrNull(comparator)
        ?.first
}

class ActorFolderArtworkResolver(
    private val smbClient: SMBClient?,
    private val localClient: LocalFileClient?,
) {
    suspend fun resolveActorFolderArtwork(folder: SMBFileItem): String? =
        withContext(Dispatchers.IO) {
            if (!folder.isDirectory) return@withContext null

            val children = listFiles(folder.path).getOrNull().orEmpty()
            val actorsFolder = children.firstOrNull { item ->
                item.isDirectory && item.name.equals(".actors", ignoreCase = true)
            } ?: return@withContext null

            val actorImages = listFiles(actorsFolder.path).getOrNull().orEmpty()
            selectActorFolderArtwork(folder.name, actorImages)?.path
        }

    private suspend fun listFiles(path: String): Result<List<SMBFileItem>> {
        return when {
            localClient != null -> localClient.listFiles(path, includeHidden = true)
            smbClient != null -> smbClient.listFiles(path, includeHidden = true)
            else -> Result.success(emptyList())
        }
    }
}
