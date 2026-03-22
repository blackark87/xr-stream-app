package blackark.app.vr.network

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Properties

data class SMBConfig(
    val serverAddress: String,
    val port: Int = 445,
    val shareName: String = "", // Optional - empty for root
    val username: String,
    val password: String,
    val domain: String = ""
)

data class SMBFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

class SMBClient(private val config: SMBConfig) {

    private var cifsContext: CIFSContext? = null

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val props = Properties().apply {
                setProperty("jcifs.smb.client.minVersion", "SMB202")
                setProperty("jcifs.smb.client.maxVersion", "SMB311")
                setProperty("jcifs.resolveOrder", "DNS")
                setProperty("jcifs.smb.client.bufferSize", "1048576")
            }

            val baseContext = BaseContext(PropertyConfiguration(props))

            val auth = NtlmPasswordAuthenticator(
                config.domain.ifEmpty { null },
                config.username,
                config.password
            )

            cifsContext = baseContext.withCredentials(auth)

            // Test connection
            val testUrl = buildSmbUrl("")
            val testFile = SmbFile(testUrl, cifsContext)
            testFile.exists() // This will throw if connection fails

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(path: String = ""): Result<List<SMBFileItem>> =
        withContext(Dispatchers.IO) {
            try {
                val context = cifsContext ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )

                val url = buildSmbUrl(path)
                println("SMBClient: Listing files at URL: $url")
                val smbFile = SmbFile(url, context)

                if (!smbFile.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Path does not exist: $path (URL: $url)")
                    )
                }

                if (!smbFile.isDirectory) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Path is not a directory: $path (URL: $url)")
                    )
                }

                val files = smbFile.listFiles()?.mapNotNull { file ->
                    try {
                        SMBFileItem(
                            name = file.name.removeSuffix("/"),
                            path = file.path,
                            isDirectory = file.isDirectory,
                            size = if (file.isDirectory) 0 else file.length(),
                            lastModified = file.lastModified()
                        )
                    } catch (e: Exception) {
                        println("SMBClient: Error accessing file: ${e.message}")
                        null // Skip files that can't be accessed
                    }
                } ?: emptyList()

                println("SMBClient: Found ${files.size} files")
                Result.success(files)
            } catch (e: Exception) {
                println("SMBClient: Error listing files: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun getInputStream(path: String): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val context = cifsContext ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )

            val url = buildSmbUrl(path)
            val smbFile = SmbFile(url, context)

            if (!smbFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist: $path"))
            }

            if (smbFile.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Path is a directory, not a file: $path"))
            }

            Result.success(smbFile.inputStream)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileInfo(path: String): Result<SMBFileItem> = withContext(Dispatchers.IO) {
        try {
            val context = cifsContext ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )

            val url = buildSmbUrl(path)
            val smbFile = SmbFile(url, context)

            if (!smbFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist: $path"))
            }

            val fileItem = SMBFileItem(
                name = smbFile.name.removeSuffix("/"),
                path = smbFile.path,
                isDirectory = smbFile.isDirectory,
                size = if (smbFile.isDirectory) 0 else smbFile.length(),
                lastModified = smbFile.lastModified()
            )

            Result.success(fileItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val context = cifsContext ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )

            val targetUrl = if (path.startsWith("smb://", ignoreCase = true)) {
                path
            } else {
                val cleanPath = path.removePrefix("/")
                if (config.shareName.isEmpty()) {
                    "smb://${config.serverAddress}:${config.port}/$cleanPath"
                } else {
                    "smb://${config.serverAddress}:${config.port}/${config.shareName}/$cleanPath"
                }
            }

            val smbFile = SmbFile(targetUrl, context)

            if (!smbFile.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("File does not exist: $path")
                )
            }

            if (smbFile.isDirectory) {
                return@withContext Result.failure(
                    IllegalArgumentException("Directory deletion is not supported: $path")
                )
            }

            smbFile.delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSmbFile(url: String): SmbFile {
        val context = cifsContext ?: throw IllegalStateException("Not connected")
        return SmbFile(url, context)
    }

    private fun buildSmbUrl(path: String): String {
        val cleanPath = path.removePrefix("/").removeSuffix("/")
        val pathPart = if (cleanPath.isNotEmpty()) "/$cleanPath/" else "/"

        // If shareName is empty, connect to root (list shares)
        // Otherwise connect to the specified share
        return if (config.shareName.isEmpty()) {
            "smb://${config.serverAddress}:${config.port}$pathPart"
        } else {
            "smb://${config.serverAddress}:${config.port}/${config.shareName}$pathPart"
        }
    }

    fun disconnect() {
        cifsContext = null
    }

    companion object {
        fun isVideoFile(fileName: String): Boolean {
            val videoExtensions = setOf(
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm",
                "m4v", "mpg", "mpeg", "3gp", "ts", "m2ts"
            )
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return extension in videoExtensions
        }
    }
}
