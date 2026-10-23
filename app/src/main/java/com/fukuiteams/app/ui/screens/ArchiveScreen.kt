package com.fukuiteams.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.Alumni
import com.fukuiteams.app.data.AlumniPlayer
import com.fukuiteams.app.data.DataRefresher
import com.fukuiteams.app.data.HistoryPlayer
import com.fukuiteams.app.data.NationalSelection
import com.fukuiteams.app.data.nationalPhotos
import com.fukuiteams.app.data.StandingsRepository
import com.fukuiteams.app.data.TeamPhotos
import com.fukuiteams.app.data.GameOutcome
import com.fukuiteams.app.data.HistoryMatch
import com.fukuiteams.app.data.HistoryRepository
import com.fukuiteams.app.data.OpponentRecord
import com.fukuiteams.app.data.SeasonSummary
import com.fukuiteams.app.data.TEAM_HISTORY
import com.fukuiteams.app.data.TeamSelection
import com.fukuiteams.app.data.opponentRecords
import com.fukuiteams.app.data.seasonSummaries
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.model.shortName
import com.fukuiteams.app.ui.components.DoubleRule
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.HomeAwayTag
import com.fukuiteams.app.ui.components.RemoteThumbnail
import com.fukuiteams.app.ui.components.MastheadTopBar
import com.fukuiteams.app.ui.components.MenuLinkRow
import com.fukuiteams.app.ui.components.NewsChip
import com.fukuiteams.app.ui.components.ScrollJumpBottomPadding
import com.fukuiteams.app.ui.components.ScrollJumpButtons
import com.fukuiteams.app.ui.components.SectionLabel
import com.fukuiteams.app.ui.components.TeamSelectorRow
import com.fukuiteams.app.ui.components.openExternalUrl
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper

/** 「過去の記録」の中の切り替え */
private enum class ArchiveTab(val label: String) {
    SEASONS("シーズンごと"),
    OPPONENTS("相手別の対戦成績"),
    MATCHES("過去の試合"),
    PLAYERS("過去の選手")
}

/** 「過去の選手」の絞り込み・並び順 */
private enum class ArchivePlayerFilter(val label: String) {
    FORMER("元選手"),
    ALL("全選手")
}

private enum class ArchivePlayerSort(val label: String) {
    APPS("出場の多い順"),
    GOALS("得点の多い順"),
    RECENT("最近まで在籍順")
}

/**
 * 過去の記録(下のメニューの「記録」から開く)。チームごとに、シーズンごとの成績・相手別の通算の対戦成績・過去の試合の一覧と、
 * 過去の実績(TeamHistory.kt)・過去のニュースと招待(トピック)への入口を出す。
 * 試合の記録は data/history.json(scripts/history.py が公式サイトから作る)。
 */
