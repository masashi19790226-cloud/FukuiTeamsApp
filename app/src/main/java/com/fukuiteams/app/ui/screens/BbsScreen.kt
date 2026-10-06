package com.fukuiteams.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.BbsImage
import com.fukuiteams.app.data.BbsParser
import com.fukuiteams.app.data.BbsPost
import com.fukuiteams.app.data.BbsPrefs
import com.fukuiteams.app.data.BbsRepository
import com.fukuiteams.app.data.BbsSettings
import com.fukuiteams.app.data.BbsSort
import com.fukuiteams.app.data.GamesRepository
import com.fukuiteams.app.data.isNg
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.components.Headline
import com.fukuiteams.app.ui.components.MastheadTopBar
import com.fukuiteams.app.ui.components.RemoteThumbnail
import com.fukuiteams.app.ui.components.SectionLabel
import com.fukuiteams.app.ui.components.ThinRule
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.Ivory
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper
import com.fukuiteams.app.ui.theme.TeamBlowinds
import com.fukuiteams.app.ui.theme.White
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** 検索の期間。 */
private enum class BbsPeriod(val label: String) {
    TODAY("今日"),
    WEEK("1週間"),
    GAME_DAYS("試合日"),
    ALL("すべて")
}

/**
 * タイムラインの上に重ねて開く画面。戻るボタンで1つずつ閉じる。
 * 検索・設定も、タイムラインの上に重ねて開く(タイムラインの表示を広く取るため)。
 */
private sealed class BbsOverlay {
    data class Detail(val no: Int) : BbsOverlay()
    data class User(val userKey: String, val name: String) : BbsOverlay()
    object Search : BbsOverlay()
    object Settings : BbsOverlay()
}

/** 投稿カードから呼ぶ操作。 */
private class BbsActions(
    val openUser: (userKey: String, name: String) -> Unit,
    val openDetail: (no: Int) -> Unit,
    val openAnchor: (no: Int) -> Unit,
    val openUrl: (url: String) -> Unit,
    val openLike: (no: Int) -> Unit
)

/** タイムラインの1行(投稿 または「前回ここまで読みました」の区切り)。 */
private sealed class TimelineEntry(val key: String) {
    class PostEntry(val post: BbsPost) : TimelineEntry("p${post.no}")
    object ReadDivider : TimelineEntry("divider")
}

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")
private val HighlightColor = Color(0xFFFDE2C4)
private const val AUTO_REFRESH_MILLIS = 5 * 60 * 1000L

/**
 * 「掲示板」タブ。福井ブローウィンズ掲示板を読みやすく表示する(投稿・返信は公式ページで)。
 * ふだんはタイムラインだけを広く表示し、検索・設定・投稿詳細・ユーザー別一覧はこの画面の上に重ねて開く。
 * 表示件数・最新の位置など、あまり変えない項目は右上の「︙」メニューにまとめた。
 */
