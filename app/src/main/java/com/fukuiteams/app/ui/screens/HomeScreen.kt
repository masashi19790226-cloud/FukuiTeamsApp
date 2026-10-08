package com.fukuiteams.app.ui.screens

import com.fukuiteams.app.data.publicViewings
import com.fukuiteams.app.ui.components.PublicViewingBadge
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
import com.fukuiteams.app.data.WatchMethod
import com.fukuiteams.app.data.gameLogDataStore
import com.fukuiteams.app.data.GamePhotos
import androidx.compose.ui.text.font.FontWeight
import com.fukuiteams.app.ui.theme.Ivory
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import com.fukuiteams.app.ui.components.resultMark
import com.fukuiteams.app.data.resultSourceUrl
import com.fukuiteams.app.data.GameOutcome
import com.fukuiteams.app.data.RemoteGameResult
import com.fukuiteams.app.data.GameResultsRepository
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.components.resultHeadline
import com.fukuiteams.app.ui.components.RemoteThumbnail
import com.fukuiteams.app.data.BirthdaysRepository
import com.fukuiteams.app.data.PlayerBirthday
import com.fukuiteams.app.data.FeatureStory
import com.fukuiteams.app.data.buildFeatureStories
import com.fukuiteams.app.data.BbsBuzz
import com.fukuiteams.app.data.BbsBuzzRepository
import com.fukuiteams.app.data.BbsPrefs
import com.fukuiteams.app.data.BbsSettings
import com.fukuiteams.app.data.isFreshResult
import com.fukuiteams.app.data.TeamPlayers
import com.fukuiteams.app.data.LeagueStandings
import com.fukuiteams.app.ui.components.DoubleRule
import com.fukuiteams.app.ui.components.ThinRule
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.HomeAwayTag
import com.fukuiteams.app.ui.components.SectionLabel
import com.fukuiteams.app.ui.components.TeamSelectorRow
import com.fukuiteams.app.ui.components.MastheadTopBar
import com.fukuiteams.app.ui.components.ScrollJumpButtons
import com.fukuiteams.app.ui.components.ScrollJumpBottomPadding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.fukuiteams.app.data.GameForecast
import com.fukuiteams.app.data.entryTime
import com.fukuiteams.app.data.WeatherRepository
import com.fukuiteams.app.data.buildResultContext
import com.fukuiteams.app.data.countdownLabel
import com.fukuiteams.app.data.daysUntil
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
    onOpenRadar: (Team?) -> Unit = {},
    // 特集「掲示板の話題」を押したとき:掲示板タブを開く(投稿の番号があればその投稿を開く)
    onOpenBbs: (Int?) -> Unit = {}
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
    // 「次の試合」の天気予報(試合の3日前から。入場の時刻と試合開始の時刻)
    var forecasts by remember { mutableStateOf<Map<String, GameForecast>>(emptyMap()) }
    // 3チームの選手の誕生日(一面の「誕生日」欄)
    var birthdays by remember { mutableStateOf<List<PlayerBirthday>>(emptyList()) }
    // 一面の「特集」(試合が無い日の読み物)に使う、選手の成績と順位
    var players by remember { mutableStateOf<Map<String, TeamPlayers>>(emptyMap()) }
    var standings by remember { mutableStateOf<Map<String, LeagueStandings>>(emptyMap()) }
    // 観戦の記録(特集「あなたの観戦記録」に使う)
    val logPrefs by LocalContext.current.gameLogDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
    // 特集「掲示板の話題」(メニューでオフにできる)
    var bbsBuzz by remember { mutableStateOf<BbsBuzz?>(null) }
    val bbsSettings by remember { BbsPrefs.flow(appContext) }.collectAsState<BbsSettings, BbsSettings?>(initial = null)

    // アプリのすべての情報(日程・結果・招待・ニュース・展望・選手・順位・試合情報ページなど)を読み直す。
    // 選手や順位はこの画面では使わないが、ほかのタブに切り替えたときに新しい内容を出せるよう一緒に読み直す
    suspend fun refreshAll() {
        val d = DataRefresher.refreshAll(appContext)
        autoResults = d.results
        invitationsResult = d.invitations
        newsResult = d.news
        previews = d.previews
        dataStatus = d.status
        birthdays = BirthdaysRepository.fetch()
        players = d.players
        standings = d.standings
        // 今日の試合の天気
        weathers = GamesRepository.games.filter { it.isToday() }
            .mapNotNull { g -> WeatherRepository.forGame(g)?.let { g.id to it } }
            .toMap()
        // 3日以内に試合があれば、その天気予報(ブローウィンズのホームゲームは入場の時刻も)
        forecasts = GamesRepository.games
            .filter { g -> g.isUpcoming() && (g.daysUntil() ?: 99L) in 0L..3L }
            .mapNotNull { g ->
                WeatherRepository.preGameForecast(g, GameLpRepository.latest[g.id]?.entryTime)?.let { g.id to it }
            }
            .toMap()
    }

    LaunchedEffect(Unit) { refreshAll() }

    // 掲示板の話題は、ほかの情報の読み込みを待たせないよう別に数える(30分以内に数えた分があればそれを使う)。
    // 選手の名字は「よく出た言葉」に選手名を拾うため。メニューでオフにしたときは数えない
    val bbsFeatureOn = bbsSettings?.featureEnabled
    LaunchedEffect(players, bbsFeatureOn) {
        if (bbsFeatureOn == true) bbsBuzz = BbsBuzzRepository.fetch(appContext, blowindsSurnames(players))
    }

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

    // 一覧のスクロール位置(右下の「一番上へ」「一番下へ」ボタンで使う)
    val listState = rememberLazyListState()

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
        floatingActionButton = { ScrollJumpButtons(listState) },
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
            state = listState,
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

            // 選手の誕生日(今日が誕生日の選手と、7日以内に誕生日の選手)。選んでいるチームの選手だけ
            val teamBirthdays = birthdays.filter { selectedTeam == null || it.team == selectedTeam }
            val bdToday = BirthdaysRepository.today()
            val soon = teamBirthdays.filter { it.daysUntilNext(bdToday) in 0..7 }
                .sortedWith(compareBy<PlayerBirthday> { it.daysUntilNext(bdToday) }.thenBy { it.team?.ordinal ?: 9 })
            if (soon.isNotEmpty()) {
                item { BirthdayCard(soon, bdToday) }
            }

            item {
                // 一面トップ:選択中のチーム(「すべて」なら全チーム)の最新の試合結果
                val latest = GamesRepository.games
                    .filter { (selectedTeam == null || it.team == selectedTeam) && autoResults.containsKey(it.id) }
                    .maxByOrNull { it.sortKey }
                if (latest != null && isFreshResult(latest)) {
                    // 試合の当日・翌日は、その結果を「速報」として大きく出す
                    LatestResultHero(latest, autoResults.getValue(latest.id), autoResults, onClick = { onOpenGame(latest.id) })
                } else {
                    // 試合の無い日は、日替わりの「特集」(注目選手・数字で見る・決戦まで・順位・ニュースなど)を一面トップに出し、
                    // 前回の結果は1行だけにする
                    val teams = selectedTeam?.let { listOf(it) } ?: Team.values().toList()
                    val onSiteIds = remember(logPrefs, GamesRepository.games) {
                        GamesRepository.games.filter { recordedWatchMethod(it.id, logPrefs) == WatchMethod.ON_SITE }.map { it.id }.toSet()
                    }
                    val buzzForFeature = bbsBuzz.takeIf { bbsSettings?.featureEnabled != false }
                    val stories = remember(teams, autoResults, previews, players, standings, newsResult, invitationsResult, GamesRepository.games, birthdays, onSiteIds, buzzForFeature) {
                        buildFeatureStories(
                            teams = teams,
                            games = GamesRepository.games,
                            results = autoResults,
                            previews = previews,
                            players = players,
                            standings = standings,
                            news = (newsResult as? AlertsResult.Success)?.items.orEmpty(),
                            invites = (invitationsResult as? AlertsResult.Success)?.items.orEmpty(),
                            birthdays = birthdays,
                            onSiteIds = onSiteIds,
                            bbsBuzz = buzzForFeature
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (stories.isNotEmpty()) {
                            FeatureHero(
                                stories = stories,
                                onOpenGame = onOpenGame,
                                onOpenTopics = { onOpenRadar(selectedTeam) },
                                onOpenBbs = onOpenBbs
                            )
                        }
                        if (latest != null) {
                            PreviousResultLine(latest, autoResults.getValue(latest.id), onClick = { onOpenGame(latest.id) })
                        }
                    }
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
                    forecasts = forecasts,
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

            // 右下のボタンに最後の行が隠れないよう、下に余白を空ける
            item { Spacer(modifier = Modifier.height(ScrollJumpBottomPadding)) }
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
                // パブリックビューイング(PV)がある試合は「PV」の札
                if (game.publicViewings().isNotEmpty()) PublicViewingBadge()
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
private fun LatestResultHero(
    game: Game,
    result: RemoteGameResult,
    allResults: Map<String, RemoteGameResult>,
    onClick: () -> Unit
) {
    // 連勝・連敗、前回対戦、今季の成績(見出しと記事に使う)
    val ctx = remember(game.id, allResults, GamesRepository.games) {
        buildResultContext(game, GamesRepository.games, allResults)
    }
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
        // ユナイテッド(屋外のサッカー)の試合だけ、その日の試合開始ごろの天気を記事に盛り込む
        var weather by remember(game.id) { mutableStateOf<MatchWeather?>(null) }
        if (game.team == Team.UNITED) {
            LaunchedEffect(game.id) { weather = WeatherRepository.forGame(game) }
        }
        val leadPrefs by context.gameLogDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
        val comment = recordedComment(game.id, leadPrefs)
        val resultLine = resultHeadline(game, result.myScore, result.opponentScore, outcome, ctx)
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
            resultLead(game, result, outcome, comment, ctx, weather),
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
    forecasts: Map<String, GameForecast>,
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
                    forecast = forecasts[game.id],
                    results = autoResults,
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
    forecast: GameForecast?,
    results: Map<String, RemoteGameResult>,
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
        // この相手とのこれまでの対戦成績(アプリが記録している試合結果から)
        HeadToHeadBox(nextGame, results)
        // 天気予報(試合の3日前から。入場の時刻 → 試合開始の時刻の順)
        if (forecast != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, LineGray)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "天気予報(${(forecast.start ?: forecast.entry)?.placeLabel ?: ""})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                if (forecast.entryTime != null && forecast.entry != null) {
                    ForecastLine("入場 ${forecast.entryTime}ごろ", forecast.entry)
                }
                forecast.start?.let { ForecastLine("試合開始 ${forecast.startTime}ごろ", it) }
            }
        }

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
        // パブリックビューイング(PV)があれば1件目の要点を出す(押すと試合の詳細で一覧が見られる)
        nextGame.publicViewings().firstOrNull()?.let { pv ->
            val count = nextGame.publicViewings().size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                PublicViewingBadge()
                Text(
                    "パブリックビューイングあり" + (if (count > 1) "(${count}件)" else "") +
                        pv.summary.takeIf { it.isNotBlank() && !pv.auto }?.let { ":$it" }.orEmpty() + " ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = NewsRed,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 天気予報の1行。例「入場 13:00ごろ  晴れ 24℃ 降水確率10%」 */
@Composable
private fun ForecastLine(label: String, w: MatchWeather) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall, color = InkSoft, maxLines = 1)
        Text(
            "${w.summary} ${w.temperature}℃" + (w.rainChance?.let { " 降水確率$it%" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
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

/**
 * 一面の「誕生日」欄。今日が誕生日の選手は顔写真付きで大きく、7日以内の選手は1行ずつ小さく出す。
 */
@Composable
private fun BirthdayCard(players: List<PlayerBirthday>, today: java.time.LocalDate) {
    val todays = players.filter { it.isBirthdayOn(today) }
    val upcoming = players.filterNot { it.isBirthdayOn(today) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, NewsRed)
            .background(Paper)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SectionLabel(if (todays.isNotEmpty()) "今日は誕生日" else "もうすぐ誕生日", red = true)
        todays.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteThumbnail(p.photo, width = 48.dp, height = 60.dp, alignTop = true, zoomCaption = p.label)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        p.team?.displayName ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = p.team?.color ?: InkSoft
                    )
                    Headline("${p.label}選手", fontSize = 19)
                    Text(
                        "${p.ageOn(today)}歳の誕生日です。おめでとうございます!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink
                    )
                }
            }
        }
        if (upcoming.isNotEmpty()) {
            if (todays.isNotEmpty()) ThinRule(color = LineGray)
            upcoming.forEach { p ->
                val d = today.plusDays(p.daysUntilNext(today).toLong())
                val week = "月火水木金土日"[d.dayOfWeek.value - 1]
                Text(
                    "${d.monthValue}/${d.dayOfMonth}($week) ${p.label}(${p.team?.let { teamShortName(it) } ?: ""}) ${p.ageOn(d)}歳に",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink,
                    maxLines = 2
                )
            }
        }
    }
}

