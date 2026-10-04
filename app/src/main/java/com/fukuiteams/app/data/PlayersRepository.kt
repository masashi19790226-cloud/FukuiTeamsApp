package com.fukuiteams.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Actions(build_previews.py)が作っている data/players.json を読む。
 * 中身は Bリーグ公式のクラブページ「選手情報」(club_detail/?TeamID=2891&tab=1)の選手一覧と今季成績。
 * キーはチーム名(BLOWINDS)と、ブローウィンズの次の対戦相手(BLOWINDS_OPP)。項目が無い数字は null(画面では「データなし」)。
 */
private const val PLAYERS_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/players.json"

data class PlayerStats(
    val number: String,
    val name: String,
    val position: String,
    val games: String?,
    val minutesPerGame: String?,
    val points: String?,
    val rebounds: String?,
    val assists: String?,
    val steals: String?,
    val blocks: String?,
    val fieldGoalPct: String?,
    val threePct: String?,
    val freeThrowPct: String?,
    val efficiency: String?,
    /** 顔写真のURL(Bリーグ公式の画像)。無ければ空 */
    val photo: String = ""
)

data class TeamPlayers(
    val season: String,
    val sourceUrl: String,
    val updatedAt: java.time.Instant?,
    val players: List<PlayerStats>,
    /** 対戦相手のデータのときだけ入る:相手のチーム名(例「金沢」)と、どの試合の相手か(試合ID) */
    val teamName: String = "",
    val gameId: String = ""
)

/** ブローウィンズの次の対戦相手の選手データのキー(players.json) */
const val BLOWINDS_OPP_KEY = "BLOWINDS_OPP"

object PlayersRepository {

    /** チーム名(Team.name)→ 選手一覧。取得できなければ空。 */
    suspend fun fetch(): Map<String, TeamPlayers> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(PLAYERS_JSON_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val root = JSONObject(text)
            val map = mutableMapOf<String, TeamPlayers>()
            root.keys().forEach { team ->
                val o = root.optJSONObject(team) ?: return@forEach
                val array = o.optJSONArray("players")
                val players = (0 until (array?.length() ?: 0)).mapNotNull { i ->
                    val p = array?.optJSONObject(i) ?: return@mapNotNull null
                    fun v(key: String): String? = p.optString(key).takeIf { it.isNotBlank() && it != "null" }
                    PlayerStats(
                        number = p.optString("number"),
                        name = p.optString("name"),
                        position = p.optString("position"),
                        games = v("games"),
                        minutesPerGame = v("min_pg"),
                        points = v("ppg"),
                        rebounds = v("rpg"),
                        assists = v("apg"),
                        steals = v("spg"),
                        blocks = v("bpg"),
                        fieldGoalPct = v("fg_pct"),
                        threePct = v("three_pct"),
                        freeThrowPct = v("ft_pct"),
                        efficiency = v("eff"),
                        photo = p.optString("photo")
                    )
                }
                map[team] = TeamPlayers(
                    season = o.optString("season"),
                    sourceUrl = o.optString("source_url"),
                    updatedAt = o.optString("updated_at").takeIf { it.isNotBlank() }?.let {
                        runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull()
                    },
                    players = players,
                    teamName = o.optString("team_name"),
                    gameId = o.optString("game_id")
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
