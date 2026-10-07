package com.fukuiteams.app.ui.screens

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.fukuiteams.app.data.appVersionName
import com.fukuiteams.app.data.APP_AUTHOR
import com.fukuiteams.app.data.AppRelease
import com.fukuiteams.app.data.AppReleaseRepository
import com.fukuiteams.app.data.BbsPrefs
import com.fukuiteams.app.data.BbsSettings
import com.fukuiteams.app.data.DataStatus
import com.fukuiteams.app.data.DataStatusRepository
import com.fukuiteams.app.data.RELEASES_PAGE_URL
import com.fukuiteams.app.notifications.scheduleBbsKeywordCheck
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
    NotificationKind("gamestart", "試合開始1時間前", true),
    NotificationKind("birthday", "選手の誕生日(朝8時ごろ)", true)
)

@Composable
fun NotificationsScreen(
    onOpenChangelog: () -> Unit = {},
    onOpenHowToUse: () -> Unit = {},
    // 掲示板タブを開いて、掲示板の設定(キーワード通知の言葉・NGなど)を開く
    onOpenBbsSettings: () -> Unit = {}
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

    // 掲示板の設定(キーワード通知・一面の特集)
    val bbsSettings by remember { BbsPrefs.flow(context) }.collectAsState<BbsSettings, BbsSettings?>(initial = null)

    // データの更新状況(GitHubの自動更新)と、配布ページの最新版
    var dataStatus by remember { mutableStateOf<DataStatus?>(DataStatusRepository.latest) }
    var statusChecked by remember { mutableStateOf(false) }
    var latestRelease by remember { mutableStateOf<AppRelease?>(null) }
    LaunchedEffect(Unit) {
        dataStatus = DataStatusRepository.fetch() ?: dataStatus
        statusChecked = true
        latestRelease = AppReleaseRepository.fetchLatest()
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
            // 下のメニューの「メニュー」画面。上から 通知の設定 → 掲示板 → 公式サイト → 表示の大きさ → 使い方
            // → データの更新状況 → このアプリについて(更新履歴・最新版の入手)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Headline("通知の設定", fontSize = 22)
                Text(
                    "チームごと・内容ごとに、スマホへの通知を受け取るか選べます。掲示板のキーワード通知は、下の「掲示板」で設定します。",
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

            // 掲示板(キーワード通知・一面の特集)。言葉の追加やNGは掲示板タブの設定で行う
            bbsSettings?.let { bs ->
                Column {
                    SectionLabel("掲示板", modifier = Modifier.padding(bottom = 6.dp))
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                        colors = CardDefaults.cardColors(containerColor = Paper),
                        border = BorderStroke(1.dp, Ink)
                    ) {
                        Column {
                            MenuSwitchRow(
                                title = "キーワード通知",
                                sub = when {
                                    bs.notifyWords.isEmpty() -> "言葉が登録されていません(下の「掲示板の設定」で追加)"
                                    else -> "言葉:" + bs.notifyWords.joinToString("・") + "(約15分おきに確認)"
                                },
                                subWarn = bs.notifyEnabled && bs.notifyWords.isEmpty(),
                                checked = bs.notifyEnabled,
                                onChange = { on ->
                                    scope.launch {
                                        BbsPrefs.setNotifyEnabled(context, on)
                                        scheduleBbsKeywordCheck(context, on)
                                    }
                                }
                            )
                            Divider(color = LineGray)
                            MenuSwitchRow(
                                title = "一面の特集に掲示板の話題を出す",
                                sub = "書き込みが多い日に、件数・よく出た言葉・反応が多かった投稿を一面の特集に出します",
                                checked = bs.featureEnabled,
                                onChange = { on -> scope.launch { BbsPrefs.setFeatureEnabled(context, on) } }
                            )
                        }
                    }
                    MenuLinkRow(
                        title = "掲示板の設定を開く",
                        sub = "通知する言葉の追加・NGワード・文字サイズなど",
                        modifier = Modifier.padding(top = 6.dp),
                        onClick = onOpenBbsSettings
                    )
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

            // データの更新状況(GitHubの自動更新が最後に動いた時刻と、処理ごとの成否)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("データの更新状況")
                DataStatusBlock(dataStatus, statusChecked)
            }

            // アプリの版と更新履歴への入口
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("このアプリについて")
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
                // 配布ページ(GitHub Releases)の最新版。いま入っている版より新しければ知らせる
                val installed = appVersionName(context)
                val release = latestRelease
                val newer = release != null && AppReleaseRepository.compareVersions(release.version, installed) > 0
                MenuLinkRow(
                    title = if (newer) "新しい版があります(v${release!!.version})" else "最新版の入手(配布ページ)",
                    sub = when {
                        release == null -> "配布ページを開きます。人に渡すときもこのページを教えてください"
                        newer -> "配布ページを開いて、上書きでインストールしてください(データは消えません)"
                        else -> "配布中の最新版:v${release.version}(いまの版はこれと同じか新しい版です)"
                    },
                    onClick = {
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(RELEASES_PAGE_URL)))
                        }
                    }
                )
            }
        }
    }
}

/** メニューの枠の中の、スイッチ付きの1行(説明付き)。 */
@Composable
private fun MenuSwitchRow(
    title: String,
    sub: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subWarn: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = if (subWarn) NewsRed else InkSoft)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Ivory)
        )
    }
}

/**
 * データの更新状況。GitHubの自動更新(約20分おき)が最後に動いた時刻と、処理ごとの成否(○・×)。
 * 3時間より前なら、自動更新が止まっている可能性があるので赤字で知らせる。
 */
@Composable
private fun DataStatusBlock(status: DataStatus?, checked: Boolean) {
    val updated = status?.updatedAt
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (status == null || updated == null) {
            Text(
                if (checked) "更新状況を確認できませんでした(通信環境をご確認ください)" else "確認中…",
                style = MaterialTheme.typography.bodyMedium,
                color = if (checked) NewsRed else InkSoft
            )
        } else {
            DataStatusDetails(status, updated)
        }
    }
}

@Composable
private fun DataStatusDetails(status: DataStatus, updated: java.time.Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val zoned = updated.atZone(java.time.ZoneId.of("Asia/Tokyo"))
        val minutes = (System.currentTimeMillis() - updated.toEpochMilli()) / 60_000
        val ago = when {
            minutes < 60 -> "${minutes.coerceAtLeast(0)}分前"
            minutes < 24 * 60 -> "${minutes / 60}時間前"
            else -> "${minutes / (24 * 60)}日前"
        }
        val stale = minutes > 3 * 60
        Text(
            "最終更新:%d/%d %02d:%02d(%s)".format(zoned.monthValue, zoned.dayOfMonth, zoned.hour, zoned.minute, ago),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (stale) NewsRed else Ink
        )
        Text(
            if (stale) "3時間以上更新されていません。自動更新が止まっている可能性があります。"
            else "試合・結果・ニュース・招待・選手の数字は、GitHubで自動更新しています(およそ20分おき)。",
            style = MaterialTheme.typography.bodySmall,
            color = if (stale) NewsRed else InkSoft
        )
        if (status.steps.isNotEmpty()) {
            Text(
                status.steps.joinToString("  ") { (name, ok) -> (if (ok) "○" else "×") + name },
                style = MaterialTheme.typography.bodySmall,
                color = if (status.failedSteps.isEmpty()) Ink else NewsRed
            )
            if (status.failedSteps.isNotEmpty()) {
                Text(
                    "×は前回の自動更新で取得に失敗したものです(次の更新で直ることが多いです)。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
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
