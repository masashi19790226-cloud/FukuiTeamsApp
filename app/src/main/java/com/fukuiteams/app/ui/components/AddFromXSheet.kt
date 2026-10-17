package com.fukuiteams.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fukuiteams.app.data.GameCandidate
import com.fukuiteams.app.data.GamesRepository
import com.fukuiteams.app.data.MyGameEntry
import com.fukuiteams.app.data.MyGamesRepository
import com.fukuiteams.app.data.XPost
import com.fukuiteams.app.data.XPostRepository
import com.fukuiteams.app.data.extractCandidates
import com.fukuiteams.app.data.findXStatusUrl
import com.fukuiteams.app.data.guessTeam
import com.fukuiteams.app.data.loadPreviewBitmap
import com.fukuiteams.app.data.officialInstagramUrl
import com.fukuiteams.app.data.officialXUrl
import com.fukuiteams.app.data.recognizeImageText
import com.fukuiteams.app.model.shortName
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper
import com.fukuiteams.app.ui.theme.White
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 投稿から予定を追加する画面(下から出る)。U-20の大会など、公式の日程に載らない試合を自分で追加する。
 * 読み込み方は2つ:
 * - Xの投稿:URLを貼る(または X で「共有」→「ふくスポ」)→ 本文から日付・時刻・相手の候補を出す
 * - 画像:スクリーンショットなどを「共有」→「ふくスポ」、または「写真から読み込む」で選ぶ
 *   → 画像の中の文字をスマホの中で読み取り、同じように候補を出す(画像は保存しない)
 * 候補の「+ 追加」か「まとめて追加」で試合タブに並ぶ(この端末の中だけ)。
 * 時刻・会場などが足りない予定は、あとで試合タブでその予定を押し、「足りないところを補足」で直せる。
 * 候補が出ないときだけ、下の「自分で入力して追加」を使う。
 */