@Composable
fun BbsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsFlow = remember { BbsPrefs.flow(context) }
    val settings by settingsFlow.collectAsState<BbsSettings, BbsSettings?>(initial = null)
    val overlays = remember { mutableStateListOf<BbsOverlay>() }
    var anchorNo by remember { mutableStateOf<Int?>(null) }
    // いいねを押すために公式ページを開いている投稿の番号
    var likeNo by remember { mutableStateOf<Int?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    // このタブを開いた時点の既読位置。NEW と「前回ここまで読みました」はこれで決める
    var baseline by remember { mutableStateOf<Int?>(null) }
    val enteredAt = remember { System.currentTimeMillis() }
    var initialScrollDone by remember { mutableStateOf(false) }
    val timelineState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    // 検索の入力(検索を閉じても残す)
    var query by rememberSaveable { mutableStateOf("") }
    var searchByLikes by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableStateOf(BbsPeriod.ALL) }

    val posts = BbsRepository.posts

    suspend fun reload() {
        val count = (settings ?: BbsPrefs.load(context)).displayCount
        if (BbsRepository.refresh(count)) {
            BbsRepository.posts.keys.maxOrNull()?.let { BbsPrefs.markRead(context, it) }
        }
    }

    // 開いたとき・表示件数を変えたときに読み込む
    val displayCount = settings?.displayCount
    LaunchedEffect(displayCount) {
        if (displayCount == null) return@LaunchedEffect
        if (baseline == null) baseline = BbsPrefs.load(context).lastReadNo
        reload()
    }

    // 自動更新:タブを表示している間、5分ごと
    val autoRefresh = settings?.autoRefresh == true
    LaunchedEffect(autoRefresh) {
        if (!autoRefresh) return@LaunchedEffect
        while (true) {
            delay(AUTO_REFRESH_MILLIS)
            reload()
        }
    }

    // 下に引っ張ったとき・更新ボタン:掲示板だけを読み直す
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            reload()
            pullToRefreshState.endRefresh()
        }
    }

    BackHandler(enabled = overlays.isNotEmpty()) {
        overlays.removeAt(overlays.lastIndex)
    }

    // 投稿ごとの返信数(読み込み済みの投稿の「>>番号」から数える)
    val replyCounts = remember(posts) {
        val counts = HashMap<Int, Int>()
        posts.values.forEach { p -> p.anchors.forEach { a -> if (a != p.no) counts[a] = (counts[a] ?: 0) + 1 } }
        counts
    }

    val actions = BbsActions(
        openUser = { key, name -> overlays.add(BbsOverlay.User(key, name)) },
        openDetail = { no -> overlays.add(BbsOverlay.Detail(no)) },
        openAnchor = { no -> anchorNo = no },
        openUrl = { url -> openBbsUrl(context, url) },
        openLike = { no -> likeNo = no }
    )

    fun openOverlay(o: BbsOverlay) {
        // 検索・設定は重ねて開かず、開いているものを閉じてから開く
        overlays.clear()
        overlays.add(o)
    }

    Scaffold(
        topBar = {
            MastheadTopBar(
                section = "掲示板",
                edition = "ブローウィンズ",
                navigationIcon = {
                    if (overlays.isNotEmpty()) {
                        IconButton(onClick = { overlays.removeAt(overlays.lastIndex) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { pullToRefreshState.startRefresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "掲示板を更新")
                    }
                    IconButton(onClick = { openOverlay(BbsOverlay.Search) }) {
                        Icon(Icons.Filled.Search, contentDescription = "検索")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                        }
                        val s = settings
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (s != null) {
                                Text(
                                    "表示件数",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkSoft,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                                BbsPrefs.DISPLAY_COUNTS.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text((if (c == s.displayCount) "● " else "   ") + "${c}件") },
                                        onClick = {
                                            menuOpen = false
                                            scope.launch { BbsPrefs.setDisplayCount(context, c) }
                                        }
                                    )
                                }
                                HorizontalDivider()
                                listOf(true to "最新を上に表示", false to "最新を下に表示").forEach { (top, label) ->
                                    DropdownMenuItem(
                                        text = { Text((if (s.newestTop == top) "● " else "   ") + label) },
                                        onClick = {
                                            menuOpen = false
                                            scope.launch { BbsPrefs.setNewestTop(context, top) }
                                        }
                                    )
                                }
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text("設定(文字サイズ・自動更新・NG)") },
                                onClick = {
                                    menuOpen = false
                                    openOverlay(BbsOverlay.Settings)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("公式の掲示板を開く") },
                                onClick = {
                                    menuOpen = false
                                    openBbsUrl(context, BbsRepository.BASE_URL)
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (overlays.isEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("投稿", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { openBbsUrl(context, BbsRepository.WRITE_URL) },
                    containerColor = NewsRed,
                    contentColor = White
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            val s = settings
            val overlay = overlays.lastOrNull()
            if (s == null) {
                Text("読み込み中…", modifier = Modifier.padding(16.dp), color = InkSoft)
            } else if (overlay != null) {
                when (overlay) {
                    is BbsOverlay.Detail -> DetailPane(overlay.no, s, posts, replyCounts, baseline ?: 0, actions)
                    is BbsOverlay.User -> UserPane(
                        userKey = overlay.userKey,
                        name = overlay.name,
                        settings = s,
                        posts = posts,
                        replyCounts = replyCounts,
                        baseline = baseline ?: 0,
                        actions = actions,
                        onToggleNg = { isNg ->
                            scope.launch {
                                if (isNg) BbsPrefs.removeNgUser(context, overlay.userKey)
                                else BbsPrefs.addNgUser(context, overlay.userKey, overlay.name)
                            }
                        }
                    )
                    is BbsOverlay.Search -> SearchPane(
                        settings = s,
                        posts = posts,
                        replyCounts = replyCounts,
                        baseline = baseline ?: 0,
                        actions = actions,
                        query = query,
                        onQuery = { query = it },
                        byLikes = searchByLikes,
                        onByLikes = { searchByLikes = it },
                        period = period,
                        onPeriod = { period = it }
                    )
                    is BbsOverlay.Settings -> SettingsPane(s)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    BbsStatusBar(
                        baseline = baseline ?: 0,
                        posts = posts,
                        sort = s.sort,
                        onSort = { sort -> scope.launch { BbsPrefs.setSort(context, sort) } }
                    )
                    TimelinePane(
                        settings = s,
                        posts = posts,
                        replyCounts = replyCounts,
                        baseline = baseline ?: 0,
                        listState = timelineState,
                        actions = actions,
                        initialScrollDone = initialScrollDone,
                        readyToScroll = (BbsRepository.lastUpdatedMillis ?: 0L) >= enteredAt || BbsRepository.errorMessage != null,
                        onInitialScrollDone = { initialScrollDone = true }
                    )
                }
            }
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    anchorNo?.let { no ->
        val s = settings ?: BbsSettings()
        // ポップアップの中の操作は、ポップアップを閉じてから行う(「>>番号」はポップアップの中身を切り替える)
        val sheetActions = BbsActions(
            openUser = { key, name -> anchorNo = null; actions.openUser(key, name) },
            openDetail = { n -> anchorNo = null; actions.openDetail(n) },
            openAnchor = { n -> anchorNo = n },
            openUrl = actions.openUrl,
            openLike = { n -> anchorNo = null; likeNo = n }
        )
        AnchorSheet(
            no = no,
            settings = s,
            replyCounts = replyCounts,
            actions = sheetActions,
            onDismiss = { anchorNo = null }
        )
    }

    likeNo?.let { no ->
        LikeDialog(
            no = no,
            onDismiss = {
                likeNo = null
                // 押したいいねの数を反映するため、その投稿を読み直す
                scope.launch { BbsRepository.reloadPost(no) }
            }
        )
    }
}

/**
 * タイムラインの上の1行。閲覧中の人数・更新時刻・新着件数と、並び順の切り替え。
 * (以前の大きな見出し・タブの列はやめ、掲示板の部分を広く取る)
 */
@Composable
private fun BbsStatusBar(baseline: Int, posts: Map<Int, BbsPost>, sort: BbsSort, onSort: (BbsSort) -> Unit) {
    val viewers = BbsRepository.viewers
    val updated = BbsRepository.lastUpdatedMillis
    val newCount = if (baseline > 0) posts.keys.count { it > baseline } else 0
    var sortOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val parts = listOfNotNull(
                viewers?.let { "${it}人閲覧中" },
                updated?.let { "${timeLabel(it)}更新" },
                if (newCount > 0) "新着${newCount}件" else null
            )
            Text(
                if (parts.isEmpty()) "読み込み中…" else parts.joinToString("・"),
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = if (newCount > 0) NewsRed else InkSoft,
                fontWeight = if (newCount > 0) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box {
                Text(
                    "${sort.label} ▼",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier
                        .border(1.dp, Ink)
                        .clickable { sortOpen = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    BbsSort.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text((if (option == sort) "● " else "   ") + option.label) },
                            onClick = {
                                sortOpen = false
                                onSort(option)
                            }
                        )
                    }
                }
            }
        }
        BbsRepository.errorMessage?.let {
            Text(it, fontSize = 12.sp, color = NewsRed, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (BbsRepository.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = TeamBlowinds,
                trackColor = DividerGray
            )
        } else {
            ThinRule(color = LineGray)
        }
    }
}

// ---------------- タイムライン ----------------

@Composable
private fun TimelinePane(
    settings: BbsSettings,
    posts: Map<Int, BbsPost>,
    replyCounts: Map<Int, Int>,
    baseline: Int,
    listState: LazyListState,
    actions: BbsActions,
    initialScrollDone: Boolean,
    readyToScroll: Boolean,
    onInitialScrollDone: () -> Unit
) {
    val dateOrder = settings.sort != BbsSort.LIKES
    val newestFirst = remember(posts) { posts.values.sortedByDescending { it.no } }
    val base = when (settings.sort) {
        BbsSort.NEW -> newestFirst.take(settings.displayCount)
        BbsSort.LIKES -> newestFirst.take(settings.displayCount)
            .sortedWith(compareByDescending<BbsPost> { it.likes }.thenByDescending { it.no })
        BbsSort.IMAGES -> newestFirst.filter { it.images.isNotEmpty() }.take(settings.displayCount)
    }
    val hiddenCount = base.count { it.isNg(settings) }
    val visible = base.filterNot { it.isNg(settings) }
    val ordered = if (dateOrder && !settings.newestTop) visible.reversed() else visible

    // 「前回ここまで読みました」の区切りを入れる(新着順・画像ありで、既読位置があるときだけ)
    val entries = mutableListOf<TimelineEntry>()
    val showDivider = dateOrder && baseline > 0 &&
        ordered.any { it.no > baseline } && ordered.any { it.no <= baseline }
    var dividerAdded = false
    ordered.forEach { p ->
        val isRead = p.no <= baseline
        if (showDivider && !dividerAdded) {
            // 最新が上:未読の後(最初の既読の前)/最新が下:既読の後(最初の未読の前)
            val boundary = if (settings.newestTop) isRead else !isRead
            if (boundary) {
                entries += TimelineEntry.ReadDivider
                dividerAdded = true
            }
        }
        entries += TimelineEntry.PostEntry(p)
    }
    // 投稿の前に置く行(NGの件数)のぶん、投稿の位置がずれる
    val headerItems = if (hiddenCount > 0) 1 else 0

    // 開いたら、未読と既読の境目から表示する
    //  最新が上:いちばん古い未読の投稿を一番上に(その下に区切り線。新しい投稿は上へスクロール)
    //  最新が下:区切り線を一番上に(その下が最初の未読)
    //  未読が無いときは、最新が上なら一番上、最新が下なら一番下
    LaunchedEffect(readyToScroll, entries.size, settings.newestTop) {
        if (initialScrollDone || !readyToScroll || entries.isEmpty()) return@LaunchedEffect
        val divider = entries.indexOfFirst { it is TimelineEntry.ReadDivider }
        val target = when {
            divider >= 0 && settings.newestTop -> (divider - 1).coerceAtLeast(0)
            divider >= 0 -> divider
            settings.newestTop -> 0
            else -> entries.lastIndex
        }
        listState.scrollToItem(target + headerItems)
        onInitialScrollDone()
    }
    // 「最新を上/下に表示」を切り替えたら、最新の投稿の位置へ
    var lastNewestTop by remember { mutableStateOf(settings.newestTop) }
    LaunchedEffect(settings.newestTop) {
        if (lastNewestTop != settings.newestTop) {
            lastNewestTop = settings.newestTop
            if (settings.newestTop) listState.scrollToItem(0)
            else listState.scrollToItem((entries.size + headerItems - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hiddenCount > 0) {
            item(key = "ng") {
                Text("NGで${hiddenCount}件を非表示", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }
        if (entries.isEmpty()) {
            item(key = "empty") {
                Text(
                    when {
                        BbsRepository.loading -> "読み込み中…"
                        settings.sort == BbsSort.IMAGES && posts.isNotEmpty() -> "読み込み済みの投稿に、画像のあるものはありません(右上の︙で表示件数を増やすと見つかることがあります)"
                        BbsRepository.errorMessage != null -> "読み込めませんでした。右上の更新ボタンで読み直してください。"
                        else -> "投稿がありません"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
        items(entries, key = { it.key }) { entry ->
            when (entry) {
                is TimelineEntry.ReadDivider -> ReadDivider()
                is TimelineEntry.PostEntry -> PostCard(
                    post = entry.post,
                    settings = settings,
                    isNew = baseline > 0 && entry.post.no > baseline,
                    replyCount = replyCounts[entry.post.no] ?: 0,
                    highlight = emptyList(),
                    actions = actions,
                    onClickCard = { actions.openDetail(entry.post.no) }
                )
            }
        }
    }
}

@Composable
private fun ReadDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f).height(2.dp).background(NewsRed))
        Text("前回ここまで読みました", color = NewsRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.weight(1f).height(2.dp).background(NewsRed))
    }
}

// ---------------- 投稿カード ----------------

@Composable
private fun PostCard(
    post: BbsPost,
    settings: BbsSettings,
    isNew: Boolean,
    replyCount: Int,
    highlight: List<String>,
    actions: BbsActions,
    onClickCard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isNew) NewsRed else LineGray)
            .background(if (isNew) White else Paper)
            .clickable(onClick = onClickCard)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    post.name.ifBlank { "(名前なし)" },
                    modifier = Modifier.clickable { actions.openUser(post.userKey, post.name) },
                    color = TeamBlowinds,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    post.shortDateTime + if (post.posterId.isNotBlank()) " ・ ID:${post.posterId}" else "",
                    fontSize = 11.sp,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isNew) {
                Text(
                    "NEW",
                    color = White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.background(NewsRed).padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            Text(
                "No.${post.no}",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = TeamBlowinds
            )
        }
        if (post.body.isNotBlank()) {
            BbsBodyText(
                text = post.body,
                fontSize = settings.fontSize,
                highlight = highlight,
                onAnchor = actions.openAnchor,
                onUrl = actions.openUrl,
                onPlainClick = onClickCard
            )
        }
        if (post.images.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                post.images.forEach { BbsImageThumb(it, post.no) }
            }
        }
        if (post.favorite.isNotBlank()) {
            Text("好きな選手:${post.favorite}", fontSize = 12.sp, color = InkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (replyCount > 0) {
                Text(
                    "返信 ${replyCount}件 ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TeamBlowinds,
                    modifier = Modifier.clickable { actions.openDetail(post.no) }.padding(vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // いいね:押すと公式ページのその投稿を開き、そこで「いいね」を押せる
            Row(
                modifier = Modifier
                    .border(1.dp, NewsRed.copy(alpha = 0.5f))
                    .clickable { actions.openLike(post.no) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.FavoriteBorder, contentDescription = "いいね", tint = NewsRed, modifier = Modifier.size(15.dp))
                Text("いいね ${post.likes}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NewsRed)
            }
            Text(
                "返信する",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier
                    .border(1.dp, LineGray)
                    .clickable { actions.openUrl(BbsRepository.replyUrl(post.no)) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/** 添付画像。大きい版のURLが分かればそれを表示し、タップで拡大する。 */
@Composable
private fun BbsImageThumb(image: BbsImage, no: Int) {
    val full by produceState<String?>(BbsParser.fullImageFromThumb(image.thumbUrl), image) {
        if (value == null) value = BbsRepository.fullImageUrl(image)
    }
    RemoteThumbnail(
        url = full ?: image.thumbUrl,
        width = 110.dp,
        height = 110.dp,
        zoomCaption = "No.$no の添付画像"
    )
}

private val LINK_REGEX = Regex("(?:>>|＞＞|≫)\\s*(\\d+)|https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")

/** 本文。「>>番号」と URL はタップできるようにし、検索語は色を付ける。 */
@Composable
private fun BbsBodyText(
    text: String,
    fontSize: Int,
    highlight: List<String>,
    onAnchor: (Int) -> Unit,
    onUrl: (String) -> Unit,
    onPlainClick: () -> Unit
) {
    val annotated = remember(text, highlight) { buildBodyText(text, highlight) }
    ClickableText(
        text = annotated,
        style = TextStyle(fontSize = fontSize.sp, lineHeight = (fontSize * 1.6f).sp, color = Ink),
        onClick = { offset ->
            val hit = annotated.getStringAnnotations(start = offset, end = offset).firstOrNull()
            when (hit?.tag) {
                "anchor" -> hit.item.toIntOrNull()?.let(onAnchor)
                "url" -> onUrl(hit.item)
                else -> onPlainClick()
            }
        }
    )
}

private fun buildBodyText(text: String, highlight: List<String>): AnnotatedString = buildAnnotatedString {
    append(text)
    LINK_REGEX.findAll(text).forEach { m ->
        val start = m.range.first
        val end = m.range.last + 1
        val anchor = m.groups[1]?.value
        if (anchor != null) {
            addStringAnnotation("anchor", anchor, start, end)
            addStyle(SpanStyle(color = TeamBlowinds, fontWeight = FontWeight.Bold, background = TeamBlowinds.copy(alpha = 0.08f)), start, end)
        } else {
            addStringAnnotation("url", m.value, start, end)
            addStyle(SpanStyle(color = TeamBlowinds, textDecoration = TextDecoration.Underline), start, end)
        }
    }
    highlight.filter { it.isNotBlank() }.forEach { word ->
        var from = 0
        while (true) {
            val at = text.indexOf(word, from, ignoreCase = true)
            if (at < 0) break
            addStyle(SpanStyle(background = HighlightColor, fontWeight = FontWeight.Bold), at, at + word.length)
            from = at + word.length
        }
    }
}

// ---------------- いいね(公式ページをアプリの中で開く) ----------------

/**
 * いいねは公式ページの「いいね」ボタンで押す仕組みなので、その投稿の公式ページ(?anc=番号)を
 * アプリの中の画面で開き、そこで押してもらう。閉じるとアプリに戻り、その投稿のいいね数を読み直す。
 */
@Composable
private fun LikeDialog(no: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(Ivory)) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Ink).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "No.$no の「いいね」を押して閉じてください",
                    color = White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "閉じる", tint = White) }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        @Suppress("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // ページ内の移動はこの画面の中で行う
                        webViewClient = WebViewClient()
                        CookieManager.getInstance().setAcceptCookie(true)
                        loadUrl(BbsRepository.BASE_URL + "?anc=$no#anc")
                    }
                },
                onRelease = { it.destroy() }
            )
        }
    }
}

// ---------------- アンカー先のポップアップ ----------------

@Composable
private fun AnchorSheet(
    no: Int,
    settings: BbsSettings,
    replyCounts: Map<Int, Int>,
    actions: BbsActions,
    onDismiss: () -> Unit
) {
    // 0:読み込み中 1:見つかった 2:見つからない
    var status by remember(no) { mutableStateOf(if (BbsRepository.posts[no] != null) 1 else 0) }
    var post by remember(no) { mutableStateOf(BbsRepository.posts[no]) }
    LaunchedEffect(no) {
        if (post == null) {
            post = BbsRepository.findPost(no)
            status = if (post != null) 1 else 2
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("アンカー先")
                Spacer(modifier = Modifier.width(8.dp))
                Text("No.$no", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TeamBlowinds)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "閉じる") }
            }
            val p = post
            when {
                p != null -> {
                    PostCard(
                        post = p,
                        settings = settings,
                        isNew = false,
                        replyCount = replyCounts[p.no] ?: 0,
                        highlight = emptyList(),
                        actions = actions,
                        onClickCard = { actions.openDetail(p.no) }
                    )
                    Button(
                        onClick = { actions.openDetail(p.no) },
                        colors = ButtonDefaults.buttonColors(containerColor = TeamBlowinds),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("この投稿を開く") }
                }
                status == 0 -> Text("読み込み中…", color = InkSoft)
                else -> {
                    Text("投稿が見つかりませんでした(削除されたか、過去ログに移った可能性があります)", color = InkSoft)
                    OutlinedButton(
                        onClick = { actions.openUrl(BbsRepository.BASE_URL + "?anc=$no") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("公式の掲示板で開く") }
                }
            }
        }
    }
}

// ---------------- 投稿詳細 ----------------

@Composable
private fun DetailPane(
    no: Int,
    settings: BbsSettings,
    posts: Map<Int, BbsPost>,
    replyCounts: Map<Int, Int>,
    baseline: Int,
    actions: BbsActions
) {
    var notFound by remember(no) { mutableStateOf(false) }
    LaunchedEffect(no) {
        if (BbsRepository.posts[no] == null) {
            notFound = BbsRepository.findPost(no) == null
        }
    }
    val post = posts[no]
    val replies = posts.values
        .filter { p -> p.no != no && no in p.anchors && !p.isNg(settings) }
        .sortedBy { it.no }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("投稿")
                Headline("No.$no", fontSize = 22, color = TeamBlowinds)
            }
        }
        item(key = "post") {
            when {
                post != null -> PostCard(
                    post = post,
                    settings = settings,
                    isNew = baseline > 0 && post.no > baseline,
                    replyCount = replyCounts[post.no] ?: 0,
                    highlight = emptyList(),
                    actions = actions,
                    onClickCard = {}
                )
                notFound -> Text("投稿が見つかりませんでした(削除されたか、過去ログに移った可能性があります)", color = InkSoft)
                else -> Text("読み込み中…", color = InkSoft)
            }
        }
        item(key = "buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { actions.openUrl(BbsRepository.replyUrl(no)) },
                    modifier = Modifier.weight(1f)
                ) { Text("返信する(公式)", maxLines = 1, fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { actions.openLike(no) },
                    modifier = Modifier.weight(1f)
                ) { Text("いいねする", maxLines = 1, fontSize = 13.sp) }
            }
        }
        item(key = "replies-title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThinRule()
                Text("この投稿への返信 ${replies.size}件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                Text("読み込み済みの投稿の中から探しています", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }
        items(replies, key = { "r${it.no}" }) { r ->
            PostCard(
                post = r,
                settings = settings,
                isNew = baseline > 0 && r.no > baseline,
                replyCount = replyCounts[r.no] ?: 0,
                highlight = emptyList(),
                actions = actions,
                onClickCard = { actions.openDetail(r.no) }
            )
        }
    }
}

// ---------------- ユーザー別一覧 ----------------

@Composable
private fun UserPane(
    userKey: String,
    name: String,
    settings: BbsSettings,
    posts: Map<Int, BbsPost>,
    replyCounts: Map<Int, Int>,
    baseline: Int,
    actions: BbsActions,
    onToggleNg: (isNg: Boolean) -> Unit
) {
    val userPosts = posts.values.filter { it.userKey == userKey }.sortedByDescending { it.no }
    val likesTotal = userPosts.sumOf { it.likes }
    val latest = userPosts.firstOrNull()?.shortDateTime?.take(5) ?: "-"
    val favorite = userPosts.firstOrNull { it.favorite.isNotBlank() }?.favorite
    val profileUrl = userPosts.firstOrNull()?.profileUrl
        ?: if (userKey.startsWith("name:")) null else "https://j-basketball.club/bbs/user.php?id=$userKey"
    val isNg = userKey in settings.ngUsers
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "profile") {
            Column(
                modifier = Modifier.fillMaxWidth().border(1.dp, Ink).background(Paper).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Headline(name.ifBlank { "(名前なし)" } + " の投稿", fontSize = 20)
                if (favorite != null) {
                    Text("好きな選手:$favorite", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserStat("${userPosts.size}", "読み込み済み投稿", Modifier.weight(1f))
                    UserStat("$likesTotal", "いいね合計", Modifier.weight(1f))
                    UserStat(latest, "最新の投稿", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { profileUrl?.let(actions.openUrl) },
                        enabled = profileUrl != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("公式プロフィール", maxLines = 1, fontSize = 13.sp) }
                    Button(
                        onClick = { onToggleNg(isNg) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isNg) InkSoft else NewsRed),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (isNg) "NGを解除" else "NGに追加", maxLines = 1, fontSize = 13.sp) }
                }
                if (isNg) {
                    Text("NGユーザーです。タイムラインと検索では表示しません。", style = MaterialTheme.typography.bodySmall, color = NewsRed)
                }
            }
        }
        if (userPosts.isEmpty()) {
            item(key = "empty") {
                Text("読み込み済みの投稿はありません", color = InkSoft)
            }
        }
        items(userPosts, key = { "u${it.no}" }) { p ->
            PostCard(
                post = p,
                settings = settings,
                isNew = baseline > 0 && p.no > baseline,
                replyCount = replyCounts[p.no] ?: 0,
                highlight = emptyList(),
                actions = actions,
                onClickCard = { actions.openDetail(p.no) }
            )
        }
    }
}

