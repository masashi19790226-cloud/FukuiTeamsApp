package com.fukuiteams.app.ui.screens

import com.fukuiteams.app.data.specialDay
import com.fukuiteams.app.ui.components.SpecialDayBanner
import com.fukuiteams.app.ui.components.SpecialDayBadge
import com.fukuiteams.app.ui.components.photoCaption
import com.fukuiteams.app.ui.components.HomeAwayRecordCard
import com.fukuiteams.app.ui.components.MenuLinkRow
import com.fukuiteams.app.ui.components.PackingChecklistCard
import com.fukuiteams.app.ui.components.GameCommentEditor
import com.fukuiteams.app.ui.components.GameShareCard
import com.fukuiteams.app.data.computeHomeAwaySummary
import com.fukuiteams.app.data.isToday
import com.fukuiteams.app.data.GameLpRepository
import com.fukuiteams.app.ui.components.GameLpSection
import com.fukuiteams.app.ui.components.PhotoStrip
import com.fukuiteams.app.ui.components.GamePhotoSpread
import com.fukuiteams.app.data.GamePhotos
import com.fukuiteams.app.ui.theme.Ivory
import androidx.compose.ui.text.style.TextOverflow
import com.fukuiteams.app.data.resultSourceUrl
import androidx.compose.ui.text.font.FontWeight
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.components.resultMark
import com.fukuiteams.app.ui.components.resultHeadline
import com.fukuiteams.app.ui.components.ThinRule
import com.fukuiteams.app.ui.components.shortLabel
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.HomeAwayTag
import com.fukuiteams.app.ui.components.SectionLabel
import com.fukuiteams.app.ui.components.TeamSelectorRow
import com.fukuiteams.app.ui.components.MastheadTopBar
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fukuiteams.app.data.HomeAwaySummary
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.GamesRepository
import com.fukuiteams.app.data.WatchMethod
import com.fukuiteams.app.data.isUpcoming
import com.fukuiteams.app.data.loadWatchMethod
import com.fukuiteams.app.data.saveWatchMethod
import com.fukuiteams.app.data.startEpochMillis
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.data.GameOutcome
import com.fukuiteams.app.data.GameResultsRepository
import com.fukuiteams.app.data.RemoteGameResult
import com.fukuiteams.app.data.loadGameOutcome
import com.fukuiteams.app.data.saveGameOutcome
import com.fukuiteams.app.data.WatchRecord
import com.fukuiteams.app.data.computeWatchRecords
import com.fukuiteams.app.data.resolveOutcome
import com.fukuiteams.app.data.watchCategoryOf
import com.fukuiteams.app.data.recordedWatchMethod
import com.fukuiteams.app.data.recordedOutcome
import com.fukuiteams.app.data.gameLogDataStore
import androidx.compose.runtime.collectAsState
import androidx.datastore.preferences.core.Preferences
import com.fukuiteams.app.data.TeamSelection
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.TeamBadge
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.Paper
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.White
import java.net.URLEncoder
import kotlinx.coroutines.launch

/**
 * 「試合」タブ。上から: チーム選択 → 譲渡・招待チケット検索(チーム単位) →
 * (試合を選んでいればその詳細) → 試合スケジュール一覧、の並び。
 * 「直近の試合」の概要はホーム画面にあるため、ここではタブを開いた時点では
 * 特定の試合を自動選択しない(スケジュールから選んだときだけ詳細が出る)。
 */
