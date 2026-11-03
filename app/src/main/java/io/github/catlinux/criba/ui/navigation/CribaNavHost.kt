package io.github.catlinux.criba.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.catlinux.criba.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
}

@Composable
fun CribaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen() }
    }
}
