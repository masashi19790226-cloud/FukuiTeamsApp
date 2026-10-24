package com.fukuiteams.app.data

import com.fukuiteams.app.model.Team

/**
 * 過去の記録(data/history.json)から数える「記録集」・選手の相手別の成績・節目。
 * どれも history.json にある数字だけで数える(数字を作らない)。
 */

/** チームの記録1つ(例「1試合最多得点」「112点」「2024/11/3 vs 信州 ○112-80」) */
data class TeamRecord(
    val label: String,
    val value: String,
    val detail: String,
    /** 押したときに開く公式の試合のページ(無ければ null) */
    val url: String? = null
)

/** 1試合の個人の記録1つ(例 ○○選手 32点 2024/11/3 vs 信州) */
data class GameHigh(
    val name: String,
    val value: Int,
    val date: String,
    val dateLabel: String,
    val opponent: String,
    val scoreLabel: String
)

/** チームの記録(公式戦。エキシビジョンは除く)。試合が無ければ空 */
fun teamRecords(team: Team): List<TeamRecord> {
    val matches = HistoryRepository.matchesFor(team).filterNot { it.isExhibition }
    if (matches.isEmpty()) return emptyList()
    val unit = if (team == Team.BLOWINDS) "点" else "得点"
    fun line(m: HistoryMatch) = "${m.dateLabel} vs ${m.opponent} ${m.scoreLabel}"
    val out = mutableListOf<TeamRecord>()
    // 同じ記録が複数あるときは、新しい試合を出す
    matches.maxWithOrNull(compareBy<HistoryMatch> { it.myScore }.thenBy { it.date })?.let { m ->
        out += TeamRecord("1試合の最多得点", "${m.myScore}$unit", line(m), m.url.ifBlank { null })
    }
    matches.filter { it.outcome == GameOutcome.WIN }
        .maxWithOrNull(compareBy<HistoryMatch> { it.myScore - it.opponentScore }.thenBy { it.date })?.let { m ->
            out += TeamRecord("最大点差の勝利", "${m.myScore - m.opponentScore}点差", line(m), m.url.ifBlank { null })
        }
    // 最多連勝(日付順。引き分け・PK戦は連勝が止まる)
    val sorted = matches.sortedWith(compareBy({ it.date }, { it.time }))
    var best = 0
    var bestFrom: HistoryMatch? = null
    var bestTo: HistoryMatch? = null
    var cur = 0
    var curFrom: HistoryMatch? = null
    for (m in sorted) {
        if (m.outcome == GameOutcome.WIN) {
            if (cur == 0) curFrom = m
            cur++
            // 同じ連勝数なら新しいほう
            if (cur >= best) {
                best = cur
                bestFrom = curFrom
                bestTo = m
            }
        } else {
            cur = 0
        }
    }
    if (best >= 2 && bestFrom != null && bestTo != null) {
        out += TeamRecord(
            "最多連勝",
            "${best}連勝",
            "${bestFrom.dateLabel}〜${bestTo.dateLabel}"
        )
    }
    // 無失点の試合数(サッカー・フットサルだけ)
    if (team != Team.BLOWINDS && team != Team.THUNDER) {
        val cleanSheets = matches.count { it.opponentScore == 0 }
        if (cleanSheets > 0) out += TeamRecord("無失点の試合", "${cleanSheets}試合", "公式戦${matches.size}試合のうち")
    }
    matches.filter { it.homeAway == "H" && it.visitors != null }
        .maxWithOrNull(compareBy<HistoryMatch> { it.visitors ?: 0 }.thenBy { it.date })?.let { m ->
            out += TeamRecord("ホームの最多観客", "${"%,d".format(m.visitors)}人", line(m), m.url.ifBlank { null })
        }
    return out
}

/**
 * 1試合の個人の記録の上位(歴代)。value が null・0 の試合は数えない。
 * 試合ごとの記録(選手の log)が無いチームは、試合の得点者(scorers)から数える(得点だけ)
 */
fun singleGameHighs(team: Team, top: Int = 5, value: (PlayerGameLog) -> Int?): List<GameHigh> {
    val players = HistoryRepository.data[team]?.players.orEmpty()
    val rows = players.flatMap { p ->
        p.log.mapNotNull { l ->
            val v = value(l) ?: return@mapNotNull null
            if (v <= 0) return@mapNotNull null
            GameHigh(p.name, v, l.date, l.dateLabel, l.opponent, l.scoreLabel)
        }
    }
    return rows.sortedWith(compareByDescending<GameHigh> { it.value }.thenByDescending { it.date }).take(top)
}

/** 試合の得点者(scorers)から、1試合の最多得点の上位(選手の試合ごとの記録が無いときの代わり) */
fun singleGameGoalsFromScorers(team: Team, top: Int = 5): List<GameHigh> =
    HistoryRepository.matchesFor(team).filterNot { it.isExhibition }.flatMap { m ->
        m.scorers.filter { it.second > 0 }.map { (n, g) ->
            GameHigh(NameChanges.current(team, n), g, m.date, m.dateLabel, m.opponent, m.scoreLabel)
        }
    }.sortedWith(compareByDescending<GameHigh> { it.value }.thenByDescending { it.date }).take(top)

/** 選手1人の、相手チームごとの成績 */
data class OpponentSplit(
    val opponent: String,
    val games: Int,
    val wins: Int,
    /** 得点(ブローウィンズは得点、ほかはゴール)の合計 */
    val points: Int,
    /** ブローウィンズだけ(無ければ null) */
    val rebounds: Int? = null,
    val assists: Int? = null
) {
    val pointsPerGame: Double get() = if (games > 0) points.toDouble() / games else 0.0
}

