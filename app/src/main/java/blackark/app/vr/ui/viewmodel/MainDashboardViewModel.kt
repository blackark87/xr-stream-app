package blackark.app.vr.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blackark.app.vr.AppState
import blackark.app.vr.data.database.entity.AvAssetLocation
import blackark.app.vr.data.database.entity.AvLibraryAsset
import blackark.app.vr.data.database.entity.FavoriteVideo
import blackark.app.vr.data.database.entity.RecentVideo
import blackark.app.vr.data.database.entity.SavedServer
import blackark.app.vr.data.repository.AvLibraryRepository
import blackark.app.vr.data.repository.ServerRepository
import blackark.app.vr.data.repository.VideoRepository
import blackark.app.vr.network.LocalFileClient
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBConfig
import blackark.app.vr.network.SMBFileItem
import blackark.app.vr.utils.AppSettingsStore
import blackark.app.vr.utils.JvrLibraryMetadataProvider
import blackark.app.vr.utils.JvrMovieMetadata
import blackark.app.vr.utils.buildAssetKey
import blackark.app.vr.utils.buildSourceScope
import blackark.app.vr.utils.extractFileName
import blackark.app.vr.utils.extractFolderPath
import blackark.app.vr.utils.extractNormalizedCodeFromFileName
import blackark.app.vr.utils.extractVirtualGroupKey
import blackark.app.vr.utils.extractVirtualGroupPart
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

data class MainDashboardState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isLoadingFiles: Boolean = false,
    val selectedServer: SavedServer? = null,
    val currentPath: String = "",
    val pathHistory: List<String> = emptyList(),
    val errorMessage: String? = null,
    val fileViewMode: FileBrowserViewMode = FileBrowserViewMode.Thumbnail,
    val fileSortMode: FileBrowserSortMode = FileBrowserSortMode.FilenameAscending,
    val avLibrary: AvLibraryState = AvLibraryState(),
    val isHandTrackingEnabled: Boolean = false,
    val isControllerHandTrackingPromptHandled: Boolean = false,
    val isAvBackgroundIndexingEnabled: Boolean = false,
    val fileMetadataRefreshToken: Long = 0L,
)

enum class FileBrowserViewMode {
    List,
    Thumbnail
}

enum class FileBrowserSortMode {
    Newest,
    Oldest,
    FilenameAscending,
    FilenameDescending,
}

