package com.fukuiteams.app.ui.screens

import com.fukuiteams.app.data.GameOutcome
import com.fukuiteams.app.data.RemoteGameResult
import com.fukuiteams.app.data.GameResultsRepository
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.components.resultHeadline
import com.fukuiteams.app.ui.components.DoubleRule
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.SectionLabel
import com.fukuiteams.app.ui.components.TeamSelectorRow
import com.fukuiteams.app.ui.components.MastheadTopBar
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.AlertsResult
import com.fukuiteams.app.data.InvitationAlertsRepository
import com.fukuiteams.app.data.GamesRepository
import com.fukuiteams.app.data.NewsAlertsRepository
import com.fukuiteams.app.data.RemoteInvitationAlert
import com.fukuiteams.app.data.timeLabel
import com.fukuiteams.app.data.isLikelyClosed
import com.fukuiteams.app.data.isUpcoming
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.TeamBadge
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.Paper
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.White

@Composable
fun HomeScreen(
    onOpenGame: (String) -> Unit,
    onOpenInvitations: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    var selectedTeam by remember { mutableStateOf<Team?>(null) }
    var newsResult by remember { mutableStateOf<AlertsResult?>(null) }
    var invitationsResult by remember { mutableStateOf<AlertsResult?>(null) }
    val pullToRefreshState = rememberPullToRefreshState()
    val appContext = LocalContext.current.applicationContext

    var autoResults by remember { mutableStateOf<Map<String, RemoteGameResult>>(emptyMap()) }

    suspend fun refreshAll() {
        GamesRepository.refresh(appContext)
        autoResults = GameResultsRepository.fetch()
        invitationsResult = InvitationAlertsRepository.fetch()
        newsResult = NewsAlertsRepository.fetch()
    }

    LaunchedEffect(Unit) { refreshAll() }

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            refreshAll()
            pullToRefreshState.endRefresh()
        }
    }

    // 「招待あり」タグは、Googleアラートで実際に募集中(14日以内に検知)の情報がある
    // チームの試合にだけ付ける。
    val teamsWithOpenInvites = (invitationsResult as? AlertsResult.Success)?.items
        ?.filterNot { it.isLikelyClosed() }
        ?.map { it.teamId }
        ?.toSet()
        ?: emptySet()

    Scaffold(
        topBar = {
            MastheadTopBar(
                section = "一面",
                edition = selectedTeam?.let { "${it.displayName}版" },
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Filled.Notifications, contentDescription = "通知設定")
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
            modifier = Modifier
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                TeamSelectorRow(
                    selectedTeam = selectedTeam,
                    onSelect = { t -> selectedTeam = if (t != null && t == selectedTeam) null else t },
                    showAll = true
                )
            }

            item {
                // 一面トップ:選択中のチーム(「すべて」なら全チーム)の最新の試合結果
                val latest = GamesRepository.games
                    .filter { (selectedTeam == null || it.team == selectedTeam) && autoResults.containsKey(it.id) }
                    .maxByOrNull { it.sortKey }
                if (latest != null) {
                    LatestResultHero(latest, autoResults.getValue(latest.id), onClick = { onOpenGame(latest.id) })
                }
            }

            item { InviteBanner(onClick = onOpenInvitations) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("次の試合")
                    // 特定のチームを選んだときはそのチームの直近3件、「すべて」のときは全体の直近3件を表示。
                    // 試合スケジュールの一覧は「試合」タブにまとめてあるので、ここでは概要だけ。
                    val games = if (selectedTeam != null) {
                        GamesRepository.games
                            .filter { it.team == selectedTeam && it.isUpcoming() }
                            .sortedBy { it.sortKey }
                            .take(3)
                    } else {
                        GamesRepository.games
                            .filter { it.isUpcoming() }
                            .sortedBy { it.sortKey }
                            .take(3)
                    }
                    if (games.isEmpty()) {
                        Text(
                            "このチームの試合データはまだ準備できていません",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft
                        )
                    } else {
                        games.forEach { game ->
                            GameCard(
                                game = game,
                                hasOpenInvite = game.team.name in teamsWithOpenInvites,
                                onClick = { onOpenGame(game.id) }
                            )
                        }
                    }
                    Text(
                        "すべての試合日程は「試合」タブでご覧いただけます",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("公式サイト")
                    OfficialSiteLinks(selectedTeam)
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel("ニュース")
                        IconButton(onClick = { pullToRefreshState.startRefresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "ニュースを更新")
                        }
                    }
                    NewsSection(newsResult, selectedTeam)
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        }
    }
}

