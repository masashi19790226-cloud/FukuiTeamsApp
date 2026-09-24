package com.fukuiteams.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.getValue
import com.fukuiteams.app.ui.components.AppBottomNavBar
import com.fukuiteams.app.ui.screens.GameDetailScreen
import com.fukuiteams.app.ui.screens.HomeScreen
import com.fukuiteams.app.ui.screens.InvitationsScreen
import com.fukuiteams.app.ui.screens.NotificationsScreen

object Routes {
    const val HOME = "home"
    const val GAME_DETAIL = "game_detail"
    const val GAME_DETAIL_WITH_ARG = "game_detail/{gameId}"
    const val INVITATIONS = "invitations"
    const val NOTIFICATIONS = "notifications"
}

@Composable
fun AppNavHost() {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore("/")

    Scaffold(
        bottomBar = {
            AppBottomNavBar(currentRoute = currentRoute) { route ->
                if (route != currentRoute) {
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenGame = { gameId -> navController.navigate("game_detail/$gameId") },
                        onOpenInvitations = { navController.navigate(Routes.INVITATIONS) },
                        onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) }
                    )
                }
                composable(Routes.GAME_DETAIL) {
                    GameDetailScreen(
                        gameId = null,
                        onBack = { navController.popBackStack() },
                        onOpenInvitations = { navController.navigate(Routes.INVITATIONS) }
                    )
                }
                composable(
                    Routes.GAME_DETAIL_WITH_ARG,
                    arguments = listOf(navArgument("gameId") { type = NavType.StringType })
                ) { entry ->
                    GameDetailScreen(
                        gameId = entry.arguments?.getString("gameId"),
                        onBack = { navController.popBackStack() },
                        onOpenInvitations = { navController.navigate(Routes.INVITATIONS) }
                    )
                }
                composable(Routes.INVITATIONS) {
                    InvitationsScreen()
                }
                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen()
                }
            }
        }
    }
}
