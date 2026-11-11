package com.fukuiteams.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.GameOutcome
import com.fukuiteams.app.data.isUpcoming
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.model.isMainTeamGame
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.Paper
import com.fukuiteams.app.ui.theme.White
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import com.fukuiteams.app.model.inkColor
import com.fukuiteams.app.model.onColor

/** カレンダーで見ている月と、押した日(試合タブを開いている間だけ覚えておく) */
class CalendarState(today: LocalDate) {
    var month by mutableStateOf(YearMonth.from(today))
    var day by mutableStateOf<LocalDate?>(today)
}

private val SunColor = Color(0xFFC0392B)
private val SatColor = Color(0xFF2457A6)
/** 今日のマスの色(薄い黄色) */
private val TodayColor = Color(0xFFFFF3C4)
private val WEEK = listOf("日", "月", "火", "水", "木", "金", "土")

/** 試合の日付(sort_key の先頭8文字。読めなければ null) */
private fun Game.localDate(): LocalDate? {
    val k = sortKey.take(8)
    if (k.length < 8) return null
    return runCatching { LocalDate.of(k.substring(0, 4).toInt(), k.substring(4, 6).toInt(), k.substring(6, 8).toInt()) }.getOrNull()
}

/**
 * 試合タブの「カレンダー」(Web版の calendar.js と同じ内容)。
 * 月ごとのマス目に試合の印(チームの色。塗りはホーム、枠はアウェイ。終わった試合は勝・敗・分)を出し、
 * 日付を押すと、その日の試合を下に並べる(並べ方は呼ぶ側の dayRow)。
 * team が null のときは4チームまとめて出す。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameCalendar(
    games: List<Game>,
    team: Team?,
    state: CalendarState,
    outcomeOf: (Game) -> GameOutcome?,
    dayRow: @Composable (Game) -> Unit
) {
    val today = remember { LocalDate.now(ZoneId.of("Asia/Tokyo")) }
    val shown = remember(games, team) {
        games.filter { team == null || it.team == team }.sortedWith(compareBy({ it.sortKey }, { it.team.ordinal }))
    }
    val byDay = remember(shown) { shown.groupBy { it.localDate() } }
    val month = state.month
    val single = team != null
    val monthMain = shown.filter { it.isMainTeamGame() && it.localDate()?.let { d -> YearMonth.from(d) == month } == true }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "日付を押すと、その日の試合が下に出ます。試合を押すと詳細を開きます。上のチームのボタンで1チームだけにもできます。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        // 月の切り替え
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { moveMonth(state, -1, shown, today) },
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Ink)
            ) { Text("‹ 前の月", color = Ink, fontSize = 12.sp) }
            Text(
                "${month.year}年${month.monthValue}月",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink
            )
            OutlinedButton(
                onClick = { moveMonth(state, 1, shown, today) },
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Ink)
            ) { Text("次の月 ›", color = Ink, fontSize = 12.sp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (monthMain.isEmpty()) "この月の試合はありません" else "この月の試合 ${monthMain.size}(HOME ${monthMain.count { it.isHome }})",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            if (month != YearMonth.from(today)) {
                Text(
                    "今日にもどる",
                    modifier = Modifier.clickable { state.month = YearMonth.from(today); state.day = today }.padding(4.dp),
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // マス目(日曜はじまり)
        val first = month.atDay(1).dayOfWeek.value % 7 // 日=0
        val days = month.lengthOfMonth()
        val cells = first + days
        val rows = (cells + 6) / 7
        Column(modifier = Modifier.fillMaxWidth().border(1.dp, LineGray)) {
            Row(modifier = Modifier.fillMaxWidth().background(Paper)) {
                WEEK.forEachIndexed { i, w ->
                    Text(
                        w,
                        modifier = Modifier.weight(1f).border(0.5.dp, LineGray).padding(vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = when (i) { 0 -> SunColor; 6 -> SatColor; else -> Ink }
                    )
                }
            }
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val idx = r * 7 + c
                        val d = idx - first + 1
                        if (d < 1 || d > days) {
                            Box(modifier = Modifier.weight(1f).heightIn(min = 58.dp).border(0.5.dp, LineGray).background(Paper))
                            continue
                        }
                        val date = month.atDay(d)
                        val list = byDay[date].orEmpty()
                        val main = list.filter { it.isMainTeamGame() }
                        val others = list.size - main.size
                        val isToday = date == today
                        // 押して選んでいる日は枠で囲む(今日は薄い黄色と「今日」の札で分かるので、枠は付けない)
                        val selected = date == state.day && !isToday
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 58.dp)
                                .border(if (selected) 2.dp else 0.5.dp, if (selected) Accent else LineGray)
                                .background(
                                    when {
                                        isToday -> TodayColor
                                        list.isNotEmpty() -> Color(0xFFF7F5F0)
                                        else -> White
                                    }
                                )
                                .clickable { state.day = date }
                                .padding(horizontal = 2.dp, vertical = 3.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    "$d",
                                    modifier = Modifier.padding(horizontal = 1.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (c) {
                                        0 -> SunColor
                                        6 -> SatColor
                                        else -> Ink
                                    },
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (isToday) {
                                    Text(
                                        "今日",
                                        modifier = Modifier.background(Ink, RoundedCornerShape(2.dp)).padding(horizontal = 2.dp),
                                        color = White,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                main.forEach { g -> CalendarPill(g, single, if (g.isUpcoming()) null else outcomeOf(g)) }
                                if (others > 0) Text("+", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = InkSoft)
                            }
                        }
                    }
                }
            }
        }

        // 印の見方
        Text(
            (if (single) "H=ホーム(塗り)・A=アウェイ(枠)" else Team.values().joinToString(" ") { "${it.initial}=${shortTeamName(it)}" } + " ／ 塗り=ホーム・枠=アウェイ") +
                "・勝/敗/分=結果・+=U-20などの試合",
            fontSize = 11.sp,
            color = InkSoft
        )

        // 押した日の試合
        val sel = state.day?.takeIf { YearMonth.from(it) == month }
        if (sel != null) {
            val list = byDay[sel].orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${sel.monthValue}月${sel.dayOfMonth}日(${WEEK[sel.dayOfWeek.value % 7]})の試合" + if (sel == today) "・今日" else "",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                if (list.isEmpty()) {
                    Text("この日は試合がありません", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                } else {
                    list.forEach { dayRow(it) }
                }
            }
        }
    }
}

/** マスの中の小さな印 */
@Composable
private fun CalendarPill(game: Game, single: Boolean, outcome: GameOutcome?) {
    val color = game.team.color
    val mark = when (outcome) {
        GameOutcome.WIN -> "勝"
        GameOutcome.LOSE -> "敗"
        GameOutcome.DRAW -> "分"
        null -> ""
    }
    Text(
        // 中立の会場での試合(ヴェルコスタ)は「N」
        (if (single) (if (game.isHome) "H" else if (game.neutral) "N" else "A") else game.team.initial) + mark,
        modifier = Modifier
            .border(1.5.dp, color, RoundedCornerShape(3.dp))
            .background(if (game.isHome) color else White, RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp),
        // 塗った印の上は白(黄色のヴェルコスタは黒)、枠だけの印はチームの色(黄色は濃い黄土色)
        color = if (game.isHome) game.team.onColor else game.team.inkColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        softWrap = false
    )
}

private fun shortTeamName(t: Team): String = when (t) {
    Team.BLOWINDS -> "ブローウィンズ"
    Team.RAC -> "丸岡RUCK"
    Team.UNITED -> "ユナイテッド"
    Team.THUNDER -> "ブルーサンダー"
    Team.VERCOSTA -> "ヴェルコスタ"
}

/** 前の月・次の月。今日のある月は今日、それ以外はその月の最初の試合の日を選ぶ */
private fun moveMonth(state: CalendarState, delta: Long, games: List<Game>, today: LocalDate) {
    val m = state.month.plusMonths(delta)
    state.month = m
    state.day = if (m == YearMonth.from(today)) today
    else games.filter { it.isMainTeamGame() }.mapNotNull { it.localDate() }.firstOrNull { YearMonth.from(it) == m }
}
