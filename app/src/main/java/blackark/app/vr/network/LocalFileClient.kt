package blackark.app.vr.network

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Local storage client backed by SAF tree/document URIs.
 */
class LocalFileClient(
    private val context: Context,
    private val rootTreeUri: String,
) {

    companion object {
        const val LOCAL_STORAGE_ADDRESS = "local://storage"

        fun resolveParentDirectoryUri(
            rootTreeUri: String,
            childDocumentUri: String,
        ): String? {
            return runCatching {
                val treeUri = Uri.parse(rootTreeUri)
                val childUri = Uri.parse(childDocumentUri)

                val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                val childDocumentId = DocumentsContract.getDocumentId(childUri)

                if (childDocumentId == treeDocumentId) {
                    return null
                }

                val parentDocumentId = childDocumentId.substringBeforeLast(
                    delimiter = "/",
                    missingDelimiterValue = treeDocumentId,
                )

                DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId).toString()
            }.getOrNull()
        }
    }

    private val appContext: Context = context.applicationContext
    private val rootUri: Uri = Uri.parse(rootTreeUri)

    private val documentProjection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
    )

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val rootDocumentId = DocumentsContract.getTreeDocumentId(rootUri)
            val rootDocumentUri =
                DocumentsContract.buildDocumentUriUsingTree(rootUri, rootDocumentId)
            val rootMimeType = appContext.contentResolver.query(
                rootDocumentUri,
                arrayOf(Document.COLUMN_MIME_TYPE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }
                val mimeTypeIndex = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE)
                cursor.getStringIfPresent(mimeTypeIndex)
            } ?: return@withContext Result.failure(
                IllegalStateException("Local root folder is not accessible")
            )

            if (rootMimeType != Document.MIME_TYPE_DIR) {
                return@withContext Result.failure(
                    IllegalStateException("Selected local root is not a directory")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(path: String = ""): Result<List<SMBFileItem>> =
        withContext(Dispatchers.IO) {
            try {
                val parentDocumentId = resolveDocumentId(path)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Directory does not exist or cannot be opened")
                    )
                val childrenUri =
                    DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, parentDocumentId)

                val files = mutableListOf<SMBFileItem>()
                appContext.contentResolver.query(
                    childrenUri,
                    documentProjection,
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndex(Document.COLUMN_SIZE)
                    val modifiedIndex = cursor.getColumnIndex(Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext()) {
                        val documentId =
                            cursor.getStringIfPresent(idIndex)?.takeIf { it.isNotBlank() }
                                ?: continue
                        val documentUri =
                            DocumentsContract.buildDocumentUriUsingTree(rootUri, documentId)
                        val displayName = cursor.getStringIfPresent(nameIndex)?.trim().orEmpty()
                        val resolvedName =
                            if (displayName.isNotBlank()) {
                                displayName
                            } else {
                                documentUri.lastPathSegment.orEmpty()
                            }

                        if (resolvedName.startsWith('.') || resolvedName.equals("extrafanart", ignoreCase = true)) {
                            continue
                        }

                        val mimeType = cursor.getStringIfPresent(mimeIndex).orEmpty()
                        val isDirectory = mimeType == Document.MIME_TYPE_DIR
                        val size = cursor.getLongIfPresent(sizeIndex).coerceAtLeast(0L)
                        val lastModified = cursor.getLongIfPresent(modifiedIndex).coerceAtLeast(0L)

                        files += SMBFileItem(
                            name = resolvedName,
                            path = documentUri.toString(),
                            isDirectory = isDirectory,
                            size = if (isDirectory) 0L else size,
                            lastModified = lastModified,
                        )
                    }
                } ?: return@withContext Result.failure(
                    IllegalStateException("Directory query failed")
                )

                Result.success(files)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getInputStream(path: String): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(path)
            val stream = appContext.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Unable to open local file input stream")
                )
            Result.success(stream)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileInfo(path: String): Result<SMBFileItem> = withContext(Dispatchers.IO) {
        try {
            val documentUri = resolveDocumentUri(path)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("File does not exist")
                )
            val resolver = appContext.contentResolver
            val info = resolver.query(
                documentUri,
                documentProjection,
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }

                val name =
                    cursor.getStringIfPresent(cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME))
                        ?.trim()
                        .orEmpty()
                val mimeType =
                    cursor.getStringIfPresent(cursor.getColumnIndex(Document.COLUMN_MIME_TYPE))
                        .orEmpty()
                val isDirectory = mimeType == Document.MIME_TYPE_DIR
                val size = cursor.getLongIfPresent(cursor.getColumnIndex(Document.COLUMN_SIZE))
                    .coerceAtLeast(0L)
                val lastModified =
                    cursor.getLongIfPresent(cursor.getColumnIndex(Document.COLUMN_LAST_MODIFIED))
                        .coerceAtLeast(0L)

                SMBFileItem(
                    name = if (name.isNotBlank()) name else documentUri.lastPathSegment.orEmpty(),
                    path = documentUri.toString(),
                    isDirectory = isDirectory,
                    size = if (isDirectory) 0L else size,
                    lastModified = lastModified,
                )
            }

            if (info != null) {
                Result.success(info)
            } else {
                Result.failure(IllegalArgumentException("File does not exist"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val documentUri = resolveDocumentUri(path)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("File does not exist")
                )

            val fileInfo = getFileInfo(path).getOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("File does not exist"))
            if (fileInfo.isDirectory) {
                return@withContext Result.failure(
                    IllegalArgumentException("Directory deletion is not supported")
                )
            }

            if (!DocumentsContract.deleteDocument(appContext.contentResolver, documentUri)) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to delete local file")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun disconnect() {
        // No connection state for SAF documents.
    }

    private fun resolveDocumentId(path: String): String? {
        if (path.isBlank()) {
            return runCatching { DocumentsContract.getTreeDocumentId(rootUri) }.getOrNull()
        }

        val uri = Uri.parse(path)
        return runCatching { DocumentsContract.getDocumentId(uri) }
            .recoverCatching { DocumentsContract.getTreeDocumentId(uri) }
            .getOrNull()
    }

    private fun resolveDocumentUri(path: String): Uri? {
        val documentId = resolveDocumentId(path) ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(rootUri, documentId)
    }

    private fun android.database.Cursor.getStringIfPresent(index: Int): String? {
        if (index < 0 || isNull(index)) return null
        return getString(index)
    }

    private fun android.database.Cursor.getLongIfPresent(index: Int): Long {
        if (index < 0 || isNull(index)) return 0L
        return getLong(index)
    }
}
