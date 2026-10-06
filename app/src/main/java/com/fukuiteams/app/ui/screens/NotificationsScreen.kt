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
import com.fukuiteams.app.ui.components.ScrollJumpButtons
import com.fukuiteams.app.ui.components.ScrollJumpBottomPadding
import com.fukuiteams.app.ui.components.MenuLinkRow
import com.fukuiteams.app.ui.components.UiScale
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay
import com.fukuiteams.app.ui.theme.NewsRed
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
    NotificationKind("gamestart", "試合開始1時間前", true)
)

@Composable
fun NotificationsScreen(
    onOpenChangelog: () -> Unit = {},
    onOpenHowToUse: () -> Unit = {}
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

    // スマホの設定でこのアプリの通知が許可されているか。設定画面から戻ってきたときにも確かめ直す
    var notificationsAllowed by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    // (この画面を開いている間、2秒おきに確かめる。スマホの設定を変えて戻ってくると表示が切り替わる)
    LaunchedEffect(Unit) {
        while (true) {
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
            delay(2_000)
        }
    }

    // 画面のスクロール位置(右下の「一番上へ」「一番下へ」ボタンで使う)
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            MastheadTopBar(section = "メニュー")
        },
        floatingActionButton = { ScrollJumpButtons(scrollState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .verticalScroll(scrollState)
                // 下は、右下のボタンに最後の行が隠れないよう広めに空ける(スクロールする範囲の内側)
                .padding(bottom = ScrollJumpBottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 下のメニューの「メニュー」画面。上から 通知の設定 → 公式サイト → アプリの使い方 → 更新履歴
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Headline("通知の設定", fontSize = 22)
                Text(
                    "チームごと・内容ごとに、スマホへの通知(号外)を受け取るか選べます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                DoubleRule(modifier = Modifier.padding(top = 6.dp))
            }

            // スマホの設定で通知が止められていると、下のスイッチがONでも届かないので知らせる
            if (!notificationsAllowed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, NewsRed)
                        .background(Paper)
                        .clickable { openAppNotificationSettings(context) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "スマホの設定で、このアプリの通知がオフになっています",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = NewsRed
                    )
                    Text(
                        "このままでは、下のスイッチをONにしても通知は届きません。ここを押すと、スマホの通知設定が開きます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink
                    )
                }
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

            // 3チームの公式サイト(一面から移した)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("公式サイト")
                Team.values().forEach { team ->
                    team.officialSiteUrl?.let { url ->
                        MenuLinkRow(
                            title = team.displayName,
                            sub = "公式サイトを開く",
                            onClick = {
                                runCatching {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                }
                            }
                        )
                    }
                }
            }

            // 表示の大きさ(二本指で変えた大きさを元に戻す)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("表示の大きさ")
                Text(
                    "どの画面でも、二本指で広げると大きく、つまむと小さく表示できます(${(UiScale.MIN * 100).toInt()}%〜${(UiScale.MAX * 100).toInt()}%)。いまの大きさ:${UiScale.percentLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                MenuLinkRow(
                    title = "表示の大きさを標準(100%)に戻す",
                    onClick = { UiScale.reset(context) }
                )
            }

            // 使い方への入口(選手の数字は下のメニューの「選手」から開くので、ここには置かない)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("使い方")
                MenuLinkRow(
                    title = "アプリの使い方",
                    sub = "各画面でできることの説明",
                    onClick = onOpenHowToUse
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

/** スマホの設定の「このアプリの通知」画面を開く。開けない機種ではアプリ情報の画面を開く。 */
private fun openAppNotificationSettings(context: android.content.Context) {
    val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
    }.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
