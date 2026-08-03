package blackark.app.vr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import blackark.app.vr.data.database.AppDatabase
import blackark.app.vr.data.database.entity.SavedServer
import blackark.app.vr.data.repository.ServerRepository
import blackark.app.vr.data.security.SmbCredentialStore
import blackark.app.vr.network.SmbEndpointParser
import blackark.app.vr.ui.viewmodel.ServerConnectionViewModel

// ViewModel Factory
class ServerConnectionViewModelFactory(
    private val repository: ServerRepository,
    private val credentialStore: SmbCredentialStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ServerConnectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ServerConnectionViewModel(repository, credentialStore) as T
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
        factory = ServerConnectionViewModelFactory(repository, remember { SmbCredentialStore(context) })
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
                viewModel.connectToServer(
                    name,
                    address,
                    port,
                    share,
                    username,
                    password,
                    domain,
                    save
                )
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
    var address by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to SMB Server") },
        text = {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("SMB address") },
                    placeholder = { Text("smb://server/share") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("User ID (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val endpoint = SmbEndpointParser.parse(address).getOrElse { failure ->
                        error = failure.message
                        return@Button
                    }
                    onConnect(
                        endpoint.suggestedName,
                        endpoint.address,
                        endpoint.port,
                        endpoint.shareName,
                        username,
                        password,
                        "",
                        true,
                    )
                },
                enabled = address.isNotBlank()
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
