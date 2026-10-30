package com.fukuiteams.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import kotlinx.coroutines.launch
import com.fukuiteams.app.data.Alumni
import com.fukuiteams.app.data.NameChanges
import com.fukuiteams.app.data.fgLabel
import com.fukuiteams.app.data.ftLabel
import com.fukuiteams.app.data.p3Label
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
import com.fukuiteams.app.ui.components.shortLabel
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.components.ThinRule
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper

/** 「過去の記録」の中の切り替え */
private enum class ArchiveTab(val label: String) {
    SEASONS("シーズンごと"),
    OPPONENTS("相手別"),
    MATCHES("過去の試合"),
    // チームの記録(1試合の最多得点・最多連勝など)と、個人の1試合の記録
    RECORDS("記録集")
}

/** 「過去の選手」の絞り込み・並び順 */
private enum class ArchivePlayerFilter(val label: String) {
    FORMER("元所属の選手だけ"),
    ALL("全員(今の選手も)")
}

/**
 * 歴代の選手の並び順。row は並べるボタンの段(0:全チーム、1・2:ブローウィンズだけ(細かい成績)、3:全チーム(身長・年齢))
 * ブローウィンズの出場時間・リバウンド・アシスト・スティール・ブロック・貢献度は1試合平均、成功率は通算(試投が少ない選手は後ろ)
 */
private enum class ArchivePlayerSort(val label: String, val row: Int = 0) {
    APPS("出場"),
    GOALS("得点"),
    // 最後に試合に出た日が新しい順(元選手なら、最近チームを離れた選手から)
    RECENT("最終出場"),
    NUMBER("背番号"),
    MINUTES("出場時間", 1),
    PPG("平均得点", 1),
    REBOUNDS("リバウンド", 1),
    ASSISTS("アシスト", 1),
    FG_PCT("FG成功率", 2),
    THREE_PCT("3P成功率", 2),
    FT_PCT("FT成功率", 2),
    EFFICIENCY("貢献度", 2),
    HEIGHT("身長", 3),
    AGE("年齢", 3)
}

/** 成功率で並べるときに、順位を付ける最低限の試投数(これより少ない選手は後ろに回す) */
private fun minAttempts(sort: ArchivePlayerSort): Int = when (sort) {
    ArchivePlayerSort.FG_PCT -> 30
    ArchivePlayerSort.THREE_PCT, ArchivePlayerSort.FT_PCT -> 15
    else -> 0
}

/**
 * ブローウィンズの細かい成績の並べ替えの値(分からなければ null)。成功率は成功数÷試投数。
 * t は通算か、選んだシーズンの成績
 */
private fun statSortValue(t: com.fukuiteams.app.data.StatLine?, sort: ArchivePlayerSort): Double? {
    if (t == null || t.games == 0) return null
    fun pct(m: Int, a: Int) = if (a >= maxOf(1, minAttempts(sort))) m.toDouble() / a else null
    return when (sort) {
        ArchivePlayerSort.MINUTES -> t.minutes
        ArchivePlayerSort.PPG -> t.ppg
        ArchivePlayerSort.REBOUNDS -> t.rpg
        ArchivePlayerSort.ASSISTS -> t.apg
        ArchivePlayerSort.FG_PCT -> pct(t.fgm, t.fga)
        ArchivePlayerSort.THREE_PCT -> pct(t.p3m, t.p3a)
        ArchivePlayerSort.FT_PCT -> pct(t.ftm, t.fta)
        ArchivePlayerSort.EFFICIENCY -> t.effPerGame
        else -> null
    }
}

/** 並べている項目の数字を、カードの上に1行で目立たせる(選手タブと同じ)。対象外なら null。t は通算か、選んだシーズンの成績 */
private fun historySortHighlight(t: com.fukuiteams.app.data.StatLine?, sort: ArchivePlayerSort, seasonName: String?): Pair<String, String>? {
    if (t == null) return null
    val pair = when (sort) {
        ArchivePlayerSort.MINUTES -> "平均出場時間" to "%.1f分".format(t.minutes)
        ArchivePlayerSort.PPG -> "平均得点" to "%.1f".format(t.ppg)
        ArchivePlayerSort.REBOUNDS -> "平均リバウンド" to "%.1f".format(t.rpg)
        ArchivePlayerSort.ASSISTS -> "平均アシスト" to "%.1f".format(t.apg)
        ArchivePlayerSort.FG_PCT -> "フィールドゴール成功率(通算)" to t.fgLabel()
        ArchivePlayerSort.THREE_PCT -> "3ポイント成功率(通算)" to t.p3Label()
        ArchivePlayerSort.FT_PCT -> "フリースロー成功率(通算)" to t.ftLabel()
        ArchivePlayerSort.EFFICIENCY -> "平均貢献度" to "%.1f".format(t.effPerGame)
        else -> null
    } ?: return null
    // シーズンを選んでいるときは「(通算)」を外して、シーズン名を付ける
    return if (seasonName != null) "${seasonName} ${pair.first.replace("(通算)", "")}" to pair.second else pair
}

