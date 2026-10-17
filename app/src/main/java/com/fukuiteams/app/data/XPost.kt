package com.fukuiteams.app.data

import com.fukuiteams.app.model.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Xの投稿(URLで指定)を、Xが埋め込み用に公開している窓口(oEmbed)で読む。
 * ログインやキーは不要。読めるのは公開の投稿の本文・投稿者・投稿日だけ(画像の中の文字は読めない)。
 */
data class XPost(
    /** 「https://x.com/ユーザー名/status/番号」の形にそろえたURL */
    val url: String,
    val authorName: String,
    val authorHandle: String,
    /** 本文(改行あり) */
    val text: String,
    /** 投稿日(読めなければ null) */
    val postedOn: LocalDate?,
    /** 埋め込み表示用のHTML(Xの公開の形のまま) */
    val embedHtml: String
)

/** 日程の候補(本文から読み取ったもの) */
data class GameCandidate(
    val date: LocalDate,
    /** 「09:30」の形。無ければ空 */
    val time: String,
    /** 相手の名前(「vs ○○」などから。無ければ空) */
    val opponent: String,
    /** 候補を見つけた行(確かめる用) */
    val line: String
) {
    val label: String
        get() {
            val week = listOf("月", "火", "水", "木", "金", "土", "日")[date.dayOfWeek.value - 1]
            return listOf("${date.monthValue}/${date.dayOfMonth}($week)", time, if (opponent.isNotBlank()) "vs $opponent" else "")
                .filter { it.isNotBlank() }.joinToString(" ")
        }
}

private val X_STATUS_URL = Regex("""https?://(?:www\.|mobile\.)?(?:x|twitter)\.com/([A-Za-z0-9_]{1,15})/status(?:es)?/(\d+)""")

/** 文の中からXの投稿のURLを探し、「https://x.com/ユーザー名/status/番号」の形にする。無ければ null */
fun findXStatusUrl(text: String?): String? {
    val m = X_STATUS_URL.find(text ?: return null) ?: return null
    return "https://x.com/${m.groupValues[1]}/status/${m.groupValues[2]}"
}

object XPostRepository {
    /** 投稿を読む。読めなければ、理由を書いた例外 */
    suspend fun fetch(url: String): XPost = withContext(Dispatchers.IO) {
        val m = X_STATUS_URL.find(url) ?: throw IllegalArgumentException("Xの投稿のURLではありません")
        val handle = m.groupValues[1]
        val tweetUrl = "https://twitter.com/$handle/status/${m.groupValues[2]}"
        val api = "https://publish.twitter.com/oembed?url=" + URLEncoder.encode(tweetUrl, "UTF-8") +
            "&omit_script=true&lang=ja&dnt=true"
        val c = URL(api).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        try {
            val code = c.responseCode
            if (code == 404 || code == 403) {
                throw IllegalStateException("投稿を読めませんでした(削除された投稿・鍵付きのアカウントの投稿は読めません)")
            }
            if (code != 200) throw IllegalStateException("投稿を読めませんでした(エラー $code)")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val html = o.optString("html")
            val doc = Jsoup.parseBodyFragment(html)
            val p = doc.selectFirst("blockquote p")
            // 改行(<br>)を残したまま本文にする
            val text = if (p != null) {
                Jsoup.parseBodyFragment(p.html().replace(Regex("""<br\s*/?>"""), "\n")).body().wholeText().trim()
            } else {
                ""
            }
            // 最後のリンクの文字が投稿日(例「October 17, 2026」)
            val dateText = doc.select("blockquote > a").lastOrNull()?.text().orEmpty()
            XPost(
                url = "https://x.com/$handle/status/${m.groupValues[2]}",
                authorName = o.optString("author_name"),
                authorHandle = handle,
                text = text,
                postedOn = parseEnglishDate(dateText),
                embedHtml = html
            )
        } finally {
            c.disconnect()
        }
    }
}

private val MONTHS = listOf(
    "january", "february", "march", "april", "may", "june",
    "july", "august", "september", "october", "november", "december"
)

