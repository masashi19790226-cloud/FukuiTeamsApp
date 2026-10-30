package com.fukuiteams.app.data

import com.fukuiteams.app.model.Team

/*
 * 順位の見通し(Web版の outlook.js と同じ内容)。
 * チームタブの「今季」に、上の順位・目標の順位までの差と、残りの試合を出す。
 * 目標の順位(プレーオフなど)は、公式の要項で確かめたものだけを使う:
 *  ・B.ONE(ブローウィンズ):各地区の1位・2位と、それ以外の15クラブの上位6クラブ(ワイルドカード)がプレーオフ
 *    (地区をまたいだ順位は勝率で決まる。同じ勝率のときの直接対戦の比べ方までは入れていないので目安)
 *  ・リーグH(ブルーサンダー):レギュラーシーズンの上位6チームがプレーオフ
 */

/** 見通しの1行(例 プレーオフ(地区2位以内)まで / 2ゲーム差 / 2位は新潟(6勝5敗)) */
data class OutlookRow(val label: String, val value: String, val note: String)

data class StandingOutlook(val title: String, val rows: List<OutlookRow>, val notes: List<String>)

private fun outlookTeamWord(team: Team): String? = when (team) {
    Team.RAC -> "丸岡"
    Team.THUNDER -> "ブルーサンダー"
    Team.UNITED -> "ユナイテッド"
    else -> null
}

/** ゲーム差(半分の単位の整数 h から「2.5」など) */
private fun gbLabel(h: Int): String = when {
    h <= 0 -> "0"
    h % 2 == 0 -> "${h / 2}"
    else -> "${(h - 1) / 2}.5"
}

/** 勝率「.878」(1なら「1.000」) */
private fun pctLabel(r: Double): String {
    val n = Math.round(r * 1000).toInt()
    return if (n >= 1000) "1.000" else "." + n.toString().padStart(3, '0')
}

private fun winPct(r: BleagueRow): Double = if (r.win + r.lose > 0) r.win.toDouble() / (r.win + r.lose) else 0.0
private fun halfGames(above: BleagueRow, me: BleagueRow): Int = (above.win - me.win) + (me.lose - above.lose)
private val BleagueRow.nameShort: String get() = short.ifEmpty { team }

/** 順位の見通し。upcoming は日程に出ている、このチームのこれからの試合の数(呼ぶ側で数える) */
fun standingOutlook(team: Team, league: LeagueStandings?, upcoming: Int): StandingOutlook? {
    if (league == null) return null
    return if (team == Team.BLOWINDS) bleagueOutlook(league.bleague, upcoming) else pointsOutlook(team, league, upcoming)
}

private fun bleagueOutlook(b: BleagueStandings?, upcoming: Int): StandingOutlook? {
    if (b == null) return null
    val own = b.districts.firstOrNull { it.label == b.ownDistrict } ?: return null
    val me = own.rows.firstOrNull { it.teamId == b.teamId } ?: return null
    val rows = mutableListOf<OutlookRow>()
    val sorted = own.rows.sortedBy { it.rank }
    val first = sorted[0]
    val second = sorted.getOrNull(1)
    if (me.rank == 1) {
        rows += OutlookRow("地区1位まで", "首位", second?.let { "2位の${it.nameShort}と${gbLabel(halfGames(me, it))}ゲーム差" } ?: "")
    } else {
        rows += OutlookRow("地区1位まで", "${gbLabel(halfGames(first, me))}ゲーム差", "1位は${first.nameShort}(${first.win}勝${first.lose}敗)")
    }
    if (second != null) {
        if (me.rank <= 2) {
            val third = sorted.getOrNull(2)
            rows += OutlookRow("プレーオフ(地区2位以内)", "圏内", third?.let { "3位の${it.nameShort}と${gbLabel(halfGames(me, it))}ゲーム差" } ?: "")
        } else {
            rows += OutlookRow("プレーオフ(地区2位以内)まで", "${gbLabel(halfGames(second, me))}ゲーム差", "2位は${second.nameShort}(${second.win}勝${second.lose}敗)")
        }
    }
    // ワイルドカード(各地区の上位2クラブを除いたクラブを勝率で並べた目安。6番目まで)
    if (me.rank > 2) {
        val cands = b.districts.flatMap { d -> d.rows.sortedBy { it.rank }.drop(2) }
            .sortedWith(compareByDescending<BleagueRow> { winPct(it) }.thenByDescending { it.pointDiff ?: -99999 }.thenBy { it.team })
        val pos = cands.indexOfFirst { it.teamId == me.teamId } + 1
        if (pos > 0 && cands.size >= 6) {
            if (pos <= 6) {
                val next = cands.getOrNull(6)
                rows += OutlookRow("ワイルドカード(目安)", "圏内(${pos}番目)", next?.let { "7番目の${it.nameShort}と${gbLabel(halfGames(me, it))}ゲーム差" } ?: "")
            } else {
                val sixth = cands[5]
                rows += OutlookRow("ワイルドカード(目安)まで", "${gbLabel(halfGames(sixth, me))}ゲーム差", "今は${pos}番目。6番目は${sixth.nameShort}(${sixth.win}勝${sixth.lose}敗)")
            }
        }
    }
    // 残りの試合と、今の地区2位のペースに並ぶのに必要な勝ち数
    val prog = me.progress.replace("%", "").trim().toDoubleOrNull() ?: 0.0
    val total = if (me.games != null && prog > 0) Math.round(me.games * 100 / prog).toInt() else null
    val played = me.win + me.lose
    val remain = if (total != null) total - played else upcoming
    if (remain > 0) rows += OutlookRow("残りの試合", "${remain}試合", if (total != null) "全${total}試合・${me.win}勝${me.lose}敗" else "日程に出ている試合")
    if (total != null && second != null && me.rank > 2 && remain > 0) {
        val pace = Math.round(winPct(second) * total).toInt()
        val need = pace - me.win
        if (need > remain) rows += OutlookRow("地区2位のペースに並ぶには", "全勝でも届かない", "今の2位のペースなら${pace}勝")
        else if (need > 0) rows += OutlookRow("地区2位のペースに並ぶには", "残りで${need}勝", "勝率${pctLabel(need.toDouble() / remain)}。今の2位のペース(勝率${pctLabel(winPct(second))})なら${pace}勝")
    }
    return StandingOutlook(
        "順位の見通し",
        rows,
        listOf("B.ONEのプレーオフは、各地区の1位・2位と、それ以外の15クラブの上位6クラブ(ワイルドカード)の16クラブ。ワイルドカードの順番は勝率で並べた目安です(同じ勝率のときは、公式は直接対戦の成績を先に見ますが、ここでは得失点差で並べています)。")
    )
}

