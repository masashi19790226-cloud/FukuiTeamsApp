package com.fukuiteams.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.AlertsResult
import com.fukuiteams.app.data.DataStatus
import com.fukuiteams.app.data.DataStatusRepository
import com.fukuiteams.app.data.InviteStatus
import com.fukuiteams.app.data.InviteStatusView
import com.fukuiteams.app.data.inviteStatus
import com.fukuiteams.app.data.inviteStatusDataStore
import com.fukuiteams.app.data.saveInviteStatus
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.launch
import com.fukuiteams.app.data.sourceLabel
import com.fukuiteams.app.ui.components.SourceTag
import com.fukuiteams.app.data.InvitationAlertsRepository
import com.fukuiteams.app.data.NewsAlertsRepository
import com.fukuiteams.app.data.RemoteInvitationAlert
import com.fukuiteams.app.data.eventInstant
import com.fukuiteams.app.data.isLikelyClosed
import com.fukuiteams.app.data.isTooOld
import com.fukuiteams.app.data.timeLabel
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.DoubleRule
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.MastheadTopBar
import com.fukuiteams.app.ui.components.SectionLabel
import com.fukuiteams.app.ui.components.TeamSelectorRow
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.Ivory
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper

/** トピック画面の分類。 */
// 並び順がそのまま絞り込みボタンの順になる(招待をニュースの左に置く)
private enum class RadarCategory(val label: String) {
    INVITE("招待"),
    NEWS("ニュース"),
    TICKET("チケット"),
    EVENT("イベント"),
    OTHER("その他")
}

/** トピック画面に並べる1件。元がニュースか招待情報かも持っておく。 */
private data class RadarItem(
    val alert: RemoteInvitationAlert,
    val category: RadarCategory,
    val fromInvitations: Boolean
)

// 見出し・本文抜粋に含まれる言葉で分類する(上から順に当てはめる)
private val INVITE_WORDS = listOf("招待", "プレゼント", "抽選", "当選", "無料")
private val TICKET_WORDS = listOf(
    "チケット", "ticket", "TICKET", "前売", "先行販売", "先行抽選", "一般販売", "販売開始", "発売", "完売",
    "当日券", "観戦券", "座席", "シーズンシート", "リセール", "Bリーグチケット"
)
private val EVENT_WORDS = listOf("イベント", "ファン", "感謝", "観戦会", "パブリックビューイング", "サイン会", "握手", "キャンペーン", "フェス", "祭", "体験", "教室")
private val OTHER_WORDS = listOf("出演", "放送", "中継", "グッズ", "募集", "ボランティア", "スポンサー", "パートナー")

private fun classify(alert: RemoteInvitationAlert, fromInvitations: Boolean): RadarCategory {
    val text = "${alert.title} ${alert.snippet}"
    return when {
        // 招待情報として集めたものは、すべて「招待」
        fromInvitations -> RadarCategory.INVITE
        INVITE_WORDS.any { text.contains(it) } -> RadarCategory.INVITE
        TICKET_WORDS.any { text.contains(it) } -> RadarCategory.TICKET
        EVENT_WORDS.any { text.contains(it) } -> RadarCategory.EVENT
        OTHER_WORDS.any { text.contains(it) } -> RadarCategory.OTHER
        else -> RadarCategory.NEWS
    }
}

// 2日以内に掲載(または検知)されたものを新着とする
private const val NEW_WITHIN_MILLIS = 2L * 24 * 60 * 60 * 1000

private fun RemoteInvitationAlert.isNewArrival(): Boolean =
    eventInstant()?.let { System.currentTimeMillis() - it.toEpochMilli() < NEW_WITHIN_MILLIS } ?: false

/**
 * トピック(旧RADAR)。既存のニュース(news_raw.json)と招待情報(invitations_raw.json)をまとめ、
 * 「ニュース・チケット・イベント・招待・その他」に分けて絞り込めるようにした画面。
 * 初期表示はブローウィンズ。チーム切替で他チームや「すべて」も見られる。
 * 旧・招待タブの役割もここに移した:「招待」では受付中と過去の招待を切り替えられる。
 * initialCategory に "INVITE" を渡すと招待、"ALL" を渡すとすべての分類で開く(チームは initialTeam、なければ3チームすべて)。
 */
