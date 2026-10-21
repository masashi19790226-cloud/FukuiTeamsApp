package com.fukuiteams.app.data

import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val RESULTS_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/results.json"

/** 前半・後半などの区切りごとの点数(ブルーサンダーだけ。リーグH公式の試合ページから) */
data class ScorePeriod(val label: String, val myScore: Int, val opponentScore: Int)

data class RemoteGameResult(
    val myScore: Int,
    val opponentScore: Int,
    val sourceUrl: String? = null,
    /** 前半・後半の点数(分からなければ空) */
    val periods: List<ScorePeriod> = emptyList()
)

/** 「前半 16-14・後半 10-14」。区切りの点数が無ければ空 */
val RemoteGameResult.periodsLabel: String
    get() = periods.joinToString("・") { "${it.label} ${it.myScore}-${it.opponentScore}" }

/**
 * 試合結果をタップしたときに開く、結果の取得元ページ。
 * results.json の source_url → 試合ごとの結果ページ → チームの結果一覧ページ の順に使う。
 */
fun Game.resultSourceUrl(result: RemoteGameResult?): String =
    result?.sourceUrl?.takeIf { it.isNotBlank() }
        ?: resultPageUrl
        ?: when (team) {
            Team.BLOWINDS -> "https://www.bleague.jp/record/?club1=2891&club2=0"
            Team.RAC -> "https://ruck-fukui.com/schedules-results"
            Team.UNITED -> "https://fukuiunited.co.jp/"
            Team.THUNDER -> "https://www.fukuieiheijihand.co.jp/latestmatch/"
        }

object GameResultsRepository {

    suspend fun fetch(): Map<String, RemoteGameResult> = withContext(Dispatchers.IO) {
        try {
            val url = URL(RESULTS_JSON_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"

            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val obj = JSONObject(text)
            val map = mutableMapOf<String, RemoteGameResult>()
            obj.keys().forEach { gameId ->
                val entry = obj.getJSONObject(gameId)
                map[gameId] = RemoteGameResult(
                    myScore = entry.optInt("my_score"),
                    opponentScore = entry.optInt("opponent_score"),
                    sourceUrl = entry.optString("source_url").takeIf { it.isNotBlank() && it != "null" },
                    periods = entry.optJSONArray("periods")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            arr.optJSONObject(i)?.let { p -> ScorePeriod(p.optString("label"), p.optInt("my"), p.optInt("opp")) }
                        }.filter { it.label.isNotBlank() }
                    }.orEmpty()
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
