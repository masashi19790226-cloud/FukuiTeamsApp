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
 * ブローウィンズのホームゲームの「試合情報」ページ(公式サイト /lp/game_...)から、
 * GitHub Actions(scripts/build_game_lp.py)が読み取った開場時刻・当日スケジュール・イベント。
 * data/game_lp.json に試合IDごとに入っている。
 */
private const val GAME_LP_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/game_lp.json"

data class LpTimelineItem(val time: String, val text: String)
data class LpEvent(val title: String, val text: String)

data class GameLp(
    /** 公式の試合情報ページのURL */
    val url: String,
    /** 開場時刻(例 13:40)。分からなければ空 */
    val openTime: String,
    val timeline: List<LpTimelineItem>,
    val events: List<LpEvent>
)

object GameLpRepository {
    /** いちばん最近読み込んだデータ(試合ID → 試合情報)。一面と試合詳細で共通に使う。 */
    var latest by mutableStateOf<Map<String, GameLp>>(emptyMap())
        private set

    suspend fun fetch(): Map<String, GameLp> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(GAME_LP_JSON_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val root = JSONObject(text)
            val map = mutableMapOf<String, GameLp>()
            root.keys().forEach { gameId ->
                val o = root.optJSONObject(gameId) ?: return@forEach
                val url = o.optString("url")
                if (url.isBlank()) return@forEach
                val tl = o.optJSONArray("timeline")
                val ev = o.optJSONArray("events")
                map[gameId] = GameLp(
                    url = url,
                    openTime = o.optString("open"),
                    timeline = (0 until (tl?.length() ?: 0)).mapNotNull { i ->
                        tl?.optJSONObject(i)?.let { LpTimelineItem(it.optString("time"), it.optString("text")) }
                    },
                    events = (0 until (ev?.length() ?: 0)).mapNotNull { i ->
                        ev?.optJSONObject(i)?.let { LpEvent(it.optString("title"), it.optString("text")) }
                    }
                )
            }
            latest = map
            map
        } catch (e: Exception) {
            // まだファイルが無い・通信できないときは、前回の内容のまま
            latest
        }
    }
}
