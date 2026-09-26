package com.fukuiteams.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.MockData
import com.fukuiteams.app.data.startEpochMillis
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.TeamBadge
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.White
import java.net.URLEncoder

/**
 * 「試合」タブ。上から: チーム選択 → 譲渡・招待チケット検索(チーム単位) →
 * (試合を選んでいればその詳細) → 試合スケジュール一覧、の並び。
 * 「直近の試合」の概要はホーム画面にあるため、ここではタブを開いた時点では
 * 特定の試合を自動選択しない(スケジュールから選んだときだけ詳細が出る)。
 */
@Composable
fun GameDetailScreen(
    gameId: String?,
    onBack: () -> Unit,
    onOpenInvitations: () -> Unit
) {
    val initialTeam = MockData.upcomingGames.firstOrNull { it.id == gameId }?.team ?: Team.BLOWINDS
    var selectedTeam by remember { mutableStateOf(initialTeam) }

    val teamGames = remember(selectedTeam) {
        MockData.upcomingGames.filter { it.team == selectedTeam }.sortedBy { it.sortKey }
    }
    // ホーム画面から特定の試合を選んで遷移してきた場合はそれを表示。
    // 「試合」タブを直接開いた場合は、何も自動選択しない。
    var selectedGameId by remember(selectedTeam) {
        mutableStateOf(gameId?.takeIf { id -> teamGames.any { it.id == id } })
    }
    val game = teamGames.firstOrNull { it.id == selectedGameId }
    val context = LocalContext.current

    // Xの個人投稿は「チーム名+チケット+譲」で広めに検索。
    // 一方、企業広告は「譲ります」という言い方をしないため、広告検索は「チーム名+招待」のみにする。
    val personalSearchKeyword = "${selectedTeam.displayName} チケット 譲"
    val encodedPersonalKeyword = URLEncoder.encode(personalSearchKeyword, "UTF-8")
    val adSearchKeyword = "${selectedTeam.displayName} 招待"
    val encodedAdKeyword = URLEncoder.encode(adSearchKeyword, "UTF-8")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("試合", style = MaterialTheme.typography.titleSmall) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Team.values().toList()) { team ->
                    FilterChip(
                        selected = team == selectedTeam,
                        onClick = { selectedTeam = team },
                        leadingIcon = { TeamBadge(team, size = 20.dp, fontSize = 10.sp) },
                        label = { Text(team.displayName) }
                    )
                }
            }

            TicketSearchSection(
                personalSearchKeyword = personalSearchKeyword,
                adSearchKeyword = adSearchKeyword,
                onSearchX = { openUrl(context, "https://x.com/search?q=$encodedPersonalKeyword&f=live") },
                onSearchAd = {
                    openUrl(
                        context,
                        "https://www.facebook.com/ads/library/?active_status=active&ad_type=all&country=JP&q=$encodedAdKeyword&search_type=keyword_unordered&media_type=all"
                    )
                }
            )

            if (game != null) {
                SelectedGameDetail(game, onOpenInvitations)
            }

            if (teamGames.isEmpty()) {
                EmptyTeamState(selectedTeam)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("試合スケジュール")
                    teamGames.forEach { g ->
                        ScheduleRow(
                            game = g,
                            selected = g.id == selectedGameId,
                            onClick = { selectedGameId = g.id }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketSearchSection(
    personalSearchKeyword: String,
    adSearchKeyword: String,
    onSearchX: () -> Unit,
    onSearchAd: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("譲渡・招待チケットを探す")
        Text(
            "検索結果を開きます(X:${personalSearchKeyword}・広告:${adSearchKeyword})",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSearchX, modifier = Modifier.weight(1f)) { Text("Xで探す") }
            OutlinedButton(onClick = onSearchAd, modifier = Modifier.weight(1f)) { Text("SNS広告を探す(Meta広告ライブラリ)") }
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

@Composable
private fun ScheduleRow(game: Game, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) game.team.color.copy(alpha = 0.06f) else White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 1.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) game.team.color else LineGray)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("vs ${game.opponent}", style = MaterialTheme.typography.titleMedium)
                    HomeAwayBadge(isHome = game.isHome)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = InkSoft, modifier = Modifier.size(13.dp))
                    Text(game.timeLabel, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = InkSoft, modifier = Modifier.size(13.dp))
                    Text(game.venue, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
            }
        }
    }
}

