package com.fukuiteams.app.data

import androidx.datastore.preferences.core.Preferences
import com.fukuiteams.app.model.Game

/**
 * ホーム・アウェイ別の成績。
 * 勝敗は観戦成績と同じ resolveOutcome()(自動取得した結果を優先し、無ければ自分で記録した勝敗)で決める。
 * どちらも無い試合は数えず、unknown として件数だけ持つ。数字を推測で作ることはしない。
 */
data class HomeAwayRecord(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0) {
    /** 結果が分かっている試合数(勝+敗+分)。 */
    val games: Int get() = wins + losses + draws

    /** 「3勝2敗」「2勝1敗1分」の形式。 */
    fun recordLabel(): String = "${wins}勝${losses}敗" + if (draws > 0) "${draws}分" else ""

    /** 勝率 = 勝利数 ÷ 試合数 × 100(小数点以下は四捨五入)。試合が無ければ「データなし」。 */
    fun winRatePercentLabel(): String =
        if (games == 0) "データなし" else "${Math.round(wins * 100.0 / games)}%"

    operator fun plus(other: HomeAwayRecord) =
        HomeAwayRecord(wins + other.wins, losses + other.losses, draws + other.draws)
}

data class HomeAwaySummary(
    val home: HomeAwayRecord,
    val away: HomeAwayRecord,
    /** 終了済みだが結果が分からない試合の数(集計から除外) */
    val unknown: Int
) {
    val total: HomeAwayRecord get() = home + away
}

/** 終了済みの試合について、ホーム・アウェイ別に勝敗を集計する。 */
fun computeHomeAwaySummary(
    games: List<Game>,
    prefs: Preferences?,
    autoResults: Map<String, RemoteGameResult>
): HomeAwaySummary {
    var home = HomeAwayRecord()
    var away = HomeAwayRecord()
    var unknown = 0
    games.filterNot { it.isUpcoming() }.forEach { game ->
        val add = when (resolveOutcome(game.id, prefs, autoResults)) {
            GameOutcome.WIN -> HomeAwayRecord(wins = 1)
            GameOutcome.LOSE -> HomeAwayRecord(losses = 1)
            GameOutcome.DRAW -> HomeAwayRecord(draws = 1)
            null -> null
        }
        if (add == null) {
            unknown++
        } else if (game.isHome) {
            home += add
        } else {
            away += add
        }
    }
    return HomeAwaySummary(home, away, unknown)
}
