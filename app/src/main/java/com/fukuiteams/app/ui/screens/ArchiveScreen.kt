package com.fukuiteams.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.fukuiteams.app.ui.components.MastheadTopBar
import com.fukuiteams.app.ui.components.MenuLinkRow
import com.fukuiteams.app.ui.components.NewsChip
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
    MATCHES("過去の試合")
}

/**
 * 過去の記録(メニューから開く)。チームごとに、シーズンごとの成績・相手別の通算の対戦成績・過去の試合の一覧と、
 * 過去の実績(TeamHistory.kt)・過去のニュースと招待(トピック)への入口を出す。
 * 試合の記録は data/history.json(scripts/history.py が公式サイトから作る)。
 */
@Composable
fun ArchiveScreen(
    onBack: () -> Unit,
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

    Scaffold(
        topBar = {
            MastheadTopBar(
                section = "過去の記録",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Headline("過去の記録", fontSize = 22)
                    Text(
                        "これまでのシーズンの成績・相手ごとの通算の対戦成績・過去の試合を振り返れます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                    DoubleRule(modifier = Modifier.padding(top = 6.dp))
                }
            }
            item {
                TeamSelectorRow(
                    selectedTeam = team,
                    onSelect = { t ->
                        if (t != null) {
                            team = t
                            TeamSelection.select(t)
                        }
                    },
                    showAll = false
                )
            }

            if (matches.isEmpty()) {
                item { NoHistoryNote(team, HistoryRepository.loaded) }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ArchiveTab.values().forEach { t ->
                            NewsChip(t.label, tab == t, modifier = Modifier.weight(1f), horizontalPadding = 4.dp) { tab = t }
                        }
                    }
                }
                when (tab) {
                    ArchiveTab.SEASONS -> {
                        items(seasons, key = { "s-" + it.season }) { s -> SeasonCard(team, s) }
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
            item { Text(" ", modifier = Modifier.padding(bottom = 8.dp)) }
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
        team == Team.BLOWINDS -> "ブローウィンズの過去の試合は、Bリーグ公式サイトのメンテナンスが終わってから加える予定です。"
        team == Team.RAC -> "丸岡RUCKの過去の試合は、女子Fリーグ公式サイトの過去の記録から、これから加える予定です。"
        else -> "過去の試合の記録を読み込めませんでした。通信環境を確かめて、あとで開き直してください(データの自動更新を待っている場合もあります)。"
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth().border(1.dp, LineGray).background(Paper).padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = Ink
    )
}

/** 1シーズン分の成績 */
@Composable
private fun SeasonCard(team: Team, s: SeasonSummary) {
    // そのシーズンにあった出来事(過去の実績)。ユナイテッドのシーズンは年、ブルーサンダーは始まった年で合わせる
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
