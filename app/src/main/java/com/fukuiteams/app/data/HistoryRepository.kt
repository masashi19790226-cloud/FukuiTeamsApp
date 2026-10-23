package com.fukuiteams.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fukuiteams.app.model.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * 過去の試合の記録(下のメニューの「チーム」「選手」・一面の「○年前の今日」・次の相手との通算の対戦成績で使う)。
 * GitHub Actions(scripts/history.py)が作る data/history.json を読む。
 * - ユナイテッド  : 公式サイトの試合情報(2019年〜今季の全公式戦)
 * - ブルーサンダー: リーグH公式の星取表・試合ページ(2024-25〜今季)
 * - 丸岡RUCK      : 女子Fリーグ公式の過去の記録(2017〜)と今季の試合一覧(得点者・観客数は無し)
 * - ブローウィンズは、まだ入っていない
 */
private const val HISTORY_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/history.json"

/** 過去の試合1つ */
data class HistoryMatch(
    val team: Team,
    /** 2019-03-24 */
    val date: String,
    val time: String,
    /** シーズン(ユナイテッドは「2019」、ブルーサンダーは「2024-25」) */
    val season: String,
    /** 大会(例「北信越フットボールリーグ」「天皇杯JFA全日本サッカー選手権大会」「リーグH」) */
    val competition: String,
    /** 試合の名前(例「第52回北信越フットボールリーグ第14節」) */
    val title: String,
    val opponent: String,
    val venue: String,
    /** H = ホーム、A = アウェイ、N = どちらでもない(県選手権・天皇杯など) */
    val homeAway: String,
    val myScore: Int,
    val opponentScore: Int,
    /** PK戦の点数(例「3-4」。PK戦が無ければ空) */
    val pk: String,
    /** 観客数(分からなければ null) */
    val visitors: Int?,
    /** 得点した選手と得点(ブルーサンダーは得点の多い3人まで) */
    val scorers: List<Pair<String, Int>>,
    val url: String
) {
    val localDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()

    /** 勝ち・負け・引き分け(PK戦は引き分けとして数える) */
    val outcome: GameOutcome
        get() = when {
            myScore > opponentScore -> GameOutcome.WIN
            myScore < opponentScore -> GameOutcome.LOSE
            else -> GameOutcome.DRAW
        }

    /** 「2019/3/24(日)」 */
    val dateLabel: String
        get() = localDate?.let { "${it.year}/${it.monthValue}/${it.dayOfMonth}(${HISTORY_WEEKDAYS[it.dayOfWeek.value - 1]})" } ?: date

    /** 「○9-0」「△1-1(PK3-4)」 */
    val scoreLabel: String
        get() {
            val mark = when (outcome) {
                GameOutcome.WIN -> "○"
                GameOutcome.LOSE -> "●"
                GameOutcome.DRAW -> "△"
            }
            return "$mark$myScore-$opponentScore" + (if (pk.isNotBlank()) "(PK$pk)" else "")
        }

    /** エキシビジョン(親善試合)か。成績には数えない */
    val isExhibition: Boolean get() = competition.contains("エキシビジョン")
}

private val HISTORY_WEEKDAYS = listOf("月", "火", "水", "木", "金", "土", "日")

/** チームごとの過去の記録 */
data class TeamHistoryData(
    val source: String,
    val sourceUrl: String,
    /** 古い順 */
    val matches: List<HistoryMatch>,
    /** これまでの選手(出場の多い順。ユナイテッド・ブルーサンダー) */
    val players: List<HistoryPlayer> = emptyList(),
    /** 日本代表・代表候補の選出(新しい順。丸岡RUCK) */
    val national: List<NationalSelection> = emptyList(),
    /** 公式のシーズンごとの成績(新しい順。ブローウィンズ。Bリーグ公式のクラブページから) */
    val officialSeasons: List<OfficialSeason> = emptyList(),
    /** リーグの表彰(新しい順。丸岡RUCK:女子Fリーグ公式の過去の記録から) */
    val awards: List<SeasonAward> = emptyList(),
    /** 選手の記録が試合ごとの出場選手から数えたものか(丸岡RUCK。false ならゴールランキングからで、出場試合数は正しくない) */
    val playersFromLineup: Boolean = false,
    /** 試合ごとの記録(ブローウィンズ:ボックススコア、丸岡RUCK:出場選手)を読み終えた試合数と全体の試合数。無ければ null */
    val detailProgress: Pair<Int, Int>? = null
)

