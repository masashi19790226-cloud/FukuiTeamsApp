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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.AlertsResult
import com.fukuiteams.app.data.InvitationAlertsRepository
import com.fukuiteams.app.data.RemoteInvitationAlert
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.TeamBadge
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.White

@Composable
fun InvitationsScreen() {
    var tabIndex by remember { mutableIntStateOf(0) }
    var alertsResult by remember { mutableStateOf<AlertsResult?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        alertsResult = null
        alertsResult = InvitationAlertsRepository.fetch()
    }

    val openCount = (alertsResult as? AlertsResult.Success)?.items?.size ?: 0

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
        Column(modifier = Modifier.padding(padding)) {
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = Accent
            ) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("募集中 $openCount") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("アーカイブ") })
            }

            if (tabIndex == 0) {
                OpenInvitationsList(alertsResult)
            } else {
                ArchivedInvitationsPlaceholder()
            }
        }
    }
}

@Composable
private fun OpenInvitationsList(alertsResult: AlertsResult?) {
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
                        if (alertsResult.items.isEmpty()) {
                            Text("まだ自動検知された情報はありません", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                alertsResult.items.take(10).forEach { alert ->
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

/**
 * 検知した情報を「終了」「結果判明」などに自動で振り分けてアーカイブする仕組みは
 * まだ作っていないため、現時点では空の状態を表示するだけ。
 */
@Composable
private fun ArchivedInvitationsPlaceholder() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "まだアーカイブされた情報はありません",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "検知した情報を終了・結果判明として自動的に振り分ける仕組みは、まだ作っていません。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
