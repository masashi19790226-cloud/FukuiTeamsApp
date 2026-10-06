package com.fukuiteams.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.Charset

/**
 * 福井ブローウィンズ掲示板(j-basketball.club)を読み取る。
 * ・読むだけ。投稿・返信・いいねは公式ページをブラウザで開いて行う
 * ・読み込んだ投稿はアプリを開いている間だけメモリに置く(スマホには保存しない)
 * ・掲示板の更新は、アプリ全体の更新(DataRefresher)とは別に、掲示板タブからだけ行う
 */
object BbsRepository {

    const val BASE_URL = "https://j-basketball.club/bbs/blowinds/"
    const val WRITE_URL = BASE_URL + "write.php"

    /** 公式は1ページ15件。 */
    const val PAGE_SIZE = 15

    fun replyUrl(no: Int) = BASE_URL + "write.php?replay=$no"

    /** 読み込み済みの投稿(番号 → 投稿)。画面はこれを見て描き直す。 */
    var posts: Map<Int, BbsPost> by mutableStateOf(emptyMap())
        private set

    /** 「現在:○人閲覧中」の人数。 */
    var viewers: Int? by mutableStateOf(null)
        private set

    /** 最後に1ページ目を読み込めた時刻(ミリ秒)。 */
    var lastUpdatedMillis: Long? by mutableStateOf(null)
        private set

    var loading: Boolean by mutableStateOf(false)
        private set

    /** 最後の読み込みで失敗したときの説明(成功したら null)。 */
    var errorMessage: String? by mutableStateOf(null)
        private set

    private val mutex = Mutex()

    /** 拡大用の大きい画像のURL(見つけたものを覚えておく)。 */
    private val fullImageCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * 最新の投稿を count 件ぶん読み込む(1ページ目から必要なページ数を順に)。
     * すでに読み込み中なら何もしない。成功したら true。
     */
    suspend fun refresh(count: Int): Boolean {
        if (!mutex.tryLock()) return false
        loading = true
        try {
            val pages = ((count + PAGE_SIZE - 1) / PAGE_SIZE).coerceIn(1, 10)
            val found = mutableListOf<BbsPost>()
            var firstViewers: Int? = null
            for (p in 1..pages) {
                if (p > 1) delay(300)
                val page = withContext(Dispatchers.IO) { fetchPage(pageUrl(p)) }
                if (p == 1) firstViewers = page.viewers
                if (page.posts.isEmpty()) break
                found += page.posts
            }
            if (found.isEmpty()) {
                errorMessage = "掲示板を読み込めませんでした(投稿が見つかりません)"
                return false
            }
            merge(found)
            if (firstViewers != null) viewers = firstViewers
            lastUpdatedMillis = System.currentTimeMillis()
            errorMessage = null
            return true
        } catch (e: Exception) {
            errorMessage = "掲示板を読み込めませんでした(通信できないか、ページの形が変わった可能性があります)"
            return false
        } finally {
            loading = false
            mutex.unlock()
        }
    }

    /**
     * 読み込み済みより古い投稿を、およそ count 件ぶん追加で読み込む(検索の「過去100件を読み込む」)。
     * 追加できた件数を返す。
     */
    suspend fun loadOlder(count: Int = 100): Int {
        if (!mutex.tryLock()) return 0
        loading = true
        try {
            val current = posts
            if (current.isEmpty()) return 0
            val newest = current.keys.max()
            val oldest = current.keys.min()
            // 削除された投稿がなければ、No.oldest は (newest - oldest) / 15 + 1 ページ目にある
            var page = ((newest - oldest) / PAGE_SIZE + 1).coerceAtLeast(1)
            var added = 0
            var tries = 0
            val found = mutableListOf<BbsPost>()
            while (added < count && tries < (count / PAGE_SIZE) + 4) {
                if (tries > 0) delay(300)
                tries++
                val result = withContext(Dispatchers.IO) { fetchPage(pageUrl(page)) }
                if (result.posts.isEmpty()) break
                val fresh = result.posts.filter { it.no !in current && found.none { f -> f.no == it.no } }
                found += fresh
                added += fresh.size
                page++
            }
            if (found.isNotEmpty()) merge(found)
            errorMessage = null
            return added
        } catch (e: Exception) {
            errorMessage = "過去の投稿を読み込めませんでした"
            return 0
        } finally {
            loading = false
            mutex.unlock()
        }
    }

    /**
     * 「>>番号」の投稿を探す。読み込み済みならそれを、無ければ公式の ?anc=番号 のページから取る。
     */
    suspend fun findPost(no: Int): BbsPost? {
        posts[no]?.let { return it }
        return try {
            val page = withContext(Dispatchers.IO) { fetchPage(BASE_URL + "?anc=$no") }
            if (page.posts.isNotEmpty()) merge(page.posts)
            page.posts.firstOrNull { it.no == no }
        } catch (e: Exception) {
            null
        }
    }

