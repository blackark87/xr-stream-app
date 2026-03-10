package com.example.myapplication.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterOffsetType
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.MovePolicy
import androidx.xr.compose.subspace.SpatialMainPanel
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.width
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.example.myapplication.BuildConfig
import com.example.myapplication.R
import com.example.myapplication.data.database.entity.RecentVideo
import com.example.myapplication.data.database.entity.SavedServer
import com.example.myapplication.network.SMBClient
import com.example.myapplication.network.SMBFileItem
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.FancyFileCard
import com.example.myapplication.ui.components.FancyMovieCard
import com.example.myapplication.ui.components.FancyServerCard
import com.example.myapplication.ui.components.LocalStorageCard
import com.example.myapplication.ui.navigation.Screen
import com.example.myapplication.ui.theme.CardBackground
import com.example.myapplication.ui.theme.DividerGray
import com.example.myapplication.ui.theme.ErrorRed
import com.example.myapplication.ui.theme.GradientEnd
import com.example.myapplication.ui.theme.GradientStart
import com.example.myapplication.ui.theme.NetflixRed
import com.example.myapplication.ui.theme.StreamingBlack
import com.example.myapplication.ui.theme.SuccessGreen
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.ui.theme.TextTertiary
import com.example.myapplication.ui.viewmodel.FileBrowserViewMode
import com.example.myapplication.ui.viewmodel.MainDashboardViewModel
import com.example.myapplication.utils.JvrLibraryMetadataProvider
import com.example.myapplication.utils.JvrMovieMetadata
import com.example.myapplication.utils.ServerCredentialAutofillStore
import com.example.myapplication.utils.ThumbnailImageLoaderProvider
import com.example.myapplication.utils.VideoThumbnailFetcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main Dashboard Screen - Netflix/Disney+ style 3-panel layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    navController: NavController,
    viewModel: MainDashboardViewModel
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recentVideos by viewModel.recentVideos.collectAsStateWithLifecycle()

    var showAddServerDialog by remember { mutableStateOf(false) }
    var serverToEdit by remember { mutableStateOf<SavedServer?>(null) }


    Subspace {
        // Keep the dashboard window movable and slightly larger in spatial mode.
        SpatialMainPanel(
            modifier = SubspaceModifier
                .width(1920.dp)
                .height(1080.dp),
            dragPolicy = MovePolicy()
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Top App Bar
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = NetflixRed,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "XR Stream",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StreamingBlack
                ),
                actions = {
                    // Connection status indicator
                    if (uiState.isConnected) {
                        Surface(
                            color = SuccessGreen,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = StreamingBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Connected",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StreamingBlack
                                )
                            }
                        }
                    }
                }
            )

            // Three-Panel Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // LEFT PANEL: Server List
                ServerListPanel(
                    servers = servers,
                    selectedServer = uiState.selectedServer,
                    isConnecting = uiState.isConnecting,
                    onServerClick = { server ->
                        viewModel.connectToServer(server)
                    },
                    onServerEdit = { server ->
                        serverToEdit = server
                    },
                    onServerDelete = { server ->
                        viewModel.deleteServer(server)
                    },
                    onAddServerClick = {
                        showAddServerDialog = true
                    },
                    modifier = Modifier
                        .weight(0.22f) // Shrink side panel to prioritize thumbnails
                        .fillMaxHeight()
                )

                Spacer(modifier = Modifier.width(16.dp))

                // CENTER PANEL: File Browser
                FileBrowserPanel(
                    files = files,
                    recentVideos = recentVideos,
                    currentPath = uiState.currentPath,
                    isConnected = uiState.isConnected,
                    isLoading = uiState.isLoadingFiles,
                    errorMessage = uiState.errorMessage,
                    viewMode = uiState.fileViewMode,
                    onFileClick = { file ->
                        viewModel.navigateToFile(file)
                    },
                    onBackClick = {
                        viewModel.navigateBack()
                    },
                    onToggleViewMode = {
                        viewModel.toggleFileViewMode()
                    },
                    onDeleteFiles = { selectedFiles ->
                        viewModel.deleteFiles(selectedFiles)
                    },
                    onPlayVideo = { filePath, fileName ->
                        navController.navigate(Screen.VideoPlayer.createRoute(filePath, fileName))
                    },
                    onFavoriteToggle = { file, isFavorite ->
                        viewModel.toggleFavoriteForFile(file, isFavorite)
                    },
                    modifier = Modifier
                        .weight(0.56f) // Wider center panel for larger thumbnails
                        .fillMaxHeight()
                )

                Spacer(modifier = Modifier.width(16.dp))

                // RIGHT PANEL: Favorites
                FavoritesPanel(
                    favorites = favorites,
                    isConnected = uiState.isConnected,
                    onFavoriteClick = { video ->
                        navController.navigate(
                            Screen.VideoPlayer.createRoute(
                                video.filePath,
                                video.fileName
                            )
                        )
                    },
                    onFavoriteToggle = { video ->
                        viewModel.toggleFavorite(video.id, !video.isFavorite)
                    },
                    modifier = Modifier
                        .weight(0.22f) // Shrink side panel to prioritize thumbnails
                        .fillMaxHeight()
                )
            }
        }
        Orbiter(
            position = ContentEdge.Top,
            offset = 66.dp,
            offsetType = OrbiterOffsetType.OuterEdge,
            alignment = Alignment.CenterHorizontally,
            elevation = 24.dp,
            shouldRenderInNonSpatial = true,
        ) {
            DashboardOrbitArea(
                onMinimize = {
                    activity?.moveTaskToBack(true)
                },
                onClose = {
                    activity?.finishAndRemoveTask()
                    activity?.finishAffinity()
                }
            )
        }

        // Add/Edit Server Dialog
        if (showAddServerDialog || serverToEdit != null) {
            AddServerDialog(
                initialServer = serverToEdit,
                defaultUsername = servers
                    .asSequence()
                    .filterNot { it.isLocalStorage }
                    .maxByOrNull { it.lastConnected }
                    ?.username
                    .orEmpty(),
                defaultPassword = servers
                    .asSequence()
                    .filterNot { it.isLocalStorage }
                    .maxByOrNull { it.lastConnected }
                    ?.password
                    .orEmpty(),
                onDismiss = {
                    showAddServerDialog = false
                    serverToEdit = null
                },
                onSave = { server ->
                    if (serverToEdit != null) {
                        viewModel.updateServer(server)
                    } else {
                        viewModel.addServer(server)
                    }
                    showAddServerDialog = false
                    serverToEdit = null
                },
                onTestConnection = { server ->
                    viewModel.testConnection(server)
                }
            )
        }

        // Loading Overlay
        if (uiState.isConnecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StreamingBlack.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = CardBackground
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = NetflixRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Connecting to server...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardOrbitArea(
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = StreamingBlack.copy(alpha = 0.90f),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "App Icon",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(30.dp)
                )
            }

            OrbiterActionButton(
                imageVector = Icons.Filled.Minimize,
                contentDescription = "Minimize",
                onClick = onMinimize
            )

            OrbiterActionButton(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose
            )
        }
    }
}

