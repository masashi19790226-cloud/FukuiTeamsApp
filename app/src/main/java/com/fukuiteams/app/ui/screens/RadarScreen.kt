package com.fukuiteams.app.ui.screens

import com.fukuiteams.app.data.PublicViewingsRepository
import com.fukuiteams.app.ui.components.PublicViewingList
import com.fukuiteams.app.ui.components.PublicViewingXSearch
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.fukuiteams.app.ui.components.RemoteThumbnail
import com.fukuiteams.app.data.InvitationAlertsRepository
import com.fukuiteams.app.data.NewsAlertsRepository
import com.fukuiteams.app.data.RemoteInvitationAlert
import com.fukuiteams.app.data.eventInstant
import com.fukuiteams.app.data.isLikelyClosed
import com.fukuiteams.app.data.isTooOld
import com.fukuiteams.app.data.timeLabel
import com.fukuiteams.app.data.TeamSelection
import com.fukuiteams.app.data.DataRefresher
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.DoubleRule
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.MastheadTopBar
import com.fukuiteams.app.ui.components.ScrollJumpButtons
import com.fukuiteams.app.ui.components.ScrollJumpBottomPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import com.fukuiteams.app.ui.components.SectionLabel
import com.fukuiteams.app.ui.components.TeamSelectorRow
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.Ivory
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper

/** トピック画面の分類。 */
// 並び順がそのまま絞り込みボタンの順になる。
// 以前は、どれにも当てはまらない記事がすべて入る「ニュース」があり、ほとんどの記事がそこに偏っていたので、
// 中身(試合・グッズなど)で分けるようにした
private enum class RadarCategory(val label: String) {
    INVITE("招待"),
    GAME("試合"),
    TICKET("チケット"),
    EVENT("イベント"),
    GOODS("グッズ"),
    OTHER("その他")
}

/** トピック画面に並べる1件。元がニュースか招待情報かも持っておく。 */
private data class RadarItem(
    val alert: RemoteInvitationAlert,
    val category: RadarCategory,
    val fromInvitations: Boolean,
    /** 一面のニュース欄にも出ている記事(ニュースとして集めたもの)なら true */
    val inNews: Boolean,
    /** 同じ記事が別のサイトにも載っていた件数(この1件にまとめた数) */
    val sameCount: Int = 0,
    /** まとめた記事の引用元(例「au Webポータル」) */
    val sameSources: List<String> = emptyList()
) {
    /** この分類で絞り込んだときに出すか(null は「すべて」)。 */
    fun matches(cat: RadarCategory?): Boolean = cat == null || category == cat
}

// 見出し・本文抜粋に含まれる言葉で分類する(上から順に当てはめる)
// 招待は「試合の観戦チケットが無料でもらえる」言い方があるものだけ(scripts/invite_filter.py と同じ考え方)。
// 以前は「プレゼント」「抽選」「無料」だけで招待にしていたため、来場者プレゼントや入会特典なども混ざっていた
private val INVITE_RE = Regex(
    "無料招待|ご招待(?!券は不要)|招待(します|いたします|企画|キャンペーン|席|チケット)|" +
        "無料観戦|観戦無料|無料で(ご)?観戦|" +
        "(観戦|ホームゲーム|試合)?(チケット|観戦券|招待券).{0,15}(プレゼント|進呈|差し上げ|配布|お渡し|当た)|" +
        "(ペア|\\d+組).{0,15}(招待|プレゼント)|\\d+名(様)?.{0,15}招待"
)
private val NOT_INVITE_RE = Regex(
    "来場者プレゼント|来場プレゼント|入会|スクール|アンバサダー|会員特典|" +
        "開催しました|実施しました|終了しました|" +
        "ファンクラブ.{0,20}招待券|招待券.{0,15}(利用方法|引換|ご利用)|ご招待券は不要"
)

/**
 * 同じ記事かどうかを見分けるための見出しの頭。サイト名(「 - au Webポータル」「 | 福井新聞」など)や
 * 空白・記号を除いた最初の20文字が同じなら、同じ記事とみなす。
 */
private fun sameArticleKey(alert: RemoteInvitationAlert): String {
    val title = alert.title
        .split(" - ", " | ", "｜", " – ").first()
        .replace(Regex("[\\s\u3000、。・「」『』【】()（）!！?？…\\.,]"), "")
    return alert.teamId + ":" + title.take(20)
}