/** 選手の細かい成績(通算と、シーズンごと。古い順)。選手タブの PlayerStats(今季の成績)とは別のもの */
data class HistoryStats(val total: StatLine, val seasons: List<StatLine>)

/**
 * 細かい成績の合計(ブローウィンズ。選手タブと同じ項目)。平均・成功率は表示のときに計算する。
 * sec は出場時間の合計(秒)
 */
data class StatLine(
    val season: String,
    val games: Int, val points: Int, val sec: Int,
    val p2m: Int, val p2a: Int, val p3m: Int, val p3a: Int, val ftm: Int, val fta: Int,
    val reb: Int, val ast: Int, val stl: Int, val blk: Int, val to: Int, val eff: Int
) {
    private fun per(v: Int) = if (games > 0) v.toDouble() / games else 0.0
    val ppg get() = per(points)
    val rpg get() = per(reb)
    val apg get() = per(ast)
    val spg get() = per(stl)
    val bpg get() = per(blk)
    val effPerGame get() = per(eff)
    /** 平均出場時間(分) */
    val minutes get() = if (games > 0) sec / 60.0 / games else 0.0
    val fgm get() = p2m + p3m
    val fga get() = p2a + p3a
}

private fun pct(m: Int, a: Int) = if (a > 0) "%.1f%%(%d/%d)".format(m * 100.0 / a, m, a) else "-"

/** 成功率の表示「45.0%(18/40)」 */
fun StatLine.fgLabel() = pct(fgm, fga)
fun StatLine.p3Label() = pct(p3m, p3a)
fun StatLine.ftLabel() = pct(ftm, fta)

internal fun statLine(o: org.json.JSONObject?, season: String): StatLine = StatLine(
    season = season,
    games = o?.optInt("g") ?: 0, points = o?.optInt("pts") ?: 0, sec = o?.optInt("sec") ?: 0,
    p2m = o?.optInt("p2m") ?: 0, p2a = o?.optInt("p2a") ?: 0, p3m = o?.optInt("p3m") ?: 0, p3a = o?.optInt("p3a") ?: 0,
    ftm = o?.optInt("ftm") ?: 0, fta = o?.optInt("fta") ?: 0,
    reb = o?.optInt("reb") ?: 0, ast = o?.optInt("ast") ?: 0, stl = o?.optInt("stl") ?: 0,
    blk = o?.optInt("blk") ?: 0, to = o?.optInt("to") ?: 0, eff = o?.optInt("eff") ?: 0
)

/** リーグの表彰1件(「2023-24」「ベストファイブ」「池内 天紀」「初受賞」) */
data class SeasonAward(val season: String, val award: String, val name: String, val note: String)

/** 公式のシーズンごとの成績1行(ブローウィンズ。Bリーグ公式のクラブページの表) */
data class OfficialSeason(
    /** 2024-25 */
    val season: String,
    /** 「B2 リーグ戦」「プレーオフ」など */
    val comp: String,
    val win: Int,
    val lose: Int,
    /** 平均得点・平均失点(「93.8」の形) */
    val ppg: String,
    val oppg: String
)

/** 日本代表・代表候補への選出1回分(チームの公式サイトのお知らせから) */
data class NationalSelection(
    /** 2025-03-31 */
    val date: String,
    /** お知らせの見出し */
    val title: String,
    /** 「日本代表」「日本代表候補」「U-18日本代表」 */
    val kind: String,
    /** 選ばれた選手の名前 */
    val players: List<String>,
    val link: String,
    /** お知らせに載っている選手の写真(名前 → URL。分かった選手だけ) */
    val photos: Map<String, String> = emptyMap(),
    /** お知らせの一番上の画像(アイキャッチ。選ばれた選手の写真が並んだ画像のことが多い)。無ければ "" */
    val image: String = ""
)

