package com.fukuiteams.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.AlertsResult
import com.fukuiteams.app.data.MockData
import com.fukuiteams.app.data.NewsAlertsRepository
import com.fukuiteams.app.data.RemoteInvitationAlert
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.TeamBadge
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.Ink
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
    var newsRefreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(newsRefreshKey) {
        newsResult = null
        newsResult = NewsAlertsRepository.fetch()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("福井チーム情報", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Filled.Notifications, contentDescription = "通知設定")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedTeam == null,
                            onClick = { selectedTeam = null },
                            label = { Text("すべて") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Ink,
                                selectedLabelColor = White
                            )
                        )
                    }
                    items(Team.values().toList()) { team ->
                        FilterChip(
                            selected = selectedTeam == team,
                            onClick = { selectedTeam = if (selectedTeam == team) null else team },
                            leadingIcon = { TeamBadge(team, size = 20.dp, fontSize = 10.sp) },
                            label = { Text(team.displayName) }
                        )
                    }
                }
            }

            item { InviteBanner(onClick = onOpenInvitations) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("今後の試合", style = MaterialTheme.typography.titleSmall)
                    val games = MockData.upcomingGames
                        .filter { selectedTeam == null || it.team == selectedTeam }
                        .sortedBy { it.sortKey }
                        .take(10)
                    if (games.isEmpty()) {
                        Text(
                            "このチームの試合データはまだ準備できていません",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft
                        )
                    } else {
                        games.forEach { game ->
                            GameCard(game = game, onClick = { onOpenGame(game.id) })
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("新着ニュース(Googleアラート)", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { newsRefreshKey++ }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "ニュースを更新")
                        }
                    }
                    NewsSection(newsResult, selectedTeam)
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
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
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    border = BorderStroke(1.dp, LineGray)
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
            .clip(RoundedCornerShape(14.dp))
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
private fun GameCard(game: Game, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        border = BorderStroke(1.dp, LineGray)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.width(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(game.dayOfWeek, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text(game.dateLabel.substringAfterLast("/"), style = MaterialTheme.typography.titleMedium)
            }
            TeamBadge(game.team, size = 34.dp, fontSize = 14.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(game.team.displayName, color = game.team.color, style = MaterialTheme.typography.labelMedium)
                Text("vs ${game.opponent}", style = MaterialTheme.typography.titleMedium)
                Text("${game.timeLabel}・${game.venue}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            if (game.hasInvitation) {
                Text(
                    "招待あり",
                    color = Accent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}

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
                .clip(RoundedCornerShape(10.dp))
                .background(team?.color ?: InkSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(team?.initial ?: "?", color = White, style = MaterialTheme.typography.titleMedium)
        }
        Column {
            Text(
                "${team?.displayName ?: news.teamId}・検知 ${news.published.ifBlank { news.detectedAt }}",
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
