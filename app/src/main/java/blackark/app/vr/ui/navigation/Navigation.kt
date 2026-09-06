package blackark.app.vr.ui.navigation

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import blackark.app.vr.XRStreamApplication
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
fun AppNavigation(
    navController: NavHostController,
    context: Context,
    hasControllerLikeInputDevice: Boolean,
    hasHandTrackingPermission: Boolean,
) {
    val appContext = remember(context) { context.applicationContext }
    val container = remember(appContext) {
        (appContext as XRStreamApplication).container
    }

    val entry by navController.currentBackStackEntryAsState()
    var readyDashboardEntryId by remember(entry?.id) { mutableStateOf<String?>(null) }
    DashboardPanelHost(
        visible = entry?.destination?.route == Screen.MainDashboard.route &&
            readyDashboardEntryId == entry?.id,
    )
    NavHost(
        navController = navController,
        startDestination = Screen.MainDashboard.route,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Screen.MainDashboard.route) { dashboardEntry ->
            // Create ViewModel
            val viewModel: MainDashboardViewModel = viewModel(
                factory = MainDashboardViewModelFactory(
                    appContext,
                    container.serverRepository,
                    container.videoRepository,
                    container.avLibraryRepository,
                    container.metadataScopeRepository,
                    container.quickAccessRepository,
                    container.credentialStore,
                )
            )

            MainDashboardScreen(
                navController = navController,
                viewModel = viewModel,
                hasControllerLikeInputDevice = hasControllerLikeInputDevice,
                hasHandTrackingPermission = hasHandTrackingPermission,
                onLayoutReady = {
                    if (navController.currentBackStackEntry?.id == dashboardEntry.id) {
                        readyDashboardEntryId = dashboardEntry.id
                    }
                },
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
