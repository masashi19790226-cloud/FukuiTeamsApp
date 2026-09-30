package com.fukuiteams.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    "previews" to "展望",
    "gamelp" to "試合情報ページ"
)

object DataStatusRepository {
    /**
     * いちばん最近読み込んだ更新状況。画面の一番上の帯(「最終更新」)が使う。
     * どの画面で読み込んでも、ここが新しくなり、帯の表示も切り替わる。
     */
    var latest by mutableStateOf<DataStatus?>(null)
        private set

    /** 最後に読み込んだ時刻(読み込みすぎないように使う) */
    private var lastFetchedAtMillis = 0L

    /** 前回の読み込みから minIntervalMillis 以上たっていれば読み込み直す(帯から呼ぶ用)。 */
    suspend fun refreshIfStale(minIntervalMillis: Long = 5 * 60_000L) {
        if (latest == null || System.currentTimeMillis() - lastFetchedAtMillis >= minIntervalMillis) fetch()
    }

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
            DataStatus(updated, failed).also {
                latest = it
                lastFetchedAtMillis = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            null
        }
    }
}
