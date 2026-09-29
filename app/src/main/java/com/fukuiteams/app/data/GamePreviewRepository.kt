package com.fukuiteams.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Actions(build_previews.py)が作っている data/previews.json を読む。
 * 各チームの次の試合の「データで見る展望」と相手の注目選手。
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
    val oppLink: String?
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
                val players = o.optJSONArray("key_players")
                map[id] = GamePreview(
                    gameId = id,
                    my = side(o.optJSONObject("my")),
                    opp = side(o.optJSONObject("opp")),
                    lastMeeting = o.optString("last_meeting").takeIf { it.isNotBlank() && it != "null" },
                    summary = o.optString("summary"),
                    keyPlayers = (0 until (players?.length() ?: 0)).map { i ->
                        val p = players!!.getJSONObject(i)
                        KeyPlayer(p.optString("number"), p.optString("name"), p.optString("position"), p.optString("stat"))
                    },
                    playersNote = o.optString("players_note"),
                    oppLink = o.optString("opp_link").takeIf { it.isNotBlank() && it != "null" }
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun side(o: JSONObject?) = PreviewSide(
        label = o?.optString("label").orEmpty(),
        record = o?.optString("record").orEmpty(),
        rank = o?.optString("rank").orEmpty(),
        form = o?.optString("form").orEmpty()
    )
}
