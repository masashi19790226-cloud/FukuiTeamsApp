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
    val detectedAt: String
)

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
                        detectedAt = obj.optString("detected_at")
                    )
                )
            }
            AlertsResult.Success(items.reversed()) // 新しいものが先頭に来るように
        } catch (e: Exception) {
            AlertsResult.Failure(e.message ?: "取得に失敗しました")
        }
    }
}
