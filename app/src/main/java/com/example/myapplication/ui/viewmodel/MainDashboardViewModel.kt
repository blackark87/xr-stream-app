package com.example.myapplication.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.AppState
import com.example.myapplication.data.database.entity.RecentVideo
import com.example.myapplication.data.database.entity.SavedServer
import com.example.myapplication.data.repository.ServerRepository
import com.example.myapplication.data.repository.VideoRepository
import com.example.myapplication.network.LocalFileClient
import com.example.myapplication.network.SMBClient
import com.example.myapplication.network.SMBConfig
import com.example.myapplication.network.SMBFileItem
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
    val errorMessage: String? = null
)

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

    // Favorites from database - filtered by currently connected server
    val favorites: StateFlow<List<RecentVideo>> = _uiState
        .flatMapLatest { state ->
            if (state.isConnected && state.selectedServer != null) {
                videoRepository.getFavoriteVideosByServer(state.selectedServer.serverAddress)
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

    /**
     * Add a new server to the database
     */
    fun addServer(server: SavedServer) {
        viewModelScope.launch {
            serverRepository.insertServer(server)
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
     * Connect to a selected server (local or SMB)
     */
    fun connectToServer(server: SavedServer) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isConnecting = true,
                errorMessage = null
            )

            try {
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

                    // Sort: directories first, then by name
                    val sortedFiles = fileList.sortedWith(
                        compareByDescending<SMBFileItem> { it.isDirectory }
                            .thenBy { it.name.lowercase() }
                    )

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

    /**
     * Toggle favorite status for a video
     */
    fun toggleFavorite(videoId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            videoRepository.toggleFavorite(videoId, isFavorite)
        }
    }

    /**
     * Toggle favorite status by file path
     */
    fun toggleFavoriteByPath(filePath: String, isFavorite: Boolean) {
        viewModelScope.launch {
            videoRepository.toggleFavoriteByPath(filePath, isFavorite)
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
