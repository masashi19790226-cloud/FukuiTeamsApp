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
 * - ブローウィンズ: Bリーグ公式の地区別の順位表(勝敗・勝率・ゲーム差・得失点・ホーム/アウェー・連勝など。bleague)
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
    val scorers: LeagueScorers? = null,
    /** Bリーグの地区別の順位表(ブローウィンズだけ。無ければ null) */
    val bleague: BleagueStandings? = null
)

/** Bリーグ公式の順位表の1行(公式の表の列のまま。読めなかった数字は null、文字は空) */
data class BleagueRow(
    val rank: Int,
    val team: String,
    /** 短い名前(例「福井」「横浜EX」) */
    val short: String,
    val teamId: String,
    val win: Int,
    val lose: Int,
    /** 勝率(例「.500」) */
    val pct: String,
    /** 1位とのゲーム差(1位は空) */
    val gamesBehind: String,
    val pointsFor: Int?,
    val pointsAgainst: Int?,
    val pointDiff: Int?,
    /** ホーム・アウェーの勝敗(例「3-0」) */
    val home: String,
    val away: String,
    /** 過去5試合の勝敗(例「2-3」) */
    val last5: String,
    /** 連勝・連敗(例「W4」「L1」) */
    val streak: String,
    val games: Int?,
    /** 試合消化率(例「13.33%」) */
    val progress: String,
    /** 地区優勝(決まったときだけ) */
    val clinch: String,
    /** 地区(リーグ全体の表のときだけ) */
    val district: String
) {
    /** 「4連勝」「1連敗」。読めなければ空 */
    val streakLabel: String
        get() {
            val m = Regex("""([WL])\s*(\d+)""").find(streak) ?: return ""
            return m.groupValues[2] + if (m.groupValues[1] == "W") "連勝" else "連敗"
        }

    /** 名前の短い方(無ければ正式名) */
    val shortName: String get() = short.ifBlank { team }
}

data class BleagueDistrict(val label: String, val rows: List<BleagueRow>)

data class BleagueStandings(
    /** 自チームのいる地区(例「中地区」) */
    val ownDistrict: String,
    val teamId: String,
    val districts: List<BleagueDistrict>,
    /** リーグ全体を勝率で並べた参考の表(公式の順位ではない) */
    val overall: BleagueDistrict?,
    val overallNote: String
) {
    val own: BleagueDistrict? get() = districts.firstOrNull { it.label == ownDistrict }
    val me: BleagueRow? get() = own?.rows?.firstOrNull { it.teamId == teamId }
}

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
    val position: String,
    /** 顔写真のURL(自チームの選手だけ。公式サイトの選手紹介から)。無ければ空 */
    val photo: String = ""
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
                    scorers = o.optJSONObject("scorers")?.let { parseScorers(it) },
                    bleague = if (o.has("districts")) parseBleague(o) else null
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
                position = r.optString("position"),
                photo = r.optString("photo")
            )
        }
        if (rows.isEmpty()) return null
        return LeagueScorers(o.optString("label"), o.optString("note"), o.optString("source_url"), rows)
    }

    private fun parseBleague(o: JSONObject): BleagueStandings? {
        fun rowsOf(arr: org.json.JSONArray?): List<BleagueRow> = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val r = arr?.optJSONObject(i) ?: return@mapNotNull null
            fun intOrNull(key: String): Int? = if (r.has(key) && !r.isNull(key)) r.optInt(key) else null
            BleagueRow(
                rank = r.optInt("rank"),
                team = r.optString("team"),
                short = r.optString("short"),
                teamId = r.optString("team_id"),
                win = r.optInt("win"),
                lose = r.optInt("lose"),
                pct = r.optString("pct"),
                gamesBehind = r.optString("gb"),
                pointsFor = intOrNull("pf"),
                pointsAgainst = intOrNull("pa"),
                pointDiff = intOrNull("diff"),
                home = r.optString("home"),
                away = r.optString("away"),
                last5 = r.optString("last5"),
                streak = r.optString("streak"),
                games = intOrNull("games"),
                progress = r.optString("progress"),
                clinch = r.optString("clinch"),
                district = r.optString("district")
            )
        }
        val arr = o.optJSONArray("districts") ?: return null
        val districts = (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i) ?: return@mapNotNull null
            BleagueDistrict(d.optString("label"), rowsOf(d.optJSONArray("table"))).takeIf { it.rows.isNotEmpty() }
        }
        if (districts.isEmpty()) return null
        val ov = o.optJSONObject("overall")
        return BleagueStandings(
            ownDistrict = o.optString("own_district"),
            teamId = o.optString("team_id"),
            districts = districts,
            overall = ov?.let { BleagueDistrict(it.optString("label"), rowsOf(it.optJSONArray("table"))) }?.takeIf { it.rows.isNotEmpty() },
            overallNote = ov?.optString("note").orEmpty()
        )
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
