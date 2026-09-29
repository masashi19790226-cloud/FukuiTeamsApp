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
import com.fukuiteams.app.ui.screens.ChangelogScreen
import com.fukuiteams.app.ui.screens.HowToUseScreen
import com.fukuiteams.app.ui.screens.PlayersScreen
import com.fukuiteams.app.ui.screens.RadarScreen

object Routes {
    const val HOME = "home"
    const val GAME_DETAIL = "game_detail"
    const val GAME_DETAIL_WITH_ARG = "game_detail/{gameId}"
    const val INVITATIONS = "invitations"
    const val NOTIFICATIONS = "notifications"
    const val CHANGELOG = "changelog"
    const val RADAR = "radar"
    const val RADAR_WITH_ARG = "radar/{category}"
    // トピック画面を「招待・プレゼント」で開く(旧・招待タブの代わり)
    const val RADAR_INVITES = "radar/INVITE"
    const val HOW_TO_USE = "how_to_use"
    const val PLAYERS = "players"
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
                        onOpenInvitations = { navController.navigate(Routes.RADAR_INVITES) },
                        onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                        onOpenRadar = { navController.navigate(Routes.RADAR) }
                    )
                }
                composable(Routes.GAME_DETAIL) {
                    GameDetailScreen(
                        gameId = null,
                        onBack = { navController.popBackStack() },
                        onOpenInvitations = { navController.navigate(Routes.RADAR_INVITES) },
                        onOpenPlayers = { navController.navigate(Routes.PLAYERS) }
                    )
                }
                composable(
                    Routes.GAME_DETAIL_WITH_ARG,
                    arguments = listOf(navArgument("gameId") { type = NavType.StringType })
                ) { entry ->
                    GameDetailScreen(
                        gameId = entry.arguments?.getString("gameId"),
                        onBack = { navController.popBackStack() },
                        onOpenInvitations = { navController.navigate(Routes.RADAR_INVITES) },
                        onOpenPlayers = { navController.navigate(Routes.PLAYERS) }
                    )
                }
                // 旧・招待タブの画面。下のメニューからは外したが、画面自体は残しておく
                composable(Routes.INVITATIONS) {
                    InvitationsScreen()
                }
                composable(Routes.RADAR) {
                    RadarScreen()
                }
                composable(
                    Routes.RADAR_WITH_ARG,
                    arguments = listOf(navArgument("category") { type = NavType.StringType })
                ) { entry ->
                    RadarScreen(initialCategory = entry.arguments?.getString("category"))
                }
                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen(
                        onOpenChangelog = { navController.navigate(Routes.CHANGELOG) },
                        onOpenHowToUse = { navController.navigate(Routes.HOW_TO_USE) },
                        onOpenPlayers = { navController.navigate(Routes.PLAYERS) }
                    )
                }
                composable(Routes.CHANGELOG) {
                    ChangelogScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.HOW_TO_USE) {
                    HowToUseScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.PLAYERS) {
                    PlayersScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
