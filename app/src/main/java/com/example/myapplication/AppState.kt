package com.example.myapplication


import com.example.myapplication.network.SMBClient
import com.example.myapplication.network.SMBConfig
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow


object AppState {
    var smbClient: SMBClient? = null
        private set

    var smbConfig: SMBConfig? = null
        private set

    // Global key event bus
    val keyEvents = kotlinx.coroutines.flow.MutableSharedFlow<android.view.KeyEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun setSMBClient(client: SMBClient, config: SMBConfig) {
        smbClient = client
        smbConfig = config
    }

    fun clear() {
        smbClient?.disconnect()
        smbClient = null
        smbConfig = null
    }
}
