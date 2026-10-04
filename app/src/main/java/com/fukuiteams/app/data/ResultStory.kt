package com.fukuiteams.app.data

import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import kotlin.math.absoluteValue

/**
 * 「速報」の見出し・記事に使う、その試合の前後関係(連勝・連敗、今季の成績、前回の対戦)。
 * すべて取得済みの試合結果から数える。途中に結果の分からない試合があるときは、その項目は使わない(数字を作らない)。
 */
data class ResultContext(
    /** この試合を含めて同じ結果が何試合続いているか(2以上のときだけ使う)。分からなければ null */
    val streak: Int?,
    /** この試合で止まった、直前までの反対の結果の連続数(例:連敗を3で止めた)。無い・分からなければ null */
    val brokenStreak: Int?,
    /** 今季の成績(この試合まで)。今季の試合の結果がすべてそろっているときだけ */
    val seasonWin: Int?,
    val seasonLose: Int?,
    val seasonDraw: Int?,
    /** 今季、同じ相手との前回の対戦(この試合より前)と、その結果 */
    val prevMeeting: Game?,
    val prevMeetingResult: RemoteGameResult?,
    // ---- 以下は後から追加した項目 ----
    /** 今季の最初の試合(開幕戦)か */
    val seasonOpener: Boolean = false,
    /** 今季最初のホームゲームか(開幕戦がアウェイのとき) */
    val homeOpener: Boolean = false,
    /** 前の試合(2日以内)も同じ相手だったか(土日の連戦など)。そうならその結果 */
    val backToBackResult: RemoteGameResult? = null,
    /** 今季のホーム(またはアウェイ)での成績(この試合まで)。すべての結果がそろっているときだけ */
    val venueWin: Int? = null,
    val venueLose: Int? = null,
    val venueDraw: Int? = null,
    /** 次の試合(まだ終わっていない同じチームの試合) */
    val nextGame: Game? = null
)

/** 今季の始まり(この日より前の試合は今季の成績に入れない) */
private fun Team.seasonStart(game: Game): String {
    val year = game.sortKey.take(4).toIntOrNull() ?: 2026
    val month = game.sortKey.substring(4, 6).toIntOrNull() ?: 1
    return when (this) {
        // バスケは秋に始まり春に終わる(8月より前の試合は、前の年の8月に始まったシーズン)
        Team.BLOWINDS -> "%04d0801".format(if (month >= 8) year else year - 1)
        // 女子フットサルも夏に始まり春に終わる
        Team.RAC -> "%04d0501".format(if (month >= 5) year else year - 1)
        // 北信越リーグは春から秋
        Team.UNITED -> "%04d0301".format(year)
    }
}

fun RemoteGameResult.outcome(): GameOutcome = when {
    myScore > opponentScore -> GameOutcome.WIN
    myScore < opponentScore -> GameOutcome.LOSE
    else -> GameOutcome.DRAW
}

