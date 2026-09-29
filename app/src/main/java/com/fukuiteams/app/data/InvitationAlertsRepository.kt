package com.fukuiteams.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Actions (check_alerts.py) が定期的に更新している data/invitations_raw.json を
 * そのままGitHub上から読みに行くだけの、認証不要のシンプルな取得処理。
 */
private const val ALERTS_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/invitations_raw.json"

data class RemoteInvitationAlert(
    val id: String,
    val teamId: String,
    val title: String,
    val link: String,
    val published: String,
    val detectedAt: String,
    // 公式サイト巡回で本文から見つけた場合の該当箇所(タイトルだけでは招待と分からない記事用)
    val snippet: String = "",
    // 見つけた場所(「ブローウィンズ公式」「公式ストア」など)。Googleアラート由来は空
    val source: String = ""
)

// 検知からこの日数を過ぎたら「終了した可能性が高い」とみなす(応募締切や当選結果までは分からないため簡易判定)。
private const val LIKELY_CLOSED_AFTER_DAYS = 14L

fun RemoteInvitationAlert.isLikelyClosed(): Boolean {
    // 1) 見出し・本文抜粋に書かれた日付(試合日・締切日)がすべて過ぎていれば終了
    val dates = mentionedDates()
    if (dates.isNotEmpty() && dates.max() < java.time.LocalDate.now(TOKYO)) return true
    // 2) 日付が書かれていないものは、見つけてから一定日数たったら終了扱い
    val detected = parseInstant(detectedAt) ?: parseInstant(published) ?: return false
    val ageMillis = System.currentTimeMillis() - detected.toEpochMilli()
    return ageMillis > LIKELY_CLOSED_AFTER_DAYS * 24 * 60 * 60 * 1000
}

/** 過去の招待にも出さない古い情報か(掲載から半年以上、または見出しが去年以前の年だけ)。 */
fun RemoteInvitationAlert.isTooOld(): Boolean {
    val instant = eventInstant()
    if (instant != null && System.currentTimeMillis() - instant.toEpochMilli() > TOO_OLD_DAYS * 24 * 60 * 60 * 1000) return true
    val thisYear = java.time.LocalDate.now(TOKYO).year
    val years = Regex("""(?<!\d)(20\d\d)(?!\d)""").findAll(title).map { it.groupValues[1].toInt() }.toList()
    return years.isNotEmpty() && years.max() < thisYear
}

private val TOKYO: java.time.ZoneId = java.time.ZoneId.of("Asia/Tokyo")
private const val TOO_OLD_DAYS = 180L

/** 「+00:00」付き・「Z」付きのどちらの書き方の日時も読む。 */
private fun parseInstant(raw: String): java.time.Instant? {
    if (raw.isBlank()) return null
    return try {
        java.time.OffsetDateTime.parse(raw).toInstant()
    } catch (e: Exception) {
        try { java.time.Instant.parse(raw) } catch (e2: Exception) { null }
    }
}

/**
 * 見出しと本文抜粋に出てくる月日(「10/3」「10月3日」「10/3(土)・4(日)」)を日付にする。
 * 年は掲載日から推定する(掲載が12月で「1/10」なら翌年)。
 */
fun RemoteInvitationAlert.mentionedDates(): List<java.time.LocalDate> {
    val base = (eventInstant() ?: java.time.Instant.now()).atZone(TOKYO).toLocalDate()
    // 「2026/10/3」「2026年10月3日」の年の部分は外してから読む
    val text = "$title $snippet".replace(Regex("""20\d\d\s*[/年.]\s*(?=\d{1,2}\s*[/月])"""), "")
    val result = mutableListOf<java.time.LocalDate>()
    val pattern = Regex("""(?<![\d/.])(\d{1,2})\s*[/月]\s*(\d{1,2})(?![\d/])(?:日)?(?:[^\d]{0,6}[・、,&～~-]\s*(\d{1,2})(?!\d))?""")
    for (m in pattern.findAll(text)) {
        val month = m.groupValues[1].toInt()
        val days = listOfNotNull(m.groupValues[2].toIntOrNull(), m.groupValues[3].toIntOrNull())
        if (month !in 1..12) continue
        for (day in days) {
            if (day !in 1..31) continue
            var year = base.year
            if (month < base.monthValue - 6) year += 1
            if (month > base.monthValue + 6) year -= 1
            try { result.add(java.time.LocalDate.of(year, month, day)) } catch (e: Exception) { }
        }
    }
    return result
}

