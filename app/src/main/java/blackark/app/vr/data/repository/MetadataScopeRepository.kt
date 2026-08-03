package blackark.app.vr.data.repository

import blackark.app.vr.data.database.dao.MetadataScopeDao
import blackark.app.vr.data.database.entity.MetadataScope

class MetadataScopeRepository(
    private val dao: MetadataScopeDao,
) {
    val allScopes = dao.observeAll()

    fun observeForServer(serverId: Long) = dao.observeForServer(serverId)

    suspend fun add(scope: MetadataScope): Long = dao.insert(scope)

    suspend fun update(scope: MetadataScope) = dao.update(scope)

    suspend fun delete(scopeId: Long) = dao.delete(scopeId)

    suspend fun deleteForServer(serverId: Long) = dao.deleteForServer(serverId)
}