@Composable
fun RadarScreen(initialCategory: String? = null, initialTeam: String? = null) {
    val context = LocalContext.current
    val startCategory = remember(initialCategory) {
        RadarCategory.values().find { it.name.equals(initialCategory, ignoreCase = true) }
    }
    // 下のメニューから開いたときはブローウィンズ。一面・通知などから分類を指定して開いたときは、
    // 指定のチーム(なければ3チームすべて)
    var selectedTeam by remember {
        mutableStateOf<Team?>(
            when {
                initialTeam != null -> Team.values().find { it.name == initialTeam }
                initialCategory != null -> null
                else -> Team.BLOWINDS
            }
        )
    }
    var selectedCategory by remember { mutableStateOf(startCategory) }
    // 招待の中の切り替え(0 = 受付中、1 = 過去の招待)
    var inviteTab by remember { mutableStateOf(0) }
    var newsResult by remember { mutableStateOf<AlertsResult?>(null) }
    var inviteResult by remember { mutableStateOf<AlertsResult?>(null) }
    // 自動更新(GitHub)が最後に動いた時刻
    var dataStatus by remember { mutableStateOf<DataStatus?>(null) }
    // 招待ごとの「応募済み」「応募不要」の記録
    val statusPrefs by context.inviteStatusDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
    val scope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()

    suspend fun refresh() {
        newsResult = NewsAlertsRepository.fetch()
        inviteResult = InvitationAlertsRepository.fetch()
        dataStatus = DataStatusRepository.fetch()
    }

    LaunchedEffect(Unit) { refresh() }

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            refresh()
            pullToRefreshState.endRefresh()
        }
    }

    val loading = newsResult == null || inviteResult == null
    val newsItems = (newsResult as? AlertsResult.Success)?.items ?: emptyList()
    val inviteItems = (inviteResult as? AlertsResult.Success)?.items ?: emptyList()

    // 同じ記事がニュースと招待の両方にあるときは、招待として1件だけ出す
    val inviteLinks = inviteItems.map { it.link }.filter { it.isNotBlank() }.toSet()
    val allItems = (
        inviteItems.map { RadarItem(it, classify(it, true), true) } +
            newsItems.filterNot { it.link.isNotBlank() && it.link in inviteLinks }
                .map { RadarItem(it, classify(it, false), false) }
        )
        .filterNot { it.alert.isTooOld() }
    // 新しい順(日時が分からないものは最後)。並べ方は既存の sortedNewestFirst() と同じ
    val sorted = allItems.sortedWith(
        compareByDescending<RadarItem> { it.alert.eventInstant() != null }
            .thenByDescending { it.alert.eventInstant() }
    )
    val teamItems = sorted.filter { selectedTeam == null || it.alert.teamId == selectedTeam?.name }
    val categoryItems = teamItems.filter { selectedCategory == null || it.category == selectedCategory }
    // 招待は、旧・招待タブと同じ判定(isLikelyClosed)で受付中と過去に分ける
    val openInvites = categoryItems.filterNot { it.alert.isLikelyClosed() }
    val pastInvites = categoryItems.filter { it.alert.isLikelyClosed() }
    val isInviteView = selectedCategory == RadarCategory.INVITE
    val shown = when {
        !isInviteView -> categoryItems
        inviteTab == 0 -> openInvites
        else -> pastInvites
    }
    val newCount = teamItems.count { it.alert.isNewArrival() }

    Scaffold(
        topBar = {
            MastheadTopBar(
                section = "トピック",
                edition = selectedTeam?.let { "${it.displayName}版" },
                actions = {
                    IconButton(onClick = { pullToRefreshState.startRefresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "更新")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    TeamSelectorRow(
                        selectedTeam = selectedTeam,
                        onSelect = { t -> selectedTeam = t },
                        showAll = true,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel(selectedTeam?.let { "${it.displayName}のトピック" } ?: "3チームのトピック", red = true)
                        Headline(
                            when {
                                loading -> "情報を集めています"
                                isInviteView && openInvites.isEmpty() -> "受付中の招待は\nいまのところなし"
                                isInviteView -> "無料招待 ${openInvites.size}件\n受付中"
                                teamItems.isEmpty() -> "いまのところ\n情報はありません"
                                newCount > 0 -> "新着 ${newCount}件\n全${teamItems.size}件"
                                else -> "情報 ${teamItems.size}件"
                            },
                            fontSize = 22
                        )
                        Text(
                            "ニュースと無料招待の情報をまとめて表示します。2日以内のものに NEW が付きます。行をタップすると元の記事を開きます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft
                        )
                        if (!loading) {
                            val (updatedText, stale) = updatedLabel(dataStatus)
                            Text(
                                updatedText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (stale) NewsRed else Ink
                            )
                        }
                        DoubleRule(modifier = Modifier.padding(top = 4.dp))
                    }
                }
                item {
                    // 分類で絞り込み(横にスクロールできる)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            CategoryChip("すべて ${teamItems.size}", selectedCategory == null) { selectedCategory = null }
                        }
                        items(RadarCategory.values().toList()) { cat ->
                            val count = teamItems.count { it.category == cat }
                            CategoryChip("${cat.label} $count", selectedCategory == cat) {
                                selectedCategory = if (selectedCategory == cat) null else cat
                                inviteTab = 0
                            }
                        }
                    }
                }
                if (isInviteView) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                InviteTab("受付中 ${openInvites.size}", inviteTab == 0, Modifier.weight(1f)) { inviteTab = 0 }
                                InviteTab("過去の招待 ${pastInvites.size}", inviteTab == 1, Modifier.weight(1f)) { inviteTab = 1 }
                            }
                            Text(
                                if (inviteTab == 0) "応募条件・締切は各記事でご確認ください。"
                                else "書かれている試合日・締切日が過ぎたもの、日付がないものは見つけてから14日たったものです。半年より前の情報は表示しません。",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft
                            )
                        }
                    }
                }
                if (selectedCategory == RadarCategory.TICKET) {
                    item { TicketSearchCard(selectedTeam) { url -> openRadarUrl(context, url) } }
                }
                if (newsResult is AlertsResult.Failure || inviteResult is AlertsResult.Failure) {
                    item {
                        Text(
                            "一部の情報を取得できませんでした(通信環境をご確認ください)。下に引っ張ると再読み込みします。",
                            style = MaterialTheme.typography.bodySmall,
                            color = NewsRed
                        )
                    }
                }
                if (loading) {
                    item { Text("読み込み中…", style = MaterialTheme.typography.bodySmall, color = InkSoft) }
                } else if (shown.isEmpty()) {
                    item {
                        Text(
                            "この分類の情報はありません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(shown) { radarItem ->
                        val isInvite = radarItem.category == RadarCategory.INVITE
                        RadarRow(
                            radarItem,
                            showClosedNote = !isInviteView,
                            inviteStatus = if (isInvite) radarItem.alert.inviteStatus(statusPrefs) else null,
                            onSetStatus = { status -> scope.launch { saveInviteStatus(context, radarItem.alert, status) } },
                            onClick = { openRadarUrl(context, radarItem.alert.link) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun InviteTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        modifier = modifier
            .border(1.dp, Ink)
            .background(if (selected) Ink else Paper)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        color = if (selected) Ivory else Ink,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@Composable
private fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        (if (selected) "✓ " else "") + label,
        modifier = Modifier
            .border(1.dp, Ink)
            .background(if (selected) Ink else Paper)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = if (selected) Ivory else Ink,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
        maxLines = 1,
        softWrap = false
    )
}

/**
 * 「チケット」で絞り込んだときに出す案内。公式X(旧Twitter)の投稿はアプリで自動取得できないため、
 * Xでチーム名とチケットを検索するボタンを出す。
 */
@Composable
private fun TicketSearchCard(selectedTeam: Team?, onOpen: (String) -> Unit) {
    val teams = selectedTeam?.let { listOf(it) } ?: Team.values().toList()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "公式Xのチケット情報は、Xの仕組み上アプリで自動取得できません。下のボタンで、Xの最新の投稿を検索できます。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        teams.forEach { team ->
            Text(
                "Xで「${team.displayName} チケット」を見る ›",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = team.color,
                modifier = Modifier
                    .clickable {
                        val query = java.net.URLEncoder.encode("${team.displayName} チケット", "UTF-8")
                        onOpen("https://x.com/search?q=$query&f=live")
                    }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .border(1.dp, Ink)
            .background(if (selected) Ink else Paper)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        color = if (selected) Ivory else Ink,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
        maxLines = 1,
        softWrap = false
    )
}

@Composable
private fun RadarRow(
    item: RadarItem,
    showClosedNote: Boolean = true,
    inviteStatus: InviteStatusView? = null,
    onSetStatus: (InviteStatus) -> Unit = {},
    onClick: () -> Unit
) {
    val alert = item.alert
    val team = Team.values().find { it.name == alert.teamId }
    val hasLink = alert.link.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .then(if (hasLink) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (alert.isNewArrival()) SectionLabel("NEW", red = true)
            when (inviteStatus?.status) {
                InviteStatus.APPLIED -> SectionLabel("応募済み")
                InviteStatus.NOT_NEEDED -> SectionLabel("応募不要")
                else -> {}
            }
            SourceTag(alert.sourceLabel())
            Text(
                item.category.label,
                modifier = Modifier.border(1.dp, Ink).padding(horizontal = 5.dp, vertical = 1.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink,
                maxLines = 1,
                softWrap = false
            )
            if (team != null) {
                Box(modifier = Modifier.width(4.dp).height(12.dp).background(team.color))
                Text(
                    team.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = team.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
        Text(alert.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (alert.snippet.isNotBlank()) {
            Text("「${alert.snippet}」", style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 3)
        }
        val details = listOf(
            alert.timeLabel().ifBlank { "日時不明" },
            if (showClosedNote && item.fromInvitations && alert.isLikelyClosed()) "受付終了の可能性" else ""
        ).filter { it.isNotBlank() }.joinToString("・")
        Text(details, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        if (inviteStatus != null) {
            // 招待は「応募済み」「応募不要」を押して記録できる(もう一度押すと外れる)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip("応募済み", inviteStatus.status == InviteStatus.APPLIED) {
                    onSetStatus(if (inviteStatus.status == InviteStatus.APPLIED) InviteStatus.NONE else InviteStatus.APPLIED)
                }
                StatusChip("応募不要", inviteStatus.status == InviteStatus.NOT_NEEDED) {
                    onSetStatus(if (inviteStatus.status == InviteStatus.NOT_NEEDED) InviteStatus.NONE else InviteStatus.NOT_NEEDED)
                }
                if (inviteStatus.autoDetected) {
                    Text("本文から自動判定", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                }
            }
        }
        Text(
            if (hasLink) "元の記事を開く ›" else "元の記事のリンクはありません",
            style = MaterialTheme.typography.labelMedium,
            color = if (hasLink) Ink else LineGray
        )
    }
}

/**
 * 「最終更新:9/29 14:15(10分前)」の表示と、古すぎる(3時間より前)かどうか。
 * 時刻は GitHub の自動更新(ニュース・招待の収集)が最後に動いた時刻。
 */
private fun updatedLabel(status: DataStatus?): Pair<String, Boolean> {
    if (status == null) return "最終更新:確認できませんでした" to false
    val updated = status.updatedAt ?: return "最終更新:確認できませんでした" to true
    val zoned = updated.atZone(java.time.ZoneId.of("Asia/Tokyo"))
    val minutes = (System.currentTimeMillis() - updated.toEpochMilli()) / 60_000
    val ago = when {
        minutes < 60 -> "${minutes.coerceAtLeast(0)}分前"
        minutes < 24 * 60 -> "${minutes / 60}時間前"
        else -> "${minutes / (24 * 60)}日前"
    }
    val base = "最終更新:%d/%d %02d:%02d(%s)".format(zoned.monthValue, zoned.dayOfMonth, zoned.hour, zoned.minute, ago)
    return when {
        minutes > 3 * 60 -> "$base・自動更新が止まっている可能性があります" to true
        status.failedSteps.isNotEmpty() -> "$base・取得に失敗:${status.failedSteps.joinToString("、")}" to true
        else -> base to false
    }
}

private fun openRadarUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        // ブラウザが見つからないなどで開けないときは何もしない(アプリは落とさない)
    }
}
