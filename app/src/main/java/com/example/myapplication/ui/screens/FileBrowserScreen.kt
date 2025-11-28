package com.example.myapplication.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.network.SMBClient
import com.example.myapplication.network.SMBFileItem
import com.example.myapplication.ui.components.FancyFileCard
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.showVideosOnly,
                    onClick = { fileBrowserViewModel.toggleVideoFilter() },
                    label = { Text("Videos Only") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Filter",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Current path
            Text(
                text = "Path: /${state.currentPath}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Get filtered files
            val filteredFiles = fileBrowserViewModel.getFilteredFiles()

            // Debug logging
            android.util.Log.d(
                "FileBrowser",
                "Filter active: ${state.showVideosOnly}, Total files: ${state.files.size}, Filtered: ${filteredFiles.size}"
            )

            // File list
            if (filteredFiles.isEmpty() && !state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.showVideosOnly) "No video files found" else "No files found",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredFiles) { file ->
                        val isVideo = SMBClient.isVideoFile(file.name)
                        if (isVideo) {
                            android.util.Log.d(
                                "FileBrowser",
                                "Video file: ${file.name}, path: ${file.path}"
                            )
                        }
                        FancyFileCard(
                            fileName = file.name,
                            isDirectory = file.isDirectory,
                            isVideoFile = isVideo,
                            fileSize = if (!file.isDirectory) formatFileSize(file.size) else null,
                            videoPath = if (isVideo) file.path else null,
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

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
