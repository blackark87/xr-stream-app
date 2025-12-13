package com.example.myapplication

import android.os.Bundle
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

class MainActivity : ComponentActivity() {

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
}