private fun teamShortName(team: Team): String = when (team) {
    Team.BLOWINDS -> "ブローウィンズ"
    Team.RAC -> "丸岡RUCK"
    Team.UNITED -> "ユナイテッド"
}

/**
 * 「次の試合」の相手との対戦成績。例「対戦成績 2勝1敗」と、直近3試合の結果(日付・スコア・HOME/AWAY)。
 * 対戦が無いときは「今季(記録上)初対戦」とだけ出す。
 */
@Composable
private fun HeadToHeadBox(game: Game, results: Map<String, RemoteGameResult>) {
    val h2h = remember(game.id, results, GamesRepository.games) {
        com.fukuiteams.app.data.headToHead(game, GamesRepository.games, results)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LineGray)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (h2h == null) {
            Text(
                "${game.opponent}とは、アプリの記録では初対戦",
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft
            )
            return@Column
        }
        Text(
            "${game.opponent}との対戦成績 ${h2h.wins}勝${h2h.losses}敗" + (if (h2h.draws > 0) "${h2h.draws}分" else ""),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        h2h.meetings.take(3).forEach { (g, r) ->
            val mark = when {
                r.myScore > r.opponentScore -> "○"
                r.myScore < r.opponentScore -> "●"
                else -> "△"
            }
            Text(
                "$mark ${r.myScore}-${r.opponentScore}  ${g.dateLabel}(${g.dayOfWeek}) ${if (g.isHome) "HOME" else "AWAY"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (mark == "○") NewsRed else Ink
            )
        }
        Text(
            "※このアプリが記録している試合(今季など)から数えています",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft
        )
    }
}