/** 選手の試合ごとの記録を、相手ごとにまとめる(対戦の多い順)。表記ゆれ・名前の変わった相手はまとめる */
fun opponentSplits(log: List<PlayerGameLog>): List<OpponentSplit> {
    val groups = mutableListOf<MutableList<PlayerGameLog>>()
    log.sortedByDescending { it.date }.forEach { l ->
        val g = groups.firstOrNull { sameOpponentLoose(it.first().opponent, l.opponent) }
        if (g != null) g += l else groups += mutableListOf(l)
    }
    return groups.map { g ->
        OpponentSplit(
            opponent = g.first().opponent,
            games = g.size,
            wins = g.count { it.won },
            points = g.sumOf { it.points },
            rebounds = g.mapNotNull { it.rebounds }.takeIf { it.isNotEmpty() }?.sum(),
            assists = g.mapNotNull { it.assists }.takeIf { it.isNotEmpty() }?.sum()
        )
    }.sortedWith(compareByDescending<OpponentSplit> { it.games }.thenByDescending { it.points })
}

/**
 * 次の相手に強い今の選手(その相手との試合で、1試合平均の得点が多い順)。
 * 2試合以上出た選手だけ。試合ごとの記録が無ければ空
 */
fun strongAgainst(team: Team, opponent: String, top: Int = 3): List<Pair<HistoryPlayer, OpponentSplit>> {
    val full = fullOpponentName(team, opponent)
    return HistoryRepository.data[team]?.players.orEmpty()
        .filter { it.now == true && it.log.isNotEmpty() }
        .mapNotNull { p ->
            val games = p.log.filter { sameOpponentLoose(it.opponent, full) || sameOpponentLoose(it.opponent, opponent) }
            if (games.size < 2) return@mapNotNull null
            val s = opponentSplits(games).firstOrNull() ?: return@mapNotNull null
            val merged = s.copy(games = games.size, wins = games.count { it.won }, points = games.sumOf { it.points })
            if (merged.points <= 0) null else p to merged
        }
        .sortedByDescending { it.second.pointsPerGame }
        .take(top)
}

/** 今の選手の、通算の出場・得点の節目が近いもの(「節目まであと」の記事と、節目の通知に使う) */
data class Milestone(
    val player: HistoryPlayer,
    /** 同じ節目を2回通知しないための目印(チーム・選手・種類・節目) */
    val key: String,
    val headline: String,
    val body: String,
    val left: Int,
    val unit: String
)

/**
 * 今の選手のうち、通算の出場・得点の節目(100試合など)や歴代最多得点が近い選手(残りの少ない順)。
 * 過去の記録(歴代の選手)の通算から。丸岡RUCKは出場選手の記録があるときだけ出場も数える
 */
fun upcomingMilestones(team: Team): List<Milestone> {
    val data = HistoryRepository.data[team] ?: return emptyList()
    val all = data.players
    val current = all.filter { it.now == true }
    if (current.isEmpty()) return emptyList()
    val knowApps = team != Team.RAC || data.playersFromLineup
    // 得点の節目と、「もうすぐ」とみなす残り(バスケットボールは1試合の得点が多いので大きく)
    val goalSteps = when (team) {
        Team.BLOWINDS -> listOf(500, 1000, 1500, 2000, 2500, 3000) to 40
        Team.THUNDER -> listOf(50, 100, 150, 200, 300) to 10
        else -> listOf(10, 20, 30, 50, 100, 150, 200) to 2
    }
    val appSteps = listOf(50, 100, 150, 200, 250, 300)
    val near = mutableListOf<Milestone>()
    val unit = if (team == Team.BLOWINDS) "点" else "得点"
    current.forEach { p ->
        if (knowApps) {
            appSteps.firstOrNull { it > p.apps }?.let { step ->
                val left = step - p.apps
                if (left in 1..3) near += Milestone(
                    p, "${team.name}|${p.name}|apps|$step",
                    "${p.name}、通算${step}試合出場まであと$left",
                    "これまで${p.seasonsLabel}に${p.apps}試合に出場。あと${left}試合で通算${step}試合に届きます。", left, "試合"
                )
            }
        }
        goalSteps.first.firstOrNull { it > p.goals }?.let { step ->
            val left = step - p.goals
            if (left in 1..goalSteps.second) near += Milestone(
                p, "${team.name}|${p.name}|goals|$step",
                "${p.name}、通算${step}${unit}まであと$left",
                "これまで${p.seasonsLabel}に通算${p.goals}$unit。あと${left}で通算${step}${unit}に届きます。", left, unit
            )
        }
    }
    // 歴代最多得点:今の選手が2位で、1位との差が小さいとき
    val byGoals = all.sortedByDescending { it.goals }
    if (byGoals.size >= 2 && byGoals[1].now == true && byGoals[0].goals > 0) {
        val gap = byGoals[0].goals - byGoals[1].goals + 1
        if (gap in 1..goalSteps.second * 2) {
            val p = byGoals[1]
            near += Milestone(
                p, "${team.name}|${p.name}|top|${byGoals[0].goals}",
                "${p.name}、歴代最多得点まであと$gap",
                "通算${p.goals}${unit}で歴代2位。1位は${byGoals[0].name}の${byGoals[0].goals}${unit}で、あと${gap}で歴代最多になります。", gap, unit
            )
        }
    }
    return near.sortedBy { it.left }
}