/**
 * チームの数字と記録(下のメニューの「チーム」から開く)。チームごとに、シーズンごとの成績・相手別の通算の対戦成績・過去の試合の一覧と、
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
    // 丸岡RUCKの元選手(standings.json の「あの選手は今」)をまだ読んでいなければ読む
    LaunchedEffect(Unit) { if (Alumni.players.isEmpty()) runCatching { StandingsRepository.fetch() } }
    // 選手の写真(「過去の選手」で、いまもチームにいる選手の写真を出す)
    LaunchedEffect(Unit) { TeamPhotos.ensureLoaded() }

    // 右下の「一番上へ」「一番下へ」ボタン用
    val listState = rememberLazyListState()
    // 上の「今季」「これまでの記録」「歩み」のボタンで飛ぶ先(LazyColumn の何行目か。並べるときに数える)
    val anchors = remember { HashMap<String, Int>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 画面を下に引っ張る・右上の更新ボタンで、アプリのすべての情報(過去の記録も)を読み直す(ほかのタブと同じ)
    val pullToRefreshState = rememberPullToRefreshState()
    // 今季のチームの数字(ブローウィンズ:Bリーグ公式のクラブ成績。次の相手の分も並べる。以前は選手タブにあったもの)
    var teamPlayers by remember { mutableStateOf<Map<String, com.fukuiteams.app.data.TeamPlayers>>(emptyMap()) }
    var previews by remember { mutableStateOf<Map<String, com.fukuiteams.app.data.GamePreview>>(emptyMap()) }
    // 今季の順位・勝敗(試合タブの「順位」と同じデータ)
    var standings by remember { mutableStateOf<Map<String, com.fukuiteams.app.data.LeagueStandings>>(emptyMap()) }
    LaunchedEffect(Unit) {
        standings = runCatching { StandingsRepository.fetch() }.getOrDefault(emptyMap())
        teamPlayers = runCatching { com.fukuiteams.app.data.PlayersRepository.fetch() }.getOrDefault(emptyMap())
        previews = runCatching { com.fukuiteams.app.data.GamePreviewRepository.fetch() }.getOrDefault(emptyMap())
    }
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            val d = DataRefresher.refreshAll(context)
            teamPlayers = d.players
            previews = d.previews
            standings = d.standings
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            MastheadTopBar(
                section = "チーム",
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
        // 「今季」「これまでの記録」「歩み」へ飛ぶボタン(チームの切り替えの下に固定)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("season" to "▼ 今季", "records" to "▼ これまでの記録", "history" to "▼ 歩み").forEach { (k, label) ->
                NewsChip(label, false, modifier = Modifier.weight(label.length + 1f), horizontalPadding = 2.dp, fontSize = 12.sp) {
                    anchors[k]?.let { index -> scope.launch { listState.animateScrollToItem(index) } }
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 上の「今季」「これまでの記録」「歩み」のボタンで飛ぶ先の行の番号を数えながら並べる
            // (LazyColumn は行の番号でしか飛べないので、add・addAll で1行ずつ数える)
            var n = 0
            anchors.clear()
            fun add(key: Any? = null, content: @Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Unit) {
                item(key = key, content = content)
                n++
            }
            fun <T> addAll(
                list: List<T>,
                key: (T) -> Any,
                content: @Composable androidx.compose.foundation.lazy.LazyItemScope.(T) -> Unit
            ) {
                items(list, key = key, itemContent = content)
                n += list.size
            }
            add {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Headline("チームの数字と記録", fontSize = 22)
                    Text(
                        "今季のチームの数字と、これまでのシーズンの成績・相手ごとの通算の対戦成績・過去の試合・チームの歩みを見られます。選手ごとの数字は「選手」タブにあります。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                    DoubleRule(modifier = Modifier.padding(top = 6.dp))
                }
            }
            // チームの写真(公式サイトのお知らせの試合・イベントの写真。日替わりで1枚。無ければ出さない。選手の写真は「今季」の下に並べる)
            add(key = "banner-${team.name}") { com.fukuiteams.app.ui.components.TeamPhotoBanner(team, "team-tab") }

            // 今季の順位・勝敗(4チームとも。試合タブの「順位」と同じデータを、数字のます目で)
            anchors["season"] = n
            add { SectionLabel("今季", modifier = Modifier.padding(top = 4.dp)) }
            add { com.fukuiteams.app.ui.components.TeamSeasonCard(team, standings[team.name]) }
            // 今の選手の写真(日替わりで5人。名前を添える)
            add(key = "players-${team.name}") {
                com.fukuiteams.app.ui.components.PlayerPhotoRow(
                    TeamPhotos.rotate(TeamPhotos.forTeam(team), "team-tab"),
                    count = 5,
                    photoWidth = 56.dp,
                    photoHeight = 70.dp
                )
            }
            // 今季のリーグ戦のペースを、クラブの過去最高のシーズンとくらべる
            add { SeasonPaceBox(team) }
            // 今季のチームの数字(ブローウィンズ。Bリーグ公式のクラブ成績を、次の対戦相手と並べて比べる)
            val roster = teamPlayers[team.name]
            if (roster != null && roster.teamStats.isNotEmpty()) {
                val preview = previews.values.firstOrNull { p ->
                    p.team.equals(team.name, ignoreCase = true) ||
                        (p.team.isBlank() && com.fukuiteams.app.data.GamesRepository.games.firstOrNull { it.id == p.gameId }?.team == team)
                }
                val nextGame = preview?.let { p -> com.fukuiteams.app.data.GamesRepository.games.firstOrNull { it.id == p.gameId } }
                val opp = teamPlayers[com.fukuiteams.app.data.BLOWINDS_OPP_KEY]?.takeIf { r ->
                    r.teamStats.isNotEmpty() && (r.gameId.isBlank() || r.gameId == preview?.gameId)
                }
                add { SectionLabel("今季のチームの数字", modifier = Modifier.padding(top = 8.dp)) }
                add {
                    TeamStatsSection(
                        mine = roster,
                        mineName = team.shortLabel(),
                        opp = opp,
                        oppName = opp?.teamName?.ifBlank { null } ?: nextGame?.opponent ?: "相手"
                    )
                }
            }
            anchors["records"] = n
            add { SectionLabel("これまでの記録", modifier = Modifier.padding(top = 8.dp)) }
            // 試合ごとの記録の読み込み具合(ブローウィンズ・丸岡RUCKは少しずつ読むので、そろうまで観客数・最多得点などが欠ける)
            history?.detailProgress?.let { (done, total) ->
                if (total > 0 && done < total) {
                    add {
                        Text(
                            "試合ごとの記録を読み込み中です($done/${total}試合)。そろうまで、観客数・チーム最多得点などは一部だけ出ます。",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = NewsRed
                        )
                    }
                }
            }

            val officialSeasons = history?.officialSeasons.orEmpty()
            if (matches.isEmpty() && officialSeasons.isNotEmpty()) {
                // 試合ごとの記録がまだ無いチーム(ブローウィンズ)は、公式のシーズンごとの成績だけ出す
                add {
                    Text(
                        "シーズンごとの成績(${history?.source ?: "公式サイト"}から)。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                }
                officialSeasons.groupBy { it.season }.forEach { (s, rows) ->
                    add(key = "os-$s") { OfficialSeasonCard(team, s, rows) }
                }
            } else if (matches.isEmpty()) {
                add { NoHistoryNote(team, HistoryRepository.loaded) }
            } else {
                add {
                    // 切り替え(シーズンごと・相手別・過去の試合・記録集)は1段に並べる。
                    // 幅は文字数に合わせる(同じ幅だと、長い「シーズンごと」だけ文字が小さくなるため)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ArchiveTab.values().forEach { t ->
                            NewsChip(t.label, tab == t, modifier = Modifier.weight(t.label.length + 1f), horizontalPadding = 2.dp, fontSize = 11.sp) { tab = t }
                        }
                    }
                }
                when (tab) {
                    ArchiveTab.SEASONS -> {
                        addAll(seasons, key = { "s-" + it.season }) { s ->
                            // ブローウィンズは、Bリーグ公式のシーズンごとの成績(平均得点・平均失点)も一緒に出す
                            SeasonCard(
                                team, s,
                                officialSeasons.filter { it.season == s.season },
                                history?.awards.orEmpty().filter { it.season == s.season }
                            )
                        }
                    }
                    ArchiveTab.OPPONENTS -> {
                        add {
                            Text(
                                "${opponents.size}チームと対戦(公式戦。対戦の多い順)。押すと試合の一覧が開きます。",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft
                            )
                        }
                        addAll(opponents, key = { "o-" + it.opponent }) { o ->
                            OpponentCard(
                                record = o,
                                opened = openedOpponent == o.opponent,
                                onToggle = { openedOpponent = if (openedOpponent == o.opponent) null else o.opponent },
                                onOpenMatch = { openExternalUrl(context, it.url) }
                            )
                        }
                    }
                    ArchiveTab.MATCHES -> {
                        add {
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
                        addAll(list, key = { "m-" + it.date + it.time + it.opponent + it.url }) { m ->
                            MatchRow(m) { openExternalUrl(context, m.url) }
                        }
                    }
                    ArchiveTab.RECORDS -> {
                        add { RecordsSection(team) { openExternalUrl(context, it) } }
                    }
                }
                add {
                    Text(
                        "出典:${history?.source.orEmpty()}(押すと公式のページが開きます)",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSoft,
                        modifier = Modifier.clickable { history?.sourceUrl?.let { openExternalUrl(context, it) } }
                    )
                }
            }

            // 過去の実績(チームの歩み・タイトルなど。TeamHistory.kt)
            // 新しい順(同じ年の中も、起きた月・シーズンの順に並べる)
            val achievements = TEAM_HISTORY.filter { it.team == team }.sortedByDescending { it.sortKey }
            if (achievements.isNotEmpty()) {
                anchors["history"] = n
                add { SectionLabel("${team.shortName}の歩み", modifier = Modifier.padding(top = 8.dp)) }
                addAll(achievements, key = { "h-" + it.year + it.title }) { h ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, LineGray)
                            .background(Paper)
                            .clickable { openExternalUrl(context, h.url) }
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("${h.whenLabel}  ${h.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink)
                        Text(h.detail, style = MaterialTheme.typography.bodySmall, color = Ink)
                        Text("出典:${h.source}", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                    }
                }
            }

            // 右下のボタンに最後の行が隠れないよう、下に余白を空ける
            add { Spacer(modifier = Modifier.height(ScrollJumpBottomPadding)) }
        }
        }
        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        }
    }
}

/**
 * 過去の選手(「選手」タブの下に出す)。代表・代表候補の選出、元選手(丸岡RUCK:退団・移籍のお知らせ)、リーグの表彰、
 * これまでの選手(出場・得点。ブローウィンズは細かい成績も)。以前は「記録」タブの「過去の選手」にあったもの。
 */
