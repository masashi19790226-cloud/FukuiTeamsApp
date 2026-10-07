package com.fukuiteams.app.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 一面の特集「掲示板の話題」の材料。ブローウィンズ掲示板の最近の投稿から、
 * 24時間の書き込み数・ふだんの1日あたりの件数・よく出た言葉・反応(返信といいね)が多かった投稿を数える。
 * 文章を作ったり要約したりはしない(数えた結果と、投稿の冒頭だけを出す)。
 * NGワード・NGユーザーに当たる投稿は数えない。
 */
data class BbsBuzz(
    /** この24時間の書き込み数 */
    val count24h: Int,
    /** 読み込めた範囲がすべて24時間以内だった(実際はもっと多い)とき true */
    val atLeast: Boolean,
    /** ふだん(24時間より前の数日)の1日あたりの書き込み数。分からなければ null */
    val usualPerDay: Double?,
    /** この24時間によく出た言葉(多い順に最大3つ) */
    val words: List<String>,
    /** この24時間で反応がいちばん多かった投稿(無ければ null) */
    val topPost: BbsPost?,
    val topReplies: Int
)

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

/** 何日前までの投稿を読むか(ふだんの件数を出すため) */
private const val LOOKBACK_DAYS = 4L

/** 読み直す間隔(一面を更新するたびに掲示板を何ページも読まないようにする) */
private const val CACHE_MILLIS = 30 * 60_000L

object BbsBuzzRepository {
    private val mutex = Mutex()
    private var cached: BbsBuzz? = null
    private var cachedAtMillis = 0L
    private var cachedPosts: List<BbsPost> = emptyList()
    private var cachedReachedSince = false

    /**
     * 掲示板の話題を数える。メニューで「一面の特集に掲示板の話題を出す」をオフにしていれば null。
     * 読めなかったときも null(一面の特集に出ないだけ)。
     * surnames:ブローウィンズの選手の名字(よく出た言葉に選手名を拾うため)
     */
    suspend fun fetch(context: Context, surnames: List<String>, force: Boolean = false): BbsBuzz? = mutex.withLock {
        val settings = BbsPrefs.load(context)
        if (!settings.featureEnabled) return@withLock null
        val now = LocalDateTime.now(JST)
        // 30分以内に読んだ投稿があれば、掲示板は読み直さずに数え直すだけにする(選手の名字やNGが変わっても反映される)
        if (!force && cachedPosts.isNotEmpty() && System.currentTimeMillis() - cachedAtMillis < CACHE_MILLIS) {
            return@withLock analyze(cachedPosts.filterNot { it.isNg(settings) }, now, surnames, cachedReachedSince)
        }
        try {
            val posts = BbsRepository.fetchRecentForFeature(since = now.minusDays(LOOKBACK_DAYS))
            val reached = posts.any { it.localDateTime?.isBefore(now.minusDays(LOOKBACK_DAYS)) == true }
            cachedPosts = posts
            cachedReachedSince = reached
            cachedAtMillis = System.currentTimeMillis()
            analyze(posts.filterNot { it.isNg(settings) }, now, surnames, reached).also { cached = it }
        } catch (e: Exception) {
            cached
        }
    }

    /** 数える本体(通信しない)。 */
    fun analyze(posts: List<BbsPost>, now: LocalDateTime, surnames: List<String>, reachedSince: Boolean): BbsBuzz {
        val border = now.minusHours(24)
        val recent = posts.filter { it.localDateTime?.isBefore(border) == false }
        val atLeast = recent.isNotEmpty() && recent.size == posts.size && !reachedSince

        // ふだんの件数:24時間より前の投稿を、その期間の日数で割る
        val since = now.minusDays(LOOKBACK_DAYS)
        val older = posts.filter { p -> p.localDateTime?.let { it.isBefore(border) && !it.isBefore(since) } == true }
        val spanStart = if (reachedSince) since else older.mapNotNull { it.localDateTime }.minOrNull()
        val usual = spanStart?.let {
            val days = Duration.between(it, border).toMinutes() / (24.0 * 60)
            if (days >= 0.5) older.size / days else null
        }

        // 反応の多い投稿:返信の数(読み込んだ投稿の「>>番号」)×2+いいね
        val replyCounts = HashMap<Int, Int>()
        posts.forEach { p -> p.anchors.forEach { a -> if (a != p.no) replyCounts[a] = (replyCounts[a] ?: 0) + 1 } }
        val top = recent
            .filter { cleanBody(it.body).isNotBlank() }
            .maxByOrNull { (replyCounts[it.no] ?: 0) * 2 + it.likes }
            ?.takeIf { (replyCounts[it.no] ?: 0) * 2 + it.likes >= 2 }

        return BbsBuzz(
            count24h = recent.size,
            atLeast = atLeast,
            usualPerDay = usual,
            words = topWords(recent, surnames),
            topPost = top,
            topReplies = top?.let { replyCounts[it.no] ?: 0 } ?: 0
        )
    }

