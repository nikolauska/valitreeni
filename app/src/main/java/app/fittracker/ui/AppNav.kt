package app.fittracker.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.fittracker.R
import app.fittracker.ui.history.HistoryScreen
import app.fittracker.ui.round.RoundScreen
import app.fittracker.ui.settings.EditExerciseScreen
import app.fittracker.ui.settings.NEW_EXERCISE_ID
import app.fittracker.ui.settings.SettingsScreen
import app.fittracker.ui.theme.brand
import app.fittracker.ui.today.TodayScreen
import app.fittracker.ui.tutorial.TutorialScreen

object Routes {
    const val TODAY = "today"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ROUND = "round"
    const val TUTORIAL = "tutorial/{id}"
    const val EDIT = "edit/{id}"
    fun tutorial(id: Long) = "tutorial/$id"
    fun edit(id: Long) = "edit/$id"
}

private data class Tab(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int)

private val tabs = listOf(
    Tab(Routes.TODAY, R.string.today_title, R.drawable.ic_nav_today),
    Tab(Routes.HISTORY, R.string.history_title, R.drawable.ic_nav_history),
    Tab(Routes.SETTINGS, R.string.settings_title, R.drawable.ic_nav_settings),
)

/**
 * [openRoundRequest] changes each time a reminder notification is tapped, so the round opens
 * even when the app was already running.
 */
@Composable
fun AppNav(openRoundRequest: Int, onRoundRequestHandled: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    LaunchedEffect(openRoundRequest) {
        if (openRoundRequest > 0) {
            nav.navigate(Routes.ROUND) { launchSingleTop = true }
            onRoundRequestHandled()
        }
    }
    fun openTab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    Scaffold(
        // Each screen has its own Scaffold for the status bar; this one only makes room for the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Detail screens (round, tutorial, edit) get the full height and their own back arrow.
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { openTab(tab.route) },
                            icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                            // The selected tab wears the brand accent, like the one main action on each screen.
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.brand.accent,
                                selectedIconColor = MaterialTheme.brand.onAccent,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onStartRound = { nav.navigate(Routes.ROUND) { launchSingleTop = true } },
                    onOpenTutorial = { nav.navigate(Routes.tutorial(it)) },
                    onOpenSettings = { openTab(Routes.SETTINGS) },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onEditExercise = { nav.navigate(Routes.edit(it)) },
                    onAddExercise = { nav.navigate(Routes.edit(NEW_EXERCISE_ID)) },
                )
            }
            composable(Routes.ROUND) {
                RoundScreen(onBack = { nav.popBackStack() }, onOpenTutorial = { nav.navigate(Routes.tutorial(it)) })
            }
            composable(Routes.TUTORIAL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                TutorialScreen(exerciseId = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                EditExerciseScreen(exerciseId = entry.arguments?.getLong("id") ?: NEW_EXERCISE_ID, onDone = { nav.popBackStack() })
            }
        }
    }
}
