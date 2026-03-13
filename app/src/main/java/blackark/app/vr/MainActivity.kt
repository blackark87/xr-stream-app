package blackark.app.vr

import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import blackark.app.vr.ui.navigation.AppNavigation
import blackark.app.vr.ui.theme.XRStreamTheme
import kotlin.math.abs
import kotlin.math.max

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val AXIS_EMIT_THRESHOLD = 0.08f
        private const val AXIS_DEADZONE_FALLBACK = 0.08f
        private const val CONTROLLER_LOG_PREFIX = "[6DoF]"
        private val X_AXES = intArrayOf(
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RX,
            MotionEvent.AXIS_HSCROLL,
        )
        private val Y_AXES = intArrayOf(
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_RY,
            MotionEvent.AXIS_VSCROLL,
            MotionEvent.AXIS_SCROLL,
        )
        private val CANDIDATE_AXES = intArrayOf(
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
            MotionEvent.AXIS_SCROLL,
        )
    }

    private var controllerAxisWasActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            XRStreamTheme(darkTheme = false) {
                val context = androidx.compose.ui.platform.LocalContext.current
                var hasHeadTrackingPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            "android.permission.HEAD_TRACKING"
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    )
                }

                var hasStoragePermission by remember {
                    val permission = "android.permission.READ_MEDIA_VIDEO"
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            permission
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

                val storageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        hasStoragePermission = isGranted
                    }
                )

                LaunchedEffect(Unit) {
                    if (!hasHeadTrackingPermission) {
                        headTrackingLauncher.launch("android.permission.HEAD_TRACKING")
                    }
                    if (!hasStoragePermission) {
                        storageLauncher.launch("android.permission.READ_MEDIA_VIDEO")
                    }
                }

                val navController = rememberNavController()
                AppNavigation(
                    navController = navController,
                    context = this
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val source = event.source
        val isDirectionalControllerKey =
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_BUTTON_THUMBL,
                KeyEvent.KEYCODE_BUTTON_THUMBR -> true

                else -> false
            }
        if (isControllerSource(source) || isDirectionalControllerKey) {
            Log.i(
                TAG,
                "$CONTROLLER_LOG_PREFIX key event received " +
                        "deviceId=${event.deviceId} source=$source(${sourceLabel(source)}) " +
                        "device=${event.device?.name ?: "virtual"} action=${event.action} " +
                        "keyCode=${KeyEvent.keyCodeToString(event.keyCode)} repeat=${event.repeatCount}",
            )
        }
        AppState.keyEvents.tryEmit(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val source = event.source
        Log.i(
            TAG,
            "$CONTROLLER_LOG_PREFIX generic motion received " +
                    "deviceId=${event.deviceId} source=$source(${sourceLabel(source)}) " +
                    "device=${event.device?.name ?: "virtual"} " +
                    "action=${MotionEvent.actionToString(event.action)} " +
                    "axes=${describeCandidateAxes(event, source)}",
        )
        if (
            event.action != MotionEvent.ACTION_MOVE &&
            event.action != MotionEvent.ACTION_HOVER_MOVE &&
            event.action != MotionEvent.ACTION_SCROLL
        ) {
            Log.i(
                TAG,
                "$CONTROLLER_LOG_PREFIX generic motion ignored due to unsupported action " +
                        "${MotionEvent.actionToString(event.action)}",
            )
            return super.dispatchGenericMotionEvent(event)
        }

        val isControllerSource = isControllerSource(source)
        val hasControllerLikeAxes = hasControllerLikeAxes(event, source)

        if (!isControllerSource && !hasControllerLikeAxes) {
            Log.i(
                TAG,
                "$CONTROLLER_LOG_PREFIX generic motion ignored due to non-controller source " +
                        "$source(${sourceLabel(source)})",
            )
            return super.dispatchGenericMotionEvent(event)
        }
        if (!isControllerSource && hasControllerLikeAxes) {
            Log.i(
                TAG,
                "$CONTROLLER_LOG_PREFIX treating non-controller source " +
                        "$source(${sourceLabel(source)}) as controller-like",
            )
        }

        // Capture across common OpenXR/controller axis profiles.
        val finalX = strongestNormalizedAxis(
            event = event,
            source = source,
            axes = X_AXES,
        )

        val finalY = strongestNormalizedAxis(
            event = event,
            source = source,
            axes = Y_AXES,
        )

        val isActive =
            abs(finalX) >= AXIS_EMIT_THRESHOLD || abs(finalY) >= AXIS_EMIT_THRESHOLD

        if (!isActive) {
            if (controllerAxisWasActive) {
                controllerAxisWasActive = false
                Log.i(
                    TAG,
                    "$CONTROLLER_LOG_PREFIX neutral axis event emitted " +
                            "deviceId=${event.deviceId} source=$source action=${event.action}",
                )
                val emitted =
                    AppState.controllerAxisEvents.tryEmit(
                        ControllerAxisEvent(
                            x = 0f,
                            y = 0f,
                            eventTimeMs = event.eventTime,
                        )
                    )
                Log.i(TAG, "$CONTROLLER_LOG_PREFIX neutral axis emission result=$emitted")
                return true
            }
            Log.i(
                TAG,
                "$CONTROLLER_LOG_PREFIX generic motion stayed inside deadzone x=$finalX y=$finalY",
            )
            return super.dispatchGenericMotionEvent(event)
        }

        controllerAxisWasActive = true
        Log.i(
            TAG,
            "$CONTROLLER_LOG_PREFIX axis event emitted " +
                    "deviceId=${event.deviceId} source=$source action=${event.action} " +
                    "x=$finalX y=$finalY",
        )
        val emitted =
            AppState.controllerAxisEvents.tryEmit(
                ControllerAxisEvent(
                    x = finalX,
                    y = finalY,
                    eventTimeMs = event.eventTime,
                )
            )
        Log.i(TAG, "$CONTROLLER_LOG_PREFIX axis emission result=$emitted")

        return true
    }

    private fun strongestNormalizedAxis(event: MotionEvent, source: Int, axes: IntArray): Float {
        var selected = 0f
        for (axis in axes) {
            val value = normalizeAxisValue(event = event, source = source, axis = axis)
            if (abs(value) > abs(selected)) {
                selected = value
            }
        }
        return selected
    }

    private fun normalizeAxisValue(event: MotionEvent, source: Int, axis: Int): Float {
        val rawValue = event.getAxisValue(axis)
        val axisRange =
            event.device?.getMotionRange(axis, source) ?: event.device?.getMotionRange(axis)

        if (shouldIgnoreAbsolutePointerAxis(
                source = source,
                axis = axis,
                rawValue = rawValue,
                axisRange = axisRange
            )
        ) {
            return 0f
        }

        val normalizedValue: Float
        val deadzone: Float

        if (axisRange != null) {
            val axisExtent = max(abs(axisRange.min), abs(axisRange.max)).coerceAtLeast(1f)
            normalizedValue = (rawValue / axisExtent).coerceIn(-1f, 1f)
            deadzone = max(axisRange.flat / axisExtent, AXIS_DEADZONE_FALLBACK)
        } else {
            normalizedValue = rawValue.coerceIn(-1f, 1f)
            deadzone = AXIS_DEADZONE_FALLBACK
        }

        return if (abs(normalizedValue) >= deadzone) normalizedValue else 0f
    }

    private fun hasControllerLikeAxes(event: MotionEvent, source: Int): Boolean {
        for (axis in X_AXES) {
            if (abs(normalizeAxisValue(event, source, axis)) >= AXIS_EMIT_THRESHOLD) {
                return true
            }
        }
        for (axis in Y_AXES) {
            if (abs(normalizeAxisValue(event, source, axis)) >= AXIS_EMIT_THRESHOLD) {
                return true
            }
        }
        return false
    }

    private fun isControllerSource(source: Int): Boolean =
        (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD

    private fun shouldIgnoreAbsolutePointerAxis(
        source: Int,
        axis: Int,
        rawValue: Float,
        axisRange: InputDevice.MotionRange?,
    ): Boolean {
        if (isControllerSource(source)) return false
        if (axis != MotionEvent.AXIS_X && axis != MotionEvent.AXIS_Y) return false
        if (abs(rawValue) <= 1.1f) return false

        val axisExtent = axisRange?.let { max(abs(it.min), abs(it.max)) } ?: abs(rawValue)
        return axisExtent > 2f
    }

    private fun sourceLabel(source: Int): String {
        val labels = mutableListOf<String>()
        if ((source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            labels += "SOURCE_JOYSTICK"
        }
        if ((source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            labels += "SOURCE_GAMEPAD"
        }
        if ((source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD) {
            labels += "SOURCE_DPAD"
        }
        if ((source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
            labels += "SOURCE_MOUSE"
        }
        if ((source and InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN) {
            labels += "SOURCE_TOUCHSCREEN"
        }
        if ((source and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) {
            labels += "SOURCE_TOUCHPAD"
        }
        if (labels.isEmpty()) {
            labels += "0x${source.toString(16)}"
        }
        return labels.joinToString("|")
    }

    private fun describeCandidateAxes(event: MotionEvent, source: Int): String {
        val parts = mutableListOf<String>()
        for (axis in CANDIDATE_AXES) {
            val rawValue = event.getAxisValue(axis)
            val normalizedValue = normalizeAxisValue(event = event, source = source, axis = axis)
            if (abs(rawValue) < 0.0001f && abs(normalizedValue) < 0.0001f) {
                continue
            }
            parts += "${MotionEvent.axisToString(axis)}=$rawValue/$normalizedValue"
        }
        return parts.joinToString().ifEmpty { "none" }
    }
}