    /** 1件だけ公式の ?anc=番号 のページから読み直す(いいねを押したあと、いいね数を反映するため)。 */
    suspend fun reloadPost(no: Int) {
        try {
            val page = withContext(Dispatchers.IO) { fetchPage(BASE_URL + "?anc=$no") }
            if (page.posts.isNotEmpty()) merge(page.posts)
        } catch (e: Exception) {
            // 読み直せなくても、次の更新で反映される
        }
    }

    /**
     * 添付画像の大きい版のURL。一覧の画像は小さな縮小版なので、
     * 縮小版のURLから元の画像の場所が分かればそれを、分からなければ公式の画像ページ(fileview.php)から探す。
     */
    suspend fun fullImageUrl(image: BbsImage): String? {
        fullImageCache[image.thumbUrl]?.let { return it }
        BbsParser.fullImageFromThumb(image.thumbUrl)?.let {
            fullImageCache[image.thumbUrl] = it
            return it
        }
        if (image.viewUrl.isBlank()) return null
        return try {
            val html = withContext(Dispatchers.IO) { download(image.viewUrl) }
            val found = BbsParser.fullImageFromViewPage(html, image.viewUrl)
            if (found != null) fullImageCache[image.thumbUrl] = found
            found
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 最新の投稿を、画面の表示とは別に読む(キーワード通知のバックグラウンド確認用)。
     * 1ページ目から順に読み、afterNo より古い投稿が出てきたら(または maxPages まで読んだら)やめる。
     * 画面に出している投稿・読み込み中の表示などには影響しない。読めなければ例外。
     */
    suspend fun fetchNewPostsForCheck(afterNo: Int, maxPages: Int = 3): List<BbsPost> {
        val found = mutableListOf<BbsPost>()
        for (p in 1..maxPages) {
            if (p > 1) delay(300)
            val page = withContext(Dispatchers.IO) { fetchPage(pageUrl(p)) }
            if (page.posts.isEmpty()) break
            found += page.posts
            if (afterNo <= 0 || page.posts.any { it.no <= afterNo }) break
        }
        return found.distinctBy { it.no }
    }

    private fun pageUrl(page: Int) = if (page <= 1) BASE_URL else BASE_URL + "?page=$page"

    private fun merge(found: List<BbsPost>) {
        val next = posts.toMutableMap()
        found.forEach { next[it.no] = it }
        posts = next
    }

    private fun fetchPage(url: String): BbsParsedPage = BbsParser.parse(download(url))

    /** ページをダウンロードして文字列にする(掲示板は Shift_JIS)。 */
    private fun download(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        )
        c.setRequestProperty("Accept-Language", "ja,en;q=0.8")
        try {
            val bytes = c.inputStream.use { it.readBytes() }
            return String(bytes, BbsParser.charset)
        } finally {
            c.disconnect()
        }
    }
}

/** 添付画像。thumbUrl は一覧の縮小画像、viewUrl は公式の画像ページ(無ければ空)。 */
data class BbsImage(val thumbUrl: String, val viewUrl: String)

/** 掲示板の投稿1件。 */
data class BbsPost(
    val no: Int,
    val name: String,
    /** 投稿者を見分けるキー(公式のユーザーページの id。無いときは「name:名前」)。 */
    val userKey: String,
    /** 「2026/10/05 18:38」の形。 */
    val dateTime: String,
    /** 「ID:xxxx」の xxxx。 */
    val posterId: String,
    val body: String,
    val images: List<BbsImage>,
    val favorite: String,
    val likes: Int
) {
    /** 投稿日(日本時間)。読めなければ null。 */
    val date: java.time.LocalDate? by lazy { BbsParser.parseDate(dateTime)?.toLocalDate() }

    /** 投稿日時(日本時間)。読めなければ null。 */
    val localDateTime: java.time.LocalDateTime? by lazy { BbsParser.parseDate(dateTime) }

    /** 一覧用の短い日時「10/05 18:38」。 */
    val shortDateTime: String
        get() = localDateTime?.let { String.format(java.util.Locale.US, "%02d/%02d %02d:%02d", it.monthValue, it.dayOfMonth, it.hour, it.minute) }
            ?: dateTime

    /** 公式のユーザーページ(分かるときだけ)。 */
    val profileUrl: String?
        get() = if (userKey.startsWith("name:")) null else "https://j-basketball.club/bbs/user.php?id=$userKey"

    /** 本文中の「>>番号」の番号(重複なし)。 */
    val anchors: List<Int> by lazy { BbsParser.anchorsIn(body) }
}

data class BbsParsedPage(val posts: List<BbsPost>, val viewers: Int?)

// ===== BbsParser ここから(Android に依存しない部分) =====

/**
 * 掲示板のページの読み取り。ページ全体を「行」のテキストにしてから、
 * 「No.番号」の行と、同じ番号の返信リンクを手がかりに1件ずつ切り出す。
 * リンクと画像の位置は、行の中に目印(種類:値)として埋め込む。
 *   R:番号 … 返信リンク(write.php?replay=番号)
 *   U:id   … ユーザーリンク(bbs/user.php?id=…)
 *   I:画像URL|画像ページURL … 画像
 */
object BbsParser {

