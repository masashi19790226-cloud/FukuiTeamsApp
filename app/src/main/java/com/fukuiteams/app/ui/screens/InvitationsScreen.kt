package com.fukuiteams.app.ui.screens

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.fukuiteams.app.ui.theme.Ivory
import com.fukuiteams.app.data.eventInstant
import com.fukuiteams.app.ui.components.DoubleRule
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.SectionLabel
import com.fukuiteams.app.ui.components.TeamSelectorRow
import com.fukuiteams.app.ui.components.MastheadTopBar
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.AlertsResult
import com.fukuiteams.app.data.InvitationAlertsRepository
import com.fukuiteams.app.data.RemoteInvitationAlert
import com.fukuiteams.app.data.timeLabel
import com.fukuiteams.app.data.isLikelyClosed
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.TeamBadge
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.Paper
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.White

// アーカイブ表示用の説明文で使う日数(実際の判定はInvitationAlertsRepository.isLikelyClosed()を使用)
private const val ARCHIVE_AFTER_DAYS = 14L

@Composable
fun InvitationsScreen() {
    var tabIndex by remember { mutableIntStateOf(0) }
    var selectedTeam by remember { mutableStateOf<Team?>(null) }
    var alertsResult by remember { mutableStateOf<AlertsResult?>(null) }
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        alertsResult = InvitationAlertsRepository.fetch()
    }

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            alertsResult = InvitationAlertsRepository.fetch()
            pullToRefreshState.endRefresh()
        }
    }

    val teamFiltered = (alertsResult as? AlertsResult.Success)?.items
        ?.filter { selectedTeam == null || it.teamId == selectedTeam?.name }
        ?: emptyList()
    val openItems = teamFiltered.filterNot { it.isLikelyClosed() }
    val archivedItems = teamFiltered.filter { it.isLikelyClosed() }

    Scaffold(
        topBar = {
            MastheadTopBar(
                section = "招待面",
                edition = selectedTeam?.let { "${it.displayName}版" },
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
            Column(modifier = Modifier.fillMaxWidth()) {
                TeamSelectorRow(
                    selectedTeam = selectedTeam,
                    onSelect = { t -> selectedTeam = if (t != null && t == selectedTeam) null else t },
                    showAll = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // 紙面の面割り風の切り替え(選択中は黒地に白抜き)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PageTab("受付中 ${openItems.size}", tabIndex == 0, Modifier.weight(1f)) { tabIndex = 0 }
                    PageTab("過去の招待 ${archivedItems.size}", tabIndex == 1, Modifier.weight(1f)) { tabIndex = 1 }
                }

                if (tabIndex == 0) {
                    OpenInvitationsList(alertsResult, openItems)
                } else {
                    ArchivedInvitationsList(archivedItems)
                }
            }
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun PageTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        modifier = modifier
            .border(1.dp, Ink)
            .background(if (selected) Ink else Paper)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        color = if (selected) Ivory else Ink,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun OpenInvitationsList(alertsResult: AlertsResult?, openItems: List<RemoteInvitationAlert>) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("受付中", red = true)
                Headline(
                    if (openItems.isEmpty()) "受付中の招待は\nいまのところなし" else "無料招待 ${openItems.size}件\n受付中",
                    fontSize = 24
                )
                Text(
                    "Googleアラートで自動検知した情報です。応募条件・締切は各記事でご確認ください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                DoubleRule(modifier = Modifier.padding(top = 6.dp))
            }
        }
        when (alertsResult) {
            null -> item { Text("読み込み中…", style = MaterialTheme.typography.bodySmall, color = InkSoft) }
            is AlertsResult.Failure -> item {
                Text("取得に失敗しました(通信環境をご確認ください)", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            is AlertsResult.Success -> items(openItems.take(10)) { alert ->
                NoticeBox(alert, onClick = { openUrl(context, alert.link) })
            }
        }
    }
}

/** 受付中の招待を「囲み記事」風に。3日以内に見つけたものには NEW を付ける。 */
@Composable
private fun NoticeBox(alert: RemoteInvitationAlert, onClick: () -> Unit) {
    val team = Team.values().find { it.name == alert.teamId }
    val isNew = alert.eventInstant()?.let {
        System.currentTimeMillis() - it.toEpochMilli() < 3L * 24 * 60 * 60 * 1000
    } ?: false
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, Ink)
            .background(Paper)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isNew) SectionLabel("NEW", red = true)
            if (team != null) {
                Box(modifier = Modifier.width(4.dp).height(12.dp).background(team.color))
                Text(team.displayName, style = MaterialTheme.typography.labelMedium, color = team.color)
            }
        }
        Headline(alert.title, fontSize = 16)
        if (alert.snippet.isNotBlank()) {
            Text("「${alert.snippet}」", style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
        val via = if (alert.source.isNotBlank()) "・${alert.source}" else ""
        Text("掲載 ${alert.timeLabel().ifBlank { "日時不明" }}$via", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        Text("記事を開く ›", style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

@Composable
private fun ArchivedInvitationsList(archivedItems: List<RemoteInvitationAlert>) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                SectionLabel("過去の招待")
                Text(
                    "検知から${ARCHIVE_AFTER_DAYS}日以上たったものをここへ移しています(締切や当選結果までは判定していません)。行をタップで記事を開きます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            }
        }
        if (archivedItems.isEmpty()) {
            item { Text("まだ過去の招待はありません", style = MaterialTheme.typography.bodyMedium) }
        } else {
            item { ArchiveTableRow("掲載", null, "内容", header = true) }
            items(archivedItems) { alert ->
                val team = Team.values().find { it.name == alert.teamId }
                ArchiveTableRow(
                    date = alert.eventInstant()?.atZone(java.time.ZoneId.of("Asia/Tokyo"))
                        ?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: "-",
                    team = team,
                    title = alert.title,
                    onClick = { openUrl(context, alert.link) }
                )
            }
        }
    }
}

/** 過去の招待の表組みの1行。左から 掲載日・チーム色・内容。 */
@Composable
private fun ArchiveTableRow(
    date: String,
    team: Team?,
    title: String,
    header: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                date,
                modifier = Modifier.width(40.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (header) FontWeight.ExtraBold else FontWeight.Normal
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (header) 0.dp else 28.dp)
                    .background(team?.color ?: Ink)
            )
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.ExtraBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (header) 2.dp else 1.dp)
                .background(if (header) Ink else LineGray)
        )
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