@Composable
fun PastPlayersSection(team: Team, profileOf: (String) -> com.fukuiteams.app.data.PlayerBirthday? = { null }) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { HistoryRepository.ensureLoaded() }
    LaunchedEffect(Unit) { if (Alumni.players.isEmpty()) runCatching { StandingsRepository.fetch() } }
    LaunchedEffect(Unit) { TeamPhotos.ensureLoaded() }
    val history = HistoryRepository.data[team]
    var playerFilter by remember { mutableStateOf(ArchivePlayerFilter.FORMER) }
    var playerSort by remember { mutableStateOf(ArchivePlayerSort.APPS) }
    // 選んでいる並び順のボタンをもう一度押すと、逆の順番にする(選手タブと同じ)
    var reversed by remember { mutableStateOf(false) }
    // 生年月日・身長:今の選手は選手紹介(プロフィール)、元選手は選手ページから読んだもの
    fun birthdayOf(p: HistoryPlayer): java.time.LocalDate? = profileOf(p.name)?.birthday ?: p.birthday
    fun heightOf(p: HistoryPlayer): Double? = profileOf(p.name)?.heightCm ?: p.heightCm
    // 細かい成績(ブローウィンズ)をどのシーズンで比べるか(null なら通算)。チームを変えたら通算に戻す
    var statSeason by remember(team) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (team != Team.RAC || history?.players.orEmpty().isNotEmpty()) {
            val all = history?.players.orEmpty()
            val hasNow = all.any { it.now != null }
            val hasDetail = all.any { it.stats != null }
            // 細かい成績のあるシーズン(古い順)
            val detailSeasons = all.flatMap { p -> p.stats?.seasons.orEmpty().map { it.season } }.distinct().sorted()
            val season = statSeason?.takeIf { hasDetail && it in detailSeasons }
            // 比べる成績(通算か、選んだシーズン)
            fun lineOf(p: HistoryPlayer): com.fukuiteams.app.data.StatLine? =
                if (season == null) p.stats?.total else p.stats?.seasons?.firstOrNull { it.season == season }
            val filtered = all.filter { playerFilter == ArchivePlayerFilter.ALL || !hasNow || it.now == false }
                // シーズンを選んでいるときは、そのシーズンに出た選手だけ
                .filter { season == null || (lineOf(it)?.games ?: 0) > 0 }
            // 出場・得点(シーズンを選んでいるときは、そのシーズンの試合数・得点)
            fun appsOf(p: HistoryPlayer): Int = if (season == null) p.apps else lineOf(p)?.games ?: 0
            fun goalsOf(p: HistoryPlayer): Int = if (season == null) p.goals else lineOf(p)?.points ?: 0
            // 身長・年齢は、選手紹介に載っている(今の)選手だけ分かる。分からない選手は最後
            val base = when (playerSort) {
                ArchivePlayerSort.APPS -> filtered.sortedWith(compareByDescending<HistoryPlayer> { appsOf(it) }.thenByDescending { goalsOf(it) })
                ArchivePlayerSort.GOALS -> filtered.sortedWith(compareByDescending<HistoryPlayer> { goalsOf(it) }.thenByDescending { appsOf(it) })
                // 最後の試合の日付が分からない選手(丸岡RUCK)は、最後のシーズンで並べる
                ArchivePlayerSort.RECENT -> filtered.sortedWith(compareByDescending<HistoryPlayer> { it.last.ifBlank { it.seasons.lastOrNull().orEmpty() } }.thenByDescending { it.apps })
                ArchivePlayerSort.NUMBER -> filtered.sortedBy { it.number.toIntOrNull() ?: 999 }
                ArchivePlayerSort.HEIGHT -> filtered.sortedWith(
                    compareBy<HistoryPlayer> { heightOf(it) == null }.thenByDescending { heightOf(it) ?: 0.0 }
                )
                ArchivePlayerSort.AGE -> filtered.sortedWith(
                    compareBy<HistoryPlayer> { birthdayOf(it) == null }.thenBy { birthdayOf(it) }
                )
                // ブローウィンズの細かい成績(値が分からない・試投が少ない選手は最後。同じ値なら出場の多い順)
                else -> filtered.sortedWith(
                    compareBy<HistoryPlayer> { statSortValue(lineOf(it), playerSort) == null }
                        .thenByDescending { statSortValue(lineOf(it), playerSort) ?: 0.0 }
                        .thenByDescending { appsOf(it) }
                )
            }
            val sorted = if (reversed) {
                val (missing, known) = base.partition {
                    (playerSort == ArchivePlayerSort.HEIGHT && heightOf(it) == null) ||
                        (playerSort == ArchivePlayerSort.AGE && birthdayOf(it) == null) ||
                        (playerSort.row in 1..2 && statSortValue(lineOf(it), playerSort) == null)
                }
                known.reversed() + missing
            } else base
            // 歴代○位(並べている項目の順位。同じ数字は同じ順位。値が分からない選手・背番号・最終出場・年齢には付けない)。
            // 元選手だけを出しているときも、順位は今の選手も含めた全員の中で数える
            val rankPool = all.filter { season == null || (lineOf(it)?.games ?: 0) > 0 }
            val rankValue: ((HistoryPlayer) -> Double?)? = when (playerSort) {
                ArchivePlayerSort.APPS -> { p -> appsOf(p).toDouble().takeIf { it > 0 } }
                ArchivePlayerSort.GOALS -> { p -> goalsOf(p).toDouble().takeIf { it > 0 } }
                ArchivePlayerSort.HEIGHT -> { p -> heightOf(p) }
                ArchivePlayerSort.RECENT, ArchivePlayerSort.NUMBER, ArchivePlayerSort.AGE -> null
                else -> { p -> statSortValue(lineOf(p), playerSort) }
            }
            val ranks: Map<String, Int> = if (rankValue == null) emptyMap() else {
                val values = rankPool.mapNotNull { p -> rankValue(p)?.let { p.name to it } }
                values.associate { (name, v) -> name to (values.count { it.second > v } + 1) }
            }
            val rankPrefix = season?.let { "${it}シーズン " } ?: "歴代"
            SectionLabel("歴代の選手(今の選手も含む)", modifier = Modifier.padding(top = 4.dp))
            if (hasNow) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArchivePlayerFilter.values().forEach { f ->
                        NewsChip(f.label, playerFilter == f, modifier = Modifier.weight(1f)) { playerFilter = f }
                    }
                }
            }
            // ブローウィンズ:比べるシーズン(通算・シーズンごと)。シーズンを選ぶと、そのシーズンの成績で並べ替え・比べられる
            if (hasDetail && detailSeasons.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NewsChip("通算", season == null) { statSeason = null }
                    detailSeasons.asReversed().forEach { s -> NewsChip(s, season == s) { statSeason = s } }
                }
            }
            // 並び順のボタン(選手タブと同じく段に分ける)。細かい成績の段(1・2段目)は、その成績がある(ブローウィンズ)ときだけ出す
            ArchivePlayerSort.values().groupBy { it.row }.toSortedMap().forEach { (row, keys) ->
                if (row in 1..2 && !hasDetail) return@forEach
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    keys.forEach { o ->
                        SortChip(
                            if (playerSort == o && reversed) o.label + "(逆)" else o.label,
                            playerSort == o,
                            Modifier.weight(o.label.length + 1f)
                        ) {
                            if (playerSort == o) {
                                reversed = !reversed
                            } else {
                                reversed = false
                                playerSort = o
                            }
                        }
                    }
                }
            }
            if (playerSort.row == 2 && minAttempts(playerSort) > 0) {
                Text(
                    "成功率は${if (season == null) "通算の" else "そのシーズンの"}成功数÷試投数です。試投が${minAttempts(playerSort)}本より少ない選手は後ろに並べます。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft
                )
            }
            Text(
                when (team) {
                    Team.THUNDER -> "リーグH公式の試合ページのスコア表から数えています(2024-25シーズン〜)。「試合」はスコア表に名前のあった試合です。"
                    Team.BLOWINDS -> "Bリーグ公式の試合ごとのボックススコア(2023-24シーズン〜)から数えています。「試合」はコートに立った試合です。選手を押すとシーズンごとの成績が開きます。"
                    Team.RAC -> if (history?.playersFromLineup == true)
                        "女子Fリーグ公式の試合ごとの記録(出場選手・シュート・得点。2017年〜今季)から数えています。「試合」はコートに立った試合(先発・途中出場)です。"
                    else
                        "女子Fリーグ公式の過去の記録のゴールランキング(1点以上の選手)から数えた得点です。試合ごとの記録を読み込むと、出場試合数も出ます(データの自動更新を待っています)。"
                    Team.UNITED -> "ユナイテッド公式サイトの試合情報(2019年〜の公式戦)の先発・交代・得点から数えています。"
                } +
                    (if (hasNow) "「元所属」はいまの選手名簿にいない(以前に所属していた)選手です。" else "") +
                    (if (season != null) "${season}シーズンに出た選手だけを、そのシーズンの成績で並べています。" else "") +
                    (if (ranks.isNotEmpty()) "「${rankPrefix.trim()}○位」は今の選手も含めた順位です。" else "") +
                    "${sorted.size}人",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            // 試合ごとの記録の読み込み具合(ブローウィンズ・丸岡RUCKは少しずつ読むので、そろうまでは数字が少なめ)
            history?.detailProgress?.let { (done, total) ->
                if (total > 0 && done < total) {
                    Text(
                        "試合ごとの記録を読み込み中です($done/${total}試合)。そろうまで、出場・得点などの数字は少なめに出ます。",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = NewsRed
                    )
                }
            }
            if (sorted.isEmpty()) {
                Text(
                    if (HistoryRepository.loaded) "歴代の選手のデータはまだありません(データの自動更新を待っている場合もあります)。" else "読み込んでいます…",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            }
            val mark = when (playerSort) {
                ArchivePlayerSort.HEIGHT -> ProfileMark.HEIGHT
                ArchivePlayerSort.AGE -> ProfileMark.AGE
                else -> ProfileMark.NONE
            }
            sorted.forEach { p ->
                androidx.compose.runtime.key(team, p.name) {
                    HistoryPlayerCard(
                        team, p, history?.playersFromLineup == true, profileOf(p.name), mark,
                        historySortHighlight(lineOf(p), playerSort, season?.let { "${it}シーズン" }),
                        rank = ranks[p.name]?.let { "$rankPrefix${it}位(${playerSort.label})" },
                        seasonLine = if (season != null) lineOf(p) else null
                    )
                }
            }
        }
        // 代表・代表候補の選出(チームの公式サイトのお知らせから)
        val national = history?.national.orEmpty()
        SectionLabel("代表・代表候補の選出", modifier = Modifier.padding(top = 4.dp))
        if (national.isEmpty()) {
            Text(
                when {
                    !HistoryRepository.loaded -> "読み込んでいます…"
                    team == Team.RAC -> "日本代表の選出のお知らせを読み込めませんでした(データの自動更新を待っている場合もあります)。"
                    team == Team.BLOWINDS -> "ブローウィンズ公式サイトのお知らせ(2023年7月〜)には、代表に選ばれたという記事は見つかりませんでした(データの自動更新を待っている場合もあります)。"
                    else -> "${team.shortName}の公式サイトのお知らせには、日本代表に選ばれたという記事は見つかりませんでした。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        } else {
            // 選手ごとの選出回数(結婚などで名字が変わった選手は、今の名前でまとめて数える)
            val counts = national.flatMap { it.players }.map { NameChanges.current(team, it) }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }
            Text(
                (if (team == Team.RAC) "丸岡RUCK公式サイトのお知らせとJFA公式のメンバー発表 " else "公式サイトのお知らせ ") +
                    "${national.size}件から。選ばれた回数:" +
                    counts.joinToString("・") { "${it.key} ${it.value}回" },
                style = MaterialTheme.typography.bodySmall,
                color = Ink
            )
            national.forEach { n -> NationalRow(team, n) { openExternalUrl(context, n.link) } }
        }
        // リーグの表彰(丸岡RUCK:女子Fリーグ公式の過去の記録の「最優秀選手賞」「ベストファイブ」など)
        val awards = history?.awards.orEmpty()
        if (awards.isNotEmpty()) {
            SectionLabel("リーグの表彰", modifier = Modifier.padding(top = 4.dp))
            awards.forEach { a ->
                Text(
                    "${seasonLabel(a.season)}  ${a.award}  ${a.name}" + (if (a.note.isNotBlank()) "(${a.note})" else ""),
                    modifier = Modifier.fillMaxWidth().border(1.dp, LineGray).background(Paper).padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }
        }
        if (team == Team.RAC) {
            // 丸岡RUCKは、公式サイトの退団・移籍のお知らせからの元選手(「あの選手は今」と同じデータ)
            // 歴代の選手の一覧に出ている選手は、そのカードに「いま」・退団の情報を出すので、ここには出さない
            val listed = history?.players.orEmpty().map { it.name.replace(Regex("[\\s　]"), "") }.toSet()
            val alumni = Alumni.players.filter { it.team == Team.RAC && NameChanges.current(team, it.name).replace(Regex("[\\s　]"), "") !in listed }
                .sortedByDescending { it.left }
            if (alumni.isNotEmpty() || listed.isEmpty()) {
            SectionLabel("ほかの元選手(退団・移籍のお知らせから)", modifier = Modifier.padding(top = 4.dp))
            Text(
                if (alumni.isEmpty()) "元選手のデータを読み込めませんでした(データの自動更新を待っている場合もあります)。"
                else "上の歴代の選手に出ていない、丸岡RUCK公式サイトの退団・移籍のお知らせ(2019年〜)の元選手${alumni.size}人。いまのクラブは女子Fリーグ公式の選手名簿から調べています。押すとページが開きます。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            alumni.forEach { a -> AlumniCard(a) { openExternalUrl(context, a.nowUrl.ifBlank { a.link }) } }
            }
        }
    }
}

/** シーズンごとのチーム内1位(細かい成績がある選手の中で、最多得点・最多出場) */
internal data class SeasonLeaders(val topScorer: String, val topPoints: Int, val mostGames: Int)

/** チームの歴代の選手の細かい成績から、シーズン → チーム内1位(ブローウィンズ)。細かい成績が無ければ空 */
internal fun seasonLeaders(team: Team): Map<String, SeasonLeaders> {
    val rows = HistoryRepository.data[team]?.players.orEmpty().flatMap { p ->
        p.stats?.seasons.orEmpty().map { s -> Triple(s.season, p.name, s) }
    }
    return rows.groupBy { it.first }.mapValues { (_, list) ->
        val top = list.maxByOrNull { it.third.points }
        SeasonLeaders(top?.second.orEmpty(), top?.third?.points ?: 0, list.maxOfOrNull { it.third.games } ?: 0)
    }
}

/** そのシーズンの成績がチーム内1位なら、印の文字(「★チーム最多得点・★チーム最多出場」)。違えば "" */
internal fun seasonStar(s: com.fukuiteams.app.data.StatLine, leaders: Map<String, SeasonLeaders>): String {
    val l = leaders[s.season] ?: return ""
    return listOfNotNull(
        "★チーム最多得点".takeIf { l.topPoints > 0 && s.points == l.topPoints },
        "★チーム最多出場".takeIf { l.mostGames > 0 && s.games == l.mostGames }
    ).joinToString("・")
}

/** 成功率などの1行(選手タブの StatRow と同じ見た目) */
@Composable
private fun HistoryStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = Ink)
    }
}

