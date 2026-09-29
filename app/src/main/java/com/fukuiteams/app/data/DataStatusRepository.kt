package com.fukuiteams.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Actions の自動更新が最後に動いた時刻と、各処理の成否(data/status.json)。
 * 一面の下に「データの最終更新」として表示し、自動更新が止まっていないかを確認できるようにする。
 */
private const val STATUS_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/status.json"

data class DataStatus(
    val updatedAt: java.time.Instant?,
    /** 失敗した処理の名前(日本語) */
    val failedSteps: List<String>
)

private val STEP_LABELS = mapOf(
    "alerts" to "Googleアラート",
    "official" to "公式サイトの巡回",
    "schedule" to "日程",
    "results" to "試合結果",
    "previews" to "展望"
)

object DataStatusRepository {
    suspend fun fetch(): DataStatus? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(STATUS_JSON_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val o = JSONObject(text)
            val updated = o.optString("updated_at").takeIf { it.isNotBlank() }?.let {
                runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull()
            }
            val steps = o.optJSONObject("steps")
            val failed = mutableListOf<String>()
            steps?.keys()?.forEach { key ->
                val v = steps.optString(key)
                if (v.isNotBlank() && v != "success") failed.add(STEP_LABELS[key] ?: key)
            }
            DataStatus(updated, failed)
        } catch (e: Exception) {
            null
        }
    }
}
