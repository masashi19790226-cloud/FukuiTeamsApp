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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fukuiteams.app.ui.components.shortLabel
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.DataStatus
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.data.BirthdaysRepository
import com.fukuiteams.app.data.PlayerBirthday
import com.fukuiteams.app.data.profileKey
import com.fukuiteams.app.data.profileMap
import com.fukuiteams.app.data.DataStatusRepository
import com.fukuiteams.app.data.GamePreview
import com.fukuiteams.app.data.GamePreviewRepository
import com.fukuiteams.app.data.GamesRepository
import com.fukuiteams.app.data.KeyPlayer
import com.fukuiteams.app.data.BLOWINDS_OPP_KEY
import com.fukuiteams.app.data.PlayerStats
import com.fukuiteams.app.data.PlayerGameLog
import com.fukuiteams.app.data.PlayersRepository
import com.fukuiteams.app.data.LeagueScorers
import com.fukuiteams.app.data.LeagueStandings
import com.fukuiteams.app.data.ScorerRow
import com.fukuiteams.app.data.TeamStat
import com.fukuiteams.app.data.StandingRow
import com.fukuiteams.app.data.StandingsRepository
import com.fukuiteams.app.data.TeamPlayers
import com.fukuiteams.app.data.TeamSelection
import com.fukuiteams.app.data.DataRefresher
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.DoubleRule
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.MastheadTopBar
import com.fukuiteams.app.ui.components.ScrollJumpButtons
import com.fukuiteams.app.ui.components.ScrollJumpBottomPadding
import com.fukuiteams.app.ui.components.RemoteThumbnail
import com.fukuiteams.app.ui.components.SectionLabel
import com.fukuiteams.app.ui.components.TeamSelectorRow
import com.fukuiteams.app.ui.components.ThinRule
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.Paper
import kotlinx.coroutines.launch

private const val NO_DATA = "データなし"

// Bリーグ公式の福井ブローウィンズのクラブページ「選手情報」(全選手の今季成績はここから取得している)
private const val BLOWINDS_BLEAGUE_URL = "https://www.bleague.jp/club_detail/?TeamID=2891&tab=1"

/**
 * 成績の文字列(例:「平均18.5点・5.5アシスト」)から部門ごとの数字を取り出す。
 * 文字列に書かれている数字だけを使い、書かれていない部門は null(=データなし)。
 */
private data class PlayerNumbers(val points: String?, val rebounds: String?, val assists: String?)

private fun parseStat(stat: String): PlayerNumbers {
    fun find(unit: String): String? =
        Regex("""([0-9]+(?:\.[0-9]+)?)\s*$unit""").find(stat)?.groupValues?.get(1)
    return PlayerNumbers(points = find("点"), rebounds = find("リバウンド"), assists = find("アシスト"))
}

/**
 * 選手の数字。
 * ・ブローウィンズの全選手:players.json(Bリーグ公式のクラブページ「選手情報」の選手一覧と今季成績)
 *   まだ届いていないときは、previews.json のクラブリーダー(各部門のチーム1位)を代わりに出す
 * ・次の対戦相手の注目選手:previews.json
 * 取得していない数字は「データなし」と表示し、作らない。
 */
