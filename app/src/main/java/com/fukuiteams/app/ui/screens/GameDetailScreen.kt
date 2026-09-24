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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fukuiteams.app.data.MockData
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.ui.components.TeamBadge
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.White

@Composable
fun GameDetailScreen(
    gameId: String?,
    onBack: () -> Unit,
    onOpenInvitations: () -> Unit
) {
    val game = MockData.upcomingGames.firstOrNull { it.id == gameId } ?: MockData.upcomingGames.first()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("試合詳細", style = MaterialTheme.typography.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MatchHeaderCard(game)

            SectionTitle("無料招待")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenInvitations),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                border = BorderStroke(2.dp, Accent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("この試合の招待 募集中 2件", style = MaterialTheme.typography.bodyLarge)
                        Text("最も早い締切:あと18時間", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                    Text("›", color = Accent, style = MaterialTheme.typography.titleMedium)
                }
            }

            SectionTitle("公式チケット")
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                border = BorderStroke(1.dp, LineGray)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("販売状況:${game.ticketStatus}・一般販売開始 ${game.ticketSaleStart}", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { /* TODO: 公式チケットページを開く */ },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Ink),
                            modifier = Modifier.weight(1f)
                        ) { Text("公式販売ページを開く") }
                        OutlinedButton(onClick = { /* TODO: 通知登録 */ }) { Text("販売開始を通知") }
                    }
                }
            }

            SectionTitle("譲渡・出品を探す(非公式)")
            Text(
                "各サービスの検索結果を開きます(キーワード:${game.team.displayName} ${game.dateLabel} 譲)",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Xで探す", "メルカリ", "ジモティー").forEach { label ->
                    OutlinedButton(
                        onClick = { /* TODO: 外部検索結果ページを開く */ },
                        modifier = Modifier.weight(1f)
                    ) { Text(label) }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
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
}

@Composable
private fun MatchHeaderCard(game: Game) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        border = BorderStroke(1.dp, LineGray)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${game.team.displayName}・ホーム戦",
                    color = InkSoft,
                    style = MaterialTheme.typography.bodySmall
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DividerGray)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("試合前", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.width(100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamBadge(game.team, size = 64.dp, fontSize = androidx.compose.ui.unit.sp(24))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(game.team.displayName, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("VS", style = MaterialTheme.typography.titleSmall, color = InkSoft)
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.width(100.dp),
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
                    Text(game.opponent, style = MaterialTheme.typography.labelMedium, color = InkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Text(
                "${game.dateLabel}(${game.dayOfWeek})${game.timeLabel} 開始・${game.venue}",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}