    private const val BASE = "https://j-basketball.club/bbs/blowinds/"
    private const val M0 = ''
    private const val M1 = ''

    /** windows-31j を優先し、使えなければ Shift_JIS。 */
    val charset: Charset = try {
        Charset.forName("windows-31j")
    } catch (e: Exception) {
        Charset.forName("Shift_JIS")
    }

    private val BLOCK_TAGS = setOf(
        "div", "p", "li", "ul", "ol", "tr", "table", "h1", "h2", "h3", "h4", "h5", "h6",
        "header", "footer", "article", "section", "dt", "dd", "dl", "hr", "form",
        "blockquote", "pre", "nav", "main", "aside"
    )
    private val SPACES = Regex("[\\r\\n\\t ]+")
    private val MARK = Regex("$M0([RUI]):([^$M1]*)$M1")
    private val NO_LINE = Regex("^No\\.(\\d+)$")
    private val DATE_LINE = Regex("(\\d{4}/\\d{1,2}/\\d{1,2}\\s+\\d{1,2}:\\d{2})(?:\\s*ID[:：]\\s*(\\S+))?")
    private val FAVORITE = Regex("好きな選手\\s*[:：]\\s*(.*)")
    private val LIKES = Regex("いいね\\s*(\\d+)")
    private val VIEWERS = Regex("現在[:：]\\s*(\\d+)\\s*人閲覧中")
    private val REPLAY = Regex("write\\.php\\?replay=(\\d+)")
    private val USER_ID = Regex("user\\.php\\?id=([^&#\"]+)")
    private val ANCHOR = Regex("(?:>>|＞＞|≫)\\s*(\\d+)")
    private val DATE_PARTS = Regex("(\\d{4})/(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})")

