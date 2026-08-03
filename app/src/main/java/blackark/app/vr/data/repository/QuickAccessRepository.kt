package blackark.app.vr.data.repository

import blackark.app.vr.data.database.dao.QuickAccessFolderDao
import blackark.app.vr.data.database.entity.QuickAccessFolder

class QuickAccessRepository(private val dao: QuickAccessFolderDao) {
    val folders = dao.observeAll()
    suspend fun add(folder: QuickAccessFolder) = dao.insert(folder)
    suspend fun delete(id: Long) = dao.delete(id)
}