@Composable
private fun EmptyTeamState(team: Team) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, LineGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("${team.displayName}の試合データはまだ準備できていません", style = MaterialTheme.typography.bodyLarge)
            Text(
                "公式サイトの日程ページが確認でき次第、反映します。公式サイトへのリンクはホーム画面にあります。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        }
    }
}

@Composable
private fun SelectedGameDetail(game: Game, onOpenInvitations: () -> Unit) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MatchHeaderCard(game)

        OutlinedButton(
            onClick = { addToCalendar(context, game) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Googleカレンダーに追加") }

        SectionTitle("無料招待")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenInvitations),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                    Text("無料招待・プレゼント情報を見る", style = MaterialTheme.typography.bodyLarge)
                    Text("自動検知した最新情報はこちらから確認できます", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
                Text("›", color = Accent, style = MaterialTheme.typography.titleMedium)
            }
        }

        SectionTitle("公式チケット")
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(1.dp, LineGray)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("販売状況:${game.ticketStatus}・一般販売開始 ${game.ticketSaleStart}", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
                Button(
                    onClick = {
                        val url = game.team.officialSiteUrl
                            ?: "https://www.google.com/search?q=" + URLEncoder.encode("${game.team.displayName} チケット", "UTF-8")
                        openUrl(context, url)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Ink),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("チケットを購入する(公式サイト)") }
            }
        }
    }
}

@Composable
private fun MatchHeaderCard(game: Game) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, LineGray)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(game.team.displayName, color = InkSoft, style = MaterialTheme.typography.bodySmall)
                    HomeAwayBadge(isHome = game.isHome)
                }
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
                    TeamBadge(game.team, size = 64.dp, fontSize = 24.sp)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = InkSoft, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "${game.dateLabel}(${game.dayOfWeek})${game.timeLabel} 開始",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { openUrl(context, "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(game.venue, "UTF-8")) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Accent, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(game.venue, style = MaterialTheme.typography.bodyMedium, color = Accent)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Filled.Map, contentDescription = "地図を開く", tint = Accent, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun HomeAwayBadge(isHome: Boolean) {
    val bg: androidx.compose.ui.graphics.Color
    val fg: androidx.compose.ui.graphics.Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val label: String
    if (isHome) {
        fg = androidx.compose.ui.graphics.Color(0xFF2F6846)
        bg = fg.copy(alpha = 0.14f)
        icon = Icons.Filled.Home
        label = "ホーム"
    } else {
        fg = androidx.compose.ui.graphics.Color(0xFF2541B2)
        bg = fg.copy(alpha = 0.12f)
        icon = Icons.Filled.Flight
        label = "アウェイ"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(11.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

/**
 * 「Googleカレンダーに追加」ボタン用。実装が簡単で権限も不要なため、
 * Android標準のカレンダー登録画面(ACTION_INSERT)を開く方式にしている。
 * 開いた画面でユーザーが最後に保存をタップする必要がある(完全自動保存ではない)。
 * MockDataの日付に年が含まれていないため、今年として計算している。
 * 試合時間は仮に2時間として終了時刻を設定している。
 */
private fun addToCalendar(context: Context, game: Game) {
    val begin = game.startEpochMillis()
    val end = begin + 2 * 60 * 60 * 1000
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, "${game.team.displayName} vs ${game.opponent}")
        putExtra(CalendarContract.Events.EVENT_LOCATION, game.venue)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
    }
    context.startActivity(intent)
}
