package blackark.app.vr.unity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import blackark.app.vr.data.database.AppDatabase
import blackark.app.vr.data.database.entity.RecentVideo
import blackark.app.vr.data.database.entity.SavedServer
import blackark.app.vr.data.database.entity.VideoDisplaySettings
import blackark.app.vr.data.security.SmbCredentialStore
import blackark.app.vr.data.security.SmbCredentials
import blackark.app.vr.network.LocalFileClient
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/** Poll immutable JSON events on Unity's main thread. No Jetpack XR session is created. */
object UnityBridge {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var credentials: SmbCredentialStore
    private var transport: RangeMediaServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = ConcurrentHashMap<String, Job>()
    private val events = ConcurrentLinkedQueue<String>()
    @JvmStatic @Synchronized fun initialize(activity: Activity) {
        if (::context.isInitialized) return
        context = activity.applicationContext
        database = AppDatabase.getDatabase(context)
        credentials = SmbCredentialStore(context)
    }
    @JvmStatic fun poll(): String = events.poll().orEmpty()
    @JvmStatic fun getPlaybackPreferences(): String = loadPlaybackPreferences(context).toString()
    @JvmStatic fun setPlaybackPreferences(json: String) = savePlaybackPreferences(context, JSONObject(json))
    @JvmStatic fun cancel(id: String) { requests.remove(id)?.cancel() }
    @JvmStatic fun releaseStream(token: String) { transport?.release(token) }
    @JvmStatic fun requiresLocalNetworkPermission(): Boolean =
        Build.VERSION.SDK_INT >= 37 && context.applicationInfo.targetSdkVersion >= 37 &&
            context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED
    private fun checkLocalNetworkPermission() {
        check(!requiresLocalNetworkPermission()) { "Allow Nearby devices permission in Android app settings to connect to SMB." }
    }
    @JvmStatic fun submit(json: String) {
        val request = JSONObject(json)
        val id = request.getString("id")
        val generation = request.optLong("generation")
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val operation = request.getString("operation")
            val started = SystemClock.elapsedRealtime()
            if (operation != "savePlayback") trace("request", "operation=$operation")
            var data: JSONObject? = null
            try {
                data = execute(request)
                ensureActive()
                if (operation != "savePlayback") trace("complete", "operation=$operation elapsedMs=${SystemClock.elapsedRealtime() - started}")
                post(id, generation, data)
            } catch (_: CancellationException) {
                data?.optString("token")?.takeIf { it.isNotBlank() }?.let(::releaseStream)
            } catch (error: Exception) {
                trace("error", "operation=$operation type=${error.javaClass.simpleName}")
                post(id, generation, JSONObject(), error.message ?: error.javaClass.simpleName)
            } finally { requests.remove(id) }
        }
        requests.put(id, job)?.cancel()
        job.start()
    }
    private fun trace(stage: String, details: String) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            Log.i("XRStreamBridge", "$stage: $details")
    }
    private suspend fun execute(request: JSONObject): JSONObject {
        check(::context.isInitialized) { "Android bridge is not initialized" }
        return when (request.getString("operation")) {
            "servers" -> JSONObject().put("servers", JSONArray().apply {
                database.serverDao().getAllServers().first().forEach { put(serverJson(it)) }
            })
            "saveServer" -> {
                checkLocalNetworkPermission()
                val id = request.optLong("serverId")
                val existing = if (id != 0L) database.serverDao().getServerById(id) else null
                val alias = existing?.credentialAlias?.takeIf { it.isNotBlank() } ?: "unity.${UUID.randomUUID()}"
                val oldAuth = existing?.let { credentials.load(it.credentialAlias) }
                val password = request.optString("password").ifEmpty { oldAuth?.password.orEmpty() }
                val auth = SmbCredentials(request.optString("username"), password, request.optString("domain"))
                val server = SavedServer(id = id, serverName = request.getString("name"), serverAddress = request.getString("address"),
                    port = request.optInt("port", 445), shareName = request.optString("share"), credentialAlias = alias)
                val client = SMBClient(toConfig(server, auth))
                try { client.connect().getOrThrow() } finally { client.disconnect() }
                credentials.save(alias, auth)
                val savedId = if (existing == null) database.serverDao().insertServer(server) else {
                    database.serverDao().updateServer(server); id
                }
                JSONObject().put("serverId", savedId)
            }
            "deleteServer" -> {
                val server = server(request)
                database.serverDao().deleteServer(server); credentials.delete(server.credentialAlias)
                JSONObject()
            }
            "browse" -> {
                val server = server(request)
                val path = request.optString("path")
                val files = if (server.isLocalStorage) {
                    val client = LocalFileClient(context, server.shareName)
                    try { client.connect().getOrThrow(); client.listFiles(path).getOrThrow() } finally { client.disconnect() }
                } else {
                    checkLocalNetworkPermission()
                    val client = SMBClient(toConfig(server, credentials.load(server.credentialAlias)))
                    try { client.connect().getOrThrow(); client.listFiles(path).getOrThrow() } finally { client.disconnect() }
                }
                JSONObject().put("files", JSONArray().apply {
                    files.sortedWith(compareByDescending<blackark.app.vr.network.SMBFileItem> { it.isDirectory }.thenBy { it.name.lowercase() }).forEach {
                        put(JSONObject().put("name", it.name).put("path", it.path).put("directory", it.isDirectory)
                            .put("size", it.size).put("video", SMBClient.isVideoFile(it.name)).put("subtitlePath", it.subtitlePath ?: ""))
                    }
                })
            }
            "open" -> {
                val server = server(request)
                if (!server.isLocalStorage) checkLocalNetworkPermission()
                val path = request.getString("path")
                val config = if (server.isLocalStorage) null else toConfig(server, credentials.load(server.credentialAlias))
                val media = synchronized(this) { transport ?: RangeMediaServer(onTrace = { trace("http", it) }).also { transport = it } }
                val (token, url) = media.register { if (config == null) localMediaReader(context, path) else smbMediaReader(config, path) }
                try {
                    currentCoroutineContext().ensureActive()
                    val recent = database.videoDao().getVideoByPath(path)
                    val display = database.videoDisplaySettingsDao().getByPath(path)
                    JSONObject().put("token", token).put("url", url)
                        .put("positionMs", playbackResumePositionMs(recent?.lastPosition ?: 0, recent?.duration ?: 0))
                        .put("format", display?.videoFormat ?: "").put("stereo", display?.stereoMode ?: "")
                } catch (error: Exception) { media.release(token); throw error }
            }
            "subtitle" -> {
                val server = server(request)
                if (!server.isLocalStorage) checkLocalNetworkPermission()
                val config = if (server.isLocalStorage) null else toConfig(server, credentials.load(server.credentialAlias))
                readSubtitle(context, request.getString("path"), config)
            }
            "savePlayback" -> {
                val server = server(request)
                val path = request.getString("path")
                val existing = database.videoDao().getVideoByPath(path)
                val recent = RecentVideo(id = existing?.id ?: 0, fileName = request.getString("name"), filePath = path,
                    serverAddress = server.serverAddress, shareName = server.shareName,
                    lastPosition = request.getLong("positionMs").coerceAtLeast(0), duration = request.getLong("durationMs").coerceAtLeast(0),
                    thumbnailPath = existing?.thumbnailPath, resolvedTitle = existing?.resolvedTitle,
                    resumeThumbnailPath = existing?.resumeThumbnailPath, resumeThumbnailPositionMs = existing?.resumeThumbnailPositionMs)
                if (existing == null) database.videoDao().insertVideo(recent) else database.videoDao().updateVideo(recent)
                database.videoDisplaySettingsDao().upsert(VideoDisplaySettings(path, request.getString("format"), request.getString("stereo")))
                JSONObject()
            }
            "recent" -> JSONObject().put("videos", JSONArray().apply {
                database.videoDao().getRecentVideos().first().forEach {
                    put(JSONObject().put("name", it.fileName).put("path", it.filePath).put("positionMs", it.lastPosition).put("durationMs", it.duration))
                }
            })
            else -> error("Unsupported bridge operation")
        }
    }
    private suspend fun server(request: JSONObject): SavedServer =
        requireNotNull(database.serverDao().getServerById(request.getLong("serverId"))) { "Server was removed" }
    private fun toConfig(server: SavedServer, auth: SmbCredentials) =
        SMBConfig(server.serverAddress, server.port, server.shareName, auth.username, auth.password, auth.domain)
    private fun serverJson(server: SavedServer): JSONObject {
        val auth = if (server.isLocalStorage) SmbCredentials("", "", "") else credentials.load(server.credentialAlias)
        return JSONObject().put("id", server.id).put("name", server.serverName).put("port", server.port)
            .put("address", server.serverAddress).put("share", server.shareName).put("local", server.isLocalStorage)
            .put("username", auth.username).put("domain", auth.domain)
    }
    @JvmStatic fun chooseLocalFolder(activity: Activity, id: String, generation: Long) {
        activity.runOnUiThread {
            activity.startActivity(Intent(activity, LocalFolderPickerActivity::class.java).putExtra("id", id).putExtra("generation", generation))
        }
    }
    fun completeLocalFolder(id: String, generation: Long, uri: String?, failure: String? = null) {
        scope.launch {
            try {
                if (failure != null) { post(id, generation, JSONObject(), failure); return@launch }
                if (uri == null) { post(id, generation, JSONObject().put("cancelled", true)); return@launch }
                val server = SavedServer(serverName = "Local storage", serverAddress = LocalFileClient.LOCAL_STORAGE_ADDRESS, port = 0, shareName = uri, isLocalStorage = true)
                val existing = database.serverDao().getServerByAddress(server.serverAddress, uri)
                val savedId = existing?.id ?: database.serverDao().insertServer(server)
                post(id, generation, JSONObject().put("serverId", savedId))
            } catch (error: Exception) { post(id, generation, JSONObject(), error.message.orEmpty()) }
        }
    }
    fun postTransportError(error: Exception) = post("", -1, JSONObject(), "Media transport: ${error.javaClass.simpleName}")
    private fun post(id: String, generation: Long, data: JSONObject, error: String? = null) {
        events.add(JSONObject().put("id", id).put("generation", generation).put("success", error == null)
            .put("data", data).put("error", error ?: "").toString())
    }
}
