package com.fukuiteams.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Forum
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.fukuiteams.app.navigation.Routes
import com.fukuiteams.app.ui.theme.NavBarColor
import com.fukuiteams.app.ui.theme.NavSelectedIconColor
import com.fukuiteams.app.ui.theme.OnBarColor
import com.fukuiteams.app.ui.theme.OnBarSoftColor
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.White

data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

val bottomNavItems = listOf(
    NavItem(Routes.HOME, "一面", Icons.Filled.Home),
    NavItem(Routes.GAME_DETAIL, "試合", Icons.Filled.Stadium),
    // チーム:今季のチームの数字と、シーズンごとの成績・相手別の対戦成績・過去の試合・チームの歩み
    NavItem(Routes.ARCHIVE, "チーム", Icons.Filled.Groups),
    // 選手:今季の選手・次の相手の選手と、過去の選手(代表選出・元選手・表彰)
    NavItem(Routes.PLAYERS, "選手", Icons.Filled.Person),
    NavItem(Routes.RADAR, "トピック", Icons.Filled.Article),
    NavItem(Routes.BBS, "掲示板", Icons.Filled.Forum),
    NavItem(Routes.NOTIFICATIONS, "メニュー", Icons.Filled.Menu)
)

@Composable
fun AppBottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    // 紙面の下端らしく、二重罫線の下に少し濃い紙色の帯
    // 標準の NavigationBar は高さが80dpに決まっていて上下の余白が大きいので、同じ役目の帯を自前で作り、
    // アイコンと文字の上下の余白を狭くする(高さは最低56dp。文字を大きくしている端末では、そのぶん高くなる)
    Column {
    DoubleRule()
    // 地色・文字の色は着せ替えで変わる(標準は淡い紙の色、チームの色の着せ替えはチームの色に白)
    Surface(color = NavBarColor) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .heightIn(min = 56.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onNavigate(item.route) })
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // アイコンは標準(24dp)より少し小さくして、文字とのつり合いをとる
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) NavSelectedIconColor else OnBarSoftColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(LocalContentColor provides if (selected) OnBarColor else OnBarSoftColor) {
                        NavLabel(item.label, selected)
                    }
                }
            }
        }
    }
}
}

/**
 * 下のメニューの文字。7つ並ぶと「トピック」「メニュー」が入りきらないことがあるので、
 * 1行に収まるまで文字を小さくする(端が欠けないように)。
 */
@Composable
private fun NavLabel(text: String, selected: Boolean) {
    var fontSize by remember(text) { mutableStateOf(12f) }
    Text(
        text,
        fontSize = fontSize.sp,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize > 8f) fontSize -= 0.5f
        }
    )
}
