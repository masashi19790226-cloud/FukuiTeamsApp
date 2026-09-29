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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fukuiteams.app.data.GamePreview
import com.fukuiteams.app.data.GamePreviewRepository
import com.fukuiteams.app.data.GamesRepository
import com.fukuiteams.app.data.KeyPlayer
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
fun PlayersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTeam by remember { mutableStateOf(Team.BLOWINDS) }
    var previews by remember { mutableStateOf<Map<String, GamePreview>?>(null) }
    var teamPlayers by remember { mutableStateOf<Map<String, TeamPlayers>>(emptyMap()) }
    // 並び順(false = 背番号順、true = 平均得点の多い順)
    var sortByPoints by remember { mutableStateOf(false) }

    suspend fun load() {
        teamPlayers = PlayersRepository.fetch()
        previews = GamePreviewRepository.fetch()
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TeamSelectorRow(
                selectedTeam = selectedTeam,
                onSelect = { t -> if (t != null) selectedTeam = t },
                showAll = false
            )

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
                SectionLabel("${selectedTeam.displayName}の選手")
                Text(
                    listOf(
                        "出典:Bリーグ公式(クラブページの選手情報)",
                        roster.season.takeIf { it.isNotBlank() }?.let { "${it}シーズン" } ?: "",
                        roster.updatedAt?.atZone(java.time.ZoneId.of("Asia/Tokyo"))
                            ?.let { "取得 %d/%d %02d:%02d".format(it.monthValue, it.dayOfMonth, it.hour, it.minute) } ?: ""
                    ).filter { it.isNotBlank() }.joinToString("・"),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SortChip("背番号順", !sortByPoints) { sortByPoints = false }
                    SortChip("得点順", sortByPoints) { sortByPoints = true }
                }
                val sorted = if (sortByPoints) {
                    roster.players.sortedByDescending { it.points?.toDoubleOrNull() ?: -1.0 }
                } else {
                    roster.players
                }
                Text(
                    "数字は今季の1試合あたりの平均です。選手を押すと、シュート成功率などの詳しい数字が開きます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                sorted.forEach { RosterCard(it) }
            } else if (myPlayers.isNotEmpty()) {
                SectionLabel("${selectedTeam.displayName}の主な選手")
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
                SectionLabel("${selectedTeam.displayName}の選手")
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

            // 次の対戦相手の注目選手
            SectionLabel("次の対戦相手の注目選手")
            if (nextGame != null) {
                Text(
                    "${nextGame.dateLabel.split("/").drop(1).joinToString("/")}(${nextGame.dayOfWeek}) vs ${nextGame.opponent}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            val oppPlayers = preview?.keyPlayers ?: emptyList()
            if (oppPlayers.isNotEmpty()) {
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
        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        }
    }
}

/** 並び順の切り替えボタン。 */
@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .border(1.dp, Ink)
            .background(if (selected) Ink else Paper)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (selected) Paper else Ink,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal
    )
}

/**
 * 全選手一覧の1人分。背番号・名前・ポジションと、試合数・平均得点・リバウンド・アシスト。
 * 押すと、出場時間・シュート成功率・スティール・ブロック・貢献度が開く。
 */
@Composable
private fun RosterCard(player: PlayerStats) {
    var expanded by remember(player.number, player.name) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .clickable { expanded = !expanded }
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
            Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelMedium, color = InkSoft)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCell("試合", player.games, Modifier.weight(1f))
            StatCell("得点", player.points, Modifier.weight(1f))
            StatCell("リバウンド", player.rebounds, Modifier.weight(1f))
            StatCell("アシスト", player.assists, Modifier.weight(1f))
        }
        if (expanded) {
            Column {
                StatRow("出場時間(平均)", player.minutesPerGame ?: NO_DATA)
                StatRow("フィールドゴール成功率", player.fieldGoalPct ?: NO_DATA)
                StatRow("3ポイント成功率", player.threePct ?: NO_DATA)
                StatRow("フリースロー成功率", player.freeThrowPct ?: NO_DATA)
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