@Composable
private fun OrbiterActionButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isActivePointer = isHovered || isFocused

    val iconTint = when {
        isPressed -> NetflixRed
        isActivePointer -> NetflixRed.copy(alpha = 0.85f)
        else -> TextPrimary
    }

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(44.dp)
            .hoverable(interactionSource = interactionSource)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private data class VirtualVideoGroup(
    val key: String,
    val files: List<SMBFileItem>
) {
    val fileCount: Int
        get() = files.size

    val representativeFile: SMBFileItem?
        get() = files.firstOrNull()
}

private sealed interface FileBrowserDisplayItem {
    val stableKey: String

    data class Entry(
        val file: SMBFileItem,
    ) : FileBrowserDisplayItem {
        override val stableKey: String = "file:${file.path}"
    }

    data class Group(
        val virtualGroup: VirtualVideoGroup,
    ) : FileBrowserDisplayItem {
        override val stableKey: String = "group:${virtualGroup.key}"
    }
}

private const val GROUP_THUMBNAIL_LOG_TAG = "VirtualGroupThumb"
private const val FILE_THUMBNAIL_LOG_TAG = "VideoFileThumb"

private data class VideoMetadataLookupRequest(
    val code: String,
    val folderPath: String,
)

private val videoFileCodePattern = Regex("(?i)([a-z]{2,10})[-_](\\d{2,5})(?!\\d)")
private val makerYearFolderPattern =
    Regex("(^|/)maker/(?:19|20)\\d{2}(/|$)", RegexOption.IGNORE_CASE)
private val avVrFolderPattern = Regex("(^|/)av/vr(/|$)", RegexOption.IGNORE_CASE)

private fun buildVideoMetadataLookupRequest(filePath: String): VideoMetadataLookupRequest? {
    val normalizedPath = filePath
        .substringBefore('?')
        .substringBefore('#')
        .replace('\\', '/')
        .trim()

    if (normalizedPath.isBlank()) return null

    val folderPath = normalizedPath.substringBeforeLast('/', "")

    val fileName = normalizedPath.substringAfterLast('/')
    val stem = fileName.substringBeforeLast('.', fileName)
    val codeMatch = videoFileCodePattern.find(stem) ?: return null

    val code = "${codeMatch.groupValues[1].uppercase()}-${codeMatch.groupValues[2]}"
    return VideoMetadataLookupRequest(code = code, folderPath = folderPath)
}

@Composable
private fun rememberVideoFileMetadata(file: SMBFileItem, isVideoFile: Boolean): JvrMovieMetadata? {
    if (!isVideoFile) return null

    val context = LocalContext.current.applicationContext
    val lookupRequest = remember(file.path) {
        buildVideoMetadataLookupRequest(file.path)
    } ?: return null

    val cachedMetadata = remember(context, lookupRequest.code, lookupRequest.folderPath) {
        JvrLibraryMetadataProvider.peekCached(context, lookupRequest.code, lookupRequest.folderPath)
    }

    val metadataState = produceState<JvrMovieMetadata?>(
        initialValue = cachedMetadata,
        key1 = lookupRequest.code,
        key2 = lookupRequest.folderPath,
    ) {
        if (cachedMetadata != null) {
            Log.d(
                FILE_THUMBNAIL_LOG_TAG,
                "Metadata immediate cache hit for file=${file.path} code=${lookupRequest.code}"
            )
            return@produceState
        }

        Log.d(
            FILE_THUMBNAIL_LOG_TAG,
            "Resolving metadata for file=${file.path} code=${lookupRequest.code} path='${lookupRequest.folderPath}'"
        )

        value = JvrLibraryMetadataProvider.getByCode(
            context = context,
            rawCode = lookupRequest.code,
            folderPath = lookupRequest.folderPath,
        )
    }

    return metadataState.value
}

@Composable
private fun rememberGroupMetadata(groupCode: String, folderPath: String): JvrMovieMetadata? {
    val context = LocalContext.current.applicationContext
    val normalizedCode = remember(groupCode) { groupCode.trim().uppercase() }
    val normalizedFolderPath = remember(folderPath) { folderPath.trim() }
    val cachedMetadata = remember(context, normalizedCode, normalizedFolderPath) {
        JvrLibraryMetadataProvider.peekCached(context, normalizedCode, normalizedFolderPath)
    }

    val metadataState = produceState<JvrMovieMetadata?>(
        initialValue = cachedMetadata,
        key1 = normalizedCode,
        key2 = normalizedFolderPath,
    ) {
        if (cachedMetadata != null) {
            Log.d(
                GROUP_THUMBNAIL_LOG_TAG,
                "Metadata immediate cache hit for group=$normalizedCode path='$normalizedFolderPath' (title='${cachedMetadata.title}')"
            )
            return@produceState
        }

        Log.d(
            GROUP_THUMBNAIL_LOG_TAG,
            "Resolving metadata for virtual group code=$normalizedCode path='$normalizedFolderPath'"
        )
        val metadata = JvrLibraryMetadataProvider.getByCode(
            context = context,
            rawCode = normalizedCode,
            folderPath = normalizedFolderPath,
        )

        if (metadata == null) {
            Log.d(
                GROUP_THUMBNAIL_LOG_TAG,
                "No remote metadata for group=$normalizedCode. Falling back to generated video thumbnail."
            )
        } else {
            Log.d(
                GROUP_THUMBNAIL_LOG_TAG,
                "Metadata loaded for group=$normalizedCode posterUrl=${metadata.posterUrl ?: "<none>"}"
            )
        }

        value = metadata
    }
    return metadataState.value
}

/**
 * LEFT PANEL: Server List
 */