    /** 本文から「>>番号」・URL・余分な空白を除いたもの */
    fun cleanBody(body: String): String = body
        .replace(Regex("(?:>>|＞＞|≫)\\s*\\d+"), " ")
        .replace(Regex("https?://\\S+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** 投稿の冒頭(特集に出す短い抜粋) */
    fun excerpt(post: BbsPost, max: Int = 40): String {
        val t = cleanBody(post.body)
        return if (t.length > max) t.take(max) + "…" else t
    }

    /**
     * よく出た言葉。2件以上の投稿に出てきた言葉を、出てきた投稿の数が多い順に3つ。
     * 言葉は「選手の名字」「カタカナ3文字以上」「漢字2〜6文字」「英数字(3P・HCなど)」を拾い、ありふれた言葉は除く。
     */
    private fun topWords(posts: List<BbsPost>, surnames: List<String>): List<String> {
        val df = HashMap<String, Int>()
        posts.forEach { p ->
            val text = Normalizer.normalize(cleanBody(p.body), Normalizer.Form.NFKC)
            val words = HashSet<String>()
            surnames.filter { it.length >= 2 && text.contains(it) }.forEach { words += it }
            KATAKANA.findAll(text).map { it.value }.filter { it.length >= 3 && it !in STOP_WORDS }.forEach { words += it }
            KANJI.findAll(text).map { it.value.removeSuffix("選手").removeSuffix("君") }
                .filter { it.length in 2..6 && it !in STOP_WORDS && words.none { w -> w != it && it.contains(w) } }
                .forEach { words += it }
            ALNUM.findAll(text).map { it.value.uppercase() }
                .filter { it.any { c -> c.isLetter() } && it.length in 2..6 && it !in STOP_ALNUM }
                .forEach { words += it }
            words.forEach { df[it] = (df[it] ?: 0) + 1 }
        }
        val chosen = mutableListOf<String>()
        df.entries.filter { it.value >= 2 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
            .forEach { (w, _) ->
                if (chosen.size < 3 && chosen.none { it.contains(w) || w.contains(it) }) chosen += w
            }
        return chosen
    }

    private val KATAKANA = Regex("[ァ-ヶー]+")
    private val KANJI = Regex("[一-龠々]+")
    private val ALNUM = Regex("[A-Za-z0-9]+")

    private val STOP_ALNUM = setOf("HTTP", "HTTPS", "WWW", "COM", "JP", "HTML", "PHP", "BBS", "NO")

    /** ありふれていて「話題」にならない言葉 */
    private val STOP_WORDS = setOf(
        // 漢字
        "今日", "明日", "昨日", "今年", "今季", "去年", "昨季", "来季", "来年", "試合", "選手", "自分", "本当", "感じ",
        "応援", "時間", "場合", "必要", "一番", "相手", "普通", "気持", "大事", "最後", "最初", "皆様", "今回", "前回",
        "次回", "意味", "問題", "関係", "個人", "全員", "全部", "今後", "以上", "以下", "程度", "部分", "理由", "結果",
        "内容", "情報", "投稿", "掲示板", "本人", "方々", "一人", "多分", "絶対", "最近", "毎回", "今度", "確", "正直",
        "結局", "仕方", "普段", "先日", "当日", "本日", "是非", "一緒", "他人", "大丈夫", "期待", "残念", "頑張", "素晴",
        "何度", "皆", "人達", "可能", "出来", "勝利", "負", "勝", "福井", "会場", "観客", "観戦", "今", "後", "前",
        "気", "見", "思", "言", "行", "来", "出", "良", "悪", "多", "少", "上", "下", "中", "人", "方", "事", "時", "日",
        // カタカナ
        "ブローウィンズ", "ブロウィン", "ブローウィン", "ブースター", "チーム", "ゲーム", "プレー", "プレイ", "バスケ",
        "ファン", "コメント", "シーズン", "スレッド", "スレ", "メンバー", "ホーム", "アウェイ", "アウェー", "ポイント",
        "レベル", "タイミング", "イメージ", "スタッフ", "フロント", "クラブ", "リーグ", "ハッピー", "ナイス", "ドンマイ",
        "ヤバい", "マジ", "ホント", "ダメ", "ムリ", "ウチ", "オレ", "コレ", "ソレ", "アレ", "ココ", "ソコ"
    )
}
