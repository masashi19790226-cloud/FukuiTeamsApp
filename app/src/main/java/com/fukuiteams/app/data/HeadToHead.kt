package com.fukuiteams.app.data

import com.fukuiteams.app.model.Game

/**
 * 次の相手との対戦成績(このアプリが記録している試合結果から数える)。
 * 試合日程と結果はアプリを使い始めたシーズンからのものなので、それより前の対戦は、
 * 過去の記録(data/history.json。ユナイテッド・ブルーサンダー)にあればそこから加える(past)。
 */
data class HeadToHead(
    val wins: Int,
    val losses: Int,
    val draws: Int,
    /** 対戦した試合と結果(新しい順) */
    val meetings: List<Pair<Game, RemoteGameResult>>,
    /** アプリの試合日程に無い、過去の記録の試合(新しい順) */
    val past: List<HistoryMatch> = emptyList()
) {
    val total: Int get() = meetings.size + past.size

    /** 対戦した試合を、アプリの記録と過去の記録をまとめて新しい順に */
    fun lines(): List<MeetingLine> =
        (meetings.map { (g, r) ->
            MeetingLine(
                sortDate = g.sortKey.take(8),
                dateLabel = "${g.dateLabel}(${g.dayOfWeek})",
                isHome = g.isHome,
                myScore = r.myScore,
                opponentScore = r.opponentScore,
                pk = "",
                outcome = r.outcome(),
                game = g
            )
        } + past.map { m ->
            MeetingLine(
                sortDate = m.date.replace("-", ""),
                dateLabel = m.dateLabel,
                isHome = when (m.homeAway) {
                    "H" -> true
                    "A" -> false
                    else -> null
                },
                myScore = m.myScore,
                opponentScore = m.opponentScore,
                pk = m.pk,
                outcome = m.outcome,
                game = null
            )
        }).sortedByDescending { it.sortDate }

    /** 過去の記録の試合を含むか */
    val usesHistory: Boolean get() = past.isNotEmpty()
}

/** 対戦した試合1つ(アプリの記録・過去の記録のどちらでも同じ形で出すため) */
data class MeetingLine(
    /** 20260926 */
    val sortDate: String,
    /** 「2026/9/26(土)」 */
    val dateLabel: String,
    /** HOME = true、AWAY = false、どちらでもない会場 = null */
    val isHome: Boolean?,
    val myScore: Int,
    val opponentScore: Int,
    /** PK戦の点数(無ければ空) */
    val pk: String,
    val outcome: GameOutcome,
    /** アプリの記録の試合なら、その試合 */
    val game: Game?
) {
    /** 「○2-1」「△1-1(PK3-4)」 */
    val scoreLabel: String
        get() = when (outcome) {
            GameOutcome.WIN -> "○"
            GameOutcome.LOSE -> "●"
            GameOutcome.DRAW -> "△"
        } + "$myScore-$opponentScore" + (if (pk.isNotBlank()) "(PK$pk)" else "")
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
    // 過去の記録(history.json)の試合のうち、アプリの試合日程に無い日の試合を加える(同じ試合を二重に数えない)
    val knownDates = games.filter { it.team == game.team }.map { it.sortKey.take(8) }.toSet()
    val before = game.sortKey.take(8).let { if (it.length == 8) "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)}" else "" }
    val past = if (before.isEmpty()) emptyList() else
        pastMeetings(game.team, game.opponent, before).filter { it.date.replace("-", "") !in knownDates }
    if (meetings.isEmpty() && past.isEmpty()) return null
    val outcomes = meetings.map { it.second.outcome() } + past.map { it.outcome }
    return HeadToHead(
        wins = outcomes.count { it == GameOutcome.WIN },
        losses = outcomes.count { it == GameOutcome.LOSE },
        draws = outcomes.count { it == GameOutcome.DRAW },
        meetings = meetings,
        past = past
    )
}
