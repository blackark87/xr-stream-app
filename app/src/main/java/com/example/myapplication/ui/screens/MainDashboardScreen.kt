package com.example.myapplication.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterOffsetType
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.MovePolicy
import androidx.xr.compose.subspace.SpatialMainPanel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
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
        // Keep the dashboard window movable in spatial mode.
        SpatialMainPanel(dragPolicy = MovePolicy())
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
                        .weight(0.28f) // Increased from 0.25f
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
                        .weight(0.44f) // Decreased from 0.5f
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
                        .weight(0.28f) // Increased from 0.25f
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
                    activity?.finishAffinity()
                }
            )
        }

        // Add/Edit Server Dialog
        if (showAddServerDialog || serverToEdit != null) {
            AddServerDialog(
                initialServer = serverToEdit,
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

    LaunchedEffect(files, isDeleteMode) {
        selectedPaths = if (isDeleteMode) {
            val currentPaths = files.map { it.path }.toSet()
            selectedPaths.filter { it in currentPaths }.toSet()
        } else {
            emptySet()
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

                        isConnected && currentPath.isNotEmpty() -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentPath,
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

                    if (currentPath.isNotEmpty() && currentPath != "/" && !isDeleteMode) {
                        IconButton(onClick = onBackClick) {
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

                files.isEmpty() -> {
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
                                items(files) { file ->
                                    val isVideoFile = SMBClient.isVideoFile(file.name)
                                    val videoInDb = recentVideos.find { it.filePath == file.path }
                                    val isFavorite = videoInDb?.isFavorite ?: false
                                    val isSelected = selectedPaths.contains(file.path)

                                    FancyFileCard(
                                        fileName = file.name,
                                        isDirectory = file.isDirectory,
                                        isVideoFile = isVideoFile,
                                        fileSize = if (!file.isDirectory) formatFileSizeHelper(file.size) else null,
                                        isFavorite = isFavorite,
                                        videoPath = if (isVideoFile) file.path else null,
                                        onFavoriteToggle = if (isDeleteMode || !isVideoFile) {
                                            null
                                        } else {
                                            { onFavoriteToggle(file, isFavorite) }
                                        },
                                        isSelected = isSelected,
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

                        FileBrowserViewMode.Thumbnail -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 180.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = files,
                                    key = { fileItem -> fileItem.path }
                                ) { file ->
                                    val isVideoFile = SMBClient.isVideoFile(file.name)
                                    val videoInDb = recentVideos.find { it.filePath == file.path }
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
                        val targets = files.filter { !it.isDirectory && selectedPaths.contains(it.path) }
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
                    .height(120.dp)
                    .background(DividerGray),
                contentAlignment = Alignment.Center
            ) {
                if (isVideoFile) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(file.path)
                            .diskCacheKey(file.path)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = "Video thumbnail",
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
                text = file.name,
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
    onDismiss: () -> Unit,
    onSave: (SavedServer) -> Unit,
    onTestConnection: suspend (SavedServer) -> Result<Unit>
) {
    var name by remember { mutableStateOf(initialServer?.serverName ?: "") }
    var address by remember { mutableStateOf(initialServer?.serverAddress ?: "") }
    var shareName by remember { mutableStateOf(initialServer?.shareName ?: "") }
    var username by remember { mutableStateOf(initialServer?.username ?: "") }
    var password by remember { mutableStateOf(initialServer?.password ?: "") }
    var domain by remember { mutableStateOf(initialServer?.domain ?: "") }

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