/**
 * 一面トップの「特集」。日替わりの記事を1本ずつ大きく出し、指で左右にスライドするか「ほかの記事」で切り替えられる。
 * 記事を押すと、その試合・ページ・トピック・掲示板を開く。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FeatureHero(
    stories: List<FeatureStory>,
    onOpenGame: (String) -> Unit,
    onOpenTopics: () -> Unit,
    onOpenBbs: (Int?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 最後の記事から最初の記事へ(またはその逆へ)そのままスライドできるよう、ページを多めに用意して真ん中から始める。
    // 何本目の記事かは「ページ番号 ÷ 記事の本数 の余り」で決める
    val loop = stories.size > 1
    val pageCount = if (loop) stories.size * LOOP_ROUNDS else stories.size
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = if (loop) stories.size * (LOOP_ROUNDS / 2) else 0,
        pageCount = { pageCount }
    )
    val currentIndex = pagerState.currentPage.mod(stories.size)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = 16.dp,
            verticalAlignment = Alignment.Top
        ) { page ->
            val story = stories[page.mod(stories.size)]
            val clickable = story.gameId != null || story.url != null || story.openInvites || story.openBbs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (clickable) Modifier.clickable {
                            when {
                                story.gameId != null -> onOpenGame(story.gameId)
                                story.url != null -> openUrl(context, story.url)
                                story.openInvites -> onOpenTopics()
                                story.openBbs -> onOpenBbs(story.bbsPostNo)
                            }
                        } else Modifier
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 「特集」の大きな赤い札と、記事の種類・チーム名
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Headline(
                        "特集",
                        fontSize = 16,
                        color = White,
                        modifier = Modifier.background(NewsRed).padding(horizontal = 10.dp, vertical = 1.dp)
                    )
                    Text(
                        story.kicker,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NewsRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    story.team?.let {
                        Text(it.displayName, style = MaterialTheme.typography.labelMedium, color = it.color, maxLines = 1)
                    }
                }
                // 見出しを全幅で先に出し、その下で 本文(左)と縦長の写真(右)を並べる(新聞の囲み記事の形)
                Headline(story.headline, fontSize = 21)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (story.bigNumber != null) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Headline(story.bigNumber, fontSize = 40, color = NewsRed)
                                story.bigUnit?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Ink,
                                        modifier = Modifier.padding(start = 3.dp, bottom = 6.dp)
                                    )
                                }
                            }
                        }
                        Text(story.body, style = MaterialTheme.typography.bodyLarge, color = Ink)
                    }
                    FeaturePhoto(story)
                }
            }
        }
        if (stories.size > 1) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 何本目の記事か(点)。今の記事の点だけ赤く大きくする
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    stories.indices.forEach { i ->
                        val current = i == currentIndex
                        Box(
                            modifier = Modifier
                                .size(if (current) 10.dp else 7.dp)
                                .background(if (current) NewsRed else LineGray, androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                    Text(
                        "${currentIndex + 1}/${stories.size}・左右にスライド",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Text(
                    "ほかの記事 ›",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Accent,
                    modifier = Modifier
                        .clickable {
                            scope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pageCount - 1))
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
        DoubleRule(modifier = Modifier.padding(top = 2.dp))
    }
}

/** 特集を左右にループさせるため、記事の本数の何倍のページを用意するか(端に着くことは実際には無い) */
private const val LOOP_ROUNDS = 1000

