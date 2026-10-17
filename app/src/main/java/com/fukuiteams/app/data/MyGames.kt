package com.fukuiteams.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * 自分で追加した予定(Xの投稿などから見つけた、U-20の大会・練習試合など)。
 * この端末の中(アプリ専用のフォルダの my_games.json)だけに保存し、外には送らない。
 * GamesRepository.games に公式の日程と一緒に並ぶ。
 */
private const val MY_GAMES_FILE = "my_games.json"

data class MyGameEntry(
    val id: String,
    val team: Team,
    val date: LocalDate,
    /** 「09:30」の形。分からなければ「時間未定」 */
    val time: String,
    val opponent: String,
    val venue: String,
    val isHome: Boolean,
    /** 元の投稿のURL(無ければ空) */
    val sourceUrl: String
) {
    fun toGame(): Game {
        val week = listOf("月", "火", "水", "木", "金", "土", "日")[date.dayOfWeek.value - 1]
        val hhmm = if (Regex("""\d{1,2}:\d{2}""").matches(time)) time.padStart(5, '0').replace(":", "") else "0000"
        return Game(
            id = id,
            team = team,
            opponent = opponent.ifBlank { "相手未定" },
            dateLabel = "${date.year}/${date.monthValue}/${date.dayOfMonth}",
            dayOfWeek = week,
            timeLabel = time.ifBlank { "時間未定" },
            venue = venue.ifBlank { "会場未定" },
            ticketStatus = "情報なし",
            ticketSaleStart = "-",
            isHome = isHome,
            sortKey = "%04d%02d%02d-%s".format(date.year, date.monthValue, date.dayOfMonth, hhmm),
            userAdded = true,
            sourceUrl = sourceUrl.ifBlank { null }
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("team", team.name)
        .put("date", date.toString())
        .put("time", time)
        .put("opponent", opponent)
        .put("venue", venue)
        .put("is_home", isHome)
        .put("source_url", sourceUrl)
}

object MyGamesRepository {
    private var entries: List<MyGameEntry> by mutableStateOf(emptyList())
    private var loaded = false

    /** 画面に出す形(Game)にしたもの */
    val games: List<Game>
        get() = entries.map { it.toGame() }

    /** まだ読んでいなければ、端末に保存した分を読む */
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        entries = try {
            val file = File(context.applicationContext.filesDir, MY_GAMES_FILE)
            if (!file.exists()) emptyList() else {
                val arr = JSONArray(file.readText())
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val team = Team.values().firstOrNull { it.name == o.optString("team") } ?: return@mapNotNull null
                    val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: return@mapNotNull null
                    MyGameEntry(
                        id = o.optString("id").ifBlank { "my${i}" },
                        team = team,
                        date = date,
                        time = o.optString("time"),
                        opponent = o.optString("opponent"),
                        venue = o.optString("venue"),
                        isHome = o.optBoolean("is_home", true),
                        sourceUrl = o.optString("source_url")
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 予定を加える(同じチーム・日付・時刻・相手の予定がすでにあれば加えない)。加えたら true */
    @Synchronized
    fun add(context: Context, entry: MyGameEntry): Boolean {
        ensureLoaded(context)
        if (entries.any { it.team == entry.team && it.date == entry.date && it.time == entry.time && it.opponent == entry.opponent }) {
            return false
        }
        entries = (entries + entry).sortedBy { it.toGame().sortKey }
        save(context)
        return true
    }

    /** IDから予定を探す(無ければ null) */
    fun find(context: Context, id: String): MyGameEntry? {
        ensureLoaded(context)
        return entries.firstOrNull { it.id == id }
    }

    /** 予定を書き換える(足りなかった時刻・相手・会場などをあとから補う)。同じIDの予定を置き換える */
    @Synchronized
    fun update(context: Context, entry: MyGameEntry) {
        ensureLoaded(context)
        entries = entries.map { if (it.id == entry.id) entry else it }.sortedBy { it.toGame().sortKey }
        save(context)
    }

    /** 予定を消す */
    @Synchronized
    fun remove(context: Context, id: String) {
        ensureLoaded(context)
        entries = entries.filterNot { it.id == id }
        save(context)
    }

    private fun save(context: Context) {
        runCatching {
            val arr = JSONArray()
            entries.forEach { arr.put(it.toJson()) }
            File(context.applicationContext.filesDir, MY_GAMES_FILE).writeText(arr.toString())
        }
    }

    /** 新しい予定のID(公式の日程の bw01 などと重ならない形) */
    fun newId(): String = "my" + System.currentTimeMillis()
}

/** ほかのアプリの「共有」から送られてきた、まだ開いていない投稿のURLや画像(試合タブで追加の画面を開く) */
object SharedPostRequest {
    var pendingText: String? by mutableStateOf(null)
    /** 共有された画像(スクリーンショットなど)の場所(content://…) */
    var pendingImage: String? by mutableStateOf(null)
}