@Composable
private fun NewsSection(newsResult: AlertsResult?, selectedTeam: Team?) {
    val context = LocalContext.current

    when (newsResult) {
        null -> Text("読み込み中…", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        is AlertsResult.Failure -> Text(
            "取得に失敗しました(通信環境をご確認ください)",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        is AlertsResult.Success -> {
            val newsList = newsResult.items.filter {
                selectedTeam == null || it.teamId == selectedTeam.name
            }
            if (newsList.isEmpty()) {
                Text("まだニュースが自動検知されていません", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            } else {
                Card(
                    shape = RoundedCornerShape(3.dp),
                    colors = CardDefaults.cardColors(containerColor = Paper),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, Ink)
                ) {
                    Column {
                        newsList.take(10).forEachIndexed { index, news ->
                            NewsRow(news, onClick = { openUrl(context, news.link) })
                            if (index != newsList.take(10).lastIndex) {
                                Divider(color = DividerGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteBanner(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Accent)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Text("無料招待・プレゼント情報", color = White, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Googleアラートで自動検知した最新情報をチェックできます", color = White, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text("一覧を見る ›", color = White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun OfficialSiteLinks(selectedTeam: Team?) {
    val context = LocalContext.current
    val teams = if (selectedTeam != null) listOf(selectedTeam) else Team.values().toList()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        teams.forEach { team ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(White)
                    .border(BorderStroke(1.dp, Ink), RoundedCornerShape(3.dp))
                    .clickable(enabled = team.officialSiteUrl != null) {
                        team.officialSiteUrl?.let { openUrl(context, it) }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TeamBadge(team, size = 28.dp, fontSize = 12.sp)
                Text(team.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (team.officialSiteUrl != null) {
                    Text("公式サイトを開く ›", style = MaterialTheme.typography.labelMedium, color = Accent)
                } else {
                    Text("準備中", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                }
            }
        }
    }
}

@Composable
private fun GameCard(game: Game, hasOpenInvite: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(3.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Ink)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.width(44.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(game.dayOfWeek, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text(
                    game.dateLabel.split("/").drop(1).joinToString("/"),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            TeamBadge(game.team, size = 34.dp, fontSize = 14.sp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(game.team.displayName, color = game.team.color, style = MaterialTheme.typography.labelMedium)
                    HomeAwayBadge(isHome = game.isHome)
                }
                Text("vs ${game.opponent}", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = InkSoft, modifier = Modifier.size(13.dp))
                    Text(game.timeLabel, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = InkSoft, modifier = Modifier.size(13.dp))
                    Text(game.venue, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
            }
            if (hasOpenInvite) {
                Text(
                    "招待あり",
                    color = Accent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(BorderStroke(1.dp, Accent), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeAwayBadge(isHome: Boolean) {
    val style = if (isHome) {
        HomeAwayStyle(
            background = androidx.compose.ui.graphics.Color(0xFF2F6846).copy(alpha = 0.14f),
            foreground = androidx.compose.ui.graphics.Color(0xFF2F6846),
            icon = Icons.Filled.Home,
            label = "ホーム"
        )
    } else {
        HomeAwayStyle(
            background = androidx.compose.ui.graphics.Color(0xFF2541B2).copy(alpha = 0.12f),
            foreground = androidx.compose.ui.graphics.Color(0xFF2541B2),
            icon = Icons.Filled.Flight,
            label = "アウェイ"
        )
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(style.background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(style.icon, contentDescription = null, tint = style.foreground, modifier = Modifier.size(11.dp))
        Text(style.label, style = MaterialTheme.typography.labelSmall, color = style.foreground)
    }
}

private data class HomeAwayStyle(
    val background: androidx.compose.ui.graphics.Color,
    val foreground: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String
)

@Composable
private fun NewsRow(news: RemoteInvitationAlert, onClick: () -> Unit) {
    val team = Team.values().find { it.name == news.teamId }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(team?.color ?: InkSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(team?.initial ?: "?", color = White, style = MaterialTheme.typography.titleMedium)
        }
        Column {
            Text(
                listOf(team?.displayName ?: news.teamId, news.timeLabel()).filter { it.isNotBlank() }.joinToString("・"),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            Text(news.title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

/** 一面トップの「速報」。最新の試合結果を大見出しとスコアボックスで見せる。 */
@Composable
private fun LatestResultHero(game: Game, result: RemoteGameResult, onClick: () -> Unit) {
    val outcome = when {
        result.myScore > result.opponentScore -> GameOutcome.WIN
        result.myScore < result.opponentScore -> GameOutcome.LOSE
        else -> GameOutcome.DRAW
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("速報", red = true)
            Text(
                "${game.dateLabel.split("/").drop(1).joinToString("/")}(${game.dayOfWeek})・${if (game.isHome) "HOME" else "AWAY"}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        }
        Headline(
            "${game.team.displayName}\n${resultHeadline(game, result.myScore, result.opponentScore, outcome)}",
            fontSize = 26
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(2.dp, Ink))
                .background(Paper)
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("福井", style = MaterialTheme.typography.labelMedium)
                Text(
                    when (outcome) {
                        GameOutcome.WIN -> "WIN"
                        GameOutcome.LOSE -> "LOSE"
                        GameOutcome.DRAW -> "DRAW"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (outcome == GameOutcome.WIN) NewsRed else InkSoft
                )
            }
            Headline("${result.myScore}", fontSize = 34, color = if (outcome == GameOutcome.WIN) NewsRed else Ink)
            Text("-", style = MaterialTheme.typography.titleLarge)
            Headline("${result.opponentScore}", fontSize = 34)
            Text(game.opponent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
        }
        DoubleRule(modifier = Modifier.padding(top = 6.dp))
    }
}

