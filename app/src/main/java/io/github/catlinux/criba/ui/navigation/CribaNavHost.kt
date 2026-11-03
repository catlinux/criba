package io.github.catlinux.criba.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.catlinux.criba.ui.home.HomeScreen
import io.github.catlinux.criba.ui.report.ReportScreen

object Routes {
    const val HOME = "home"
    const val REPORT = "report"
}

@Composable
fun CribaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onOpenReport = { navController.navigate(Routes.REPORT) })
        }
        composable(Routes.REPORT) {
            ReportScreen(onBack = { navController.navigateUp() })
        }
    }
}