@Composable
fun ArchiveScreen(
    /** 左上の戻るボタンで戻る(下のメニューのタブとして開くときは null で、戻るボタンを出さない) */
    onBack: (() -> Unit)? = null,
    /** トピック(そのチームの、すべての分類)を開く。過去1年分のニュースは「それより前」から見られる */
    onOpenTopics: (Team) -> Unit,
    /** トピックの「招待」を開く(受付中と過去の招待を切り替えられる) */
    onOpenInvites: () -> Unit
) {
    val context = LocalContext.current
    var team by remember { mutableStateOf(TeamSelection.teamForSingle()) }
    var tab by remember { mutableStateOf(ArchiveTab.SEASONS) }
    LaunchedEffect(Unit) { HistoryRepository.ensureLoaded() }

    val history = HistoryRepository.data[team]
    val matches = history?.matches.orEmpty()
    val seasons = remember(team, matches) { seasonSummaries(team, matches) }
    val opponents = remember(team, matches) { opponentRecords(matches) }
    // 「過去の試合」で選んでいるシーズン(チームを変えたら、いちばん新しいシーズンに戻す)
    var season by remember(team, matches) { mutableStateOf(seasons.firstOrNull()?.season) }
    // 「相手別」で開いている相手
    var openedOpponent by remember(team) { mutableStateOf<String?>(null) }
    // 「過去の選手」の絞り込み・並び順
    var playerFilter by remember { mutableStateOf(ArchivePlayerFilter.FORMER) }
    var playerSort by remember { mutableStateOf(ArchivePlayerSort.APPS) }
    // 丸岡RUCKの元選手(standings.json の「あの選手は今」)をまだ読んでいなければ読む
    LaunchedEffect(Unit) { if (Alumni.players.isEmpty()) runCatching { StandingsRepository.fetch() } }
    // 選手の写真(「過去の選手」で、いまもチームにいる選手の写真を出す)
    LaunchedEffect(Unit) { TeamPhotos.ensureLoaded() }

    // 右下の「一番上へ」「一番下へ」ボタン用
    val listState = rememberLazyListState()
    // 画面を下に引っ張る・右上の更新ボタンで、アプリのすべての情報(過去の記録も)を読み直す(ほかのタブと同じ)
    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            DataRefresher.refreshAll(context)
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            MastheadTopBar(
                section = "過去の記録",
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                },
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
        // チームの切り替えボタンは一番上に固定し、下にスクロールしても常に表示する(ほかのタブと同じ)
        Column(modifier = Modifier.fillMaxSize()) {
        TeamSelectorRow(
            selectedTeam = team,
            onSelect = { t ->
                if (t != null) {
                    team = t
                    TeamSelection.select(t)
                }
            },
            showAll = false,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Headline("過去の記録", fontSize = 22)
                    Text(
                        "これまでのシーズンの成績・相手ごとの通算の対戦成績・過去の試合・過去の選手を振り返れます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                    DoubleRule(modifier = Modifier.padding(top = 6.dp))
                }
            }

            val officialSeasons = history?.officialSeasons.orEmpty()
            if (matches.isEmpty() && officialSeasons.isNotEmpty()) {
                // 試合ごとの記録がまだ無いチーム(ブローウィンズ)は、公式のシーズンごとの成績だけ出す
                item {
                    Text(
                        "シーズンごとの成績(${history?.source ?: "公式サイト"}から)。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                }
                officialSeasons.groupBy { it.season }.forEach { (s, rows) ->
                    item(key = "os-$s") { OfficialSeasonCard(team, s, rows) }
                }
            } else if (matches.isEmpty()) {
                item { NoHistoryNote(team, HistoryRepository.loaded) }
            } else {
                item {
                    // 切り替えは2つずつ2段に並べる
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ArchiveTab.values().toList().chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { t ->
                                    NewsChip(t.label, tab == t, modifier = Modifier.weight(1f), horizontalPadding = 4.dp) { tab = t }
                                }
                            }
                        }
                    }
                }
                when (tab) {
                    ArchiveTab.SEASONS -> {
                        items(seasons, key = { "s-" + it.season }) { s ->
                            // ブローウィンズは、Bリーグ公式のシーズンごとの成績(平均得点・平均失点)も一緒に出す
                            SeasonCard(
                                team, s,
                                officialSeasons.filter { it.season == s.season },
                                history?.awards.orEmpty().filter { it.season == s.season }
                            )
                        }
                    }
                    ArchiveTab.OPPONENTS -> {
                        item {
                            Text(
                                "${opponents.size}チームと対戦(公式戦。対戦の多い順)。押すと試合の一覧が開きます。",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft
                            )
                        }
                        items(opponents, key = { "o-" + it.opponent }) { o ->
                            OpponentCard(
                                record = o,
                                opened = openedOpponent == o.opponent,
                                onToggle = { openedOpponent = if (openedOpponent == o.opponent) null else o.opponent },
                                onOpenMatch = { openExternalUrl(context, it.url) }
                            )
                        }
                    }
                    ArchiveTab.MATCHES -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                seasons.forEach { s ->
                                    NewsChip(seasonLabel(s.season), season == s.season) { season = s.season }
                                }
                            }
                        }
                        val list = matches.filter { it.season == season }.sortedByDescending { it.date + it.time }
                        items(list, key = { "m-" + it.date + it.time + it.opponent + it.url }) { m ->
                            MatchRow(m) { openExternalUrl(context, m.url) }
                        }
                    }
                    ArchiveTab.PLAYERS -> {
                        // 日本代表・代表候補の選出(チームの公式サイトのお知らせから。いまは丸岡RUCKだけ見つかっている)
                        val national = history?.national.orEmpty()
                        item { SectionLabel("代表・代表候補の選出", modifier = Modifier.padding(top = 4.dp)) }
                        if (national.isEmpty()) {
                            item {
                                Text(
                                    when (team) {
                                        Team.RAC -> "日本代表の選出のお知らせを読み込めませんでした(データの自動更新を待っている場合もあります)。"
                                        Team.BLOWINDS -> "ブローウィンズ公式サイトのお知らせ(2023年7月〜)には、代表に選ばれたという記事は見つかりませんでした(データの自動更新を待っている場合もあります)。"
                                        else -> "${team.shortName}の公式サイトのお知らせには、日本代表に選ばれたという記事は見つかりませんでした。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSoft
                                )
                            }
                        } else {
                            // 選手ごとの選出回数
                            val counts = national.flatMap { it.players }.groupingBy { it }.eachCount()
                                .entries.sortedByDescending { it.value }
                            item {
                                Text(
                                    "公式サイトのお知らせ ${national.size}件から。選ばれた回数:" +
                                        counts.joinToString("・") { "${it.key} ${it.value}回" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Ink
                                )
                            }
                            items(national, key = { "n-" + it.link }) { n ->
                                NationalRow(team, n) { openExternalUrl(context, n.link) }
                            }
                        }
                        if (team == Team.RAC) {
                            item { SectionLabel("元選手(退団・移籍)", modifier = Modifier.padding(top = 4.dp)) }
                            // 丸岡RUCKは、公式サイトの退団・移籍のお知らせからの元選手(「あの選手は今」と同じデータ)
                            val alumni = Alumni.players.filter { it.team == Team.RAC }.sortedByDescending { it.left }
                            item {
                                Text(
                                    if (alumni.isEmpty()) "元選手のデータを読み込めませんでした(データの自動更新を待っている場合もあります)。"
                                    else "丸岡RUCK公式サイトの退団・移籍のお知らせ(2019年〜)に載っている元選手${alumni.size}人。いまのクラブは女子Fリーグ公式の選手名簿から調べています。押すとページが開きます。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSoft
                                )
                            }
                            items(alumni, key = { "a-" + it.name + it.left }) { a ->
                                AlumniCard(a) { openExternalUrl(context, a.nowUrl.ifBlank { a.link }) }
                            }
                        }
                        // リーグの表彰(丸岡RUCK:女子Fリーグ公式の過去の記録の「最優秀選手賞」「ベストファイブ」など)
                        val awards = history?.awards.orEmpty()
                        if (awards.isNotEmpty()) {
                            item { SectionLabel("リーグの表彰", modifier = Modifier.padding(top = 4.dp)) }
                            items(awards, key = { "aw-" + it.season + it.award + it.name }) { a ->
                                Text(
                                    "${seasonLabel(a.season)}  ${a.award}  ${a.name}" + (if (a.note.isNotBlank()) "(${a.note})" else ""),
                                    modifier = Modifier.fillMaxWidth().border(1.dp, LineGray).background(Paper).padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Ink
                                )
                            }
                        }
                        if (team != Team.RAC || history?.players.orEmpty().isNotEmpty()) {
                            val all = history?.players.orEmpty()
                            val hasNow = all.any { it.now != null }
                            val filtered = all.filter { playerFilter == ArchivePlayerFilter.ALL || !hasNow || it.now == false }
                            val sorted = when (playerSort) {
                                ArchivePlayerSort.APPS -> filtered.sortedWith(compareByDescending<HistoryPlayer> { it.apps }.thenByDescending { it.goals })
                                ArchivePlayerSort.GOALS -> filtered.sortedWith(compareByDescending<HistoryPlayer> { it.goals }.thenByDescending { it.apps })
                                // 最後の試合の日付が分からない選手(丸岡RUCK)は、最後のシーズンで並べる
                                ArchivePlayerSort.RECENT -> filtered.sortedWith(compareByDescending<HistoryPlayer> { it.last.ifBlank { it.seasons.lastOrNull().orEmpty() } }.thenByDescending { it.apps })
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (hasNow) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            ArchivePlayerFilter.values().forEach { f ->
                                                NewsChip(f.label, playerFilter == f, modifier = Modifier.weight(1f)) { playerFilter = f }
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ArchivePlayerSort.values().forEach { o ->
                                            NewsChip(o.label, playerSort == o, modifier = Modifier.weight(1f), horizontalPadding = 2.dp, fontSize = 11.sp) { playerSort = o }
                                        }
                                    }
                                    Text(
                                        (if (team == Team.THUNDER)
                                            "リーグH公式の試合ページのスコア表から数えています(2024-25シーズン〜)。「試合」はスコア表に名前のあった試合です。"
                                        else if (team == Team.BLOWINDS)
                                            "Bリーグ公式の試合ごとのボックススコア(2023-24シーズン〜)から数えています。「試合」はコートに立った試合です。"
                                        else if (team == Team.RAC)
                                            "女子Fリーグ公式の過去の記録のゴールランキング(1点以上の選手。今季は入りません)から数えています。「試合」は得点したシーズンの出場試合です。"
                                        else
                                            "ユナイテッド公式サイトの試合情報(2019年〜の公式戦)の先発・交代・得点から数えています。") +
                                            (if (hasNow) "「元選手」はいまの選手名簿にいない選手です。" else "") +
                                            "${sorted.size}人",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkSoft
                                    )
                                }
                            }
                            item { SectionLabel("これまでの選手", modifier = Modifier.padding(top = 4.dp)) }
                            items(sorted, key = { "p-" + it.name }) { p -> HistoryPlayerCard(team, p) }
                        }
                    }
                }
                item {
                    Text(
                        "出典:${history?.source.orEmpty()}(押すと公式のページが開きます)",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSoft,
                        modifier = Modifier.clickable { history?.sourceUrl?.let { openExternalUrl(context, it) } }
                    )
                }
            }

            // 過去の実績(チームの歩み・タイトルなど。TeamHistory.kt)
            val achievements = TEAM_HISTORY.filter { it.team == team }.sortedByDescending { it.year }
            if (achievements.isNotEmpty()) {
                item { SectionLabel("${team.shortName}の歩み", modifier = Modifier.padding(top = 8.dp)) }
                items(achievements, key = { "h-" + it.year + it.title }) { h ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LineGray)
                            .background(Paper)
                            .clickable { openExternalUrl(context, h.url) }
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("${h.year}年  ${h.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink)
                        Text(h.detail, style = MaterialTheme.typography.bodySmall, color = Ink)
                        Text("出典:${h.source}", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                    }
                }
            }

            // 過去のニュース・招待(トピックの画面で見る)
            item { SectionLabel("過去のニュース・招待", modifier = Modifier.padding(top = 8.dp)) }
            item {
                MenuLinkRow(
                    title = "${team.shortName}の過去のニュース",
                    sub = "トピックを開きます。下の「それより前(過去1年)」を押すと、45日より前のニュースも見られます",
                    onClick = { onOpenTopics(team) }
                )
            }
            item {
                MenuLinkRow(
                    title = "過去の招待・プレゼント企画",
                    sub = "トピックの「招待」を開きます。「過去の招待」に切り替えると、終わった招待を見られます",
                    onClick = onOpenInvites
                )
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

/** 「2024」→「2024シーズン」、「2024-25」→「2024-25シーズン」 */
private fun seasonLabel(season: String): String = "${season}シーズン"

/** まだ記録が無いチームの説明 */
@Composable
private fun NoHistoryNote(team: Team, loaded: Boolean) {
    val text = when {
        !loaded -> "読み込んでいます…"
        team == Team.BLOWINDS -> "ブローウィンズの過去の記録を読み込めませんでした。画面を下に引っ張って読み直すか、データの自動更新を待ってください。"
        else -> "過去の試合の記録を読み込めませんでした。通信環境を確かめて、あとで開き直してください(データの自動更新を待っている場合もあります)。"
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth().border(1.dp, LineGray).background(Paper).padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = Ink
    )
}

/** 公式のシーズンごとの成績(1シーズン分。リーグ戦・プレーオフなどの行をまとめて出す) */
@Composable
private fun OfficialSeasonCard(team: Team, season: String, rows: List<com.fukuiteams.app.data.OfficialSeason>) {
    val startYear = season.take(4).toIntOrNull()
    val events = TEAM_HISTORY.filter { it.team == team && it.year == startYear }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Headline(seasonLabel(season), fontSize = 18)
        rows.forEach { r ->
            Text(
                "${r.comp}  ${r.win}勝${r.lose}敗(${r.win + r.lose}試合)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            if (r.ppg.isNotBlank() && r.oppg.isNotBlank()) {
                Text(
                    "平均得点 ${r.ppg}・平均失点 ${r.oppg}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            }
        }
        events.forEach { e ->
            Text("★ ${e.title}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = NewsRed)
        }
    }
}

/** 1シーズン分の成績 */
@Composable
private fun SeasonCard(
    team: Team,
    s: SeasonSummary,
    official: List<com.fukuiteams.app.data.OfficialSeason> = emptyList(),
    awards: List<com.fukuiteams.app.data.SeasonAward> = emptyList()
) {
    // そのシーズンにあった出来事(過去の実績)。ユナイテッドのシーズンは年、ブルーサンダー・丸岡RUCKは始まった年で合わせる
    val startYear = s.season.take(4).toIntOrNull()
    val events = TEAM_HISTORY.filter { it.team == team && it.year == startYear }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Headline(seasonLabel(s.season), fontSize = 18)
        s.league?.let { l ->
            Text(
                "${s.leagueName ?: "リーグ戦"}  ${l.label}(${l.games}試合)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                "得点 ${l.scored}・失点 ${l.conceded}(差 ${signed(l.scored - l.conceded)})",
                style = MaterialTheme.typography.bodySmall,
                color = Ink
            )
        }
        if (s.all.games != (s.league?.games ?: -1)) {
            Text(
                "公式戦すべて  ${s.all.label}(${s.all.games}試合・${s.all.scored}得点${s.all.conceded}失点)",
                style = MaterialTheme.typography.bodySmall,
                color = Ink
            )
        }
        s.topScorer?.let { (name, goals) ->
            Text("チーム最多得点  $name ${goals}点", style = MaterialTheme.typography.bodySmall, color = Ink)
        }
        s.averageHomeVisitors?.let {
            Text("ホームの観客数の平均  ${"%,d".format(it)}人", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        // リーグの表彰(丸岡RUCK)
        awards.forEach { a ->
            Text(
                "表彰 ${a.award}  ${a.name}" + (if (a.note.isNotBlank()) "(${a.note})" else ""),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = NewsRed
            )
        }
        // 公式の成績(ブローウィンズ。Bリーグ公式のクラブページの表)
        official.forEach { r ->
            Text(
                "公式記録 ${r.comp}  ${r.win}勝${r.lose}敗" +
                    (if (r.ppg.isNotBlank() && r.oppg.isNotBlank()) "・平均得点 ${r.ppg}・平均失点 ${r.oppg}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        }
        events.forEach { e ->
            Text("★ ${e.title}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = NewsRed)
        }
    }
}

private fun signed(n: Int): String = if (n > 0) "+$n" else "$n"

/** 相手1チーム分の通算成績。押すと試合の一覧が開く */
@Composable
private fun OpponentCard(
    record: OpponentRecord,
    opened: Boolean,
    onToggle: () -> Unit,
    onOpenMatch: (HistoryMatch) -> Unit
) {
    val last = record.matches.first()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (opened) Ink else LineGray)
            .background(Paper)
            .clickable(onClick = onToggle)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                record.opponent,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(if (opened) "▲" else "▼", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        if (record.otherNames.isNotEmpty()) {
            // チーム名が変わったクラブは、以前の名前も添える(以前の名前の試合も合わせて数えている)
            Text(
                "以前の名前など:" + record.otherNames.joinToString("・"),
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft
            )
        }
        Text(
            "通算 ${record.record.label}(${record.record.games}試合・${record.record.scored}得点${record.record.conceded}失点)",
            style = MaterialTheme.typography.bodySmall,
            color = if (record.record.win > record.record.lose) NewsRed else Ink
        )
        Text("前回 ${last.dateLabel} ${last.scoreLabel}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        if (opened) {
            record.matches.forEach { m ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenMatch(m) }.padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        m.scoreLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (m.outcome == GameOutcome.WIN) NewsRed else Ink
                    )
                    Text(
                        "${m.dateLabel} ${m.competition}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink,
                        maxLines = 1
                    )
                    if (m.homeAway != "N") HomeAwayTag(isHome = m.homeAway == "H")
                }
            }
        }
    }
}

/** 過去の試合1つ。押すと公式の結果のページを開く */
@Composable
private fun MatchRow(m: HistoryMatch, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LineGray)
            .background(Paper)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                m.dateLabel + (if (m.time.isNotBlank()) " ${m.time}" else ""),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            if (m.homeAway != "N") HomeAwayTag(isHome = m.homeAway == "H")
        }
        Text(m.title.ifBlank { m.competition }, style = MaterialTheme.typography.labelMedium, color = InkSoft, maxLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                m.scoreLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = if (m.outcome == GameOutcome.WIN) NewsRed else Ink
            )
            Text("vs ${m.opponent}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink)
        }
        if (m.scorers.isNotEmpty()) {
            Text(
                "得点 " + m.scorers.joinToString("・") { (n, g) -> if (g > 1) "$n $g" else n },
                style = MaterialTheme.typography.bodySmall,
                color = Ink
            )
        }
        val place = listOfNotNull(
            m.venue.takeIf { it.isNotBlank() },
            m.visitors?.let { "観客 ${"%,d".format(it)}人" }
        ).joinToString("・")
        if (place.isNotBlank()) Text(place, style = MaterialTheme.typography.labelSmall, color = InkSoft)
    }
}

/** これまでの選手1人(ユナイテッド・ブルーサンダー) */
@Composable
private fun HistoryPlayerCard(team: Team, p: HistoryPlayer) {
    // 写真:いまのチームの選手の写真(公式サイトの選手紹介など)に名前が同じ選手がいれば出す。
    // いなければ、過去の記録に入っている写真(ブローウィンズ:Bリーグ公式の、福井にいたシーズンの写真)。
    // 元選手の写真は、ユナイテッド公式サイトには残っていない(選手のページが消えている)ため出せない
    val photo = remember(team, p.name, p.photo, TeamPhotos.own) {
        TeamPhotos.findByName(TeamPhotos.forTeam(team), p.name)
            ?: p.photo.takeIf { it.isNotBlank() }?.let { com.fukuiteams.app.data.PhotoRef(it, p.name) }
            // 丸岡RUCKの元選手は「あの選手は今」の写真
            ?: Alumni.players.firstOrNull { it.team == team && it.photo.isNotBlank() &&
                it.name.replace(Regex("[\\s　]"), "") == p.name.replace(Regex("[\\s　]"), "") }
                ?.let { com.fukuiteams.app.data.PhotoRef(it.photo, p.name) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LineGray)
            .background(Paper)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
    if (photo != null) {
        RemoteThumbnail(photo.url, width = 44.dp, height = 55.dp, alignTop = true, zoomCaption = photo.label)
    }
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                (if (p.number.isNotBlank()) "#${p.number} " else "") + p.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            if (p.position.isNotBlank()) Text(p.position, style = MaterialTheme.typography.labelSmall, color = InkSoft)
            when (p.now) {
                true -> Text("在籍中", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = NewsRed)
                false -> Text("元選手", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                null -> {}
            }
        }
        val record = if (team == Team.UNITED || team == Team.BLOWINDS)
            "出場 ${p.apps}試合(先発${p.starts})・${p.goals}得点"
        else
            "${p.apps}試合・${p.goals}得点"
        Text(record, style = MaterialTheme.typography.bodySmall, color = if (p.goals > 0) Ink else InkSoft)
        Text(
            "${p.seasonsLabel}(${p.seasons.size}シーズン)" + (if (p.last.isNotBlank()) "・最後の試合 ${p.last.replace("-", "/")}" else ""),
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft
        )
        // 元選手のいま(ブローウィンズ:Bリーグ公式、ブルーサンダー:リーグH公式の選手ページから)
        if (p.now == false && p.nowChecked) {
            Text(
                if (p.nowTeam.isNotBlank()) "いま:${p.nowTeam}"
                else if (team == Team.BLOWINDS) "いま:Bリーグのクラブには所属していません"
                else "いま:リーグHのチームには所属していません",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (p.nowTeam.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                color = if (p.nowTeam.isNotBlank()) NewsRed else InkSoft
            )
        }
    }
    }
}

/** 丸岡RUCKの元選手1人(退団・移籍のお知らせと、いまのクラブ) */
@Composable
private fun AlumniCard(a: AlumniPlayer, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LineGray)
            .background(Paper)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
    // 退団・移籍のお知らせに載っている写真(あれば)
    if (a.photo.isNotBlank()) {
        RemoteThumbnail(a.photo, width = 52.dp, height = 52.dp, alignTop = true, zoomCaption = a.name)
    }
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            (if (a.number.isNotBlank()) "元#${a.number} " else "") + a.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Text(
            if (a.left.isBlank()) a.note.ifBlank { "元選手" }
            else "${a.left.replace("-", "/")} ${a.kind}" + (if (a.dest.isNotBlank()) "(移籍先:${a.dest})" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = Ink
        )
        Text(
            if (a.now.isNotBlank())
                "いまは ${a.now}" + (if (a.nowNumber.isNotBlank()) "(#${a.nowNumber})" else "") +
                    (a.goals?.let { "・今季${it}得点" } ?: "")
            else "いまは女子Fリーグの選手名簿に載っていません",
            style = MaterialTheme.typography.bodySmall,
            color = if (a.now.isNotBlank()) NewsRed else InkSoft
        )
    }
    }
}

