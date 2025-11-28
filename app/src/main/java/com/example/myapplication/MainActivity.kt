package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            "android.permission.READ_MEDIA_VIDEO"
                        } else {
                            "android.permission.READ_EXTERNAL_STORAGE"
                        }
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
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                "android.permission.READ_MEDIA_VIDEO"
                            } else {
                                "android.permission.READ_EXTERNAL_STORAGE"
                            }
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

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            com.example.myapplication.AppState.keyEvents.tryEmit(event)
        }
        return super.dispatchKeyEvent(event)
    }
}