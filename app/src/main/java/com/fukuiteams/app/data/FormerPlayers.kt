package com.fukuiteams.app.data

import com.fukuiteams.app.model.Game

/**
 * 次の相手にいる、福井のチームの元選手(元所属)。Web版の formerPlayersAt と同じ。
 * 歴代の選手(history.json)のうち、いまの所属(now_team)が相手のクラブの選手と、
 * 丸岡RUCKは退団・移籍のお知らせ(元選手)のいまのクラブから。
 * note:福井での記録など(「福井での記録:2023-24〜2024-25・104試合・428点」)
 */
data class FormerPlayer(
    val name: String,
    var number: String = "",
    var position: String = "",
    var stat: String = "",
    val note: String = ""
)

/** 名前を比べるための形(空白と「・」を除く) */
fun formerNameKey(n: String): String = n.replace(Regex("[\\s　・･]"), "")

fun formerPlayersAt(game: Game): List<FormerPlayer> {
    val team = game.team
    val names = listOf(game.opponent, fullOpponentName(team, game.opponent)).filter { it.isNotBlank() }.distinct()
    fun at(club: String) = club.isNotBlank() && names.any { sameOpponentLoose(club, it) }
    val out = mutableListOf<FormerPlayer>()
    HistoryRepository.data[team]?.players.orEmpty().forEach { p ->
        if (p.now != false || !at(p.nowTeam)) return@forEach
        val rec = listOf(p.seasonsLabel, "${p.apps}試合", "${p.goals}${goalUnit(team)}").filter { it.isNotBlank() }.joinToString("・")
        out += FormerPlayer(name = p.name, note = "福井での記録:$rec")
    }
    Alumni.players.filter { it.team == team && at(it.now) }.forEach { a ->
        val goals = a.goals?.let { "今季${it}得点" }.orEmpty()
        val same = out.firstOrNull { formerNameKey(it.name) == formerNameKey(a.name) }
        if (same != null) {
            // 歴代の選手にもいる選手は、いまの背番号と今季の得点だけ足す
            if (same.number.isBlank()) same.number = a.nowNumber
            if (same.stat.isBlank()) same.stat = goals
        } else {
            val left = if (a.left.isNotBlank()) "${a.left.replace("-", "/")} ${a.kind}".trim() else ""
            out += FormerPlayer(name = a.name, number = a.nowNumber, stat = goals, note = left)
        }
    }
    // ブローウィンズは、次の相手の選手名簿(Bリーグ公式)から、いまの背番号・ポジション・今季の平均得点を足す
    val opp = PlayersRepository.latest[BLOWINDS_OPP_KEY]
    if (team == com.fukuiteams.app.model.Team.BLOWINDS && opp != null && opp.gameId == game.id) {
        out.forEach { f ->
            val r = opp.players.firstOrNull { formerNameKey(it.name) == formerNameKey(f.name) } ?: return@forEach
            if (f.number.isBlank()) f.number = r.number
            if (f.position.isBlank()) f.position = r.position
            if (f.stat.isBlank()) f.stat = r.points?.takeIf { it.isNotBlank() }?.let { "平均${it}点" }.orEmpty()
        }
    }
    return out
}