/** これまでにチームでプレーした選手1人の通算の記録(scripts/history.py が試合ごとの記録から足し合わせる) */
data class HistoryPlayer(
    val name: String,
    /** いちばん新しい試合の背番号・ポジション */
    val number: String,
    val position: String,
    /** プレーしたシーズン(古い順) */
    val seasons: List<String>,
    /** 出場した試合(ユナイテッドは先発・途中出場。ブルーサンダーは試合のスコア表に名前のあった試合) */
    val apps: Int,
    /** 先発した試合(ユナイテッドだけ) */
    val starts: Int,
    val goals: Int,
    /** 最初と最後の試合の日付(2019-03-24) */
    val first: String,
    val last: String,
    /** いまもチームにいるか(今の選手名簿にいれば true、いなければ false、分からなければ null) */
    val now: Boolean?,
    /** 選手の写真(ブローウィンズ:Bリーグ公式の、福井にいたシーズンの写真。無ければ今のクラブの写真)。無ければ "" */
    val photo: String = "",
    /** 元選手のいまのクラブ(ブローウィンズ:Bリーグ公式の選手ページから)。Bリーグのクラブにいなければ "" */
    val nowTeam: String = "",
    /** いまのクラブを調べたか(調べて "" なら、Bリーグのクラブには所属していない) */
    val nowChecked: Boolean = false,
    /** 結婚などで変わる前の名前(丸岡RUCK) */
    val former: List<String> = emptyList(),
    /** シュート数の合計(丸岡RUCK:試合ごとの記録から)。分からなければ null */
    val shots: Int? = null,
    /** 細かい成績(ブローウィンズ:試合ごとのボックススコアの合計)。無ければ null */
    val stats: HistoryStats? = null
) {
    /** 「2019〜2022」(1シーズンだけなら「2019」) */
    val seasonsLabel: String
        get() = when {
            seasons.isEmpty() -> ""
            seasons.size == 1 -> seasons.first()
            else -> "${seasons.first()}〜${seasons.last()}"
        }
}

object HistoryRepository {
    var data by mutableStateOf<Map<Team, TeamHistoryData>>(emptyMap())
        private set

    /** 読み込みを試したか(読めなかったときも true) */
    var loaded by mutableStateOf(false)
        private set

    fun matchesFor(team: Team): List<HistoryMatch> = data[team]?.matches.orEmpty()

