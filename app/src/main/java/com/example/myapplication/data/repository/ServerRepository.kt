package com.example.myapplication.data.repository

import com.example.myapplication.data.database.dao.ServerDao
import com.example.myapplication.data.database.entity.SavedServer
import kotlinx.coroutines.flow.Flow

class ServerRepository(private val serverDao: ServerDao) {

    val allServers: Flow<List<SavedServer>> = serverDao.getAllServers()

    suspend fun getServerById(serverId: Long): SavedServer? {
        return serverDao.getServerById(serverId)
    }

    suspend fun getServerByAddress(address: String, share: String): SavedServer? {
        return serverDao.getServerByAddress(address, share)
    }

    suspend fun insertServer(server: SavedServer): Long {
        return serverDao.insertServer(server)
    }

    suspend fun updateServer(server: SavedServer) {
        serverDao.updateServer(server)
    }

    suspend fun deleteServer(server: SavedServer) {
        serverDao.deleteServer(server)
    }

    suspend fun deleteServerById(serverId: Long) {
        serverDao.deleteServerById(serverId)
    }

    suspend fun updateLastConnected(serverId: Long) {
        serverDao.updateLastConnected(serverId, System.currentTimeMillis())
    }
}
