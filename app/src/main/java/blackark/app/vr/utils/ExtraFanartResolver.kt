package blackark.app.vr.utils

import blackark.app.vr.network.LocalFileClient
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBFileItem

private val extraFanartImageExtensions = setOf("jpg", "jpeg", "png", "webp")
private val naturalNameChunkPattern = Regex("""\d+|\D+""")

class ExtraFanartResolver(
    private val smbClient: SMBClient?,
    private val localClient: LocalFileClient?,
    private val localRootTreeUri: String?,
) {
    suspend fun resolve(videoPath: String): List<String> {
        if (videoPath.isBlank()) return emptyList()

        val siblings = listParentChildren(videoPath)
        val extraFanartFolder = siblings.firstOrNull { item ->
            item.isDirectory && item.name.equals("extrafanart", ignoreCase = true)
        } ?: return emptyList()
        val fanartFiles = when {
            localClient != null -> {
                localClient.listFiles(extraFanartFolder.path, includeHidden = true)
            }

            smbClient != null -> {
                smbClient.listFiles(extraFanartFolder.path, includeHidden = true)
            }

            else -> Result.success(emptyList<SMBFileItem>())
        }.getOrNull().orEmpty()

        return selectExtraFanartFiles(fanartFiles).map(SMBFileItem::path)
    }

    private suspend fun listParentChildren(videoPath: String): List<SMBFileItem> {
        val local = localClient
        if (local != null) {
            val parentUri = LocalFileClient.resolveParentDirectoryUri(
                rootTreeUri = localRootTreeUri.orEmpty(),
                childDocumentUri = videoPath,
            ) ?: return emptyList()
            return local.listFiles(parentUri, includeHidden = true).getOrNull().orEmpty()
        }

        val smb = smbClient ?: return emptyList()
        val parentPath = videoPath.trimEnd('/').substringBeforeLast('/', "")
        if (parentPath.isBlank()) return emptyList()
        return smb.listFiles(parentPath, includeHidden = true).getOrNull().orEmpty()
    }
}

internal fun selectExtraFanartFiles(files: List<SMBFileItem>): List<SMBFileItem> =
    files
        .asSequence()
        .filter { file ->
            !file.isDirectory &&
                file.name.substringAfterLast('.', "").lowercase() in extraFanartImageExtensions
        }
        .sortedWith { left, right ->
            compareNaturalNames(left.name, right.name).takeIf { it != 0 }
                ?: left.path.compareTo(right.path, ignoreCase = true)
        }
        .toList()

private fun compareNaturalNames(left: String, right: String): Int {
    val leftChunks = naturalNameChunkPattern.findAll(left).map { it.value }.toList()
    val rightChunks = naturalNameChunkPattern.findAll(right).map { it.value }.toList()
    val sharedSize = minOf(leftChunks.size, rightChunks.size)

    for (index in 0 until sharedSize) {
        val leftChunk = leftChunks[index]
        val rightChunk = rightChunks[index]
        val comparison =
            if (leftChunk.firstOrNull()?.isDigit() == true && rightChunk.firstOrNull()?.isDigit() == true) {
                compareNumericNames(leftChunk, rightChunk)
            } else {
                leftChunk.compareTo(rightChunk, ignoreCase = true)
            }
        if (comparison != 0) return comparison
    }

    return when {
        leftChunks.size != rightChunks.size -> leftChunks.size.compareTo(rightChunks.size)
        else -> left.compareTo(right, ignoreCase = true).takeIf { it != 0 }
            ?: left.compareTo(right)
    }
}

private fun compareNumericNames(left: String, right: String): Int {
    val normalizedLeft = left.trimStart('0').ifEmpty { "0" }
    val normalizedRight = right.trimStart('0').ifEmpty { "0" }
    return when {
        normalizedLeft.length != normalizedRight.length -> {
            normalizedLeft.length.compareTo(normalizedRight.length)
        }

        normalizedLeft != normalizedRight -> normalizedLeft.compareTo(normalizedRight)
        else -> left.length.compareTo(right.length)
    }
}