    /** history.json を読み直す。読めなかったときは前回の分を残す */
    suspend fun refresh() {
        val parsed = withContext(Dispatchers.IO) {
            try {
                val connection = URL(HISTORY_JSON_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.requestMethod = "GET"
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                parse(JSONObject(text))
            } catch (e: Exception) {
                // 読めなかったときは前回の分のまま(まだ history.json が無いときも)
                emptyMap()
            }
        }
        if (parsed.isNotEmpty()) data = parsed
        loaded = true
    }

    /** まだ読んでいなければ読む */
    suspend fun ensureLoaded() {
        if (!loaded || data.isEmpty()) refresh()
    }

    private fun parse(root: JSONObject): Map<Team, TeamHistoryData> {
        val teams = root.optJSONObject("teams") ?: return emptyMap()
        val out = mutableMapOf<Team, TeamHistoryData>()
        teams.keys().forEach { key ->
            val team = Team.values().firstOrNull { it.name == key } ?: return@forEach
            val o = teams.optJSONObject(key) ?: return@forEach
            val arr = o.optJSONArray("matches") ?: return@forEach
            val matches = (0 until arr.length()).mapNotNull { i ->
                val m = arr.optJSONObject(i) ?: return@mapNotNull null
                val date = m.optString("date")
                val opp = m.optString("opp")
                if (date.isBlank() || opp.isBlank() || !m.has("my") || !m.has("their")) return@mapNotNull null
                val sc = m.optJSONArray("scorers")
                HistoryMatch(
                    team = team,
                    date = date,
                    time = m.optString("time"),
                    season = m.optString("season"),
                    competition = m.optString("comp"),
                    title = m.optString("title"),
                    // 「&#039;05加茂FC」のような文字の書き方を、ふつうの文字に戻す
                    opponent = opp.replace("&#039;", "'").replace("&amp;", "&"),
                    venue = m.optString("venue"),
                    homeAway = m.optString("ha", "N"),
                    myScore = m.optInt("my"),
                    opponentScore = m.optInt("their"),
                    pk = m.optString("pk"),
                    // 観客数 0 は「分からない」として扱う
                    visitors = if (m.isNull("visitors")) null else m.optInt("visitors").takeIf { it > 0 },
                    scorers = sc?.let { a ->
                        (0 until a.length()).mapNotNull { j ->
                            a.optJSONObject(j)?.let { s -> s.optString("name") to s.optInt("goals") }
                        }.filter { it.first.isNotBlank() }
                    }.orEmpty(),
                    url = m.optString("url")
                )
            }.sortedWith(compareBy({ it.date }, { it.time }))
            if (matches.isNotEmpty()) {
                val pArr = o.optJSONArray("players")
                val players = if (pArr == null) emptyList() else (0 until pArr.length()).mapNotNull { i ->
                    val q = pArr.optJSONObject(i) ?: return@mapNotNull null
                    val name = q.optString("name")
                    if (name.isBlank()) return@mapNotNull null
                    val sArr = q.optJSONArray("seasons")
                    HistoryPlayer(
                        name = name,
                        number = q.optString("number"),
                        position = q.optString("position"),
                        seasons = sArr?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
                        apps = q.optInt("apps"),
                        starts = q.optInt("starts"),
                        goals = q.optInt("goals"),
                        first = q.optString("first"),
                        last = q.optString("last"),
                        now = if (!q.has("now") || q.isNull("now")) null else q.optBoolean("now"),
                        photo = q.optString("photo"),
                        nowTeam = q.optString("now_team"),
                        nowChecked = q.optString("now_checked").isNotBlank(),
                        shots = if (q.has("shots")) q.optInt("shots") else null,
                        former = q.optJSONArray("former")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
                        stats = q.optJSONObject("stats")?.let { st ->
                            val sArr2 = st.optJSONArray("seasons")
                            HistoryStats(
                                total = statLine(st.optJSONObject("total"), ""),
                                seasons = if (sArr2 == null) emptyList() else (0 until sArr2.length()).mapNotNull { k ->
                                    sArr2.optJSONObject(k)?.let { statLine(it, it.optString("season")) }
                                }
                            )
                        }
                    )
                }
                val nArr = o.optJSONArray("national")
                val national = if (nArr == null) emptyList() else (0 until nArr.length()).mapNotNull { i ->
                    val q = nArr.optJSONObject(i) ?: return@mapNotNull null
                    val names = q.optJSONArray("players")
                    NationalSelection(
                        date = q.optString("date"),
                        title = q.optString("title"),
                        kind = q.optString("kind"),
                        players = names?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
                        link = q.optString("link"),
                        photos = q.optJSONObject("photos")?.let { ph -> ph.keys().asSequence().associateWith { k -> ph.optString(k) } }
                            ?.filterValues { it.isNotBlank() }.orEmpty(),
                        image = q.optString("image")
                    )
                }
                val sArr = o.optJSONArray("seasons")
                val officialSeasons = if (sArr == null) emptyList() else (0 until sArr.length()).mapNotNull { i ->
                    val q = sArr.optJSONObject(i) ?: return@mapNotNull null
                    val season = q.optString("season")
                    if (season.isBlank()) return@mapNotNull null
                    OfficialSeason(
                        season = season,
                        comp = q.optString("comp").ifBlank { q.optString("code") },
                        win = q.optInt("win"),
                        lose = q.optInt("lose"),
                        ppg = q.optString("ppg"),
                        oppg = q.optString("oppg")
                    )
                }
                val aArr = o.optJSONArray("awards")
                val awards = if (aArr == null) emptyList() else (0 until aArr.length()).mapNotNull { i ->
                    val q = aArr.optJSONObject(i) ?: return@mapNotNull null
                    if (q.optString("name").isBlank()) return@mapNotNull null
                    SeasonAward(q.optString("season"), q.optString("award"), q.optString("name"), q.optString("note"))
                }
                out[team] = TeamHistoryData(
                    o.optString("source"), o.optString("source_url"), matches, players, national, officialSeasons, awards,
                    playersFromLineup = o.optString("players_from") == "lineup",
                    detailProgress = o.optJSONObject("detail_progress")?.let { dp -> dp.optInt("done") to dp.optInt("total") }
                )
            }
        }
        return out
    }
}

// ---------- 集計 ----------

/** 勝ち・負け・引き分けの数と得点・失点 */
data class WinLoss(val win: Int, val lose: Int, val draw: Int, val scored: Int, val conceded: Int) {
    val games: Int get() = win + lose + draw