/** 無料招待(観戦チケットが無料でもらえる情報)か。 */
private fun isRealInvite(text: String): Boolean = !NOT_INVITE_RE.containsMatchIn(text) && INVITE_RE.containsMatchIn(text)
private val TICKET_RE = Regex(
    "チケット|ticket|TICKET|前売|先行販売|先行抽選|一般販売|当日券|観戦券|座席|シーズンシート|リセール|立ち見"
)
// 試合の結果・お知らせ・試合の話題
private val GAME_RE = Regex(
    "勝|敗|引き分け|第\\d+節|開幕|戦[】」 ]|戦$|vs|VS|ＶＳ|試合|得点|ゴール|ハイライト|速報|プレーオフ|" +
        "優勝|昇格|連勝|連敗|黒星|白星|スコア|MATCH|結果|GAME|Game|MVP|FINAL SCORE|PHOTO|フォト"
)
private val EVENT_RE = Regex("イベント|ファン|感謝|観戦会|パブリックビューイング|サイン会|握手|キャンペーン|フェス|祭|体験|教室|交流会")
private val GOODS_RE = Regex("グッズ|販売|発売|ユニフォーム|ユニ|限定|メニュー|コラボ商品|ウォッチ|タオル|ストア")

private fun classify(alert: RemoteInvitationAlert, fromInvitations: Boolean): RadarCategory {
    val text = "${alert.title} ${alert.snippet}"
    return when {
        // 招待情報として集めたものも、ニュースも、無料招待の言い方があるときだけ「招待」
        // (公式ストアの¥0チケット・招待特設ページは、それ自体が招待の受付なので必ず「招待」)
        fromInvitations && alert.sourceLabel().let { it.contains("公式ストア") || it.contains("特設") } -> RadarCategory.INVITE
        isRealInvite(text) -> RadarCategory.INVITE
        TICKET_RE.containsMatchIn(text) -> RadarCategory.TICKET
        GAME_RE.containsMatchIn(text) -> RadarCategory.GAME
        EVENT_RE.containsMatchIn(text) -> RadarCategory.EVENT
        GOODS_RE.containsMatchIn(text) -> RadarCategory.GOODS
        else -> RadarCategory.OTHER
    }
}

// 2日以内に掲載(または検知)されたものを新着とする
private const val NEW_WITHIN_MILLIS = 2L * 24 * 60 * 60 * 1000

private fun RemoteInvitationAlert.isNewArrival(): Boolean =
    eventInstant()?.let { System.currentTimeMillis() - it.toEpochMilli() < NEW_WITHIN_MILLIS } ?: false

/**
 * トピック(旧RADAR)。既存のニュース(news_raw.json)と招待情報(invitations_raw.json)をまとめ、
 * 「ニュース・チケット・イベント・招待・その他」に分けて絞り込めるようにした画面。
 * 初期表示は「すべて」(3チーム)。チーム切替で1チームに絞り込める。
 * 旧・招待タブの役割もここに移した:「招待」では受付中と過去の招待を切り替えられる。
 * initialCategory に "INVITE" を渡すと招待、"ALL" を渡すとすべての分類で開く(チームは initialTeam、なければ3チームすべて)。
 */
