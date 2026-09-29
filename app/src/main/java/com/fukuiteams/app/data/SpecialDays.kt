package com.fukuiteams.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * コラボ企画など「特別な日」。一面の特集枠・日程の★印・試合詳細・ウィジェットで目立たせる。
 * 一覧は GitHub の scripts/special_days.json から読む(書き換えて push すればアプリの作り直しは不要)。
 * 読めないときは、アプリに組み込んだ一覧を使う。
 */
private const val SPECIAL_DAYS_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/scripts/special_days.json"

data class SpecialDay(
    /** 2026-12-20 の形 */
    val date: String,
    val title: String,
    val note: String,
    val teams: List<Team>,
    /** 公式発表済みなら true。false のときは「公式発表前」と表示する */
    val confirmed: Boolean
) {
    val localDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()

    /** 今日から何日後か(当日は 0、過ぎていれば負)。 */
    fun daysUntil(): Long? = localDate?.let { ChronoUnit.DAYS.between(LocalDate.now(ZoneId.of("Asia/Tokyo")), it) }

    /** 「12/20(日)」の形。 */
    fun dateLabel(): String {
        val d = localDate ?: return date
        val week = listOf("月", "火", "水", "木", "金", "土", "日")[d.dayOfWeek.value - 1]
        return "${d.monthValue}/${d.dayOfMonth}($week)"
    }
}

// 組み込みの一覧(GitHub から読めないとき用。scripts/special_days.json と同じ内容)
private val BUILT_IN = listOf(
    SpecialDay(
        date = "2026-12-20",
        title = "ブローウィンズ×丸岡RUCK コラボデー",
        note = "同じ週末にセーレン・ドリームアリーナで両チームの試合が予定されています。コラボ企画がありそうです。詳細は公式発表をお待ちください。",
        teams = listOf(Team.BLOWINDS, Team.RAC),
        confirmed = false
    )
)

object SpecialDaysRepository {
    /** いま使っている一覧。画面はこれを見て表示を変える。 */
    var days by mutableStateOf(BUILT_IN)
        private set

    /** GitHub から読み直す。失敗したら今の一覧のまま。 */
    suspend fun refresh(): List<SpecialDay> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(SPECIAL_DAYS_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val array = JSONObject(text).optJSONArray("days")
            val list = (0 until (array?.length() ?: 0)).mapNotNull { i ->
                val o = array?.optJSONObject(i) ?: return@mapNotNull null
                val teamArray = o.optJSONArray("teams")
                val teams = (0 until (teamArray?.length() ?: 0)).mapNotNull { j ->
                    Team.values().find { it.name == teamArray?.optString(j) }
                }
                val date = o.optString("date")
                if (date.isBlank() || teams.isEmpty()) null
                else SpecialDay(date, o.optString("title"), o.optString("note"), teams, o.optBoolean("confirmed", false))
            }
            days = list
        } catch (e: Exception) {
            // 読めなければ今の一覧のまま
        }
        days
    }
}

/** この試合が特別な日の試合なら、その特別な日。 */
fun Game.specialDay(days: List<SpecialDay> = SpecialDaysRepository.days): SpecialDay? {
    val ymd = sortKey.take(8)
    return days.firstOrNull { it.date.replace("-", "") == ymd && team in it.teams }
}

/** 一面の特集枠に出す特別な日(今日から45日以内で、まだ過ぎていないもの。近い順の先頭)。 */
fun upcomingSpecialDay(days: List<SpecialDay> = SpecialDaysRepository.days): SpecialDay? =
    days.filter { d -> d.daysUntil()?.let { it in 0L..45L } == true }.minByOrNull { it.date }
