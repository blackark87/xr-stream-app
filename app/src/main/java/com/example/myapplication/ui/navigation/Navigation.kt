package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myapplication.ui.screens.FileBrowserScreen
import com.example.myapplication.ui.screens.ServerConnectionScreen
import com.example.myapplication.ui.screens.VideoPlayerScreen

sealed class Screen(val route: String) {
    object ServerConnection : Screen("server_connection")
    object FileBrowser : Screen("file_browser")
    object VideoPlayer : Screen("video_player/{filePath}/{fileName}") {
        fun createRoute(filePath: String, fileName: String): String {
            val encodedPath = android.net.Uri.encode(filePath)
            return "video_player/$encodedPath/$fileName"
        }
    }
}

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.ServerConnection.route
    ) {
        composable(Screen.ServerConnection.route) {
            ServerConnectionScreen(
                onNavigateToFileBrowser = {
                    navController.navigate(Screen.FileBrowser.route) {
                        popUpTo(Screen.ServerConnection.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.FileBrowser.route) {
            FileBrowserScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onVideoSelected = { file ->
                    val route = Screen.VideoPlayer.createRoute(file.path, file.name)
                    navController.navigate(route)
                }
            )
        }

        composable(
            route = Screen.VideoPlayer.route,
            arguments = listOf(
                androidx.navigation.navArgument("filePath") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("fileName") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
            
            VideoPlayerScreen(
                videoFilePath = filePath,
                videoFileName = fileName,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
