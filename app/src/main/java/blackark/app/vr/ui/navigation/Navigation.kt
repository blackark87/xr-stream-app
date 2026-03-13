package blackark.app.vr.ui.navigation

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import blackark.app.vr.data.database.AppDatabase
import blackark.app.vr.data.repository.ServerRepository
import blackark.app.vr.data.repository.VideoRepository
import blackark.app.vr.ui.screens.MainDashboardScreen
import blackark.app.vr.ui.screens.VideoPlayerScreen
import blackark.app.vr.ui.viewmodel.MainDashboardViewModel
import blackark.app.vr.ui.viewmodel.MainDashboardViewModelFactory

sealed class Screen(val route: String) {
    object MainDashboard : Screen("main_dashboard")
    object VideoPlayer : Screen("video_player/{filePath}/{fileName}") {
        fun createRoute(filePath: String, fileName: String): String {
            val encodedPath = java.net.URLEncoder.encode(filePath, "UTF-8").replace("+", "%20")
            val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
            return "video_player/$encodedPath/$encodedName"
        }
    }
}

@Composable
fun AppNavigation(navController: NavHostController, context: Context) {
    NavHost(
        navController = navController,
        startDestination = Screen.MainDashboard.route,
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(160)) },
        popEnterTransition = {
            fadeIn(
                animationSpec = tween(
                    durationMillis = 180,
                    delayMillis = 160
                )
            )
        },
        popExitTransition = { fadeOut(animationSpec = tween(140)) },
    ) {
        composable(Screen.MainDashboard.route) {
            // Create repositories
            val database = AppDatabase.getDatabase(context)
            val serverRepository = ServerRepository(database.serverDao())
            val videoRepository = VideoRepository(database.videoDao())

            // Create ViewModel
            val viewModel: MainDashboardViewModel = viewModel(
                factory = MainDashboardViewModelFactory(context, serverRepository, videoRepository)
            )

            MainDashboardScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = Screen.VideoPlayer.route,
            arguments = listOf(
                androidx.navigation.navArgument("filePath") {
                    type = androidx.navigation.NavType.StringType
                },
                androidx.navigation.navArgument("fileName") {
                    type = androidx.navigation.NavType.StringType
                }
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



