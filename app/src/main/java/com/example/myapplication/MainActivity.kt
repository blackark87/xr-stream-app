package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.view.KeyEvent
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.navigation.AppNavigation
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

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
        // Log every single key press. NO FILTERS.
        // Look at Logcat for "XR_RAW_KEY" when you press buttons.
        Log.d("XR_RAW_KEY", "Key Code: ${event.keyCode} | Action: ${event.action}")

        return super.dispatchKeyEvent(event)
    }

    // 2. CATCH THUMBSTICKS (Movement)
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {

        // 1. Filter out the "Mouse Pointer" (The big numbers you saw)
        // If the source is a Class Pointer, ignore it.
        if (event.isFromSource(android.view.InputDevice.SOURCE_CLASS_POINTER)) {
            // This is the laser pointer moving. We don't care about this right now.
            return super.dispatchGenericMotionEvent(event)
        }

        // 2. Scan ALL Axis IDs to find the Stick
        // Standard Android Axes go from 0 to 48. We check them all.
        val axisList = arrayOf(
            "AXIS_X" to android.view.MotionEvent.AXIS_X, // 0
            "AXIS_Y" to android.view.MotionEvent.AXIS_Y, // 1
            "AXIS_Z" to android.view.MotionEvent.AXIS_Z, // 11
            "AXIS_RX" to android.view.MotionEvent.AXIS_RX, // 12
            "AXIS_RY" to android.view.MotionEvent.AXIS_RY, // 13
            "AXIS_RZ" to android.view.MotionEvent.AXIS_RZ, // 14
            "AXIS_HAT_X" to android.view.MotionEvent.AXIS_HAT_X, // 15
            "AXIS_HAT_Y" to android.view.MotionEvent.AXIS_HAT_Y, // 16
            "AXIS_LTRIGGER" to android.view.MotionEvent.AXIS_LTRIGGER, // 17
            "AXIS_RTRIGGER" to android.view.MotionEvent.AXIS_RTRIGGER, // 18
            "AXIS_GAS" to android.view.MotionEvent.AXIS_GAS, // 22
            "AXIS_BRAKE" to android.view.MotionEvent.AXIS_BRAKE // 23
        )

        for ((name, axisId) in axisList) {
            val value = event.getAxisValue(axisId)

            // Only log if the value is significant ( > 0.1 or < -0.1)
            // And ignore the HUGE pixel numbers (check for range -1.0 to 1.0)
            if (kotlin.math.abs(value) > 0.1f && kotlin.math.abs(value) <= 1.5f) {
                Log.d("XR_STICK_SCAN", "FOUND IT! Name: $name Value: $value")
            }
        }

        return super.dispatchGenericMotionEvent(event)
    }
}