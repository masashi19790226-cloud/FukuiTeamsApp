package com.fukuiteams.app.data

import com.fukuiteams.app.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 試合当日の天気(Open-Meteo。登録やAPIキー不要の無料の天気予報)。
 * ホームゲームは福井市、アウェイは対戦相手の地名から場所を決め、試合開始の時刻の予報を返す。
 */
data class MatchWeather(val placeLabel: String, val summary: String, val temperature: Int, val rainChance: Int?)

object WeatherRepository {

    // 福井市中心部(ホームゲームの会場はおおむね福井市・坂井市周辺)
    private const val FUKUI_LAT = 36.064
    private const val FUKUI_LON = 136.220

    suspend fun forGame(game: Game): MatchWeather? = withContext(Dispatchers.IO) {
        try {
            val (lat, lon, label) = if (game.isHome) {
                Triple(FUKUI_LAT, FUKUI_LON, "福井")
            } else {
                geocode(game.opponent) ?: geocode(game.opponent.take(2)) ?: return@withContext null
            }
            // 試合の日の分だけ取る(終わった試合の「速報」でも、その日の天気を出せるように日付で指定する)
            val date = game.dateLabel.split("/").let { "%04d-%02d-%02d".format(it[0].toInt(), it[1].toInt(), it[2].toInt()) }
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&hourly=temperature_2m,precipitation_probability,weather_code&timezone=Asia%2FTokyo" +
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
            MatchWeather(label, weatherLabel(code), Math.round(temp).toInt(), rain)
        } catch (e: Exception) {
            null
        }
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
