package blackark.app.vr.ui.navigation

import android.content.Context
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

    NavHost(
        navController = navController,
        startDestination = Screen.MainDashboard.route,
        enterTransition = {
            fadeIn(
                animationSpec = tween(
                    durationMillis = 220,
                    delayMillis = 60,
                )
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 140))
        },
        popEnterTransition = {
            fadeIn(
                animationSpec = tween(
                    durationMillis = 180,
                    delayMillis = 40,
                )
            )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 120))
        },
    ) {
        composable(Screen.MainDashboard.route) {
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