@Composable
fun RadarScreen(initialCategory: String? = null, initialTeam: String? = null) {
    val context = LocalContext.current
    val startCategory = remember(initialCategory) {
        RadarCategory.values().find { it.name.equals(initialCategory, ignoreCase = true) }
    }
    // 最初は3チームすべて(「すべて」)。一面などからチームを指定して開いたときは、そのチーム
    // 選んでいるチーム(null = すべて)。ほかのタブと共通なので、タブを切り替えても変わらない。
    // 一面などからチームを指定して開いたときは、そのチームにする
    LaunchedEffect(initialTeam) {
        initialTeam?.let { id -> Team.values().find { it.name == id } }?.let { TeamSelection.select(it) }
    }
    val selectedTeam = TeamSelection.current
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
        // ニュースの中のパブリックビューイングの記事と、手で登録した分も読み直す
        (newsResult as? AlertsResult.Success)?.let { PublicViewingsRepository.updateFromNews(it.items) }
        PublicViewingsRepository.refresh()
        inviteResult = InvitationAlertsRepository.fetch()
        dataStatus = DataStatusRepository.fetch()
    }

    LaunchedEffect(Unit) { refresh() }

    // 手動で更新したときは、アプリのすべての情報を読み直す
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            val d = DataRefresher.refreshAll(context)
            newsResult = d.news
            inviteResult = d.invitations
            dataStatus = d.status
            pullToRefreshState.endRefresh()
        }
    }

    val loading = newsResult == null || inviteResult == null
    val newsItems = (newsResult as? AlertsResult.Success)?.items ?: emptyList()
    val inviteItems = (inviteResult as? AlertsResult.Success)?.items ?: emptyList()

    // 同じ記事がニュースと招待の両方にあるときは、招待として1件だけ出す(「ニュース」の絞り込みにも出す)
    val inviteLinks = inviteItems.map { it.link }.filter { it.isNotBlank() }.toSet()
    val newsLinks = newsItems.map { it.link }.filter { it.isNotBlank() }.toSet()
    val allItems = (
        inviteItems.map { RadarItem(it, classify(it, true), true, inNews = it.link.isNotBlank() && it.link in newsLinks) } +
            newsItems.filterNot { it.link.isNotBlank() && it.link in inviteLinks }
                .map { RadarItem(it, classify(it, false), false, inNews = true) }
        )
        // 古い情報の除外は招待だけにかける。ニュースは一面と同じものをすべて出す
        // (ニュースは自動更新の側で45日より前のものを除いている)
        .filterNot { !it.inNews && it.alert.isTooOld() }
    // 新しい順(日時が分からないものは最後)。並べ方は既存の sortedNewestFirst() と同じ
    val sortedAll = allItems.sortedWith(
        compareByDescending<RadarItem> { it.alert.eventInstant() != null }
            .thenByDescending { it.alert.eventInstant() }
    )
    // 同じ記事が別のサイトにも載っているもの(見出しの頭が同じ)は、いちばん新しい1件にまとめる
    val sorted = sortedAll.groupBy { sameArticleKey(it.alert) }.values.map { group ->
        val first = group.first().let { f ->
            // まとめた記事のどれかに画像があれば、それを使う
            if (f.alert.image.isNotBlank()) f
            else group.firstOrNull { it.alert.image.isNotBlank() }?.let { f.copy(alert = f.alert.copy(image = it.alert.image)) } ?: f
        }
        first.copy(
            sameCount = group.size - 1,
            sameSources = group.drop(1).map { it.alert.sourceLabel() }.filter { it.isNotBlank() }.distinct()
        )
    }
    val teamItems = sorted.filter { selectedTeam == null || it.alert.teamId == selectedTeam?.name }
    val categoryItems = teamItems.filter { it.matches(selectedCategory) }
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

    // ---- 直近3日と、それより前(過去1年)に分ける(招待の「受付中/過去」の表示は今までどおり) ----
    var showPast by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // 45日より前の保管庫。「それより前」を開いたときに初めて読み込む(通信量を抑えるため)
    var archiveResult by remember { mutableStateOf<AlertsResult?>(null) }
    // 検索中(1文字以上入力)も、過去1年分を探すために読み込む
    val searching = query.isNotBlank()
    LaunchedEffect(showPast, searching) {
        if ((showPast || searching) && archiveResult == null) archiveResult = NewsAlertsRepository.fetchArchive()
    }
    val recentBorder = java.time.Instant.now().minus(java.time.Duration.ofDays(3))
    val recentItems = shown.filter { it.alert.eventInstant()?.isAfter(recentBorder) == true }
    val olderItems = shown.filterNot { it.alert.eventInstant()?.isAfter(recentBorder) == true }
    // 分類ボタン(すべて・各分類)の横の数字は、直近3日の件数
    val teamRecentItems = teamItems.filter { it.alert.eventInstant()?.isAfter(recentBorder) == true }
    val knownIds = sorted.map { it.alert.id }.toSet()
    val knownLinks = sorted.map { it.alert.link }.filter { it.isNotBlank() }.toSet()
    val archiveItems = ((archiveResult as? AlertsResult.Success)?.items ?: emptyList())
        .filterNot { it.id in knownIds || (it.link.isNotBlank() && it.link in knownLinks) }
        .map { RadarItem(it, classify(it, false), false, inNews = true) }
        .filter { (selectedTeam == null || it.alert.teamId == selectedTeam?.name) && it.matches(selectedCategory) }
    val pastAll = (olderItems + archiveItems).sortedByDescending { it.alert.eventInstant() }
    // 検索:空白で区切った言葉がすべて見出し(または媒体名)に入っているもの
    val words = query.trim().split(Regex("[\\s　]+")).filter { it.isNotBlank() }
    // 検索結果は、直近3日・それより前・過去1年分のすべてから探す(新しい順)
    val searchResults = if (words.isEmpty()) emptyList() else (recentItems + pastAll).filter { item ->
        val text = item.alert.title + " " + item.alert.sourceLabel()
        words.all { w -> text.contains(w, ignoreCase = true) }
    }

    // 一覧のスクロール位置(右下の「一番上へ」「一番下へ」ボタンで使う)
    val listState = rememberLazyListState()

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
        floatingActionButton = { ScrollJumpButtons(listState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            // チームの切り替えボタンは一番上に固定し、下にスクロールしても常に表示する
            Column(modifier = Modifier.fillMaxSize()) {
            TeamSelectorRow(
                selectedTeam = selectedTeam,
                onSelect = { t -> TeamSelection.select(t) },
                showAll = true,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel(selectedTeam?.let { "${it.displayName}のトピック" } ?: "3チームのトピック", red = true)
                        Headline(
                            when {
                                loading -> "情報を集めています"
                                // 見出しは1行にまとめる
                                isInviteView && openInvites.isEmpty() -> "受付中の招待はいまのところなし"
                                isInviteView -> "無料招待 ${openInvites.size}件 受付中"
                                teamItems.isEmpty() -> "いまのところ情報はありません"
                                newCount > 0 -> "新着 ${newCount}件・全${teamItems.size}件"
                                else -> "情報 ${teamItems.size}件"
                            },
                            fontSize = 22
                        )
                        Text(
                            // 説明は短く(詳しくはメニューの「アプリの使い方」)
                            "ボタンの数字は直近3日の件数です。行を押すと元の記事を開きます。",
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
                    // 分類で絞り込み。ボタンは2行に分けて、横にスクロールしなくても全部見えるようにする
                    // ボタンの横の数字は直近3日の件数
                    val chips = listOf<RadarCategory?>(null) + RadarCategory.values().toList()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.chunked(4).forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                line.forEach { cat ->
                                    if (cat == null) {
                                        CategoryChip("すべて ${teamRecentItems.size}", selectedCategory == null) { selectedCategory = null }
                                    } else {
                                        val count = teamRecentItems.count { it.matches(cat) }
                                        CategoryChip("${cat.label} $count", selectedCategory == cat) {
                                            selectedCategory = if (selectedCategory == cat) null else cat
                                            inviteTab = 0
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // 検索欄(分類ボタンのすぐ下。直近3日〜過去1年分のすべてから探す。招待の表示では出さない)
                if (!isInviteView) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                // 説明の文字が長いと2行に折り返して欄が高くなるので、1行に収める(はみ出す分は「…」)
                                placeholder = {
                                    Text(
                                        "選手名・イベント名などで検索",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 14.sp
                                    )
                                },
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        Text(
                                            "✕",
                                            modifier = Modifier.clickable { query = "" }.padding(8.dp),
                                            color = InkSoft
                                        )
                                    }
                                }
                            )
                            if (searching) {
                                Text(
                                    when {
                                        archiveResult == null -> "過去1年分の記事を読み込んでいます…(読み込み中も直近45日分から探しています)"
                                        archiveResult is AlertsResult.Failure -> "45日より前の記事を読み込めませんでした。直近45日分から探しています。"
                                        else -> "「${words.joinToString(" ")}」を含む記事 ${searchResults.size}件(選んでいるチーム・分類の中から)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSoft
                                )
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
                // 「イベント」では、これからのパブリックビューイング(手で登録した分・自動で見つけた記事)と、Xで探すボタンを先に出す
                if (selectedCategory == RadarCategory.EVENT) {
                    item {
                        val pvs = PublicViewingsRepository.upcoming(selectedTeam)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Ink)
                                .background(Paper)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "パブリックビューイング" + if (pvs.isNotEmpty()) "(これから${pvs.size}件)" else "",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Ink
                            )
                            // 「Xで探す」ボタンは、一覧より先(見出しのすぐ下)に出す。ブローウィンズの分だけ
                            if (selectedTeam == null || selectedTeam == Team.BLOWINDS) {
                                PublicViewingXSearch(listOf(Team.BLOWINDS))
                            }
                            if (pvs.isNotEmpty()) {
                                PublicViewingList(pvs, showTeam = selectedTeam == null)
                            } else {
                                Text("これからのパブリックビューイングは、まだ見つかっていません。", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                            }
                        }
                    }
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
                } else if (shown.isEmpty() && !(searching && !isInviteView)) {
                    item {
                        Text(
                            "この分類の情報はありません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else if (isInviteView) {
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
                } else if (searching) {
                    // 検索中は検索結果だけを出す
                    if (searchResults.isEmpty()) {
                        item {
                            Text(
                                "見つかりませんでした",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSoft,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                    items(searchResults) { radarItem ->
                        val isInvite = radarItem.category == RadarCategory.INVITE
                        RadarRow(
                            radarItem,
                            showClosedNote = true,
                            inviteStatus = if (isInvite) radarItem.alert.inviteStatus(statusPrefs) else null,
                            onSetStatus = { status -> scope.launch { saveInviteStatus(context, radarItem.alert, status) } },
                            onClick = { openRadarUrl(context, radarItem.alert.link) }
                        )
                    }
                } else {
                    // 直近3日
                    item { SectionLabel("直近3日 ${recentItems.size}件") }
                    if (recentItems.isEmpty()) {
                        item {
                            Text("直近3日の情報はありません", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                        }
                    }
                    items(recentItems) { radarItem ->
                        val isInvite = radarItem.category == RadarCategory.INVITE
                        RadarRow(
                            radarItem,
                            showClosedNote = true,
                            inviteStatus = if (isInvite) radarItem.alert.inviteStatus(statusPrefs) else null,
                            onSetStatus = { status -> scope.launch { saveInviteStatus(context, radarItem.alert, status) } },
                            onClick = { openRadarUrl(context, radarItem.alert.link) }
                        )
                    }
                    // それより前(過去1年)。見出しを押すと開く
                    item {
                        val archiveLoaded = archiveResult is AlertsResult.Success
                        Text(
                            (if (showPast) "▲ " else "▼ ") + "それより前(過去1年) " +
                                if (showPast && archiveLoaded) "${pastAll.size}件" else "${olderItems.size}件〜",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .border(1.dp, Ink)
                                .background(if (showPast) Ink else Paper)
                                .clickable { showPast = !showPast }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            color = if (showPast) Ivory else Ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                    if (showPast) {
                        item {
                            Text(
                                when {
                                    archiveResult == null -> "過去の記事を読み込んでいます…"
                                    archiveResult is AlertsResult.Failure -> "45日より前の記事を読み込めませんでした(通信環境をご確認ください)。"
                                    else -> "4日以上前〜1年前の記事です。上の検索欄からキーワードで探せます。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft
                            )
                        }
                        items(pastAll) { radarItem ->
                            val isInvite = radarItem.category == RadarCategory.INVITE
                            RadarRow(
                                radarItem,
                                showClosedNote = true,
                                inviteStatus = if (isInvite) radarItem.alert.inviteStatus(statusPrefs) else null,
                                onSetStatus = { status -> scope.launch { saveInviteStatus(context, radarItem.alert, status) } },
                                onClick = { openRadarUrl(context, radarItem.alert.link) }
                            )
                        }
                    }
                }
                // 右下のボタンに最後の行が隠れないよう、下に余白を空ける
                item { Spacer(modifier = Modifier.height(ScrollJumpBottomPadding)) }
            }
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
        // どのチームの情報かは、途中で切れないよう一番上の左に1行で出す
        if (team != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.width(4.dp).height(14.dp).background(team.color))
                Text(
                    team.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = team.color,
                    maxLines = 1
                )
            }
        }
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
        }
        // 見出し。サムネイル画像があれば左に小さく出す(Bingニュース・公式サイトのお知らせ)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            if (alert.image.isNotBlank()) RemoteThumbnail(alert.image, size = 64.dp)
            Text(
                alert.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
        }
        if (alert.snippet.isNotBlank()) {
            Text("「${alert.snippet}」", style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 3)
        }
        val details = listOf(
            alert.timeLabel().ifBlank { "日時不明" },
            if (showClosedNote && item.fromInvitations && alert.isLikelyClosed()) "受付終了の可能性" else "",
            // 同じ記事が別のサイトにも載っていたら、その件数と引用元を添える
            if (item.sameCount > 0) {
                "同じ記事ほか${item.sameCount}件" +
                    (item.sameSources.take(2).takeIf { it.isNotEmpty() }?.joinToString("・", prefix = "(", postfix = "など)") ?: "")
            } else ""
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
