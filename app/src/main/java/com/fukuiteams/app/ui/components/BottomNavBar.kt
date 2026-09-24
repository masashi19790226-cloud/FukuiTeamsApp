package com.fukuiteams.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.fukuiteams.app.navigation.Routes
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.White

data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

val bottomNavItems = listOf(
    NavItem(Routes.HOME, "ホーム", Icons.Filled.Home),
    NavItem(Routes.GAME_DETAIL, "試合", Icons.Filled.SportsBaseball),
    NavItem(Routes.INVITATIONS, "無料招待", Icons.Filled.CardGiftcard),
    NavItem(Routes.NOTIFICATIONS, "通知", Icons.Filled.Notifications)
)

@Composable
fun AppBottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar(containerColor = White) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    unselectedIconColor = InkSoft,
                    unselectedTextColor = InkSoft,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
