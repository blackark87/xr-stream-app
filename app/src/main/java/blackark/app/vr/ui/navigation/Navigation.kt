package blackark.app.vr.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import blackark.app.vr.data.database.AppDatabase
import blackark.app.vr.data.repository.AvLibraryRepository
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
    val appContext = remember(context) { context.applicationContext }
    val database = remember(appContext) { AppDatabase.getDatabase(appContext) }
    val serverRepository = remember(database) { ServerRepository(database.serverDao()) }
    val videoRepository = remember(database) {
        VideoRepository(
            database.videoDao(),
            database.favoriteVideoDao(),
        )
    }
    val avLibraryRepository = remember(database) {
        AvLibraryRepository(
            avLibraryDao = database.avLibraryDao(),
            virtualGroupMetadataDao = database.virtualGroupMetadataDao(),
        )
    }

    NavHost(
        navController = navController,
        startDestination = Screen.MainDashboard.route,
        enterTransition = {
            androidx.compose.animation.EnterTransition.None
        },
        exitTransition = {
            androidx.compose.animation.ExitTransition.None
        },
        popEnterTransition = {
            androidx.compose.animation.EnterTransition.None
        },
        popExitTransition = {
            androidx.compose.animation.ExitTransition.None
        },
    ) {
        composable(Screen.MainDashboard.route) {
            // Create ViewModel
            val viewModel: MainDashboardViewModel = viewModel(
                factory = MainDashboardViewModelFactory(
                    appContext,
                    serverRepository,
                    videoRepository,
                    avLibraryRepository,
                )
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
                    val poppedToDashboard =
                        navController.popBackStack(Screen.MainDashboard.route, inclusive = false)
                    if (!poppedToDashboard) {
                        navController.navigate(
                            route = Screen.MainDashboard.route,
                            navOptions = navOptions {
                                launchSingleTop = true
                                popUpTo(Screen.MainDashboard.route) {
                                    inclusive = false
                                }
                            },
                        )
                    }
                }
            )
        }
    }
}
