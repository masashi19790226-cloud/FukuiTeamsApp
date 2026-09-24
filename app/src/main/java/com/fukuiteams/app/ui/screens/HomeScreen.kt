package com.fukuiteams.app.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.MockData
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.NewsItem
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
                    Text("今週の試合", style = MaterialTheme.typography.titleSmall)
                    val games = MockData.upcomingGames.filter { selectedTeam == null || it.team == selectedTeam }
                    games.forEach { game ->
                        GameCard(game = game, onClick = { onOpenGame(game.id) })
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("新着ニュース", style = MaterialTheme.typography.titleSmall)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        border = BorderStroke(1.dp, LineGray)
                    ) {
                        val newsList = MockData.news.filter { selectedTeam == null || it.team == selectedTeam }
                        Column {
                            newsList.forEachIndexed { index, news ->
                                NewsRow(news)
                                if (index != newsList.lastIndex) {
                                    Divider(color = DividerGray)
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("無料招待 募集中", color = White, style = MaterialTheme.typography.labelLarge)
                Text("3件", color = White, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("締切間近:ホームゲーム ペア招待券プレゼント・あと18時間", color = White, style = MaterialTheme.typography.bodyLarge)
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
                Text(game.dateLabel.substringAfter("/"), style = MaterialTheme.typography.titleMedium)
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
private fun NewsRow(news: NewsItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(news.team.color),
            contentAlignment = Alignment.Center
        ) {
            Text(news.team.initial, color = White, style = MaterialTheme.typography.titleMedium)
        }
        Column {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = news.team.color, fontWeight = FontWeight.Bold)) {
                        append(news.team.displayName)
                    }
                    append("・${news.source}・${news.postedAgo}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            Text(news.headline, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
