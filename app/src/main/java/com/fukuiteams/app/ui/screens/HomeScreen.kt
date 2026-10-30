package com.fukuiteams.app.ui.screens

import com.fukuiteams.app.data.PhotoRef
import com.fukuiteams.app.data.TeamPhotos
import com.fukuiteams.app.ui.components.FallbackPhoto
import com.fukuiteams.app.ui.components.PlayerPhotoRow

import com.fukuiteams.app.model.shortName
import com.fukuiteams.app.ui.components.HomeAwayLabel
import com.fukuiteams.app.ui.components.openExternalUrl
import com.fukuiteams.app.data.isGoing
import com.fukuiteams.app.ui.components.GoingBadge
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
import com.fukuiteams.app.data.FeatureSeen
import com.fukuiteams.app.ui.components.FeaturePlayerCard
import com.fukuiteams.app.ui.components.FeatureDataGraphic
import com.fukuiteams.app.ui.components.SportLines
import com.fukuiteams.app.ui.components.hasFeatureDataGraphic
import com.fukuiteams.app.ui.components.numberFromCaption
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
import com.fukuiteams.app.model.isMainTeamGame
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
    // 4チームの選手の誕生日(一面の「誕生日」欄)
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
        weathers = mainGames().filter { it.isToday() }
            .mapNotNull { g -> WeatherRepository.forGame(g)?.let { g.id to it } }
            .toMap()
        // 3日以内に試合があれば、その天気予報(ブローウィンズのホームゲームは入場の時刻も)
        forecasts = mainGames()
            .filter { g -> g.isUpcoming() && (g.daysUntil() ?: 99L) in 0L..3L }
            .mapNotNull { g ->
                WeatherRepository.preGameForecast(g, GameLpRepository.latest[g.id]?.entryTime)?.let { g.id to it }
            }
            .toMap()
    }

    // 最初の読み込みが終わったか(特集の「見た記事」の記録を、読み込み途中の少ない記事で書きかえないため)
    var loadedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        refreshAll()
        loadedOnce = true
    }

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
            val todayGames = mainGames()
                .filter { (selectedTeam == null || it.team == selectedTeam) && it.isToday() && !autoResults.containsKey(it.id) }
                .sortedBy { it.sortKey }
            if (todayGames.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        todayGames.forEach { g ->
                            MatchDayCard(
                                g, weathers[g.id], forecasts[g.id], GameLpRepository.latest[g.id],
                                results = autoResults, preview = previews[g.id],
                                multi = todayGames.size > 1, onOpen = { onOpenGame(g.id) }
                            )
                        }
                    }
                }
            }

            // コラボ企画など特別な日が45日以内にあれば、特集枠を出す(選んでいるチームが関係するときだけ)
            val special = upcomingSpecialDay()
            if (special != null && (selectedTeam == null || selectedTeam in special.teams)) {
                item { SpecialDayBanner(special, onOpenGame = { g -> onOpenGame(g.id) }) }
            }

            // 一面トップ:選択中のチーム(「すべて」なら全チーム)の最新の試合結果
            val latest = mainGames()
                .filter { (selectedTeam == null || it.team == selectedTeam) && autoResults.containsKey(it.id) }
                .maxByOrNull { it.sortKey }
            // 速報に出している試合(下の「各チームの近況」では短く出す)
            val heroId = latest?.takeIf { isFreshResult(it) }?.id
            item {
                if (latest != null && isFreshResult(latest)) {
                    // 試合の当日・翌日は、その結果を「速報」として大きく出す
                    LatestResultHero(latest, autoResults.getValue(latest.id), autoResults, onClick = { onOpenGame(latest.id) })
                } else {
                    // 試合の無い日は「特集」(注目選手・数字で見る・決戦まで・順位・ニュースなど)を一面トップに出す。
                    // 記事はアプリを起動するたびに選び直し、前に見た記事は出さない(FeatureSeen)
                    val teams = selectedTeam?.let { listOf(it) } ?: Team.values().toList()
                    val onSiteIds = remember(logPrefs, GamesRepository.games) {
                        GamesRepository.games.filter { recordedWatchMethod(it.id, logPrefs) == WatchMethod.ON_SITE }.map { it.id }.toSet()
                    }
                    val buzzForFeature = bbsBuzz.takeIf { bbsSettings?.featureEnabled != false }
                    // 過去の記録(history.json)を読み終えたら、過去の話の記事も作り直す
                    val historyData = com.fukuiteams.app.data.HistoryRepository.data
                    val seenAtOpen = remember { FeatureSeen.seenAtOpen(appContext) }
                    val stories = remember(teams, autoResults, previews, players, standings, newsResult, invitationsResult, GamesRepository.games, birthdays, onSiteIds, buzzForFeature, historyData) {
                        buildFeatureStories(
                            teams = teams,
                            games = mainGames(),
                            results = autoResults,
                            previews = previews,
                            players = players,
                            standings = standings,
                            news = (newsResult as? AlertsResult.Success)?.items.orEmpty(),
                            invites = (invitationsResult as? AlertsResult.Success)?.items.orEmpty(),
                            birthdays = birthdays,
                            onSiteIds = onSiteIds,
                            bbsBuzz = buzzForFeature,
                            // アプリを起動するたびに選び直し、前に見た記事は出さない
                            seedOverride = FeatureSeen.seed,
                            seen = seenAtOpen,
                            // 写真の無い記事のうち、図を作れる記事と選手の記事は、代わりの写真ではなく図・選手カードにする
                            keepWithoutPhoto = { s -> hasFeatureDataGraphic(s) || numberFromCaption(s.photoCaption).isNotEmpty() }
                        )
                    }
                    if (loadedOnce) LaunchedEffect(stories) { FeatureSeen.noteStories(appContext, stories) }
                    // 前回の結果は、下の「各チームの近況」にまとめた
                    if (stories.isNotEmpty()) {
                        FeatureHero(
                            stories = stories,
                            results = autoResults,
                            standings = standings,
                            onShown = { s -> FeatureSeen.markShown(appContext, s) },
                            onOpenGame = onOpenGame,
                            onOpenTopics = { onOpenRadar(selectedTeam) },
                            onOpenBbs = onOpenBbs
                        )
                    }
                }
            }

            // 節目達成(最近の試合で通算の節目に届いた選手。試合の日から2日まで)
            // (過去の記録 history.json を読み終えたら数え直す。無いときは欄ごと出さない)
            val achToday = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"))
            val achievements = (selectedTeam?.let { listOf(it) } ?: Team.values().toList())
                .flatMap { com.fukuiteams.app.data.recentAchievements(it, achToday) }
            if (achievements.isNotEmpty()) {
                item { AchievementCard(achievements) }
            }

            // 一面の「次の試合」。次の試合の日にほかのチームの試合もあれば、同じ日の試合をすべて並べる
            // (並びは開始時刻順。「時間未定」の試合は後ろ)。本日の試合は上に出しているので、ここには出さない
            val todayIds = todayGames.map { it.id }.toSet()
            val upcoming = mainGames()
                .filter { (selectedTeam == null || it.team == selectedTeam) && it.isUpcoming() && it.id !in todayIds }
            val firstDay = upcoming.minByOrNull { it.sortKey }?.sortKey?.take(8)
            val nextGames = upcoming
                .filter { firstDay != null && it.sortKey.take(8) == firstDay }
                .sortedBy { g -> if (Regex("""\d{1,2}:\d{2}""").matches(g.timeLabel.trim())) g.sortKey else g.sortKey.take(8) + "-9999" }
            // 本日の試合があって、そのあとの試合がまだ無いときは「次の試合」の欄を出さない
            if (nextGames.isNotEmpty() || todayIds.isEmpty()) {
                item {
                    TwoColumnFront(
                        nextGames = nextGames,
                        hasOpenInvite = { g -> g.hasMatchingInvite(openInvites) },
                        autoResults = autoResults,
                        previews = previews,
                        lps = GameLpRepository.latest,
                        forecasts = forecasts,
                        onOpenGame = onOpenGame
                    )
                }
            }

            // 各チームの近況(前回の結果と次の試合)。上に詳しく出している試合は「(上に掲載)」と短く出す
            val shownAbove = todayIds + nextGames.map { it.id }
            val digestNextIds = teamDigestNextIds(selectedTeam, autoResults, shownAbove)
            item {
                TeamsDigest(
                    selectedTeam = selectedTeam,
                    autoResults = autoResults,
                    openInvites = openInvites,
                    heroId = heroId,
                    shownAbove = shownAbove,
                    onOpenGame = onOpenGame
                )
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("その後の日程")
                    // 特定のチームを選んだときはそのチームの直近6件、「すべて」のときは全体の直近6件を表示。
                    // 試合スケジュールの一覧は「試合」タブにまとめてあるので、ここでは概要だけ。
                    // 上の「本日の試合」「次の試合」「各チームの近況」に出した試合は除く(同じ試合を2回出さない)
                    val listed = shownAbove + digestNextIds
                    val games = upcoming.filter { it.id !in listed }.sortedBy { it.sortKey }.take(6)
                    if (games.isEmpty()) {
                        Text(
                            if (upcoming.isNotEmpty()) "このあとの予定は、まだ発表されていません"
                            else "このチームの試合データはまだ準備できていません",
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
                    // 更新は右上のボタンか、画面を下に引っ張って行う(ニュース欄だけの更新ボタンは置かない)
                    SectionLabel("ニュース")
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
private fun MatchDayCard(
    game: Game,
    weather: MatchWeather?,
    forecast: GameForecast?,
    lp: GameLp?,
    /** 対戦成績を数えるための試合結果 */
    results: Map<String, RemoteGameResult> = emptyMap(),
    /** データで見る展望(無ければ null) */
    preview: GamePreview? = null,
    /** 同じ日に2試合以上あるとき。1試合ずつチームの色の枠・上の帯・うっすらチームの色の背景で、区切りが分かるようにする */
    multi: Boolean = false,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, if (multi) game.team.color else NewsRed)
            .background(if (multi) game.team.color.copy(alpha = 0.07f) else Paper)
            .clickable(onClick = onOpen)
    ) {
    if (multi) Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(game.team.color))
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
        val weatherColumns = forecast?.toColumns().orEmpty().ifEmpty {
            listOfNotNull(weather?.let { ForecastColumn("試合開始", game.timeLabel, it) })
        }
        if (weatherColumns.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, LineGray)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val place = forecast?.let { it.start ?: it.entry ?: it.before }?.placeLabel ?: weather?.placeLabel.orEmpty()
                WeatherForecastHeading("天気予報($place)", weatherColumns)
                ForecastTable(weatherColumns)
            }
            val rainExpected = weatherColumns.any { column ->
                column.w.precipitationMm?.let { it >= 0.1 } == true || column.w.summary.contains("雨") || column.w.summary.contains("雷")
            }
            val snowExpected = weatherColumns.any { column ->
                column.w.snowfallCm?.let { it >= 0.1 } == true || column.w.snowDepthCm?.let { it >= 1 } == true || column.w.summary.contains("雪")
            }
            if (rainExpected || snowExpected) {
                Text(
                    listOfNotNull("雨の予報あり".takeIf { rainExpected }, "雪の予報あり".takeIf { snowExpected }).joinToString("・"),
                    style = MaterialTheme.typography.labelMedium,
                    color = NewsRed,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                "天気予報データはありません（会場・時刻未確定、または取得できません）",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        }
        Text("チケット:${game.ticketStatus}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        // ブローウィンズのホームゲームは、公式の試合情報ページの開場時刻・当日スケジュール・イベントも出す
        if (lp != null) {
            ThinRule(modifier = Modifier.padding(vertical = 4.dp))
            GameLpSection(lp, startExpanded = true)
        }
        // 対戦成績と展望(「次の試合」の欄には本日の試合を出さないので、ここに出す)
        HeadToHeadBox(game, results)
        if (preview != null) PreviewSection(game, preview)
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
                // チェックインした試合は「行く予定」の札
                val cardLogPrefs by LocalContext.current.gameLogDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
                if (isGoing(game.id, cardLogPrefs)) GoingBadge()
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
            // 記事の画像(無い・読めないときは、そのチームの選手の写真を日替わりで)を、チームの頭文字の上に重ねる
            val candidates = remember(news.id, news.image, TeamPhotos.own) {
                listOfNotNull(news.image.takeIf { it.isNotBlank() }?.let { PhotoRef(it, "") }) +
                    TeamPhotos.rotate(TeamPhotos.forTeam(team), news.id).take(6)
            }
            // 選手の写真のときは、名前を写真の下のほうに重ねる
            FallbackPhoto(candidates, width = 48.dp, height = 48.dp, nameLabel = com.fukuiteams.app.ui.components.PhotoNameLabel.OVERLAY)
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

/** URLをブラウザなどで開く(共通の openExternalUrl を使う) */
private fun openUrl(context: Context, url: String) = openExternalUrl(context, url)
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
        buildResultContext(game, mainGames(), allResults)
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
                // 枠を画像の形に合わせる(横長は横幅いっぱい、縦長は高さ260dpで中央。左右の灰色の帯を出さない)
                height = 260.dp,
                fitFrameToImage = true
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
                val block: @Composable () -> Unit = {
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
                if (nextGames.size > 1) {
                    // 同じ日に2試合以上あるときは、1試合ずつチームの色の枠と、うっすらチームの色の背景で囲む
                    // (以前は区切りの線だけで、どこからどこまでが1試合か分かりにくかったため)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (index > 0) 10.dp else 4.dp)
                            .border(1.5.dp, game.team.color)
                            .background(game.team.color.copy(alpha = 0.07f))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(game.team.color))
                        Box(modifier = Modifier.padding(10.dp)) { block() }
                    }
                } else {
                    block()
                }
            }
        }

        // 他チームの近況は、下の「各チームの近況」(TeamsDigest)にまとめた
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
            // チェックインした試合は「行く予定」の札
            val nextLogPrefs by LocalContext.current.gameLogDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
            if (isGoing(nextGame.id, nextLogPrefs)) GoingBadge()
            // チーム名は残りの幅に収め、入りきらないときは「…」で切る
            // (「行く予定」の札が付くと幅が足りなくなり、右端の「あと○日」が1文字ずつ縦に折り返していたため)
            Text(
                nextGame.team.displayName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = nextGame.team.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 「あと○日」は赤い枠と赤い文字(塗りつぶしにすると、赤地に白文字の HOME の札と見分けにくいため)。折り返さず1行で出す
            nextGame.countdownLabel()?.let { label ->
                Text(
                    label,
                    modifier = Modifier
                        .border(1.5.dp, NewsRed)
                        .background(Paper)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = NewsRed,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
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
        // 福井側の選手の写真(日替わりで4人)
        PlayerPhotoRow(
            TeamPhotos.rotate(TeamPhotos.forTeam(nextGame.team), nextGame.id),
            count = 4,
            photoWidth = 56.dp,
            photoHeight = 70.dp
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
                WeatherForecastHeading(
                    "天気予報(${(forecast.start ?: forecast.entry)?.placeLabel ?: ""})",
                    forecast.toColumns()
                )
                // 時刻ごとに列を分けた表(入場の1時間前 → 入場 → 試合開始)。行は天気・気温・降水確率・降水量など
                ForecastTable(forecast.toColumns())
            }
        }

        // ブローウィンズのホームゲームは、公式の試合情報ページの開場時刻・当日スケジュール・イベントを出す
        // (スケジュールとイベントは押すと開く。試合当日は上の「本日の試合」に出すので、ここでは出さない)
        if (lp != null && !nextGame.isToday()) {
            ThinRule(modifier = Modifier.padding(vertical = 4.dp))
            GameLpSection(lp, startExpanded = false)
        }

        if (preview != null) PreviewSection(nextGame, preview)
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

/** データで見る展望と相手の注目選手(「次の試合」と「本日の試合」で使う) */
@Composable
private fun PreviewSection(game: Game, preview: GamePreview) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DoubleRule(modifier = Modifier.padding(vertical = 6.dp))
            SectionLabel("データで見る展望")
            PreviewTable(preview)
            if (preview.summary.isNotBlank()) {
                Text(preview.summary, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }

            ThinRule(modifier = Modifier.padding(vertical = 6.dp))
            SectionLabel("相手の注目選手")
            val oppPhotos = TeamPhotos.forOpponent(game)
            if (preview.keyPlayers.isNotEmpty()) {
                preview.keyPlayers.forEach { KeyPlayerRow(it, TeamPhotos.findByName(oppPhotos, it.name)) }
                // 注目選手のほかの相手の選手の写真
                val others = oppPhotos.filter { p -> preview.keyPlayers.none { TeamPhotos.findByName(listOf(p), it.name) != null } }
                PlayerPhotoRow(others, count = 4, photoWidth = 52.dp, photoHeight = 65.dp)
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
}

/** 天気予報の表の1列(見出し・時刻・予報) */
private data class ForecastColumn(val title: String, val time: String, val w: MatchWeather)

/** 試合前予報を、本日の試合カードと次の試合カードで共通の表形式にする。 */
private fun GameForecast.toColumns(): List<ForecastColumn> = listOfNotNull(
    before?.takeIf { beforeTime != null }?.let {
        ForecastColumn(if (beforeLabel.startsWith("入場")) "入場1時間前" else "開始1時間前", beforeTime.orEmpty(), it)
    },
    entry?.takeIf { entryTime != null }?.let { ForecastColumn("入場", entryTime.orEmpty(), it) },
    start?.let { ForecastColumn("試合開始", startTime, it) }
)

/** 天気予報の見出しとアプリ取得時刻を同じ行に表示する。 */
@Composable
private fun WeatherForecastHeading(title: String, cols: List<ForecastColumn>) {
    val retrievedAt = cols.maxOf { it.w.retrievedAt }
    val fetched = retrievedAt.atZone(java.time.ZoneId.of("Asia/Tokyo"))
        .format(java.time.format.DateTimeFormatter.ofPattern("M/d HH:mm"))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "取得 $fetched",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * 天気予報の表。列が時刻(入場の1時間前・入場・試合開始)、行が 天気・気温・降水確率・降水量(・降雪・積雪)。
 * 降水量・降雪・積雪は、どれかの時刻で降る(積もっている)ときだけ行を出す
 */
@Composable
private fun ForecastTable(cols: List<ForecastColumn>) {
    if (cols.isEmpty()) return
    fun mm(v: Double?) = v?.takeIf { it >= 0.1 }?.let { "%.1fmm".format(it) } ?: "0"
    fun cm(v: Double?) = v?.takeIf { it >= 0.1 }?.let { if (it >= 1) "${Math.round(it)}cm" else "%.1fcm".format(it) } ?: "0"
    val rows = buildList<Pair<String, (MatchWeather) -> String>> {
        add("天気" to { w: MatchWeather -> w.summary })
        add("気温" to { w: MatchWeather -> "${w.temperature}℃" })
        if (cols.any { it.w.rainChance != null }) add("降水確率" to { w: MatchWeather -> w.rainChance?.let { "$it%" } ?: "-" })
        if (cols.any { (it.w.precipitationMm ?: 0.0) >= 0.1 }) add("降水量(1時間)" to { w: MatchWeather -> mm(w.precipitationMm) })
        if (cols.any { (it.w.snowfallCm ?: 0.0) >= 0.1 }) add("降雪(1時間)" to { w: MatchWeather -> cm(w.snowfallCm) })
        if (cols.any { (it.w.snowDepthCm ?: 0) >= 1 }) add("積雪" to { w: MatchWeather -> w.snowDepthCm?.takeIf { it >= 1 }?.let { "${it}cm" } ?: "0" })
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        // 見出しの行(時刻)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Spacer(modifier = Modifier.weight(1.1f))
            cols.forEach { c ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(c.title, style = MaterialTheme.typography.labelSmall, color = InkSoft, maxLines = 1, softWrap = false)
                    Text(c.time, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                }
            }
        }
        ThinRule(color = LineGray, modifier = Modifier.padding(vertical = 3.dp))
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall, color = InkSoft, maxLines = 1)
                cols.forEach { c ->
                    Text(
                        value(c.w),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 天気予報の1行。例「入場 13:00ごろ  晴れ 24℃ 降水確率10%」。
 * 雨・雪が降る(積もっている)ときは、その下に降水量・降雪量・積雪を出す(例「1時間に2.5mm・積雪15cm」)
 */
@Composable
private fun ForecastLine(label: String, w: MatchWeather) {
    Row(verticalAlignment = Alignment.Top) {
        // 項目名は2行まで(文字を大きくしている画面や「入場の1時間前」で、時刻の端が切れないように)
        Text(label, modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall, color = InkSoft, maxLines = 2)
        Column {
            Text(
                "${w.summary} ${w.temperature}℃" + (w.rainChance?.let { " 降水確率$it%" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            if (w.amountLabel.isNotBlank()) {
                Text(w.amountLabel, style = MaterialTheme.typography.bodySmall, color = Ink)
            }
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
private fun KeyPlayerRow(player: KeyPlayer, photo: PhotoRef? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 2.dp)) {
        // 相手の選手の写真が分かれば左に出す
        if (photo != null) RemoteThumbnail(photo.url, width = 44.dp, height = 55.dp, alignTop = true, zoomCaption = photo.label)
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

/** 「各チームの近況」に次の試合を詳しく出すチームの、その試合のID(下の「その後の日程」から除くため) */
private fun teamDigestNextIds(selectedTeam: Team?, autoResults: Map<String, RemoteGameResult>, shownAbove: Set<String>): Set<String> =
    digestOrder(selectedTeam).mapNotNull { team ->
        mainGames().filter { it.team == team && it.isUpcoming() && !autoResults.containsKey(it.id) }.minByOrNull { it.sortKey }?.id
    }.filter { it !in shownAbove }.toSet()

/** 近況に並べるチームの順(上で選んだチームを先頭に) */
private fun digestOrder(selectedTeam: Team?): List<Team> =
    if (selectedTeam != null) listOf(selectedTeam) + Team.values().filter { it != selectedTeam } else Team.values().toList()

/**
 * 「各チームの近況」。4チームそれぞれの前回の結果と次の試合を1チーム1段で並べる
 * (以前の「前回の結果」の1行と「各チームの近況」をまとめたもの)。押すとその試合を開く。
 * 上に詳しく出している試合(速報 heroId・本日の試合/次の試合 shownAbove)は、日付と相手だけの短い行にして重ねない。
 */
@Composable
private fun TeamsDigest(
    selectedTeam: Team?,
    autoResults: Map<String, RemoteGameResult>,
    openInvites: List<RemoteInvitationAlert>,
    heroId: String?,
    shownAbove: Set<String>,
    onOpenGame: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, Ink)
            .background(Paper)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        SectionLabel("各チームの近況")
        digestOrder(selectedTeam).forEachIndexed { index, team ->
            val teamGames = mainGames().filter { it.team == team }
            val last = teamGames.filter { autoResults.containsKey(it.id) }.maxByOrNull { it.sortKey }
            val next = teamGames.filter { it.isUpcoming() && !autoResults.containsKey(it.id) }.minByOrNull { it.sortKey }
            if (index > 0) ThinRule(color = LineGray)
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(team.color, RoundedCornerShape(2.dp)))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(team.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = team.color)
                    // 前回の結果
                    if (last != null) {
                        val r = autoResults.getValue(last.id)
                        val outcome = when {
                            r.myScore > r.opponentScore -> GameOutcome.WIN
                            r.myScore < r.opponentScore -> GameOutcome.LOSE
                            else -> GameOutcome.DRAW
                        }
                        DigestLine("前回", onClick = { onOpenGame(last.id) }) {
                            Text(
                                "${resultMark(outcome)} ${r.myScore}-${r.opponentScore}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (outcome == GameOutcome.WIN) NewsRed else Ink,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                "${last.monthDayLabel()}(${last.dayOfWeek}) ${last.opponent}戦" + (if (last.id == heroId) "(上の速報)" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    // 次の試合
                    when {
                        next == null -> DigestLine("次", onClick = null) {
                            Text("次の試合は未発表", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                        }
                        next.id in shownAbove -> DigestLine("次", onClick = { onOpenGame(next.id) }) {
                            Text(
                                "${next.monthDayLabel()}(${next.dayOfWeek}) ${next.opponent}戦(上に掲載)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        else -> DigestLine("次", onClick = { onOpenGame(next.id) }) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "${next.monthDayLabel()}(${next.dayOfWeek}) ${next.timeLabel}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Ink,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    next.countdownLabel()?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = NewsRed, maxLines = 1, softWrap = false)
                                    }
                                    HomeAwayTag(isHome = next.isHome)
                                    if (next.hasMatchingInvite(openInvites)) {
                                        Text(
                                            "招待あり",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = White,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.background(NewsRed).padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    "vs ${next.opponent}・${next.venue}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 近況の1行(左に「前回」「次」の小さな札)。押すとその試合を開く */
@Composable
private fun DigestLine(label: String, onClick: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = InkSoft,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp).width(34.dp).border(1.dp, LineGray).padding(vertical = 1.dp)
        )
        content()
    }
}

/** 「10/31」の形の日付 */
private fun Game.monthDayLabel(): String = dateLabel.split("/").drop(1).joinToString("/")


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
                    "${d.monthValue}/${d.dayOfMonth}($week) ${p.label}(${p.team?.let { it.shortName } ?: ""}) ${p.ageOn(d)}歳に",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * 「次の試合」の相手との対戦成績。例「対戦成績 2勝1敗」と、直近3試合の結果(日付・スコア・HOME/AWAY)。
 * 対戦が無いときは「今季(記録上)初対戦」とだけ出す。
 */
@Composable
private fun HeadToHeadBox(game: Game, results: Map<String, RemoteGameResult>) {
    // 過去の記録(history.json)を読み終えたら数え直す
    val history = com.fukuiteams.app.data.HistoryRepository.data
    val h2h = remember(game.id, results, GamesRepository.games, history) {
        com.fukuiteams.app.data.headToHead(game, mainGames(), results)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LineGray)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // この試合で節目に届くかもしれない選手(過去の記録の通算から)
        val reach = remember(game.team, history) { com.fukuiteams.app.data.reachableNextGame(game.team) }
        if (h2h == null) {
            Text(
                "${game.opponent}とは、アプリの記録では初対戦",
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft
            )
            ReachableLines(reach)
            return@Column
        }
        Text(
            "${game.opponent}との対戦成績 ${h2h.wins}勝${h2h.losses}敗" + (if (h2h.draws > 0) "${h2h.draws}分" else ""),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        h2h.lines().take(3).forEach { line ->
            val ha = when (line.isHome) {
                true -> " HOME"
                false -> " AWAY"
                null -> ""
            }
            Text(
                "${line.scoreLabel.take(1)} ${line.scoreLabel.drop(1)}  ${line.dateLabel}$ha",
                style = MaterialTheme.typography.bodySmall,
                color = if (line.outcome == com.fukuiteams.app.data.GameOutcome.WIN) NewsRed else Ink
            )
        }
        // この相手に強い今の選手(過去の記録の、選手の試合ごとの記録から。その相手との1試合平均の得点が多い順)
        val strong = remember(game.team, game.opponent, history) {
            com.fukuiteams.app.data.strongAgainst(game.team, game.opponent)
        }
        if (strong.isNotEmpty()) {
            val unit = if (game.team == com.fukuiteams.app.model.Team.BLOWINDS) "点" else "得点"
            // 見出しの下に1人1行で並べる(1行に続けると、選手の名前の途中で折り返して読みにくいため)
            Text(
                "この相手に強い選手",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            strong.forEach { (p, sp) ->
                Text(
                    "・${p.name} 平均${"%.1f".format(sp.pointsPerGame)}$unit(${sp.games}試合・ふだんは${"%.1f".format(com.fukuiteams.app.data.overallPerGame(p))}$unit)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink
                )
            }
        }
        ReachableLines(reach)
        Text(
            if (h2h.usesHistory) "※公式サイトの過去の試合の記録と、このアプリが記録している試合から数えています(公式戦)"
            else "※このアプリが記録している試合(今季など)から数えています",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft
        )
    }
}

/** 「この試合で節目に届くかも」(対戦成績の欄の中) */
@Composable
private fun ReachableLines(list: List<com.fukuiteams.app.data.ReachableMilestone>) {
    if (list.isEmpty()) return
    Text("この試合で節目に届くかも", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = NewsRed)
    list.forEach { r ->
        Text("・${r.player.name} ${r.text}", style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

/** 一面の「節目達成」の欄(最近の試合で通算の節目に届いた選手) */
@Composable
private fun AchievementCard(list: List<com.fukuiteams.app.data.Achievement>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("節目達成")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Paper)
                .border(1.dp, Ink)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            list.forEach { a ->
                Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(a.team.color))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(a.team.displayName, style = MaterialTheme.typography.labelMedium, color = a.team.color)
                        Text("${a.player.name}、${a.text}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
                        val d = runCatching { java.time.LocalDate.parse(a.date) }.getOrNull()
                        Text(
                            "${d?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: a.date}の${a.opponent}戦で",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft
                        )
                    }
                }
            }
            Text("公式の試合の記録から数えた、福井のクラブでの通算の記録です(ほかのクラブでの試合は含みません)。", style = MaterialTheme.typography.labelSmall, color = InkSoft)
        }
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
    /** 図(直近の結果・順位・決戦まで)を作るための試合結果と順位 */
    results: Map<String, RemoteGameResult> = emptyMap(),
    standings: Map<String, LeagueStandings> = emptyMap(),
    /** 記事を画面に出したとき(「見た記事」の記録に使う) */
    onShown: (FeatureStory) -> Unit = {},
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
    // 画面に出した記事を「見た記事」として記録する(次に起動したときから出さない)
    LaunchedEffect(currentIndex, stories) { stories.getOrNull(currentIndex)?.let(onShown) }
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
                // 見出しを全幅で先に出し、その下は新聞のように、右の写真(絵)のまわりに本文を回り込ませる
                // (写真より本文が長いときは、写真の下から全幅で続ける。下に空きができないように)
                Headline(story.headline, fontSize = 21)
                FeatureWrapBody(story, results, standings)
                // ブローウィンズの選手の記事は、その選手の Bリーグ公式ページ(ハイライト動画)へ
                if (story.playerUrl.isNotBlank()) {
                    Text(
                        "▶ この選手のハイライト動画(Bリーグ公式) ›",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                        modifier = Modifier
                            .clickable { openUrl(context, story.playerUrl) }
                            .padding(vertical = 4.dp)
                    )
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
private val FEATURE_PHOTO_WIDTH = 132.dp
private val FEATURE_PHOTO_HEIGHT = 176.dp
/** 写真も図も無い記事の、小さな四角(チームの頭文字) */
private val FEATURE_TILE_SIZE = 72.dp

/** 特集の右の絵の大きさ(写真・選手カード・図は縦長、どれも無い記事は小さな四角) */
private fun featurePhotoSize(story: FeatureStory): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> =
    if (story.photo.isNotBlank() || numberFromCaption(story.photoCaption).isNotEmpty() || hasFeatureDataGraphic(story)) {
        FEATURE_PHOTO_WIDTH to FEATURE_PHOTO_HEIGHT
    } else {
        FEATURE_TILE_SIZE to FEATURE_TILE_SIZE
    }

/**
 * 特集の本文。右に写真(絵)を置き、本文をそのまわりに流す(新聞の回り込み)。
 * 写真の横に入りきらない本文は、写真の下から全幅で続ける。
 */
@Composable
private fun FeatureWrapBody(story: FeatureStory, results: Map<String, RemoteGameResult>, standings: Map<String, LeagueStandings>) {
    val (photoW, photoH) = featurePhotoSize(story)
    val gap = 12.dp
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink)
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val leftW = maxOf(maxWidth - photoW - gap, 80.dp)
        // 大きな数字の行の高さ(見出しの文字 40sp の行の高さ + すき間)
        val bigH = if (story.bigNumber != null) with(density) { 48.sp.toDp() } + 4.dp else 0.dp
        // 写真の横に入る本文の文字数(写真の高さに収まる行まで)
        val split = remember(story.body, leftW, photoH, bigH, bodyStyle, density) {
            val leftPx = with(density) { leftW.roundToPx() }
            val availPx = with(density) { (photoH - bigH).toPx() }
            val layout = textMeasurer.measure(
                androidx.compose.ui.text.AnnotatedString(story.body),
                style = bodyStyle,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = leftPx)
            )
            var lastLine = -1
            for (l in 0 until layout.lineCount) {
                if (layout.getLineBottom(l) <= availPx) lastLine = l else break
            }
            when {
                lastLine >= layout.lineCount - 1 -> story.body.length
                lastLine < 0 -> 0
                else -> layout.getLineEnd(lastLine, visibleEnd = false).coerceIn(0, story.body.length)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.width(leftW), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    if (split > 0) Text(story.body.substring(0, split).trimEnd(), style = bodyStyle)
                }
                Spacer(modifier = Modifier.width(gap))
                FeaturePhoto(story, photoW, photoH, results, standings)
            }
            if (split < story.body.length) {
                Text(story.body.substring(split).trimStart('\n'), style = bodyStyle, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * 一面の特集の右の絵。
 * ・写真がある記事:写真(顔が切れないよう上を残す。読めなければ次の写真へ)。代わりに付けた選手の写真には、下に選手名を重ねる
 * ・写真の無い選手の記事:チームカラーの選手カード(斜体の背番号・名前)
 * ・写真の無い順位・数字・決戦までなどの記事:記事の中身の図(直近の結果・順位表・試合までの日数)と競技のコートの線画
 * ・どれでもない記事:チームの頭文字の小さな四角
 * ・写真を押しても拡大はせず、記事全体と同じく、その試合・記事・掲示板などを開く
 */
@Composable
private fun FeaturePhoto(
    story: FeatureStory,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    results: Map<String, RemoteGameResult>,
    standings: Map<String, LeagueStandings>
) {
    val accent = story.team?.color ?: Ink
    // 試す写真の順番:記事の写真 → 代わりの選手の写真。読めなければ次へ進む
    val photos = remember(story) {
        (listOf(story.photo to story.photoCaption) + story.photoAlternates.map { it.first to it.second })
            .filter { it.first.isNotBlank() }
            .distinctBy { it.first }
    }
    var index by remember(story) { mutableStateOf(0) }
    val current = photos.getOrNull(index)
    // ニュースの横長の画像は左右を切って中央を出す(選手の写真は顔が切れないよう上を残す)
    val newsImage = index == 0 && current != null && current.second == null && story.url != null && story.bigNumber == null
    val isPlayer = numberFromCaption(story.photoCaption).isNotEmpty()
    Box(
        modifier = Modifier
            .size(width, height)
            .background(accent)
    ) {
        // 写真の無いとき(または写真を読み込むまで・どの写真も読み込めないとき)に見える絵
        when {
            isPlayer -> FeaturePlayerCard(story, modifier = Modifier.fillMaxSize())
            story.photo.isBlank() && hasFeatureDataGraphic(story) -> {
                if (!FeatureDataGraphic(story, mainGames(), results, standings, modifier = Modifier.fillMaxSize())) {
                    FeatureGraphic(story, modifier = Modifier.fillMaxSize())
                }
            }
            else -> FeatureGraphic(story, modifier = Modifier.fillMaxSize(), compact = width < FEATURE_PHOTO_WIDTH)
        }
        if (current != null) {
            RemoteThumbnail(
                current.first,
                width = width,
                height = height,
                alignTop = !newsImage,
                trimTransparent = true,
                backgroundColor = accent,
                framed = false,
                highRes = true,
                onFailed = { index++ }
            )
            current.second?.let { caption ->
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
private fun FeatureGraphic(story: FeatureStory, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (compact) {
        // 小さな四角:チームの頭文字とチーム名だけ(記事の種類は見出しの上に出ているので省く)
        Box(modifier = modifier) {
            story.team?.let { SportLines(it, Modifier.fillMaxSize()) }
            Column(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Headline(story.team?.initial ?: "特集", fontSize = if (story.team != null) 36 else 18, color = White)
                Text(story.team?.shortName ?: "全チーム", style = MaterialTheme.typography.labelSmall, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
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
            story.team?.displayName ?: "全チーム",
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

/**
 * 一面で使う試合(トップチームの公式の試合だけ)。
 * U-20・サテライトなどの試合と、自分で追加した予定は、試合タブの一覧にだけ出す
 */
private fun mainGames(): List<Game> = GamesRepository.games.filter { it.isMainTeamGame() }
