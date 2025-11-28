package com.example.myapplication

import android.view.KeyEvent
import com.example.myapplication.network.SMBClient
import com.example.myapplication.network.SMBConfig
import kotlinx.coroutines.flow.MutableSharedFlow

object AppState {
    var smbClient: SMBClient? = null
        private set

    var smbConfig: SMBConfig? = null
        private set
    val keyEvents = MutableSharedFlow<KeyEvent>(extraBufferCapacity = 1)

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
