package com.fukuiteams.app.data

import com.fukuiteams.app.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/**
 * 試合当日の天気(Open-Meteo。登録やAPIキー不要の無料の天気予報)。
 * 会場名から場所を決め、試合開始の時刻の予報を返す。
 */
data class MatchWeather(
    val placeLabel: String,
    val summary: String,
    val temperature: Int,
    val rainChance: Int?,
    /** その1時間の降水量(mm)。取れなければ null */
    val precipitationMm: Double? = null,
    /** その1時間の降雪量(cm)。取れなければ null */
    val snowfallCm: Double? = null,
    /** 積雪の深さ(cm)。取れなければ null */
    val snowDepthCm: Int? = null,
    /** この予報をアプリが取得した日時(予報の発表日時とは別) */
    val retrievedAt: Instant = Instant.now()
) {
    /**
     * 降水量・降雪量・積雪(0より多いものだけ)。例「1時間に2.5mm・降雪1cm・積雪15cm」。どれも無ければ ""
     */
    val amountLabel: String
        get() = listOfNotNull(
            precipitationMm?.takeIf { it >= 0.1 }?.let { "1時間に${"%.1f".format(it)}mm" },
            snowfallCm?.takeIf { it >= 0.1 }?.let { "降雪${if (it >= 1) Math.round(it).toString() else "%.1f".format(it)}cm" },
            snowDepthCm?.takeIf { it >= 1 }?.let { "積雪${it}cm" }
        ).joinToString("・")
}

/** 試合前の天気予報(一面の「次の試合」用)。入場の時刻と試合開始の時刻、それぞれの予報。 */
data class GameForecast(
    val entryTime: String?,
    val entry: MatchWeather?,
    val startTime: String,
    val start: MatchWeather?,
    /** 入場の1時間前(入場の時刻が分からなければ試合開始の1時間前)の時刻(例「11:00」)と、その名前・予報。取れなければ null */
    val beforeTime: String? = null,
    val beforeLabel: String = "",
    val before: MatchWeather? = null
)

object WeatherRepository {

