package blackark.app.vr

import android.annotation.SuppressLint
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
    private var lastControllerRawLogAtMs = 0L
    private var lastControllerRawLogSignature: String? = null
    private val controllerAxisResolver = ControllerAxisResolver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettingsStore.initializeThemeMode(this)
        AppSettingsStore.initializeHandTrackingForSession(this)
        logControllerInputState("activity-created")

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
                            logControllerDeviceChange("added", deviceId)
                            hasControllerLikeInputDevice = hasConnectedControllerLikeInputDevice()
                        }

                        override fun onInputDeviceRemoved(deviceId: Int) {
                            Log.i(CONTROLLER_INPUT_TAG, "device removed deviceId=$deviceId")
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
                            logControllerDeviceChange("changed", deviceId)
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

    override fun onResume() {
        super.onResume()
        logControllerInputState("activity-resumed")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.i(CONTROLLER_INPUT_TAG, "window-focus hasFocus=$hasFocus")
    }

    private fun handleGlobalKeyEvent(event: KeyEvent): Boolean {
        AppState.keyEvents.tryEmit(event)
        val consumed = AppState.consumePlaybackBackKeyEvents.value &&
                (event.keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                        event.keyCode == KeyEvent.KEYCODE_BACK)
        Log.d(
            CONTROLLER_INPUT_TAG,
            "key-global action=${keyActionLogValue(event.action)} " +
                "code=${KeyEvent.keyCodeToString(event.keyCode)} deviceId=${event.deviceId} " +
                "name=${event.device?.name.orEmpty()} source=0x${event.source.toString(16)} " +
                "repeat=${event.repeatCount} consumed=$consumed",
        )
        return consumed
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.d(
            CONTROLLER_INPUT_TAG,
            "key-dispatch action=${keyActionLogValue(event.action)} " +
                "code=${KeyEvent.keyCodeToString(event.keyCode)} deviceId=${event.deviceId} " +
                "name=${event.device?.name.orEmpty()} source=0x${event.source.toString(16)} " +
                "repeat=${event.repeatCount}",
        )
        onUserInteraction()
        if (handleGlobalKeyEvent(event)) {
            return true
        }
        if (window.superDispatchKeyEvent(event)) {
            return true
        }
        val decorView = window.decorView
        return event.dispatch(this, decorView.keyDispatcherState, this)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (
            event.actionMasked != MotionEvent.ACTION_MOVE &&
            event.actionMasked != MotionEvent.ACTION_HOVER_MOVE
        ) {
            logRawControllerMotion(event, reason = "unsupported-action", force = true)
            return super.dispatchGenericMotionEvent(event)
        }

        val samples = controllerAxisResolver.resolveSamples(event)
        if (samples.isEmpty()) {
            logRawControllerMotion(event, reason = "unresolved-axes")
            return super.dispatchGenericMotionEvent(event)
        }

        val resolvedHasSignal = samples.any { sample ->
            abs(sample.x) >= RuntimeConfigRegistry.current.controller.axisEmitThreshold ||
                abs(sample.y) >= RuntimeConfigRegistry.current.controller.axisEmitThreshold
        }
        val rawHasSignal = SUPPORTED_CONTROLLER_AXIS_PAIRS.any { pair ->
            abs(event.getAxisValue(pair.xAxis)) >=
                RuntimeConfigRegistry.current.controller.axisProfileThreshold ||
                abs(event.getAxisValue(pair.yAxis)) >=
                RuntimeConfigRegistry.current.controller.axisProfileThreshold
        }
        if (rawHasSignal && !resolvedHasSignal) {
            logRawControllerMotion(
                event,
                reason = "resolved-below-threshold:${samples.last().profileLabel}",
            )
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

    private fun logControllerDeviceChange(change: String, deviceId: Int) {
        val device = InputDevice.getDevice(deviceId)
        if (device == null) {
            Log.i(CONTROLLER_INPUT_TAG, "device $change deviceId=$deviceId unavailable")
            return
        }
        val ranges = device.motionRanges.joinToString(separator = ",") { range ->
            "${MotionEvent.axisToString(range.axis)}@0x${range.source.toString(16)}" +
                "[${range.min},${range.max};flat=${range.flat}]"
        }
        Log.i(
            CONTROLLER_INPUT_TAG,
            "device $change deviceId=$deviceId name=${device.name} " +
                "sources=0x${device.sources.toString(16)} controllerLike=${isControllerLikeInputDevice(device)} " +
                "ranges=$ranges",
        )
    }

    private fun logControllerInputState(reason: String) {
        val devices = InputDevice.getDeviceIds()
            .map { deviceId -> InputDevice.getDevice(deviceId) }
            .filterNotNull()
        val controllerDevices = devices.filter(::isControllerLikeInputDevice)
        Log.i(
            CONTROLLER_INPUT_TAG,
            "input-state reason=$reason hasWindowFocus=${hasWindowFocus()} " +
                "deviceCount=${devices.size} controllerCount=${controllerDevices.size} " +
                "controllers=${controllerDevices.joinToString(separator = ",") { device ->
                    "${device.id}:${device.name}:0x${device.sources.toString(16)}"
                }}",
        )
        controllerDevices.forEach { device ->
            logControllerDeviceChange("present-$reason", device.id)
        }
    }

    private fun keyActionLogValue(action: Int): String = when (action) {
        KeyEvent.ACTION_DOWN -> "DOWN"
        KeyEvent.ACTION_UP -> "UP"
        KeyEvent.ACTION_MULTIPLE -> "MULTIPLE"
        else -> action.toString()
    }

    private fun logRawControllerMotion(
        event: MotionEvent,
        reason: String,
        force: Boolean = false,
    ) {
        val now = SystemClock.elapsedRealtime()
        val signature = "$reason:${event.actionMasked}:${event.deviceId}:${event.source}"
        val logIntervalMs = RuntimeConfigRegistry.current.controller.logIntervalMs
        if (
            !force &&
            signature == lastControllerRawLogSignature &&
            now - lastControllerRawLogAtMs < logIntervalMs
        ) {
            return
        }
        lastControllerRawLogAtMs = now
        lastControllerRawLogSignature = signature

        val axes = listOf(
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_RX,
            MotionEvent.AXIS_RY,
            MotionEvent.AXIS_HSCROLL,
            MotionEvent.AXIS_VSCROLL,
        ).joinToString(separator = ",") { axis ->
            "${MotionEvent.axisToString(axis)}=${"%.3f".format(event.getAxisValue(axis))}"
        }
        val ranges = event.device?.motionRanges.orEmpty().joinToString(separator = ",") { range ->
            "${MotionEvent.axisToString(range.axis)}@0x${range.source.toString(16)}" +
                "[${range.min},${range.max};flat=${range.flat}]"
        }
        Log.w(
            CONTROLLER_INPUT_TAG,
            "motion-raw reason=$reason action=${MotionEvent.actionToString(event.action)} " +
                "deviceId=${event.deviceId} name=${event.device?.name.orEmpty()} " +
                "source=0x${event.source.toString(16)} pointers=${event.pointerCount} " +
                "history=${event.historySize} axes={$axes} ranges={$ranges}",
        )
    }

}
