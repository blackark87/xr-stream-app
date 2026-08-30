package blackark.app.vr

import androidx.xr.runtime.math.Pose
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBConfig
import blackark.app.vr.player.PlaybackSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ControllerAxisEvent(
    val x: Float,
    val y: Float,
    val eventTimeMs: Long,
)

data class DashboardPanelSize(
    val widthDp: Float = 1280f,
    val heightDp: Float = 760f,
)

object AppState {
    var smbClient: SMBClient? = null
        private set

    var smbConfig: SMBConfig? = null
        private set

    private var directPlaybackKey: String? = null
    private var directPlaybackSource: PlaybackSource.Direct? = null

    // Global key event bus
    val keyEvents = MutableSharedFlow<android.view.KeyEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // Global 6DoF controller thumbstick (left stick/hat) axis event bus.
    val controllerAxisEvents = MutableSharedFlow<ControllerAxisEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _consumePlaybackBackKeyEvents = MutableStateFlow(false)
    val consumePlaybackBackKeyEvents: StateFlow<Boolean> =
        _consumePlaybackBackKeyEvents.asStateFlow()

    private val _dashboardPanelPose = MutableStateFlow<Pose?>(null)
    val dashboardPanelPose: StateFlow<Pose?> = _dashboardPanelPose.asStateFlow()
    private val _dashboardPanelSize = MutableStateFlow(DashboardPanelSize())
    val dashboardPanelSize: StateFlow<DashboardPanelSize> = _dashboardPanelSize.asStateFlow()

    fun setSMBClient(client: SMBClient, config: SMBConfig) {
        smbClient = client
        smbConfig = config
    }

    fun setDirectPlayback(key: String, source: PlaybackSource.Direct) {
        directPlaybackKey = key
        directPlaybackSource = source
    }

    fun directPlaybackFor(key: String): PlaybackSource.Direct? =
        directPlaybackSource?.takeIf { directPlaybackKey == key }

    fun clearDirectPlayback() {
        directPlaybackKey = null
        directPlaybackSource = null
    }

    fun updateDashboardPanelPose(pose: Pose) {
        if (_dashboardPanelPose.value != pose) {
            _dashboardPanelPose.value = Pose(pose)
        }
    }

    fun updateDashboardPanelSize(widthDp: Float, heightDp: Float) {
        val nextSize = DashboardPanelSize(widthDp = widthDp, heightDp = heightDp)
        if (_dashboardPanelSize.value != nextSize) {
            _dashboardPanelSize.value = nextSize
        }
    }

    fun resetDashboardPanelPlacement() {
        _dashboardPanelPose.value = null
        _dashboardPanelSize.value = DashboardPanelSize()
    }

    fun setConsumePlaybackBackKeyEvents(enabled: Boolean) {
        _consumePlaybackBackKeyEvents.value = enabled
    }

    fun clear() {
        smbClient?.disconnect()
        smbClient = null
        smbConfig = null
        clearDirectPlayback()
        _consumePlaybackBackKeyEvents.value = false
    }
}