@Composable
private fun ServerListPanel(
    servers: List<SavedServer>,
    selectedServer: SavedServer?,
    isConnecting: Boolean,
    onServerClick: (SavedServer) -> Unit,
    onServerEdit: (SavedServer) -> Unit,
    onServerDelete: (SavedServer) -> Unit,
    onAddServerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = StreamingBlack
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Panel Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Servers",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                IconButton(
                    onClick = onAddServerClick,
                    enabled = !isConnecting
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Server",
                        tint = NetflixRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(16.dp))

            // Server List with Local Storage option
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Local Storage card (always first)
                item {
                    LocalStorageCard(
                        onClick = { onServerClick(SavedServer.createLocalStorageServer()) },
                        isSelected = selectedServer?.isLocalStorage == true,
                        isConnecting = isConnecting
                    )
                }

                // SMB servers
                if (servers.isNotEmpty()) {
                    items(servers) { server ->
                        FancyServerCard(
                            server = server,
                            onClick = { onServerClick(server) },
                            onEdit = { onServerEdit(server) },
                            onDelete = { onServerDelete(server) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * CENTER PANEL: File Browser
 */
@Composable
private fun FileBrowserPanel(
    files: List<SMBFileItem>,
    recentVideos: List<RecentVideo>,
    currentPath: String,
    isConnected: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    viewMode: FileBrowserViewMode,
    onFileClick: (SMBFileItem) -> Unit,
    onBackClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onDeleteFiles: suspend (List<SMBFileItem>) -> Result<Int>,
    onPlayVideo: (String, String) -> Unit,
    onFavoriteToggle: (SMBFileItem, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isDeleteMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeleteInProgress by remember { mutableStateOf(false) }
    var activeVirtualGroupKey by remember(currentPath) { mutableStateOf<String?>(null) }

    val virtualGroups = remember(files) { buildVirtualVideoGroups(files) }
    val activeVirtualGroup = remember(virtualGroups, activeVirtualGroupKey) {
        activeVirtualGroupKey?.let(virtualGroups::get)
    }
    val displayItems = remember(files, virtualGroups, activeVirtualGroupKey) {
        buildFileBrowserDisplayItems(files, virtualGroups, activeVirtualGroupKey)
    }
    val displayedPath = remember(currentPath, activeVirtualGroup) {
        activeVirtualGroup?.let { group ->
            if (currentPath.isBlank() || currentPath == "/") {
                group.key
            } else {
                "$currentPath/${group.key}"
            }
        } ?: currentPath
    }

    LaunchedEffect(displayItems, isDeleteMode) {
        selectedPaths = if (isDeleteMode) {
            val currentPaths = displayItems
                .mapNotNull { item ->
                    when (item) {
                        is FileBrowserDisplayItem.Entry -> item.file.path
                        is FileBrowserDisplayItem.Group -> null
                    }
                }
                .toSet()
            selectedPaths.filter { it in currentPaths }.toSet()
        } else {
            emptySet()
        }
    }

    LaunchedEffect(virtualGroups, activeVirtualGroupKey) {
        if (activeVirtualGroupKey != null && activeVirtualGroupKey !in virtualGroups.keys) {
            activeVirtualGroupKey = null
        }
    }

    fun toggleSelection(file: SMBFileItem) {
        if (file.isDirectory) return
        selectedPaths = if (selectedPaths.contains(file.path)) {
            selectedPaths - file.path
        } else {
            selectedPaths + file.path
        }
    }

    fun openVirtualGroup(groupKey: String) {
        activeVirtualGroupKey = groupKey
        selectedPaths = emptySet()
        isDeleteMode = false
    }

    fun handleBackAction() {
        if (activeVirtualGroupKey != null) {
            activeVirtualGroupKey = null
            selectedPaths = emptySet()
            isDeleteMode = false
            return
        }
        onBackClick()
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = StreamingBlack
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Panel Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Files",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    when {
                        isDeleteMode -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${selectedPaths.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedPaths.isEmpty()) TextTertiary else NetflixRed,
                                maxLines = 1
                            )
                        }

                        isConnected && displayedPath.isNotEmpty() -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = displayedPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onToggleViewMode,
                        enabled = isConnected && !isLoading && !isDeleteMode && !isDeleteInProgress
                    ) {
                        Icon(
                            imageVector = if (viewMode == FileBrowserViewMode.List) {
                                Icons.Filled.ViewModule
                            } else {
                                Icons.AutoMirrored.Filled.ViewList
                            },
                            contentDescription = if (viewMode == FileBrowserViewMode.List) {
                                "Switch to thumbnail view"
                            } else {
                                "Switch to list view"
                            },
                            tint = if (isDeleteMode) TextTertiary else TextSecondary
                        )
                    }

                    IconButton(
                        onClick = {
                            if (!isDeleteMode) {
                                isDeleteMode = true
                                selectedPaths = emptySet()
                                return@IconButton
                            }

                            if (selectedPaths.isEmpty()) {
                                isDeleteMode = false
                                return@IconButton
                            }

                            showDeleteConfirmDialog = true
                        },
                        enabled = isConnected && !isLoading && !isDeleteInProgress
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = if (isDeleteMode) {
                                "Confirm selected file deletion"
                            } else {
                                "Select files to delete"
                            },
                            tint = if (isDeleteMode) NetflixRed else TextSecondary
                        )
                    }

                    if ((activeVirtualGroup != null || (currentPath.isNotEmpty() && currentPath != "/")) && !isDeleteMode) {
                        IconButton(onClick = ::handleBackAction) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(16.dp))

            // Content
            when {
                !isConnected -> {
                    EmptyState(
                        icon = Icons.Filled.Cloud,
                        message = "Connect to a server to browse files"
                    )
                }

                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = NetflixRed,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                displayItems.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.FolderOpen,
                        message = "No files in this directory"
                    )
                }

                else -> {
                    when (viewMode) {
                        FileBrowserViewMode.List -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = displayItems,
                                    key = { item -> item.stableKey }
                                ) { item ->
                                    when (item) {
                                        is FileBrowserDisplayItem.Group -> {
                                            val groupMetadata =
                                                rememberGroupMetadata(
                                                    item.virtualGroup.key,
                                                    currentPath
                                                )
                                            VirtualGroupListCard(
                                                group = item.virtualGroup,
                                                metadata = groupMetadata,
                                                onClick = {
                                                    if (!isDeleteMode) {
                                                        openVirtualGroup(item.virtualGroup.key)
                                                    }
                                                }
                                            )
                                        }

                                        is FileBrowserDisplayItem.Entry -> {
                                            val file = item.file
                                            val isVideoFile =
                                                !file.isDirectory && SMBClient.isVideoFile(file.name)
                                            val videoInDb =
                                                recentVideos.find { it.filePath == file.path }
                                            val isFavorite = videoInDb?.isFavorite ?: false
                                            val isSelected = selectedPaths.contains(file.path)

                                            FileListEntryCard(
                                                file = file,
                                                isVideoFile = isVideoFile,
                                                isFavorite = isFavorite,
                                                isSelectionMode = isDeleteMode,
                                                isSelected = isSelected,
                                                onFavoriteToggle = if (isDeleteMode || !isVideoFile) {
                                                    null
                                                } else {
                                                    { onFavoriteToggle(file, isFavorite) }
                                                },
                                                onClick = {
                                                    if (isDeleteMode) {
                                                        toggleSelection(file)
                                                    } else if (isVideoFile) {
                                                        onPlayVideo(file.path, file.name)
                                                    } else {
                                                        onFileClick(file)
                                                    }
                                                },
                                                modifier = Modifier
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        FileBrowserViewMode.Thumbnail -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = displayItems,
                                    key = { item -> item.stableKey }
                                ) { item ->
                                    when (item) {
                                        is FileBrowserDisplayItem.Group -> {
                                            val groupMetadata =
                                                rememberGroupMetadata(
                                                    item.virtualGroup.key,
                                                    currentPath
                                                )
                                            VirtualGroupThumbnailCard(
                                                group = item.virtualGroup,
                                                metadata = groupMetadata,
                                                onClick = {
                                                    if (!isDeleteMode) {
                                                        openVirtualGroup(item.virtualGroup.key)
                                                    }
                                                }
                                            )
                                        }

                                        is FileBrowserDisplayItem.Entry -> {
                                            val file = item.file
                                            val isVideoFile =
                                                !file.isDirectory && SMBClient.isVideoFile(file.name)
                                            val videoInDb =
                                                recentVideos.find { it.filePath == file.path }
                                            val isFavorite = videoInDb?.isFavorite ?: false
                                            val isSelected = selectedPaths.contains(file.path)

                                            FileThumbnailCard(
                                                file = file,
                                                isVideoFile = isVideoFile,
                                                isFavorite = isFavorite,
                                                isSelectionMode = isDeleteMode,
                                                isSelected = isSelected,
                                                onFavoriteToggle = if (isDeleteMode || !isVideoFile) {
                                                    null
                                                } else {
                                                    { onFavoriteToggle(file, isFavorite) }
                                                },
                                                onClick = {
                                                    if (isDeleteMode) {
                                                        toggleSelection(file)
                                                    } else if (isVideoFile) {
                                                        onPlayVideo(file.path, file.name)
                                                    } else {
                                                        onFileClick(file)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleteInProgress) {
                    showDeleteConfirmDialog = false
                }
            },
            title = {
                Text("Delete selected files?")
            },
            text = {
                Text("This will permanently delete ${selectedPaths.size} file(s).")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targets =
                            files.filter { !it.isDirectory && selectedPaths.contains(it.path) }
                        if (targets.isEmpty()) {
                            isDeleteMode = false
                            selectedPaths = emptySet()
                            showDeleteConfirmDialog = false
                            return@TextButton
                        }

                        scope.launch {
                            isDeleteInProgress = true
                            val result = onDeleteFiles(targets)
                            isDeleteInProgress = false
                            showDeleteConfirmDialog = false

                            if (result.isSuccess) {
                                isDeleteMode = false
                                selectedPaths = emptySet()
                            }
                        }
                    },
                    enabled = !isDeleteInProgress
                ) {
                    if (isDeleteInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NetflixRed
                        )
                    } else {
                        Text("Delete", color = NetflixRed)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !isDeleteInProgress
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

private data class GroupHoverPreviewSpec(
    val model: Any,
    val diskCacheKey: String?,
    val source: String,
)

private fun buildGroupHoverPreviewSpec(
    shouldUsePoster: Boolean,
    posterUrl: String?,
    posterCacheKey: String?,
    representativePath: String?,
): GroupHoverPreviewSpec? {
    return when {
        shouldUsePoster && !posterUrl.isNullOrBlank() -> GroupHoverPreviewSpec(
            model = posterUrl,
            diskCacheKey = posterCacheKey,
            source = "poster",
        )

        !representativePath.isNullOrBlank() -> GroupHoverPreviewSpec(
            model = VideoThumbnailFetcher.Model(representativePath),
            diskCacheKey = representativePath,
            source = "generated",
        )

        else -> null
    }
}


private object CenteredPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        return IntOffset(
            x = (windowSize.width - popupContentSize.width) / 2,
            y = (windowSize.height - popupContentSize.height) / 2,
        )
    }
}

@Composable
private fun GroupHoverPreviewPopup(
    show: Boolean,
    groupKey: String,
    title: String,
    previewSpec: GroupHoverPreviewSpec?,
    interactionSource: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null,
) {
    if (!show || previewSpec == null) return

    val context = LocalContext.current
    val requestBuilder = ImageRequest.Builder(context)
        .data(previewSpec.model)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)

    if (!previewSpec.diskCacheKey.isNullOrBlank()) {
        requestBuilder.diskCacheKey(previewSpec.diskCacheKey)
    }

    Popup(
        popupPositionProvider = CenteredPopupPositionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        )
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = StreamingBlack.copy(alpha = 0.96f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.45f)),
            modifier = Modifier
                .width(520.dp)
                .padding(10.dp)
                .let { base ->
                    if (interactionSource != null) {
                        base.hoverable(interactionSource = interactionSource)
                    } else {
                        base
                    }
                }
                .let { base ->
                    if (onClick != null) {
                        base.clickable(onClick = onClick)
                    } else {
                        base
                    }
                }
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(800f / 540f)
                        .background(Color.Black.copy(alpha = 0.24f)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = requestBuilder.build(),
                        imageLoader = ThumbnailImageLoaderProvider.get(context),
                        contentDescription = "Expanded poster preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$groupKey · ${previewSpec.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun VirtualGroupListCard(
    group: VirtualVideoGroup,
    metadata: JvrMovieMetadata?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = ThumbnailImageLoaderProvider.get(context)

    val displayTitle = metadata?.title ?: group.key
    val secondaryText = if (metadata != null) {
        "${group.key} · ${group.fileCount} part files"
    } else {
        "${group.fileCount} part files"
    }

    val posterUrl = metadata?.posterUrl
    val representativePath = group.representativeFile?.path
    val posterCacheKey = posterUrl?.let { buildGroupPosterCacheKey(group.key, it) }

    var posterLoadFailed by remember(group.key, posterUrl) { mutableStateOf(false) }
    val shouldUsePoster = !posterUrl.isNullOrBlank() && !posterLoadFailed
    val previewSpec = remember(shouldUsePoster, posterUrl, posterCacheKey, representativePath) {
        buildGroupHoverPreviewSpec(
            shouldUsePoster = shouldUsePoster,
            posterUrl = posterUrl,
            posterCacheKey = posterCacheKey,
            representativePath = representativePath,
        )
    }

    val previewInteractionSource = remember { MutableInteractionSource() }
    val previewPopupInteractionSource = remember { MutableInteractionSource() }
    val isPreviewHovered by previewInteractionSource.collectIsHoveredAsState()
    val isPreviewFocused by previewInteractionSource.collectIsFocusedAsState()
    val isPreviewPopupHovered by previewPopupInteractionSource.collectIsHoveredAsState()
    var showPreviewPopup by remember(group.key, previewSpec) { mutableStateOf(false) }

    LaunchedEffect(previewSpec, isPreviewHovered, isPreviewFocused, isPreviewPopupHovered) {
        if (previewSpec == null) {
            showPreviewPopup = false
            return@LaunchedEffect
        }

        if (isPreviewHovered || isPreviewFocused || isPreviewPopupHovered) {
            if (!showPreviewPopup) {
                delay(500)
                if (isPreviewHovered || isPreviewFocused || isPreviewPopupHovered) {
                    showPreviewPopup = true
                }
            }
            return@LaunchedEffect
        }

        delay(120)
        if (!isPreviewHovered && !isPreviewFocused && !isPreviewPopupHovered) {
            showPreviewPopup = false
        }
    }

    LaunchedEffect(group.key, posterUrl, representativePath, posterLoadFailed, showPreviewPopup) {
        val source = when {
            shouldUsePoster -> "remote-poster"
            !representativePath.isNullOrBlank() -> "generated-video-frame"
            else -> "none"
        }

        val posterCached = if (!posterCacheKey.isNullOrBlank()) {
            val snapshot = imageLoader.diskCache?.openSnapshot(posterCacheKey)
            val exists = snapshot != null
            snapshot?.close()
            exists
        } else {
            false
        }

        Log.d(
            GROUP_THUMBNAIL_LOG_TAG,
            "Render group=${group.key} source=$source posterFailed=$posterLoadFailed previewPopup=$showPreviewPopup posterUrl=${posterUrl ?: "<none>"} posterCacheKey=${posterCacheKey ?: "<none>"} posterCached=$posterCached representativePath=${representativePath ?: "<none>"}"
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp, 60.dp)
                    .background(DividerGray)
                    .hoverable(interactionSource = previewInteractionSource),
                contentAlignment = Alignment.Center
            ) {
                when {
                    shouldUsePoster -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(posterUrl)
                                .diskCacheKey(posterCacheKey)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .listener(
                                    onSuccess = { _, _ ->
                                        Log.d(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Poster load success for group=${group.key}, cacheKey=${posterCacheKey ?: "<none>"}"
                                        )
                                    },
                                    onError = { _, result ->
                                        Log.w(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Poster load failed for group=${group.key}, url=$posterUrl, reason=${result.throwable.message}. Falling back to generated thumbnail."
                                        )
                                        posterLoadFailed = true
                                    }
                                )
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Poster thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    !representativePath.isNullOrBlank() -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(VideoThumbnailFetcher.Model(representativePath))
                                .diskCacheKey(representativePath)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .listener(
                                    onSuccess = { _, _ ->
                                        Log.d(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Generated thumbnail success for group=${group.key}, path=$representativePath"
                                        )
                                    },
                                    onError = { _, result ->
                                        Log.e(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Generated thumbnail failed for group=${group.key}, path=$representativePath, reason=${result.throwable.message}"
                                        )
                                    }
                                )
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Generated video thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    else -> {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open grouped files",
                tint = TextSecondary
            )
        }

        GroupHoverPreviewPopup(
            show = showPreviewPopup,
            groupKey = group.key,
            title = displayTitle,
            previewSpec = previewSpec,
            interactionSource = previewPopupInteractionSource,
            onClick = onClick,
        )
    }
}

@Composable
private fun VirtualGroupThumbnailCard(
    group: VirtualVideoGroup,
    metadata: JvrMovieMetadata?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = ThumbnailImageLoaderProvider.get(context)

    val displayTitle = metadata?.title ?: group.key
    val secondaryText = if (metadata != null) {
        "${group.key} · ${group.fileCount} part files"
    } else {
        "${group.fileCount} part files"
    }

    val posterUrl = metadata?.posterUrl
    val representativePath = group.representativeFile?.path
    val posterCacheKey = posterUrl?.let { buildGroupPosterCacheKey(group.key, it) }

    var posterLoadFailed by remember(group.key, posterUrl) { mutableStateOf(false) }
    val shouldUsePoster = !posterUrl.isNullOrBlank() && !posterLoadFailed
    val previewSpec = remember(shouldUsePoster, posterUrl, posterCacheKey, representativePath) {
        buildGroupHoverPreviewSpec(
            shouldUsePoster = shouldUsePoster,
            posterUrl = posterUrl,
            posterCacheKey = posterCacheKey,
            representativePath = representativePath,
        )
    }

    val previewInteractionSource = remember { MutableInteractionSource() }
    val previewPopupInteractionSource = remember { MutableInteractionSource() }
    val isPreviewHovered by previewInteractionSource.collectIsHoveredAsState()
    val isPreviewFocused by previewInteractionSource.collectIsFocusedAsState()
    val isPreviewPopupHovered by previewPopupInteractionSource.collectIsHoveredAsState()
    var showPreviewPopup by remember(group.key, previewSpec) { mutableStateOf(false) }

    LaunchedEffect(previewSpec, isPreviewHovered, isPreviewFocused, isPreviewPopupHovered) {
        if (previewSpec == null) {
            showPreviewPopup = false
            return@LaunchedEffect
        }

        if (isPreviewHovered || isPreviewFocused || isPreviewPopupHovered) {
            if (!showPreviewPopup) {
                delay(500)
                if (isPreviewHovered || isPreviewFocused || isPreviewPopupHovered) {
                    showPreviewPopup = true
                }
            }
            return@LaunchedEffect
        }

        delay(120)
        if (!isPreviewHovered && !isPreviewFocused && !isPreviewPopupHovered) {
            showPreviewPopup = false
        }
    }

    LaunchedEffect(group.key, posterUrl, representativePath, posterLoadFailed, showPreviewPopup) {
        val source = when {
            shouldUsePoster -> "remote-poster"
            !representativePath.isNullOrBlank() -> "generated-video-frame"
            else -> "none"
        }

        val posterCached = if (!posterCacheKey.isNullOrBlank()) {
            val snapshot = imageLoader.diskCache?.openSnapshot(posterCacheKey)
            val exists = snapshot != null
            snapshot?.close()
            exists
        } else {
            false
        }

        Log.d(
            GROUP_THUMBNAIL_LOG_TAG,
            "Render group=${group.key} source=$source posterFailed=$posterLoadFailed previewPopup=$showPreviewPopup posterUrl=${posterUrl ?: "<none>"} posterCacheKey=${posterCacheKey ?: "<none>"} posterCached=$posterCached representativePath=${representativePath ?: "<none>"}"
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(DividerGray)
                    .hoverable(interactionSource = previewInteractionSource),
                contentAlignment = Alignment.Center
            ) {
                when {
                    shouldUsePoster -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(posterUrl)
                                .diskCacheKey(posterCacheKey)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .listener(
                                    onSuccess = { _, _ ->
                                        Log.d(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Poster load success for group=${group.key}, cacheKey=${posterCacheKey ?: "<none>"}"
                                        )
                                    },
                                    onError = { _, result ->
                                        Log.w(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Poster load failed for group=${group.key}, url=$posterUrl, reason=${result.throwable.message}. Falling back to generated thumbnail."
                                        )
                                        posterLoadFailed = true
                                    }
                                )
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Poster thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    !representativePath.isNullOrBlank() -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(VideoThumbnailFetcher.Model(representativePath))
                                .diskCacheKey(representativePath)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .listener(
                                    onSuccess = { _, _ ->
                                        Log.d(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Generated thumbnail success for group=${group.key}, path=$representativePath"
                                        )
                                    },
                                    onError = { _, result ->
                                        Log.e(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Generated thumbnail failed for group=${group.key}, path=$representativePath, reason=${result.throwable.message}"
                                        )
                                    }
                                )
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Generated video thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    else -> {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open grouped files",
                    tint = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = secondaryText,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        GroupHoverPreviewPopup(
            show = showPreviewPopup,
            groupKey = group.key,
            title = displayTitle,
            previewSpec = previewSpec,
            interactionSource = previewPopupInteractionSource,
            onClick = onClick,
        )
    }
}

@Composable
private fun FileListEntryCard(
    file: SMBFileItem,
    isVideoFile: Boolean,
    isFavorite: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onFavoriteToggle: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = rememberVideoFileMetadata(file, isVideoFile)
    val displayTitle = if (isVideoFile) metadata?.title ?: file.name else file.name

    val posterUrl = metadata?.posterUrl
    val posterCacheKey = posterUrl?.let { buildFilePosterCacheKey(file.name, it) }
    var posterLoadFailed by remember(file.name, posterUrl) { mutableStateOf(false) }
    val shouldUsePoster = isVideoFile && !posterUrl.isNullOrBlank() && !posterLoadFailed

    val previewSpec = remember(shouldUsePoster, posterUrl, posterCacheKey, isVideoFile, file.name) {
        buildGroupHoverPreviewSpec(
            shouldUsePoster = shouldUsePoster,
            posterUrl = posterUrl,
            posterCacheKey = posterCacheKey,
            representativePath = if (isVideoFile) file.path else null,
        )
    }

    val previewInteractionSource = remember { MutableInteractionSource() }
    val previewPopupInteractionSource = remember { MutableInteractionSource() }
    val isPreviewHovered by previewInteractionSource.collectIsHoveredAsState()
    val isPreviewFocused by previewInteractionSource.collectIsFocusedAsState()
    val isPreviewPopupHovered by previewPopupInteractionSource.collectIsHoveredAsState()
    var showPreviewPopup by remember(file.name, previewSpec) { mutableStateOf(false) }

    LaunchedEffect(previewSpec, isPreviewHovered, isPreviewFocused, isPreviewPopupHovered) {
        if (previewSpec == null || !isVideoFile) {
            showPreviewPopup = false
            return@LaunchedEffect
        }

        if (isPreviewHovered || isPreviewFocused || isPreviewPopupHovered) {
            if (!showPreviewPopup) {
                delay(500)
                if (isPreviewHovered || isPreviewFocused || isPreviewPopupHovered) {
                    showPreviewPopup = true
                }
            }
            return@LaunchedEffect
        }

        delay(120)
        if (!isPreviewHovered && !isPreviewFocused && !isPreviewPopupHovered) {
            showPreviewPopup = false
        }
    }

    FancyFileCard(
        fileName = displayTitle,
        isDirectory = file.isDirectory,
        isVideoFile = isVideoFile,
        fileSize = if (!file.isDirectory) {
            formatFileSizeHelper(file.size)
        } else {
            null
        },
        isFavorite = isFavorite,
        videoPath = if (isVideoFile) file.path else null,
        thumbnailModel = when {
            shouldUsePoster -> posterUrl
            isVideoFile -> VideoThumbnailFetcher.Model(file.path)
            else -> null
        },
        thumbnailDiskCacheKey = when {
            shouldUsePoster -> posterCacheKey
            isVideoFile -> file.name
            else -> null
        },
        onThumbnailLoadSuccess = {
            if (shouldUsePoster) {
                Log.d(
                    FILE_THUMBNAIL_LOG_TAG,
                    "Poster load success for file=${file.path}, cacheKey=${posterCacheKey ?: "<none>"}"
                )
            }
        },
        onThumbnailLoadError = { throwable ->
            if (shouldUsePoster) {
                Log.w(
                    FILE_THUMBNAIL_LOG_TAG,
                    "Poster load failed for file=${file.path}, url=$posterUrl, reason=${throwable?.message}. Falling back to generated thumbnail."
                )
                posterLoadFailed = true
            } else if (isVideoFile) {
                Log.e(
                    FILE_THUMBNAIL_LOG_TAG,
                    "Generated thumbnail failed for file=${file.path}, reason=${throwable?.message}"
                )
            }
        },
        onFavoriteToggle = if (isSelectionMode || !isVideoFile) {
            null
        } else {
            onFavoriteToggle
        },
        isSelected = isSelected,
        onClick = onClick,
        modifier = modifier.hoverable(interactionSource = previewInteractionSource),
    )

    GroupHoverPreviewPopup(
        show = showPreviewPopup,
        groupKey = metadata?.code ?: file.name,
        title = displayTitle,
        previewSpec = previewSpec,
        interactionSource = previewPopupInteractionSource,
        onClick = onClick,
    )
}

@Composable
private fun FileThumbnailCard(
    file: SMBFileItem,
    isVideoFile: Boolean,
    isFavorite: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onFavoriteToggle: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val metadata = rememberVideoFileMetadata(file, isVideoFile)
    val displayTitle = if (isVideoFile) metadata?.title ?: file.name else file.name

    val posterUrl = metadata?.posterUrl
    val posterCacheKey = posterUrl?.let { buildFilePosterCacheKey(file.name, it) }
    var posterLoadFailed by remember(file.name, posterUrl) { mutableStateOf(false) }
    val shouldUsePoster = isVideoFile && !posterUrl.isNullOrBlank() && !posterLoadFailed

    val previewSpec = remember(shouldUsePoster, posterUrl, posterCacheKey, isVideoFile, file.name) {
        buildGroupHoverPreviewSpec(
            shouldUsePoster = shouldUsePoster,
            posterUrl = posterUrl,
            posterCacheKey = posterCacheKey,
            representativePath = if (isVideoFile) file.path else null,
        )
    }

    val previewInteractionSource = remember { MutableInteractionSource() }
    val previewPopupInteractionSource = remember { MutableInteractionSource() }
    val isPreviewHovered by previewInteractionSource.collectIsHoveredAsState()
    val isPreviewFocused by previewInteractionSource.collectIsFocusedAsState()
    val isPreviewPopupHovered by previewPopupInteractionSource.collectIsHoveredAsState()
    var showPreviewPopup by remember(file.name, previewSpec) { mutableStateOf(false) }

    LaunchedEffect(previewSpec, isPreviewHovered, isPreviewFocused, isPreviewPopupHovered) {
        if (previewSpec == null || !isVideoFile) {
            showPreviewPopup = false
            return@LaunchedEffect
        }

        if (isPreviewHovered || isPreviewFocused || isPreviewPopupHovered) {
            if (!showPreviewPopup) {
                delay(500)
                if (isPreviewHovered || isPreviewFocused || isPreviewPopupHovered) {
                    showPreviewPopup = true
                }
            }
            return@LaunchedEffect
        }

        delay(120)
        if (!isPreviewHovered && !isPreviewFocused && !isPreviewPopupHovered) {
            showPreviewPopup = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) BorderStroke(2.dp, NetflixRed) else null
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(DividerGray)
                    .hoverable(interactionSource = previewInteractionSource),
                contentAlignment = Alignment.Center
            ) {
                if (isVideoFile) {
                    val imageRequest = ImageRequest.Builder(context)
                        .data(
                            if (shouldUsePoster) {
                                posterUrl
                            } else {
                                VideoThumbnailFetcher.Model(file.path)
                            }
                        )
                        .diskCacheKey(
                            if (shouldUsePoster) {
                                posterCacheKey
                            } else {
                                file.name
                            }
                        )
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .listener(
                            onSuccess = { _, _ ->
                                if (shouldUsePoster) {
                                    Log.d(
                                        FILE_THUMBNAIL_LOG_TAG,
                                        "Poster load success for file=${file.path}, cacheKey=${posterCacheKey ?: "<none>"}"
                                    )
                                }
                            },
                            onError = { _, result ->
                                if (shouldUsePoster) {
                                    Log.w(
                                        FILE_THUMBNAIL_LOG_TAG,
                                        "Poster load failed for file=${file.path}, url=$posterUrl, reason=${result.throwable.message}. Falling back to generated thumbnail."
                                    )
                                    posterLoadFailed = true
                                } else {
                                    Log.e(
                                        FILE_THUMBNAIL_LOG_TAG,
                                        "Generated thumbnail failed for file=${file.path}, reason=${result.throwable.message}"
                                    )
                                }
                            }
                        )
                        .build()

                    AsyncImage(
                        model = imageRequest,
                        imageLoader = ThumbnailImageLoaderProvider.get(context),
                        contentDescription = if (shouldUsePoster) {
                            "Poster thumbnail"
                        } else {
                            "Video thumbnail"
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                if (!isSelectionMode && isVideoFile && onFavoriteToggle != null) {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = if (isFavorite) {
                                "Remove from favorites"
                            } else {
                                "Add to favorites"
                            },
                            tint = if (isFavorite) NetflixRed else TextPrimary
                        )
                    }
                }

                if (isSelectionMode && isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected for deletion",
                        tint = NetflixRed,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isVideoFile) TextPrimary else TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!file.isDirectory) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFileSizeHelper(file.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }

    GroupHoverPreviewPopup(
        show = showPreviewPopup,
        groupKey = metadata?.code ?: file.name,
        title = displayTitle,
        previewSpec = previewSpec,
        interactionSource = previewPopupInteractionSource,
        onClick = onClick,
    )
}

/**
 * RIGHT PANEL: Favorites
 */
@Composable
private fun FavoritesPanel(
    favorites: List<RecentVideo>,
    isConnected: Boolean,
    onFavoriteClick: (RecentVideo) -> Unit,
    onFavoriteToggle: (RecentVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = StreamingBlack
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Panel Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Favorites",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(16.dp))

            // Content
            when {
                !isConnected -> {
                    EmptyState(
                        icon = Icons.Outlined.FavoriteBorder,
                        message = "Connect to view favorites"
                    )
                }

                favorites.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.FavoriteBorder,
                        message = "No favorites yet"
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(favorites) { video ->
                            FancyMovieCard(
                                video = video,
                                isFavorite = true,
                                onClick = { onFavoriteClick(video) },
                                onFavoriteToggle = { onFavoriteToggle(video) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Add Server Dialog
 */
/**
 * Add/Edit Server Dialog
 */
@Composable
private fun AddServerDialog(
    initialServer: SavedServer? = null,
    defaultUsername: String = "",
    defaultPassword: String = "",
    onDismiss: () -> Unit,
    onSave: (SavedServer) -> Unit,
    onTestConnection: suspend (SavedServer) -> Result<Unit>
) {
    val context = LocalContext.current
    val (storedUsername, storedPassword) = remember {
        ServerCredentialAutofillStore.load(context)
    }

    val buildConfigUsername = remember { BuildConfig.SMB_DEFAULT_USERNAME.trim() }
    val buildConfigPassword = remember { BuildConfig.SMB_DEFAULT_PASSWORD.trim() }

    val initialUsername = remember(
        initialServer?.id,
        defaultUsername,
        storedUsername,
        buildConfigUsername,
    ) {
        if (initialServer != null) {
            initialServer.username
        } else {
            listOf(storedUsername, defaultUsername, buildConfigUsername)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }
    }

    val initialPassword = remember(
        initialServer?.id,
        defaultPassword,
        storedPassword,
        buildConfigPassword,
    ) {
        if (initialServer != null) {
            initialServer.password
        } else {
            listOf(storedPassword, defaultPassword, buildConfigPassword)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }
    }

    var name by remember(initialServer?.id) { mutableStateOf(initialServer?.serverName ?: "") }
    var address by remember(initialServer?.id) {
        mutableStateOf(
            initialServer?.serverAddress ?: ""
        )
    }
    var shareName by remember(initialServer?.id) { mutableStateOf(initialServer?.shareName ?: "") }
    var username by remember(initialServer?.id, initialUsername) { mutableStateOf(initialUsername) }
    var password by remember(initialServer?.id, initialPassword) { mutableStateOf(initialPassword) }
    var domain by remember(initialServer?.id) { mutableStateOf(initialServer?.domain ?: "") }

    LaunchedEffect(initialServer?.id, initialUsername, initialPassword) {
        Log.d(
            "AddServerDialog",
            "Autofill ready (editing=${initialServer != null}, usernameLength=${initialUsername.length}, hasPassword=${initialPassword.isNotBlank()})"
        )
    }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Use standard Dialog composable
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .width(500.dp)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = CardBackground
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (initialServer == null) "Add Server" else "Edit Server",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Server Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingConnection
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("IP Address") },
                        placeholder = { Text("e.g., 192.168.1.100") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingConnection
                    )
                    OutlinedTextField(
                        value = shareName,
                        onValueChange = { shareName = it },
                        label = { Text("Share Name (Optional)") },
                        placeholder = { Text("e.g., Videos") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingConnection
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingConnection
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        enabled = !isTestingConnection
                    )
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text("Domain (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingConnection
                    )
                }

                if (connectionError != null) {
                    Text(
                        text = connectionError!!,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isTestingConnection
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    val isSaveEnabled =
                        name.isNotBlank() && address.isNotBlank() && !isTestingConnection

                    Button(
                        onClick = {
                            if (isSaveEnabled) {
                                scope.launch {
                                    isTestingConnection = true
                                    connectionError = null

                                    val serverToSave = SavedServer(
                                        id = initialServer?.id ?: 0, // Preserve ID if editing
                                        serverName = name,
                                        serverAddress = address,
                                        shareName = shareName,
                                        username = username,
                                        password = password,
                                        domain = domain
                                    )

                                    val result = onTestConnection(serverToSave)

                                    if (result.isSuccess) {
                                        ServerCredentialAutofillStore.save(
                                            context,
                                            username,
                                            password
                                        )
                                        Log.d(
                                            "AddServerDialog",
                                            "Stored manual autofill credentials (usernameLength=${username.length}, hasPassword=${password.isNotBlank()})"
                                        )
                                        onSave(serverToSave)
                                    } else {
                                        connectionError =
                                            "Connection failed: ${result.exceptionOrNull()?.message}"
                                        isTestingConnection = false
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetflixRed
                        ),
                        enabled = isSaveEnabled
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = TextPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

private fun buildGroupPosterCacheKey(groupKey: String, posterUrl: String): String {
    return "group-poster:$groupKey:${posterUrl.hashCode()}"
}

private fun buildFilePosterCacheKey(fileName: String, posterUrl: String): String {
    return "file-poster:${fileName.hashCode()}:${posterUrl.hashCode()}"
}

private val multipartVideoPattern = Regex("""^(.+)-(\d{1,2})$""")

private fun buildVirtualVideoGroups(files: List<SMBFileItem>): Map<String, VirtualVideoGroup> {
    val groupedCandidates = files
        .asSequence()
        .filter { !it.isDirectory && SMBClient.isVideoFile(it.name) }
        .mapNotNull { file ->
            val groupKey = extractVirtualGroupKey(file.name) ?: return@mapNotNull null
            groupKey to file
        }
        .groupBy(
            keySelector = { it.first },
            valueTransform = { it.second }
        )
        .filterValues { candidates -> candidates.size > 1 }

    return groupedCandidates.mapValues { (groupKey, groupedFiles) ->
        VirtualVideoGroup(
            key = groupKey,
            files = groupedFiles.sortedWith(
                compareBy<SMBFileItem>(
                    { extractVirtualGroupPart(it.name) ?: Int.MAX_VALUE },
                    { it.name.lowercase() }
                )
            )
        )
    }
}

private fun buildFileBrowserDisplayItems(
    files: List<SMBFileItem>,
    virtualGroups: Map<String, VirtualVideoGroup>,
    activeGroupKey: String?
): List<FileBrowserDisplayItem> {
    if (activeGroupKey != null) {
        val activeGroup = virtualGroups[activeGroupKey] ?: return emptyList()
        return activeGroup.files.map { groupedFile ->
            FileBrowserDisplayItem.Entry(file = groupedFile)
        }
    }

    val emittedGroupKeys = mutableSetOf<String>()
    val displayItems = mutableListOf<FileBrowserDisplayItem>()

    files.forEach { file ->
        if (file.isDirectory) {
            displayItems += FileBrowserDisplayItem.Entry(file = file)
            return@forEach
        }

        if (!SMBClient.isVideoFile(file.name)) {
            return@forEach
        }

        val groupKey = extractVirtualGroupKey(file.name)
        val virtualGroup = groupKey?.let(virtualGroups::get)

        if (virtualGroup == null) {
            displayItems += FileBrowserDisplayItem.Entry(file = file)
            return@forEach
        }

        if (!emittedGroupKeys.add(virtualGroup.key)) {
            return@forEach
        }

        displayItems += FileBrowserDisplayItem.Group(
            virtualGroup = virtualGroup,
        )
    }

    return displayItems
}

private fun extractVirtualGroupKey(fileName: String): String? {
    val stem = fileName.substringBeforeLast('.', fileName)
    val match = multipartVideoPattern.matchEntire(stem) ?: return null
    val baseName = match.groupValues[1].trimEnd('-', '_', ' ')
    return baseName.takeIf { it.isNotBlank() }
}

private fun extractVirtualGroupPart(fileName: String): Int? {
    val stem = fileName.substringBeforeLast('.', fileName)
    val match = multipartVideoPattern.matchEntire(stem) ?: return null
    return match.groupValues[2].toIntOrNull()
}

/**
 * Helper function to format file size
 */
private fun formatFileSizeHelper(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
























































