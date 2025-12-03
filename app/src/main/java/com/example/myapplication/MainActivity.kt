package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
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

        logInputDevices()

        setContent {
            MyApplicationTheme {
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

    private fun logInputDevices() {
        val deviceIds = InputDevice.getDeviceIds()
        Log.i("XR_HARDWARE", "Found ${deviceIds.size} input devices:")
        for (deviceId in deviceIds) {
            val device = InputDevice.getDevice(deviceId)
            Log.i("XR_HARDWARE", " - ID: ${device?.id}, Name: ${device?.name}, Sources: ${device?.sources}, Vendor: ${device?.vendorId}, Product: ${device?.productId}")
        }
    }

    // 1. CATCH BUTTONS (A, B, X, Y, Triggers)
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.i("XR_INPUT", "KeyEvent: Code=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)}) | Action=${event.action} | Source=${event.source}")

        // Emit to AppState
        AppState.keyEvents.tryEmit(event)

        return super.dispatchKeyEvent(event)
    }

    // 2. CATCH TOUCH (Trigger might be mapped to Touch Down/Up)
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Log all touch events to diagnose "Virtual Touchscreen" behavior
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
            Log.i("XR_INPUT", "TouchEvent: Action=${event.action} | Source=${event.source} | X=${event.x}, Y=${event.y}")
        }
        return super.dispatchTouchEvent(event)
    }

    // 3. CATCH THUMBSTICKS (Movement)
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {

        // Only log action/source to avoid spamming string allocations for axes
        Log.i("XR_INPUT", "GenericMotion: Source=${event.source} | Action=${event.action} | Device=${event.device.name}")

        // --- CUSTOM CONTROLLER MAPPING ---

        // Strategy: We check standard Stick axes (X, Y, Z, RX, RY, RZ, HAT)
        // AND Scroll axes (HSCROLL, VSCROLL) which sometimes map to sticks on "Mouse" devices.
        // We ignore "Pointer Coordinates" (values > 1.5) to avoid false positives from the virtual cursor.

        val axisOfInterest = listOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_RX, MotionEvent.AXIS_RY,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_HSCROLL, MotionEvent.AXIS_VSCROLL
        )

        var maxAxisValue = 0f

        for (axisId in axisOfInterest) {
            val value = event.getAxisValue(axisId)
            // Filter: Ignore large values (coordinates) and keep the largest normalized input
            if (kotlin.math.abs(value) <= 1.5f && kotlin.math.abs(value) > kotlin.math.abs(maxAxisValue)) {
                maxAxisValue = value
            }
        }

        val threshold = 0.5f

        // Mapping: Left (Negative) -> Forward (DPAD_RIGHT). Right (Positive) -> Backward (DPAD_LEFT).
        if (kotlin.math.abs(maxAxisValue) > threshold) {
            if (maxAxisValue < -threshold && !isStickLeftProcessed) {
                // Stick Pushed LEFT
                Log.i("XR_CONTROLLER", "Action: Stick LEFT -> Seek FORWARD")
                val keyEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
                AppState.keyEvents.tryEmit(keyEvent)
                isStickLeftProcessed = true
            } else if (maxAxisValue > threshold && !isStickRightProcessed) {
                // Stick Pushed RIGHT
                Log.i("XR_CONTROLLER", "Action: Stick RIGHT -> Seek BACKWARD")
                val keyEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT)
                AppState.keyEvents.tryEmit(keyEvent)
                isStickRightProcessed = true
            }
        } else {
            isStickLeftProcessed = false
            isStickRightProcessed = false
        }

        // Trigger Logic (Button R2)
        val triggerAxes = listOf(
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS
        )

        val maxTriggerValue = triggerAxes.map { event.getAxisValue(it) }.maxOrNull() ?: 0f

        if (maxTriggerValue > threshold && !isTriggerProcessed) {
            Log.i("XR_CONTROLLER", "Action: TRIGGER -> Toggle UI")
            val keyEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_R2)
            AppState.keyEvents.tryEmit(keyEvent)
            isTriggerProcessed = true
        } else if (maxTriggerValue <= threshold) {
            isTriggerProcessed = false
        }

        return super.dispatchGenericMotionEvent(event)
    }
}