@Composable
fun AddFromXSheet(
    initialText: String?,
    defaultTeam: Team,
    onDismiss: () -> Unit,
    onAdded: (gameId: String, team: Team) -> Unit,
    /** 共有で送られてきた画像(content://…)。あれば開いたらすぐ読み取る */
    initialImage: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var urlText by remember { mutableStateOf(findXStatusUrl(initialText) ?: initialText.orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // 読み込んだもの(Xの投稿か画像のどちらか)
    var post by remember { mutableStateOf<XPost?>(null) }
    var showEmbed by remember { mutableStateOf(true) }
    var imagePreview by remember { mutableStateOf<Bitmap?>(null) }
    var imageText by remember { mutableStateOf<String?>(null) }
    var showImageText by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<GameCandidate>>(emptyList()) }

    // 追加する予定のチーム・ホーム/アウェイ(候補にも、手で入力するときにも使う)
    var team by remember { mutableStateOf(defaultTeam) }
    var isHome by remember { mutableStateOf(true) }
    // 追加した候補(同じ候補を2回追加しない)と、追加した件数
    var addedKeys by remember { mutableStateOf(setOf<String>()) }
    var addedCount by remember { mutableStateOf(0) }
    var lastAddedId by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    // 手で入力して追加(候補が出ないとき用。ふだんは閉じておく)
    var showForm by remember { mutableStateOf(false) }
    var dateText by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }
    var opponent by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf("") }

    // 閉じるとき:1件でも追加していたら、そのチームの「今後の試合」を出す
    fun close() {
        if (addedCount > 0) onAdded(lastAddedId, team) else onDismiss()
    }

    fun sourceUrl(): String = post?.url ?: findXStatusUrl(urlText).orEmpty()

    /** 予定を1件加える。加えたら true(同じ予定がすでにあれば false で、理由を message に出す) */
    fun addEntry(date: LocalDate, time: String, opp: String, place: String): Boolean {
        val t = time.ifBlank { "時間未定" }
        if (alreadyInSchedule(team, date, t, opp)) {
            message = "${date.monthValue}/${date.dayOfMonth} ${if (opp.isNotBlank()) "vs $opp " else ""}は、すでに日程にあります"
            return false
        }
        val entry = MyGameEntry(
            id = MyGamesRepository.newId() + addedCount,
            team = team,
            date = date,
            time = t,
            opponent = opp.trim(),
            venue = place.trim(),
            isHome = isHome,
            sourceUrl = sourceUrl()
        )
        if (!MyGamesRepository.add(context, entry)) {
            message = "同じ予定がすでに追加されています"
            return false
        }
        addedCount += 1
        lastAddedId = entry.id
        return true
    }

    fun addCandidate(c: GameCandidate) {
        if (addCandidateKey(c) in addedKeys) return
        if (addEntry(c.date, c.time, c.opponent, "")) {
            addedKeys = addedKeys + addCandidateKey(c)
            message = "追加しました。時刻・会場など足りないところは、試合タブでその予定を押すと補足できます。"
        }
    }

    fun loadX() {
        val url = findXStatusUrl(urlText)
        if (url == null) {
            error = "XのURLが見つかりません。「https://x.com/○○/status/数字」の形のURLを貼ってください"
            return
        }
        urlText = url
        loading = true
        error = null
        message = null
        post = null
        imagePreview = null
        imageText = null
        candidates = emptyList()
        scope.launch {
            try {
                val p = XPostRepository.fetch(url)
                post = p
                candidates = extractCandidates(p.text, p.postedOn)
                guessTeam(p.text, p.authorName + " " + p.authorHandle)?.let { team = it }
            } catch (e: Exception) {
                error = e.message ?: "投稿を読めませんでした"
            } finally {
                loading = false
            }
        }
    }

    fun loadImage(uri: Uri) {
        loading = true
        error = null
        message = null
        post = null
        imagePreview = null
        imageText = null
        candidates = emptyList()
        scope.launch {
            try {
                imagePreview = loadPreviewBitmap(context, uri)
                val text = recognizeImageText(context, uri)
                imageText = text
                candidates = extractCandidates(text, null)
                guessTeam(text, "")?.let { team = it }
                if (text.isBlank()) error = "画像の中に文字が見つかりませんでした"
            } catch (e: Exception) {
                error = e.message ?: "画像の文字を読み取れませんでした"
            } finally {
                loading = false
            }
        }
    }

    // 写真(スクリーンショット)を選ぶ
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadImage(uri)
    }

    // 共有で送られてきた画像・URLは、開いたらすぐ読み込む
    LaunchedEffect(Unit) {
        if (initialImage != null) {
            loadImage(Uri.parse(initialImage))
        } else if (findXStatusUrl(initialText) != null) {
            loadX()
        }
    }

    ModalBottomSheet(onDismissRequest = { close() }, sheetState = sheetState, containerColor = Paper) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("投稿から予定を追加", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "U-20の大会など、公式の日程に載らない試合を追加できます。追加した予定はこのスマホの中だけに保存されます。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            // 各チームの公式X・インスタ。開いた投稿(またはそのスクリーンショット)を「共有」→「ふくスポ」で送ると、この画面で読み込む
            Text("公式のXを開く", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Team.values().forEach { t ->
                    NewsChip(t.shortName, false, modifier = Modifier.weight(1f)) { openExternalUrl(context, t.officialXUrl) }
                }
            }
            Text("公式のインスタを開く", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Team.values().forEach { t ->
                    t.officialInstagramUrl?.let { ig ->
                        NewsChip(t.shortName, false, modifier = Modifier.weight(1f)) { openExternalUrl(context, ig) }
                    }
                }
            }
            Text(
                "Xの投稿は「共有」→「ふくスポ」。日程が画像だけのときやインスタの投稿は、スクリーンショットを撮って「共有」→「ふくスポ」で読み込めます(丸岡RUCKは公式サイトにインスタが載っていないため、Xだけです)。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("XのURL") },
                placeholder = { Text("https://x.com/○○/status/…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { loadX() },
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Xの投稿を読み込む")
            }
            OutlinedButton(
                onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("写真(スクリーンショット)から読み込む", color = Ink, fontWeight = FontWeight.Bold)
            }
            if (loading) {
                Text("読み込み中…(画像は文字の読み取りに数秒かかります)", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NewsRed) }

            // 読み込んだXの投稿
            post?.let { p ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, LineGray)
                        .background(White)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "${p.authorName}(@${p.authorHandle})" + (p.postedOn?.let { " ${it.year}/${it.monthValue}/${it.dayOfMonth}" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft
                    )
                    SelectionContainer {
                        Text(p.text.ifBlank { "(本文なし)" }, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NewsChip(if (showEmbed) "投稿の表示を閉じる" else "投稿を画像付きで表示", showEmbed) { showEmbed = !showEmbed }
                    NewsChip("Xで開く", false) { openExternalUrl(context, p.url) }
                }
                if (showEmbed) {
                    XEmbedView(p.embedHtml, modifier = Modifier.fillMaxWidth().height(460.dp))
                }
            }

            // 読み込んだ画像と、読み取った文字
            if (imagePreview != null || imageText != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, LineGray)
                        .background(White)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    imagePreview?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "読み込んだ画像",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                        )
                    }
                    imageText?.takeIf { it.isNotBlank() }?.let { text ->
                        Text(
                            if (showImageText) "▲ 読み取った文字を閉じる" else "▼ 読み取った文字を見る",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Accent,
                            modifier = Modifier.clickable { showImageText = !showImageText }
                        )
                        if (showImageText) {
                            SelectionContainer {
                                Text(text, style = MaterialTheme.typography.bodySmall, color = Ink)
                            }
                        }
                    }
                }
            }

            // 見つけた日程の候補(押すとすぐ追加)
            if (post != null || imageText != null) {
                Text("見つけた日程の候補", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Team.values().forEach { t ->
                        NewsChip(t.shortName, team == t, modifier = Modifier.weight(1f)) { team = t }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NewsChip("ホーム", isHome, modifier = Modifier.weight(1f)) { isHome = true }
                    NewsChip("アウェイ", !isHome, modifier = Modifier.weight(1f)) { isHome = false }
                }
                if (candidates.isEmpty()) {
                    Text(
                        "日付が見つかりませんでした。下の「自分で入力して追加」から追加できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                } else {
                    Text(
                        "チームは読み込んだ文字から選んでいます。違うときは上で選び直してから追加してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                    candidates.forEach { c ->
                        val added = addCandidateKey(c) in addedKeys
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Ink)
                                .background(Paper)
                                .clickable(enabled = !added) { addCandidate(c) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(c.label, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
                            Text(
                                if (added) "追加済み" else "+ 追加",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (added) InkSoft else NewsRed
                            )
                        }
                    }
                    val rest = candidates.filter { addCandidateKey(it) !in addedKeys }
                    if (rest.size > 1) {
                        Button(
                            onClick = {
                                var n = 0
                                rest.forEach { c ->
                                    if (addEntry(c.date, c.time, c.opponent, "")) {
                                        addedKeys = addedKeys + addCandidateKey(c)
                                        n++
                                    }
                                }
                                if (n > 0) {
                                    message = "${n}件追加しました。時刻・会場など足りないところは、試合タブでその予定を押すと補足できます。"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("候補をまとめて追加(${rest.size}件)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NewsRed) }

            // 候補が出ないとき用:自分で入力して追加(ふだんは閉じておく)
            Text(
                if (showForm) "▲ 自分で入力して追加を閉じる" else "▼ 自分で入力して追加",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Accent,
                modifier = Modifier.fillMaxWidth().clickable { showForm = !showForm }.padding(vertical = 4.dp)
            )
            if (showForm) {
                if (post == null && imageText == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Team.values().forEach { t ->
                            NewsChip(t.shortName, team == t, modifier = Modifier.weight(1f)) { team = t }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NewsChip("ホーム", isHome, modifier = Modifier.weight(1f)) { isHome = true }
                        NewsChip("アウェイ", !isHome, modifier = Modifier.weight(1f)) { isHome = false }
                    }
                }
                GameFields(
                    dateText = dateText, onDate = { dateText = it },
                    timeText = timeText, onTime = { timeText = it },
                    opponent = opponent, onOpponent = { opponent = it },
                    venue = venue, onVenue = { venue = it }
                )
                Button(
                    onClick = {
                        val date = parseInputDate(dateText)
                        val time = parseInputTime(timeText)
                        when {
                            date == null -> message = "日付を「2026/10/17」の形で入れてください"
                            time == null -> message = "開始時刻を「09:30」の形で入れてください(分からなければ空にしてください)"
                            addEntry(date, time, opponent, venue) -> {
                                message = "追加しました。"
                                dateText = ""; timeText = ""; opponent = ""; venue = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("日程に追加", fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(onClick = { close() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (addedCount > 0) "閉じる(${addedCount}件追加しました)" else "閉じる")
            }
        }
    }
}

/**
 * 自分で追加した予定の「足りないところを補足」の画面(下から出る)。
 * 画像やXの投稿から追加した予定に、あとから時刻・相手・会場・ホーム/アウェイを書き足す。
 */
@Composable
fun MyGameEditSheet(gameId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entry = remember(gameId) { MyGamesRepository.find(context, gameId) }
    if (entry == null) {
        LaunchedEffect(gameId) { onDismiss() }
        return
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var dateText by remember { mutableStateOf("${entry.date.year}/${entry.date.monthValue}/${entry.date.dayOfMonth}") }
    var timeText by remember { mutableStateOf(entry.time.takeIf { it != "時間未定" }.orEmpty()) }
    var opponent by remember { mutableStateOf(entry.opponent) }
    var venue by remember { mutableStateOf(entry.venue) }
    var isHome by remember { mutableStateOf(entry.isHome) }
    var message by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Paper) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("足りないところを補足", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${entry.team.shortName}・自分で追加した予定です。分かったところだけ書き足してください(空のままなら「未定」のまま)。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            GameFields(
                dateText = dateText, onDate = { dateText = it },
                timeText = timeText, onTime = { timeText = it },
                opponent = opponent, onOpponent = { opponent = it },
                venue = venue, onVenue = { venue = it }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NewsChip("ホーム", isHome, modifier = Modifier.weight(1f)) { isHome = true }
                NewsChip("アウェイ", !isHome, modifier = Modifier.weight(1f)) { isHome = false }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NewsRed) }
            Button(
                onClick = {
                    val date = parseInputDate(dateText)
                    val time = parseInputTime(timeText)
                    when {
                        date == null -> message = "日付を「2026/10/17」の形で入れてください"
                        time == null -> message = "開始時刻を「09:30」の形で入れてください(分からなければ空にしてください)"
                        else -> {
                            MyGamesRepository.update(
                                context,
                                entry.copy(date = date, time = time, opponent = opponent.trim(), venue = venue.trim(), isHome = isHome)
                            )
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存する", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("閉じる")
            }
        }
    }
}

/** 日付・開始時刻・相手・会場の入力欄(追加と補足で共通) */
@Composable
private fun GameFields(
    dateText: String, onDate: (String) -> Unit,
    timeText: String, onTime: (String) -> Unit,
    opponent: String, onOpponent: (String) -> Unit,
    venue: String, onVenue: (String) -> Unit
) {
    OutlinedTextField(
        value = dateText, onValueChange = onDate, singleLine = true,
        label = { Text("日付(例 2026/10/17)") }, modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = timeText, onValueChange = onTime, singleLine = true,
        label = { Text("開始時刻(例 09:30。空なら時間未定)") }, modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = opponent, onValueChange = onOpponent, singleLine = true,
        label = { Text("相手(例 【U-20】Laufen)") }, modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = venue, onValueChange = onVenue, singleLine = true,
        label = { Text("会場(空なら会場未定)") }, modifier = Modifier.fillMaxWidth()
    )
}

/** 候補を見分けるための文字(同じ候補を2回追加しないため) */
private fun addCandidateKey(c: GameCandidate): String = "${c.date}|${c.time}|${c.opponent}"

/** 相手の名前を比べやすい形にする(【U-20】などの札・空白を除き、小文字にする) */
private fun normalizeOpponent(name: String): String =
    name.replace(Regex("""【[^】]*】"""), "").replace(Regex("""[\s　]"""), "").lowercase()

/**
 * 同じ試合がすでに日程(公式の日程と、自分で追加した予定)にあるか。
 * 同じチーム・同じ日で、時刻が分かっていれば時刻も、相手が分かっていれば相手も同じなら「ある」とみなす。
 */
private fun alreadyInSchedule(team: Team, date: LocalDate, time: String, opponent: String): Boolean {
    val label = "${date.year}/${date.monthValue}/${date.dayOfMonth}"
    val t = time.trimStart('0')
    val opp = normalizeOpponent(opponent)
    return GamesRepository.games.any { g ->
        g.team == team && g.dateLabel == label &&
            (time == "時間未定" || g.timeLabel.trimStart('0') == t) &&
            (opp.isBlank() || normalizeOpponent(g.opponent).let { it.isNotBlank() && (it.contains(opp) || opp.contains(it)) })
    }
}

/** 「2026/10/17」「2026-10-17」「10/17」(年を省くと今年。1か月以上前になるなら来年)。読めなければ null */
private fun parseInputDate(text: String): LocalDate? {
    val t = text.trim().replace("／", "/").replace("-", "/").replace(".", "/")
    val parts = t.split("/").mapNotNull { it.trim().toIntOrNull() }
    return runCatching {
        when (parts.size) {
            3 -> LocalDate.of(parts[0], parts[1], parts[2])
            2 -> {
                val today = LocalDate.now()
                val d = LocalDate.of(today.year, parts[0], parts[1])
                if (d.isBefore(today.minusDays(30))) d.plusYears(1) else d
            }
            else -> null
        }
    }.getOrNull()
}

/** 「9:30」→「09:30」、空なら「時間未定」。読めなければ null */
private fun parseInputTime(text: String): String? {
    val t = text.trim().replace("：", ":")
    if (t.isBlank() || t == "時間未定") return "時間未定"
    val m = Regex("""(\d{1,2}):(\d{2})""").matchEntire(t) ?: return null
    val h = m.groupValues[1].toInt()
    val mi = m.groupValues[2].toInt()
    return if (h in 0..23 && mi in 0..59) "%02d:%02d".format(h, mi) else null
}

/**
 * Xの投稿を、Xの公式の埋め込みの形(画像付き)で表示する。
 * 中のリンクを押したときは、この中では開かずにブラウザ・Xのアプリで開く。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun XEmbedView(embedHtml: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val page = """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>body{margin:0;padding:4px;background:#FBF9F4;}</style></head>
        <body>$embedHtml
        <script async src="https://platform.twitter.com/widgets.js" charset="utf-8"></script>
        </body></html>
    """.trimIndent()
    AndroidView(
        modifier = modifier.border(1.dp, LineGray),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        // 埋め込みの中身(枠の中の読み込み)はそのまま、押されたリンクだけ外で開く
                        if (request?.isForMainFrame == true) {
                            openExternalUrl(context, request.url.toString())
                            return true
                        }
                        return false
                    }
                }
                loadDataWithBaseURL("https://platform.twitter.com", page, "text/html", "utf-8", null)
            }
        }
    )
}
