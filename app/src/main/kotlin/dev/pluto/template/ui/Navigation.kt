package dev.pluto.template.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

const val HOME_ROUTE = "home"
const val WEB_ROUTE = "web"

/** The two screens. Each is a parameter, so the production app injects ViewModels and the tests inject stand-ins. */
@Composable
fun PlutoNavHost(
    controller: NavHostController,
    home: @Composable (openWeb: () -> Unit) -> Unit,
    web: @Composable () -> Unit,
) {
    NavHost(navController = controller, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) { home { controller.navigate(WEB_ROUTE) } }
        composable(WEB_ROUTE) { web() }
    }
}
