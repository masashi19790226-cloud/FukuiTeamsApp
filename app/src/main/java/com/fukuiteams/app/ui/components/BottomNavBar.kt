package com.fukuiteams.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.fukuiteams.app.navigation.Routes
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.White

data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

val bottomNavItems = listOf(
    NavItem(Routes.HOME, "一面", Icons.Filled.Home),
    NavItem(Routes.GAME_DETAIL, "試合", Icons.Filled.SportsSoccer),
    NavItem(Routes.INVITATIONS, "招待", Icons.Filled.CardGiftcard),
    NavItem(Routes.NOTIFICATIONS, "通知", Icons.Filled.Notifications)
)

@Composable
fun AppBottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    // 紙面の下端らしく、二重罫線の下に少し濃い紙色の帯
    Column {
    DoubleRule()
    NavigationBar(containerColor = DividerGray) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NewsRed,
                    selectedTextColor = Ink,
                    unselectedIconColor = InkSoft,
                    unselectedTextColor = InkSoft,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
}
