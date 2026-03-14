package blackark.app.vr

import androidx.xr.runtime.math.Pose
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBConfig
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

object AppState {
    var smbClient: SMBClient? = null
        private set

    var smbConfig: SMBConfig? = null
        private set

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

    private val _dashboardPanelPose = MutableStateFlow<Pose?>(null)
    val dashboardPanelPose: StateFlow<Pose?> = _dashboardPanelPose.asStateFlow()

    fun setSMBClient(client: SMBClient, config: SMBConfig) {
        smbClient = client
        smbConfig = config
    }

    fun updateDashboardPanelPose(pose: Pose) {
        if (_dashboardPanelPose.value != pose) {
            _dashboardPanelPose.value = Pose(pose)
        }
    }

    fun clear() {
        smbClient?.disconnect()
        smbClient = null
        smbConfig = null
    }
}
