package com.fukuiteams.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import com.fukuiteams.app.model.Game

/**
 * 「どうやって観戦したか」「試合結果のスコア」は、公式の自動取得手段が無いため
 * この端末のDataStoreに、ユーザー自身が入力した内容として保存する。
 * 外部には送信しない。
 */
val Context.gameLogDataStore: DataStore<Preferences> by preferencesDataStore(name = "game_log")

enum class WatchMethod(val label: String) {
    ON_SITE("現地観戦"),
    STREAMING("配信(バスケットLIVEなど)"),
    NOT_WATCHED("見ていない")
}

private fun watchMethodKey(gameId: String) = stringPreferencesKey("watch_method_$gameId")

suspend fun saveWatchMethod(context: Context, gameId: String, method: WatchMethod) {
    context.gameLogDataStore.edit { it[watchMethodKey(gameId)] = method.name }
}

suspend fun loadWatchMethod(context: Context, gameId: String): WatchMethod? {
    val value = context.gameLogDataStore.data.first()[watchMethodKey(gameId)] ?: return null
    return WatchMethod.values().find { it.name == value }
}

enum class GameOutcome(val label: String) {
    WIN("勝ち"),
    LOSE("負け"),
    DRAW("引き分け")
}

private fun outcomeKey(gameId: String) = stringPreferencesKey("outcome_$gameId")

suspend fun saveGameOutcome(context: Context, gameId: String, outcome: GameOutcome) {
    context.gameLogDataStore.edit { it[outcomeKey(gameId)] = outcome.name }
}

suspend fun loadGameOutcome(context: Context, gameId: String): GameOutcome? {
    val value = context.gameLogDataStore.data.first()[outcomeKey(gameId)] ?: return null
    return GameOutcome.values().find { it.name == value }
}

/** 観戦成績(勝ち・負け・引き分け・結果未確定の件数)。 */
data class WatchRecord(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0, val unknown: Int = 0) {
    val watched: Int get() = wins + losses + draws + unknown

    /** 勝ち÷(勝ち+負け)を「.667」「1.000」の形式で。まだ勝ち負けが無いときは「-」。 */
    fun winRateLabel(): String {
        val decided = wins + losses
        if (decided == 0) return "-"
        val rate = wins.toDouble() / decided
        val text = String.format(java.util.Locale.US, "%.3f", rate)
        return if (rate >= 1.0) text else text.removePrefix("0")
    }

    /** 「4勝2敗」「2勝1敗1分」の形式。 */
    fun summaryLabel(): String = "${wins}勝${losses}敗" + if (draws > 0) "${draws}分" else ""

    operator fun plus(other: WatchRecord) =
        WatchRecord(wins + other.wins, losses + other.losses, draws + other.draws, unknown + other.unknown)
}

/**
 * 観戦方法ごとの成績を集計する(通算)。終了済みの全試合が対象で、
 * 「見ていない」または観戦方法が未記録の試合は NOT_WATCHED(未観戦)に入る。
 * 勝敗は、自動取得した結果(results.json)を優先し、無ければ手動で記録した勝敗を使う。
 * まだ始まっていない試合は、観戦予定として記録されていても数えない。
 */
fun computeWatchRecords(
    games: List<Game>,
    prefs: Preferences?,
    autoResults: Map<String, RemoteGameResult>
): Map<WatchMethod, WatchRecord> {
    val records = mutableMapOf(
        WatchMethod.ON_SITE to WatchRecord(),
        WatchMethod.STREAMING to WatchRecord(),
        WatchMethod.NOT_WATCHED to WatchRecord()
    )

    games.filterNot { it.isUpcoming() }.forEach { game ->
        // 観戦方法が未記録の試合は「未観戦」として数える
        val method = WatchMethod.values().find { it.name == prefs?.get(watchMethodKey(game.id)) }
            ?: WatchMethod.NOT_WATCHED

        val outcome = resolveOutcome(game.id, prefs, autoResults)
        val add = when (outcome) {
            GameOutcome.WIN -> WatchRecord(wins = 1)
            GameOutcome.LOSE -> WatchRecord(losses = 1)
            GameOutcome.DRAW -> WatchRecord(draws = 1)
            null -> WatchRecord(unknown = 1)
        }
        records[method] = records.getValue(method) + add
    }
    return records
}

/**
 * 試合の勝敗を決める。自動取得した結果(results.json)を優先し、無ければ手動の記録。
 * どちらも無ければ null。
 */
fun resolveOutcome(
    gameId: String,
    prefs: Preferences?,
    autoResults: Map<String, RemoteGameResult>
): GameOutcome? {
    val auto = autoResults[gameId]
    return when {
        auto != null && auto.myScore > auto.opponentScore -> GameOutcome.WIN
        auto != null && auto.myScore < auto.opponentScore -> GameOutcome.LOSE
        auto != null -> GameOutcome.DRAW
        else -> GameOutcome.values().find { it.name == prefs?.get(outcomeKey(gameId)) }
    }
}
