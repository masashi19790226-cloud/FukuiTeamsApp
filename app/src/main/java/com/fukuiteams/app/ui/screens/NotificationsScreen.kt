package com.fukuiteams.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import com.fukuiteams.app.data.NotificationPrefsKeys
import com.fukuiteams.app.data.notificationDataStore
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.notifications.rescheduleGameStartNotifications
import com.fukuiteams.app.ui.components.TeamBadge
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.White
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class NotificationKind(val id: String, val label: String, val defaultOn: Boolean)

private val kindDefs = listOf(
    NotificationKind("invite", "無料招待の新着", true),
    NotificationKind("news", "ニュース", true),
    NotificationKind("gamestart", "試合開始前", true)
)

@Composable
fun NotificationsScreen() {
    // ON/OFFは端末に保存され、アプリを閉じても消えない(DataStore)。
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val teamPrefs by context.notificationDataStore.data
        .map { prefs -> Team.values().associateWith { prefs[NotificationPrefsKeys.teamKey(it.name)] ?: true } }
        .collectAsState(initial = Team.values().associateWith { true })

    val kindPrefs by context.notificationDataStore.data
        .map { prefs -> kindDefs.associate { it.id to (prefs[NotificationPrefsKeys.kindKey(it.id)] ?: it.defaultOn) } }
        .collectAsState(initial = kindDefs.associate { it.id to it.defaultOn })

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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
                                Switch(
                                    checked = teamPrefs[team] ?: true,
                                    onCheckedChange = { newValue ->
                                        scope.launch {
                                            context.notificationDataStore.edit {
                                                it[NotificationPrefsKeys.teamKey(team.name)] = newValue
                                            }
                                            rescheduleGameStartNotifications(context)
                                        }
                                    },
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
                        kindDefs.forEachIndexed { index, kind ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(kind.label, style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = kindPrefs[kind.id] ?: kind.defaultOn,
                                    onCheckedChange = { newValue ->
                                        scope.launch {
                                            context.notificationDataStore.edit {
                                                it[NotificationPrefsKeys.kindKey(kind.id)] = newValue
                                            }
                                            rescheduleGameStartNotifications(context)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                                )
                            }
                            if (index != kindDefs.lastIndex) Divider(color = DividerGray)
                        }
                    }
                }
            }
        }
    }
}
