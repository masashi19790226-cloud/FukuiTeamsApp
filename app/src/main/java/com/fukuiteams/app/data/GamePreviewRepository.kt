package com.fukuiteams.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Actions(build_previews.py)が作っている data/previews.json を読む。
 * 各チームの次の試合の「データで見る展望」と相手の注目選手。
 * ブローウィンズは福井側の主力選手(Bリーグ公式のクラブリーダー)も入る(「選手の数字」画面で使う)。
 */
private const val PREVIEWS_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/previews.json"

data class PreviewSide(val label: String, val record: String, val rank: String, val form: String)

data class KeyPlayer(val number: String, val name: String, val position: String, val stat: String)

data class GamePreview(
    val gameId: String,
    val my: PreviewSide,
    val opp: PreviewSide,
    val lastMeeting: String?,
    val summary: String,
    val keyPlayers: List<KeyPlayer>,
    val playersNote: String,
    val oppLink: String?,
    // 以下は後から追加した項目。古い previews.json に無くても空として読む
    val team: String = "",
    val myKeyPlayers: List<KeyPlayer> = emptyList(),
    val myPlayersNote: String = ""
)

object GamePreviewRepository {

    suspend fun fetch(): Map<String, GamePreview> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(PREVIEWS_JSON_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val root = JSONObject(text)
            val map = mutableMapOf<String, GamePreview>()
            root.keys().forEach { id ->
                val o = root.getJSONObject(id)
                map[id] = GamePreview(
                    gameId = id,
                    my = side(o.optJSONObject("my")),
                    opp = side(o.optJSONObject("opp")),
                    lastMeeting = o.optString("last_meeting").takeIf { it.isNotBlank() && it != "null" },
                    summary = o.optString("summary"),
                    keyPlayers = players(o.optJSONArray("key_players")),
                    playersNote = o.optString("players_note"),
                    oppLink = o.optString("opp_link").takeIf { it.isNotBlank() && it != "null" },
                    team = o.optString("team"),
                    myKeyPlayers = players(o.optJSONArray("my_key_players")),
                    myPlayersNote = o.optString("my_players_note")
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun players(array: JSONArray?): List<KeyPlayer> =
        (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val p = array?.optJSONObject(i) ?: return@mapNotNull null
            KeyPlayer(p.optString("number"), p.optString("name"), p.optString("position"), p.optString("stat"))
        }

    private fun side(o: JSONObject?) = PreviewSide(
        label = o?.optString("label").orEmpty(),
        record = o?.optString("record").orEmpty(),
        rank = o?.optString("rank").orEmpty(),
        form = o?.optString("form").orEmpty()
    )
}