/** 細かい成績の2行(試合数・平均出場時間・平均得点・リバウンド・アシストと、成功率・スティール・ブロック・貢献度) */
@Composable
internal fun StatLines(s: com.fukuiteams.app.data.StatLine, label: String, star: String = "") {
    Column(modifier = Modifier.padding(top = 2.dp)) {
        // そのシーズンのチーム内1位(最多得点・最多出場)の印
        if (star.isNotBlank()) {
            Text(star, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = NewsRed)
        }
        Text(
            "$label ${s.games}試合・平均${"%.1f".format(s.minutes)}分  ${"%.1f".format(s.ppg)}点・" +
                "${"%.1f".format(s.rpg)}リバウンド・${"%.1f".format(s.apg)}アシスト",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Text(
            "FG ${s.fgLabel()}・3P ${s.p3Label()}・FT ${s.ftLabel()}・" +
                "スティール${"%.1f".format(s.spg)}・ブロック${"%.1f".format(s.bpg)}・貢献度${"%.1f".format(s.effPerGame)}",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft
        )
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
    val events = TEAM_HISTORY.filter { it.team == team && it.year == startYear }.sortedBy { it.sortKey }
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
                "${r.comp}  ${r.win}勝${r.lose}敗(${r.win + r.lose}試合・${com.fukuiteams.app.data.winRateLabel(r.win, r.win + r.lose)})",
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
    val events = TEAM_HISTORY.filter { it.team == team && it.year == startYear }.sortedBy { it.sortKey }
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
                "${s.leagueName ?: "リーグ戦"}  ${l.label}(${l.games}試合・${l.rateLabel})",
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
                "公式戦すべて  ${s.all.label}(${s.all.games}試合・${s.all.rateLabel}・${s.all.scored}得点${s.all.conceded}失点)",
                style = MaterialTheme.typography.bodySmall,
                color = Ink
            )
        }
        s.topScorer?.let { (name, goals) ->
            Text("チーム最多得点  $name ${goals}点", style = MaterialTheme.typography.bodySmall, color = Ink)
        }
        // ブローウィンズは試合ごとのボックススコアから(得点者の記録が試合に無いため)
        if (s.topScorer == null) {
            seasonLeaders(team)[s.season]?.takeIf { it.topPoints > 0 }?.let { l ->
                Text("チーム最多得点  ${l.topScorer} ${l.topPoints}点", style = MaterialTheme.typography.bodySmall, color = Ink)
            }
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
                "公式記録 ${r.comp}  ${r.win}勝${r.lose}敗(${com.fukuiteams.app.data.winRateLabel(r.win, r.win + r.lose)})" +
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
            "通算 ${record.record.label}(${record.record.games}試合・${record.record.rateLabel}・${record.record.scored}得点${record.record.conceded}失点)",
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

/**
 * 記録集:チームの記録(1試合の最多得点・最大点差の勝利・最多連勝・ホームの最多観客など)と、
 * 個人の1試合の記録の上位5人(ブローウィンズ:得点・リバウンド・アシスト、ほかのチーム:得点)。公式戦だけ
 */
@Composable
private fun RecordsSection(team: Team, onOpenUrl: (String) -> Unit) {
    val history = HistoryRepository.data
    val records = remember(team, history) { com.fukuiteams.app.data.teamRecords(team) }
    val unit = if (team == Team.BLOWINDS) "点" else "得点"
    // 個人の1試合の記録(選手の試合ごとの記録から。無いときは試合の得点者から)
    val individual: List<Pair<String, List<com.fukuiteams.app.data.GameHigh>>> = remember(team, history) {
        val pts = com.fukuiteams.app.data.singleGameHighs(team) { it.points }
            .ifEmpty { if (team != Team.BLOWINDS) com.fukuiteams.app.data.singleGameGoalsFromScorers(team) else emptyList() }
        listOfNotNull(
            ("1試合の最多得点" to pts).takeIf { pts.isNotEmpty() },
            com.fukuiteams.app.data.singleGameHighs(team) { it.rebounds }.takeIf { it.isNotEmpty() }?.let { "1試合の最多リバウンド" to it },
            com.fukuiteams.app.data.singleGameHighs(team) { it.assists }.takeIf { it.isNotEmpty() }?.let { "1試合の最多アシスト" to it }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "公式の試合の記録(エキシビジョンを除く公式戦)から数えた、${team.shortName}の記録です。押すと公式の試合のページが開きます。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        if (records.isEmpty()) {
            Text("記録を数えられる試合がまだありません。", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        records.forEach { r ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Ink)
                    .background(Paper)
                    .then(if (r.url != null) Modifier.clickable { onOpenUrl(r.url) } else Modifier)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink)
                    Text(r.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = NewsRed)
                }
                Text(r.detail, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }
        ChallengeSection(team)
        if (individual.isNotEmpty()) {
            SectionLabel("個人の1試合の記録", modifier = Modifier.padding(top = 4.dp))
            individual.forEach { (label, rows) ->
                Column(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Ink).background(Paper).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink)
                    // 同じ数字は同じ順位
                    rows.forEach { h ->
                        val place = rows.count { it.value > h.value } + 1
                        val u = when (label) {
                            "1試合の最多得点" -> unit
                            else -> ""
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${place}位", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (place == 1) NewsRed else Ink, modifier = Modifier.width(30.dp))
                            Text("${h.value}$u", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.width(52.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(h.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Ink)
                                Text(
                                    "${h.dateLabel} vs ${h.opponent}" + (if (h.scoreLabel.isNotBlank()) " ${h.scoreLabel}" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        history[team]?.detailProgress?.let { (done, total) ->
            if (total > 0 && done < total) {
                Text(
                    "試合ごとの記録を読み込み中です($done/${total}試合)。そろうまで、個人の記録・観客数は一部の試合だけから数えています。",
                    style = MaterialTheme.typography.labelSmall,
                    color = NewsRed
                )
            }
        }
    }
}

/** 今季のリーグ戦のペースを、クラブの過去最高のシーズンとくらべる(今季の欄)。くらべられなければ何も出さない */
@Composable
private fun SeasonPaceBox(team: Team) {
    val history = HistoryRepository.data
    val pace = remember(team, history) {
        com.fukuiteams.app.data.seasonPace(team, java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")))
    } ?: return
    Column(
        modifier = Modifier.fillMaxWidth().border(1.dp, Ink).background(Paper).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("クラブ最高のシーズンとくらべると(${pace.leagueName ?: "リーグ戦"})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text(
            "今季 ${pace.record}。それぞれの項目で、これまでのシーズン(リーグ戦10試合以上)のいちばん良い数字とくらべます。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        pace.rows.forEach { r ->
            ThinRule(color = LineGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Ink)
                Text(r.now, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink)
                Column(horizontalAlignment = Alignment.End) {
                    Text("最高 ${r.best}(${r.bestSeason})", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                    if (r.ahead) Text("▲上回るペース", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = NewsRed)
                }
            }
        }
    }
}

/** 記録への挑戦(今の選手の、次の節目まで。記録集の中) */
@Composable
private fun ChallengeSection(team: Team) {
    val history = HistoryRepository.data
    val rows = remember(team, history) { com.fukuiteams.app.data.milestoneBoard(team).take(8) }
    if (rows.isEmpty()) return
    val unit = com.fukuiteams.app.data.goalUnit(team)
    SectionLabel("記録への挑戦", modifier = Modifier.padding(top = 4.dp))
    Text(
        "今の選手の、${team.shortName}での通算の記録と次の節目まで(近い順)。「早ければ」は通算の1試合平均で数えた目安です。",
        style = MaterialTheme.typography.bodySmall,
        color = InkSoft
    )
    rows.forEach { r ->
        val p = r.player
        Column(
            modifier = Modifier.fillMaxWidth().border(1.dp, Ink).background(Paper).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "通算${p.apps}試合・${p.goals}$unit" + (r.rank?.let { "・歴代${it}位" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft,
                    maxLines = 1
                )
            }
            ChallengeLine(
                "通算${r.goal.step}${unit}まで あと",
                r.goal.left,
                r.goal.games?.let { "(平均${"%.1f".format(r.avg)}$unit・早ければ${it}試合後)" } ?: ""
            )
            r.apps?.let { ChallengeLine("通算${it.step}試合出場まで あと", it.left, "") }
            r.above?.let { ChallengeLine("歴代${it.rank}位の${it.name}(${it.goals}$unit)を抜くまで あと", it.gap, "") }
        }
    }
}

/** 「〜まで あと 49 (補足)」の1行。数字を赤く太く */
@Composable
private fun ChallengeLine(lead: String, value: Int, note: String) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append(lead)
            pushStyle(androidx.compose.ui.text.SpanStyle(color = NewsRed, fontWeight = FontWeight.Bold))
            append(value.toString())
            pop()
            if (note.isNotEmpty()) {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = InkSoft, fontSize = 11.sp))
                append(note)
                pop()
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = Ink
    )
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
private fun HistoryPlayerCard(
    team: Team,
    p: HistoryPlayer,
    fromLineup: Boolean = false,
    profile: com.fukuiteams.app.data.PlayerBirthday? = null,
    mark: ProfileMark = ProfileMark.NONE,
    /** 並べている項目の名前と数字(ブローウィンズの細かい成績で並べているとき)。カードの上に1行で目立たせる */
    highlight: Pair<String, String>? = null,
    /** 並べている項目での順位(例「歴代3位(得点)」)。無ければ null */
    rank: String? = null,
    /** シーズンを選んで比べているときの、そのシーズンの成績(ます目をこの数字にする)。通算なら null */
    seasonLine: com.fukuiteams.app.data.StatLine? = null
) {
    // 選手タブの今の選手のカードと同じ見た目(写真・背番号・名前・プロフィール・数字のます目)。
    // 写真:選手紹介の写真 → いまのチームの選手の写真 → 過去の記録の写真(ブローウィンズ:Bリーグ公式) → 丸岡RUCKの「あの選手は今」の写真
    // (元選手の写真は、ユナイテッド公式サイトには残っていない(選手のページが消えている)ため出せない)
    // 候補を順に試す(読み込めなければ次の写真。全部だめでも同じ大きさの枠は出す)
    val photos = remember(team, p.name, p.photo, profile?.photo, TeamPhotos.own) {
        val key = p.name.replace(Regex("[\\s　]"), "")
        listOfNotNull(
            profile?.photo,
            TeamPhotos.findByName(TeamPhotos.forTeam(team), p.name)?.url,
            p.photo,
            Alumni.players.firstOrNull { it.team == team && it.photo.isNotBlank() && it.name.replace(Regex("[\\s　]"), "") == key }?.photo
        )
    }
    // 細かい成績(ブローウィンズ)がある選手は、押すとシーズンごとの成績が開く
    var opened by remember(team, p.name) { mutableStateOf(false) }
    // 出た試合の一覧・相手別の成績(試合ごとの記録がある選手だけ)
    var logOpened by remember(team, p.name) { mutableStateOf(false) }
    var splitOpened by remember(team, p.name) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .then(if (p.stats != null) Modifier.clickable { opened = !opened } else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 写真が無い・読み込めない選手も、並びがそろうよう同じ大きさの枠を出す
            PlayerPhotoBox(photos, p.name)
            val number = p.number.ifBlank { profile?.number.orEmpty() }
            Headline(if (number.isNotBlank()) "#$number" else "#-", fontSize = 22, modifier = Modifier.width(58.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                // 結婚などで名字が変わった選手は、前の名前も添える
                if (p.former.isNotEmpty()) {
                    Text("旧:${p.former.joinToString("・")}", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                }
                profile?.reading?.let { PlayerReading(it) }
                (p.position.ifBlank { profile?.position.orEmpty() }).takeIf { it.isNotBlank() }?.let {
                    Text("ポジション:$it", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
                when (p.now) {
                    true -> Text("在籍中", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = NewsRed)
                    false -> Text("元所属", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = InkSoft)
                    null -> {}
                }
            }
        }
        // 生年月日・身長など(今の選手は選手紹介から。元選手は選手ページから読んだ生年月日・身長)。
        // 元選手も今の選手と同じ形(「○年○月○日生まれ(○歳)」「○cm」。並べている項目は色の付いた枠で太字)にそろえる
        if (profile != null) {
            ProfileLines(profile, mark)
        } else {
            val heightText = p.heightCm?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()
            val bd = p.birthday
            if (bd != null) {
                ProfileLines(
                    com.fukuiteams.app.data.PlayerBirthday(
                        team = team, name = p.name, number = p.number, photo = "", birthday = bd, height = heightText
                    ),
                    mark
                )
            } else if (heightText.isNotBlank()) {
                ProfileLine("${heightText}cm", marked = mark == ProfileMark.HEIGHT)
            }
        }
        // 丸岡RUCKの元所属の選手:退団・移籍のお知らせ(あの選手は今)の、退団の時期と今のクラブ
        val alumni = if (team == Team.RAC && p.now != true) {
            val key = p.name.replace(Regex("[\\s　]"), "")
            Alumni.players.firstOrNull {
                it.team == team && NameChanges.current(team, it.name).replace(Regex("[\\s　]"), "") == key
            }
        } else null
        // 元所属の選手のいま(ブローウィンズ:Bリーグ公式、ブルーサンダー:リーグH公式、丸岡RUCK:女子Fリーグ公式の選手名簿から)。
        // 丸岡RUCKで「あの選手は今」にいまのクラブがあれば、そちらを出す(同じことを2回出さない)
        val nowFromAlumni = alumni?.now?.takeIf { it.isNotBlank() }
        if (nowFromAlumni != null) {
            Text("いま:$nowFromAlumni", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = NewsRed)
        } else if (p.now == false && p.nowChecked) {
            Text(
                if (p.nowTeam.isNotBlank()) "いま:${p.nowTeam}"
                else when (team) {
                    Team.BLOWINDS -> "いま:Bリーグのクラブには所属していません"
                    Team.RAC -> "いま:女子Fリーグのクラブには所属していません"
                    else -> "いま:リーグHのチームには所属していません"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (p.nowTeam.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                color = if (p.nowTeam.isNotBlank()) NewsRed else InkSoft
            )
        }
        if (alumni != null) {
            // 退団の時期・種類・移籍先(種類が「元選手」だけのときは、上の「元所属」と同じなので出さない)
            val leftText = listOfNotNull(
                alumni.left.takeIf { it.isNotBlank() }?.replace("-", "/"),
                alumni.kind.takeIf { it.isNotBlank() && it != "元選手" },
                alumni.dest.takeIf { it.isNotBlank() }?.let { "($it)" }
            ).joinToString(" ").ifBlank { alumni.note.replace("元選手", "元所属の選手") }
            if (leftText.isNotBlank()) {
                Text(leftText, style = MaterialTheme.typography.labelSmall, color = InkSoft)
            }
        }
        // シーズンと最後の試合は、途中で折り返さないよう2行に分ける
        Text(
            "${p.seasonsLabel}(${p.seasons.size}シーズン)",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft
        )
        if (p.last.isNotBlank()) {
            Text("最後の試合 ${p.last.replace("-", "/")}", style = MaterialTheme.typography.labelSmall, color = InkSoft)
        }
        // 並べている項目での順位(歴代○位)
        rank?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = NewsRed)
        }
        // 並べている項目の数字を、開かなくても見えるように1行出す(選手タブと同じ)
        highlight?.let { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(com.fukuiteams.app.ui.theme.Accent.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = Ink)
            }
        }
        val st = p.stats
        if (st != null && st.total.games > 0) {
            // ブローウィンズ:選手タブの今の選手と同じます目(試合・平均出場時間・平均得点・リバウンド・アシスト)。
            // 通算の数字(シーズンを選んで比べているときは、そのシーズンの数字)
            val t = seasonLine ?: st.total
            if (seasonLine != null) {
                Text("${seasonLine.season}シーズンの成績", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = InkSoft)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell("試合", "${t.games}", Modifier.weight(1f))
                StatCell("出場時間", "%.1f".format(t.minutes), Modifier.weight(1.2f))
                StatCell("得点", "%.1f".format(t.ppg), Modifier.weight(1f))
                StatCell("リバウンド", "%.1f".format(t.rpg), Modifier.weight(1.3f))
                StatCell("アシスト", "%.1f".format(t.apg), Modifier.weight(1f))
            }
            Text(
                if (opened) "▲ 閉じる" else "▼ 成功率・シーズンごとの成績(先発${p.starts}試合・通算${p.goals}得点)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = NewsRed
            )
            if (opened) {
                // 選手タブと同じ並び(成功率は「45.0%(18/40)」)。通算のあとに、シーズンごと(新しい順)
                Column {
                    HistoryStatRow("フィールドゴール成功率", t.fgLabel())
                    HistoryStatRow("3ポイント成功率", t.p3Label())
                    HistoryStatRow("フリースロー成功率", t.ftLabel())
                    HistoryStatRow("スティール(平均)", "%.1f".format(t.spg))
                    HistoryStatRow("ブロック(平均)", "%.1f".format(t.bpg))
                    HistoryStatRow("貢献度(平均)", "%.1f".format(t.effPerGame))
                }
                val leaders = remember(team, HistoryRepository.data) { seasonLeaders(team) }
                st.seasons.asReversed().forEach { s -> StatLines(s, seasonLabel(s.season), seasonStar(s, leaders)) }
            }
        } else {
            // 数字のます目(選手タブと同じ形)。丸岡RUCKでゴールランキングからのときは、出場・先発は分からないので「-」
            val knowApps = team != Team.RAC || fromLineup
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell("出場", if (knowApps) "${p.apps}試合" else "-", Modifier.weight(1f))
                if (team != Team.THUNDER) {
                    StatCell("先発", if (knowApps) "${p.starts}" else "-", Modifier.weight(1f))
                }
                StatCell("得点", "${p.goals}", Modifier.weight(1f))
                if (team == Team.RAC && fromLineup) {
                    StatCell("シュート", p.shots?.toString() ?: "-", Modifier.weight(1f))
                }
            }
        }
        // 出た試合の一覧と、相手別の成績(試合ごとの記録から)
        if (p.log.isNotEmpty()) {
            HistoryGameLogSection(team, p, logOpened, { logOpened = !logOpened }, splitOpened, { splitOpened = !splitOpened })
        }
    }
}

/** 1試合分の、選手の数字(「先発・12点・5リバウンド・3アシスト・24分」「途中出場・2得点・シュート5」) */
private fun gameLogLine(team: Team, l: com.fukuiteams.app.data.HistoryGameLog): String = listOfNotNull(
    if (l.started) "先発" else "途中出場".takeIf { team != Team.THUNDER },
    if (team == Team.BLOWINDS) "${l.points}点" else "${l.points}得点",
    l.rebounds?.let { "${it}リバウンド" },
    l.assists?.let { "${it}アシスト" },
    l.seconds?.let { "${it / 60}分" },
    l.shots?.let { "シュート$it" }
).joinToString("・")

/** 選手の「出た試合」(新しい順)と「相手別の成績」。押すと開く */
@Composable
private fun HistoryGameLogSection(
    team: Team,
    p: HistoryPlayer,
    logOpened: Boolean,
    onToggleLog: () -> Unit,
    splitOpened: Boolean,
    onToggleSplit: () -> Unit
) {
    // 出た試合は多いと長くなるので、はじめは新しい20試合だけ。「もっと見る」で全部
    var showAll by remember(team, p.name) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NewsChip(
                (if (logOpened) "▲ " else "▼ ") + "出た試合(${p.log.size})",
                logOpened,
                modifier = Modifier.weight(1f),
                horizontalPadding = 2.dp,
                fontSize = 11.sp
            ) { onToggleLog() }
            NewsChip(
                (if (splitOpened) "▲ " else "▼ ") + "相手別の成績",
                splitOpened,
                modifier = Modifier.weight(1f),
                horizontalPadding = 2.dp,
                fontSize = 11.sp
            ) { onToggleSplit() }
        }
        if (logOpened) {
            val list = p.log.asReversed()
            (if (showAll) list else list.take(20)).forEach { l ->
                Column(modifier = Modifier.fillMaxWidth().border(1.dp, LineGray).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            l.scoreLabel.ifBlank { "-" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (l.won) NewsRed else Ink
                        )
                        Text(
                            "${l.dateLabel} vs ${l.opponent}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (l.homeAway == "H" || l.homeAway == "A") HomeAwayTag(isHome = l.homeAway == "H")
                    }
                    Text(gameLogLine(team, l), style = MaterialTheme.typography.labelSmall, color = InkSoft)
                }
            }
            if (!showAll && list.size > 20) {
                Text(
                    "▼ 残り${list.size - 20}試合も見る",
                    modifier = Modifier.fillMaxWidth().clickable { showAll = true }.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = NewsRed
                )
            }
        }
        if (splitOpened) {
            val splits = remember(team, p.name, p.log) { com.fukuiteams.app.data.opponentSplits(p.log) }
            val unit = if (team == Team.BLOWINDS) "点" else "得点"
            splits.forEach { sp ->
                Row(
                    modifier = Modifier.fillMaxWidth().border(1.dp, LineGray).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        sp.opponent,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${sp.games}試合(${sp.wins}勝)・" +
                            (if (team == Team.BLOWINDS) "平均${"%.1f".format(sp.pointsPerGame)}点" else "${sp.points}$unit") +
                            (sp.rebounds?.let { r -> "・${"%.1f".format(r.toDouble() / sp.games)}R" } ?: "") +
                            (sp.assists?.let { a -> "・${"%.1f".format(a.toDouble() / sp.games)}A" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSoft
                    )
                }
            }
            if (team == Team.BLOWINDS) {
                Text("R=リバウンド、A=アシスト(1試合平均)", style = MaterialTheme.typography.labelSmall, color = InkSoft)
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
            if (a.left.isBlank()) a.note.replace("元選手", "元所属の選手").ifBlank { "元所属の選手" }
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
                // 写真を切り取らずに縦横比のまま出す(縦長の顔写真も顔が切れないように)
                naturalAspect = true,
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
