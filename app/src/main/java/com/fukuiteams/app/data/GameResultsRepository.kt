package com.fukuiteams.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val RESULTS_JSON_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/data/results.json"

data class RemoteGameResult(val myScore: Int, val opponentScore: Int)

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
                    opponentScore = entry.optInt("opponent_score")
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