/** 一面の特集の写真の枠(縦長。顔が切れないように) */
private val FEATURE_PHOTO_WIDTH = 150.dp
private val FEATURE_PHOTO_HEIGHT = 200.dp

/**
 * 一面の特集の縦長の写真。どの記事にも画像を出し、枠の中に余白を作らない。
 * ・選手の写真は顔が切れないよう上を残す。切り抜き写真の周りの透明な余白は切り取り、
 *   それでも見える透明な部分の後ろはチームカラーにする。ニュースの横長の画像は左右を切って枠に合わせる
 * ・代わりに付けた選手の写真には、下に選手名を重ねる
 * ・写真が無い記事・写真を読み込めないときは、チームカラーの札(記事の種類・チームの頭文字・チーム名)にする
 * ・写真を押しても拡大はせず、記事全体と同じく、その試合・記事・掲示板などを開く
 */
@Composable
private fun FeaturePhoto(story: FeatureStory) {
    val accent = story.team?.color ?: Ink
    val newsImage = story.photo.isNotBlank() && story.photoCaption == null && story.url != null && story.bigNumber == null
    Box(
        modifier = Modifier
            .size(FEATURE_PHOTO_WIDTH, FEATURE_PHOTO_HEIGHT)
            .background(accent)
    ) {
        // 写真を読み込むまで(読み込めないとき)は、色の札を出しておく
        FeatureGraphic(story, modifier = Modifier.fillMaxSize())
        if (story.photo.isNotBlank()) {
            RemoteThumbnail(
                story.photo,
                width = FEATURE_PHOTO_WIDTH,
                height = FEATURE_PHOTO_HEIGHT,
                alignTop = !newsImage,
                trimTransparent = true,
                backgroundColor = accent,
                framed = false
            )
            story.photoCaption?.let { caption ->
                Text(
                    caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Ink.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/** 写真が無いときの色の札:記事の種類・チームの頭文字・チーム名 */
@Composable
private fun FeatureGraphic(story: FeatureStory, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            story.kicker,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = White,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
        Headline(story.team?.initial ?: "特集", fontSize = if (story.team != null) 64 else 34, color = White)
        Text(
            story.team?.displayName ?: "3チーム",
            style = MaterialTheme.typography.labelSmall,
            color = White.copy(alpha = 0.85f),
            maxLines = 1
        )
    }
}

/**
 * ブローウィンズの選手の名字(特集「掲示板の話題」で、よく出た言葉に選手名を拾うため)。
 * 「藤永 佳昭」→「藤永」、「ヒシグバータル・オーギル」→「ヒシグバータル」「オーギル」
 */
private fun blowindsSurnames(players: Map<String, TeamPlayers>): List<String> =
    players[Team.BLOWINDS.name]?.players.orEmpty().flatMap { p ->
        val name = p.name.trim()
        when {
            name.contains(' ') || name.contains('　') -> listOf(name.split(' ', '　').first())
            name.contains('・') -> name.split('・')
            else -> listOf(name)
        }
    }.map { it.trim() }.filter { it.length >= 2 }.distinct()

/** 試合の無い日に出す「前回の結果」の1行。押すとその試合を開く。 */
@Composable
private fun PreviousResultLine(game: Game, result: RemoteGameResult, onClick: () -> Unit) {
    val mark = when {
        result.myScore > result.opponentScore -> "○"
        result.myScore < result.opponentScore -> "●"
        else -> "△"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LineGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("前回の結果", style = MaterialTheme.typography.labelMedium, color = InkSoft)
        Text(
            "${game.team.displayName} ${game.dateLabel.split("/").drop(1).joinToString("/")}(${game.dayOfWeek}) ${game.opponent}戦 $mark ${result.myScore}-${result.opponentScore}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (mark == "○") NewsRed else Ink,
            modifier = Modifier.weight(1f),
            maxLines = 2
        )
        Text("›", style = MaterialTheme.typography.titleMedium, color = InkSoft)
    }
}