    /**
     * 「次の試合」に出す天気予報。入場の時刻(分かるときだけ)と試合開始の時刻の予報を返す。
     * 試合開始の時刻が決まっていない(時間未定)ときや、予報が取れないときは null。
     */
    suspend fun preGameForecast(game: Game, entryTime: String?): GameForecast? {
        val startHour = Regex("""^(\d{1,2}):\d{2}$""").find(game.timeLabel.trim())?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val entryHour = entryTime?.let { Regex("""^(\d{1,2}):\d{2}$""").find(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
        // 会場へ向かうころの天気:入場の1時間前(入場の時刻が分からなければ試合開始の1時間前)
        val baseHour = entryHour ?: startHour
        val beforeHour = (baseHour - 1).takeIf { it >= 0 }
        val map = forGameHours(game, listOfNotNull(beforeHour, entryHour, startHour))
        val start = map[startHour]
        val entry = entryHour?.let { map[it] }
        val before = beforeHour?.let { map[it] }
        if (start == null && entry == null && before == null) return null
        return GameForecast(
            entryTime?.takeIf { entry != null }, entry, game.timeLabel.trim(), start,
            beforeTime = beforeHour?.takeIf { before != null }?.let { "%d:00".format(it) },
            beforeLabel = if (entryHour != null) "入場の1時間前" else "試合開始の1時間前",
            before = before
        )
    }


    // 福井市中心部(ホームゲームの会場はおおむね福井市・坂井市周辺)
    private const val FUKUI_LAT = 36.064
    private const val FUKUI_LON = 136.220

    suspend fun forGame(game: Game): MatchWeather? = withContext(Dispatchers.IO) {
        try {
            val (lat, lon, label) = gameLocation(game) ?: return@withContext null
            // 試合の日の分だけ取る(終わった試合の「速報」でも、その日の天気を出せるように日付で指定する)
            val date = game.dateLabel.split("/").let { "%04d-%02d-%02d".format(it[0].toInt(), it[1].toInt(), it[2].toInt()) }
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&hourly=temperature_2m,precipitation_probability,weather_code,precipitation,snowfall,snow_depth&timezone=Asia%2FTokyo" +
                "&start_date=$date&end_date=$date"
            val o = JSONObject(get(url))
            val hourly = o.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            // 試合開始の時刻(「15:05」なら15時)の予報を使う
            val hour = game.timeLabel.substringBefore(":").toIntOrNull() ?: 12
            val target = "%sT%02d:00".format(date, hour)
            val idx = (0 until times.length()).firstOrNull { times.getString(it) == target } ?: return@withContext null
            val temp = hourly.getJSONArray("temperature_2m").optDouble(idx)
            val rain = hourly.optJSONArray("precipitation_probability")?.optInt(idx, -1)?.takeIf { it >= 0 }
            val code = hourly.getJSONArray("weather_code").optInt(idx)
            MatchWeather(label, weatherLabel(code), Math.round(temp).toInt(), rain, amounts(hourly, idx).first, amounts(hourly, idx).second, amounts(hourly, idx).third)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 試合の日の、指定した時刻(時)ごとの予報。1回の問い合わせでまとめて取る。
     * 例:hours = [13, 14] → 13時(開場)と14時(試合開始)の予報。取れなかった時刻は入らない。
     */
    suspend fun forGameHours(game: Game, hours: List<Int>): Map<Int, MatchWeather> = withContext(Dispatchers.IO) {
        try {
            val (lat, lon, label) = gameLocation(game) ?: return@withContext emptyMap()
            val date = game.dateLabel.split("/").let { "%04d-%02d-%02d".format(it[0].toInt(), it[1].toInt(), it[2].toInt()) }
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&hourly=temperature_2m,precipitation_probability,weather_code,precipitation,snowfall,snow_depth&timezone=Asia%2FTokyo" +
                "&start_date=$date&end_date=$date"
            val hourly = JSONObject(get(url)).getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            hours.distinct().mapNotNull { hour ->
                val target = "%sT%02d:00".format(date, hour)
                val idx = (0 until times.length()).firstOrNull { times.getString(it) == target } ?: return@mapNotNull null
                val temp = hourly.getJSONArray("temperature_2m").optDouble(idx)
                if (temp.isNaN()) return@mapNotNull null
                val rain = hourly.optJSONArray("precipitation_probability")?.optInt(idx, -1)?.takeIf { it >= 0 }
                val code = hourly.getJSONArray("weather_code").optInt(idx)
                val (precip, snow, depth) = amounts(hourly, idx)
                hour to MatchWeather(label, weatherLabel(code), Math.round(temp).toInt(), rain, precip, snow, depth)
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 会場の場所の表(会場名の一部・緯度と経度・表示する地名)。Open-Meteo の地名検索は施設名を探せないことが多いので、
     * 日程に出てくる会場は先にこの表で探す(Web版の weather.js の VENUES と同じ)。位置は会場のある市区町村のあたり
     */
    private val VENUES: List<Triple<String, Pair<Double, Double>, String>> = listOf(
        Triple("セーレン・ドリームアリーナ", 36.064 to 136.22, "福井市"),
        Triple("北陸電力福井体育館", 36.064 to 136.22, "福井市"),
        Triple("飛騨高山ビッグアリーナ", 36.146 to 137.252, "高山市"),
        Triple("敦賀市総合運動公園", 35.645 to 136.055, "敦賀市"),
        Triple("越前市アイシン", 35.903 to 136.169, "越前市"),
        Triple("このはなアリーナ", 34.987 to 138.436, "静岡市"),
        Triple("山形県総合運動公園", 38.36 to 140.378, "天童市"),
        Triple("国立代々木競技場", 35.668 to 139.7, "渋谷区"),
        Triple("鹿児島県総合体育センター", 31.56 to 130.548, "鹿児島市"),
        Triple("アオーレ長岡", 37.446 to 138.851, "長岡市"),
        Triple("いしかわ総合スポーツセンター", 36.582 to 136.618, "金沢市"),
        Triple("エスフォルタアリーナ八王子", 35.656 to 139.338, "八王子市"),
        Triple("マエダアリーナ", 40.8 to 140.69, "青森市"),
        Triple("熊本県立総合体育館", 32.803 to 130.708, "熊本市"),
        Triple("横浜BUNTAI", 35.443 to 139.637, "横浜市"),
        Triple("バルドラール浦安アリーナ", 35.653 to 139.902, "浦安市"),
        Triple("俵田翁記念体育館", 33.951 to 131.247, "宇部市"),
        Triple("生駒市", 34.692 to 135.7, "生駒市"),
        Triple("和光市", 35.781 to 139.606, "和光市"),
        Triple("本宮市", 37.513 to 140.394, "本宮市"),
        Triple("富山県西部体育センター", 36.754 to 137.026, "高岡市"),
        Triple("エントリオ", 35.254 to 136.822, "稲沢市"),
        Triple("枇杷島スポーツセンター", 35.19 to 136.875, "名古屋市"),
        Triple("クロコくんホール", 35.103 to 136.917, "名古屋市"),
        Triple("ウィングアリーナ刈谷", 34.989 to 136.997, "刈谷市"),
        Triple("アリーナ立川立飛", 35.712 to 139.416, "立川市"),
        Triple("大田区総合体育館", 35.561 to 139.716, "大田区"),
        Triple("豊見城市民体育館", 26.161 to 127.669, "豊見城市")
    )

    /** 対戦相手の本拠地ではなく、試合会場を優先する。ホーム会場が未登録なら従来の福井市座標を使う。 */
    private suspend fun gameLocation(game: Game): Triple<Double, Double, String>? {
        val venue = game.venue.trim()
        if (venue.isNotBlank() && venue != "会場未定" && !venue.contains("調整中") && !venue.contains("未定")) {
            VENUES.firstOrNull { venue.contains(it.first) }?.let { return Triple(it.second.first, it.second.second, it.third) }
            geocode(venue)?.let { return it }
            // 会場名に市区町村の名前があれば、その地名で探す(例「○○市総合体育館」)
            Regex("([^\\s　0-9A-Za-z()（）・]{1,5}?[市区町村])").find(venue)?.groupValues?.get(1)?.let { city ->
                (geocode(city) ?: geocode(city.dropLast(1)))?.let { return it }
            }
            // Open-Meteoの地名検索で施設名が見つからない場合、県内の市区町村名を含む会場のみ地域を補助検索。
            Regex("(福井|坂井|敦賀|鯖江|越前|大野|勝山|あわら|小浜|三国)").find(venue)?.value?.let { city ->
                geocode(city)?.let { return it }
            }
        }
        return if (game.isHome) Triple(FUKUI_LAT, FUKUI_LON, "福井市") else null
    }

    /** 降水量(mm)・降雪量(cm)・積雪の深さ(m → cm)。取れないものは null */
    private fun amounts(hourly: JSONObject, idx: Int): Triple<Double?, Double?, Int?> {
        fun at(key: String): Double? = hourly.optJSONArray(key)?.let { a ->
            if (idx < a.length() && !a.isNull(idx)) a.optDouble(idx).takeIf { !it.isNaN() } else null
        }
        return Triple(at("precipitation"), at("snowfall"), at("snow_depth")?.let { Math.round(it * 100).toInt() })
    }

    private fun geocode(name: String): Triple<Double, Double, String>? = try {
        if (name.isBlank()) null else {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                URLEncoder.encode(name, "UTF-8") + "&count=1&language=ja&countryCode=JP"
            val results = JSONObject(get(url)).optJSONArray("results")
            if (results == null || results.length() == 0) null else {
                val r = results.getJSONObject(0)
                Triple(r.getDouble("latitude"), r.getDouble("longitude"), r.optString("name", name))
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    /** WMOの天気コードを日本語に。 */
    private fun weatherLabel(code: Int): String = when (code) {
        0 -> "快晴"
        1, 2 -> "晴れ"
        3 -> "くもり"
        45, 48 -> "霧"
        in 51..57 -> "霧雨"
        in 61..67, in 80..82 -> "雨"
        in 71..77, 85, 86 -> "雪"
        in 95..99 -> "雷雨"
        else -> "−"
    }
}
