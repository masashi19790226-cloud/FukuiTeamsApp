package com.fukuiteams.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Actions (check_alerts.py) が定期的に更新している data/news_raw.json を
 * そのままGitHub上から読みに行くだけの、認証不要のシンプルな取得処理。
 */
private const val NEWS_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/news_raw.json"

/** 45日より前のニュースの保管庫(1年分)。トピックの「それより前」を開いたとき・検索したときだけ読む */
private const val NEWS_ARCHIVE_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/news_archive.json"

object NewsAlertsRepository {

    suspend fun fetch(): AlertsResult = fetchFrom(NEWS_JSON_URL)

    /** 過去のニュース(45日より前〜1年前)。まだ保管庫が無いときは空の一覧 */
    suspend fun fetchArchive(): AlertsResult = fetchFrom(NEWS_ARCHIVE_JSON_URL, missingIsEmpty = true)

    /** missingIsEmpty:ファイルがまだ無い(404)ときは、失敗ではなく0件として扱う */
    private suspend fun fetchFrom(address: String, missingIsEmpty: Boolean = false): AlertsResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(address)
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
                        source = obj.optString("source")
                    )
                )
            }
            AlertsResult.Success(items.sortedNewestFirst())
        } catch (e: java.io.FileNotFoundException) {
            if (missingIsEmpty) AlertsResult.Success(emptyList()) else AlertsResult.Failure(e.message ?: "取得に失敗しました")
        } catch (e: Exception) {
            AlertsResult.Failure(e.message ?: "取得に失敗しました")
        }
    }
}