@Composable
fun PlayersScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // ほかのタブで選んでいたチームで開く(「すべて」のときは直前に選んでいたチーム)
    var selectedTeam by remember { mutableStateOf(TeamSelection.teamForSingle()) }
    var previews by remember { mutableStateOf<Map<String, GamePreview>?>(null) }
    var teamPlayers by remember { mutableStateOf<Map<String, TeamPlayers>>(emptyMap()) }
    // 並び順(背番号順・得点順・出場時間順・リバウンド順・アシスト順)
    var sortKey by remember { mutableStateOf(PlayerSort.NUMBER) }
    // 対戦相手の全選手一覧の並び順(福井側とは別に選べる)
    var oppSortKey by remember { mutableStateOf(PlayerSort.NUMBER) }

    // 上の「自チーム」「相手」リンクで、その見出しの位置まで画面を動かすための情報
    val scrollState = rememberScrollState()
    // (画面の描き直しを起こさないよう、状態(State)ではなくただの入れ物に位置を覚えておく)
    val marks = remember { JumpMarks() }
    fun jumpTo(target: LayoutCoordinates?) {
        val viewport = marks.viewport ?: return
        val t = target ?: return
        if (!viewport.isAttached || !t.isAttached) return
        val dy = t.positionInRoot().y - viewport.positionInRoot().y
        scope.launch { scrollState.animateScrollTo((scrollState.value + dy).toInt().coerceAtLeast(0)) }
    }

    // 自動更新の最終時刻(相手の注目選手・主な選手の「何日時点」表示に使う)
    var dataStatus by remember { mutableStateOf<DataStatus?>(null) }

    // 丸岡RUCK・ユナイテッドの順位表
    var leagueStandings by remember { mutableStateOf<Map<String, LeagueStandings>>(emptyMap()) }

    // 3チームの選手の身長・体重・出身地など(公式サイトの選手ページから。チーム+名前で引く)
    var profiles by remember { mutableStateOf<Map<String, PlayerBirthday>>(emptyMap()) }

    suspend fun load() {
        profiles = BirthdaysRepository.fetch().profileMap()
        teamPlayers = PlayersRepository.fetch()
        leagueStandings = StandingsRepository.fetch()
        previews = GamePreviewRepository.fetch()
        dataStatus = DataStatusRepository.fetch()
    }

    LaunchedEffect(Unit) { load() }

    // 画面を下に引っ張る・右上の更新ボタンで、選手データを読み込み直す
    val pullToRefreshState = rememberPullToRefreshState()
    // 手動で更新したときは、アプリのすべての情報を読み直す
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            val d = DataRefresher.refreshAll(context)
            teamPlayers = d.players
            leagueStandings = d.standings
            previews = d.previews
            dataStatus = d.status
            profiles = BirthdaysRepository.fetch().profileMap()
            pullToRefreshState.endRefresh()
        }
    }

    // 選択中のチームの展望データ(次の試合1つ分)。team 項目が無い古いデータは試合IDからチームを探す
    val preview = previews?.values?.firstOrNull { p ->
        p.team.equals(selectedTeam.name, ignoreCase = true) ||
            (p.team.isBlank() && GamesRepository.games.firstOrNull { it.id == p.gameId }?.team == selectedTeam)
    }
    val nextGame = preview?.let { p -> GamesRepository.games.firstOrNull { it.id == p.gameId } }

    Scaffold(
        topBar = {
            MastheadTopBar(
                section = "選手の数字",
                edition = "${selectedTeam.displayName}版",
                // 戻り先があるときだけ左上に戻るボタンを出す(下のメニューのタブとして開いたときは出さない)
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
        floatingActionButton = { ScrollJumpButtons(scrollState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
        // チームの切り替えボタンと「自チーム/相手チーム」へ飛ぶリンクは一番上に固定し、常に表示する
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
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            JumpLink("▼ ${selectedTeam.shortLabel()}", Modifier.weight(1f)) { jumpTo(marks.own) }
            // 丸岡RUCK・ユナイテッドは、全選手の一覧へ飛ぶボタンも出す
            if (selectedTeam != Team.BLOWINDS) {
                JumpLink("▼ 全選手", Modifier.weight(0.8f)) { jumpTo(marks.all) }
            }
            JumpLink("▼ 相手:${nextGame?.opponent ?: "次の対戦相手"}", Modifier.weight(if (selectedTeam != Team.BLOWINDS) 1.2f else 1f)) { jumpTo(marks.opp) }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { marks.viewport = it }
                .verticalScroll(scrollState)
                // 下は、右下の「一番上へ」「一番下へ」ボタンに最後の行が隠れないよう広めに空ける
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = ScrollJumpBottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Headline("選手の数字", fontSize = 22)
                Text(
                    "公式サイトから自動で集めた数字だけを載せています。取得できていない項目は「$NO_DATA」と表示します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                DoubleRule(modifier = Modifier.padding(top = 4.dp))
            }

            if (previews == null) {
                Text("読み込み中…", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                return@Column
            }

            // 福井側の選手
            val roster = teamPlayers[selectedTeam.name]
            val myPlayers = preview?.myKeyPlayers ?: emptyList()
            if (roster != null && roster.players.isNotEmpty()) {
                // チーム全体の今季の数字(Bリーグ公式のクラブ成績)。次の対戦相手の分も取れていれば並べて比べる
                if (roster.teamStats.isNotEmpty()) {
                    val opp = teamPlayers[BLOWINDS_OPP_KEY]?.takeIf { r ->
                        r.teamStats.isNotEmpty() && (r.gameId.isBlank() || r.gameId == preview?.gameId)
                    }
                    SectionLabel("チームの数字(今季)", modifier = Modifier.onGloballyPositioned { marks.own = it })
                    TeamStatsSection(
                        mine = roster,
                        mineName = selectedTeam.shortLabel(),
                        opp = opp,
                        oppName = opp?.teamName?.ifBlank { null } ?: nextGame?.opponent ?: "相手"
                    )
                }
                SectionLabel(
                    "${selectedTeam.displayName}の選手",
                    modifier = if (roster.teamStats.isEmpty()) Modifier.onGloballyPositioned { marks.own = it } else Modifier
                )
                RosterSection(roster, sortKey, profileOf = { name -> profiles[profileKey(selectedTeam, name)] }) { sortKey = it }
            } else if (myPlayers.isNotEmpty()) {
                SectionLabel("${selectedTeam.displayName}の主な選手", modifier = Modifier.onGloballyPositioned { marks.own = it })
                AsOfLine(previewUpdatedAt(dataStatus), null)
                Text(
                    "Bリーグ公式の「クラブリーダー」(平均得点・リバウンド・アシストの各部門でチーム1位の選手)です。" +
                        "その部門で1位の数字だけが分かるため、ほかの部門は「$NO_DATA」になります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                myPlayers.forEach { PlayerCard(it) }
                if (!preview?.myPlayersNote.isNullOrBlank()) {
                    Text("※${preview?.myPlayersNote}(チームの試合数)", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                }
            } else {
                val st = leagueStandings[selectedTeam.name]
                if (selectedTeam != Team.BLOWINDS && st != null) {
                    // 丸岡RUCK・ユナイテッドは、選手の得点(取れたとき)とリーグの順位表を出す
                    val scorers = st.scorers
                    if (scorers != null) {
                        SectionLabel("${selectedTeam.displayName}の得点", modifier = Modifier.onGloballyPositioned { marks.own = it })
                        ScorersSection(
                            scorers = scorers,
                            rows = scorers.rows.filter { it.team.contains(teamWord(selectedTeam)) },
                            team = selectedTeam,
                            updatedAt = st.updatedAt,
                            emptyMessage = "まだ得点した選手はいません。",
                            profileOf = { name -> profiles[profileKey(selectedTeam, name)] }
                        )
                    }
                    SectionLabel(
                        "${st.league}の順位",
                        modifier = if (scorers == null) Modifier.onGloballyPositioned { marks.own = it } else Modifier
                    )
                    StandingsSection(
                        standings = st,
                        ownTeam = selectedTeam,
                        nextOpponent = nextGame?.opponent
                    )
                } else {
                    SectionLabel("${selectedTeam.displayName}の選手", modifier = Modifier.onGloballyPositioned { marks.own = it })
                    NoDataBox(
                        when (selectedTeam) {
                            Team.BLOWINDS -> "まだ選手データが届いていません。GitHubの自動更新(1時間おき)が動くと、Bリーグ公式の選手情報から全選手の成績が表示されます。"
                            Team.RAC, Team.UNITED -> "このチームの順位表はまだ届いていません。GitHubの自動更新(1時間おき)が動くと表示されます。"
                        }
                    )
                }
            }
            // 丸岡RUCK・ユナイテッドの全選手(公式サイトの選手紹介の全員。得点などの数字を重ねる)
            if (selectedTeam != Team.BLOWINDS) {
                val teamRoster = profiles.values.filter { it.team == selectedTeam }
                SectionLabel("${selectedTeam.displayName}の全選手", modifier = Modifier.onGloballyPositioned { marks.all = it })
                if (teamRoster.isEmpty()) {
                    NoDataBox("全選手のデータはまだ届いていません。GitHubの自動更新が動くと、公式サイトの選手紹介から表示されます。")
                } else {
                    AllPlayersSection(
                        team = selectedTeam,
                        roster = teamRoster,
                        scorerRows = leagueStandings[selectedTeam.name]?.scorers?.rows.orEmpty()
                            .filter { it.team.contains(teamWord(selectedTeam)) }
                    )
                }
            }
            if (selectedTeam == Team.BLOWINDS) {
                LinkText("Bリーグ公式の選手情報を見る ›") { openPlayersUrl(context, BLOWINDS_BLEAGUE_URL) }
            } else {
                selectedTeam.officialSiteUrl?.let { url ->
                    LinkText("公式サイトで選手を見る ›") { openPlayersUrl(context, url) }
                }
            }

            ThinRule(modifier = Modifier.padding(vertical = 4.dp))

            // 次の対戦相手。ブローウィンズは相手(Bリーグのクラブ)の全選手の成績があれば一覧で、無ければ注目選手(クラブリーダー)を出す
            val oppRoster = if (selectedTeam == Team.BLOWINDS) {
                teamPlayers[BLOWINDS_OPP_KEY]?.takeIf { r ->
                    r.players.isNotEmpty() && (r.gameId.isBlank() || r.gameId == preview?.gameId)
                }
            } else {
                null
            }
            // 丸岡RUCKは、相手チームの得点者(女子Fリーグ公式の得点ランキングから)を表で出す
            val oppScorers = if (selectedTeam == Team.RAC && nextGame != null) {
                leagueStandings[Team.RAC.name]?.scorers
            } else {
                null
            }
            SectionLabel(
                when {
                    oppRoster != null -> "次の対戦相手の選手"
                    oppScorers != null -> "次の対戦相手の得点"
                    else -> "次の対戦相手の注目選手"
                },
                modifier = Modifier.onGloballyPositioned { marks.opp = it }
            )
            if (nextGame != null) {
                Text(
                    "${nextGame.dateLabel.split("/").drop(1).joinToString("/")}(${nextGame.dayOfWeek}) vs ${nextGame.opponent}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            val oppPlayers = preview?.keyPlayers ?: emptyList()
            if (oppRoster != null) {
                RosterSection(oppRoster, oppSortKey) { oppSortKey = it }
            } else if (oppScorers != null && nextGame != null) {
                ScorersSection(
                    scorers = oppScorers,
                    rows = oppScorers.rows.filter { r ->
                        r.team.contains(nextGame.opponent) || nextGame.opponent.contains(r.team)
                    },
                    team = selectedTeam,
                    updatedAt = leagueStandings[Team.RAC.name]?.updatedAt,
                    emptyMessage = "${nextGame.opponent}の選手は得点ランキングに載っていません。"
                )
            } else if (oppPlayers.isNotEmpty()) {
                AsOfLine(previewUpdatedAt(dataStatus), null)
                oppPlayers.forEach { PlayerCard(it) }
                if (!preview?.playersNote.isNullOrBlank()) {
                    Text("※${preview?.playersNote}(チームの試合数)", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                }
            } else {
                NoDataBox(
                    if (preview == null) "次の試合の展望データがまだありません。"
                    else "相手の選手データは取得できていません。"
                )
            }
            preview?.oppLink?.let { link ->
                LinkText("相手チームの情報を見る ›") { openPlayersUrl(context, link) }
            }
            Text(
                "選手の数字はGitHubの自動更新(1時間おき)で" +
                    when (selectedTeam) {
                        Team.BLOWINDS -> "Bリーグ公式"
                        Team.RAC -> "女子Fリーグ公式"
                        Team.UNITED -> "ユナイテッド公式"
                    } +
                    "から取り直しています。下に引っ張ると最新のデータを読み込みます。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
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
 * 1チーム分の全選手一覧。何日時点の数字か・出典・並び順のボタン・選手ごとのカード。
 * ブローウィンズと、その次の対戦相手で共通に使う。
 */
@Composable
private fun RosterSection(
    roster: TeamPlayers,
    sortKey: PlayerSort,
    // 選手名 → 身長・出身地など(自チームのときだけ。相手チームは null を返す)
    profileOf: (String) -> PlayerBirthday? = { null },
    onSortChange: (PlayerSort) -> Unit
) {
    // 選んでいる並び順のボタンをもう一度押すと、逆の順番にする
    var reversed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 何日時点の数字か(Bリーグ公式から取り直した日時と、その時点の今季の試合数)
        val maxGames = roster.players.mapNotNull { it.games?.toIntOrNull() }.maxOrNull()
        AsOfLine(roster.updatedAt, maxGames?.let { "今季${it}試合" })
        Text(
            listOf(
                "出典:Bリーグ公式(クラブページの選手情報)",
                // シーズンは「2026-27」の形のときだけ出す(読み取りに失敗した文字が出ないように)
                roster.season.takeIf { SEASON_PATTERN.matches(it) }?.let { "${it}シーズン" } ?: ""
            ).filter { it.isNotBlank() }.joinToString("・"),
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        // 並び順のボタン。1段目は背番号・出場時間・得点・リバウンド・アシスト、2段目は3つの成功率と貢献度。
        // 各段とも、文字の長さに合わせて幅を配分し、画面幅いっぱいに収める
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "並び順(背番号以外は数字の大きい順。同じボタンをもう一度押すと逆の順)",
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft
            )
            // 「身長」「年齢」「誕生日」は、公式サイトの選手紹介のデータがある選手がいるとき(自チーム)だけ出す
            val hasProfiles = roster.players.any { profileOf(it.name) != null }
            PlayerSort.entries.filter { it !in PROFILE_SORTS || hasProfiles }
                .groupBy { it.row }.toSortedMap().values.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    line.forEach { key ->
                        SortChip(
                            if (sortKey == key && reversed) key.label + "(逆)" else key.label,
                            sortKey == key,
                            Modifier.weight(key.label.length + 1f)
                        ) {
                            if (sortKey == key) {
                                reversed = !reversed
                            } else {
                                reversed = false
                                onSortChange(key)
                            }
                        }
                    }
                }
            }
        }
        Text(
            "数字は今季の1試合あたりの平均です(出場時間は「分:秒」)。選手を押すと、シュート成功率などの詳しい数字が開きます。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        // 並べ替えても、写真・開閉の状態がその選手についていくよう、選手ごとに key を付ける
        val sorted = sortPlayers(roster.players, sortKey, profileOf)
        // 逆の順番のときも、数字が分からない選手は最後のまま
        val shown = if (reversed) {
            val (missing, known) = sorted.partition { sortValueMissing(it, sortKey, profileOf) }
            known.reversed() + missing
        } else sorted
        shown.forEach { p -> key(p.number, p.name) { RosterCard(p, sortKey, profileOf(p.name)) } }
    }
}

/**
 * 成功率を「50.0%」の形にそろえる。すでに % が付いていればそのまま。
 * % の無い数字は、1以下なら割合(0.5 → 50.0%)、それより大きければ百分率(50 → 50.0%)とみなす。
 */
private fun percentText(value: String?): String? {
    val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (v.endsWith("%")) return v
    val n = v.toDoubleOrNull() ?: return v
    val pct = if (n <= 1.0) n * 100 else n
    return "%.1f%%".format(pct)
}

/**
 * 成功率と「成功数/試投数」をまとめた表示。例:41.9%(18/43)。
 * 成功数・試投数が無いときは成功率だけ、どちらも無ければ「データなし」。
 */
private fun shotText(pct: String?, made: String?, attempted: String?): String {
    val p = percentText(pct)
    val counts = if (made != null && attempted != null) "$made/$attempted" else null
    return when {
        p != null && counts != null -> "$p($counts)"
        p != null -> p
        counts != null -> counts
        else -> NO_DATA
    }
}

/** シーズン表記(例 2026-27) */
private val SEASON_PATTERN = Regex("""\d{4}-\d{2}""")

/**
 * 「9月29日(火) 21:15 時点の数字」の1行。数字をいつ取り直したかを示す。
 * extra があれば「(今季2試合)」のように後ろに付ける。日時が分からなければ「取得日時不明」。
 */
@Composable
private fun AsOfLine(updatedAt: java.time.Instant?, extra: String?) {
    val time = updatedAt?.atZone(java.time.ZoneId.of("Asia/Tokyo"))
    val base = if (time != null) {
        val week = "月火水木金土日"[time.dayOfWeek.value - 1]
        "%d月%d日(%s) %02d:%02d 時点の数字".format(time.monthValue, time.dayOfMonth, week, time.hour, time.minute)
    } else {
        "取得日時不明の数字"
    }
    Text(
        if (extra.isNullOrBlank()) base else "$base($extra)",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = Ink
    )
}

/** 展望データ(相手の注目選手・主な選手)を作った時刻。自動更新で展望の作成に失敗していれば分からないので null。 */
private fun previewUpdatedAt(status: DataStatus?): java.time.Instant? =
    status?.takeIf { "展望" !in it.failedSteps }?.updatedAt

/**
 * 丸岡RUCK・ユナイテッドのリーグ順位表。自チームの行は太字・チームカラー、次の対戦相手の行には「次」を付ける。
 * 丸岡RUCKは試合数・勝敗・得失点差・直近5試合まで、ユナイテッドは順位と勝点だけ(公式サイトにある分だけ)。
 */
@Composable
private fun StandingsSection(standings: LeagueStandings, ownTeam: Team, nextOpponent: String?) {
    val ownWord = when (ownTeam) {
        Team.RAC -> "丸岡"
        Team.UNITED -> "福井ユナイテッド"
        Team.BLOWINDS -> "ブローウィンズ"
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AsOfLine(standings.updatedAt, null)
        Text(
            listOf(standings.note, "出典:${standings.sourceUrl}").filter { it.isNotBlank() }.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        // ファイナルシーズンに試合があればそちらを先に出す
        listOfNotNull(
            standings.final?.takeIf { p -> p.rows.any { (it.played ?: 0) > 0 } },
            standings.regular,
            standings.final?.takeIf { p -> p.rows.none { (it.played ?: 0) > 0 } }
        ).forEach { part ->
            Text(part.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Ink)
            StandingsTable(part.rows, ownWord, ownTeam.color, nextOpponent)
        }
    }
}

/** 順位表・得点の表で、そのチームの行を見分けるための文字 */
private fun teamWord(team: Team): String = when (team) {
    Team.RAC -> "丸岡"
    Team.UNITED -> "福井ユナイテッド"
    Team.BLOWINDS -> "ブローウィンズ"
}

/**
 * 選手の得点(丸岡RUCK・ユナイテッド)。何日時点か・出典・表。
 * 丸岡RUCKは女子Fリーグ公式の得点ランキング(1点以上の選手だけ)、ユナイテッドは公式サイトの試合結果から集計した全選手。
 */
@Composable
private fun ScorersSection(
    scorers: LeagueScorers,
    rows: List<ScorerRow>,
    team: Team,
    updatedAt: java.time.Instant?,
    emptyMessage: String,
    // 選手名 → 身長・出身地など(自チームのときだけ)
    profileOf: (String) -> PlayerBirthday? = { null }
) {
    val isUnited = team == Team.UNITED
    // ユナイテッドは得点のない選手も含むので、最初は得点した選手だけを出し、ボタンで全員を出す
    var showAll by remember(scorers, team) { mutableStateOf(false) }
    val shown = if (isUnited && !showAll) rows.filter { it.goals > 0 } else rows
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AsOfLine(updatedAt, null)
        Text(
            listOf(scorers.note, "出典:${scorers.sourceUrl}").filter { it.isNotBlank() }.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        if (shown.isEmpty()) {
            NoDataBox(emptyMessage)
        } else {
            ScorersTable(shown, isUnited, team.color, profileOf)
        }
        if (isUnited && rows.any { it.goals == 0 }) {
            Text(
                if (showAll) "▲ 得点した選手だけにする" else "▼ 全選手(${rows.size}人)を表示",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAll = !showAll }
                    .padding(vertical = 4.dp)
            )
        }
        Text(
            if (isUnited) {
                "先発=先発メンバーに入った試合数、ベンチ=控えでメンバー入りした試合数です(途中から出たかどうかは公式に載っていないため数えていません)。"
            } else {
                "順位はリーグ全体の得点ランキングの順位です。出場・アシストなど、ほかの数字は公式に載っていないため表示していません。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
    }
}

@Composable
private fun ScorersTable(
    rows: List<ScorerRow>,
    isUnited: Boolean,
    ownColor: androidx.compose.ui.graphics.Color,
    profileOf: (String) -> PlayerBirthday? = { null }
) {
    val headers = if (isUnited) listOf("番号", "選手", "得点", "先発", "ベンチ") else listOf("順位", "選手", "得点", "シュート", "出場")
    val weights = listOf(1f, 3.4f, 1f, 1.2f, 1f)
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Ink).background(Paper)) {
        Row(modifier = Modifier.fillMaxWidth().background(Ink).padding(vertical = 5.dp, horizontal = 6.dp)) {
            headers.forEachIndexed { i, h ->
                Text(h, modifier = Modifier.weight(weights[i]), color = Paper, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    textAlign = if (i == 1) TextAlign.Start else TextAlign.Center, maxLines = 1)
            }
        }
        rows.forEach { r ->
            val cells = if (isUnited) {
                listOf(
                    r.number.ifBlank { "-" },
                    r.name + if (r.position.isNotBlank()) " ${r.position}" else "",
                    "${r.goals}",
                    "${r.starts ?: "-"}",
                    "${r.bench ?: "-"}"
                )
            } else {
                listOf("${r.rank}位", r.name, "${r.goals}", "${r.shots ?: "-"}", r.games?.let { "${it}試合" } ?: "-")
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cells.forEachIndexed { i, c ->
                    if (i == 1) {
                        // 選手名の欄。自チームの選手は顔写真を名前の左に出す(公式サイトの選手紹介から)
                        Row(
                            modifier = Modifier.weight(weights[i]),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RemoteThumbnail(
                                r.photo, width = 40.dp, height = 50.dp, alignTop = true,
                                // タップで大きく表示
                                zoomCaption = (if (r.number.isNotBlank()) "#${r.number} " else "") + r.name
                            )
                            // 選手名は途中で切らず、入りきらないときは折り返す。下に身長・出身地など(分かる選手だけ)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    c,
                                    fontSize = 12.sp,
                                    color = Ink,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                profileOf(r.name)?.profileLine?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, fontSize = 10.sp, color = InkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    } else {
                        Text(
                            c,
                            modifier = Modifier.weight(weights[i]),
                            fontSize = 12.sp,
                            fontWeight = if (i == 2 && r.goals > 0) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (i == 2 && r.goals > 0) ownColor else Ink,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            ThinRule(color = LineGray)
        }
    }
}

@Composable
private fun StandingsTable(rows: List<StandingRow>, ownWord: String, ownColor: androidx.compose.ui.graphics.Color, nextOpponent: String?) {
    val detailed = rows.any { it.played != null }
    val headers = if (detailed) listOf("順", "チーム", "勝点", "試合", "勝", "分", "敗", "差") else listOf("順位", "チーム", "勝点")
    val weights = if (detailed) listOf(0.7f, 3.4f, 1f, 1f, 0.8f, 0.8f, 0.8f, 1f) else listOf(1f, 4f, 1.2f)
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Ink).background(Paper)) {
        Row(modifier = Modifier.fillMaxWidth().background(Ink).padding(vertical = 5.dp, horizontal = 6.dp)) {
            headers.forEachIndexed { i, h ->
                Text(h, modifier = Modifier.weight(weights[i]), color = Paper, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    textAlign = if (i == 1) TextAlign.Start else TextAlign.Center, maxLines = 1)
            }
        }
        rows.forEach { r ->
            val own = r.team.contains(ownWord)
            val next = !own && nextOpponent != null && (r.team.contains(nextOpponent) || nextOpponent.contains(r.team))
            val cells = if (detailed) {
                listOf("${r.rank}", r.team + if (next) " ◀次" else "", "${r.points}", "${r.played ?: "-"}", "${r.win ?: "-"}",
                    "${r.draw ?: "-"}", "${r.lose ?: "-"}", r.goalDiff?.let { if (it > 0) "+$it" else "$it" } ?: "-")
            } else {
                listOf("${r.rank}", r.team + if (next) " ◀次" else "", "${r.points}")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (own) ownColor.copy(alpha = 0.08f) else Paper)
                    .padding(vertical = 6.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cells.forEachIndexed { i, c ->
                    Text(
                        c,
                        modifier = Modifier.weight(weights[i]),
                        fontSize = 12.sp,
                        fontWeight = if (own) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (own && i == 1) ownColor else Ink,
                        textAlign = if (i == 1) TextAlign.Start else TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ThinRule(color = LineGray)
        }
    }
}

/** 「自チーム」「相手」へ飛ぶための位置の入れ物(スクロールする部分と、それぞれの見出し)。 */
private class JumpMarks {
    var viewport: LayoutCoordinates? = null
    var own: LayoutCoordinates? = null
    var opp: LayoutCoordinates? = null
    // 丸岡RUCK・ユナイテッドの「全選手」
    var all: LayoutCoordinates? = null
}

/** 上に固定する「自チーム」「相手」へ飛ぶリンク。 */
/** 選手カードのプロフィールの行のうち、並び順に合わせて色を付ける行 */
private enum class ProfileMark { NONE, BIRTHDAY, AGE, HEIGHT }

/**
 * 選手カードの写真の下に出す、生年月日・年齢の行と、身長・体重・出身地などの行、出身校の行(カードの幅いっぱい・1行ずつ)。
 * mark:身長・年齢・誕生日で並べているときは、その行の背景に色を付けて太字にする(成功率・貢献度で並べたときと同じ見た目)。
 * 誕生日順のときは、次の誕生日まで何日かも出す
 */
@Composable
private fun ProfileLines(p: PlayerBirthday, mark: ProfileMark) {
    val today = BirthdaysRepository.today()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val days = p.daysUntilNext(today)
        if (mark == ProfileMark.BIRTHDAY) {
            // 誕生日順のときは、色の付いた枠の中で 生年月日 と「次の誕生日まであと○日」を2行に分けて出す
            // (1行に詰めると、文字を大きくしている画面で途中が切れるため)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Accent.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    birthdayText(p, today),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (days == 0) "今日が誕生日!" else "次の誕生日まであと${days}日",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (days == 0) NewsRed else Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            ProfileLine(birthdayText(p, today), marked = mark == ProfileMark.AGE)
        }
        p.profileLine.takeIf { it.isNotBlank() }?.let {
            ProfileLine(it, marked = mark == ProfileMark.HEIGHT)
        }
        // 出身校は長い名前(例「京北高等学校(現・東洋大学京北高等学校)→筑波大学」)もあるので、切らずに折り返す
        p.schoolLine.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = InkSoft, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** プロフィールの1行。marked なら背景に色を付けて太字にする */
@Composable
private fun ProfileLine(text: String, marked: Boolean, color: androidx.compose.ui.graphics.Color? = null) {
    Text(
        text,
        style = if (marked) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
        fontWeight = if (marked) FontWeight.ExtraBold else FontWeight.Normal,
        color = color ?: if (marked) Ink else InkSoft,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (marked) Modifier.background(Accent.copy(alpha = 0.08f)).padding(horizontal = 8.dp, vertical = 4.dp)
                else Modifier
            )
    )
}

/** 「2000年3月14日生まれ(26歳)」 */
private fun birthdayText(p: PlayerBirthday, today: java.time.LocalDate): String =
    "${p.birthday.year}年${p.birthday.monthValue}月${p.birthday.dayOfMonth}日生まれ(${p.currentAge(today)}歳)"

/**
 * チーム全体の今季の数字(Bリーグ公式の「クラブ成績」)。項目ごとに 自チーム・相手チーム の数字とリーグ内の順位を並べ、
 * 順位が上のほうを太字・チームカラーにする。相手のデータが無いときは自チームだけ。
 */
@Composable
private fun TeamStatsSection(mine: TeamPlayers, mineName: String, opp: TeamPlayers?, oppName: String) {
    val oppByKey = opp?.teamStats?.associateBy { it.key }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(modifier = Modifier.fillMaxWidth().border(1.dp, Ink).background(Paper)) {
            Row(modifier = Modifier.fillMaxWidth().background(Ink).padding(vertical = 6.dp, horizontal = 8.dp)) {
                Text("項目", modifier = Modifier.weight(1.6f), color = Paper, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(mineName, modifier = Modifier.weight(1.2f), color = Paper, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (opp != null) {
                    Text(oppName, modifier = Modifier.weight(1.2f), color = Paper, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            mine.teamStats.forEach { st ->
                val o = oppByKey[st.key]
                // 順位が上(数字が小さい)ほうを目立たせる
                val oRank = o?.rank
                val sRank = st.rank
                val mineBetter = oRank != null && sRank != null && sRank < oRank
                val oppBetter = oRank != null && sRank != null && oRank < sRank
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.6f)) {
                        // 項目名は短い呼び方にし、それでも入らないときは折り返す(途中で切らない)
                        Text(teamStatLabel(st), fontSize = 13.sp, color = Ink)
                        Text(st.key, fontSize = 10.sp, color = InkSoft)
                    }
                    TeamStatCell(st, mineBetter, mine.leagueTeams, Modifier.weight(1.2f))
                    if (opp != null) {
                        if (o != null) TeamStatCell(o, oppBetter, opp.leagueTeams, Modifier.weight(1.2f))
                        else Text("-", modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center, color = InkSoft)
                    }
                }
                ThinRule(color = LineGray)
            }
        }
        // 何チーム中の順位か(例「B.ONE(全25チーム)の中の順位」)。相手が別のリーグならそれも書く
        val leagueNote = listOfNotNull(mine, opp).map { t ->
            when {
                t.league.isNotBlank() && t.leagueTeams > 0 -> "${t.league}(全${t.leagueTeams}チーム)"
                t.league.isNotBlank() -> t.league
                else -> ""
            }
        }.filter { it.isNotBlank() }.distinct()
        Text(
            listOf(
                "出典:Bリーグ公式(クラブ成績)",
                mine.teamStatsUpdated.takeIf { it.isNotBlank() }?.let { "${it}更新" } ?: "",
                if (leagueNote.isNotEmpty()) "「○位」は${leagueNote.joinToString("・")}の中の順位です" else "「○位」はリーグ内の順位です"
            ).filter { it.isNotBlank() }.joinToString("・"),
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
    }
}

/** クラブ成績の項目の短い呼び方(表の幅に収まるように) */
private fun teamStatLabel(st: TeamStat): String = when (st.key) {
    "PPG" -> "平均得点"
    "FG%" -> "FG成功率"
    "3FG%" -> "3P成功率"
    "FT%" -> "FT成功率"
    "RPG" -> "平均リバウンド"
    "APG" -> "平均アシスト"
    "BPG" -> "平均ブロック"
    "SPG" -> "平均スティール"
    else -> st.label
}

@Composable
private fun TeamStatCell(st: TeamStat, better: Boolean, teams: Int, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            st.value + st.unit,
            fontSize = 15.sp,
            fontWeight = if (better) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (better) NewsRed else Ink,
            maxLines = 1
        )
        // 何チーム中の何位か(チーム数が分からなければ順位だけ)
        st.rank?.let { Text(if (teams > 0) "${it}位/${teams}" else "${it}位", fontSize = 10.sp, color = InkSoft) }
    }
}

/** 丸岡RUCK・ユナイテッドの全選手の並び順 */
private enum class RosterSort(val label: String) {
    NUMBER("背番号"),
    GOALS("得点"),
    HEIGHT("身長"),
    AGE("年齢"),
    BIRTHDAY("誕生日")
}

/**
 * 丸岡RUCK・ユナイテッドの全選手。公式サイトの選手紹介(写真・背番号・ポジション・身長など)の全員に、
 * 得点の表の数字(丸岡RUCK:得点・シュート・出場、ユナイテッド:得点・先発・ベンチ)を重ねて出す。
 * 得点の表に載っていない選手は、得点0として出す(シュート数などは分からないので「-」)。
 */
@Composable
private fun AllPlayersSection(team: Team, roster: List<PlayerBirthday>, scorerRows: List<ScorerRow>) {
    var sort by remember(team) { mutableStateOf(RosterSort.NUMBER) }
    // 選んでいる並び順のボタンをもう一度押すと、逆の順番にする
    var reversed by remember(team) { mutableStateOf(false) }
    val today = BirthdaysRepository.today()
    val rowsByName = remember(scorerRows) { scorerRows.associateBy { com.fukuiteams.app.data.playerNameKey(it.name) } }
    fun rowOf(p: PlayerBirthday) = rowsByName[com.fukuiteams.app.data.playerNameKey(p.name)]
    val base = when (sort) {
        RosterSort.NUMBER -> roster.sortedBy { (it.number.ifBlank { rowOf(it)?.number.orEmpty() }).toIntOrNull() ?: 999 }
        RosterSort.GOALS -> roster.sortedWith(
            compareByDescending<PlayerBirthday> { rowOf(it)?.goals ?: 0 }.thenBy { it.number.toIntOrNull() ?: 999 }
        )
        RosterSort.HEIGHT -> roster.sortedWith(
            compareBy<PlayerBirthday> { it.heightCm == null }.thenByDescending { it.heightCm ?: 0.0 }
        )
        RosterSort.AGE -> roster.sortedBy { it.birthday }
        // 次の誕生日が近い順
        RosterSort.BIRTHDAY -> roster.sortedBy { it.daysUntilNext(today) }
    }
    // 逆の順番のときも、身長が分からない選手は最後のまま
    val sorted = if (reversed) {
        val (missing, known) = base.partition { sort == RosterSort.HEIGHT && it.heightCm == null }
        known.reversed() + missing
    } else base
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "公式サイトの選手紹介の${roster.size}人です。同じ並び順のボタンをもう一度押すと逆の順になります。" +
                (if (team == Team.RAC) "得点・シュート・出場は女子Fリーグ公式の得点ランキングから(得点していない選手は得点0、ほかは「-」)。"
                else "得点・先発・ベンチは公式サイトの試合結果から数えた数字です(試合のメンバーに入っていない選手は0)。"),
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RosterSort.entries.forEach { key ->
                SortChip(
                    if (sort == key && reversed) key.label + "(逆)" else key.label,
                    sort == key,
                    Modifier.weight(key.label.length + 1f)
                ) {
                    if (sort == key) {
                        reversed = !reversed
                    } else {
                        reversed = false
                        sort = key
                    }
                }
            }
        }
        sorted.forEach { p ->
            key(p.team, p.name) { AllPlayerCard(team, p, rowOf(p), sort) }
        }
    }
}

@Composable
private fun AllPlayerCard(team: Team, p: PlayerBirthday, row: ScorerRow?, sort: RosterSort) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteThumbnail(p.photo, width = 72.dp, height = 90.dp, alignTop = true, zoomCaption = p.label)
            // 公式の選手一覧で背番号が読めなかった選手は、得点の表の背番号を使う
            val number = p.number.ifBlank { row?.number.orEmpty() }
            Headline(if (number.isNotBlank()) "#$number" else "#-", fontSize = 22, modifier = Modifier.width(58.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                (p.position.ifBlank { row?.position.orEmpty() }).takeIf { it.isNotBlank() }?.let {
                    Text("ポジション:$it", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
            }
        }
        // 生年月日・身長など。折り返さないよう、写真の下にカードの幅いっぱいで出す
        ProfileLines(
            p,
            when (sort) {
                RosterSort.BIRTHDAY -> ProfileMark.BIRTHDAY
                RosterSort.AGE -> ProfileMark.AGE
                RosterSort.HEIGHT -> ProfileMark.HEIGHT
                else -> ProfileMark.NONE
            }
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCell("得点", "${row?.goals ?: 0}", Modifier.weight(1f))
            if (team == Team.RAC) {
                StatCell("シュート", row?.shots?.toString() ?: "-", Modifier.weight(1f))
                StatCell("出場", row?.games?.let { "${it}試合" } ?: "-", Modifier.weight(1f))
            } else {
                StatCell("先発", "${row?.starts ?: 0}", Modifier.weight(1f))
                StatCell("ベンチ", "${row?.bench ?: 0}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun JumpLink(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        modifier = modifier
            .border(1.dp, Ink)
            .background(Paper)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        color = Ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * 全選手一覧の並び順(ボタンはこの順に左から並ぶ)。row はボタンを置く段(0:1段目、1:2段目)。
 * 1行に収めるため、ボタンの文字は「順」を省く。
 */
private enum class PlayerSort(val label: String, val row: Int = 0) {
    NUMBER("背番号"),
    MINUTES("出場時間"),
    POINTS("得点"),
    REBOUNDS("リバウンド"),
    ASSISTS("アシスト"),
    FIELD_GOAL_PCT("FG成功率", 1),
    THREE_PCT("3P成功率", 1),
    FREE_THROW_PCT("FT成功率", 1),
    EFFICIENCY("貢献度", 1),
    // 3段目:身長(高い順)・年齢(年上から)・誕生日(次の誕生日が近い順)。
    // 公式サイトの選手紹介のデータがある自チームだけボタンを出す
    HEIGHT("身長", 2),
    AGE("年齢", 2),
    BIRTHDAY("誕生日", 2)
}

/** その並び順の数字が分からない選手か(逆の順番にしても最後に置くため) */
private fun sortValueMissing(p: PlayerStats, key: PlayerSort, profileOf: (String) -> PlayerBirthday?): Boolean = when (key) {
    PlayerSort.NUMBER -> false
    PlayerSort.MINUTES -> minutesToSeconds(p.minutesPerGame) == null
    PlayerSort.POINTS -> p.points?.toDoubleOrNull() == null
    PlayerSort.REBOUNDS -> p.rebounds?.toDoubleOrNull() == null
    PlayerSort.ASSISTS -> p.assists?.toDoubleOrNull() == null
    PlayerSort.FIELD_GOAL_PCT -> percentValue(p.fieldGoalPct) == null
    PlayerSort.THREE_PCT -> percentValue(p.threePct) == null
    PlayerSort.FREE_THROW_PCT -> percentValue(p.freeThrowPct) == null
    PlayerSort.EFFICIENCY -> p.efficiency?.toDoubleOrNull() == null
    PlayerSort.HEIGHT -> profileOf(p.name)?.heightCm == null
    PlayerSort.AGE, PlayerSort.BIRTHDAY -> profileOf(p.name) == null
}

/** 公式サイトの選手紹介のデータを使う並び順 */
private val PROFILE_SORTS = setOf(PlayerSort.HEIGHT, PlayerSort.AGE, PlayerSort.BIRTHDAY)

/**
 * 選んだ並び順で選手を並べる。背番号順以外は数字の大きい順。
 * 数字が無い選手(今季の出場なしなど)は最後に回す。
 * 成功率が同じときは試投数の多い順、それも同じなら背番号順のまま。
 */
private fun sortPlayers(
    players: List<PlayerStats>,
    key: PlayerSort,
    profileOf: (String) -> PlayerBirthday? = { null }
): List<PlayerStats> {
    val value: (PlayerStats) -> Double? = when (key) {
        PlayerSort.NUMBER -> return players
        // 次の誕生日が近い順(誕生日が分からない選手は最後)
        PlayerSort.BIRTHDAY -> {
            val today = BirthdaysRepository.today()
            return players.sortedBy { profileOf(it.name)?.daysUntilNext(today) ?: 9999 }
        }
        // 年上から(生年月日が分からない選手は最後)
        PlayerSort.AGE -> return players.sortedWith(
            compareBy<PlayerStats> { profileOf(it.name) == null }.thenBy { profileOf(it.name)?.birthday }
        )
        // 身長の高い順(身長が分からない選手は最後)
        PlayerSort.HEIGHT -> { p -> profileOf(p.name)?.heightCm }
        PlayerSort.POINTS -> { p -> p.points?.toDoubleOrNull() }
        PlayerSort.MINUTES -> { p -> minutesToSeconds(p.minutesPerGame) }
        PlayerSort.REBOUNDS -> { p -> p.rebounds?.toDoubleOrNull() }
        PlayerSort.ASSISTS -> { p -> p.assists?.toDoubleOrNull() }
        PlayerSort.FIELD_GOAL_PCT -> { p -> percentValue(p.fieldGoalPct) }
        PlayerSort.THREE_PCT -> { p -> percentValue(p.threePct) }
        PlayerSort.FREE_THROW_PCT -> { p -> percentValue(p.freeThrowPct) }
        PlayerSort.EFFICIENCY -> { p -> p.efficiency?.toDoubleOrNull() }
    }
    val attempts: (PlayerStats) -> Double = when (key) {
        PlayerSort.FIELD_GOAL_PCT -> { p -> p.fieldGoalsAttempted?.toDoubleOrNull() ?: 0.0 }
        PlayerSort.THREE_PCT -> { p -> p.threesAttempted?.toDoubleOrNull() ?: 0.0 }
        PlayerSort.FREE_THROW_PCT -> { p -> p.freeThrowsAttempted?.toDoubleOrNull() ?: 0.0 }
        else -> { _ -> 0.0 }
    }
    return players.sortedWith(
        compareByDescending<PlayerStats> { value(it) ?: -1e9 }.thenByDescending { attempts(it) }
    )
}

/** 成功率「41.9%」を 41.9 の数にする(並べ替え用)。読めなければ null。 */
private fun percentValue(text: String?): Double? =
    text?.trim()?.removeSuffix("%")?.trim()?.toDoubleOrNull()

/** 出場時間「分:秒」(例 32:24)を秒に直す。読めなければ null。 */
private fun minutesToSeconds(text: String?): Double? {
    val parts = text?.trim()?.split(":") ?: return null
    return when (parts.size) {
        2 -> {
            val m = parts[0].toIntOrNull() ?: return null
            val s = parts[1].toIntOrNull() ?: return null
            (m * 60 + s).toDouble()
        }
        1 -> parts[0].toDoubleOrNull()?.times(60)
        else -> null
    }
}

/** 並び順の切り替えボタン。幅は呼び出し側(modifier)で決め、文字は中央に1行で表示する。 */
@Composable
private fun SortChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        modifier = modifier
            .border(1.dp, Ink)
            .background(if (selected) Ink else Paper)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        color = if (selected) Paper else Ink,
        fontSize = 12.sp,
        letterSpacing = 0.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal
    )
}

/**
 * 全選手一覧の1人分。背番号・名前・ポジションと、試合数・平均出場時間・平均得点・リバウンド・アシスト。
 * 押すと、シュート成功率・スティール・ブロック・貢献度が開く。
 * 今季まだ成績が1つも無い選手は、数字の段の代わりに「今季の出場なし」の1行にし、押しても開かない。
 */
@Composable
private fun RosterCard(player: PlayerStats, sortKey: PlayerSort = PlayerSort.NUMBER, profile: PlayerBirthday? = null) {
    var expanded by remember(player.number, player.name) { mutableStateOf(false) }
    val hasStats = listOf(
        player.games, player.minutesPerGame, player.points, player.rebounds, player.assists,
        player.fieldGoalPct, player.threePct, player.freeThrowPct, player.steals, player.blocks, player.efficiency
    ).any { it != null }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .then(if (hasStats) Modifier.clickable { expanded = !expanded } else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 顔写真(ブローウィンズは公式サイトの選手紹介、相手チームはBリーグ公式の画像。読み込めないときは出さない)
            RemoteThumbnail(
                player.photo, width = 72.dp, height = 90.dp, alignTop = true,
                // タップで大きく表示(カードの開閉はせず、写真だけ大きくする)
                zoomCaption = (if (player.number.isNotBlank()) "#${player.number} " else "") + player.name +
                    (if (player.position.isNotBlank()) "(${player.position})" else "")
            )
            Headline(
                if (player.number.isNotBlank()) "#${player.number}" else "#-",
                fontSize = 22,
                modifier = Modifier.width(58.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                // 長い名前(例:チョンディー・ブラウン ジュニア)も途中で切らず、折り返して全部表示する
                Text(
                    player.name.ifBlank { NO_DATA },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "ポジション:${player.position.ifBlank { NO_DATA }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )

            }
            if (hasStats) {
                Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelMedium, color = InkSoft)
            }
        }
        // 生年月日・年齢と、身長・体重・出身地・出身校(公式サイトの選手ページから。分かる選手だけ)。
        // 折り返さないよう、写真の下にカードの幅いっぱいで出す
        profile?.let {
            ProfileLines(
                it,
                when (sortKey) {
                    PlayerSort.BIRTHDAY -> ProfileMark.BIRTHDAY
                    PlayerSort.AGE -> ProfileMark.AGE
                    PlayerSort.HEIGHT -> ProfileMark.HEIGHT
                    else -> ProfileMark.NONE
                }
            )
        }
        if (!hasStats) {
            Text(
                "今季の出場なし",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
                textAlign = TextAlign.Center
            )
            return@Column
        }
        // 成功率・貢献度で並べているときは、その数字をカードを開かなくても見えるように1行出す
        sortHighlight(player, sortKey)?.let { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Accent.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = Ink)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCell("試合", player.games, Modifier.weight(1f))
            StatCell("出場時間", player.minutesPerGame, Modifier.weight(1.2f))
            StatCell("得点", player.points, Modifier.weight(1f))
            StatCell("リバウンド", player.rebounds, Modifier.weight(1.3f))
            StatCell("アシスト", player.assists, Modifier.weight(1f))
        }
        if (expanded) {
            Column {
                // 成功率の横に「成功数/試投数」(今季の合計)を添える。例:41.9%(18/43)
                StatRow("フィールドゴール成功率", shotText(player.fieldGoalPct, player.fieldGoalsMade, player.fieldGoalsAttempted))
                StatRow("3ポイント成功率", shotText(player.threePct, player.threesMade, player.threesAttempted))
                StatRow("フリースロー成功率", shotText(player.freeThrowPct, player.freeThrowsMade, player.freeThrowsAttempted))
                StatRow("スティール(平均)", player.steals ?: NO_DATA)
                StatRow("ブロック(平均)", player.blocks ?: NO_DATA)
                StatRow("貢献度(平均)", player.efficiency ?: NO_DATA)
                if (player.gameLog.isNotEmpty()) {
                    GameLogSection(player.gameLog)
                }
            }
        }
    }
}

/** 成功率・貢献度で並べているときに、カードに目立たせて出す (項目名, 数字)。それ以外の並びでは null。 */
private fun sortHighlight(player: PlayerStats, sortKey: PlayerSort): Pair<String, String>? = when (sortKey) {
    PlayerSort.FIELD_GOAL_PCT ->
        "フィールドゴール成功率" to shotText(player.fieldGoalPct, player.fieldGoalsMade, player.fieldGoalsAttempted)
    PlayerSort.THREE_PCT ->
        "3ポイント成功率" to shotText(player.threePct, player.threesMade, player.threesAttempted)
    PlayerSort.FREE_THROW_PCT ->
        "フリースロー成功率" to shotText(player.freeThrowPct, player.freeThrowsMade, player.freeThrowsAttempted)
    PlayerSort.EFFICIENCY -> "貢献度(平均)" to (player.efficiency ?: NO_DATA)
    else -> null
}

/** 試合ごとの成績のうち、最初に表示する試合数(残りは「すべて見る」で開く)。 */
private const val GAME_LOG_INITIAL = 5

/**
 * 選手の今季の試合ごとの成績(新しい順)。最初は直近5試合、「すべての試合を見る」で全試合。
 * 1試合を2行で:「10/4(土) vs 金沢 HOME 勝 先発」と「26:17・5点・FG 2/8・3P 0/4・FT 1/2・R4・A6・貢献9」。
 */
@Composable
private fun GameLogSection(log: List<PlayerGameLog>) {
    var showAll by remember(log) { mutableStateOf(false) }
    val shown = if (showAll) log else log.take(GAME_LOG_INITIAL)
    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "試合ごとの成績(今季・新しい順)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        ThinRule(color = LineGray)
        shown.forEach { g ->
            GameLogRow(g)
            ThinRule(color = LineGray)
        }
        if (log.size > GAME_LOG_INITIAL) {
            Text(
                if (showAll) "直近${GAME_LOG_INITIAL}試合だけにする ▲" else "すべての試合を見る(${log.size}試合) ▼",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAll = !showAll }
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Accent,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GameLogRow(g: PlayerGameLog) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        val head = listOfNotNull(
            gameLogDate(g.date),
            if (g.opponent.isNotBlank()) "vs ${g.opponent}" else null,
            when (g.home) { true -> "HOME"; false -> "AWAY"; else -> null },
            when (g.win) { true -> "勝"; false -> "負"; else -> null },
            if (g.starter) "先発" else null
        ).joinToString(" ")
        Text(head, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Ink)
        fun shot(label: String, made: String?, att: String?) =
            if (made != null && att != null) "$label $made/$att" else null
        val line = listOfNotNull(
            g.minutes,
            g.points?.let { "${it}点" },
            shot("FG", g.fieldGoalsMade, g.fieldGoalsAttempted),
            shot("3P", g.threesMade, g.threesAttempted),
            shot("FT", g.freeThrowsMade, g.freeThrowsAttempted),
            g.rebounds?.let { "リバ$it" },
            g.assists?.let { "アシ$it" },
            g.steals?.takeIf { it != "0" }?.let { "スティール$it" },
            g.blocks?.takeIf { it != "0" }?.let { "ブロック$it" },
            g.efficiency?.let { "貢献$it" }
        ).joinToString("・")
        Text(line.ifBlank { NO_DATA }, style = MaterialTheme.typography.bodySmall, color = InkSoft)
    }
}

/** 「2026-10-04」→「10/4(土)」。読めなければそのまま。 */
private fun gameLogDate(date: String): String = try {
    val d = java.time.LocalDate.parse(date)
    val week = "月火水木金土日"[d.dayOfWeek.value - 1]
    "${d.monthValue}/${d.dayOfMonth}($week)"
} catch (e: Exception) {
    date
}

/** 数字1つ分(上に項目名、下に数字)。数字が無ければ「データなし」。 */
@Composable
private fun StatCell(label: String, value: String?, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkSoft, maxLines = 1)
        Text(
            value ?: NO_DATA,
            style = if (value == null) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium,
            fontWeight = if (value == null) FontWeight.Normal else FontWeight.ExtraBold,
            color = if (value == null) InkSoft else Ink,
            maxLines = 1
        )
    }
}

/** 選手1人分。背番号・選手名を大きく、下に各数字を表で。 */
@Composable
private fun PlayerCard(player: KeyPlayer) {
    val numbers = parseStat(player.stat)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Headline(
                if (player.number.isNotBlank()) "#${player.number}" else "#-",
                fontSize = 24,
                modifier = Modifier.width(64.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                // 長い名前(例:チョンディー・ブラウン ジュニア)も途中で切らず、折り返して全部表示する
                Text(
                    player.name.ifBlank { NO_DATA },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "ポジション:${player.position.ifBlank { NO_DATA }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            }
        }
        Column {
            StatRow("出場試合数", NO_DATA)
            StatRow("得点(合計)", NO_DATA)
            StatRow("平均得点", numbers.points?.let { "$it 点" } ?: NO_DATA)
            StatRow("平均リバウンド", numbers.rebounds ?: NO_DATA)
            StatRow("平均アシスト", numbers.assists ?: NO_DATA)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = InkSoft)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (value == NO_DATA) FontWeight.Normal else FontWeight.ExtraBold,
                color = if (value == NO_DATA) InkSoft else Ink
            )
        }
        ThinRule(color = LineGray)
    }
}

@Composable
private fun NoDataBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LineGray)
            .background(Paper)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(NO_DATA, style = MaterialTheme.typography.titleMedium, color = InkSoft)
            Text(message, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Accent,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    )
}

private fun openPlayersUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        // 開けないときは何もしない(アプリは落とさない)
    }
}