private fun pointsOutlook(team: Team, league: LeagueStandings, upcoming: Int): StandingOutlook? {
    val part = league.final?.takeIf { it.rows.isNotEmpty() } ?: league.regular
    if (part == null || part.rows.isEmpty()) return null
    val word = outlookTeamWord(team)
    val sorted = part.rows.sortedBy { it.rank }
    val me = sorted.firstOrNull { word != null && it.team.contains(word) } ?: return null
    val rows = mutableListOf<OutlookRow>()
    val first = sorted[0]
    val second = sorted.getOrNull(1)
    val unit = "勝点"
    // 上位リーグ(ファイナルシーズン)がまだ始まっていないとき
    if (part === league.final && part.rows.all { (it.played ?: 0) == 0 }) {
        val reg = league.regular?.rows?.firstOrNull { word != null && it.team.contains(word) }
        rows += OutlookRow(part.label, "これから", reg?.let { "レギュラーシーズンは${it.rank}位(${unit}${it.points})" } ?: "")
        if (upcoming > 0) rows += OutlookRow("残りの試合", "${upcoming}試合", "日程に出ている試合(リーグ戦以外も含むことがあります)")
        return StandingOutlook("順位の見通し", rows, listOf("「${part.label}」の試合がまだありません。始まると、1位・ひとつ上の順位までの差が出ます。"))
    }
    if (me === first) {
        rows += OutlookRow("1位まで", "首位", second?.let { "2位の${it.team}と${unit}差${me.points - it.points}" } ?: "")
    } else {
        rows += OutlookRow("1位まで", "${unit}差${first.points - me.points}", "1位は${first.team}(${unit}${first.points})")
        val above = sorted.getOrNull(sorted.indexOf(me) - 1)
        if (above != null && above !== first) rows += OutlookRow("ひとつ上の順位まで", "${unit}差${above.points - me.points}", "${above.rank}位は${above.team}(${unit}${above.points})")
    }
    // プレーオフ(リーグH:上位6チーム)
    if (team == Team.THUNDER && part === league.regular && sorted.size > 6) {
        if (me.rank <= 6) {
            val seventh = sorted[6]
            rows += OutlookRow("プレーオフ(6位以内)", "圏内", "7位の${seventh.team}と${unit}差${me.points - seventh.points}")
        } else {
            val sixth = sorted[5]
            rows += OutlookRow("プレーオフ(6位以内)まで", "${unit}差${sixth.points - me.points}", "6位は${sixth.team}(${unit}${sixth.points})")
        }
    }
    if (upcoming > 0) rows += OutlookRow("残りの試合", "${upcoming}試合", "日程に出ている試合(リーグ戦以外も含むことがあります)")
    val notes = mutableListOf("順位表(${part.label})から数えています。")
    if (team == Team.THUNDER) notes += "リーグHのプレーオフは、レギュラーシーズンの上位6チーム。同じ勝点のときの順位は公式の順位表のとおりです。"
    return StandingOutlook("順位の見通し", rows, notes)
}
