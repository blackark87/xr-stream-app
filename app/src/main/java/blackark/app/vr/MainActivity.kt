package blackark.app.vr

import android.os.Bundle
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
        private const val AXIS_EMIT_THRESHOLD = 0.08f
        private const val AXIS_DEADZONE_FALLBACK = 0.08f
    }

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
        AppState.keyEvents.tryEmit(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (
            event.action != MotionEvent.ACTION_MOVE &&
            event.action != MotionEvent.ACTION_HOVER_MOVE &&
            event.action != MotionEvent.ACTION_SCROLL
        ) {
            return super.dispatchGenericMotionEvent(event)
        }

        val source = event.source
        val isControllerSource =
            (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                    (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD

        if (!isControllerSource) {
            return super.dispatchGenericMotionEvent(event)
        }

        // Capture across common OpenXR/controller axis profiles.
        val finalX = strongestNormalizedAxis(
            event = event,
            source = source,
            axes = intArrayOf(
                MotionEvent.AXIS_X,
                MotionEvent.AXIS_HAT_X,
                MotionEvent.AXIS_Z,
                MotionEvent.AXIS_RX,
                MotionEvent.AXIS_HSCROLL,
            ),
        )

        val finalY = strongestNormalizedAxis(
            event = event,
            source = source,
            axes = intArrayOf(
                MotionEvent.AXIS_Y,
                MotionEvent.AXIS_HAT_Y,
                MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_RY,
                MotionEvent.AXIS_VSCROLL,
            ),
        )

        if (abs(finalX) < AXIS_EMIT_THRESHOLD && abs(finalY) < AXIS_EMIT_THRESHOLD) {
            return super.dispatchGenericMotionEvent(event)
        }

        AppState.controllerAxisEvents.tryEmit(
            ControllerAxisEvent(
                x = finalX,
                y = finalY,
                eventTimeMs = event.eventTime,
            )
        )

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
        val axisRange = event.device?.getMotionRange(axis, source) ?: event.device?.getMotionRange(axis)

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
}
