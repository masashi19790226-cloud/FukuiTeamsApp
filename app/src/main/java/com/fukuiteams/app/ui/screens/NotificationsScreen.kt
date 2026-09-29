package com.fukuiteams.app.ui.screens

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.fukuiteams.app.data.appVersionName
import com.fukuiteams.app.data.APP_AUTHOR
import com.fukuiteams.app.ui.theme.Ivory
import com.fukuiteams.app.ui.components.DoubleRule
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.SectionLabel
import com.fukuiteams.app.ui.components.MastheadTopBar
import com.fukuiteams.app.ui.components.MenuLinkRow
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
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.Paper
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
fun NotificationsScreen(
    onOpenChangelog: () -> Unit = {},
    onOpenHowToUse: () -> Unit = {},
    onOpenPlayers: () -> Unit = {}
) {
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
            MastheadTopBar(section = "通知設定")
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Headline("号外の届け先を\n選べます", fontSize = 22)
                Text(
                    "チームごと・内容ごとに、スマホへの通知(号外)を受け取るか選べます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                DoubleRule(modifier = Modifier.padding(top = 6.dp))
            }

            Column {
                SectionLabel("チーム", modifier = Modifier.padding(bottom = 6.dp))
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                    colors = CardDefaults.cardColors(containerColor = Paper),
                    border = BorderStroke(1.dp, Ink)
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
                                    Box(modifier = Modifier.width(5.dp).height(22.dp).background(team.color))
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
                                    colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Ivory)
                                )
                            }
                            if (index != Team.values().lastIndex) Divider(color = LineGray)
                        }
                    }
                }
            }

            Column {
                SectionLabel("通知する内容", modifier = Modifier.padding(bottom = 6.dp))
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                    colors = CardDefaults.cardColors(containerColor = Paper),
                    border = BorderStroke(1.dp, Ink)
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
                                    colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Ivory)
                                )
                            }
                            if (index != kindDefs.lastIndex) Divider(color = LineGray)
                        }
                    }
                }
            }

            // 使い方・選手の数字への入口
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("メニュー")
                MenuLinkRow(
                    title = "アプリの使い方",
                    sub = "各画面でできることの説明",
                    onClick = onOpenHowToUse
                )
                MenuLinkRow(
                    title = "選手の数字",
                    sub = "背番号・選手名と、公式サイトから取得した成績",
                    onClick = onOpenPlayers
                )
            }

            // アプリの版と更新履歴への入口
            Column {
                SectionLabel("このアプリについて", modifier = Modifier.padding(bottom = 6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Ink)
                        .background(Paper)
                        .clickable(onClick = onOpenChangelog)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("更新履歴", style = MaterialTheme.typography.bodyLarge)
                        Text("いま入っている版:v${appVersionName(context)}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                        Text("制作:$APP_AUTHOR", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = Ink)
                }
            }
        }
    }
}
