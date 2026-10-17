package com.fukuiteams.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.BleagueDistrict
import com.fukuiteams.app.data.BleagueRow
import com.fukuiteams.app.data.BleagueStandings
import com.fukuiteams.app.data.LeagueStandings
import com.fukuiteams.app.data.StandingRow
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.Paper

/*
 * 3チームのリーグの順位表(試合タブの「順位」で使う)。
 * - ブローウィンズ: Bリーグ公式の地区別の順位表(BleagueStandingsSection)
 * - 丸岡RUCK・ユナイテッド: 女子Fリーグ公式の結果から計算した順位表/ユナイテッド公式の順位表(LeagueStandingsSection)
 */

/** 試合タブの「順位」の要点(例「中地区 2位・4勝4敗・首位とゲーム差2」)。順位表がまだ無ければ、その旨 */
fun teamStandingsSummary(team: Team, standings: LeagueStandings?): String {
    val bleague = standings?.bleague?.takeIf { team == Team.BLOWINDS }
    return when {
        standings == null || (team == Team.BLOWINDS && bleague == null) -> "まだ届いていません(自動更新で表示)"
        bleague != null -> bleagueSummary(bleague)
        else -> leagueSummary(standings, team)
    }
}

/** 試合タブの「順位」を押したときに開く順位表。順位表がまだ無ければ何も出さない */
@Composable
fun TeamStandingsContent(team: Team, standings: LeagueStandings?, nextOpponent: String?, onOpenUrl: (String) -> Unit) {
    if (standings == null) return
    val bleague = standings.bleague
    if (team == Team.BLOWINDS) {
        if (bleague != null) BleagueStandingsSection(standings, bleague, nextOpponent, onOpenUrl)
    } else {
        LeagueStandingsSection(standings, team, nextOpponent)
    }
}

/** 「中地区 2位・4勝4敗・首位とゲーム差2」 */
private fun bleagueSummary(b: BleagueStandings): String {
    val own = b.own ?: return "順位表を読み取れませんでした"
    val me = b.me ?: return own.label
    val gap = if (me.rank == 1) "首位" else "首位とゲーム差${me.gamesBehind.ifBlank { "0" }}"
    return "${own.label} ${me.rank}位・${me.win}勝${me.lose}敗・$gap" +
        (me.streakLabel.takeIf { it.isNotBlank() }?.let { "・$it" } ?: "")
}

/** 「レギュラーシーズン 6位・勝点13」(ファイナルシーズンに試合があればそちら) */
private fun leagueSummary(st: LeagueStandings, team: Team): String {
    val word = if (team == Team.RAC) "丸岡" else "福井ユナイテッド"
    val part = st.final?.takeIf { p -> p.rows.any { (it.played ?: 0) > 0 } } ?: st.regular
        ?: return "順位表を読み取れませんでした"
    val me = part.rows.firstOrNull { it.team.contains(word) } ?: return part.label
    return "${part.label} ${me.rank}位・勝点${me.points}" +
        (if (me.win != null && me.lose != null) "・${me.win}勝${me.lose}敗" + (if ((me.draw ?: 0) > 0) "${me.draw}分" else "") else "")
}

/** 「10月17日(土) 21:15 時点の数字」の1行(選手タブの AsOfLine と同じ書き方) */
@Composable
private fun StandingsAsOfLine(updatedAt: java.time.Instant?, extra: String?) {
    val time = updatedAt?.atZone(java.time.ZoneId.of("Asia/Tokyo"))
    val base = if (time != null) {
        val week = "月火水木金土日"[time.dayOfWeek.value - 1]
        "%d月%d日(%s) %02d:%02d 時点の数字".format(time.monthValue, time.dayOfMonth, week, time.hour, time.minute)
    } else {
        "取得日時不明の数字"
    }
    Text(
        if (extra.isNullOrBlank()) base else "$base($extra)",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = Ink
    )
}

