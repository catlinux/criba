package io.github.catlinux.criba.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.catlinux.criba.domain.ReportCategory
import io.github.catlinux.criba.ui.home.HomeScreen
import io.github.catlinux.criba.ui.report.ReportScreen
import io.github.catlinux.criba.ui.report.ReportViewModel
import io.github.catlinux.criba.ui.review.ReviewScreen

object Routes {
    const val HOME = "home"
    const val REPORT = "report"
    const val REVIEW = "review/{category}"
    fun review(category: ReportCategory): String = "review/${category.name}"
}

@Composable
fun CribaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onOpenReport = { navController.navigate(Routes.REPORT) })
        }
        composable(Routes.REPORT) { entry ->
            val reportViewModel: ReportViewModel = hiltViewModel(entry)
            ReportScreen(
                onBack = { navController.navigateUp() },
                onReview = { category -> navController.navigate(Routes.review(category)) },
                viewModel = reportViewModel,
            )
        }
        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument("category") { type = NavType.StringType }),
        ) { entry ->
            val category = entry.arguments?.getString("category")?.let { name ->
                ReportCategory.entries.firstOrNull { it.name == name }
            }
            val reportEntry = remember(entry) { navController.getBackStackEntry(Routes.REPORT) }
            val reportViewModel: ReportViewModel = hiltViewModel(reportEntry)
            if (category == null) {
                navController.navigateUp()
            } else {
                ReviewScreen(
                    category = category,
                    reportViewModel = reportViewModel,
                    onBack = { navController.navigateUp() },
                    onReturnToReport = { movedPaths ->
                        reportViewModel.removeMoved(movedPaths)
                        navController.navigateUp()
                    },
                )
            }
        }
    }
}
