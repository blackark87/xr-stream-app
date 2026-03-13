package blackark.app.vr.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_servers")
data class SavedServer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val serverName: String,
    val serverAddress: String,
    val port: Int = 445,
    val shareName: String = "", // Optional - empty for root/all shares
    val username: String = "",
    val password: String = "", // Note: In production, consider encrypting this
    val domain: String = "",
    val lastConnected: Long = System.currentTimeMillis(),
    val isLocalStorage: Boolean = false // true if this is local device storage
) {
    companion object {
        fun createLocalStorageServer(): SavedServer {
            return SavedServer(
                serverName = "Local Storage",
                serverAddress = "local://storage",
                port = 0,
                shareName = "",
                username = "",
                password = "",
                domain = "",
                isLocalStorage = true
            )
        }
    }
}
