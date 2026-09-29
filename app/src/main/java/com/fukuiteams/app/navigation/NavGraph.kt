package com.fukuiteams.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.fukuiteams.app.ui.screens.NotificationsScreen
import com.fukuiteams.app.ui.screens.ChangelogScreen
import com.fukuiteams.app.ui.screens.HowToUseScreen
import com.fukuiteams.app.ui.screens.PlayersScreen
import com.fukuiteams.app.ui.screens.RadarScreen

object Routes {
    const val HOME = "home"
    const val GAME_DETAIL = "game_detail"
    const val GAME_DETAIL_WITH_ARG = "game_detail/{gameId}"
    const val NOTIFICATIONS = "notifications"
    const val CHANGELOG = "changelog"
    const val RADAR = "radar"
    // トピック画面を分類・チームを指定して開く。category は INVITE / ALL など、team は BLOWINDS などで省略可
    const val RADAR_WITH_ARG = "radar/{category}?team={team}"
    // トピック画面を「招待」(3チームすべて)で開く
    const val RADAR_INVITES = "radar/INVITE"

    /** トピック画面を「すべての分類」で開くルート。team が null なら3チームすべて。 */
    fun radarAll(team: com.fukuiteams.app.model.Team?): String =
        "radar/ALL" + (team?.let { "?team=${it.name}" } ?: "")
    const val HOW_TO_USE = "how_to_use"
    const val PLAYERS = "players"
}

@Composable
fun AppNavHost(initialRoute: String? = null) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore("/")

    LaunchedEffect(initialRoute) {
        if (initialRoute != null) {
            // 知らないルートが来てもアプリが落ちないようにする
            runCatching { navController.navigate(initialRoute) }
        }
    }

    // 下のメニューの画面(一面・試合・選手・トピック・通知)へ切り替える。戻るボタンで一面に戻る
    fun navigateTab(route: String) {
        if (route != currentRoute) {
            navController.navigate(route) {
                popUpTo(Routes.HOME) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            AppBottomNavBar(currentRoute = currentRoute) { route -> navigateTab(route) }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenGame = { gameId -> navController.navigate("game_detail/$gameId") },
                        onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                        onOpenRadar = { team -> navController.navigate(Routes.radarAll(team)) }
                    )
                }
                composable(Routes.GAME_DETAIL) {
                    GameDetailScreen(
                        gameId = null,
                        onBack = { navController.popBackStack() },
                        onOpenInvitations = { navController.navigate(Routes.RADAR_INVITES) },
                        onOpenPlayers = { navigateTab(Routes.PLAYERS) }
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
                        onOpenPlayers = { navigateTab(Routes.PLAYERS) }
                    )
                }
                composable(Routes.RADAR) {
                    RadarScreen()
                }
                composable(
                    Routes.RADAR_WITH_ARG,
                    arguments = listOf(
                        navArgument("category") { type = NavType.StringType },
                        navArgument("team") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { entry ->
                    RadarScreen(
                        initialCategory = entry.arguments?.getString("category"),
                        initialTeam = entry.arguments?.getString("team")
                    )
                }
                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen(
                        onOpenChangelog = { navController.navigate(Routes.CHANGELOG) },
                        onOpenHowToUse = { navController.navigate(Routes.HOW_TO_USE) }
                    )
                }
                composable(Routes.CHANGELOG) {
                    ChangelogScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.HOW_TO_USE) {
                    HowToUseScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.PLAYERS) {
                    // 下のメニューの「選手」タブ。ほかのタブと同じく、左上の戻るボタンは出さない
                    PlayersScreen()
                }
            }
        }
    }
}
