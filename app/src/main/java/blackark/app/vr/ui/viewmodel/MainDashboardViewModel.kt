package blackark.app.vr.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blackark.app.vr.AppState
import blackark.app.vr.data.database.entity.FavoriteVideo
import blackark.app.vr.data.database.entity.RecentVideo
import blackark.app.vr.data.database.entity.SavedServer
import blackark.app.vr.data.repository.ServerRepository
import blackark.app.vr.data.repository.VideoRepository
import blackark.app.vr.network.LocalFileClient
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBConfig
import blackark.app.vr.network.SMBFileItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainDashboardState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isLoadingFiles: Boolean = false,
    val selectedServer: SavedServer? = null,
    val currentPath: String = "",
    val pathHistory: List<String> = emptyList(),
    val errorMessage: String? = null,
    val fileViewMode: FileBrowserViewMode = FileBrowserViewMode.Thumbnail
)

enum class FileBrowserViewMode {
    List,
    Thumbnail
}

class MainDashboardViewModel(
    private val context: Context,
    private val serverRepository: ServerRepository,
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainDashboardState())
    val uiState: StateFlow<MainDashboardState> = _uiState.asStateFlow()

    // Server list from database
    val servers: StateFlow<List<SavedServer>> = serverRepository.allServers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // File list from current directory
    private val _files = MutableStateFlow<List<SMBFileItem>>(emptyList())
    val files: StateFlow<List<SMBFileItem>> = _files.asStateFlow()

    // Favorites from database - filtered by currently connected source
    @OptIn(ExperimentalCoroutinesApi::class)
    val favorites: StateFlow<List<FavoriteVideo>> = _uiState
        .flatMapLatest { state ->
            if (state.isConnected && state.selectedServer != null) {
                videoRepository.getFavoriteVideosBySource(
                    serverAddress = state.selectedServer.serverAddress,
                    shareName = state.selectedServer.shareName,
                )
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Recent videos from database - filtered by currently connected source
    @OptIn(ExperimentalCoroutinesApi::class)
    val recentVideos: StateFlow<List<RecentVideo>> = _uiState
        .flatMapLatest { state ->
            if (state.isConnected && state.selectedServer != null) {
                videoRepository.getRecentVideosBySource(
                    serverAddress = state.selectedServer.serverAddress,
                    shareName = state.selectedServer.shareName,
                    limit = 1000,
                )
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var smbClient: SMBClient? = null
    private var localClient: LocalFileClient? = null

    private fun filterAndSortBrowsableFiles(fileList: List<SMBFileItem>): List<SMBFileItem> {
        return fileList
            .filter { it.isDirectory || SMBClient.isVideoFile(it.name) }
            .sortedWith(
                compareByDescending<SMBFileItem> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
    }

    /**
     * Add a new server to the database
     */
    fun addServer(server: SavedServer) {
        viewModelScope.launch {
            serverRepository.insertServer(server)
        }
    }

    /**
     * Update an existing server in the database
     */
    fun updateServer(server: SavedServer) {
        viewModelScope.launch {
            serverRepository.updateServer(server)

            // If updating the currently connected server, reconnect with new details if needed
            // For now, we'll just disconnect to be safe if credentials changed
            if (_uiState.value.selectedServer?.id == server.id) {
                disconnect()
            }
        }
    }

    /**
     * Delete a server from the database
     */
    fun deleteServer(server: SavedServer) {
        viewModelScope.launch {
            serverRepository.deleteServer(server)

            // If deleting the currently connected server, disconnect
            if (_uiState.value.selectedServer?.id == server.id) {
                disconnect()
            }
        }
    }

    /**
     * Test connection to a server without saving it
     */
    suspend fun testConnection(server: SavedServer): Result<Unit> {
        return try {
            val config = SMBConfig(
                serverAddress = server.serverAddress,
                port = server.port,
                shareName = server.shareName,
                username = server.username,
                password = server.password,
                domain = server.domain
            )

            val client = SMBClient(config)
            val result = client.connect()
            client.disconnect()
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Connect to a selected server (local or SMB)
     */
    fun connectToServer(server: SavedServer) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isConnecting = true,
                errorMessage = null
            )

            try {
                // Clear existing clients before connecting to a new one
                smbClient?.disconnect()
                smbClient = null
                localClient?.disconnect()
                localClient = null
                AppState.clear()

                val result = if (server.isLocalStorage) {
                    // Connect to local storage
                    val client = LocalFileClient(context)
                    val connectionResult = client.connect()

                    if (connectionResult.isSuccess) {
                        localClient = client
                    }
                    connectionResult
                } else {
                    // Connect to SMB server
                    val config = SMBConfig(
                        serverAddress = server.serverAddress,
                        port = server.port,
                        shareName = server.shareName,
                        username = server.username,
                        password = server.password,
                        domain = server.domain
                    )

                    val client = SMBClient(config)
                    val connectionResult = client.connect()

                    if (connectionResult.isSuccess) {
                        smbClient = client
                        AppState.setSMBClient(client, config)
                    }
                    connectionResult
                }

                if (result.isSuccess) {
                    // Update last connected time
                    serverRepository.updateLastConnected(server.id)

                    _uiState.value = _uiState.value.copy(
                        isConnecting = false,
                        isConnected = true,
                        selectedServer = server,
                        currentPath = "",
                        pathHistory = emptyList(),
                        errorMessage = null
                    )

                    // Load root directory files
                    loadFiles("")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isConnecting = false,
                        isConnected = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Connection failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    isConnected = false,
                    errorMessage = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    /**
     * Disconnect from current server (local or SMB)
     */
    fun disconnect() {
        smbClient?.disconnect()
        smbClient = null
        localClient?.disconnect()
        localClient = null
        AppState.clear()

        _uiState.value = _uiState.value.copy(
            isConnected = false,
            selectedServer = null,
            currentPath = "",
            pathHistory = emptyList()
        )
        _files.value = emptyList()
    }

    /**
     * Load files from the specified path (local or SMB)
     */
    fun loadFiles(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingFiles = true,
                errorMessage = null
            )

            // Determine which client to use
            val result = when {
                localClient != null -> localClient!!.listFiles(path)
                smbClient != null -> smbClient!!.listFiles(path)
                else -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingFiles = false,
                        errorMessage = "No active connection"
                    )
                    return@launch
                }
            }

            try {
                if (result.isSuccess) {
                    val fileList = result.getOrNull() ?: emptyList()
                    val sortedFiles = filterAndSortBrowsableFiles(fileList)

                    _files.value = sortedFiles
                    _uiState.value = _uiState.value.copy(
                        isLoadingFiles = false,
                        currentPath = path,
                        errorMessage = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingFiles = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to load files"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingFiles = false,
                    errorMessage = e.message ?: "Error loading files"
                )
            }
        }
    }

    /**
     * Navigate to a file or directory
     */
    fun navigateToFile(file: SMBFileItem) {
        if (!file.isDirectory) return

        // Add current path to history
        val newHistory = _uiState.value.pathHistory + _uiState.value.currentPath
        _uiState.value = _uiState.value.copy(pathHistory = newHistory)

        // Navigate to the directory
        val dirName = file.name.removeSuffix("/")
        val newPath = if (_uiState.value.currentPath.isEmpty()) {
            dirName
        } else {
            "${_uiState.value.currentPath}/$dirName"
        }

        loadFiles(newPath)
    }

    /**
     * Navigate back to previous directory
     */
    fun navigateBack() {
        val history = _uiState.value.pathHistory
        if (history.isEmpty()) return

        val previousPath = history.last()
        val newHistory = history.dropLast(1)

        _uiState.value = _uiState.value.copy(pathHistory = newHistory)
        loadFiles(previousPath)
    }

    fun toggleFavoriteForFile(file: SMBFileItem, currentIsFavorite: Boolean) {
        val selectedServer = _uiState.value.selectedServer ?: return

        viewModelScope.launch {
            setFavorite(
                filePath = file.path,
                fileName = file.name,
                serverAddress = selectedServer.serverAddress,
                shareName = selectedServer.shareName,
                isFavorite = !currentIsFavorite,
            )
        }
    }

    fun toggleFavoriteEntry(
        filePath: String,
        fileName: String,
        serverAddress: String,
        shareName: String,
        currentIsFavorite: Boolean,
    ) {
        viewModelScope.launch {
            setFavorite(
                filePath = filePath,
                fileName = fileName,
                serverAddress = serverAddress,
                shareName = shareName,
                isFavorite = !currentIsFavorite,
            )
        }
    }

    suspend fun removeFromRecent(video: RecentVideo) {
        videoRepository.removeFromRecent(video)
    }

    suspend fun clearRecentHistory() {
        videoRepository.clearRecentHistory()
    }

    suspend fun clearAllFavorites() {
        videoRepository.clearAllFavorites()
    }

    fun toggleFileViewMode() {
        _uiState.value = _uiState.value.copy(
            fileViewMode = when (_uiState.value.fileViewMode) {
                FileBrowserViewMode.List -> FileBrowserViewMode.Thumbnail
                FileBrowserViewMode.Thumbnail -> FileBrowserViewMode.List
            }
        )
    }

    suspend fun deleteFiles(files: List<SMBFileItem>): Result<Int> {
        if (files.isEmpty()) return Result.success(0)

        val targets = files.filterNot { it.isDirectory }
        if (targets.isEmpty()) {
            return Result.failure(IllegalArgumentException("Only files can be deleted."))
        }

        val activeLocalClient = localClient
        val activeSmbClient = smbClient
        if (activeLocalClient == null && activeSmbClient == null) {
            return Result.failure(IllegalStateException("No active connection"))
        }

        _uiState.value = _uiState.value.copy(
            isLoadingFiles = true,
            errorMessage = null
        )

        var deletedCount = 0
        val errors = mutableListOf<String>()

        for (file in targets) {
            val deleteResult = when {
                activeLocalClient != null -> activeLocalClient.deleteFile(file.path)
                activeSmbClient != null -> activeSmbClient.deleteFile(file.path)
                else -> Result.failure(IllegalStateException("No active connection"))
            }

            if (deleteResult.isSuccess) {
                deletedCount += 1
                try {
                    val existingVideo = videoRepository.getVideoByPath(file.path)
                    if (existingVideo != null) {
                        videoRepository.deleteVideoById(existingVideo.id)
                    }
                    videoRepository.removeFavoriteByPath(file.path)
                } catch (dbError: Exception) {
                    Log.w(
                        "MainDashboardViewModel",
                        "Failed to remove library entries for ${file.path}: ${dbError.message}"
                    )
                }
            } else {
                val reason = deleteResult.exceptionOrNull()?.message ?: "unknown error"
                errors += "${file.name}: $reason"
            }
        }

        val refreshResult = when {
            activeLocalClient != null -> activeLocalClient.listFiles(_uiState.value.currentPath)
            activeSmbClient != null -> activeSmbClient.listFiles(_uiState.value.currentPath)
            else -> Result.failure(IllegalStateException("No active connection"))
        }

        if (refreshResult.isSuccess) {
            val refreshedFiles = refreshResult.getOrNull().orEmpty()
            _files.value = filterAndSortBrowsableFiles(refreshedFiles)
            _uiState.value = _uiState.value.copy(
                isLoadingFiles = false,
                errorMessage = null
            )
        } else {
            _uiState.value = _uiState.value.copy(
                isLoadingFiles = false,
                errorMessage = refreshResult.exceptionOrNull()?.message ?: "Failed to refresh files"
            )
        }

        return if (errors.isEmpty()) {
            Result.success(deletedCount)
        } else {
            val detail = errors.take(3).joinToString(" | ")
            Result.failure(
                IllegalStateException(
                    "Deleted $deletedCount of ${targets.size} file(s). $detail"
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    private suspend fun setFavorite(
        filePath: String,
        fileName: String,
        serverAddress: String,
        shareName: String,
        isFavorite: Boolean,
    ) {
        if (isFavorite) {
            videoRepository.addFavorite(
                FavoriteVideo(
                    filePath = filePath,
                    fileName = fileName,
                    serverAddress = serverAddress,
                    shareName = shareName,
                )
            )
        } else {
            videoRepository.removeFavoriteByPath(filePath)
        }
    }
}