    /** 「5勝2敗1分」(引き分けが無ければ「5勝2敗」) */
    val label: String get() = "${win}勝${lose}敗" + (if (draw > 0) "${draw}分" else "")

    /** 「勝率 71%」(勝利数÷試合数×100。引き分けも試合数に含める。試合の成績カードと同じ計算) */
    val rateLabel: String get() = winRateLabel(win, games)
}

/** 勝率の表示「勝率 71%」(勝利数÷試合数×100 を四捨五入。試合が無ければ「勝率 -」) */
fun winRateLabel(win: Int, games: Int): String =
    if (games <= 0) "勝率 -" else "勝率 ${Math.round(win * 100.0 / games)}%"

fun List<HistoryMatch>.winLoss(): WinLoss = WinLoss(
    win = count { it.outcome == GameOutcome.WIN },
    lose = count { it.outcome == GameOutcome.LOSE },
    draw = count { it.outcome == GameOutcome.DRAW },
    scored = sumOf { it.myScore },
    conceded = sumOf { it.opponentScore }
)

/** 1シーズン分のまとめ */
data class SeasonSummary(
    val season: String,
    /** そのシーズンの公式戦(エキシビジョンを除く) */
    val all: WinLoss,
    /** リーグ戦だけ(北信越フットボールリーグ・リーグH・女子Fリーグ) */
    val league: WinLoss?,
    val leagueName: String?,
    /** いちばん点を取った選手(同点は先に出た選手)と得点 */
    val topScorer: Pair<String, Int>?,
    /** ホームの試合の観客数の平均(分かる試合だけ) */
    val averageHomeVisitors: Int?
)

/** シーズンごとのまとめ(新しいシーズンから) */
fun seasonSummaries(team: Team, matches: List<HistoryMatch>): List<SeasonSummary> =
    matches.filterNot { it.isExhibition }.groupBy { it.season }.map { (season, list) ->
        val leagueList = list.filter { isLeague(team, it) }
        val goals = linkedMapOf<String, Int>()
        list.forEach { m -> m.scorers.forEach { (n, g) -> goals[n] = (goals[n] ?: 0) + g } }
        val visitors = list.filter { it.homeAway == "H" }.mapNotNull { it.visitors }
        SeasonSummary(
            season = season,
            all = list.winLoss(),
            league = leagueList.takeIf { it.isNotEmpty() }?.winLoss(),
            leagueName = leagueList.firstOrNull()?.competition,
            // ブルーサンダーは試合ごとの上位3人だけなので、シーズンの得点王は出さない
            topScorer = if (team == Team.THUNDER) null else goals.maxByOrNull { it.value }?.toPair(),
            averageHomeVisitors = visitors.takeIf { it.isNotEmpty() }?.average()?.toInt()
        )
    }.sortedByDescending { it.season }

private fun isLeague(team: Team, m: HistoryMatch): Boolean = when (team) {
    Team.UNITED -> m.competition.contains("北信越フットボールリーグ")
    Team.THUNDER -> m.competition == "リーグH"
    Team.RAC -> m.competition == "女子Fリーグ"
    // ブローウィンズは「B2 リーグ戦」「B.ONE リーグ戦」など(プレーオフは除く)
    Team.BLOWINDS -> m.competition.endsWith("リーグ戦")
    else -> false
}

/** 相手チームごとの通算成績 */
data class OpponentRecord(
    val opponent: String,
    val record: WinLoss,
    /** その相手との試合(新しい順) */
    val matches: List<HistoryMatch>,
    /** 以前のチーム名など、同じ相手のほかの表記(新しい順。チーム名が変わったクラブ) */
    val otherNames: List<String> = emptyList()
)

/** 相手チームごとの通算成績(対戦の多い順。エキシビジョンは除く)。表記ゆれの相手はまとめる */
fun opponentRecords(matches: List<HistoryMatch>): List<OpponentRecord> {
    val groups = mutableListOf<MutableList<HistoryMatch>>()
    matches.filterNot { it.isExhibition }.sortedByDescending { it.date }.forEach { m ->
        val g = groups.firstOrNull { sameOpponentLoose(it.first().opponent, m.opponent) }
        if (g != null) g += m else groups += mutableListOf(m)
    }
    return groups.map { g ->
        val name = g.first().opponent
        // 書き方が少し違うだけ(「FC.マツセロナ」と「FCマツセロナ」など)のものは出さない
        val others = g.map { it.opponent }.distinct().filter { rawNameKey(it) != rawNameKey(name) }
        OpponentRecord(name, g.winLoss(), g, others)
    }
        .sortedWith(compareByDescending<OpponentRecord> { it.record.games }.thenByDescending { it.matches.first().date })
}

/** 相手チーム名を比べるための形(空白・かっこ・「FC」の有無、全角・半角の違いをそろえる) */
private fun rawNameKey(name: String): String =
    java.text.Normalizer.normalize(name.replace("&#039;", "'").replace("&amp;", "&"), java.text.Normalizer.Form.NFKC)
        .replace(Regex("[(（][^)）]*[)）]"), "")
        .replace(Regex("[\\s・･.]"), "")
        .replace(Regex("^FC|FC$"), "")
        .lowercase()

/**
 * チーム名が変わった・表記が違うだけの同じクラブ(先頭の名前にそろえて同じ相手として数える)。
 * 例:さいたまサイコロ → さいたまSAICOLO、アルコイリス神戸 → アルコ神戸、ユニアオレディース → アスレジーナユニアオあいち一宮、
 * 府中アスレティックFCプリメイラ → 府中アスレティックFCレディース → 立川・府中アスレティックFCレディース → 立川アスレティックFCレディース
 */
private val OPPONENT_ALIASES: List<List<String>> = listOf(
    listOf("さいたまSAICOLO", "さいたまサイコロ"),
    listOf("アルコ神戸", "アルコイリス神戸"),
    listOf("アスレジーナユニアオあいち一宮", "ユニアオレディース"),
    listOf(
        "立川アスレティックFCレディース", "立川・府中アスレティックFCレディース",
        "府中アスレティックFCレディース", "府中アスレティックFCプリメイラ"
    ),
    listOf("JAPANサッカーカレッジ", "JAPAN.S.C.", "JAPAN.S.C")
)
private val OPPONENT_ALIAS_KEYS: Map<String, String> = OPPONENT_ALIASES.flatMap { group ->
    val canonical = rawNameKey(group.first())
    group.map { rawNameKey(it) to canonical }
}.toMap()

private fun nameKey(name: String): String = rawNameKey(name).let { OPPONENT_ALIAS_KEYS[it] ?: it }

/**
 * 同じ相手か(表記ゆれにやや広く対応する)。
 * 「AC長野パルセイロ(長野県代表)」と「AC長野パルセイロ」、「アルバモス大阪」と「アルバモス大阪高石」、
 * 「トヨタ車体ブレイヴキングス刈谷」と「ブレイヴキングス刈谷」のような違いを同じ相手とみなす
 */
fun sameOpponentLoose(a: String, b: String): Boolean {
    val ka = nameKey(a)
    val kb = nameKey(b)
    if (ka.isEmpty() || kb.isEmpty()) return false
    if (ka == kb) return true
    val shorter = minOf(ka.length, kb.length)
    if (shorter >= 3 && (ka.startsWith(kb) || kb.startsWith(ka))) return true
    return shorter >= 5 && (ka.endsWith(kb) || kb.endsWith(ka))
}

/** その相手との、date より前の過去の試合(新しい順。エキシビジョンは除く) */
fun pastMeetings(team: Team, opponent: String, beforeDate: String): List<HistoryMatch> =
    HistoryRepository.matchesFor(team)
        .filter { !it.isExhibition && it.date < beforeDate && sameOpponentLoose(it.opponent, opponent) }
        .sortedByDescending { it.date }
