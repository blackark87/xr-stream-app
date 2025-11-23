package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.database.AppDatabase
import com.example.myapplication.data.database.entity.SavedServer
import com.example.myapplication.data.repository.ServerRepository
import com.example.myapplication.ui.viewmodel.ServerConnectionViewModel

// ViewModel Factory
class ServerConnectionViewModelFactory(
    private val repository: ServerRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ServerConnectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServerConnectionViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun ServerConnectionScreen(
    onNavigateToFileBrowser: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember { ServerRepository(database.serverDao()) }
    val viewModel: ServerConnectionViewModel = viewModel(
        factory = ServerConnectionViewModelFactory(repository)
    )

    val state by viewModel.state.collectAsState()

    var showNewServerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isConnected) {
        if (state.isConnected) {
            onNavigateToFileBrowser()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "XR Video Player",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Connect to SMB Server",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (state.savedServers.isNotEmpty()) {
                Text(
                    text = "Saved Servers",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.savedServers) { server ->
                        SavedServerCard(
                            server = server,
                            onConnect = { viewModel.connectToSavedServer(it) },
                            onDelete = { viewModel.deleteSavedServer(it) },
                            isConnecting = state.isLoading
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = { showNewServerDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                Text("Add New Server")
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

    if (showNewServerDialog) {
        NewServerDialog(
            onDismiss = { showNewServerDialog = false },
            onConnect = { name, address, port, share, username, password, domain, save ->
                viewModel.connectToServer(name, address, port, share, username, password, domain, save)
                showNewServerDialog = false
            }
        )
    }
}

@Composable
fun SavedServerCard(
    server: SavedServer,
    onConnect: (SavedServer) -> Unit,
    onDelete: (SavedServer) -> Unit,
    isConnecting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
                    text = server.serverName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${server.serverAddress}:${server.port}/${server.shareName}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "User: ${server.username}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row {
                IconButton(
                    onClick = { onConnect(server) },
                    enabled = !isConnecting
                ) {
                    Text("Connect")
                }
                IconButton(onClick = { onDelete(server) }) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
fun NewServerDialog(
    onDismiss: () -> Unit,
    onConnect: (String, String, Int, String, String, String, String, Boolean) -> Unit
) {
    // Hardcoded for testing
    val serverName = "Test Server"
    val serverAddress = "192.168.1.105"
    val port = 445
    val shareName = ""
    val username = "blackark87"
    val domain = ""
    val saveCredentials = true

    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to SMB Server") },
        text = {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Server: blackark87@192.168.1.105",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConnect(
                        serverName,
                        serverAddress,
                        port,
                        shareName,
                        username,
                        password,
                        domain,
                        saveCredentials
                    )
                },
                enabled = password.isNotBlank()
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
