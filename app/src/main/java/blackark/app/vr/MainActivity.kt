package blackark.app.vr

import android.hardware.input.InputManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import blackark.app.vr.ui.navigation.AppNavigation
import blackark.app.vr.ui.theme.XRStreamTheme
import blackark.app.vr.remote.RuntimeConfigRegistry
import blackark.app.vr.utils.AppSettingsStore
import blackark.app.vr.utils.AppThemeMode
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    companion object {
        private const val CONTROLLER_INPUT_TAG = "ControllerInputDebug"
    }

    private val activeControllerDeviceIds = mutableSetOf<Int>()
    private var lastControllerAxisLogAtMs = 0L
    private val controllerAxisResolver = ControllerAxisResolver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettingsStore.initializeThemeMode(this)

        setContent {
            val useDarkTheme = when (AppSettingsStore.themeMode) {
                AppThemeMode.SystemDefault -> isSystemInDarkTheme()
                AppThemeMode.Light -> false
                AppThemeMode.Dark -> true
            }
            XRStreamTheme(darkTheme = useDarkTheme) {
                val context = androidx.compose.ui.platform.LocalContext.current
                var hasHeadTrackingPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            "android.permission.HEAD_TRACKING"
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    )
                }

                var hasHandTrackingPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            "android.permission.HAND_TRACKING"
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    )
                }

                val headTrackingLauncher =
                    androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                        onResult = { isGranted ->
                            hasHeadTrackingPermission = isGranted
                        }
                    )

                val handTrackingLauncher =
                    androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                        onResult = { isGranted ->
                            hasHandTrackingPermission = isGranted
                        }
                    )

                LaunchedEffect(Unit) {
                    if (!hasHeadTrackingPermission) {
                        headTrackingLauncher.launch("android.permission.HEAD_TRACKING")
                    }
                    if (!hasHandTrackingPermission) {
                        handTrackingLauncher.launch("android.permission.HAND_TRACKING")
                    }
                }

                var hasControllerLikeInputDevice by remember {
                    mutableStateOf(hasConnectedControllerLikeInputDevice())
                }

                DisposableEffect(Unit) {
                    val inputManager = getSystemService(InputManager::class.java)
                    val listener = object : InputManager.InputDeviceListener {
                        override fun onInputDeviceAdded(deviceId: Int) {
                            hasControllerLikeInputDevice = hasConnectedControllerLikeInputDevice()
                        }

                        override fun onInputDeviceRemoved(deviceId: Int) {
                            controllerAxisResolver.clearDevice(deviceId)
                            if (activeControllerDeviceIds.remove(deviceId) && activeControllerDeviceIds.isEmpty()) {
                                AppState.controllerAxisEvents.tryEmit(
                                    ControllerAxisEvent(
                                        x = 0f,
                                        y = 0f,
                                        eventTimeMs = SystemClock.uptimeMillis(),
                                    )
                                )
                            }
                            hasControllerLikeInputDevice = hasConnectedControllerLikeInputDevice()
                        }

                        override fun onInputDeviceChanged(deviceId: Int) {
                            controllerAxisResolver.clearDevice(deviceId)
                            hasControllerLikeInputDevice = hasConnectedControllerLikeInputDevice()
                        }
                    }

                    inputManager?.registerInputDeviceListener(listener, null)
                    onDispose {
                        inputManager?.unregisterInputDeviceListener(listener)
                    }
                }

                val navController = rememberNavController()
                AppNavigation(
                    navController = navController,
                    context = this,
                    hasControllerLikeInputDevice = hasControllerLikeInputDevice,
                    hasHandTrackingPermission = hasHandTrackingPermission,
                )
            }
        }
    }

    private fun handleGlobalKeyEvent(event: KeyEvent): Boolean {
        AppState.keyEvents.tryEmit(event)
        return AppState.consumePlaybackBackKeyEvents.value &&
                (event.keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                        event.keyCode == KeyEvent.KEYCODE_BACK)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleGlobalKeyEvent(event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (handleGlobalKeyEvent(event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (
            event.action != MotionEvent.ACTION_MOVE &&
            event.action != MotionEvent.ACTION_HOVER_MOVE
        ) {
            return super.dispatchGenericMotionEvent(event)
        }

        val samples = controllerAxisResolver.resolveSamples(event)
        if (samples.isEmpty()) {
            return super.dispatchGenericMotionEvent(event)
        }

        var consumed = false
        samples.forEach { sample ->
            if (sample.profileChanged) {
                Log.i(
                    CONTROLLER_INPUT_TAG,
                    "profile deviceId=${sample.deviceId} name=${event.device?.name.orEmpty()} " +
                        "source=0x${event.source.toString(16)} axes=${sample.profileLabel}",
                )
            }
            val isActive =
                abs(sample.x) >= RuntimeConfigRegistry.current.controller.axisEmitThreshold ||
                    abs(sample.y) >= RuntimeConfigRegistry.current.controller.axisEmitThreshold
            if (isActive) {
                activeControllerDeviceIds += sample.deviceId
                consumed = true
                AppState.controllerAxisEvents.tryEmit(
                    ControllerAxisEvent(
                        x = sample.x,
                        y = sample.y,
                        eventTimeMs = sample.eventTimeMs,
                    )
                )
                val now = SystemClock.elapsedRealtime()
                if (
                    now - lastControllerAxisLogAtMs >=
                    RuntimeConfigRegistry.current.controller.logIntervalMs
                ) {
                    lastControllerAxisLogAtMs = now
                    Log.d(
                        CONTROLLER_INPUT_TAG,
                        "axis profile=${sample.profileLabel} x=${"%.3f".format(sample.x)} " +
                            "y=${"%.3f".format(sample.y)}",
                    )
                }
            } else if (activeControllerDeviceIds.remove(sample.deviceId)) {
                consumed = true
                if (activeControllerDeviceIds.isEmpty()) {
                    AppState.controllerAxisEvents.tryEmit(
                        ControllerAxisEvent(
                            x = 0f,
                            y = 0f,
                            eventTimeMs = sample.eventTimeMs,
                        )
                    )
                }
                Log.d(CONTROLLER_INPUT_TAG, "axis neutral profile=${sample.profileLabel}")
            }
        }
        return if (consumed) true else super.dispatchGenericMotionEvent(event)
    }

    private fun hasConnectedControllerLikeInputDevice(): Boolean {
        for (deviceId in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(deviceId) ?: continue
            if (isControllerLikeInputDevice(device)) {
                return true
            }
        }
        return false
    }

    private fun isControllerLikeInputDevice(device: InputDevice): Boolean {
        return ControllerAxisResolver.isControllerLikeInputDevice(device)
    }

}
