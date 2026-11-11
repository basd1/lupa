package dev.bastian.lupa.ui

import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.bastian.lupa.R
import dev.bastian.lupa.ui.apps.AppsScreen
import dev.bastian.lupa.domain.PermissionCategory
import dev.bastian.lupa.ui.category.CategoryScreen
import dev.bastian.lupa.ui.detail.AppDetailScreen
import dev.bastian.lupa.ui.summary.SummaryScreen
import dev.bastian.lupa.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SummaryRoute

@Serializable
data object AppsRoute

@Serializable
data object SettingsRoute

@Serializable
data class CategoryRoute(val category: PermissionCategory)

@Serializable
data class AppDetailRoute(val packageName: String)

private class TopLevel(val route: Any, val labelRes: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(SummaryRoute, R.string.nav_summary, Icons.Filled.Dashboard),
    TopLevel(AppsRoute, R.string.nav_apps, Icons.Filled.Apps),
    TopLevel(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings),
)

private fun NavHostController.navigateTopLevel(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

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
                        onClick = { navController.navigateTopLevel(item.route) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = SummaryRoute, modifier = Modifier.padding(padding)) {
            composable<SummaryRoute> {
                SummaryScreen(
                    onShowApps = { navController.navigateTopLevel(AppsRoute) },
                    onOpenCategory = { navController.navigate(CategoryRoute(it)) },
                )
            }
            composable<CategoryRoute> { entry ->
                CategoryScreen(
                    category = entry.toRoute<CategoryRoute>().category,
                    onBack = { navController.popBackStack() },
                    onOpenApp = { navController.navigate(AppDetailRoute(it)) },
                )
            }
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
