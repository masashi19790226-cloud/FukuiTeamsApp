package com.fukuiteams.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 丸岡RUCK(女子Fリーグ)とユナイテッド(北信越リーグ1部)の順位表。
 * GitHub Actions(scripts/build_previews.py と scripts/standings.py)が作る data/standings.json を読む。
 * - 丸岡RUCK : 女子Fリーグ公式の試合結果から計算した順位表(レギュラーシーズン・ファイナルシーズン)
 * - ユナイテッド: ユナイテッド公式サイトの順位表(順位・勝点のみ)
 * どちらも選手の得点(scorers)が付く。
 */
private const val STANDINGS_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/standings.json"

data class StandingRow(
    val rank: Int,
    val team: String,
    val points: Int,
    /** 以下はユナイテッド(公式サイトの順位表)には無いので null */
    val played: Int?,
    val win: Int?,
    val draw: Int?,
    val lose: Int?,
    val goalDiff: Int?,
    val form: String?
)

data class StandingsPart(val label: String, val rows: List<StandingRow>)

data class LeagueStandings(
    val league: String,
    val note: String,
    val sourceUrl: String,
    val updatedAt: java.time.Instant?,
    val regular: StandingsPart?,
    val final: StandingsPart?,
    /** 選手の得点(後から追加した項目。古い standings.json に無ければ null) */
    val scorers: LeagueScorers? = null
)

/**
 * 選手1人分の得点。
 * - 丸岡RUCK  : 女子Fリーグ公式の得点ランキング(リーグ全体。得点・PK・シュート・出場試合)
 * - ユナイテッド: 公式サイトの試合結果から集計(ユナイテッドの選手だけ。得点・先発・ベンチ入り・背番号)
 * そのリーグに無い項目は null。
 */
data class ScorerRow(
    val rank: Int,
    val name: String,
    val team: String,
    val goals: Int,
    val shots: Int?,
    val games: Int?,
    val starts: Int?,
    val bench: Int?,
    val number: String,
    val position: String
)

data class LeagueScorers(
    val label: String,
    val note: String,
    val sourceUrl: String,
    val rows: List<ScorerRow>
)

object StandingsRepository {
    /** チーム(RAC / UNITED)→ 順位表。取得できなければ空。 */
    suspend fun fetch(): Map<String, LeagueStandings> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(STANDINGS_JSON_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val root = JSONObject(text)
            val map = mutableMapOf<String, LeagueStandings>()
            root.keys().forEach { team ->
                val o = root.optJSONObject(team) ?: return@forEach
                map[team] = LeagueStandings(
                    league = o.optString("league"),
                    note = o.optString("note"),
                    sourceUrl = o.optString("source_url"),
                    updatedAt = o.optString("updated_at").takeIf { it.isNotBlank() }?.let {
                        runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull()
                    },
                    regular = o.optJSONObject("regular")?.let { parsePart(it) },
                    final = o.optJSONObject("final")?.let { parsePart(it) },
                    scorers = o.optJSONObject("scorers")?.let { parseScorers(it) }
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun parseScorers(o: JSONObject): LeagueScorers? {
        val arr = o.optJSONArray("rows") ?: return null
        val rows = (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            fun intOrNull(key: String): Int? = if (r.has(key)) r.optInt(key) else null
            ScorerRow(
                rank = r.optInt("rank"),
                name = r.optString("name"),
                team = r.optString("team"),
                goals = r.optInt("goals"),
                shots = intOrNull("shots"),
                games = intOrNull("games"),
                starts = intOrNull("starts"),
                bench = intOrNull("bench"),
                number = r.optString("number"),
                position = r.optString("position")
            )
        }
        if (rows.isEmpty()) return null
        return LeagueScorers(o.optString("label"), o.optString("note"), o.optString("source_url"), rows)
    }

    private fun parsePart(o: JSONObject): StandingsPart {
        val arr = o.optJSONArray("table")
        val rows = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val r = arr?.optJSONObject(i) ?: return@mapNotNull null
            fun intOrNull(key: String): Int? = if (r.has(key)) r.optInt(key) else null
            StandingRow(
                rank = r.optInt("rank"),
                team = r.optString("team"),
                points = r.optInt("points"),
                played = intOrNull("played"),
                win = intOrNull("win"),
                draw = intOrNull("draw"),
                lose = intOrNull("lose"),
                goalDiff = intOrNull("gd"),
                form = r.optString("form").takeIf { it.isNotBlank() }
            )
        }
        return StandingsPart(o.optString("label"), rows)
    }
}
