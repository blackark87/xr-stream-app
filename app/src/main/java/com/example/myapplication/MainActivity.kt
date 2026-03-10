package com.example.myapplication

import android.os.Bundle
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
import com.example.myapplication.ui.navigation.AppNavigation
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    companion object {
        private const val AXIS_POINTER_VALUE_MAX = 1.5f
        private const val AXIS_EMIT_THRESHOLD = 0.08f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        AppState.keyEvents.tryEmit(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(event)
        }

        // Capture across common OpenXR/controller axis profiles and ignore pointer-like values.
        val finalX = strongestNormalizedAxis(
            event,
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RX,
            MotionEvent.AXIS_HSCROLL,
        )
        val finalY = strongestNormalizedAxis(
            event,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_RY,
            MotionEvent.AXIS_VSCROLL,
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

    private fun strongestNormalizedAxis(event: MotionEvent, vararg axes: Int): Float {
        var selected = 0f
        for (axis in axes) {
            val value = normalizeAxisValue(event.getAxisValue(axis))
            if (abs(value) > abs(selected)) {
                selected = value
            }
        }
        return selected
    }

    private fun normalizeAxisValue(value: Float): Float {
        if (abs(value) > AXIS_POINTER_VALUE_MAX) {
            return 0f
        }
        return value.coerceIn(-1f, 1f)
    }
}
