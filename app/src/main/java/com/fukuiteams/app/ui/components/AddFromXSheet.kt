package com.fukuiteams.app.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fukuiteams.app.data.GameCandidate
import com.fukuiteams.app.data.MyGameEntry
import com.fukuiteams.app.data.MyGamesRepository
import com.fukuiteams.app.data.XPost
import com.fukuiteams.app.data.XPostRepository
import com.fukuiteams.app.data.extractCandidates
import com.fukuiteams.app.data.findXStatusUrl
import com.fukuiteams.app.data.guessTeam
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
 * Xの投稿から予定を追加する画面(下から出る)。
 * 1. XのURLを貼る(または「共有」で送られてきたURL)→「読み込む」
 * 2. 投稿の本文と、投稿そのもの(画像付き)を表示。本文から日付・時刻・相手を探して候補を出す
 * 3. 候補を押すと入力欄に入る。確かめて(直して)「日程に追加」→ 試合タブに並ぶ(この端末の中だけ)
 * 本文に日程が無い投稿(画像だけの投稿など)は、表示された投稿を見ながら手で入力する。
 */
@Composable
fun AddFromXSheet(
    initialText: String?,
    defaultTeam: Team,
    onDismiss: () -> Unit,
    onAdded: (gameId: String, team: Team) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var urlText by remember { mutableStateOf(findXStatusUrl(initialText) ?: initialText.orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var post by remember { mutableStateOf<XPost?>(null) }
    var candidates by remember { mutableStateOf<List<GameCandidate>>(emptyList()) }
    var showEmbed by remember { mutableStateOf(true) }

    // 入力欄
    var team by remember { mutableStateOf(defaultTeam) }
    var dateText by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }
    var opponent by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf("") }
    var isHome by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    fun load() {
        val url = findXStatusUrl(urlText)
        if (url == null) {
            error = "XのURLが見つかりません。「https://x.com/○○/status/数字」の形のURLを貼ってください"
            return
        }
        urlText = url
        loading = true
        error = null
        post = null
        candidates = emptyList()
        scope.launch {
            try {
                val p = XPostRepository.fetch(url)
                post = p
                candidates = extractCandidates(p.text, p.postedOn)
                guessTeam(p.text, p.authorName + " " + p.authorHandle)?.let { team = it }
                // 候補が1つだけなら、最初から入力欄に入れておく
                candidates.singleOrNull()?.let { c ->
                    dateText = "${c.date.year}/${c.date.monthValue}/${c.date.dayOfMonth}"
                    timeText = c.time
                    if (c.opponent.isNotBlank()) opponent = c.opponent
                }
            } catch (e: Exception) {
                error = e.message ?: "投稿を読めませんでした"
            } finally {
                loading = false
            }
        }
    }

    // 共有で送られてきたURLは、開いたらすぐ読み込む
    LaunchedEffect(Unit) {
        if (findXStatusUrl(initialText) != null) load()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Paper) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Xの投稿から予定を追加", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "U-20の大会など、公式の日程に載らない試合を自分で追加できます。追加した予定はこのスマホの中だけに保存されます。",
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
                onClick = { load() },
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "読み込み中…" else "読み込む")
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NewsRed) }

            post?.let { p ->
                // 投稿の本文(長押しで文字を選べる)
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

                Text("本文から見つけた日程の候補", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (candidates.isEmpty()) {
                    Text(
                        "本文に日付が見つかりませんでした(日程が画像だけに書かれている投稿など)。上の投稿を見ながら、下に入力してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                } else {
                    Text("押すと下の入力欄に入ります。", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    candidates.forEach { c ->
                        NewsChip(c.label, false, modifier = Modifier.fillMaxWidth()) {
                            dateText = "${c.date.year}/${c.date.monthValue}/${c.date.dayOfMonth}"
                            timeText = c.time
                            if (c.opponent.isNotBlank()) opponent = c.opponent
                            message = null
                        }
                    }
                }
            }

            // 入力欄(投稿を読まなくても、手で入れて追加できる)
            Text("追加する予定", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Team.values().forEach { t ->
                    NewsChip(t.displayName, team == t, modifier = Modifier.weight(1f)) { team = t }
                }
            }
            OutlinedTextField(
                value = dateText, onValueChange = { dateText = it }, singleLine = true,
                label = { Text("日付(例 2026/10/17)") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = timeText, onValueChange = { timeText = it }, singleLine = true,
                label = { Text("開始時刻(例 09:30。空なら時間未定)") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = opponent, onValueChange = { opponent = it }, singleLine = true,
                label = { Text("相手(例 【U-20】Laufen)") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = venue, onValueChange = { venue = it }, singleLine = true,
                label = { Text("会場(空なら会場未定)") }, modifier = Modifier.fillMaxWidth()
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
                            val entry = MyGameEntry(
                                id = MyGamesRepository.newId(),
                                team = team,
                                date = date,
                                time = time,
                                opponent = opponent.trim(),
                                venue = venue.trim(),
                                isHome = isHome,
                                sourceUrl = post?.url ?: findXStatusUrl(urlText).orEmpty()
                            )
                            if (MyGamesRepository.add(context, entry)) {
                                onAdded(entry.id, team)
                            } else {
                                message = "同じ予定がすでに追加されています"
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("日程に追加", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("閉じる")
            }
        }
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