/** 「Bリーグ公式の順位表を見る ›」などのリンクの文字 */
@Composable
private fun StandingsLinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Accent,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    )
}

/**
 * 丸岡RUCK・ユナイテッドのリーグ順位表。自チームの行は太字・チームカラー、次の対戦相手の行には「次」を付ける。
 * 丸岡RUCKは試合数・勝敗・得失点差・直近5試合まで、ユナイテッドは順位と勝点だけ(公式サイトにある分だけ)。
 */
@Composable
fun LeagueStandingsSection(standings: LeagueStandings, ownTeam: Team, nextOpponent: String?) {
    val ownWord = when (ownTeam) {
        Team.RAC -> "丸岡"
        Team.UNITED -> "福井ユナイテッド"
        Team.BLOWINDS -> "ブローウィンズ"
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StandingsAsOfLine(standings.updatedAt, null)
        Text(
            listOf(standings.note, "出典:${standings.sourceUrl}").filter { it.isNotBlank() }.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        // ファイナルシーズンに試合があればそちらを先に出す
        listOfNotNull(
            standings.final?.takeIf { p -> p.rows.any { (it.played ?: 0) > 0 } },
            standings.regular,
            standings.final?.takeIf { p -> p.rows.none { (it.played ?: 0) > 0 } }
        ).forEach { part ->
            Text(part.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Ink)
            LeagueStandingsTable(part.rows, ownWord, ownTeam.color, nextOpponent)
        }
    }
}

/**
 * ブローウィンズの地区の順位表(Bリーグ公式の順位表から)。
 * 上に福井のいまの成績(順位・勝率・首位とのゲーム差・連勝/連敗・ホーム/アウェー・得失点など)、
 * その下に地区の表(行を押すと、そのクラブの得点・失点・ホーム/アウェーなどを開く)。
 * ほかの地区と、リーグ全体を勝率で並べた参考の表は、ボタンで開く。
 */
@Composable
fun BleagueStandingsSection(
    standings: LeagueStandings,
    bleague: BleagueStandings,
    nextOpponent: String?,
    onOpen: (String) -> Unit
) {
    val own = bleague.own ?: return
    val me = bleague.me
    var showOthers by remember(bleague) { mutableStateOf(false) }
    var showOverall by remember(bleague) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StandingsAsOfLine(standings.updatedAt, null)
        Text(
            listOf(standings.note, "出典:${standings.sourceUrl}").filter { it.isNotBlank() }.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        if (me != null) {
            BleagueMyCard(me, own, bleague)
        }
        Text(own.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Ink)
        BleagueTable(own.rows, bleague.teamId, nextOpponent, showDistrict = false)
        Text(
            "差=1位とのゲーム差、直近5=過去5試合の勝敗、連続=今の連勝・連敗。行を押すと、得点・失点・ホーム/アウェーの成績などを表示します。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        val others = bleague.districts.filter { it.label != own.label }
        if (others.isNotEmpty()) {
            BleagueToggle(if (showOthers) "▲ ほかの地区を閉じる" else "▼ ほかの地区(${others.joinToString("・") { it.label }})を見る") {
                showOthers = !showOthers
            }
            if (showOthers) {
                others.forEach { d ->
                    Text(d.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Ink)
                    BleagueTable(d.rows, bleague.teamId, nextOpponent, showDistrict = false)
                }
            }
        }
        bleague.overall?.let { ov ->
            BleagueToggle(if (showOverall) "▲ ${ov.label}を閉じる" else "▼ ${ov.label}を見る") { showOverall = !showOverall }
            if (showOverall) {
                if (bleague.overallNote.isNotBlank()) {
                    Text(bleague.overallNote, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
                BleagueTable(ov.rows, bleague.teamId, nextOpponent, showDistrict = true)
            }
        }
        StandingsLinkText("Bリーグ公式の順位表を見る ›") { onOpen(standings.sourceUrl) }
    }
}

/** 福井のいまの成績(地区の順位・勝敗・首位とのゲーム差など)の囲み */
@Composable
private fun BleagueMyCard(me: BleagueRow, own: BleagueDistrict, bleague: BleagueStandings) {
    val top = own.rows.minByOrNull { it.rank }
    val overallRank = bleague.overall?.rows?.firstOrNull { it.teamId == me.teamId }?.rank
    val overallCount = bleague.overall?.rows?.size ?: 0
    val color = Team.BLOWINDS.color
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(color.copy(alpha = 0.06f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "${own.label} ${me.rank}位" + if (me.clinch.isNotBlank()) "(${me.clinch})" else "",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            "${me.win}勝${me.lose}敗(勝率${me.pct})" + when {
                me.rank == 1 -> {
                    val second = own.rows.filter { it.rank > 1 }.minByOrNull { it.rank }
                    second?.let { "・2位の${it.shortName}とゲーム差${it.gamesBehind.ifBlank { "0" }}" } ?: ""
                }
                top != null -> "・首位の${top.shortName}とゲーム差${me.gamesBehind.ifBlank { "0" }}"
                else -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        BleagueDetailLines(me)
        if (overallRank != null && overallCount > 0) {
            Text(
                "${bleague.overall?.label?.substringBefore("(") ?: "リーグ全体"}では勝率で${overallCount}クラブ中${overallRank}番目(参考)",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        }
    }
}

/** 1クラブ分の細かい数字(得点・失点・ホーム/アウェー・直近5試合・連勝/連敗・試合数) */
@Composable
private fun BleagueDetailLines(r: BleagueRow) {
    val games = r.games ?: (r.win + r.lose)
    val avg: (Int?) -> String = { v -> if (v != null && games > 0) "%.1f".format(v.toDouble() / games) else "-" }
    val lines = listOf(
        "得点 ${r.pointsFor ?: "-"}(平均${avg(r.pointsFor)})・失点 ${r.pointsAgainst ?: "-"}(平均${avg(r.pointsAgainst)})・得失点差 " +
            (r.pointDiff?.let { if (it > 0) "+$it" else "$it" } ?: "-"),
        "ホーム ${winLose(r.home)}・アウェー ${winLose(r.away)}",
        "過去5試合 ${winLose(r.last5)}" +
            (r.streakLabel.takeIf { it.isNotBlank() }?.let { "・いま$it" } ?: ""),
        "${games}試合消化" + (r.progress.takeIf { it.isNotBlank() }?.let { "(全体の$it)" } ?: "")
    )
    lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink) }
}

/** 「3-0」→「3勝0敗」。読めなければ「-」 */
private fun winLose(text: String): String {
    val m = Regex("""(\d+)\s*-\s*(\d+)""").find(text) ?: return "-"
    return "${m.groupValues[1]}勝${m.groupValues[2]}敗"
}

/** 地区(またはリーグ全体)の順位表。自チームの行は太字・チームカラー、次の対戦相手には「◀次」。行を押すと細かい数字を開く */
@Composable
private fun BleagueTable(rows: List<BleagueRow>, ownTeamId: String, nextOpponent: String?, showDistrict: Boolean) {
    var opened by remember(rows) { mutableStateOf(setOf<String>()) }
    val headers = if (showDistrict) listOf("順", "クラブ", "地区", "勝", "敗", "勝率")
    else listOf("順", "クラブ", "勝", "敗", "勝率", "差", "直近5", "連続")
    val weights = if (showDistrict) listOf(0.7f, 2.6f, 1.2f, 0.8f, 0.8f, 1.2f)
    else listOf(0.6f, 2.2f, 0.7f, 0.7f, 1.1f, 0.8f, 1.0f, 1.1f)
    val ownColor = Team.BLOWINDS.color
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Ink).background(Paper)) {
        Row(modifier = Modifier.fillMaxWidth().background(Ink).padding(vertical = 5.dp, horizontal = 6.dp)) {
            headers.forEachIndexed { i, h ->
                Text(h, modifier = Modifier.weight(weights[i]), color = Paper, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    textAlign = if (i == 1) TextAlign.Start else TextAlign.Center, maxLines = 1)
            }
        }
        rows.forEach { r ->
            val own = r.teamId.isNotBlank() && r.teamId == ownTeamId
            val next = !own && !nextOpponent.isNullOrBlank() &&
                (r.team.contains(nextOpponent) || nextOpponent.contains(r.shortName) || nextOpponent.contains(r.team))
            val key = r.teamId.ifBlank { r.team }
            val name = r.shortName + if (next) " ◀次" else ""
            val cells = if (showDistrict) {
                listOf("${r.rank}", name, r.district.removeSuffix("地区"), "${r.win}", "${r.lose}", r.pct)
            } else {
                listOf("${r.rank}", name, "${r.win}", "${r.lose}", r.pct, r.gamesBehind.ifBlank { "-" },
                    r.last5.ifBlank { "-" }, r.streakLabel.ifBlank { "-" })
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (own) ownColor.copy(alpha = 0.08f) else Paper)
                    .clickable { opened = if (key in opened) opened - key else opened + key }
                    .padding(vertical = 6.dp, horizontal = 6.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    cells.forEachIndexed { i, c ->
                        Text(
                            c,
                            modifier = Modifier.weight(weights[i]),
                            fontSize = 12.sp,
                            fontWeight = if (own) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (own && i == 1) ownColor else Ink,
                            textAlign = if (i == 1) TextAlign.Start else TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (key in opened) {
                    Column(modifier = Modifier.padding(top = 4.dp, start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(r.team, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Ink)
                        BleagueDetailLines(r)
                    }
                }
            }
            ThinRule(color = LineGray)
        }
    }
}

/** 「▼ ほかの地区を見る」などの開け閉めの文字 */
@Composable
private fun BleagueToggle(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Accent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun LeagueStandingsTable(rows: List<StandingRow>, ownWord: String, ownColor: androidx.compose.ui.graphics.Color, nextOpponent: String?) {
    val detailed = rows.any { it.played != null }
    val headers = if (detailed) listOf("順", "チーム", "勝点", "試合", "勝", "分", "敗", "差") else listOf("順位", "チーム", "勝点")
    val weights = if (detailed) listOf(0.7f, 3.4f, 1f, 1f, 0.8f, 0.8f, 0.8f, 1f) else listOf(1f, 4f, 1.2f)
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Ink).background(Paper)) {
        Row(modifier = Modifier.fillMaxWidth().background(Ink).padding(vertical = 5.dp, horizontal = 6.dp)) {
            headers.forEachIndexed { i, h ->
                Text(h, modifier = Modifier.weight(weights[i]), color = Paper, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    textAlign = if (i == 1) TextAlign.Start else TextAlign.Center, maxLines = 1)
            }
        }
        rows.forEach { r ->
            val own = r.team.contains(ownWord)
            val next = !own && nextOpponent != null && (r.team.contains(nextOpponent) || nextOpponent.contains(r.team))
            val cells = if (detailed) {
                listOf("${r.rank}", r.team + if (next) " ◀次" else "", "${r.points}", "${r.played ?: "-"}", "${r.win ?: "-"}",
                    "${r.draw ?: "-"}", "${r.lose ?: "-"}", r.goalDiff?.let { if (it > 0) "+$it" else "$it" } ?: "-")
            } else {
                listOf("${r.rank}", r.team + if (next) " ◀次" else "", "${r.points}")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (own) ownColor.copy(alpha = 0.08f) else Paper)
                    .padding(vertical = 6.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cells.forEachIndexed { i, c ->
                    Text(
                        c,
                        modifier = Modifier.weight(weights[i]),
                        fontSize = 12.sp,
                        fontWeight = if (own) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (own && i == 1) ownColor else Ink,
                        textAlign = if (i == 1) TextAlign.Start else TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ThinRule(color = LineGray)
        }
    }
}