@Composable
private fun UserStat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier = modifier.background(Ivory).border(1.dp, LineGray).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, fontSize = 20.sp, color = TeamBlowinds, maxLines = 1)
        Text(label, fontSize = 10.sp, color = InkSoft, maxLines = 1)
    }
}

// ---------------- 検索 ----------------

@Composable
private fun SearchPane(
    settings: BbsSettings,
    posts: Map<Int, BbsPost>,
    replyCounts: Map<Int, Int>,
    baseline: Int,
    actions: BbsActions,
    query: String,
    onQuery: (String) -> Unit,
    byLikes: Boolean,
    onByLikes: (Boolean) -> Unit,
    period: BbsPeriod,
    onPeriod: (BbsPeriod) -> Unit
) {
    val scope = rememberCoroutineScope()
    var olderMessage by remember { mutableStateOf<String?>(null) }
    val games = GamesRepository.games
    val gameDays = remember(games) {
        games.filter { it.team == Team.BLOWINDS }.mapNotNull { g ->
            val parts = g.dateLabel.split("/").mapNotNull { it.trim().toIntOrNull() }
            if (parts.size == 3) runCatching { LocalDate.of(parts[0], parts[1], parts[2]) }.getOrNull() else null
        }.toSet()
    }
    val words = query.trim().split(Regex("[\\s　]+")).filter { it.isNotEmpty() }
    val today = LocalDate.now(JST)
    val results = posts.values.filter { p ->
        if (p.isNg(settings)) return@filter false
        val d = p.date
        val inPeriod = when (period) {
            BbsPeriod.ALL -> true
            BbsPeriod.TODAY -> d == today
            BbsPeriod.WEEK -> d != null && !d.isBefore(today.minusDays(6))
            BbsPeriod.GAME_DAYS -> d != null && d in gameDays
        }
        inPeriod && words.all { w ->
            p.body.contains(w, ignoreCase = true) || p.name.contains(w, ignoreCase = true) ||
                w.removePrefix("No.").removePrefix("no.") == p.no.toString()
        }
    }.let { list ->
        if (byLikes) list.sortedWith(compareByDescending<BbsPost> { it.likes }.thenByDescending { it.no })
        else list.sortedByDescending { it.no }
    }
    val loadedMin = posts.keys.minOrNull()
    val loadedMax = posts.keys.maxOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "form") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("キーワード・名前・No(空白で区切ると全部を含む)") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "消す") }
                        }
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("並び順", style = MaterialTheme.typography.bodySmall, color = InkSoft, modifier = Modifier.width(44.dp))
                    Segmented(
                        options = listOf(false to "新着順", true to "いいね順"),
                        selected = byLikes,
                        onSelect = onByLikes,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("期間", style = MaterialTheme.typography.bodySmall, color = InkSoft, modifier = Modifier.width(44.dp))
                    Segmented(
                        options = BbsPeriod.values().map { it to it.label },
                        selected = period,
                        onSelect = onPeriod,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    (if (words.isEmpty()) "すべての投稿" else "「${words.joinToString(" ")}」") +
                        " ${results.size}件 ・ ${if (byLikes) "いいね順" else "新着順"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (loadedMin != null && loadedMax != null) {
                        "読み込み済みの ${posts.size}件(No.$loadedMin〜No.$loadedMax)から探しています"
                    } else "まだ投稿を読み込んでいません",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                if (period == BbsPeriod.GAME_DAYS && gameDays.isEmpty()) {
                    Text("ブローウィンズの試合日程がまだ読み込まれていません", style = MaterialTheme.typography.bodySmall, color = NewsRed)
                }
                ThinRule(color = LineGray)
            }
        }
        items(results, key = { "s${it.no}" }) { p ->
            PostCard(
                post = p,
                settings = settings,
                isNew = baseline > 0 && p.no > baseline,
                replyCount = replyCounts[p.no] ?: 0,
                highlight = words,
                actions = actions,
                onClickCard = { actions.openDetail(p.no) }
            )
        }
        item(key = "older") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            olderMessage = "読み込み中…"
                            val added = BbsRepository.loadOlder(100)
                            olderMessage = if (added > 0) "過去の投稿を${added}件読み込みました" else "これ以上読み込めませんでした"
                        }
                    },
                    enabled = !BbsRepository.loading && posts.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("過去100件を読み込む") }
                olderMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = InkSoft) }
            }
        }
    }
}

