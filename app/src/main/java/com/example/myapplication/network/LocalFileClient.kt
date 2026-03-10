package com.example.myapplication.network

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Local file system client for browsing device storage
 */
class LocalFileClient(private val context: Context) {

    companion object {
        const val LOCAL_STORAGE_ADDRESS = "local://storage"
    }

    /**
     * Connect to local storage (always succeeds if storage is available)
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check if external storage is available
            val state = Environment.getExternalStorageState()
            if (Environment.MEDIA_MOUNTED == state || Environment.MEDIA_MOUNTED_READ_ONLY == state) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("External storage not available"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List files in the given directory path
     * @param path Relative path from the root directory, empty for common directories
     */
    suspend fun listFiles(path: String = ""): Result<List<SMBFileItem>> =
        withContext(Dispatchers.IO) {
            try {
                val directory = if (path.isEmpty()) {
                    // Return common root directories
                    return@withContext Result.success(getCommonDirectories())
                } else {
                    File(path)
                }

                if (!directory.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Directory does not exist: $path")
                    )
                }

                if (!directory.isDirectory) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Path is not a directory: $path")
                    )
                }

                val files = directory.listFiles()?.mapNotNull { file ->
                    try {
                        // Skip hidden files
                        if (file.isHidden) return@mapNotNull null

                        SMBFileItem(
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = file.isDirectory,
                            size = if (file.isDirectory) 0 else file.length(),
                            lastModified = file.lastModified()
                        )
                    } catch (e: Exception) {
                        println("LocalFileClient: Error accessing file: ${e.message}")
                        null // Skip files that can't be accessed
                    }
                } ?: emptyList()

                Result.success(files)
            } catch (e: Exception) {
                println("LocalFileClient: Error listing files: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Get common root directories accessible to the app
     */
    private fun getCommonDirectories(): List<SMBFileItem> {
        val directories = mutableListOf<SMBFileItem>()

        // Add Movies directory
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        if (moviesDir.exists()) {
            directories.add(
                SMBFileItem(
                    name = "Movies",
                    path = moviesDir.absolutePath,
                    isDirectory = true,
                    size = 0,
                    lastModified = moviesDir.lastModified()
                )
            )
        }

        // Add DCIM directory (camera recordings)
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        if (dcimDir.exists()) {
            directories.add(
                SMBFileItem(
                    name = "DCIM",
                    path = dcimDir.absolutePath,
                    isDirectory = true,
                    size = 0,
                    lastModified = dcimDir.lastModified()
                )
            )
        }

        // Add Downloads directory
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir.exists()) {
            directories.add(
                SMBFileItem(
                    name = "Downloads",
                    path = downloadsDir.absolutePath,
                    isDirectory = true,
                    size = 0,
                    lastModified = downloadsDir.lastModified()
                )
            )
        }

        // Add Documents directory
        val documentsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (documentsDir != null && documentsDir.exists()) {
            directories.add(
                SMBFileItem(
                    name = "Documents",
                    path = documentsDir.absolutePath,
                    isDirectory = true,
                    size = 0,
                    lastModified = documentsDir.lastModified()
                )
            )
        }

        // Add external storage root
        val externalStorageDir = Environment.getExternalStorageDirectory()
        if (externalStorageDir.exists()) {
            directories.add(
                SMBFileItem(
                    name = "Internal Storage",
                    path = externalStorageDir.absolutePath,
                    isDirectory = true,
                    size = 0,
                    lastModified = externalStorageDir.lastModified()
                )
            )
        }

        return directories
    }

    /**
     * Get input stream for a file
     */
    suspend fun getInputStream(path: String): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)

            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist: $path"))
            }

            if (file.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Path is a directory, not a file: $path"))
            }

            Result.success(file.inputStream())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get file info for a specific path
     */
    suspend fun getFileInfo(path: String): Result<SMBFileItem> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)

            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist: $path"))
            }

            val fileItem = SMBFileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0 else file.length(),
                lastModified = file.lastModified()
            )

            Result.success(fileItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)

            if (!file.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("File does not exist: $path")
                )
            }

            if (file.isDirectory) {
                return@withContext Result.failure(
                    IllegalArgumentException("Directory deletion is not supported: $path")
                )
            }

            if (!file.delete()) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to delete file: $path")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun disconnect() {
        // Nothing to disconnect for local storage
    }
}