/** 日本代表・代表候補への選出1回分。押すとお知らせのページが開く */
@Composable
private fun NationalRow(team: Team, n: NationalSelection, onClick: () -> Unit) {
    // 選ばれた選手の写真。記事の中の画像を最優先(本文の選手の写真 → お知らせの一番上の画像)。
    // 無いときだけ 元選手の写真 → いまの選手の写真。名前は写真の下に出す
    val (photos, articleImage) = nationalPhotos(team, n)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NewsRed)
            .background(Paper)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(n.date.replace("-", "/"), style = MaterialTheme.typography.labelSmall, color = InkSoft)
            Text(n.kind, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = NewsRed)
        }
        Text(
            n.players.joinToString("・").ifBlank { "(選手名はお知らせのページで)" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Text(n.title, style = MaterialTheme.typography.labelSmall, color = InkSoft, maxLines = 2)
        if (articleImage) {
            // お知らせの一番上の画像(選手の写真が並んだ横長の画像)を幅いっぱいに。押すと大きく表示
            val p = photos.first()
            com.fukuiteams.app.ui.components.RemoteThumbnail(
                url = p.url,
                fillWidth = true,
                height = 120.dp,
                alignTop = true,
                zoomCaption = p.label,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(p.label, style = MaterialTheme.typography.labelSmall, color = InkSoft)
        } else if (photos.isNotEmpty()) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                com.fukuiteams.app.ui.components.PlayerPhotoRow(photos, count = photos.size, photoWidth = 52.dp, photoHeight = 65.dp)
            }
        }
    }
}