/**
 * 記事・招待情報の日時。Googleアラートの掲載日時(published)を優先し、
 * 無ければアプリ側で検知した日時(detected_at)を使う。
 */
fun RemoteInvitationAlert.eventInstant(): java.time.Instant? {
    for (raw in listOf(published, detectedAt)) {
        if (raw.isBlank()) continue
        try {
            return java.time.OffsetDateTime.parse(raw).toInstant()
        } catch (e: Exception) {
        }
        try {
            return java.time.Instant.parse(raw)
        } catch (e: Exception) {
        }
    }
    return null
}

/** 「9/26(土) 17:45・3時間前」のような日本時間の表示。日時が分からなければ空文字。 */
fun RemoteInvitationAlert.timeLabel(): String {
    val instant = eventInstant() ?: return ""
    val zoned = instant.atZone(java.time.ZoneId.of("Asia/Tokyo"))
    val weekdays = listOf("月", "火", "水", "木", "金", "土", "日")
    val base = "%d/%d(%s) %02d:%02d".format(
        zoned.monthValue, zoned.dayOfMonth, weekdays[zoned.dayOfWeek.value - 1], zoned.hour, zoned.minute
    )
    val minutes = (System.currentTimeMillis() - instant.toEpochMilli()) / 60_000
    val relative = when {
        minutes < 0 -> ""
        minutes < 60 -> "${minutes.coerceAtLeast(1)}分前"
        minutes < 24 * 60 -> "${minutes / 60}時間前"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}日前"
        else -> ""
    }
    return if (relative.isEmpty()) base else "$base・$relative"
}

/** 新しい順(日時が分からないものは最後)に並べる。 */
fun List<RemoteInvitationAlert>.sortedNewestFirst(): List<RemoteInvitationAlert> =
    sortedWith(compareByDescending<RemoteInvitationAlert> { it.eventInstant() != null }
        .thenByDescending { it.eventInstant() })

sealed class AlertsResult {
    data class Success(val items: List<RemoteInvitationAlert>) : AlertsResult()
    data class Failure(val message: String) : AlertsResult()
}

object InvitationAlertsRepository {

    suspend fun fetch(): AlertsResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(ALERTS_JSON_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"

            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val array = JSONArray(text)
            val items = mutableListOf<RemoteInvitationAlert>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(
                    RemoteInvitationAlert(
                        id = obj.optString("id"),
                        teamId = obj.optString("team"),
                        title = obj.optString("title"),
                        link = obj.optString("link"),
                        published = obj.optString("published"),
                        detectedAt = obj.optString("detected_at"),
                        snippet = obj.optString("snippet"),
                        source = obj.optString("source")
                    )
                )
            }
            AlertsResult.Success(items.sortedNewestFirst()) // 新しいものが先頭に来るように
        } catch (e: Exception) {
            AlertsResult.Failure(e.message ?: "取得に失敗しました")
        }
    }
}

/**
 * この試合向けの招待情報があるか。
 * 招待の見出し・本文抜粋に試合日(「10/3」「10月3日」など)が書かれていれば該当とみなす。
 * 日付が1つも書かれていない招待は、対戦相手名が書かれている場合だけ該当とみなす。
 */
fun com.fukuiteams.app.model.Game.hasMatchingInvite(invites: List<RemoteInvitationAlert>): Boolean {
    val parts = dateLabel.split("/").mapNotNull { it.trim().toIntOrNull() }
    if (parts.size < 3) return false
    val month = parts[1]
    val day = parts[2]
    val datePattern = Regex("""(?<!\d)$month\s*[/月]\s*$day(?!\d)""")
    val anyDate = Regex("""(?<!\d)\d{1,2}\s*[/月]\s*\d{1,2}(?!\d)""")
    return invites.any { invite ->
        if (invite.teamId != team.name) return@any false
        val text = "${invite.title} ${invite.snippet}"
        when {
            datePattern.containsMatchIn(text) -> true
            // 「10/3(土)・4(日)」のような2日連戦の書き方
            Regex("""(?<!\d)$month\s*[/月]\s*\d{1,2}[^\d]{0,6}[・、,&～~-]\s*$day(?!\d)""").containsMatchIn(text) -> true
            anyDate.containsMatchIn(text) -> false
            else -> opponent.isNotBlank() && text.contains(opponent)
        }
    }
}
