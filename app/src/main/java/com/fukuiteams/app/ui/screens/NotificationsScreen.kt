package com.fukuiteams.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.TeamBadge
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.White

private data class NotificationKind(val id: String, val label: String)

@Composable
fun NotificationsScreen() {
    // TODO: 実装時は DataStore 等で永続化する。ここは画面確認用のローカル state。
    val teamSwitches = remember {
        mutableStateOf(Team.values().associateWith { true })
    }
    val kinds = remember {
        listOf(
            NotificationKind("invite", "無料招待の新着"),
            NotificationKind("news", "ニュース"),
            NotificationKind("gamestart", "試合開始前"),
            NotificationKind("ticketsale", "公式チケット販売開始")
        )
    }
    val kindSwitches = remember {
        mutableStateOf(mapOf("invite" to true, "news" to false, "gamestart" to true, "ticketsale" to true))
    }
    var keyword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知設定", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                border = BorderStroke(1.dp, LineGray)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("通知の表示例", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    Text("新しい無料招待:福井ブローウィンズ", style = MaterialTheme.typography.titleMedium)
                    Text("ホームゲーム ペア招待券プレゼント・締切まであと18時間", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Column {
                Text("チーム", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    border = BorderStroke(1.dp, LineGray)
                ) {
                    Column {
                        Team.values().forEachIndexed { index, team ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TeamBadge(team, size = 28.dp, fontSize = 12.sp)
                                    Text(team.displayName, style = MaterialTheme.typography.bodyLarge)
                                }
                                val checked = teamSwitches.value[team] ?: true
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { teamSwitches.value = teamSwitches.value + (team to it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                                )
                            }
                            if (index != Team.values().lastIndex) Divider(color = DividerGray)
                        }
                    }
                }
            }

            Column {
                Text("通知する内容", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    border = BorderStroke(1.dp, LineGray)
                ) {
                    Column {
                        kinds.forEachIndexed { index, kind ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(kind.label, style = MaterialTheme.typography.bodyLarge)
                                val checked = kindSwitches.value[kind.id] ?: false
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { kindSwitches.value = kindSwitches.value + (kind.id to it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                                )
                            }
                            if (index != kinds.lastIndex) Divider(color = DividerGray)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("キーワード通知(任意)", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("例:ペア 招待") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("個人譲渡の投稿でこの語句を含むものが見つかったら通知します", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }
    }
}