@Composable
fun GameDetailScreen(
    gameId: String?,
    onBack: () -> Unit,
    onOpenInvitations: () -> Unit,
    onOpenPlayers: () -> Unit = {}
) {
    val allGames = GamesRepository.games
    // 試合を指定して開いたときはその試合のチーム、それ以外はほかのタブで選んでいたチーム
    val openedGameTeam = allGames.firstOrNull { it.id == gameId }?.team
    val initialTeam = openedGameTeam ?: TeamSelection.teamForSingle()
    var selectedTeam by remember { mutableStateOf(initialTeam) }
    LaunchedEffect(openedGameTeam) {
        openedGameTeam?.let { TeamSelection.focus(it) }
    }

    val allTeamGames = remember(selectedTeam, allGames) {
        allGames.filter { it.team == selectedTeam }.sortedBy { it.sortKey }
    }
    val upcomingTeamGames = remember(allTeamGames) { allTeamGames.filter { it.isUpcoming() } }
    val pastTeamGames = remember(allTeamGames) { allTeamGames.filterNot { it.isUpcoming() }.sortedByDescending { it.sortKey } }

    // 一面の「速報」などから終わった試合を開いたときは、「過去の試合」を開いておく(その試合を一覧に出すため)
    var scheduleTabIndex by remember(selectedTeam) {
        mutableStateOf(if (gameId != null && pastTeamGames.any { it.id == gameId }) 1 else 0)
    }

    // ホーム画面から特定の試合を選んで遷移してきた場合はそれを表示。
    // 「試合」タブを直接開いた場合は、何も自動選択しない。
    var selectedGameId by remember(selectedTeam) {
        mutableStateOf(gameId?.takeIf { id -> allTeamGames.any { it.id == id } })
    }
    val game = allTeamGames.firstOrNull { it.id == selectedGameId }
    val context = LocalContext.current

    var autoResults by remember { mutableStateOf<Map<String, RemoteGameResult>>(emptyMap()) }
    // 観戦方法・勝敗の記録は、変更されるたびに観戦成績カードへ即反映させる
    val gameLogPrefs by context.gameLogDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
    val watchRecords = remember(allTeamGames, gameLogPrefs, autoResults) {
        computeWatchRecords(allTeamGames, gameLogPrefs, autoResults)
    }
    // ホーム・アウェイ別成績(観戦成績と同じ勝敗の決め方で集計)
    val homeAwaySummary = remember(allTeamGames, gameLogPrefs, autoResults) {
        computeHomeAwaySummary(allTeamGames, gameLogPrefs, autoResults)
    }
    // 一面などから特定の試合を開いたときに、その試合の位置まで画面を動かすための情報
    val listScroll = rememberScrollState()
    val jumpMarks = remember { ListJumpMarks() }
    LaunchedEffect(gameId) {
        if (gameId == null) return@LaunchedEffect
        // 一覧が描かれるのを少し待ってから動かす
        kotlinx.coroutines.delay(350)
        val viewport = jumpMarks.viewport
        val target = jumpMarks.selected
        if (viewport != null && target != null && viewport.isAttached && target.isAttached) {
            val dy = target.positionInRoot().y - viewport.positionInRoot().y
            listScroll.animateScrollTo((listScroll.value + dy - 8f).toInt().coerceAtLeast(0))
        }
    }
    // 観戦成績のタイルをタップしたときに開く試合一覧(null なら閉じている)
    var statsFilter by remember(selectedTeam) { mutableStateOf<StatsFilter?>(null) }
    val pullToRefreshState = rememberPullToRefreshState()

    suspend fun refreshResults() {
        GamesRepository.refresh(context)
        autoResults = GameResultsRepository.fetch()
    }

    LaunchedEffect(Unit) { refreshResults() }

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            refreshResults()
            pullToRefreshState.endRefresh()
        }
    }

    // Xの個人投稿は「チーム名+チケット+譲」で広めに検索。
    val personalSearchKeyword = "${selectedTeam.displayName} チケット 譲"
    val encodedPersonalKeyword = URLEncoder.encode(personalSearchKeyword, "UTF-8")
    // 広告文にはチームの略称だけが書かれていることが多いので、「招待」などは付けずにチーム名だけで探す。
    // ユナイテッドは「ユナイテッド」だけだと海外サッカーの広告まで出るので「福井」を付ける。
    val adSearchKeyword = when (selectedTeam) {
        Team.BLOWINDS -> "ブローウィンズ"
        Team.UNITED -> "福井ユナイテッド"
        Team.RAC -> "丸岡ラック"
    }
    val encodedAdKeyword = URLEncoder.encode(adSearchKeyword, "UTF-8")

    Scaffold(
        topBar = {
            MastheadTopBar(
                section = "試合面",
                edition = "${selectedTeam.displayName}版",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { pullToRefreshState.startRefresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "結果を更新")
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
        // チームの切り替えボタンは一番上に固定し、下にスクロールしても常に表示する
        Column(modifier = Modifier.fillMaxSize()) {
        TeamSelectorRow(
            selectedTeam = selectedTeam,
            onSelect = { t ->
                if (t != null) {
                    selectedTeam = t
                    TeamSelection.select(t)
                }
            },
            showAll = false,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { jumpMarks.viewport = it }
                .verticalScroll(listScroll)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 成績(観戦成績とホーム・アウェイ別成績を1つにまとめた)。見出しと要点1行だけ出し、押すと開く
            StatsSummarySection(watchRecords, homeAwaySummary, onOpenList = { statsFilter = it })

            if (allTeamGames.isEmpty()) {
                EmptyTeamState(selectedTeam)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScheduleTabChip(
                            label = "今後の試合 ${upcomingTeamGames.size}",
                            selected = scheduleTabIndex == 0,
                            onClick = { scheduleTabIndex = 0 }
                        )
                        ScheduleTabChip(
                            label = "過去の試合 ${pastTeamGames.size}",
                            selected = scheduleTabIndex == 1,
                            onClick = { scheduleTabIndex = 1 }
                        )
                    }
                    val isPastTab = scheduleTabIndex == 1
                    val listToShow = if (isPastTab) pastTeamGames else upcomingTeamGames
                    if (listToShow.isEmpty()) {
                        Text(
                            if (!isPastTab) "今後の試合予定はありません" else "過去の試合の記録はまだありません",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft
                        )
                    } else {
                        // 月ごとにまとめる(並び順はそのまま:今後の試合は古い月から、過去の試合は新しい月から)
                        val groups = listToShow.groupBy { it.monthKey() }
                        // 最初に開いておく月:いちばん上の月と、選ばれている試合の月
                        var openMonths by remember(selectedTeam, scheduleTabIndex) {
                            mutableStateOf(
                                setOfNotNull(
                                    groups.keys.firstOrNull(),
                                    listToShow.firstOrNull { it.id == selectedGameId }?.monthKey()
                                )
                            )
                        }
                        var prevYear: Int? = null
                        groups.forEach { (month, monthGames) ->
                            val open = month in openMonths
                            MonthHeader(
                                month = month,
                                showYear = prevYear == null || prevYear != month.first,
                                gameCount = monthGames.size,
                                homeCount = monthGames.count { it.isHome },
                                open = open,
                                onClick = { openMonths = if (open) openMonths - month else openMonths + month }
                            )
                            prevYear = month.first
                            if (open) {
                                monthGames.forEach { g ->
                                    val isSelected = g.id == selectedGameId
                                    val photoCount = remember(g.id, GamePhotos.version) { GamePhotos.list(context, g.id).size }
                                    Box(modifier = if (isSelected) Modifier.onGloballyPositioned { jumpMarks.selected = it } else Modifier) {
                                        ScheduleRow(
                                            game = g,
                                            selected = isSelected,
                                            // もう一度押すと閉じる
                                            onClick = { selectedGameId = if (isSelected) null else g.id },
                                            score = if (isPastTab) autoResults[g.id] else null,
                                            outcome = if (isPastTab) resolveOutcome(g.id, gameLogPrefs, autoResults) else null,
                                            showResult = isPastTab,
                                            photoCount = photoCount
                                        )
                                    }
                                    // 押した試合は、その行のすぐ下に詳細を開く
                                    if (isSelected) {
                                        if (isPastTab) {
                                            PastGameInline(g, autoResults[g.id], gameLogPrefs, autoResults)
                                        } else {
                                            UpcomingGameInline(
                                                game = g,
                                                onOpenInvitations = onOpenInvitations,
                                                personalSearchKeyword = personalSearchKeyword,
                                                adSearchKeyword = adSearchKeyword,
                                                onSearchX = { openUrl(context, "https://x.com/search?q=$encodedPersonalKeyword&f=live") },
                                                onSearchAd = {
                                                    openUrl(
                                                        context,
                                                        "https://www.facebook.com/ads/library/?active_status=all&ad_type=all&country=JP&q=$encodedAdKeyword&search_type=keyword_unordered&media_type=all"
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        }
    }

    statsFilter?.let { filter ->
        val filteredGames = pastTeamGames.filter { filter.matches(watchCategoryOf(it.id, gameLogPrefs)) }
        val record = when (filter) {
            StatsFilter.ON_SITE -> watchRecords[WatchMethod.ON_SITE] ?: WatchRecord()
            StatsFilter.OTHERS -> (watchRecords[WatchMethod.STREAMING] ?: WatchRecord()) +
                (watchRecords[WatchMethod.NOT_WATCHED] ?: WatchRecord())
            StatsFilter.ALL -> watchRecords.values.fold(WatchRecord()) { acc, r -> acc + r }
        }
        ModalBottomSheet(onDismissRequest = { statsFilter = null }, containerColor = Paper) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${filter.label}の試合",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${record.summaryLabel()}・勝率 ${record.winRateLabel()}(新しい順)",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                if (filteredGames.isEmpty()) {
                    Text("該当する試合はありません", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                } else {
                    filteredGames.forEach { g ->
                        ScheduleRow(
                            game = g,
                            selected = false,
                            onClick = {
                                // タップした試合を「過去の試合」で選択状態にして詳細を表示
                                scheduleTabIndex = 1
                                selectedGameId = g.id
                                statsFilter = null
                            },
                            score = autoResults[g.id],
                            outcome = resolveOutcome(g.id, gameLogPrefs, autoResults),
                            showResult = true
                        )
                    }
                }
            }
        }
    }
}

/**
 * 過去の試合一覧で、選択した行のすぐ下に出す記録欄。
 * 観戦方法と(自動取得の結果が無い試合は)勝敗を、その場でタップして記録できる。
 */
@Composable
private fun QuickRecordPanel(
    game: Game,
    watchMethod: WatchMethod?,
    manualOutcome: GameOutcome?,
    hasAutoResult: Boolean,
    autoResult: RemoteGameResult?,
    outcome: GameOutcome?,
    photoCaption: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(game.team.color.copy(alpha = 0.06f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("観戦方法", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WatchMethod.values().forEach { method ->
                val label = when (method) {
                    WatchMethod.ON_SITE -> "現地"
                    WatchMethod.STREAMING -> "配信"
                    WatchMethod.NOT_WATCHED -> "見ていない"
                }
                QuickChoice(label, watchMethod == method, game.team.color, Modifier.weight(1f)) {
                    scope.launch { saveWatchMethod(context, game.id, method) }
                }
            }
        }
        if (!hasAutoResult) {
            Text("勝敗(自動取得の結果がまだ無い試合のみ)", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GameOutcome.values().forEach { option ->
                    val label = when (option) {
                        GameOutcome.WIN -> "勝ち"
                        GameOutcome.LOSE -> "負け"
                        GameOutcome.DRAW -> "引分"
                    }
                    QuickChoice(label, manualOutcome == option, game.team.color, Modifier.weight(1f)) {
                        scope.launch { saveGameOutcome(context, game.id, option) }
                    }
                }
            }
        }
        Text("写真", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        PhotoStrip(game, photoCaption)
        // コメント(一面の速報記事とSNS投稿に使う)と、X・Instagramへの投稿
        GameCommentEditor(game)
        GameShareCard(game, autoResult, outcome)
    }
}

@Composable
private fun QuickChoice(
    label: String,
    selected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) color else Paper)
            .border(BorderStroke(1.dp, if (selected) color else LineGray), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) White else Ink, style = MaterialTheme.typography.labelLarge)
    }
}

/** 観戦成績のタイルの区分。 */
private enum class StatsFilter(val label: String) {
    ON_SITE("現地観戦"),
    OTHERS("それ以外"),
    ALL("全体");

    fun matches(method: WatchMethod): Boolean = when (this) {
        ON_SITE -> method == WatchMethod.ON_SITE
        OTHERS -> method != WatchMethod.ON_SITE
        ALL -> true
    }
}

/**
 * 選択中のチームについて、終了済みの試合の通算成績を「現地観戦」「それ以外」「全体」に分けて表示する。
 * 現地観戦 = 観戦方法を「現地」にした試合。それ以外 = 配信・見ていない・未記録の試合。
 */
@Composable
private fun WatchStatsCard(
    records: Map<WatchMethod, WatchRecord>,
    onOpenList: (StatsFilter) -> Unit
) {
    val onSite = records[WatchMethod.ON_SITE] ?: WatchRecord()
    val streaming = records[WatchMethod.STREAMING] ?: WatchRecord()
    val others = streaming + (records[WatchMethod.NOT_WATCHED] ?: WatchRecord())
    val all = onSite + others

    val onSiteRate = onSite.rate()
    val allRate = all.rate()
    // 紙面の見出し:現地観戦の勝率を全体と比べて一言
    val headline = when {
        onSiteRate == null -> "現地観戦の記録はまだ"
        allRate != null && onSiteRate >= allRate + 0.05 -> "現地で強し\n勝率 ${onSite.winRateLabel()}"
        allRate != null && onSiteRate <= allRate - 0.05 -> "現地では苦戦\n勝率 ${onSite.winRateLabel()}"
        else -> "現地観戦\n勝率 ${onSite.winRateLabel()}"
    }
    val lead = when {
        all.watched == 0 -> "まだ終了した試合がありません。"
        onSiteRate == null -> "過去の試合を選んで観戦方法を「現地」にすると、ここに勝率が出ます。"
        allRate == null -> "現地観戦は${onSite.summaryLabel()}。"
        onSiteRate > allRate -> "現地観戦は${onSite.summaryLabel()}。全体の${all.winRateLabel()}を上回る。"
        onSiteRate < allRate -> "現地観戦は${onSite.summaryLabel()}。全体の${all.winRateLabel()}を下回る。"
        else -> "現地観戦は${onSite.summaryLabel()}。全体と同じ勝率。"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("観戦成績")
            Spacer(modifier = Modifier.weight(1f))
            Text("通算・行をタップで試合一覧", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        Headline(headline, fontSize = 24)
        Text(lead, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        if (all.watched > 0) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                StatsTableRow(listOf("区分", "試合", "勝", "敗", "分", "勝率"), header = true)
                StatsTableRow(onSite.asCells("現地観戦 ›"), bold = true, highlightRate = true) { onOpenList(StatsFilter.ON_SITE) }
                StatsTableRow(others.asCells("それ以外 ›")) { onOpenList(StatsFilter.OTHERS) }
                StatsTableRow(all.asCells("全体 ›")) { onOpenList(StatsFilter.ALL) }
            }
            if (all.unknown > 0) {
                Text(
                    "結果が未確定の試合 ${all.unknown}件は除外(一覧で勝敗を記録できます)",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            }
        }
    }
}

private fun WatchRecord.asCells(label: String) =
    listOf(label, "$decidedGames", "$wins", "$losses", "$draws", winRateLabel())

@Composable
private fun StatsTableRow(
    cells: List<String>,
    header: Boolean = false,
    bold: Boolean = false,
    highlightRate: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val weights = listOf(2.4f, 1f, 0.8f, 0.8f, 0.8f, 1.3f)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 7.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            cells.forEachIndexed { i, cell ->
                val isRate = i == cells.lastIndex
                Text(
                    cell,
                    modifier = Modifier.weight(weights[i]),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (header || bold || (isRate && highlightRate)) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (isRate && highlightRate && !header) NewsRed else Ink
                )
            }
        }
        if (header) {
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Ink))
        } else {
            ThinRule(color = LineGray)
        }
    }
}

@Composable
private fun TicketSearchSection(
    personalSearchKeyword: String,
    adSearchKeyword: String,
    onSearchX: () -> Unit,
    onSearchAd: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("譲渡・招待チケットを探す")
        Text(
            "X:「${personalSearchKeyword}」・SNS広告(Meta広告ライブラリ):「${adSearchKeyword}」で検索します",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSearchX, modifier = Modifier.weight(1f)) { Text("Xで探す", maxLines = 1) }
            OutlinedButton(onClick = onSearchAd, modifier = Modifier.weight(1f)) { Text("SNS広告", maxLines = 1) }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(DividerGray)
                .padding(12.dp)
        ) {
            Text(
                "取引は各サービス上で行われ、本アプリは内容や安全性を保証しません。定価を大きく超える転売にはご注意ください。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ScheduleTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) Ink else Paper)
            .border(BorderStroke(1.dp, if (selected) Ink else LineGray), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) White else InkSoft,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ScheduleRow(
    game: Game,
    selected: Boolean,
    onClick: () -> Unit,
    score: RemoteGameResult? = null,
    outcome: GameOutcome? = null,
    showResult: Boolean = false,
    photoCount: Int = 0
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) game.team.color.copy(alpha = 0.06f) else White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 1.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) game.team.color else LineGray)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 日付の下に HOME/AWAY を置く(見出しが長くても押し出されないように)
            Column(
                modifier = Modifier.width(58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(game.dayOfWeek, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text(
                    game.dateLabel.split("/").drop(1).joinToString("/"),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false
                )
                HomeAwayLabel(isHome = game.isHome)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (game.specialDay() != null) SpecialDayBadge()
                Text(
                    if (showResult) resultHeadline(game, score?.myScore, score?.opponentScore, outcome) else "vs ${game.opponent}",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (photoCount > 0) {
                        Text("写真$photoCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ink,
                            modifier = Modifier.border(1.dp, Ink).padding(horizontal = 3.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = InkSoft, modifier = Modifier.size(13.dp))
                    Text(game.timeLabel, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = InkSoft, modifier = Modifier.size(13.dp))
                    Text(
                        game.venue,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showResult) {
                ResultBadge(game = game, score = score, outcome = outcome)
            }
        }
    }
}

/**
 * 過去の試合一覧の右端に出す「○90-88」「●80-82」のような結果。
 * 結果が出ている試合は、タップすると取得元のページ(B.LEAGUE公式など)を開く。
 */
@Composable
private fun ResultBadge(game: Game, score: RemoteGameResult?, outcome: GameOutcome?) {
    val context = LocalContext.current
    val text = when {
        outcome == null -> "結果待ち"
        score != null -> "${resultMark(outcome)}${score.myScore}-${score.opponentScore}"
        else -> resultMark(outcome)
    }
    Column(
        modifier = Modifier
            .then(
                if (outcome != null) Modifier.clickable { openUrl(context, game.resultSourceUrl(score)) }
                else Modifier
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (outcome == null) 11.sp else 15.sp,
            color = when (outcome) {
                GameOutcome.WIN -> NewsRed
                null -> InkSoft
                else -> Ink
            }
        )
        if (outcome != null) {
            Text("詳報 ›", fontSize = 10.sp, color = InkSoft)
        }
    }
}

@Composable
private fun EmptyTeamState(team: Team) {
    Card(
        shape = RoundedCornerShape(3.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Ink)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("${team.displayName}の試合データはまだ準備できていません", style = MaterialTheme.typography.bodyLarge)
            Text(
                "公式サイトの日程ページが確認でき次第、反映します。公式サイトへのリンクはホーム画面にあります。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        }
    }
}

/** 「今後の試合」一覧で試合を押したとき、その行のすぐ下に開く詳細。中の項目は見出しだけにして、押すと開く。 */
@Composable
private fun UpcomingGameInline(
    game: Game,
    onOpenInvitations: () -> Unit,
    personalSearchKeyword: String,
    adSearchKeyword: String,
    onSearchX: () -> Unit,
    onSearchAd: () -> Unit
) {
    val context = LocalContext.current
    val lp = GameLpRepository.latest[game.id]
    LaunchedEffect(game.id) { if (GameLpRepository.latest.isEmpty()) GameLpRepository.fetch() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, game.team.color)
            .background(White)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "会場:${game.venue} ›地図 ・ チケット:${game.ticketStatus}",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
            modifier = Modifier.clickable {
                openUrl(context, "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(game.venue, "UTF-8"))
            }
        )
        // コラボ企画など特別な日の試合なら、特集枠を出す
        game.specialDay()?.let { day -> SpecialDayBanner(day, showCountdown = true) }

        // ブローウィンズのホームゲームは、公式の試合情報ページの開場時刻・当日スケジュール・イベント
        if (lp != null) {
            ToggleBlock(
                title = "試合情報(公式)",
                summary = listOfNotNull(
                    lp.openTime.takeIf { it.isNotBlank() }?.let { "開場 $it" },
                    lp.events.size.takeIf { it > 0 }?.let { "イベント${it}件" }
                ).joinToString("・"),
                startOpen = game.isToday()
            ) { GameLpSection(lp, startExpanded = game.isToday()) }
        }

        // 持ち物チェック(見出しを押すと開く。試合当日は最初から開いている)
        PackingChecklistCard(game)

        // チケット:公式の販売状況・無料招待・譲渡チケット探しをまとめる
        ToggleBlock(title = "チケット", summary = "公式・無料招待・譲渡を探す", startOpen = false) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("販売状況:${game.ticketStatus}・一般販売開始 ${game.ticketSaleStart}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text(
                    "無料招待の情報を見る(トピックの「招待」) ›",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenInvitations)
                        .padding(vertical = 4.dp)
                )
                TicketSearchSection(
                    personalSearchKeyword = personalSearchKeyword,
                    adSearchKeyword = adSearchKeyword,
                    onSearchX = onSearchX,
                    onSearchAd = onSearchAd
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { addToCalendar(context, game) },
                modifier = Modifier.weight(1f)
            ) { Text("カレンダーに追加", maxLines = 1, softWrap = false, fontSize = 13.sp) }
            Button(
                onClick = {
                    val url = game.team.officialSiteUrl
                        ?: "https://www.google.com/search?q=" + URLEncoder.encode("${game.team.displayName} チケット", "UTF-8")
                    openUrl(context, url)
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Ink),
                modifier = Modifier.weight(1f)
            ) { Text("チケットを買う", maxLines = 1, softWrap = false, fontSize = 13.sp) }
        }
    }
}

/** 「過去の試合」一覧で試合を押したとき、その行のすぐ下に開く詳細。結果・写真と、観戦の記録・コメント・SNS投稿。 */
@Composable
private fun PastGameInline(
    game: Game,
    autoResult: RemoteGameResult?,
    prefs: Preferences?,
    autoResults: Map<String, RemoteGameResult>
) {
    val context = LocalContext.current
    val photos = remember(game.id, GamePhotos.version) { GamePhotos.list(context, game.id) }
    val outcome = resolveOutcome(game.id, prefs, autoResults)
    val watchMethod = recordedWatchMethod(game.id, prefs)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PastGameResultCard(game, autoResult)
        if (photos.isNotEmpty()) {
            GamePhotoSpread(
                game = game,
                photos = photos,
                leadCaption = photoCaption(game, autoResult, outcome, watchMethod)
            )
        }
        QuickRecordPanel(
            game = game,
            watchMethod = watchMethod,
            manualOutcome = recordedOutcome(game.id, prefs),
            hasAutoResult = autoResult != null,
            autoResult = autoResult,
            outcome = outcome,
            photoCaption = photoCaption(game, autoResult, outcome, watchMethod)
        )
    }
}

/** 見出し(▼/▲)と要点1行。押すと中身が開く。 */
@Composable
private fun ToggleBlock(title: String, summary: String, startOpen: Boolean, content: @Composable () -> Unit) {
    var open by remember(title) { mutableStateOf(startOpen) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ThinRule(color = LineGray)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("${if (open) "▲" else "▼"} $title", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Ink)
            if (summary.isNotBlank()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (open) content()
    }
}

/** 試合の年と月(並べ替え・まとめ用)。dateLabel は「2026/10/3」の形。 */
private fun Game.monthKey(): Pair<Int, Int> {
    val p = dateLabel.split("/")
    return (p.getOrNull(0)?.toIntOrNull() ?: 0) to (p.getOrNull(1)?.toIntOrNull() ?: 0)
}

/** 月の見出し「2026年10月 10試合(HOME 5)」。押すとその月の試合が開閉する。 */
@Composable
private fun MonthHeader(
    month: Pair<Int, Int>,
    showYear: Boolean,
    gameCount: Int,
    homeCount: Int,
    open: Boolean,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Headline(if (showYear) "${month.first}年${month.second}月" else "${month.second}月", fontSize = 20)
            Text("${gameCount}試合(HOME $homeCount)", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            Spacer(modifier = Modifier.weight(1f))
            Text(if (open) "▲" else "▼", style = MaterialTheme.typography.titleMedium, color = Ink)
        }
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Ink))
    }
}

/**
 * 成績(観戦成績とホーム・アウェイ別成績)。ふだんは見出しと要点1行だけで、押すと両方の表が開く。
 */
@Composable
private fun StatsSummarySection(
    records: Map<WatchMethod, WatchRecord>,
    homeAway: HomeAwaySummary,
    onOpenList: (StatsFilter) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val onSite = records[WatchMethod.ON_SITE] ?: WatchRecord()
    val summary = if (homeAway.total.games == 0 && onSite.watched == 0) {
        "まだ終了した試合はありません"
    } else {
        "観戦 現地${onSite.summaryLabel()} ・ 全体 ${homeAway.total.recordLabel()}" +
            "(HOME ${homeAway.home.recordLabel()} / AWAY ${homeAway.away.recordLabel()})"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().clickable { open = !open }) {
            Text("${if (open) "▲" else "▼"} 成績", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Ink)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        if (open) {
            WatchStatsCard(records, onOpenList)
            HomeAwayRecordCard(homeAway)
        }
    }
}

/** 一面などから開いた試合の位置まで画面を動かすための入れ物(スクロールする部分と、選ばれた試合の行)。 */
private class ListJumpMarks {
    var viewport: LayoutCoordinates? = null
    var selected: LayoutCoordinates? = null
}

/** スコアの両側にチーム名を置いた得点板。左が福井側、右が対戦相手。 */
@Composable
private fun ScoreBoard(game: Game, myScore: Int, opponentScore: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScoreSide(
            name = game.team.shortLabel(),
            sub = if (game.isHome) "ホーム" else "アウェイ",
            color = game.team.color,
            won = myScore > opponentScore,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$myScore - $opponentScore",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 4.dp),
            maxLines = 1,
            softWrap = false
        )
        ScoreSide(
            name = game.opponent,
            sub = if (game.isHome) "アウェイ" else "ホーム",
            color = InkSoft,
            won = opponentScore > myScore,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ScoreSide(name: String, sub: String, color: androidx.compose.ui.graphics.Color, won: Boolean, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            name,
            color = color,
            fontWeight = if (won) FontWeight.Black else FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .width(36.dp)
                .height(3.dp)
                .background(color)
        )
        Text(sub, style = MaterialTheme.typography.labelSmall, color = InkSoft, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun PastGameResultCard(game: Game, autoResult: RemoteGameResult?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 一覧側の記録欄で変更しても、ここにすぐ反映されるように保存データを直接見る
    val prefs by context.gameLogDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
    val outcome = recordedOutcome(game.id, prefs)

    Card(
        shape = RoundedCornerShape(3.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Ink)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("試合結果")

            val result = autoResult
            if (result != null) {
                ScoreBoard(
                    game = game,
                    myScore = result.myScore,
                    opponentScore = result.opponentScore
                )
                val diff = result.myScore - result.opponentScore
                val (label, color) = when {
                    diff > 0 -> "勝利" to androidx.compose.ui.graphics.Color(0xFF2F6846)
                    diff < 0 -> "敗戦" to androidx.compose.ui.graphics.Color(0xFFB3261E)
                    else -> "引き分け" to InkSoft
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(color.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    "公式サイトから自動取得したスコアです",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                OutlinedButton(
                    onClick = { openUrl(context, game.resultSourceUrl(result)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("取得元のページで詳報を見る") }
                return@Column
            }

            if (game.resultPageUrl != null) {
                OutlinedButton(
                    onClick = { openUrl(context, game.resultPageUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("結果ページを見る(公式サイト)") }
            }

            Text(
                "自動取得はまだできていません。見てきた結果を、タップで記録できます。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameOutcome.values().forEach { option ->
                    val isSelected = outcome == option
                    val color = when (option) {
                        GameOutcome.WIN -> androidx.compose.ui.graphics.Color(0xFF2F6846)
                        GameOutcome.LOSE -> androidx.compose.ui.graphics.Color(0xFFB3261E)
                        GameOutcome.DRAW -> InkSoft
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) color.copy(alpha = 0.12f) else Paper)
                            .border(
                                BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) color else LineGray),
                                RoundedCornerShape(3.dp)
                            )
                            .clickable {
                                scope.launch { saveGameOutcome(context, game.id, option) }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option.label, color = if (isSelected) color else Ink, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchMethodPicker(game: Game) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 一覧側の記録欄で変更しても、ここにすぐ反映されるように保存データを直接見る
    val prefs by context.gameLogDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
    val selected = recordedWatchMethod(game.id, prefs)

    Card(
        shape = RoundedCornerShape(3.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Ink)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("どうやって観戦しましたか?")
            Text(
                "この記録はあなたの端末だけに保存され、どこにも送信されません。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchMethod.values().forEach { method ->
                    val isSelected = selected == method
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) Accent.copy(alpha = 0.10f) else Paper)
                            .border(
                                BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Accent else LineGray),
                                RoundedCornerShape(3.dp)
                            )
                            .clickable {
                                scope.launch { saveWatchMethod(context, game.id, method) }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(BorderStroke(2.dp, if (isSelected) Accent else LineGray), CircleShape)
                                .background(if (isSelected) Accent else Paper),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(White)
                                )
                            }
                        }
                        Text(method.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}


@Composable
private fun MatchHeaderCard(game: Game, isPast: Boolean = false) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(3.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Ink)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(game.team.displayName, color = InkSoft, style = MaterialTheme.typography.bodySmall)
                    HomeAwayLabel(isHome = game.isHome)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DividerGray)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(if (isPast) "試合終了" else "試合前", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamBadge(game.team, size = 64.dp, fontSize = 24.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        game.team.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("VS", style = MaterialTheme.typography.titleSmall, color = InkSoft)
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(DividerGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("?", color = InkSoft, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        game.opponent,
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = InkSoft, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "${game.dateLabel}(${game.dayOfWeek})${game.timeLabel} 開始",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { openUrl(context, "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(game.venue, "UTF-8")) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Accent, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(game.venue, style = MaterialTheme.typography.bodyMedium, color = Accent)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Filled.Map, contentDescription = "地図を開く", tint = Accent, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** 紙面風の HOME / AWAY 表示。HOME は黒地に白抜き、AWAY は黒枠。 */
@Composable
private fun HomeAwayLabel(isHome: Boolean) {
    HomeAwayTag(isHome = isHome, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun HomeAwayBadge(isHome: Boolean) {
    val bg: androidx.compose.ui.graphics.Color
    val fg: androidx.compose.ui.graphics.Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val label: String
    if (isHome) {
        fg = androidx.compose.ui.graphics.Color(0xFF2F6846)
        bg = fg.copy(alpha = 0.14f)
        icon = Icons.Filled.Home
        label = "ホーム"
    } else {
        fg = androidx.compose.ui.graphics.Color(0xFF2541B2)
        bg = fg.copy(alpha = 0.12f)
        icon = Icons.Filled.Flight
        label = "アウェイ"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(11.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

/**
 * 「Googleカレンダーに追加」ボタン用。実装が簡単で権限も不要なため、
 * Android標準のカレンダー登録画面(ACTION_INSERT)を開く方式にしている。
 * 開いた画面でユーザーが最後に保存をタップする必要がある(完全自動保存ではない)。
 * MockDataの日付に年が含まれていないため、今年として計算している。
 * 試合時間は仮に2時間として終了時刻を設定している。
 */
private fun addToCalendar(context: Context, game: Game) {
    val begin = game.startEpochMillis()
    val end = begin + 2 * 60 * 60 * 1000
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, "${game.team.displayName} vs ${game.opponent}")
        putExtra(CalendarContract.Events.EVENT_LOCATION, game.venue)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
    }
    context.startActivity(intent)
}
