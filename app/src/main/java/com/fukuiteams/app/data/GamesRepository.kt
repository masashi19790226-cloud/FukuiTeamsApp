package com.fukuiteams.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val GAMES_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/games.json"
private const val CACHE_FILE_NAME = "games_cache.json"

/**
 * 試合日程は GitHub 上の data/games.json から読み込む。
 * これにより、試合が追加・変更されてもアプリを作り直さずに反映できる。
 *
 * 読み込みの優先順位:
 *   1. GitHub から取得できた最新の games.json
 *   2. 前回取得できたときに端末に保存しておいたもの(オフライン時など)
 *   3. アプリに組み込んである MockData(初回起動で通信できないときの最後の手段)
 */
object GamesRepository {

    /** 画面はこれを参照する。更新されると自動で画面も描き直される。 */
    var games: List<Game> by mutableStateOf(MockData.upcomingGames)
        private set

    private var cacheLoaded = false

    suspend fun refresh(context: Context): List<Game> {
        val appContext = context.applicationContext

        if (!cacheLoaded) {
            cacheLoaded = true
            val cached = withContext(Dispatchers.IO) { readCache(appContext) }
            if (!cached.isNullOrEmpty()) games = cached
        }

        val downloaded = withContext(Dispatchers.IO) { download(appContext) }
        if (!downloaded.isNullOrEmpty()) games = downloaded
        return games
    }

    /**
     * ウィジェット用。画面が開いていなくても、端末に保存した日程(なければ通信して取得)を返す。
     */
    suspend fun loadForWidget(context: Context): List<Game> {
        val appContext = context.applicationContext
        val downloaded = withContext(Dispatchers.IO) { download(appContext) }
        if (!downloaded.isNullOrEmpty()) return downloaded
        val cached = withContext(Dispatchers.IO) { readCache(appContext) }
        return if (!cached.isNullOrEmpty()) cached else games
    }

    private fun download(context: Context): List<Game>? = try {
        val connection = URL(GAMES_JSON_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val parsed = parse(text)
        if (parsed.isNotEmpty()) {
            File(context.filesDir, CACHE_FILE_NAME).writeText(text)
        }
        parsed
    } catch (e: Exception) {
        null
    }

    private fun readCache(context: Context): List<Game>? = try {
        val file = File(context.filesDir, CACHE_FILE_NAME)
        if (file.exists()) parse(file.readText()) else null
    } catch (e: Exception) {
        null
    }

    private fun parse(text: String): List<Game> {
        val array = JSONArray(text)
        val list = mutableListOf<Game>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val team = runCatching { Team.valueOf(o.getString("team").uppercase()) }.getOrNull() ?: continue

            // "2026-09-26" → "2026/9/26"(アプリ内の表示・計算はこの形式)
            val dateParts = o.optString("date").split("-")
            if (dateParts.size != 3) continue
            val year = dateParts[0].toIntOrNull() ?: continue
            val month = dateParts[1].toIntOrNull() ?: continue
            val day = dateParts[2].toIntOrNull() ?: continue
            val dateLabel = "$year/$month/$day"

            val time = o.optString("time")
            val fallbackSortKey = "%04d%02d%02d-%s".format(
                year, month, day,
                if (Regex("""\d{1,2}:\d{2}""").matches(time)) time.padStart(5, '0').replace(":", "") else "0000"
            )

            list += Game(
                id = o.getString("id"),
                team = team,
                opponent = o.optString("opponent"),
                dateLabel = dateLabel,
                dayOfWeek = o.optString("day_of_week"),
                timeLabel = time,
                venue = o.optString("venue"),
                ticketStatus = o.optString("ticket_status", "情報なし"),
                ticketSaleStart = o.optString("ticket_sale_start", "-"),
                isHome = o.optBoolean("is_home", true),
                sortKey = o.optString("sort_key").ifBlank { fallbackSortKey },
                resultPageUrl = o.optString("result_page_url").ifBlank { null }
            )
        }
        return list
    }
}