    fun parse(html: String): BbsParsedPage {
        val lines = toLines(html)
        val plain = lines.map { stripMarks(it) }
        val viewers = plain.firstNotNullOfOrNull { VIEWERS.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val posts = mutableListOf<BbsPost>()
        for (i in lines.indices) {
            val no = NO_LINE.matchEntire(plain[i])?.groupValues?.get(1)?.toIntOrNull() ?: continue
            // 同じ番号の返信リンクの目印がある行(=その投稿の終わり)を探す
            val replyMark = "${M0}R:$no$M1"
            var end = -1
            for (k in i + 1 until minOf(lines.size, i + 300)) {
                if (NO_LINE.matches(plain[k])) break
                if (lines[k].contains(replyMark)) {
                    end = k
                    break
                }
            }
            if (end < 0) continue

            // 直前の「2026/10/05 18:38 ID:xxxx」の行
            var dateIndex = -1
            var dateMatch: MatchResult? = null
            for (j in i - 1 downTo maxOf(0, i - 5)) {
                val m = DATE_LINE.find(plain[j])
                if (m != null) {
                    dateIndex = j
                    dateMatch = m
                    break
                }
            }
            // その前の行が名前(ユーザーリンクの id を userKey にする)
            var name = ""
            var userKey = ""
            if (dateIndex >= 0) {
                for (j in dateIndex - 1 downTo maxOf(0, dateIndex - 4)) {
                    if (userKey.isEmpty()) {
                        MARK.findAll(lines[j]).firstOrNull { it.groupValues[1] == "U" }?.let { userKey = it.groupValues[2] }
                    }
                    if (name.isEmpty() && plain[j].isNotEmpty() && !NO_LINE.matches(plain[j])) name = plain[j]
                    if (name.isNotEmpty() && userKey.isNotEmpty()) break
                }
            }

            // No行から返信リンク(または「好きな選手」の行)までが本文
            val bodyLines = mutableListOf<String>()
            val images = mutableListOf<BbsImage>()
            var favorite = ""
            for (k in i + 1 until end) {
                val fav = FAVORITE.find(plain[k])
                if (fav != null) {
                    favorite = fav.groupValues[1].trim()
                    break
                }
                MARK.findAll(lines[k]).filter { it.groupValues[1] == "I" }.forEach { m ->
                    val parts = m.groupValues[2].split('|')
                    images += BbsImage(parts[0], parts.getOrElse(1) { "" })
                }
                bodyLines += plain[k]
            }
            // 返信リンク付近の「いいね数字」
            var likes = 0
            for (k in end until minOf(lines.size, end + 4)) {
                val m = LIKES.find(plain[k])
                if (m != null) {
                    likes = m.groupValues[1].toIntOrNull() ?: 0
                    break
                }
            }
            val body = bodyLines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim('\n', ' ')
            if (userKey.isNotEmpty()) userKey = decodeKey(userKey)
            posts += BbsPost(
                no = no,
                name = name,
                userKey = userKey.ifEmpty { "name:$name" },
                dateTime = dateMatch?.groupValues?.get(1)?.replace(Regex("\\s+"), " ") ?: "",
                posterId = dateMatch?.groupValues?.get(2) ?: "",
                body = body,
                images = images.distinctBy { it.thumbUrl },
                favorite = favorite,
                likes = likes
            )
        }
        return BbsParsedPage(posts.distinctBy { it.no }, viewers)
    }

    /** id は URL エンコードされたまま(%3D など)届くので、一度戻してから使う。 */
    private fun decodeKey(raw: String): String = try {
        java.net.URLEncoder.encode(URLDecoder.decode(raw, "UTF-8"), "UTF-8")
    } catch (e: Exception) {
        raw
    }

    /** ページ全体をテキストの行にする。br とブロック要素で改行し、リンクと画像の位置に目印を入れる。 */
    fun toLines(html: String): List<String> {
        val doc = Jsoup.parse(html, BASE)
        doc.select("script, style, noscript, template").remove()
        val sb = StringBuilder()
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                when (node) {
                    is TextNode -> sb.append(node.wholeText.replace(SPACES, " "))
                    is Element -> {
                        val tag = node.normalName()
                        if (tag in BLOCK_TAGS || tag == "br") sb.append('\n')
                        if (tag == "a") {
                            val href = node.attr("href")
                            val reply = REPLAY.find(href)
                            if (reply != null) {
                                sb.append(M0).append("R:").append(reply.groupValues[1]).append(M1)
                            } else {
                                USER_ID.find(href)?.let { sb.append(M0).append("U:").append(it.groupValues[1]).append(M1) }
                            }
                        }
                        if (tag == "img") {
                            val src = node.absUrl("src")
                            if (src.isNotBlank() && !isSiteImage(src)) {
                                val parent = node.parent()
                                val view = if (parent != null && parent.normalName() == "a" && parent.attr("href").contains("fileview.php")) {
                                    parent.absUrl("href")
                                } else ""
                                sb.append(M0).append("I:").append(src.replace("|", "%7C")).append('|').append(view).append(M1)
                            }
                        }
                    }
                }
            }

            override fun tail(node: Node, depth: Int) {
                if (node is Element && node.normalName() in BLOCK_TAGS) sb.append('\n')
            }
        }, doc)
        return sb.split('\n').map { it.trim(' ') }
    }

    /** アイコン・ロゴなど、投稿の添付ではない画像。 */
    private fun isSiteImage(src: String): Boolean =
        src.contains("/bbs/img/") || src.contains("user_icons") || src.contains("/material/")

    private fun stripMarks(line: String): String = line.replace(MARK, "").trim(' ')

    /** 縮小画像(fitimg/resize.php?img=/var/www/html/j-basketball.club/…)から元の画像のURLを作る。 */
    fun fullImageFromThumb(thumb: String): String? {
        val raw = Regex("[?&]img=([^&]+)").find(thumb)?.groupValues?.get(1) ?: return null
        val path = try { URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
        val marker = "/j-basketball.club/"
        val at = path.indexOf(marker)
        if (at < 0 || !path.contains("/bbs/data/")) return null
        return "https://j-basketball.club/" + path.substring(at + marker.length)
    }

    /** 公式の画像ページ(fileview.php)から、元の画像(/bbs/data/…)のURLを探す。 */
    fun fullImageFromViewPage(html: String, pageUrl: String): String? {
        val doc = Jsoup.parse(html, pageUrl)
        return doc.select("img[src]").map { it.absUrl("src") }.firstOrNull { it.contains("/bbs/data/") && !it.contains("/material/") }
    }

    fun parseDate(text: String): java.time.LocalDateTime? {
        val m = DATE_PARTS.find(text) ?: return null
        val v = m.groupValues.drop(1).map { it.toInt() }
        return try {
            java.time.LocalDateTime.of(v[0], v[1], v[2], v[3], v[4])
        } catch (e: Exception) {
            null
        }
    }

    fun anchorsIn(body: String): List<Int> =
        ANCHOR.findAll(body).mapNotNull { it.groupValues[1].toIntOrNull() }.distinct().toList()
}

// ===== BbsParser ここまで =====
