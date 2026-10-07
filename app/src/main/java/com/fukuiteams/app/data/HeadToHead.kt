package com.fukuiteams.app.data

import com.fukuiteams.app.model.Game

/**
 * 次の相手との対戦成績(このアプリが記録している試合結果から数える)。
 * 試合日程と結果はアプリを使い始めたシーズンからのものなので、それより前の対戦は入らない。
 */
data class HeadToHead(
    val wins: Int,
    val losses: Int,
    val draws: Int,
    /** 対戦した試合と結果(新しい順) */
    val meetings: List<Pair<Game, RemoteGameResult>>
) {
    val total: Int get() = meetings.size
}

/** 相手チーム名を比べるための形(空白や「FC」の有無、全角・半角の違いをそろえる) */
private fun opponentKey(name: String): String =
    java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC)
        .replace(Regex("[\\s・()（）]"), "")
        .replace(Regex("^FC|FC$"), "")
        .lowercase()

private fun sameOpponent(a: String, b: String): Boolean {
    val ka = opponentKey(a)
    val kb = opponentKey(b)
    if (ka.isEmpty() || kb.isEmpty()) return false
    // 「AC長野パルセイロ(長野県代表)」と「AC長野パルセイロ」のような表記ゆれも同じ相手とみなす
    return ka == kb || (minOf(ka.length, kb.length) >= 2 && (ka.startsWith(kb) || kb.startsWith(ka)))
}

/** game(これからの試合)の相手との、これまでの対戦成績。対戦が1試合も無ければ null。 */
fun headToHead(game: Game, games: List<Game>, results: Map<String, RemoteGameResult>): HeadToHead? {
    val meetings = games
        .filter { it.team == game.team && it.id != game.id && it.sortKey < game.sortKey && sameOpponent(it.opponent, game.opponent) }
        .mapNotNull { g -> results[g.id]?.let { g to it } }
        .sortedByDescending { it.first.sortKey }
    if (meetings.isEmpty()) return null
    val outcomes = meetings.map { it.second.outcome() }
    return HeadToHead(
        wins = outcomes.count { it == GameOutcome.WIN },
        losses = outcomes.count { it == GameOutcome.LOSE },
        draws = outcomes.count { it == GameOutcome.DRAW },
        meetings = meetings
    )
}
