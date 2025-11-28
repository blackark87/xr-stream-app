package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
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
import com.example.myapplication.ui.viewmodel.MainDashboardViewModel

/**
 * Main Dashboard Screen - Netflix/Disney+ style 3-panel layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    navController: NavController,
    viewModel: MainDashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recentVideos by viewModel.recentVideos.collectAsStateWithLifecycle()

    var showAddServerDialog by remember { mutableStateOf(false) }

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
            modifier = Modifier.fillMaxSize()
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
                    onServerDelete = { server ->
                        viewModel.deleteServer(server)
                    },
                    onAddServerClick = {
                        showAddServerDialog = true
                    },
                    modifier = Modifier
                        .weight(0.25f)
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
                    onFileClick = { file ->
                        viewModel.navigateToFile(file)
                    },
                    onBackClick = {
                        viewModel.navigateBack()
                    },
                    onPlayVideo = { filePath, fileName ->
                        navController.navigate(Screen.VideoPlayer.createRoute(filePath, fileName))
                    },
                    onFavoriteToggle = { filePath, isFavorite ->
                        viewModel.toggleFavoriteByPath(filePath, !isFavorite)
                    },
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                )

                Spacer(modifier = Modifier.width(16.dp))

                // RIGHT PANEL: Favorites
                FavoritesPanel(
                    favorites = favorites,
                    isConnected = uiState.isConnected,
                    onFavoriteClick = { video ->
                        navController.navigate("video_player/${video.filePath}/${video.fileName}")
                    },
                    onFavoriteToggle = { video ->
                        viewModel.toggleFavorite(video.id, !video.isFavorite)
                    },
                    modifier = Modifier
                        .weight(0.25f)
                        .fillMaxHeight()
                )
            }
        }

        // Add Server Dialog
        if (showAddServerDialog) {
            AddServerDialog(
                onDismiss = { showAddServerDialog = false },
                onSave = { server ->
                    viewModel.addServer(server)
                    showAddServerDialog = false
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

/**
 * LEFT PANEL: Server List
 */
@Composable
private fun ServerListPanel(
    servers: List<SavedServer>,
    selectedServer: SavedServer?,
    isConnecting: Boolean,
    onServerClick: (SavedServer) -> Unit,
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
    onFileClick: (SMBFileItem) -> Unit,
    onBackClick: () -> Unit,
    onPlayVideo: (String, String) -> Unit,
    onFavoriteToggle: (String, Boolean) -> Unit,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Files",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    if (isConnected && currentPath.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            maxLines = 1
                        )
                    }
                }

                if (currentPath.isNotEmpty() && currentPath != "/") {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextSecondary
                        )
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
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files) { file ->
                            val isVideoFile = SMBClient.isVideoFile(file.name)
                            // Check if this file is in recent videos and get its favorite status
                            val videoInDb = recentVideos.find { it.filePath == file.path }
                            val isFavorite = videoInDb?.isFavorite ?: false

                            FancyFileCard(
                                fileName = file.name,
                                isDirectory = file.isDirectory,
                                isVideoFile = isVideoFile,
                                fileSize = if (!file.isDirectory) formatFileSizeHelper(file.size) else null,
                                isFavorite = isFavorite,
                                videoPath = if (isVideoFile) file.path else null,
                                onFavoriteToggle = if (isVideoFile) {
                                    { onFavoriteToggle(file.path, isFavorite) }
                                } else null,
                                onClick = {
                                    if (isVideoFile) {
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
@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onSave: (SavedServer) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var shareName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }

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
                    text = "Add Server",
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
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("IP Address") },
                        placeholder = { Text("e.g., 192.168.1.100") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = shareName,
                        onValueChange = { shareName = it },
                        label = { Text("Share Name (Optional)") },
                        placeholder = { Text("e.g., Videos") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text("Domain (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    val isSaveEnabled = name.isNotBlank() && address.isNotBlank()

                    Button(
                        onClick = {
                            if (isSaveEnabled) {
                                onSave(
                                    SavedServer(
                                        serverName = name,
                                        serverAddress = address,
                                        shareName = shareName, // Can be empty
                                        username = username,
                                        password = password,
                                        domain = domain
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetflixRed
                        ),
                        enabled = isSaveEnabled
                    ) {
                        Text("Save")
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