class MainDashboardViewModel(
    private val context: Context,
    private val serverRepository: ServerRepository,
    private val videoRepository: VideoRepository,
    private val avLibraryRepository: AvLibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainDashboardState(
            isHandTrackingEnabled = AppSettingsStore.isHandTrackingEnabled(
                context.applicationContext
            ),
            isControllerHandTrackingPromptHandled =
                AppSettingsStore.isControllerHandTrackingPromptHandled(
                    context.applicationContext
                ),
            isAvBackgroundIndexingEnabled = AppSettingsStore.isBackgroundIndexingEnabled(
                context.applicationContext
            )
        )
    )
    val uiState: StateFlow<MainDashboardState> = _uiState.asStateFlow()

    val servers: StateFlow<List<SavedServer>> = serverRepository.allServers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _files = MutableStateFlow<List<SMBFileItem>>(emptyList())
    val files: StateFlow<List<SMBFileItem>> = _files.asStateFlow()

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
    private var avScanJob: Job? = null
    private var avVisibleSyncJob: Job? = null
    private var avCastRepairJob: Job? = null
    private var avCastRepairSourceScope: String? = null
    private val avCastRepairAttemptedCacheKeys = mutableSetOf<String>()
    private val systemZoneId: ZoneId = ZoneId.systemDefault()

    private fun filterAndSortBrowsableFiles(fileList: List<SMBFileItem>): List<SMBFileItem> {
        val browsableFiles = fileList.filter { it.isDirectory || SMBClient.isVideoFile(it.name) }
        val nameAscendingComparator =
            compareBy<SMBFileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.name }
                .thenByDescending { it.lastModified }
        val primaryGroupFilesByKey = buildPrimaryGroupFilesByKey(browsableFiles)
        val cachedReleaseDateByLookup = mutableMapOf<Pair<String, String>, Long?>()
        val sortDateByPath = when (_uiState.value.fileSortMode) {
            FileBrowserSortMode.Newest,
            FileBrowserSortMode.Oldest -> browsableFiles.associate { file ->
                file.path to resolveSortableDateEpochDay(
                    file = file,
                    primaryGroupFilesByKey = primaryGroupFilesByKey,
                    cachedReleaseDateByLookup = cachedReleaseDateByLookup,
                )
            }

            FileBrowserSortMode.FilenameAscending,
            FileBrowserSortMode.FilenameDescending -> emptyMap()
        }
        val sortComparator = when (_uiState.value.fileSortMode) {
            FileBrowserSortMode.Newest -> compareByDescending<SMBFileItem> {
                sortDateByPath[it.path] ?: Long.MIN_VALUE
            }
                .then(nameAscendingComparator)

            FileBrowserSortMode.Oldest -> compareBy<SMBFileItem> {
                sortDateByPath[it.path] ?: Long.MIN_VALUE
            }
                .then(nameAscendingComparator)

            FileBrowserSortMode.FilenameAscending -> nameAscendingComparator
            FileBrowserSortMode.FilenameDescending -> nameAscendingComparator.reversed()
        }

        return browsableFiles
            .sortedWith(
                compareByDescending<SMBFileItem> { it.isDirectory }
                    .then(sortComparator)
            )
    }

    private fun buildPrimaryGroupFilesByKey(files: List<SMBFileItem>): Map<String, SMBFileItem> {
        return files
            .asSequence()
            .filter { !it.isDirectory && SMBClient.isVideoFile(it.name) }
            .mapNotNull { file ->
                extractVirtualGroupKey(file.name)?.let { groupKey -> groupKey to file }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )
            .filterValues { groupedFiles -> groupedFiles.size > 1 }
            .mapValues { (_, groupedFiles) ->
                groupedFiles.minWithOrNull(
                    compareBy<SMBFileItem>(
                        { extractVirtualGroupPart(it.name) ?: Int.MAX_VALUE },
                        { it.name.lowercase() }
                    )
                ) ?: groupedFiles.first()
            }
    }

    private fun resolveSortableDateEpochDay(
        file: SMBFileItem,
        primaryGroupFilesByKey: Map<String, SMBFileItem>,
        cachedReleaseDateByLookup: MutableMap<Pair<String, String>, Long?>,
    ): Long {
        val fallbackFile = if (file.isDirectory) {
            file
        } else {
            extractVirtualGroupKey(file.name)
                ?.let(primaryGroupFilesByKey::get)
                ?: file
        }

        return cachedReleaseDateEpochDay(
            file = fallbackFile,
            cachedReleaseDateByLookup = cachedReleaseDateByLookup,
        ) ?: lastModifiedToEpochDay(fallbackFile.lastModified)
        ?: Long.MIN_VALUE
    }

    private fun cachedReleaseDateEpochDay(
        file: SMBFileItem,
        cachedReleaseDateByLookup: MutableMap<Pair<String, String>, Long?>,
    ): Long? {
        if (file.isDirectory || !SMBClient.isVideoFile(file.name)) {
            return null
        }

        val normalizedCode = extractNormalizedCodeFromFileName(file.name) ?: return null
        val folderPath = extractFolderPath(file.path)
        val lookupKey = normalizedCode to folderPath

        return cachedReleaseDateByLookup.getOrPut(lookupKey) {
            JvrLibraryMetadataProvider.peekCached(
                context = context.applicationContext,
                rawCode = normalizedCode,
                folderPath = folderPath,
            )?.releaseDate?.toEpochDay()
        }
    }

    private fun lastModifiedToEpochDay(lastModified: Long): Long? {
        if (lastModified <= 0L) {
            return null
        }

        return Instant.ofEpochMilli(lastModified)
            .atZone(systemZoneId)
            .toLocalDate()
            .toEpochDay()
    }

    fun addServer(server: SavedServer) {
        viewModelScope.launch {
            serverRepository.insertServer(server)
        }
    }

    fun updateServer(server: SavedServer) {
        viewModelScope.launch {
            serverRepository.updateServer(server)
            if (_uiState.value.selectedServer?.id == server.id) {
                disconnect()
            }
        }
    }

    fun deleteServer(server: SavedServer) {
        viewModelScope.launch {
            serverRepository.deleteServer(server)
            if (_uiState.value.selectedServer?.id == server.id) {
                disconnect()
            }
        }
    }

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

    fun connectToServer(server: SavedServer) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isConnecting = true,
                errorMessage = null
            )

            try {
                smbClient?.disconnect()
                smbClient = null
                localClient?.disconnect()
                localClient = null
                avScanJob?.cancel()
                avVisibleSyncJob?.cancel()
                avCastRepairJob?.cancel()
                avCastRepairJob = null
                avCastRepairSourceScope = null
                avCastRepairAttemptedCacheKeys.clear()
                AppState.clear()

                var connectedServer = server
                val result = if (server.isLocalStorage) {
                    val localTreeUri = resolveConfiguredLocalTreeUri(server)
                    if (localTreeUri.isNullOrBlank()) {
                        Result.failure(
                            IllegalStateException("Select a local folder first.")
                        )
                    } else {
                        connectedServer = server.copy(shareName = localTreeUri)
                        val client = LocalFileClient(context, localTreeUri)
                        val connectionResult = client.connect()
                        if (connectionResult.isSuccess) {
                            localClient = client
                        } else {
                            AppSettingsStore.clearLocalStorageTreeUri(context.applicationContext)
                        }
                        connectionResult
                    }
                } else {
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
                    if (!connectedServer.isLocalStorage) {
                        serverRepository.updateLastConnected(connectedServer.id)
                    }

                    _uiState.value = _uiState.value.copy(
                        isConnecting = false,
                        isConnected = true,
                        selectedServer = connectedServer,
                        currentPath = "",
                        pathHistory = emptyList(),
                        errorMessage = null,
                        avLibrary = _uiState.value.avLibrary.copy(
                            scan = AvScanState(),
                            selectedAssetKey = null,
                        ),
                    )

                    loadFiles("")
                    refreshAvSnapshot(
                        buildSourceScope(
                            connectedServer.serverAddress,
                            connectedServer.shareName,
                        )
                    )
                    startAvLibraryIndexingIfEnabled(connectedServer)
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

    fun connectToLocalTree(treeUri: Uri) {
        val uriString = treeUri.toString()
        AppSettingsStore.setLocalStorageTreeUri(context.applicationContext, uriString)
        connectToServer(
            SavedServer.createLocalStorageServer().copy(shareName = uriString)
        )
    }

    fun switchToLocalSource(configuredTreeUri: String?) {
        val currentState = _uiState.value
        if (currentState.isConnected && currentState.selectedServer?.isLocalStorage == true) {
            return
        }

        if (currentState.isConnected) {
            disconnect()
        }

        configuredTreeUri
            ?.takeIf { it.isNotBlank() }
            ?.let { treeUri ->
                connectToServer(
                    SavedServer.createLocalStorageServer().copy(shareName = treeUri)
                )
            }
    }

    fun switchToSmbSource() {
        val currentState = _uiState.value
        if (currentState.isConnected && currentState.selectedServer?.isLocalStorage == true) {
            disconnect()
        }
    }

    fun switchToExternalSource() {
        if (_uiState.value.isConnected) {
            disconnect()
        }
    }

    fun disconnect() {
        avScanJob?.cancel()
        avScanJob = null
        avVisibleSyncJob?.cancel()
        avVisibleSyncJob = null
        avCastRepairJob?.cancel()
        avCastRepairJob = null
        avCastRepairSourceScope = null
        avCastRepairAttemptedCacheKeys.clear()
        smbClient?.disconnect()
        smbClient = null
        localClient?.disconnect()
        localClient = null
        AppState.clear()

        _uiState.value = _uiState.value.copy(
            isConnected = false,
            selectedServer = null,
            currentPath = "",
            pathHistory = emptyList(),
            avLibrary = AvLibraryState(),
            isHandTrackingEnabled = _uiState.value.isHandTrackingEnabled,
            isControllerHandTrackingPromptHandled =
                _uiState.value.isControllerHandTrackingPromptHandled,
            isAvBackgroundIndexingEnabled = _uiState.value.isAvBackgroundIndexingEnabled,
        )
        _files.value = emptyList()
    }

    fun loadFiles(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingFiles = true,
                errorMessage = null
            )

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
                    syncCurrentListingForAv(sortedFiles)
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

    fun navigateToFile(file: SMBFileItem) {
        if (!file.isDirectory) return

        val newHistory = _uiState.value.pathHistory + _uiState.value.currentPath
        _uiState.value = _uiState.value.copy(pathHistory = newHistory)

        val newPath = if (localClient != null) {
            file.path
        } else {
            val dirName = file.name.removeSuffix("/")
            if (_uiState.value.currentPath.isEmpty()) {
                dirName
            } else {
                "${_uiState.value.currentPath}/$dirName"
            }
        }

        loadFiles(newPath)
    }

    private fun resolveConfiguredLocalTreeUri(server: SavedServer): String? {
        val provided = server.shareName.takeIf { it.isNotBlank() }
        if (provided != null) {
            return provided
        }

        return AppSettingsStore.getLocalStorageTreeUri(context.applicationContext)
    }

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

    fun setFileSortMode(sortMode: FileBrowserSortMode) {
        if (_uiState.value.fileSortMode == sortMode) {
            return
        }

        _uiState.value = _uiState.value.copy(fileSortMode = sortMode)

        if (_files.value.isNotEmpty()) {
            val sortedFiles = filterAndSortBrowsableFiles(_files.value)
            _files.value = sortedFiles
            syncCurrentListingForAv(sortedFiles)
        }
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

        _uiState.value.selectedServer?.let(::startAvLibraryIndexingIfEnabled)

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

    fun clearAvFilters() {
        updateAvFilters(
            AvFilterState(
                activeFamily = AvFilterFamily.None,
                selectedVrFilter = AvVrFilterOption.All,
                visibleMonth = _uiState.value.avLibrary.filters.visibleMonth,
            )
        )
    }

    fun setAvFilterFamily(family: AvFilterFamily) {
        val current = _uiState.value.avLibrary.filters
        updateAvFilters(
            when (family) {
                AvFilterFamily.None -> current.copy(activeFamily = AvFilterFamily.None)
                AvFilterFamily.Studio -> current.copy(
                    activeFamily = AvFilterFamily.Studio,
                    selectedCastIds = emptySet(),
                    selectedReleaseDate = null,
                )

                AvFilterFamily.Casts -> current.copy(
                    activeFamily = AvFilterFamily.Casts,
                    selectedStudio = null,
                    selectedReleaseDate = null,
                )

                AvFilterFamily.ReleaseDate -> current.copy(
                    activeFamily = AvFilterFamily.ReleaseDate,
                    selectedStudio = null,
                    selectedCastIds = emptySet(),
                )
            }
        )
    }

    fun selectAvStudio(studio: String?) {
        updateAvFilters(
            _uiState.value.avLibrary.filters.copy(
                activeFamily = if (studio == null) AvFilterFamily.None else AvFilterFamily.Studio,
                selectedStudio = studio,
                selectedCastIds = emptySet(),
                selectedReleaseDate = null,
            )
        )
    }

    fun toggleAvCast(performerId: String) {
        val current = _uiState.value.avLibrary.filters
        val nextSelection = if (performerId in current.selectedCastIds) {
            current.selectedCastIds - performerId
        } else {
            current.selectedCastIds + performerId
        }
        updateAvFilters(
            current.copy(
                activeFamily = AvFilterFamily.Casts,
                selectedStudio = null,
                selectedCastIds = nextSelection,
                selectedReleaseDate = null,
            )
        )
    }

    fun selectAvReleaseDate(date: java.time.LocalDate?) {
        updateAvFilters(
            _uiState.value.avLibrary.filters.copy(
                activeFamily = if (date == null) AvFilterFamily.None else AvFilterFamily.ReleaseDate,
                selectedStudio = null,
                selectedCastIds = emptySet(),
                selectedReleaseDate = date,
                visibleMonth = date?.let(java.time.YearMonth::from)
                    ?: _uiState.value.avLibrary.filters.visibleMonth,
            )
        )
    }

    fun selectAvVrFilter(filter: AvVrFilterOption) {
        updateAvFilters(
            _uiState.value.avLibrary.filters.copy(
                selectedVrFilter = filter,
            )
        )
    }

    fun showPreviousAvMonth() {
        updateAvFilters(
            _uiState.value.avLibrary.filters.copy(
                visibleMonth = _uiState.value.avLibrary.filters.visibleMonth.minusMonths(1)
            )
        )
    }

    fun showNextAvMonth() {
        updateAvFilters(
            _uiState.value.avLibrary.filters.copy(
                visibleMonth = _uiState.value.avLibrary.filters.visibleMonth.plusMonths(1)
            )
        )
    }

    fun selectAvWork(assetKey: String?) {
        _uiState.value = _uiState.value.copy(
            avLibrary = _uiState.value.avLibrary.copy(
                selectedAssetKey = assetKey,
            )
        )
    }

    fun refreshAvLibrary() {
        val sourceScope = currentSourceScope() ?: return
        viewModelScope.launch {
            refreshAvSnapshot(sourceScope)
        }
    }

    fun mergeAvPerformers(
        sourcePerformerId: String,
        targetPerformerId: String,
    ) {
        if (sourcePerformerId.isBlank() || targetPerformerId.isBlank() || sourcePerformerId == targetPerformerId) {
            return
        }

        val sourceScope = currentSourceScope() ?: return
        viewModelScope.launch {
            JvrLibraryMetadataProvider.mergePerformersManually(
                context = context.applicationContext,
                sourcePerformerId = sourcePerformerId,
                targetPerformerId = targetPerformerId,
            )

            val currentFilters = _uiState.value.avLibrary.filters
            if (sourcePerformerId in currentFilters.selectedCastIds) {
                updateAvFilters(
                    currentFilters.copy(
                        selectedCastIds = currentFilters.selectedCastIds
                            .minus(sourcePerformerId)
                            .plus(targetPerformerId)
                    )
                )
            }

            refreshAvSnapshot(sourceScope)
        }
    }

    fun addAvPerformerAliases(
        performerId: String,
        englishName: String?,
        japaneseName: String?,
    ) {
        if (performerId.isBlank()) return

        val sourceScope = currentSourceScope() ?: return
        viewModelScope.launch {
            JvrLibraryMetadataProvider.addPerformerAliasesManually(
                context = context.applicationContext,
                performerId = performerId,
                englishName = englishName,
                japaneseName = japaneseName,
            )
            refreshAvSnapshot(sourceScope)
        }
    }

    fun saveAvWorkMetadata(
        assetKey: String,
        metadata: JvrMovieMetadata,
    ) {
        if (assetKey.isBlank()) return

        val sourceScope = currentSourceScope() ?: return
        viewModelScope.launch {
            val asset = avLibraryRepository.getAssetByKey(assetKey) ?: return@launch
            val cacheKey = asset.metadataCacheKey ?: return@launch
            val source = asset.metadataSource
                ?.takeIf { it.isNotBlank() }
                ?: cacheKey.substringBefore(':', "")
            if (source.isBlank()) return@launch

            JvrLibraryMetadataProvider.saveManualMetadata(
                context = context.applicationContext,
                cacheKey = cacheKey,
                source = source,
                metadata = metadata,
            )

            refreshAvSnapshot(sourceScope)
            bumpFileMetadataRefreshToken()
        }
    }

    fun setAvBackgroundIndexingEnabled(enabled: Boolean) {
        AppSettingsStore.setBackgroundIndexingEnabled(context.applicationContext, enabled)
        _uiState.value = _uiState.value.copy(isAvBackgroundIndexingEnabled = enabled)

        if (!enabled) {
            stopAvLibraryIndexing()
            return
        }

        _uiState.value.selectedServer?.let(::startAvLibraryIndexingIfEnabled)
    }

    fun setHandTrackingEnabled(enabled: Boolean) {
        AppSettingsStore.setHandTrackingEnabled(context.applicationContext, enabled)
        _uiState.value = _uiState.value.copy(isHandTrackingEnabled = enabled)
    }

    fun setControllerHandTrackingPromptHandled(handled: Boolean = true) {
        AppSettingsStore.setControllerHandTrackingPromptHandled(
            context.applicationContext,
            handled
        )
        _uiState.value = _uiState.value.copy(
            isControllerHandTrackingPromptHandled = handled
        )
    }

    suspend fun clearAvMetadataLinks() {
        avLibraryRepository.clearMetadataLinks()
        refreshAvLibrary()
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

    private fun updateAvFilters(filters: AvFilterState) {
        val snapshot = _uiState.value.avLibrary.snapshot
        _uiState.value = _uiState.value.copy(
            avLibrary = _uiState.value.avLibrary.copy(
                filters = filters,
                filteredWorks = snapshot.applyFilters(filters),
            )
        )
    }

    private suspend fun refreshAvSnapshot(sourceScope: String) {
        val snapshot = avLibraryRepository.loadSnapshot(sourceScope)
        val currentFilters = _uiState.value.avLibrary.filters
        val filteredWorks = snapshot.applyFilters(currentFilters)
        val selectedAssetKey = _uiState.value.avLibrary.selectedAssetKey
            ?.takeIf { key -> snapshot.works.any { it.assetKey == key } }

        _uiState.value = _uiState.value.copy(
            avLibrary = _uiState.value.avLibrary.copy(
                snapshot = snapshot,
                filteredWorks = filteredWorks,
                selectedAssetKey = selectedAssetKey,
                scan = _uiState.value.avLibrary.scan.copy(
                    discoveredWorkCount = snapshot.works.size,
                ),
            )
        )

        startAvCastRepairIfNeeded(sourceScope)
    }

    private fun startAvCastRepairIfNeeded(sourceScope: String) {
        if (avCastRepairJob?.isActive == true && avCastRepairSourceScope == sourceScope) {
            return
        }

        avCastRepairJob?.cancel()
        avCastRepairSourceScope = sourceScope
        avCastRepairJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val candidates = avLibraryRepository
                    .getPresentAssetsMissingPerformerRefs(sourceScope)
                    .filter { asset ->
                        val cacheKey = asset.metadataCacheKey
                        !cacheKey.isNullOrBlank() && cacheKey !in avCastRepairAttemptedCacheKeys
                    }

                if (candidates.isEmpty()) {
                    return@launch
                }

                var repairedAny = false
                candidates.forEach { asset ->
                    if (!isActive) return@launch

                    val cacheKey = asset.metadataCacheKey ?: return@forEach
                    avCastRepairAttemptedCacheKeys += cacheKey

                    val refreshedMetadata = JvrLibraryMetadataProvider.refreshByCacheKey(
                        context = context.applicationContext,
                        cacheKey = cacheKey,
                        fallbackFolderPath = asset.representativeFolderPath.orEmpty(),
                    )

                    if (refreshedMetadata?.casts?.isNotEmpty() == true) {
                        repairedAny = true
                    }
                }

                if (repairedAny && isActive) {
                    withContext(Dispatchers.Main) {
                        refreshAvSnapshot(sourceScope)
                    }
                }
            } catch (_: CancellationException) {
                // Ignore cancellation when switching sources or shutting down.
            } catch (e: Exception) {
                Log.w(
                    "MainDashboardViewModel",
                    "Failed to repair AV cast metadata for $sourceScope: ${e.message}"
                )
            } finally {
                if (avCastRepairSourceScope == sourceScope) {
                    avCastRepairSourceScope = null
                }
            }
        }
    }

    private fun syncCurrentListingForAv(files: List<SMBFileItem>) {
        val sourceScope = currentSourceScope() ?: return
        val visibleVideos = files.filter { !it.isDirectory && SMBClient.isVideoFile(it.name) }

        avVisibleSyncJob?.cancel()
        if (visibleVideos.isEmpty()) {
            return
        }

        avVisibleSyncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val syncedAt = System.currentTimeMillis()
                visibleVideos.forEach { file ->
                    processScannedVideo(
                        sourceScope = sourceScope,
                        file = file,
                        scanStartedAt = syncedAt,
                        allowFingerprint = false,
                        forceMetadataRefresh = true,
                    )
                }
                refreshAvSnapshot(sourceScope)
                bumpFileMetadataRefreshToken()
            } catch (_: CancellationException) {
                // Folder navigation cancels any in-flight visible listing sync.
            } catch (e: Exception) {
                Log.w("MainDashboardViewModel", "Visible AV sync failed: ${e.message}")
            }
        }
    }

    private fun stopAvLibraryIndexing() {
        avScanJob?.cancel()
        avScanJob = null
        updateScanState(
            _uiState.value.avLibrary.scan.copy(
                isRunning = false,
                currentPath = null,
                errorMessage = null,
            )
        )
    }

    private fun startAvLibraryIndexingIfEnabled(server: SavedServer) {
        if (!_uiState.value.isAvBackgroundIndexingEnabled) {
            stopAvLibraryIndexing()
            return
        }
        startAvLibraryIndexing(server)
    }

    private fun startAvLibraryIndexing(server: SavedServer) {
        avScanJob?.cancel()
        avScanJob = viewModelScope.launch {
            val sourceScope = buildSourceScope(server.serverAddress, server.shareName)
            val scanStartedAt = System.currentTimeMillis()
            updateScanState(
                AvScanState(
                    isRunning = true,
                    scannedFileCount = 0,
                    discoveredWorkCount = _uiState.value.avLibrary.snapshot.works.size,
                    errorMessage = null,
                )
            )

            var scannedFiles = 0
            var refreshCounter = 0

            try {
                val directories = ArrayDeque(resolveScanRoots())
                while (directories.isNotEmpty() && isActive) {
                    val path = directories.removeFirst()
                    updateScanState(
                        _uiState.value.avLibrary.scan.copy(
                            isRunning = true,
                            currentPath = path.ifBlank { "/" },
                            scannedFileCount = scannedFiles,
                        )
                    )

                    val listResult = listDirectoryForScan(path)
                    if (listResult.isFailure) {
                        Log.w(
                            "MainDashboardViewModel",
                            "AV scan failed for path=$path: ${listResult.exceptionOrNull()?.message}"
                        )
                        continue
                    }

                    val items = filterAndSortBrowsableFiles(listResult.getOrNull().orEmpty())
                    items.forEach { item ->
                        if (item.isDirectory) {
                            val nextPath = buildNextScanPath(path, item)
                            if (nextPath != null) {
                                directories.addLast(nextPath)
                            }
                        } else if (SMBClient.isVideoFile(item.name)) {
                            processScannedVideo(sourceScope, item, scanStartedAt)
                            scannedFiles += 1
                            refreshCounter += 1
                            if (refreshCounter >= 20) {
                                refreshCounter = 0
                                refreshAvSnapshot(sourceScope)
                                updateScanState(
                                    _uiState.value.avLibrary.scan.copy(
                                        isRunning = true,
                                        currentPath = path.ifBlank { "/" },
                                        scannedFileCount = scannedFiles,
                                        discoveredWorkCount = _uiState.value.avLibrary.snapshot.works.size,
                                    )
                                )
                            }
                        }
                    }
                }

                avLibraryRepository.markStaleLocationsMissing(sourceScope, scanStartedAt)
                refreshAvSnapshot(sourceScope)
                updateScanState(
                    _uiState.value.avLibrary.scan.copy(
                        isRunning = false,
                        currentPath = null,
                        scannedFileCount = scannedFiles,
                        discoveredWorkCount = _uiState.value.avLibrary.snapshot.works.size,
                        lastCompletedAt = System.currentTimeMillis(),
                        errorMessage = null,
                    )
                )
            } catch (_: CancellationException) {
                refreshAvSnapshot(sourceScope)
                updateScanState(
                    _uiState.value.avLibrary.scan.copy(
                        isRunning = false,
                        currentPath = null,
                        errorMessage = null,
                    )
                )
            } catch (e: Exception) {
                Log.e("MainDashboardViewModel", "AV scan failed", e)
                refreshAvSnapshot(sourceScope)
                updateScanState(
                    _uiState.value.avLibrary.scan.copy(
                        isRunning = false,
                        currentPath = null,
                        scannedFileCount = scannedFiles,
                        discoveredWorkCount = _uiState.value.avLibrary.snapshot.works.size,
                        errorMessage = e.message ?: "Scan failed",
                    )
                )
            }
        }
    }

    private suspend fun processScannedVideo(
        sourceScope: String,
        file: SMBFileItem,
        scanStartedAt: Long,
        allowFingerprint: Boolean = true,
        forceMetadataRefresh: Boolean = false,
    ) {
        val existingLocation = avLibraryRepository.getLocationByPath(file.path)
        var asset = existingLocation?.let { avLibraryRepository.getAssetByKey(it.assetKey) }
        var normalizedCode = asset?.normalizedCode ?: extractNormalizedCodeFromFileName(file.name)
        var contentFingerprint = existingLocation?.contentFingerprint

        if (asset == null && normalizedCode != null) {
            asset = avLibraryRepository.getAssetBySourceAndCode(sourceScope, normalizedCode)
        }

        if (allowFingerprint && asset == null && file.size > 0L) {
            val fingerprintMatch = findFingerprintMatch(sourceScope, file)
            if (fingerprintMatch != null) {
                asset = avLibraryRepository.getAssetByKey(fingerprintMatch.first)
                normalizedCode = asset?.normalizedCode
                contentFingerprint = fingerprintMatch.second
            }
        }

        if (normalizedCode == null) {
            return
        }

        val assetKey = asset?.assetKey ?: buildAssetKey(sourceScope, normalizedCode)
        var representativePath = chooseRepresentativePath(asset?.representativePath, file)
        var representativeFileName = if (representativePath == file.path) {
            file.name
        } else {
            asset?.representativeFileName ?: file.name
        }
        var representativeFolderPath = if (representativePath == file.path) {
            extractFolderPath(file.path)
        } else {
            asset?.representativeFolderPath ?: extractFolderPath(file.path)
        }

        if (allowFingerprint && contentFingerprint == null && file.size > 0L) {
            contentFingerprint = computeContentFingerprint(file.path, file.size)
        }

        var metadataCacheKey = asset?.metadataCacheKey
        var metadataSource = asset?.metadataSource
        var cachedTitle = asset?.cachedTitle
        var cachedPosterUrl = asset?.cachedPosterUrl
        var cachedStudio = asset?.cachedStudio
        var cachedReleaseDateEpochDay = asset?.cachedReleaseDateEpochDay
        var hasMetadata = asset?.hasMetadata ?: false
        var metadataResolvedAt = asset?.metadataResolvedAt
        val resolvedSource =
            JvrLibraryMetadataProvider.resolveMetadataSource(extractFolderPath(file.path))

        if (
            forceMetadataRefresh &&
            resolvedSource != null &&
            (!hasMetadata || metadataSource != resolvedSource)
        ) {
            val refreshedMetadata = JvrLibraryMetadataProvider.refreshByCacheKey(
                context = context.applicationContext,
                cacheKey = JvrLibraryMetadataProvider.buildMetadataCacheKey(
                    rawCode = normalizedCode,
                    source = resolvedSource,
                ),
                fallbackFolderPath = extractFolderPath(file.path),
            )

            metadataSource = resolvedSource
            metadataCacheKey = JvrLibraryMetadataProvider.buildMetadataCacheKey(
                rawCode = normalizedCode,
                source = resolvedSource,
            )

            if (refreshedMetadata != null) {
                representativePath = file.path
                representativeFileName = file.name
                representativeFolderPath = extractFolderPath(file.path)
                cachedTitle = refreshedMetadata.title
                cachedPosterUrl = refreshedMetadata.posterUrl
                cachedStudio = refreshedMetadata.studio
                cachedReleaseDateEpochDay = refreshedMetadata.releaseDate?.toEpochDay()
                hasMetadata = true
                metadataResolvedAt = scanStartedAt
            } else {
                hasMetadata = false
            }
        }

        if (shouldPromoteMetadataSource(current = metadataSource, candidate = resolvedSource)) {
            val promotedSource = resolvedSource.orEmpty()
            val promotedMetadata = JvrLibraryMetadataProvider.getByCode(
                context = context.applicationContext,
                rawCode = normalizedCode,
                folderPath = extractFolderPath(file.path),
            )
            if (promotedMetadata != null) {
                metadataSource = promotedSource
                metadataCacheKey = JvrLibraryMetadataProvider.buildMetadataCacheKey(
                    rawCode = normalizedCode,
                    source = promotedSource,
                )
                representativePath = file.path
                representativeFileName = file.name
                representativeFolderPath = extractFolderPath(file.path)
                cachedTitle = promotedMetadata.title
                cachedPosterUrl = promotedMetadata.posterUrl
                cachedStudio = promotedMetadata.studio
                cachedReleaseDateEpochDay = promotedMetadata.releaseDate?.toEpochDay()
                hasMetadata = true
                metadataResolvedAt = scanStartedAt
            }
        }

        if (metadataCacheKey.isNullOrBlank()) {
            if (resolvedSource != null) {
                metadataSource = resolvedSource
                metadataCacheKey = JvrLibraryMetadataProvider.buildMetadataCacheKey(
                    rawCode = normalizedCode,
                    source = resolvedSource,
                )
                val metadata = JvrLibraryMetadataProvider.getByCode(
                    context = context.applicationContext,
                    rawCode = normalizedCode,
                    folderPath = extractFolderPath(file.path),
                )
                if (metadata != null) {
                    cachedTitle = metadata.title
                    cachedPosterUrl = metadata.posterUrl
                    cachedStudio = metadata.studio
                    cachedReleaseDateEpochDay = metadata.releaseDate?.toEpochDay()
                    hasMetadata = true
                    metadataResolvedAt = scanStartedAt
                }
            }
        } else {
            val metadata = JvrLibraryMetadataProvider.getByCacheKey(
                context = context.applicationContext,
                cacheKey = metadataCacheKey,
            )
            if (metadata != null) {
                cachedTitle = metadata.title
                cachedPosterUrl = metadata.posterUrl
                cachedStudio = metadata.studio
                cachedReleaseDateEpochDay = metadata.releaseDate?.toEpochDay()
                hasMetadata = true
                metadataResolvedAt = metadataResolvedAt ?: scanStartedAt
            }
        }

        avLibraryRepository.upsertAsset(
            AvLibraryAsset(
                assetKey = assetKey,
                sourceScope = sourceScope,
                normalizedCode = normalizedCode,
                metadataCacheKey = metadataCacheKey,
                metadataSource = metadataSource,
                representativePath = representativePath,
                representativeFileName = representativeFileName,
                representativeFolderPath = representativeFolderPath,
                cachedTitle = cachedTitle,
                cachedPosterUrl = cachedPosterUrl,
                cachedStudio = cachedStudio,
                cachedReleaseDateEpochDay = cachedReleaseDateEpochDay,
                hasMetadata = hasMetadata,
                lastSeenAt = scanStartedAt,
                lastScannedAt = scanStartedAt,
                metadataResolvedAt = metadataResolvedAt,
            )
        )

        avLibraryRepository.upsertLocation(
            AvAssetLocation(
                filePath = file.path,
                assetKey = assetKey,
                sourceScope = sourceScope,
                fileName = file.name,
                partNumber = extractVirtualGroupPart(file.name),
                size = file.size,
                lastModified = file.lastModified,
                contentFingerprint = contentFingerprint,
                lastSeenAt = scanStartedAt,
                isPresent = true,
            )
        )
    }

    private suspend fun findFingerprintMatch(
        sourceScope: String,
        file: SMBFileItem,
    ): Pair<String, String>? {
        val candidates = avLibraryRepository.getMissingFingerprintCandidates(sourceScope, file.size)
        if (candidates.isEmpty()) {
            return null
        }

        val fingerprint = computeContentFingerprint(file.path, file.size) ?: return null
        val matched = candidates.firstOrNull { it.contentFingerprint == fingerprint } ?: return null
        return matched.assetKey to fingerprint
    }

    private fun chooseRepresentativePath(
        currentRepresentativePath: String?,
        candidate: SMBFileItem,
    ): String {
        if (currentRepresentativePath.isNullOrBlank()) {
            return candidate.path
        }

        val currentRepresentativeName = extractFileName(currentRepresentativePath)
        val currentPartNumber = extractVirtualGroupPart(currentRepresentativeName)
        val candidatePartNumber = extractVirtualGroupPart(candidate.name)

        return when {
            currentPartNumber == null && candidatePartNumber != null -> currentRepresentativePath
            currentPartNumber != null && candidatePartNumber == null -> candidate.path
            candidatePartNumber != null && currentPartNumber != null &&
                    candidatePartNumber < currentPartNumber -> candidate.path

            else -> currentRepresentativePath
        }
    }

    private fun shouldPromoteMetadataSource(
        current: String?,
        candidate: String?,
    ): Boolean {
        if (candidate.isNullOrBlank()) return false
        return metadataSourcePriority(candidate) > metadataSourcePriority(current)
    }

    private fun metadataSourcePriority(source: String?): Int {
        return when (source?.lowercase()) {
            "avwiki" -> 2
            "jvr" -> 1
            else -> 0
        }
    }

    private suspend fun resolveScanRoots(): List<String> {
        return when {
            localClient != null -> {
                val roots = localClient!!.listFiles("")
                    .getOrNull()
                    .orEmpty()
                    .filter { it.isDirectory }
                    .map { it.path }
                    .distinct()
                    .sortedBy { it.length }

                roots.filter { candidate ->
                    roots.none { other ->
                        other != candidate && candidate.startsWith("${other.trimEnd('/')}/")
                    }
                }
            }

            else -> listOf("")
        }
    }

    private suspend fun listDirectoryForScan(path: String): Result<List<SMBFileItem>> {
        return when {
            localClient != null -> localClient!!.listFiles(path)
            smbClient != null -> smbClient!!.listFiles(path)
            else -> Result.failure(IllegalStateException("No active connection"))
        }
    }

    private fun buildNextScanPath(currentPath: String, item: SMBFileItem): String? {
        return when {
            localClient != null -> item.path
            smbClient != null -> {
                if (currentPath.isBlank()) item.name else "$currentPath/${item.name}"
            }

            else -> null
        }
    }

    private suspend fun computeContentFingerprint(path: String, size: Long): String? {
        if (size <= 0L) return null

        return withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(size.toString().toByteArray())

            try {
                when {
                    path.startsWith("smb://", ignoreCase = true) && smbClient != null -> {
                        val smbFile = smbClient!!.getSmbFile(path)
                        SmbRandomAccessFile(smbFile, "r").use { file ->
                            updateDigestWithFileSamples(
                                read = { offset, buffer, length ->
                                    file.seek(offset)
                                    file.read(buffer, 0, length)
                                },
                                size = size,
                                digest = digest,
                            )
                        }
                    }

                    else -> {
                        RandomAccessFile(path, "r").use { file ->
                            updateDigestWithFileSamples(
                                read = { offset, buffer, length ->
                                    file.seek(offset)
                                    file.read(buffer, 0, length)
                                },
                                size = size,
                                digest = digest,
                            )
                        }
                    }
                }

                digest.digest().joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                Log.w(
                    "MainDashboardViewModel",
                    "Failed to compute fingerprint for $path: ${e.message}"
                )
                null
            }
        }
    }

    private fun updateDigestWithFileSamples(
        read: (Long, ByteArray, Int) -> Int,
        size: Long,
        digest: MessageDigest,
    ) {
        val firstChunk = ByteArray(minOf(64 * 1024L, size).toInt())
        val firstRead = read(0L, firstChunk, firstChunk.size)
        if (firstRead > 0) {
            digest.update(firstChunk, 0, firstRead)
        }

        val tailSize = minOf(64 * 1024L, size).toInt()
        val tailChunk = ByteArray(tailSize)
        val tailOffset = (size - tailSize).coerceAtLeast(0L)
        val tailRead = read(tailOffset, tailChunk, tailChunk.size)
        if (tailRead > 0) {
            digest.update(tailChunk, 0, tailRead)
        }
    }

    private fun updateScanState(scanState: AvScanState) {
        _uiState.value = _uiState.value.copy(
            avLibrary = _uiState.value.avLibrary.copy(
                scan = scanState,
            )
        )
    }

    private fun bumpFileMetadataRefreshToken() {
        _uiState.value = _uiState.value.copy(
            fileMetadataRefreshToken = _uiState.value.fileMetadataRefreshToken + 1L,
        )

        if (_files.value.isEmpty()) {
            return
        }

        when (_uiState.value.fileSortMode) {
            FileBrowserSortMode.Newest,
            FileBrowserSortMode.Oldest -> {
                _files.value = filterAndSortBrowsableFiles(_files.value)
            }

            FileBrowserSortMode.FilenameAscending,
            FileBrowserSortMode.FilenameDescending -> Unit
        }
    }

    private fun currentSourceScope(): String? {
        val server = _uiState.value.selectedServer ?: return null
        return buildSourceScope(server.serverAddress, server.shareName)
    }
}
