package blackark.app.vr.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.xr.compose.platform.LocalSession
import androidx.xr.runtime.HandTrackingMode
import androidx.xr.runtime.SessionConfigureSuccess
import androidx.xr.scenecore.InputEvent

@Composable
internal fun ApplyHandTrackingPreference(
    isHandTrackingEnabled: Boolean,
    hasHandTrackingPermission: Boolean,
    logTag: String,
) {
    val session = LocalSession.current

    LaunchedEffect(session, isHandTrackingEnabled, hasHandTrackingPermission) {
        val activeSession = session ?: return@LaunchedEffect
        val desiredMode =
            if (isHandTrackingEnabled && hasHandTrackingPermission) {
                HandTrackingMode.BOTH
            } else {
                HandTrackingMode.DISABLED
            }

        if (activeSession.config.handTracking == desiredMode) {
            return@LaunchedEffect
        }

        val result = activeSession.configure(
            activeSession.config.copy(handTracking = desiredMode)
        )
        if (result !is SessionConfigureSuccess) {
            Log.w(logTag, "Failed to update XR hand tracking: ${result::class.java.simpleName}")
        }
    }
}

internal fun isSpatialInputSourceAllowed(
    isHandTrackingEnabled: Boolean,
    source: InputEvent.Source,
): Boolean {
    if (isHandTrackingEnabled) {
        return true
    }

    return source == InputEvent.Source.CONTROLLER || source == InputEvent.Source.MOUSE
}