fun buildResultContext(game: Game, games: List<Game>, results: Map<String, RemoteGameResult>): ResultContext? {
    val result = results[game.id] ?: return null
    val start = game.team.seasonStart(game)
    // 今季、この試合までの同じチームの試合(古い順)
    val season = games
        .filter { it.team == game.team && it.sortKey.take(8) >= start && it.sortKey <= game.sortKey }
        .sortedBy { it.sortKey }
    // アプリの日程データが今季の最初の試合から全部そろっているか。
    // (ユナイテッドのように、途中から日程を集め始めたチームは、今季の成績や開幕戦などを数えられない)
    // 今季の始まりの目安から60日以内に最初の試合があれば、そろっているとみなす
    val covered = season.firstOrNull()?.let { first ->
        daysBetween(start + "-0000", first.sortKey)?.let { it <= 60 }
    } == true
    val known = covered && season.all { results.containsKey(it.id) }

    // 連続:この試合からさかのぼって同じ結果が何試合続いたか。途中に結果不明の試合があれば分からない扱い
    val me = result.outcome()
    var streak = 0
    var streakKnown = true
    var idx = season.lastIndex
    while (idx >= 0) {
        val r = results[season[idx].id]
        if (r == null) { streakKnown = false; break }
        if (r.outcome() != me) break
        streak++
        idx--
    }
    // 日程データの最初の試合までさかのぼっても続いていて、それより前の試合が分からないときは、何連勝か分からない
    if (idx < 0 && !covered) streakKnown = false
    // この試合の直前までの、反対の結果の連続(この試合で止まった連勝・連敗)
    var broken = 0
    var brokenKnown = streakKnown
    if (streakKnown && idx >= 0) {
        val first = results[season[idx].id]?.outcome()
        if (first != null && first != me && first != GameOutcome.DRAW && me != GameOutcome.DRAW) {
            while (idx >= 0) {
                val r = results[season[idx].id]
                if (r == null) { brokenKnown = false; break }
                if (r.outcome() != first) break
                broken++
                idx--
            }
            if (idx < 0 && !covered) brokenKnown = false
        }
    }

    val prev = season.dropLast(1).lastOrNull { it.opponent == game.opponent && results.containsKey(it.id) }
    val outcomes = season.mapNotNull { results[it.id]?.outcome() }
    // 前の試合が2日以内の同じ相手なら連戦(土日の2連戦など)
    val before = season.dropLast(1).lastOrNull()
    val backToBack = before?.takeIf { b ->
        b.opponent == game.opponent && daysBetween(b.sortKey, game.sortKey)?.let { it in 0..2 } == true
    }?.let { results[it.id] }
    val sameVenue = season.filter { it.isHome == game.isHome }
    val venueKnown = covered && sameVenue.all { results.containsKey(it.id) }
    val venueOutcomes = sameVenue.mapNotNull { results[it.id]?.outcome() }
    // 次の試合(これから行われる試合だけ。結果が取れていない過去の試合は除く)
    val nowKey = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Tokyo"))
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    val next = games
        .filter { it.team == game.team && it.sortKey > game.sortKey && it.sortKey.take(8) >= nowKey.take(8) && !results.containsKey(it.id) }
        .minByOrNull { it.sortKey }
    return ResultContext(
        seasonOpener = covered && season.size == 1,
        homeOpener = covered && game.isHome && season.size > 1 && season.count { it.isHome } == 1,
        backToBackResult = backToBack,
        venueWin = if (venueKnown) venueOutcomes.count { it == GameOutcome.WIN } else null,
        venueLose = if (venueKnown) venueOutcomes.count { it == GameOutcome.LOSE } else null,
        venueDraw = if (venueKnown) venueOutcomes.count { it == GameOutcome.DRAW } else null,
        nextGame = next,
        streak = streak.takeIf { streakKnown && it >= 2 },
        brokenStreak = broken.takeIf { brokenKnown && it >= 2 },
        seasonWin = if (known) outcomes.count { it == GameOutcome.WIN } else null,
        seasonLose = if (known) outcomes.count { it == GameOutcome.LOSE } else null,
        seasonDraw = if (known) outcomes.count { it == GameOutcome.DRAW } else null,
        prevMeeting = prev,
        prevMeetingResult = prev?.let { results[it.id] }
    )
}

/** 同じ試合ではいつも同じ言い回しになるよう、試合IDから選ぶ(開くたびに変わらないように) */
fun <T> Game.pick(options: List<T>, salt: Int = 0): T =
    options[((id.hashCode() + salt * 31).absoluteValue) % options.size]

/** 並び順のキー(YYYYMMDD-HHMM)どうしの日数の差。読めなければ null */
private fun daysBetween(a: String, b: String): Long? = try {
    val f = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
    java.time.temporal.ChronoUnit.DAYS.between(
        java.time.LocalDate.parse(a.take(8), f),
        java.time.LocalDate.parse(b.take(8), f)
    )
} catch (e: Exception) {
    null
}
