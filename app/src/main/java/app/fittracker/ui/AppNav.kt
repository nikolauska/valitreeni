package app.fittracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.fittracker.ui.round.RoundScreen
import app.fittracker.ui.today.TodayScreen
import app.fittracker.ui.tutorial.TutorialScreen

object Routes {
    const val TODAY = "today"
    const val ROUND = "round"
    const val TUTORIAL = "tutorial/{id}"
    fun tutorial(id: Long) = "tutorial/$id"
}

/**
 * [openRoundRequest] changes each time a reminder notification is tapped, so the round opens
 * even when the app was already running.
 */
@Composable
fun AppNav(openRoundRequest: Int, onRoundRequestHandled: () -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(openRoundRequest) {
        if (openRoundRequest > 0) {
            nav.navigate(Routes.ROUND) { launchSingleTop = true }
            onRoundRequestHandled()
        }
    }
    NavHost(nav, startDestination = Routes.TODAY) {
        composable(Routes.TODAY) {
            TodayScreen(
                onStartRound = { nav.navigate(Routes.ROUND) { launchSingleTop = true } },
                onOpenTutorial = { nav.navigate(Routes.tutorial(it)) },
            )
        }
        composable(Routes.ROUND) {
            RoundScreen(onBack = { nav.popBackStack() }, onOpenTutorial = { nav.navigate(Routes.tutorial(it)) })
        }
        composable(Routes.TUTORIAL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            TutorialScreen(exerciseId = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
        }
    }
}
