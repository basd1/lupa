package dev.bastian.lupa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.bastian.lupa.R
import dev.bastian.lupa.ui.apps.AppsScreen
import dev.bastian.lupa.ui.detail.AppDetailScreen
import dev.bastian.lupa.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SummaryRoute

@Serializable
data object AppsRoute

@Serializable
data object SettingsRoute

@Serializable
data class AppDetailRoute(val packageName: String)

private class TopLevel(val route: Any, val labelRes: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(SummaryRoute, R.string.nav_summary, Icons.Filled.Dashboard),
    TopLevel(AppsRoute, R.string.nav_apps, Icons.Filled.Apps),
    TopLevel(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun LupaNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    Scaffold(
        // Each screen applies the status bar inset itself.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (topLevel.any { backStack?.destination?.hasRoute(it.route::class) == true }) NavigationBar {
                topLevel.forEach { item ->
                    NavigationBarItem(
                        selected = backStack?.destination?.hasRoute(item.route::class) == true,
                        onClick = { navController.navigate(item.route) { launchSingleTop = true } },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = AppsRoute, modifier = Modifier.padding(padding)) {
            composable<SummaryRoute> { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.nav_summary)) } }
            composable<AppsRoute> {
                AppsScreen(onOpenApp = { navController.navigate(AppDetailRoute(it)) })
            }
            composable<AppDetailRoute> { entry ->
                AppDetailScreen(
                    packageName = entry.toRoute<AppDetailRoute>().packageName,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}
