package com.example.myapplication

import com.example.myapplication.network.SMBClient
import com.example.myapplication.network.SMBConfig

object AppState {
    var smbClient: SMBClient? = null
        private set

    var smbConfig: SMBConfig? = null
        private set

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