// ---------------- 設定 ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPane(settings: BbsSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var newWord by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsBlock("表示") {
            Text("文字サイズ", style = MaterialTheme.typography.bodyMedium)
            Segmented(
                options = BbsPrefs.FONT_SIZES.map { (size, label) -> size to "$label(${size})" },
                selected = settings.fontSize,
                onSelect = { scope.launch { BbsPrefs.setFontSize(context, it) } }
            )
            Text("本文はこの大きさで表示します", fontSize = settings.fontSize.sp, color = InkSoft)
            Text("表示件数", style = MaterialTheme.typography.bodyMedium)
            Segmented(
                options = BbsPrefs.DISPLAY_COUNTS.map { it to "${it}件" },
                selected = settings.displayCount,
                onSelect = { scope.launch { BbsPrefs.setDisplayCount(context, it) } }
            )
            Text("並び順", style = MaterialTheme.typography.bodyMedium)
            Segmented(
                options = BbsSort.values().map { it to it.label },
                selected = settings.sort,
                onSelect = { scope.launch { BbsPrefs.setSort(context, it) } }
            )
            Segmented(
                options = listOf(true to "最新を上に表示", false to "最新を下に表示"),
                selected = settings.newestTop,
                onSelect = { scope.launch { BbsPrefs.setNewestTop(context, it) } }
            )
        }
        SettingsBlock("更新") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("自動更新", style = MaterialTheme.typography.bodyMedium)
                    Text("5分ごと(掲示板タブを開いている間)", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
                Switch(
                    checked = settings.autoRefresh,
                    onCheckedChange = { on -> scope.launch { BbsPrefs.setAutoRefresh(context, on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = TeamBlowinds, checkedThumbColor = White)
                )
            }
        }
        SettingsBlock("NGワード") {
            Text("この言葉を含む投稿(名前・本文)を表示しません", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            if (settings.ngWords.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    settings.ngWords.forEach { w ->
                        Row(
                            modifier = Modifier.background(DividerGray).padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(w, fontSize = 13.sp)
                            IconButton(
                                onClick = { scope.launch { BbsPrefs.removeNgWord(context, w) } },
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Filled.Close, contentDescription = "「$w」を削除", modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("ワードを入力") }
                )
                Button(
                    onClick = {
                        val w = newWord.trim()
                        if (w.isNotEmpty()) {
                            scope.launch { BbsPrefs.addNgWord(context, w) }
                            newWord = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) { Text("追加") }
            }
        }
        SettingsBlock("NGユーザー") {
            if (settings.ngUsers.isEmpty()) {
                Text(
                    "NGユーザーはいません。投稿の名前を押して開く画面から追加できます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            } else {
                settings.ngUsers.entries.sortedBy { it.value }.forEach { (key, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name.ifBlank { "(名前なし)" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { scope.launch { BbsPrefs.removeNgUser(context, key) } }) { Text("解除") }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { openBbsUrl(context, BbsRepository.BASE_URL) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("公式の掲示板を開く") }
        Text(
            "この画面は福井ブローウィンズ掲示板(j-basketball.club)を読みやすく表示するものです。投稿・返信・いいねは公式ページで行ってください。設定はこの端末だけに保存されます。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
    }
}

@Composable
private fun SettingsBlock(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        Column(
            modifier = Modifier.fillMaxWidth().border(1.dp, LineGray).background(Paper).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { content() }
    }
}

// ---------------- 共通部品 ----------------

/** 紙面風の切り替えボタン(選ばれているものは黒地に白文字)。 */
@Composable
private fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth().border(1.dp, Ink).background(Paper)) {
        options.forEach { (value, label) ->
            val sel = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (sel) Ink else Paper)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (sel) White else Ink,
                    fontSize = 12.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun timeLabel(millis: Long): String {
    val t = java.time.Instant.ofEpochMilli(millis).atZone(JST)
    return String.format(java.util.Locale.US, "%d:%02d", t.hour, t.minute)
}

private fun openBbsUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // ブラウザが無いときは何もしない
    }
}
