package blackark.app.vr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBFileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FileBrowserState(
    val isLoading: Boolean = false,
    val currentPath: String = "",
    val files: List<SMBFileItem> = emptyList(),
    val pathHistory: List<String> = emptyList(),
    val error: String? = null,
    val selectedFile: SMBFileItem? = null,
    val canGoBack: Boolean = false,
    val showVideosOnly: Boolean = true
)

class FileBrowserViewModel : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    private var smbClient: SMBClient? = null

    fun setSMBClient(client: SMBClient) {
        smbClient = client
        loadFiles("")
    }

    fun loadFiles(path: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val client = smbClient
            if (client == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "No SMB client available"
                )
                return@launch
            }

            val result = client.listFiles(path)

            if (result.isSuccess) {
                val files = result.getOrNull() ?: emptyList()

                // Sort: directories first, then by name
                val sortedFiles = files.sortedWith(
                    compareByDescending<SMBFileItem> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )

                _state.value = _state.value.copy(
                    isLoading = false,
                    currentPath = path,
                    files = sortedFiles,
                    error = null
                )
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to load files"
                )
            }
        }
    }

    fun navigateToDirectory(directory: SMBFileItem) {
        if (!directory.isDirectory) return

        println("FileBrowserViewModel: Navigating to directory: ${directory.name}")
        println("FileBrowserViewModel: Current path: ${_state.value.currentPath}")

        // Add current path to history
        val newHistory = _state.value.pathHistory + _state.value.currentPath
        _state.value = _state.value.copy(
            pathHistory = newHistory,
            canGoBack = true
        )

        // Simply append the directory name to the current path
        val dirName = directory.name.removeSuffix("/")
        val fullPath = if (_state.value.currentPath.isEmpty()) {
            dirName
        } else {
            "${_state.value.currentPath}/$dirName"
        }

        println("FileBrowserViewModel: New full path: $fullPath")
        loadFiles(fullPath)
    }

    fun navigateBack(): Boolean {
        val history = _state.value.pathHistory
        if (history.isEmpty()) {
            return false // At root, can't go back
        }

        val previousPath = history.last()
        val newHistory = history.dropLast(1)

        _state.value = _state.value.copy(
            pathHistory = newHistory,
            canGoBack = newHistory.isNotEmpty()
        )
        loadFiles(previousPath)

        return true
    }

    fun selectFile(file: SMBFileItem) {
        if (file.isDirectory) {
            navigateToDirectory(file)
        } else if (SMBClient.isVideoFile(file.name)) {
            _state.value = _state.value.copy(selectedFile = file)
        }
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedFile = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun getVideoFiles(): List<SMBFileItem> {
        return _state.value.files.filter {
            !it.isDirectory && SMBClient.isVideoFile(it.name)
        }
    }

    fun toggleVideoFilter() {
        _state.value = _state.value.copy(showVideosOnly = !_state.value.showVideosOnly)
    }

    fun getFilteredFiles(): List<SMBFileItem> {
        return if (_state.value.showVideosOnly) {
            // When filter is on, show directories (for navigation) and video files only
            _state.value.files.filter { it.isDirectory || SMBClient.isVideoFile(it.name) }
        } else {
            _state.value.files
        }
    }
}
