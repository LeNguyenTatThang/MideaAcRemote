package com.midea.acremote.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.midea.acremote.R
import com.midea.acremote.ui.screens.FavoritesScreen
import com.midea.acremote.ui.screens.RemoteScreen
import com.midea.acremote.ui.screens.ScheduleScreen
import com.midea.acremote.ui.screens.SettingsScreen
import com.midea.acremote.ui.screens.TimerScreen

object Routes {
    const val REMOTE = "remote"
    const val TIMER = "timer"
    const val FAVORITES = "favorites"
    const val SCHEDULE = "schedule"
    const val SETTINGS = "settings"
}

private data class Tab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

private val tabs = listOf(
    Tab(Routes.REMOTE, R.string.nav_remote, Icons.Filled.AcUnit),
    Tab(Routes.TIMER, R.string.nav_timer, Icons.Outlined.Timer),
    Tab(Routes.FAVORITES, R.string.nav_favorites, Icons.Filled.Star),
    Tab(Routes.SCHEDULE, R.string.nav_schedule, Icons.Outlined.Event),
    Tab(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings)
)

@Composable
fun AppRoot(viewModel: RemoteViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = backStack?.destination?.route == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.REMOTE,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.REMOTE) { RemoteScreen(viewModel) }
            composable(Routes.TIMER) { TimerScreen(viewModel) }
            composable(Routes.FAVORITES) { FavoritesScreen(viewModel) }
            composable(Routes.SCHEDULE) { ScheduleScreen(viewModel) }
            composable(Routes.SETTINGS) { SettingsScreen(viewModel) }
        }
    }
}