/** 「October 17, 2026」の形の日付。読めなければ null */
private fun parseEnglishDate(text: String): LocalDate? {
    val m = Regex("""([A-Za-z]+)\s+(\d{1,2}),\s*(\d{4})""").find(text) ?: return null
    val month = MONTHS.indexOf(m.groupValues[1].lowercase()) + 1
    if (month <= 0) return null
    return runCatching { LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[2].toInt()) }.getOrNull()
}

/** 本文・投稿者の名前から、どのチームの投稿かを推し量る(分からなければ null) */
fun guessTeam(text: String, author: String): Team? {
    val all = "$author\n$text"
    return when {
        Regex("RUCK|ラック|丸岡", RegexOption.IGNORE_CASE).containsMatchIn(all) -> Team.RAC
        Regex("ブローウィンズ|BLOWINDS", RegexOption.IGNORE_CASE).containsMatchIn(all) -> Team.BLOWINDS
        Regex("ユナイテッド|UNITED", RegexOption.IGNORE_CASE).containsMatchIn(all) -> Team.UNITED
        else -> null
    }
}

// 「10/17」「10月17日」「2026/10/17」のほか、ポスターに多い「10.17(土)」も日付とみなす(点のときは曜日のかっこが続くものだけ)
private val DATE_RE = Regex("""(?:(20\d{2})\s*[年/.\-]\s*)?(\d{1,2})\s*(?:月|/|／|[.．](?=\s*\d{1,2}\s*[(（]))\s*(\d{1,2})\s*日?""")
private val TIME_RE = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})|(\d{1,2})\s*時\s*(?:(\d{1,2})\s*分)?""")
private val OPPONENT_RE = Regex("""(?:vs\.?|VS\.?|Vs\.?|ｖｓ|ＶＳ|対)\s*([^\s　、。,，@＠()（）\n]{1,30})""")

/**
 * 本文から日程の候補を探す(行ごとに「日付」と、同じ行か次の行の「時刻」「vs 相手」)。
 * 年が書かれていなければ、投稿日(分からなければ今日)の年とし、それより1か月以上前になるときは翌年とする。
 */
fun extractCandidates(text: String, postedOn: LocalDate?, today: LocalDate = LocalDate.now()): List<GameCandidate> {
    val base = postedOn ?: today
    val lines = text.split("\n").map { it.trim() }
    val out = mutableListOf<GameCandidate>()
    lines.forEachIndexed { i, line ->
        DATE_RE.findAll(line).forEach { dm ->
            val month = dm.groupValues[2].toIntOrNull() ?: return@forEach
            val day = dm.groupValues[3].toIntOrNull() ?: return@forEach
            if (month !in 1..12 || day !in 1..31) return@forEach
            val year = dm.groupValues[1].toIntOrNull()
            var date = runCatching { LocalDate.of(year ?: base.year, month, day) }.getOrNull() ?: return@forEach
            if (year == null && date.isBefore(base.minusDays(30))) {
                date = runCatching { date.plusYears(1) }.getOrNull() ?: return@forEach
            }
            // 時刻と相手は、日付より後ろの部分(同じ行の次の日付の前まで)と、次の行(日付が無い行のときだけ)から探す
            val after = line.substring(dm.range.last + 1)
            val sameLine = DATE_RE.find(after)?.let { after.substring(0, it.range.first) } ?: after
            val next = lines.getOrElse(i + 1) { "" }.takeIf { !DATE_RE.containsMatchIn(it) }.orEmpty()
            val rest = sameLine + "\n" + next
            val tm = TIME_RE.find(rest)
            val time = tm?.let {
                val h = (it.groupValues[1].ifBlank { it.groupValues[3] }).toIntOrNull()
                val mi = (it.groupValues[2].ifBlank { it.groupValues[4] }).toIntOrNull() ?: 0
                if (h != null && h in 0..23 && mi in 0..59) "%02d:%02d".format(h, mi) else null
            }.orEmpty()
            val opponent = OPPONENT_RE.find(rest)?.groupValues?.get(1)?.trim().orEmpty()
            out += GameCandidate(date, time, opponent, line)
        }
    }
    return out.distinctBy { Triple(it.date, it.time, it.opponent) }.take(10)
}
