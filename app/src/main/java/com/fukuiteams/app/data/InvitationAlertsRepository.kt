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
    return try {
        val instant = java.time.Instant.parse(detectedAt)
        val ageMillis = System.currentTimeMillis() - instant.toEpochMilli()
        ageMillis > LIKELY_CLOSED_AFTER_DAYS * 24 * 60 * 60 * 1000
    } catch (e: Exception) {
        false
    }
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
