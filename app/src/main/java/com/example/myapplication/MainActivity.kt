package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.navigation.AppNavigation
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.AppState

class MainActivity : ComponentActivity() {

    // State for axis-to-key emulation
    private var isStickLeftProcessed = false
    private var isStickRightProcessed = false
    private var isTriggerProcessed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                var hasHeadTrackingPermission by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            "android.permission.HEAD_TRACKING"
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    )
                }

                var hasStoragePermission by androidx.compose.runtime.remember {
                    val permission =
                        "android.permission.READ_MEDIA_VIDEO"
                    androidx.compose.runtime.mutableStateOf(
                        androidx.core.content.ContextCompat.checkSelfPermission(
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

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (!hasHeadTrackingPermission) {
                        headTrackingLauncher.launch("android.permission.HEAD_TRACKING")
                    }
                    if (!hasStoragePermission) {
                        val permission =
                            "android.permission.READ_MEDIA_VIDEO"
                        storageLauncher.launch(permission)
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

    // 1. CATCH BUTTONS (A, B, X, Y, Triggers)
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Log every single key press to ensure we see "A" button and others.
        Log.i("XR_INPUT", "KeyEvent: Code=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)}) | Action=${event.action} | Source=${event.source}")

        // Emit to AppState for listeners (VideoPlayerViewModel)
        AppState.keyEvents.tryEmit(event)

        return super.dispatchKeyEvent(event)
    }

    // 2. CATCH THUMBSTICKS (Movement)
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {

        // Log EVERYTHING first.
        Log.i("XR_INPUT", "GenericMotion: Source=${event.source} | Action=${event.action} | Device=${event.device.name}")

        // DEBUG: Scan ALL axes to see what the controller is actually emitting.
        val debugAxisList = arrayOf(
            "AXIS_X" to MotionEvent.AXIS_X,
            "AXIS_Y" to MotionEvent.AXIS_Y,
            "AXIS_Z" to MotionEvent.AXIS_Z,
            "AXIS_RX" to MotionEvent.AXIS_RX,
            "AXIS_RY" to MotionEvent.AXIS_RY,
            "AXIS_RZ" to MotionEvent.AXIS_RZ,
            "AXIS_HAT_X" to MotionEvent.AXIS_HAT_X,
            "AXIS_HAT_Y" to MotionEvent.AXIS_HAT_Y,
            "AXIS_LTRIGGER" to MotionEvent.AXIS_LTRIGGER,
            "AXIS_RTRIGGER" to MotionEvent.AXIS_RTRIGGER,
            "AXIS_GAS" to MotionEvent.AXIS_GAS,
            "AXIS_BRAKE" to MotionEvent.AXIS_BRAKE,
            "AXIS_HSCROLL" to MotionEvent.AXIS_HSCROLL,
            "AXIS_VSCROLL" to MotionEvent.AXIS_VSCROLL,
            "AXIS_GENERIC_1" to MotionEvent.AXIS_GENERIC_1
        )

        for ((name, axisId) in debugAxisList) {
            val value = event.getAxisValue(axisId)
            if (kotlin.math.abs(value) > 0.1f) {
                Log.i("XR_CONTROLLER_DEBUG", "Axis $name ($axisId) = $value")
            }
        }

        // NOTE: We REMOVED the SOURCE_TOUCHSCREEN filter because XR controllers often
        // report as Source 4098 (Touchscreen) when acting as a pointer.
        // Instead, we rely on the Magnitude of the axis value to distinguish
        // pointer coordinates (large values) from stick inputs (normalized -1 to 1).

        // --- CUSTOM CONTROLLER MAPPING ---

        // 1. SEEK LOGIC (Thumbsticks)
        // Check multiple axes to support Left/Right hands and different controller mappings.
        // Also check SCROLL axes because "Mouse" emulation often maps sticks to scrolling.
        val stickLeftX = event.getAxisValue(MotionEvent.AXIS_X)
        val stickRightX = event.getAxisValue(MotionEvent.AXIS_Z) // Often Right Stick X
        val stickRX = event.getAxisValue(MotionEvent.AXIS_RX) // Sometimes Right Stick X
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X) // D-Pad
        val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL) // Scroll Horizontal

        // Filter out "Coordinate" axes (Large values). Stick inputs are <= 1.0 (usually).
        // We use 1.5f as a safe threshold. Pointer coordinates are usually >> 1.5.
        // We iterate through candidate axes and pick the largest *valid* normalized value.
        val candidates = listOf(stickLeftX, stickRightX, stickRX, hatX, hScroll)
        val maxAxisValue = candidates
            .filter { kotlin.math.abs(it) <= 1.5f } // Ignore pointer coordinates
            .maxByOrNull { kotlin.math.abs(it) } ?: 0f

        val threshold = 0.5f

        // Mapping: Left (Negative) -> Forward (DPAD_RIGHT). Right (Positive) -> Backward (DPAD_LEFT).
        if (kotlin.math.abs(maxAxisValue) > threshold) {
            if (maxAxisValue < -threshold && !isStickLeftProcessed) {
                // Stick Pushed LEFT -> Seek Forward
                Log.i("XR_CONTROLLER", "Action: Stick LEFT -> Seek FORWARD")
                val keyEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
                AppState.keyEvents.tryEmit(keyEvent)
                isStickLeftProcessed = true
            } else if (maxAxisValue > threshold && !isStickRightProcessed) {
                // Stick Pushed RIGHT -> Seek Backward
                Log.i("XR_CONTROLLER", "Action: Stick RIGHT -> Seek BACKWARD")
                val keyEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT)
                AppState.keyEvents.tryEmit(keyEvent)
                isStickRightProcessed = true
            }
        } else {
            // Reset state when stick returns to neutral
            isStickLeftProcessed = false
            isStickRightProcessed = false
        }

        // 2. TRIGGER LOGIC (Toggle UI)
        // Check all potential trigger axes
        val rTrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
        val lTrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        val gas = event.getAxisValue(MotionEvent.AXIS_GAS)
        val brake = event.getAxisValue(MotionEvent.AXIS_BRAKE)

        val maxTriggerValue = listOf(rTrigger, lTrigger, gas, brake).maxOrNull() ?: 0f
        val isTriggerDown = maxTriggerValue > threshold

        if (isTriggerDown && !isTriggerProcessed) {
            Log.i("XR_CONTROLLER", "Action: TRIGGER -> Toggle UI")
            // Map Trigger to Button R2 (VideoPlayerViewModel listens for this)
            val keyEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_R2)
            AppState.keyEvents.tryEmit(keyEvent)
            isTriggerProcessed = true
        } else if (!isTriggerDown) {
            isTriggerProcessed = false
        }
        // ---------------------------------

        return super.dispatchGenericMotionEvent(event)
    }
}
