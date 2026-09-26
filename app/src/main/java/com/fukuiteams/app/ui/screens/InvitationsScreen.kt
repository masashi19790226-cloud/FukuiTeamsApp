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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.fukuiteams.app.data.MockData
import com.fukuiteams.app.data.RemoteInvitationAlert
import com.fukuiteams.app.model.InvitationEvent
import com.fukuiteams.app.model.InvitationSource
import com.fukuiteams.app.model.InvitationStatus
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.TeamBadge
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.White

@Composable
fun InvitationsScreen() {
    var tabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("無料招待・プレゼント", style = MaterialTheme.typography.titleLarge) },
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
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("募集中 ${MockData.openInvitations.size}") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("アーカイブ") })
            }

            if (tabIndex == 0) {
                OpenInvitationsList()
            } else {
                ArchivedInvitationsList()
            }
        }
    }
}

@Composable
private fun OpenInvitationsList() {
    val context = LocalContext.current
    var alertsResult by remember { mutableStateOf<AlertsResult?>(null) }

    LaunchedEffect(Unit) {
        alertsResult = InvitationAlertsRepository.fetch()
    }

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("自動検知した最新情報(Googleアラート)", style = MaterialTheme.typography.labelMedium, color = InkSoft)
                when (val result = alertsResult) {
                    null -> Text("読み込み中…", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    is AlertsResult.Failure -> Text(
                        "取得に失敗しました(通信環境をご確認ください)",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                    is AlertsResult.Success -> {
                        if (result.items.isEmpty()) {
                            Text("まだ自動検知された情報はありません", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                result.items.take(10).forEach { alert ->
                                    AutoDetectedAlertCard(alert, onClick = { openUrl(context, alert.link) })
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("サンプル(仕様確認用のダミーデータ)", style = MaterialTheme.typography.labelMedium, color = InkSoft)
        }

        items(MockData.openInvitations) { invitation ->
            OpenInvitationCard(invitation)
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

@Composable
private fun OpenInvitationCard(invitation: InvitationEvent) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        border = BorderStroke(1.dp, LineGray)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SourceBadge(invitation.source)
                    TeamBadge(invitation.team, size = 22.dp, fontSize = 10.sp)
                    Text(invitation.team.displayName, color = invitation.team.color, style = MaterialTheme.typography.labelMedium)
                }
                Text(invitation.deadlineLabel, color = Accent, style = MaterialTheme.typography.labelMedium)
            }
            Text(invitation.title, style = MaterialTheme.typography.titleMedium)
            Column {
                Text("発信元:${invitation.fromWho}・検知 ${invitation.detectedAt}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text("対象試合:${invitation.relatedGameLabel}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { /* TODO: 応募ページを開く */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.weight(1f)
                ) { Text("応募ページを開く") }
                OutlinedButton(onClick = { /* TODO: 元投稿を開く */ }) { Text("元投稿") }
            }
        }
    }
}

@Composable
private fun ArchivedInvitationsList() {
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(MockData.archivedInvitations) { invitation ->
            ArchivedInvitationCard(invitation)
        }
    }
}

@Composable
private fun ArchivedInvitationCard(invitation: InvitationEvent) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        border = BorderStroke(1.dp, LineGray)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TeamBadge(invitation.team, size = 20.dp, fontSize = 9.sp)
                    Text(
                        "${invitation.source.label}・",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                    Text(invitation.team.displayName, color = invitation.team.color, style = MaterialTheme.typography.labelMedium)
                }
                StatusBadge(invitation.status)
            }
            Text(invitation.title, style = MaterialTheme.typography.titleMedium)
            Text("締切 ${invitation.deadlineLabel}・対象試合 ${invitation.relatedGameLabel}", style = MaterialTheme.typography.bodySmall, color = InkSoft)

            if (invitation.status == InvitationStatus.RESULT_LINKED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ResultStat("当選", invitation.winners ?: "-")
                    ResultStat("応募", invitation.applicants ?: "-")
                    ResultStat("倍率", invitation.competitionRate ?: "-")
                }
            } else if (invitation.status == InvitationStatus.CLOSED_NO_RESULT) {
                OutlinedButton(onClick = { /* TODO: 結果の投稿を紐づける */ }) {
                    Text("結果の投稿を紐づける", color = Accent)
                }
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SourceBadge(source: InvitationSource) {
    val (bg, fg) = when (source) {
        InvitationSource.OFFICIAL -> Ink to White
        InvitationSource.AD -> White to Ink
        InvitationSource.PERSONAL -> DividerGray to Ink
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(source.label, color = fg, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StatusBadge(status: InvitationStatus) {
    val label = when (status) {
        InvitationStatus.OPEN -> "募集中"
        InvitationStatus.CLOSED_NO_RESULT -> "終了・結果未反映"
        InvitationStatus.RESULT_LINKED -> "結果反映済み"
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = InkSoft)
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
