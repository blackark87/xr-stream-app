package com.example.myapplication.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.network.SMBClient
import com.example.myapplication.network.SMBFileItem
import com.example.myapplication.ui.viewmodel.FileBrowserViewModel

@Composable
fun FileBrowserScreen(
    onNavigateBack: () -> Unit,
    onVideoSelected: (SMBFileItem) -> Unit
) {
    val fileBrowserViewModel: FileBrowserViewModel = viewModel()
    val state by fileBrowserViewModel.state.collectAsState()

    // Set SMB client from AppState when screen opens
    LaunchedEffect(Unit) {
        com.example.myapplication.AppState.smbClient?.let { client ->
            fileBrowserViewModel.setSMBClient(client)
        }
    }



    BackHandler(enabled = true) {
        val handledBack = fileBrowserViewModel.navigateBack()
        if (!handledBack) {
            com.example.myapplication.AppState.clear()
            onNavigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Browse Files",
                    style = MaterialTheme.typography.headlineSmall
                )

                if (state.canGoBack) {
                    IconButton(onClick = { fileBrowserViewModel.navigateBack() }) {
                        Text("⬅", style = MaterialTheme.typography.titleLarge)
                    }
                }

                TextButton(
                    onClick = {
                        com.example.myapplication.AppState.clear()
                        onNavigateBack()
                    }
                ) {
                    Text("Disconnect")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Current path
            Text(
                text = "Path: /${state.currentPath}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // File list
            if (state.files.isEmpty() && !state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No files found",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.files) { file ->
                        FileItemCard(
                            file = file,
                            onClick = {
                                if (file.isDirectory) {
                                    fileBrowserViewModel.navigateToDirectory(file)
                                } else {
                                    fileBrowserViewModel.selectFile(file)
                                    onVideoSelected(file)
                                }
                            }
                        )
                    }
                }
            }

            if (state.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun FileItemCard(
    file: SMBFileItem,
    onClick: () -> Unit
) {
    val isVideo = SMBClient.isVideoFile(file.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (isVideo) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (file.isDirectory) "Folder" else "File",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (!file.isDirectory) {
                        Text(
                            text = formatFileSize(file.size),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (isVideo) {
                        Text(
                            text = "Video",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = if (file.isDirectory) "→" else "",
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
