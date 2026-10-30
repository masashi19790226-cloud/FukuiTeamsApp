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
fun singleGameHighs(team: Team, top: Int = 5, value: (HistoryGameLog) -> Int?): List<GameHigh> {
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
fun opponentSplits(log: List<HistoryGameLog>): List<OpponentSplit> {
    val groups = mutableListOf<MutableList<HistoryGameLog>>()
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
            // ふだん(通算の1試合平均)より、この相手との試合のほうが多く得点している選手だけ(「強い」と言えるもの)
            if (merged.points <= 0 || merged.pointsPerGame < overallPerGame(p)) null else p to merged
        }
        .sortedByDescending { it.second.pointsPerGame }
        .take(top)
}

/** 選手の通算の1試合平均の得点(ブローウィンズは細かい成績の平均得点、ほかは得点÷出場) */
fun overallPerGame(p: HistoryPlayer): Double {
    val t = p.stats?.total
    if (t != null && t.games > 0) return t.ppg
    if (p.log.isNotEmpty()) return p.log.sumOf { it.points }.toDouble() / p.log.size
    return if (p.apps > 0) p.goals.toDouble() / p.apps else 0.0
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

// ---------- 記録への挑戦(Web版の history.js の milestoneBoard など) ----------

/** 得点の節目(表の先は同じ間隔で続ける)。ブローウィンズは点、ほかはゴール */
private fun goalStepAfter(team: Team, v: Int): Int {
    val (list, every) = when (team) {
        Team.BLOWINDS -> listOf(500, 1000, 1500, 2000, 2500, 3000) to 500
        Team.THUNDER -> listOf(50, 100, 150, 200, 300) to 100
        else -> listOf(10, 20, 30, 50, 100, 150, 200) to 50
    }
    list.firstOrNull { it > v }?.let { return it }
    val last = list.last()
    return last + (Math.floorDiv(v - last, every) + 1) * every
}

/** 出場試合数の節目 */
private fun appsStepAfter(v: Int): Int {
    val list = listOf(50, 100, 150, 200, 250, 300)
    list.firstOrNull { it > v }?.let { return it }
    return 300 + (Math.floorDiv(v - 300, 100) + 1) * 100
}

/** 節目の得点の単位 */
fun goalUnit(team: Team): String = if (team == Team.BLOWINDS) "点" else "得点"

/** 次の節目(step)まであと left。games は通算の1試合平均で数えた「早ければ何試合後」(平均が0なら null) */
data class NextStep(val step: Int, val left: Int, val games: Int? = null)

/** 歴代の得点の順位で、ひとつ上の選手(抜くまでの数 gap) */
data class AbovePlayer(val name: String, val goals: Int, val gap: Int, val rank: Int)

/** 「記録への挑戦」の1人分 */
data class MilestoneRow(
    val player: HistoryPlayer,
    /** 通算の1試合平均の得点(得点÷出場) */
    val avg: Double,
    val goal: NextStep,
    /** 出場の節目(丸岡RUCKで出場が分からないときは null) */
    val apps: NextStep?,
    /** 歴代の得点の順位(得点が0なら null) */
    val rank: Int?,
    val above: AbovePlayer?,
    val near: Int
)

/**
 * 今の選手の「記録への挑戦」の一覧(次の節目までの近い順)。
 * 1人ずつ:通算の出場・得点、次の得点の節目まであといくつか(通算の1試合平均から、早ければ何試合後か)、
 * 次の出場の節目、歴代の得点の順位と、ひとつ上の選手までの差。
 */
fun milestoneBoard(team: Team): List<MilestoneRow> {
    val data = HistoryRepository.data[team] ?: return emptyList()
    val all = data.players
    val knowApps = team != Team.RAC || data.playersFromLineup
    val byGoals = all.filter { it.goals > 0 }.sortedWith(compareByDescending<HistoryPlayer> { it.goals }.thenBy { it.name })
    val rows = all.filter { it.now == true }.map { p ->
        val avg = if (p.apps > 0) p.goals.toDouble() / p.apps else 0.0
        val gStep = goalStepAfter(team, p.goals)
        val gLeft = gStep - p.goals
        val goal = NextStep(gStep, gLeft, if (avg > 0) kotlin.math.ceil(gLeft / avg).toInt() else null)
        val apps = if (knowApps) appsStepAfter(p.apps).let { NextStep(it, it - p.apps) } else null
        var rank: Int? = null
        var above: AbovePlayer? = null
        if (p.goals > 0) {
            rank = byGoals.indexOfFirst { it.goals == p.goals } + 1
            val higher = byGoals.filter { it.goals > p.goals }
            if (higher.isNotEmpty()) {
                val a = higher.last()
                above = AbovePlayer(a.name, a.goals, a.goals - p.goals + 1, byGoals.indexOfFirst { it.goals == a.goals } + 1)
            }
        }
        val near = minOf(goal.games ?: 9999, apps?.left ?: 9999)
        MilestoneRow(p, avg, goal, apps, rank, above, near)
    }
    return rows.sortedWith(compareBy<MilestoneRow> { it.near }.thenByDescending { it.player.goals }.thenBy { it.player.name })
}

/** 次の試合で節目に届くかもしれない選手1人分(例「通算150得点まであと2」) */
data class ReachableMilestone(val player: HistoryPlayer, val text: String, val left: Int)

/**
 * 次の試合で節目に届くかもしれない選手(「次の試合」の欄に出す)。
 * 得点:残りが通算の1試合平均(四捨五入)以下。出場:あと1試合。歴代最多得点:2位で、1位を抜くのに必要な数が平均以下。
 * 最近出場していない選手は出さない(チームの最後の試合から30日より前が最後の出場)。
 */
fun reachableNextGame(team: Team): List<ReachableMilestone> {
    val data = HistoryRepository.data[team] ?: return emptyList()
    val lastTeam = data.matches.lastOrNull()?.localDate ?: return emptyList()
    val unit = goalUnit(team)
    val out = mutableListOf<ReachableMilestone>()
    for (r in milestoneBoard(team)) {
        val p = r.player
        val pl = runCatching { java.time.LocalDate.parse(p.last) }.getOrNull() ?: continue
        if (java.time.temporal.ChronoUnit.DAYS.between(pl, lastTeam) > 30) continue
        val per = Math.round(r.avg).toInt()
        val ab = r.above
        if (ab != null && ab.rank == 1 && ab.gap <= per) {
            out += ReachableMilestone(p, "歴代最多得点まであと${ab.gap}", ab.gap)
        } else if (per >= 1 && r.goal.left <= per) {
            out += ReachableMilestone(p, "通算${r.goal.step}${unit}まであと${r.goal.left}", r.goal.left)
        }
        if (r.apps != null && r.apps.left == 1) out += ReachableMilestone(p, "通算${r.apps.step}試合出場まであと1", 1)
    }
    return out.take(3)
}

/** 最近の試合での節目達成1つ分 */
data class Achievement(
    val team: Team,
    val player: HistoryPlayer,
    /** 例「通算1000点を達成」 */
    val text: String,
    val date: String,
    val dateLabel: String,
    val opponent: String,
    /** goals・apps・top */
    val kind: String
)

/**
 * 最近の試合で節目を達成した選手(試合の日から2日まで。一面に出す)。
 * 選手の試合ごとの記録の最後の試合で、通算の得点・出場が節目を越えたか、歴代最多得点になったか。
 */
fun recentAchievements(team: Team, today: java.time.LocalDate): List<Achievement> {
    val data = HistoryRepository.data[team] ?: return emptyList()
    val unit = goalUnit(team)
    val knowApps = team != Team.RAC || data.playersFromLineup
    val out = mutableListOf<Achievement>()
    for (p in data.players.filter { it.now == true && it.log.isNotEmpty() }) {
        val last = p.log.sortedBy { it.date }.last()
        val d = last.localDate ?: continue
        val diff = java.time.temporal.ChronoUnit.DAYS.between(d, today)
        if (diff < 0 || diff > 2) continue
        val before = p.goals - last.points
        val step = goalStepAfter(team, before)
        fun add(text: String, kind: String) {
            out += Achievement(team, p, text, last.date, last.dateLabel, last.opponent, kind)
        }
        if (last.points > 0 && step <= p.goals) add("通算${step}${unit}を達成", "goals")
        if (knowApps && p.apps == appsStepAfter(p.apps - 1)) add("通算${p.apps}試合出場を達成", "apps")
        if (last.points > 0) {
            val best = data.players.filter { it !== p }.maxOfOrNull { it.goals } ?: 0
            if (p.goals > best && before <= best && best > 0) add("歴代最多得点に(通算${p.goals}${unit})", "top")
        }
    }
    return out.sortedWith(compareByDescending<Achievement> { it.date }.thenBy { it.player.name })
}

/** シーズンのペースの1項目(例 勝率 40% / 最高 92%(2023-24 46勝4敗)) */
data class PaceRow(val label: String, val now: String, val best: String, val bestSeason: String, val ahead: Boolean)

/** 今季のリーグ戦のペース */
data class SeasonPace(val season: String, val leagueName: String?, val record: String, val rows: List<PaceRow>)

/**
 * 今季のリーグ戦のペースを、クラブの過去のシーズン(リーグ戦10試合以上)の最高とくらべる。
 * 今季=いちばん新しいシーズンで、最後の試合が今日から60日以内・リーグ戦3試合以上のとき。
 */
fun seasonPace(team: Team, today: java.time.LocalDate): SeasonPace? {
    val matches = HistoryRepository.matchesFor(team)
    if (matches.isEmpty()) return null
    val sums = seasonSummaries(team, matches).filter { (it.league?.games ?: 0) > 0 }
    if (sums.size < 2) return null
    val cur = sums[0]
    val curLeague = cur.league ?: return null
    val lastD = matches.filter { it.season == cur.season }.sortedBy { it.date }.lastOrNull()?.localDate ?: return null
    if (java.time.temporal.ChronoUnit.DAYS.between(lastD, today) > 60 || curLeague.games < 3) return null
    val past = sums.drop(1).filter { (it.league?.games ?: 0) >= 10 }
    if (past.isEmpty()) return null
    fun rate(w: WinLoss) = w.win.toDouble() / w.games
    fun sc(w: WinLoss) = w.scored.toDouble() / w.games
    fun co(w: WinLoss) = w.conceded.toDouble() / w.games
    fun pct(x: Double) = "${Math.round(x * 100)}%"
    fun f1(x: Double) = "%.1f".format(x)
    val unit = if (team == Team.BLOWINDS) "点" else ""
    val rows = mutableListOf<PaceRow>()
    val bestRate = past.maxWithOrNull(compareBy<SeasonSummary>({ rate(it.league!!) }, { it.season }))!!
    rows += PaceRow("勝率", pct(rate(curLeague)), pct(rate(bestRate.league!!)), "${bestRate.season} ${bestRate.league.label}", rate(curLeague) > rate(bestRate.league))
    val bestSc = past.maxWithOrNull(compareBy<SeasonSummary>({ sc(it.league!!) }, { it.season }))!!
    rows += PaceRow("1試合平均の得点", f1(sc(curLeague)) + unit, f1(sc(bestSc.league!!)) + unit, bestSc.season, sc(curLeague) > sc(bestSc.league))
    val bestCo = past.maxWithOrNull(compareBy<SeasonSummary>({ -co(it.league!!) }, { it.season }))!!
    rows += PaceRow("1試合平均の失点", f1(co(curLeague)) + unit, f1(co(bestCo.league!!)) + unit, bestCo.season, co(curLeague) < co(bestCo.league))
    return SeasonPace(cur.season, cur.leagueName, curLeague.label, rows)
}
