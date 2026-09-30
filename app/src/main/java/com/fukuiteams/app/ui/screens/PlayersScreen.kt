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
import com.fukuiteams.app.data.DataStatusRepository
import com.fukuiteams.app.data.GamePreview
import com.fukuiteams.app.data.GamePreviewRepository
import com.fukuiteams.app.data.GamesRepository
import com.fukuiteams.app.data.KeyPlayer
import com.fukuiteams.app.data.BLOWINDS_OPP_KEY
import com.fukuiteams.app.data.PlayerStats
import com.fukuiteams.app.data.PlayersRepository
import com.fukuiteams.app.data.TeamPlayers
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.DoubleRule
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.MastheadTopBar
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
    var selectedTeam by remember { mutableStateOf(Team.BLOWINDS) }
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

    suspend fun load() {
        teamPlayers = PlayersRepository.fetch()
        previews = GamePreviewRepository.fetch()
        dataStatus = DataStatusRepository.fetch()
    }

    LaunchedEffect(Unit) { load() }

    // 画面を下に引っ張る・右上の更新ボタンで、選手データを読み込み直す
    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            load()
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
            onSelect = { t -> if (t != null) selectedTeam = t },
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
            JumpLink("▼ 相手:${nextGame?.opponent ?: "次の対戦相手"}", Modifier.weight(1f)) { jumpTo(marks.opp) }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { marks.viewport = it }
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
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
                SectionLabel("${selectedTeam.displayName}の選手", modifier = Modifier.onGloballyPositioned { marks.own = it })
                RosterSection(roster, sortKey) { sortKey = it }
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
                SectionLabel("${selectedTeam.displayName}の選手", modifier = Modifier.onGloballyPositioned { marks.own = it })
                NoDataBox(
                    when (selectedTeam) {
                        Team.BLOWINDS -> "まだ選手データが届いていません。GitHubの自動更新(1時間おき)が動くと、Bリーグ公式の選手情報から全選手の成績が表示されます。"
                        Team.RAC, Team.UNITED -> "このチームの選手の成績は、今のところアプリで自動取得していません。"
                    }
                )
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
            SectionLabel(
                if (oppRoster != null) "次の対戦相手の選手" else "次の対戦相手の注目選手",
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
                "選手の数字はGitHubの自動更新(1時間おき)でBリーグ公式から取り直しています。下に引っ張ると最新のデータを読み込みます。",
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
private fun RosterSection(roster: TeamPlayers, sortKey: PlayerSort, onSortChange: (PlayerSort) -> Unit) {
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
        // 並び順のボタン5つを1行に並べる。文字の長さに合わせて幅を配分し、画面幅いっぱいに収める
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("並び順(背番号以外は数字の大きい順)", style = MaterialTheme.typography.labelSmall, color = InkSoft)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PlayerSort.entries.forEach { key ->
                    SortChip(
                        key.label,
                        sortKey == key,
                        Modifier.weight(key.label.length + 1f)
                    ) { onSortChange(key) }
                }
            }
        }
        Text(
            "数字は今季の1試合あたりの平均です(出場時間は「分:秒」)。選手を押すと、シュート成功率などの詳しい数字が開きます。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        sortPlayers(roster.players, sortKey).forEach { RosterCard(it) }
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

/** 「自チーム」「相手」へ飛ぶための位置の入れ物(スクロールする部分と、それぞれの見出し)。 */
private class JumpMarks {
    var viewport: LayoutCoordinates? = null
    var own: LayoutCoordinates? = null
    var opp: LayoutCoordinates? = null
}

/** 上に固定する「自チーム」「相手」へ飛ぶリンク。 */
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

/** 全選手一覧の並び順(ボタンはこの順に左から並ぶ)。1行に収めるため、ボタンの文字は「順」を省く。 */
private enum class PlayerSort(val label: String) {
    NUMBER("背番号"),
    MINUTES("出場時間"),
    POINTS("得点"),
    REBOUNDS("リバウンド"),
    ASSISTS("アシスト")
}

/**
 * 選んだ並び順で選手を並べる。背番号順以外は数字の大きい順。
 * 数字が無い選手(今季の出場なしなど)は最後に回す。同じ数字どうしは背番号順のまま。
 */
private fun sortPlayers(players: List<PlayerStats>, key: PlayerSort): List<PlayerStats> {
    val value: (PlayerStats) -> Double? = when (key) {
        PlayerSort.NUMBER -> return players
        PlayerSort.POINTS -> { p -> p.points?.toDoubleOrNull() }
        PlayerSort.MINUTES -> { p -> minutesToSeconds(p.minutesPerGame) }
        PlayerSort.REBOUNDS -> { p -> p.rebounds?.toDoubleOrNull() }
        PlayerSort.ASSISTS -> { p -> p.assists?.toDoubleOrNull() }
    }
    return players.sortedByDescending { value(it) ?: -1.0 }
}

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
private fun RosterCard(player: PlayerStats) {
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
            Headline(
                if (player.number.isNotBlank()) "#${player.number}" else "#-",
                fontSize = 22,
                modifier = Modifier.width(58.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    player.name.ifBlank { NO_DATA },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
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
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCell("試合", player.games, Modifier.weight(1f))
            StatCell("出場時間", player.minutesPerGame, Modifier.weight(1.2f))
            StatCell("得点", player.points, Modifier.weight(1f))
            StatCell("リバウンド", player.rebounds, Modifier.weight(1f))
            StatCell("アシスト", player.assists, Modifier.weight(1f))
        }
        if (expanded) {
            Column {
                StatRow("フィールドゴール成功率", percentText(player.fieldGoalPct) ?: NO_DATA)
                StatRow("3ポイント成功率", percentText(player.threePct) ?: NO_DATA)
                StatRow("フリースロー成功率", percentText(player.freeThrowPct) ?: NO_DATA)
                StatRow("スティール(平均)", player.steals ?: NO_DATA)
                StatRow("ブロック(平均)", player.blocks ?: NO_DATA)
                StatRow("貢献度(平均)", player.efficiency ?: NO_DATA)
            }
        }
    }
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
                Text(
                    player.name.ifBlank { NO_DATA },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
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
