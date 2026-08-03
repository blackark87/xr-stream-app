package blackark.app.vr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import blackark.app.vr.data.database.entity.SavedServer
import blackark.app.vr.data.repository.ServerRepository
import blackark.app.vr.data.security.SmbCredentialStore
import blackark.app.vr.data.security.SmbCredentials
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ServerConnectionState(
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null,
    val savedServers: List<SavedServer> = emptyList(),
    val currentServer: SavedServer? = null
)

class ServerConnectionViewModel(
    private val serverRepository: ServerRepository,
    private val credentialStore: SmbCredentialStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerConnectionState())
    val state: StateFlow<ServerConnectionState> = _state.asStateFlow()

    private var smbClient: SMBClient? = null

    init {
        loadSavedServers()
    }

    private fun loadSavedServers() {
        viewModelScope.launch {
            serverRepository.allServers.collect { servers ->
                _state.value = _state.value.copy(savedServers = servers)
            }
        }
    }

    fun connectToServer(
        serverName: String,
        serverAddress: String,
        port: Int = 445,
        shareName: String,
        username: String,
        password: String,
        domain: String = "",
        saveCredentials: Boolean = true
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val config = SMBConfig(
                    serverAddress = serverAddress,
                    port = port,
                    shareName = shareName,
                    username = username,
                    password = password,
                    domain = domain
                )

                val client = SMBClient(config)
                val result = client.connect()

                if (result.isSuccess) {
                    smbClient = client

                    // Save to AppState for sharing across screens
                    blackark.app.vr.AppState.setSMBClient(client, config)

                    // Save server if requested
                    if (saveCredentials) {
                        val credentialAlias = SmbCredentialStore.aliasForNewServer()
                        credentialStore.save(
                            credentialAlias,
                            SmbCredentials(username, password, domain),
                        )
                        val server = SavedServer(
                            serverName = serverName,
                            serverAddress = serverAddress,
                            port = port,
                            shareName = shareName,
                            credentialAlias = credentialAlias,
                            lastConnected = System.currentTimeMillis()
                        )

                        val serverId = serverRepository.insertServer(server)
                        val savedServer = server.copy(id = serverId)

                        _state.value = _state.value.copy(
                            isLoading = false,
                            isConnected = true,
                            currentServer = savedServer,
                            error = null
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            isConnected = true,
                            error = null
                        )
                    }
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isConnected = false,
                        error = result.exceptionOrNull()?.message ?: "Connection failed"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isConnected = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    fun connectToSavedServer(server: SavedServer) {
        val credentials = credentialStore.load(server.credentialAlias)
        connectToServer(
            serverName = server.serverName,
            serverAddress = server.serverAddress,
            port = server.port,
            shareName = server.shareName,
            username = credentials.username,
            password = credentials.password,
            domain = credentials.domain,
            saveCredentials = false // Already saved
        )

        // Update last connected time
        viewModelScope.launch {
            serverRepository.updateLastConnected(server.id)
        }

        _state.value = _state.value.copy(currentServer = server)
    }

    fun deleteSavedServer(server: SavedServer) {
        viewModelScope.launch {
            credentialStore.delete(server.credentialAlias)
            serverRepository.deleteServer(server)
        }
    }

    fun getSMBClient(): SMBClient? = smbClient

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun disconnect() {
        smbClient?.disconnect()
        smbClient = null
        blackark.app.vr.AppState.clear()
        _state.value = _state.value.copy(isConnected = false, currentServer = null)
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
