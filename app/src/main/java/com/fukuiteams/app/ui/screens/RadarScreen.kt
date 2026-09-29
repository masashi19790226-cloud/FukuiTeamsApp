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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.AlertsResult
import com.fukuiteams.app.data.InvitationAlertsRepository
import com.fukuiteams.app.data.NewsAlertsRepository
import com.fukuiteams.app.data.RemoteInvitationAlert
import com.fukuiteams.app.data.eventInstant
import com.fukuiteams.app.data.isLikelyClosed
import com.fukuiteams.app.data.isTooOld
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.TeamBadge
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper
import com.fukuiteams.app.ui.theme.White
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class RadarTab(val label: String) {
    ALL("すべて"),
    NEWS("ニュース"),
    INVITE("招待・プレゼント")
}

private data class RadarItem(
    val alert: RemoteInvitationAlert,
    val kind: RadarTab
)

@Composable
fun RadarScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var newsItems by remember { mutableStateOf<List<RemoteInvitationAlert>>(emptyList()) }
    var inviteItems by remember { mutableStateOf<List<RemoteInvitationAlert>>(emptyList()) }
    var selectedTeam by remember { mutableStateOf<Team?>(Team.BLOWINDS) }
    var selectedTab by remember { mutableStateOf(RadarTab.ALL) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            errorMessage = null

            val newsResult = NewsAlertsRepository.fetch()
            val inviteResult = InvitationAlertsRepository.fetch()

            newsItems = when (newsResult) {
                is AlertsResult.Success -> newsResult.items
                is AlertsResult.Failure -> emptyList()
            }
            inviteItems = when (inviteResult) {
                is AlertsResult.Success -> inviteResult.items
                is AlertsResult.Failure -> emptyList()
            }

            val errors = listOfNotNull(
                (newsResult as? AlertsResult.Failure)?.message,
                (inviteResult as? AlertsResult.Failure)?.message
            )
            errorMessage = if (errors.size == 2) "ニュースと招待情報を取得できませんでした" else errors.firstOrNull()
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        load()
    }

    val radarItems = remember(newsItems, inviteItems, selectedTeam, selectedTab) {
        val all = buildList {
            if (selectedTab == RadarTab.ALL || selectedTab == RadarTab.NEWS) {
                addAll(newsItems.map { RadarItem(it, RadarTab.NEWS) })
            }
            if (selectedTab == RadarTab.ALL || selectedTab == RadarTab.INVITE) {
                addAll(
                    inviteItems
                        .filterNot { it.isLikelyClosed() || it.isTooOld() }
                        .map { RadarItem(it, RadarTab.INVITE) }
                )
            }
        }

        all.filter { item ->
            selectedTeam == null || item.alert.teamId == selectedTeam.name
        }.sortedByDescending { radarInstant(it.alert) ?: Instant.EPOCH }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BLOWINDS RADAR", fontSize = 19.sp)
                        Text(
                            "新着情報をまとめてチェック",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { load() }, enabled = !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "更新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Paper)
        ) {
            TeamFilter(
                selectedTeam = selectedTeam,
                onSelected = { selectedTeam = it }
            )

            TabFilter(
                selectedTab = selectedTab,
                onSelected = { selectedTab = it }
            )

            when {
                loading && radarItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null && radarItems.isEmpty() -> {
                    ErrorContent(
                        message = errorMessage ?: "取得に失敗しました",
                        onRetry = { load() }
                    )
                }

                radarItems.isEmpty() -> {
                    EmptyContent()
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp,
                            top = 8.dp,
                            end = 12.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "${radarItems.size}件",
                                style = MaterialTheme.typography.labelMedium,
                                color = InkSoft,
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                            )
                        }

                        items(
                            items = radarItems,
                            key = { "${it.kind.name}_${it.alert.id}" }
                        ) { item ->
                            RadarCard(
                                item = item,
                                onClick = { openUrl(context, item.alert.link) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamFilter(
    selectedTeam: Team?,
    onSelected: (Team?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = selectedTeam == null,
            onClick = { onSelected(null) },
            label = { Text("全チーム") },
            leadingIcon = if (selectedTeam == null) {
                { Icon(Icons.Filled.Newspaper, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null
        )

        Team.values().forEach { team ->
            FilterChip(
                selected = selectedTeam == team,
                onClick = { onSelected(if (selectedTeam == team) null else team) },
                label = { Text(team.initial) },
                leadingIcon = {
                    TeamBadge(team, size = 18.dp, fontSize = 8.sp)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = team.color.copy(alpha = 0.14f),
                    selectedLabelColor = Ink
                )
            )
        }
    }
}

@Composable
private fun TabFilter(
    selectedTab: RadarTab,
    onSelected: (RadarTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RadarTab.values().forEach { tab ->
            FilterChip(
                selected = selectedTab == tab,
                onClick = { onSelected(tab) },
                label = { Text(tab.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Ink,
                    selectedLabelColor = White
                )
            )
        }
    }
}

@Composable
private fun RadarCard(
    item: RadarItem,
    onClick: () -> Unit
) {
    val alert = item.alert
    val team = Team.values().find { it.name == alert.teamId }
    val isNew = isNewItem(alert)
    val sourceLabel = sourceLabel(alert.source, item.kind)
    val dateLabel = radarTimeLabel(alert)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = alert.link.isNotBlank(), onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = White),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, LineGray),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (team != null) {
                    TeamBadge(team, size = 28.dp, fontSize = 11.sp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("?", color = White, fontSize = 12.sp)
                    }
                }

                Text(
                    if (item.kind == RadarTab.INVITE) "招待・プレゼント" else "ニュース",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.kind == RadarTab.INVITE) Accent else NewsRed
                )

                if (isNew) {
                    Text(
                        "NEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NewsRed)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft
                )
            }

            Text(
                alert.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Ink
            )

            if (alert.snippet.isNotBlank() && item.kind == RadarTab.INVITE) {
                Text(
                    alert.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                    maxLines = 2
                )
            }

            if (dateLabel.isNotBlank()) {
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("情報を取得できませんでした", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(1.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        Spacer(modifier = Modifier.width(1.dp))
        Button(onClick = onRetry) {
            Text("もう一度取得")
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "該当する新着情報はありません",
            style = MaterialTheme.typography.bodyLarge,
            color = InkSoft
        )
    }
}

private fun radarInstant(alert: RemoteInvitationAlert): Instant? {
    return parseInstant(alert.detectedAt) ?: alert.eventInstant()
}

private fun parseInstant(raw: String): Instant? {
    if (raw.isBlank()) return null
    return try {
        OffsetDateTime.parse(raw).toInstant()
    } catch (_: Exception) {
        try {
            Instant.parse(raw)
        } catch (_: Exception) {
            null
        }
    }
}

private fun isNewItem(alert: RemoteInvitationAlert): Boolean {
    val instant = radarInstant(alert) ?: return false
    val age = System.currentTimeMillis() - instant.toEpochMilli()
    return age in 0..(24L * 60L * 60L * 1000L)
}

private fun radarTimeLabel(alert: RemoteInvitationAlert): String {
    val instant = radarInstant(alert) ?: return ""
    val zoned = instant.atZone(ZoneId.of("Asia/Tokyo"))
    val formatter = DateTimeFormatter.ofPattern("M/d(E) HH:mm")
    return formatter.format(zoned)
}

private fun sourceLabel(source: String, kind: RadarTab): String {
    if (source.isBlank()) {
        return if (kind == RadarTab.INVITE) "情報収集" else "ニュース"
    }
    return when {
        source.contains("Google", ignoreCase = true) -> "Google"
        source.contains("SNS", ignoreCase = true) -> "SNS"
        source.contains("福井新聞") -> "福井新聞"
        source.contains("NHK") -> "NHK"
        source.contains("公式") -> "公式"
        else -> source
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
