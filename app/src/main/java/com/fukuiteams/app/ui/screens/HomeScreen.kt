package com.fukuiteams.app.ui.screens

import com.fukuiteams.app.data.commentHeadline
import com.fukuiteams.app.data.upcomingSpecialDay
import com.fukuiteams.app.data.specialDay
import com.fukuiteams.app.data.SpecialDaysRepository
import com.fukuiteams.app.ui.components.SpecialDayBanner
import com.fukuiteams.app.ui.components.SpecialDayBadge
import com.fukuiteams.app.data.resultLead
import com.fukuiteams.app.data.recordedComment
import com.fukuiteams.app.ui.components.SourceTag
import com.fukuiteams.app.data.sourceLabel
import androidx.compose.runtime.collectAsState
import androidx.datastore.preferences.core.Preferences
import com.fukuiteams.app.ui.components.photoCaption
import com.fukuiteams.app.ui.components.NewspaperPhoto
import com.fukuiteams.app.data.recordedWatchMethod
import com.fukuiteams.app.data.gameLogDataStore
import com.fukuiteams.app.data.GamePhotos
import androidx.compose.ui.text.font.FontWeight
import com.fukuiteams.app.ui.theme.Ivory
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import com.fukuiteams.app.ui.components.resultMark
import com.fukuiteams.app.data.resultSourceUrl
import com.fukuiteams.app.data.GameOutcome
import com.fukuiteams.app.data.RemoteGameResult
import com.fukuiteams.app.data.GameResultsRepository
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.components.resultHeadline
import com.fukuiteams.app.ui.components.DoubleRule
import com.fukuiteams.app.ui.components.ThinRule
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.HomeAwayTag
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
import com.fukuiteams.app.data.DataStatus
import com.fukuiteams.app.data.DataStatusRepository
import com.fukuiteams.app.data.MatchWeather
import com.fukuiteams.app.data.WeatherRepository
import com.fukuiteams.app.data.countdownLabel
import com.fukuiteams.app.data.isToday
import com.fukuiteams.app.data.GameLp
import com.fukuiteams.app.data.GameLpRepository
import com.fukuiteams.app.ui.components.GameLpSection
import com.fukuiteams.app.widget.NextGameWidget
import com.fukuiteams.app.data.hasMatchingInvite
import com.fukuiteams.app.data.InvitationAlertsRepository
import com.fukuiteams.app.data.GamesRepository
import com.fukuiteams.app.data.GamePreview
import com.fukuiteams.app.data.GamePreviewRepository
import com.fukuiteams.app.data.KeyPlayer
import com.fukuiteams.app.data.NewsAlertsRepository
import com.fukuiteams.app.data.RemoteInvitationAlert
import com.fukuiteams.app.data.timeLabel
import com.fukuiteams.app.data.isLikelyClosed
import com.fukuiteams.app.data.isUpcoming
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.data.TeamSelection
import com.fukuiteams.app.data.DataRefresher
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
    onOpenNotifications: () -> Unit,
    // 「ニュースをもっと見る」:一面で選んでいるチーム(null = すべて)でトピックを開く
    onOpenRadar: (Team?) -> Unit = {}
) {
    // 選んでいるチーム(null = すべて)。ほかのタブと共通なので、タブを切り替えても変わらない
    val selectedTeam = TeamSelection.current
    var newsResult by remember { mutableStateOf<AlertsResult?>(null) }
    var invitationsResult by remember { mutableStateOf<AlertsResult?>(null) }
    val pullToRefreshState = rememberPullToRefreshState()
    val appContext = LocalContext.current.applicationContext

    var autoResults by remember { mutableStateOf<Map<String, RemoteGameResult>>(emptyMap()) }
    var previews by remember { mutableStateOf<Map<String, GamePreview>>(emptyMap()) }
    var dataStatus by remember { mutableStateOf<DataStatus?>(null) }
    var weathers by remember { mutableStateOf<Map<String, MatchWeather>>(emptyMap()) }

    // アプリのすべての情報(日程・結果・招待・ニュース・展望・選手・順位・試合情報ページなど)を読み直す。
    // 選手や順位はこの画面では使わないが、ほかのタブに切り替えたときに新しい内容を出せるよう一緒に読み直す
    suspend fun refreshAll() {
        val d = DataRefresher.refreshAll(appContext)
        autoResults = d.results
        invitationsResult = d.invitations
        newsResult = d.news
        previews = d.previews
        dataStatus = d.status
        // 今日の試合の天気
        weathers = GamesRepository.games.filter { it.isToday() }
            .mapNotNull { g -> WeatherRepository.forGame(g)?.let { g.id to it } }
            .toMap()
    }

    LaunchedEffect(Unit) { refreshAll() }

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            refreshAll()
            pullToRefreshState.endRefresh()
        }
    }

    // 「招待あり」タグは、募集中の招待情報のうち、その試合の日付(または対戦相手)が
    // 書かれているものがある試合にだけ付ける。チームに招待が1件あるだけで全試合に付けない。
    val openInvites = (invitationsResult as? AlertsResult.Success)?.items
        ?.filterNot { it.isLikelyClosed() }
        ?: emptyList()

    Scaffold(
        topBar = {
            // 通知設定は下のメニューの「メニュー」から開くので、ここにはボタンを置かない。右端は更新ボタン
            MastheadTopBar(
                section = "一面",
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
        // チームの切り替えボタンは一番上に固定し、下にスクロールしても常に表示する
        Column(modifier = Modifier.fillMaxSize()) {
        TeamSelectorRow(
            selectedTeam = selectedTeam,
            onSelect = { t -> TeamSelection.select(if (t != null && t == selectedTeam) null else t) },
            showAll = true,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // 試合当日だけ、一番上に当日のまとめ(開始時刻・会場の地図・天気)
            val todayGames = GamesRepository.games
                .filter { (selectedTeam == null || it.team == selectedTeam) && it.isToday() && !autoResults.containsKey(it.id) }
                .sortedBy { it.sortKey }
            if (todayGames.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        todayGames.forEach { g ->
                            MatchDayCard(g, weathers[g.id], GameLpRepository.latest[g.id], onOpen = { onOpenGame(g.id) })
                        }
                    }
                }
            }

            // コラボ企画など特別な日が45日以内にあれば、特集枠を出す(選んでいるチームが関係するときだけ)
            val special = upcomingSpecialDay()
            if (special != null && (selectedTeam == null || selectedTeam in special.teams)) {
                item { SpecialDayBanner(special, onOpenGame = { g -> onOpenGame(g.id) }) }
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


            item {
                // 一面の「次の試合」と他チームの近況。
                // 次の試合の日にほかのチームの試合もあれば、同じ日の試合をすべて「次の試合」に並べる
                // (並びは開始時刻順。「時間未定」の試合は後ろ)
                val upcoming = GamesRepository.games
                    .filter { (selectedTeam == null || it.team == selectedTeam) && it.isUpcoming() }
                val firstDay = upcoming.minByOrNull { it.sortKey }?.sortKey?.take(8)
                val nextGames = upcoming
                    .filter { firstDay != null && it.sortKey.take(8) == firstDay }
                    .sortedBy { g -> if (Regex("""\d{1,2}:\d{2}""").matches(g.timeLabel.trim())) g.sortKey else g.sortKey.take(8) + "-9999" }
                val sideTeams = if (selectedTeam != null) {
                    Team.values().filter { it != selectedTeam }
                } else {
                    Team.values().filter { t -> nextGames.none { it.team == t } }
                }
                TwoColumnFront(
                    nextGames = nextGames,
                    hasOpenInvite = { g -> g.hasMatchingInvite(openInvites) },
                    sideTeams = sideTeams,
                    autoResults = autoResults,
                    previews = previews,
                    lps = GameLpRepository.latest,
                    onOpenGame = onOpenGame
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("今後の日程")
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
                                hasOpenInvite = game.hasMatchingInvite(openInvites),
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

            // 公式サイトへのリンクは、下のメニューの「メニュー」に移した

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
                    // ニュース・招待などの自動更新が最後に動いた時刻
                    DataStatusLine(dataStatus)
                    NewsSection(newsResult, selectedTeam) { onOpenRadar(selectedTeam) }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
        }
        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        }
    }
}

/** 試合当日のまとめ。赤い見出しで目立たせ、開始時刻・会場(地図)・天気をまとめて出す。 */
@Composable
private fun MatchDayCard(game: Game, weather: MatchWeather?, lp: GameLp?, onOpen: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, NewsRed)
            .background(Paper)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SectionLabel("本日の試合", red = true)
        Text(game.team.displayName, style = MaterialTheme.typography.labelMedium, color = game.team.color)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Headline("${game.timeLabel} 試合開始", fontSize = 20)
            HomeAwayTag(isHome = game.isHome)
        }
        Headline("vs ${game.opponent}", fontSize = 17)
        Text(
            "会場:${game.venue} ›地図",
            style = MaterialTheme.typography.bodyMedium,
            color = Accent,
            modifier = Modifier.clickable {
                openUrl(context, "https://www.google.com/maps/search/?api=1&query=" +
                    java.net.URLEncoder.encode(game.venue, "UTF-8"))
            }
        )
        if (weather != null) {
            Text(
                "${weather.placeLabel}の${game.timeLabel.substringBefore(":")}時ごろの天気:${weather.summary} ${weather.temperature}℃" +
                    (weather.rainChance?.let { " 降水確率$it%" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink
            )
        }
        Text("チケット:${game.ticketStatus}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        // ブローウィンズのホームゲームは、公式の試合情報ページの開場時刻・当日スケジュール・イベントも出す
        if (lp != null) {
            ThinRule(modifier = Modifier.padding(vertical = 4.dp))
            GameLpSection(lp, startExpanded = true)
        }
    }
}

/** ニュース欄に出す、自動更新(GitHub)の最終更新時刻と取得の失敗。 */
@Composable
private fun DataStatusLine(status: DataStatus?) {
    val updated = status?.updatedAt
    val text: String
    var warn = false
    if (status == null || updated == null) {
        text = "データの最終更新:確認できませんでした"
        warn = status != null
    } else {
        val zoned = updated.atZone(java.time.ZoneId.of("Asia/Tokyo"))
        val minutes = (System.currentTimeMillis() - updated.toEpochMilli()) / 60_000
        val ago = when {
            minutes < 60 -> "${minutes.coerceAtLeast(0)}分前"
            minutes < 24 * 60 -> "${minutes / 60}時間前"
            else -> "${minutes / (24 * 60)}日前"
        }
        val base = "データの最終更新:%d/%d %02d:%02d(%s)".format(zoned.monthValue, zoned.dayOfMonth, zoned.hour, zoned.minute, ago)
        text = when {
            minutes > 3 * 60 -> { warn = true; "$base・自動更新が止まっている可能性があります" }
            status.failedSteps.isNotEmpty() -> { warn = true; "$base・取得に失敗:${status.failedSteps.joinToString("、")}" }
            else -> base
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (warn) NewsRed else InkSoft,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NewsSection(newsResult: AlertsResult?, selectedTeam: Team?, onOpenRadar: () -> Unit) {
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
                        // 一面は最新3件だけ。続きはトピック画面でまとめて見る
                        newsList.take(3).forEachIndexed { index, news ->
                            NewsRow(news, onClick = { openUrl(context, news.link) })
                            if (index != newsList.take(3).lastIndex) {
                                Divider(color = DividerGray)
                            }
                        }
                    }
                }
                Text(
                    "ニュースをもっと見る(トピック) ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = Accent,
                    modifier = Modifier
                        .clickable(onClick = onOpenRadar)
                        .padding(vertical = 6.dp)
                )
            }
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
                modifier = Modifier.width(54.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(game.dayOfWeek, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text(
                    game.dateLabel.split("/").drop(1).joinToString("/"),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false
                )
                HomeAwayLabel(isHome = game.isHome)
            }
            TeamBadge(game.team, size = 34.dp, fontSize = 14.sp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (game.specialDay() != null) SpecialDayBadge()
                Text(
                    game.team.displayName,
                    color = game.team.color,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("vs ${game.opponent}", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = InkSoft, modifier = Modifier.size(13.dp))
                    Text(game.timeLabel, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = InkSoft, modifier = Modifier.size(13.dp))
                    Text(
                        game.venue,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (hasOpenInvite) {
                Text(
                    "招待あり",
                    maxLines = 1,
                    softWrap = false,
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

/** 紙面風の HOME / AWAY 表示。HOME は黒地に白抜き、AWAY は黒枠。 */
@Composable
private fun HomeAwayLabel(isHome: Boolean) {
    HomeAwayTag(isHome = isHome, modifier = Modifier.padding(top = 2.dp))
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // 1行目:引用元(どこの記事か)を枠付きで目立たせ、その横にチーム名と日時
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceTag(news.sourceLabel())
                Text(
                    listOf(team?.displayName ?: news.teamId, news.timeLabel())
                        .filter { it.isNotBlank() }.joinToString("・"),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
        val context = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("速報", red = true)
            HomeAwayTag(isHome = game.isHome)
            Text(
                "${game.dateLabel.split("/").drop(1).joinToString("/")}(${game.dayOfWeek}) ${game.timeLabel} 試合開始",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        }
        // 会場(押すと地図)と、その日の試合開始ごろの天気
        Text(
            "会場:${game.venue} ›地図",
            style = MaterialTheme.typography.bodySmall,
            color = Accent,
            modifier = Modifier.clickable {
                openUrl(context, "https://www.google.com/maps/search/?api=1&query=" +
                    java.net.URLEncoder.encode(game.venue, "UTF-8"))
            }
        )
        var weather by remember(game.id) { mutableStateOf<MatchWeather?>(null) }
        LaunchedEffect(game.id) { weather = WeatherRepository.forGame(game) }
        weather?.let { w ->
            val hour = game.timeLabel.substringBefore(":").toIntOrNull()
            Text(
                "天気(${w.placeLabel}${hour?.let { "・${it}時ごろ" } ?: ""}):${w.summary} ${w.temperature}℃" +
                    (w.rainChance?.let { " 降水確率$it%" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        }
        val leadPrefs by context.gameLogDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
        val comment = recordedComment(game.id, leadPrefs)
        val resultLine = resultHeadline(game, result.myScore, result.opponentScore, outcome)
        // 試合画面でコメントを書いていれば、その言葉を大見出しにし、結果は袖見出し(小さめの見出し)にする
        val commentTitle = commentHeadline(comment)
        if (commentTitle != null) {
            Headline(commentTitle, fontSize = 26)
            Text(
                "${game.team.displayName} $resultLine",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = game.team.color
            )
        } else {
            Headline("${game.team.displayName}\n$resultLine", fontSize = 26)
        }
        // 記事の書き出し(リード文)。試合結果と、試合画面で書いたコメントから組み立てる
        Text(
            resultLead(game, result, outcome, comment),
            style = MaterialTheme.typography.bodyMedium,
            color = Ink
        )
        // その試合に写真が登録されていれば、1枚目を写真説明付きで載せる
        val photos = remember(game.id, GamePhotos.version) { GamePhotos.list(context, game.id) }
        if (photos.isNotEmpty()) {
            val prefs by context.gameLogDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
            NewspaperPhoto(
                file = photos.first(),
                caption = photoCaption(game, result, outcome, recordedWatchMethod(game.id, prefs)),
                height = 200.dp
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(2.dp, Ink))
                // スコアボックスをタップすると取得元のページ(B.LEAGUE公式など)を開く
                .clickable { openUrl(context, game.resultSourceUrl(result)) }
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
        Text(
            "スコアをタップで詳報(取得元のページ)",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        DoubleRule(modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * 一面の「次の試合」欄。横幅いっぱいに次の試合と「データで見る展望」「相手の注目選手」を載せ、
 * その下に他チームの近況を2段で並べる。展望データがまだないときは試合情報だけを出す。
 * 同じ日にほかのチームの試合もあるときは、同じ日の試合をすべて(区切り線をはさんで)縦に並べる。
 */
@Composable
private fun TwoColumnFront(
    nextGames: List<Game>,
    hasOpenInvite: (Game) -> Boolean,
    sideTeams: List<Team>,
    autoResults: Map<String, RemoteGameResult>,
    previews: Map<String, GamePreview>,
    lps: Map<String, GameLp>,
    onOpenGame: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Ink)
                .background(Paper)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionLabel(if (nextGames.size > 1) "次の試合(同じ日に${nextGames.size}試合)" else "次の試合")
            if (nextGames.isEmpty()) {
                Text("予定はまだ発表されていません", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            nextGames.forEachIndexed { index, game ->
                if (index > 0) {
                    // 試合と試合の区切り(チームの色の太線)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .height(3.dp)
                            .background(game.team.color)
                    )
                }
                NextGameBlock(
                    nextGame = game,
                    hasOpenInvite = hasOpenInvite(game),
                    preview = previews[game.id],
                    lp = lps[game.id],
                    onOpenGame = onOpenGame
                )
            }
        }

        // 他チームの近況(2段)。同じ日に3チームとも試合があるときは出さない
        if (sideTeams.isNotEmpty()) {
            SectionLabel("各チームの近況", modifier = Modifier.padding(top = 6.dp))
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                sideTeams.forEachIndexed { index, team ->
                    if (index > 0) Box(modifier = Modifier.padding(horizontal = 8.dp).width(1.dp).fillMaxHeight().background(Ink))
                    Box(modifier = Modifier.weight(1f)) { TeamBrief(team, autoResults, onOpenGame) }
                }
            }
        }
        DoubleRule(modifier = Modifier.padding(top = 6.dp))
    }
}

/** 「次の試合」欄の1試合分。押すとその試合の詳細を開く。 */
@Composable
private fun NextGameBlock(
    nextGame: Game,
    hasOpenInvite: Boolean,
    preview: GamePreview?,
    lp: GameLp?,
    onOpenGame: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenGame(nextGame.id) },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // チーム名の左に HOME/AWAY の札(大きめ)、右端に試合まであと何日かを赤い札で出す
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeAwayTag(isHome = nextGame.isHome, fontSize = 14.sp)
            Text(
                nextGame.team.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = nextGame.team.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            // 「あと○日」は赤い枠と赤い文字(塗りつぶしにすると、赤地に白文字の HOME の札と見分けにくいため)
            nextGame.countdownLabel()?.let { label ->
                Text(
                    label,
                    modifier = Modifier
                        .border(1.5.dp, NewsRed)
                        .background(Paper)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = NewsRed,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Headline(
            "${nextGame.dateLabel.split("/").drop(1).joinToString("/")}(${nextGame.dayOfWeek}) ${nextGame.timeLabel}\n${nextGame.opponent}戦",
            fontSize = 19
        )
        Text(
            "${nextGame.venue}・チケット:${nextGame.ticketStatus}",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )

        // ブローウィンズのホームゲームは、公式の試合情報ページの開場時刻・当日スケジュール・イベントを出す
        // (スケジュールとイベントは押すと開く。試合当日は上の「本日の試合」に出すので、ここでは出さない)
        if (lp != null && !nextGame.isToday()) {
            ThinRule(modifier = Modifier.padding(vertical = 4.dp))
            GameLpSection(lp, startExpanded = false)
        }

        if (preview != null) {
            DoubleRule(modifier = Modifier.padding(vertical = 6.dp))
            SectionLabel("データで見る展望")
            PreviewTable(preview)
            if (preview.summary.isNotBlank()) {
                Text(preview.summary, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }

            ThinRule(modifier = Modifier.padding(vertical = 6.dp))
            SectionLabel("相手の注目選手")
            if (preview.keyPlayers.isNotEmpty()) {
                preview.keyPlayers.forEach { KeyPlayerRow(it) }
                if (preview.playersNote.isNotBlank()) {
                    Text("※${preview.playersNote}", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                }
            } else {
                Text("相手の選手データは公開されていません", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            preview.oppLink?.let { link ->
                Text(
                    "相手チームの情報を見る ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                    modifier = Modifier.clickable { openUrl(context, link) }.padding(top = 2.dp)
                )
            }
        }
        if (hasOpenInvite) {
            Text("無料招待あり ›", style = MaterialTheme.typography.labelMedium, color = NewsRed, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** 展望の比較表(福井側・項目名・相手側)。値がない行は出さない。 */
@Composable
private fun PreviewTable(preview: GamePreview) {
    val rows = listOf(
        Triple(preview.my.rank, "順位", preview.opp.rank),
        Triple(preview.my.record, "成績", preview.opp.record),
        Triple(preview.my.form, "直近", preview.opp.form)
    ).filter { it.first.isNotBlank() || it.third.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(preview.my.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Accent,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            Text("", modifier = Modifier.width(44.dp))
            Text(preview.opp.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        rows.forEach { (mine, label, theirs) ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(mine.ifBlank { "−" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                Text(label, modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = InkSoft,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(theirs.ifBlank { "−" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            }
        }
        preview.lastMeeting?.let {
            Text("前回対戦:$it", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}

@Composable
private fun KeyPlayerRow(player: KeyPlayer) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 2.dp)) {
        // 丸岡RUCKの相手の得点者は背番号が分からないので「-」
        Headline(if (player.number.isNotBlank()) "#${player.number}" else "#-", fontSize = 20, modifier = Modifier.width(52.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(player.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(player.position, player.stat).filter { it.isNotBlank() }.joinToString("・"),
                style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}

/** 右段の1チーム分。最新結果(あれば)と次の試合を1〜2行で。 */
@Composable
private fun TeamBrief(team: Team, autoResults: Map<String, RemoteGameResult>, onOpenGame: (String) -> Unit) {
    val teamGames = GamesRepository.games.filter { it.team == team }
    val latest = teamGames.filter { autoResults.containsKey(it.id) }.maxByOrNull { it.sortKey }
    val next = teamGames.filter { it.isUpcoming() }.minByOrNull { it.sortKey }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                (latest ?: next)?.let { g -> Modifier.clickable { onOpenGame(g.id) } } ?: Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.width(4.dp).height(12.dp).background(team.color))
            Text(team.shortLabelForFront(), style = MaterialTheme.typography.labelMedium)
        }
        if (latest != null) {
            val r = autoResults.getValue(latest.id)
            val outcome = when {
                r.myScore > r.opponentScore -> GameOutcome.WIN
                r.myScore < r.opponentScore -> GameOutcome.LOSE
                else -> GameOutcome.DRAW
            }
            Text(
                "${resultMark(outcome)}${r.myScore}-${r.opponentScore} ${latest.opponent}",
                style = MaterialTheme.typography.bodySmall,
                color = if (outcome == GameOutcome.WIN) NewsRed else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            if (next != null) "次:${next.dateLabel.split("/").drop(1).joinToString("/")} ${next.opponent}" else "次:日程発表待ち",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Team.shortLabelForFront(): String = when (this) {
    Team.BLOWINDS -> "ブローウィンズ"
    Team.RAC -> "丸岡RUCK"
    Team.UNITED -> "ユナイテッド"
}



