package com.fukuiteams.app.data

import com.fukuiteams.app.model.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

/**
 * 3チームの選手の誕生日。GitHub Actions(build_previews.py)が1日1回、
 * ブローウィンズ・ユナイテッドの公式サイトの選手ページと、女子Fリーグ公式の丸岡RUCKの選手名簿から集め、
 * data/standings.json の "BIRTHDAYS" に入れている。
 * あわせて、身長・体重・出身地・出身校(公式サイトに載っている分だけ)も入っている。
 * 丸岡RUCKは身長・出身地だけ(公式サイトに体重・出身校が無い)。
 */
private const val STANDINGS_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/standings.json"

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

data class PlayerBirthday(
    val team: Team?,
    val name: String,
    val number: String,
    val photo: String,
    val birthday: LocalDate,
    /** 身長(cm)・体重(kg)。分からなければ空 */
    val height: String = "",
    val weight: String = "",
    /** 出身地(例「兵庫県」)・出身校(ブローウィンズのみ)。分からなければ空 */
    val hometown: String = "",
    val school: String = "",
    /** ポジション(例「GK」「FP」「C/PF」)。分からなければ空 */
    val position: String = ""
) {
    /** その日に迎える年齢(例:2026年10月8日に1999年10月8日生まれ → 27) */
    fun ageOn(date: LocalDate): Int = date.year - birthday.year

    /** その日が誕生日か(2月29日生まれは、うるう年でない年は2月28日を誕生日とする) */
    fun isBirthdayOn(date: LocalDate): Boolean {
        if (birthday.monthValue == 2 && birthday.dayOfMonth == 29 && !date.isLeapYear) {
            return date.monthValue == 2 && date.dayOfMonth == 28
        }
        return date.monthValue == birthday.monthValue && date.dayOfMonth == birthday.dayOfMonth
    }

    /** 今日から次の誕生日まで何日か(今日なら0) */
    fun daysUntilNext(today: LocalDate): Int {
        for (d in 0..366) if (isBirthdayOn(today.plusDays(d.toLong()))) return d
        return 999
    }

    /** 一覧に出す名前(例「#13 川島 聖那」) */
    val label: String get() = (if (number.isNotBlank()) "#$number " else "") + name

    /** 今の年齢(満年齢) */
    fun currentAge(today: LocalDate): Int = java.time.Period.between(birthday, today).years

    /** 身長の数字(並べ替え・比べる用)。分からなければ null */
    val heightCm: Double? get() = height.toDoubleOrNull()

    /** 選手タブに出す1行(例「175cm/75kg・兵庫県出身・東海大学」)。何も分からなければ空 */
    val profileLine: String get() {
        val body = when {
            height.isNotBlank() && weight.isNotBlank() -> "${height}cm/${weight}kg"
            height.isNotBlank() -> "${height}cm"
            else -> ""
        }
        return listOf(body, hometown.takeIf { it.isNotBlank() }?.let { "${it}出身" } ?: "", school)
            .filter { it.isNotBlank() }.joinToString("・")
    }
}

/** 名前を比べるための形(空白を取り、異体字をそろえる) */
fun playerNameKey(name: String): String =
    name.replace(Regex("[\\s　・･]+"), "").replace('髙', '高').replace('﨑', '崎')

/** チーム+名前 → 選手のプロフィール(選手タブで、成績の一覧の選手と結び付けるため) */
fun List<PlayerBirthday>.profileMap(): Map<String, PlayerBirthday> =
    filter { it.team != null }.associateBy { profileKey(it.team!!, it.name) }

fun profileKey(team: Team, name: String): String = team.name + "|" + playerNameKey(name)

object BirthdaysRepository {

    fun today(): LocalDate = LocalDate.now(JST)

    /** 取得できなければ空。 */
    suspend fun fetch(): List<PlayerBirthday> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(STANDINGS_JSON_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val arr = JSONObject(text).optJSONObject("BIRTHDAYS")?.optJSONArray("players") ?: return@withContext emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val date = runCatching { LocalDate.parse(o.optString("birthday")) }.getOrNull() ?: return@mapNotNull null
                val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PlayerBirthday(
                    team = Team.values().firstOrNull { it.name == o.optString("team") },
                    name = name,
                    number = o.optString("number"),
                    photo = o.optString("photo"),
                    birthday = date,
                    height = o.optString("height"),
                    weight = o.optString("weight"),
                    hometown = o.optString("hometown"),
                    school = o.optString("school"),
                    position = o.optString("position")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
