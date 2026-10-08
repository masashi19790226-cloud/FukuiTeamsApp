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
    val photo: String = "",
    /** 今季のシュートの成功数・試投数(合計)。Bリーグ公式の選手ページから。無ければ null */
    val fieldGoalsMade: String? = null,
    val fieldGoalsAttempted: String? = null,
    val threesMade: String? = null,
    val threesAttempted: String? = null,
    val freeThrowsMade: String? = null,
    val freeThrowsAttempted: String? = null,
    /** 今季の試合ごとの成績(新しい順)。Bリーグ公式の選手ページから。無ければ空 */
    val gameLog: List<PlayerGameLog> = emptyList()
)

/** 選手の1試合分の成績。数字が無い項目は null。 */
data class PlayerGameLog(
    /** 試合日(例 2026-10-04) */
    val date: String,
    /** 対戦相手(例「金沢」) */
    val opponent: String,
    /** true:ホーム / false:アウェイ / null:不明 */
    val home: Boolean?,
    /** true:勝ち / false:負け / null:不明 */
    val win: Boolean?,
    /** 先発出場か */
    val starter: Boolean,
    val minutes: String?,
    val points: String?,
    val fieldGoalsMade: String?,
    val fieldGoalsAttempted: String?,
    val threesMade: String?,
    val threesAttempted: String?,
    val freeThrowsMade: String?,
    val freeThrowsAttempted: String?,
    val rebounds: String?,
    val assists: String?,
    val steals: String?,
    val blocks: String?,
    val turnovers: String?,
    val efficiency: String?
)

data class TeamPlayers(
    val season: String,
    val sourceUrl: String,
    val updatedAt: java.time.Instant?,
    val players: List<PlayerStats>,
    /** 対戦相手のデータのときだけ入る:相手のチーム名(例「金沢」)と、どの試合の相手か(試合ID) */
    val teamName: String = "",
    val gameId: String = "",
    /** チーム全体の今季の数字(Bリーグ公式のクラブページの「クラブ成績」。リーグ内の順位つき) */
    val teamStats: List<TeamStat> = emptyList(),
    /** クラブ成績の公式の更新日時(例「2026年10月04日19:07」) */
    val teamStatsUpdated: String = ""
)

/** チーム全体の数字1項目(例 key=PPG・label=平均得点数・value=81.5・unit=点・rank=14) */
data class TeamStat(val key: String, val label: String, val value: String, val unit: String, val rank: Int?)

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
                        photo = p.optString("photo"),
                        fieldGoalsMade = v("fgm"),
                        fieldGoalsAttempted = v("fga"),
                        threesMade = v("three_m"),
                        threesAttempted = v("three_a"),
                        freeThrowsMade = v("ftm"),
                        freeThrowsAttempted = v("fta"),
                        gameLog = parseGameLog(p.optJSONArray("game_log"))
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
                    gameId = o.optString("game_id"),
                    teamStats = o.optJSONArray("team_stats")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            val t = arr.optJSONObject(i) ?: return@mapNotNull null
                            val value = t.optString("value").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            TeamStat(
                                key = t.optString("key"),
                                label = t.optString("label"),
                                value = value,
                                unit = t.optString("unit"),
                                rank = if (t.isNull("rank")) null else t.optInt("rank").takeIf { it > 0 }
                            )
                        }
                    } ?: emptyList(),
                    teamStatsUpdated = o.optString("team_stats_updated")
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

/** players.json の game_log(試合ごとの成績)を読む。読めない行は飛ばす。 */
private fun parseGameLog(array: org.json.JSONArray?): List<PlayerGameLog> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        val g = array.optJSONObject(i) ?: return@mapNotNull null
        fun v(key: String): String? = g.optString(key).takeIf { it.isNotBlank() && it != "null" }
        val date = v("date") ?: return@mapNotNull null
        PlayerGameLog(
            date = date,
            opponent = g.optString("opp"),
            home = when (g.optString("ha")) { "H" -> true; "A" -> false; else -> null },
            win = when (g.optString("wl")) { "W" -> true; "L" -> false; else -> null },
            starter = g.optBoolean("start", false),
            minutes = v("min"),
            points = v("pts"),
            fieldGoalsMade = v("fgm"),
            fieldGoalsAttempted = v("fga"),
            threesMade = v("tpm"),
            threesAttempted = v("tpa"),
            freeThrowsMade = v("ftm"),
            freeThrowsAttempted = v("fta"),
            rebounds = v("reb"),
            assists = v("ast"),
            steals = v("stl"),
            blocks = v("blk"),
            turnovers = v("tov"),
            efficiency = v("eff")
        )
    }.sortedByDescending { it.date }
}
