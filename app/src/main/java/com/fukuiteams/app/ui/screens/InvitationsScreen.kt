package com.fukuiteams.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.AlertsResult
import com.fukuiteams.app.data.InvitationAlertsRepository
import com.fukuiteams.app.data.RemoteInvitationAlert
import com.fukuiteams.app.data.isLikelyClosed
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.TeamBadge
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.White
import kotlinx.coroutines.launch

// アーカイブ表示用の説明文で使う日数(実際の判定はInvitationAlertsRepository.isLikelyClosed()を使用)
private const val ARCHIVE_AFTER_DAYS = 14L

@Composable
fun InvitationsScreen() {
    var tabIndex by remember { mutableIntStateOf(0) }
    var selectedTeam by remember { mutableStateOf<Team?>(null) }
    var alertsResult by remember { mutableStateOf<AlertsResult?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        isRefreshing = true
        alertsResult = InvitationAlertsRepository.fetch()
        isRefreshing = false
    }

    val teamFiltered = (alertsResult as? AlertsResult.Success)?.items
        ?.filter { selectedTeam == null || it.teamId == selectedTeam?.name }
        ?: emptyList()
    val openItems = teamFiltered.filterNot { it.isLikelyClosed() }
    val archivedItems = teamFiltered.filter { it.isLikelyClosed() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("無料招待・プレゼント", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "更新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshKey++ },
            modifier = Modifier.padding(padding)
        ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = Accent
            ) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("募集中 ${openItems.size}") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("アーカイブ ${archivedItems.size}") })
            }

            if (tabIndex == 0) {
                OpenInvitationsList(alertsResult, openItems)
            } else {
                ArchivedInvitationsList(archivedItems)
            }
        }
        }
    }
}

@Composable
private fun OpenInvitationsList(alertsResult: AlertsResult?, openItems: List<RemoteInvitationAlert>) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("自動検知した最新情報(Googleアラート)", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                when (alertsResult) {
                    null -> Text("読み込み中…", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    is AlertsResult.Failure -> Text(
                        "取得に失敗しました(通信環境をご確認ください)",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                    is AlertsResult.Success -> {
                        if (openItems.isEmpty()) {
                            Text("まだ自動検知された情報はありません", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                openItems.take(10).forEach { alert ->
                                    AutoDetectedAlertCard(alert, onClick = { openUrl(context, alert.link) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedInvitationsList(archivedItems: List<RemoteInvitationAlert>) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "検知から${ARCHIVE_AFTER_DAYS}日以上経過したものを自動的にここへ移しています(応募締切や当選結果までは判定していません)",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        }
        if (archivedItems.isEmpty()) {
            item {
                Text("まだアーカイブされた情報はありません", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            items(archivedItems) { alert ->
                AutoDetectedAlertCard(alert, onClick = { openUrl(context, alert.link) })
            }
        }
    }
}

@Composable
private fun AutoDetectedAlertCard(alert: RemoteInvitationAlert, onClick: () -> Unit) {
    val team = Team.values().find { it.name == alert.teamId }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        border = BorderStroke(1.dp, LineGray)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (team != null) {
                    TeamBadge(team, size = 20.dp, fontSize = 9.sp)
                    Text(team.displayName, color = team.color, style = MaterialTheme.typography.labelMedium)
                } else {
                    Text(alert.teamId, style = MaterialTheme.typography.labelMedium, color = InkSoft)
                }
            }
            Text(alert.title, style = MaterialTheme.typography.bodyLarge)
            Text("検知:${alert.published.ifBlank { alert.detectedAt }}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
