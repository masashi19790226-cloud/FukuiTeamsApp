package com.fukuiteams.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.GameOutcome
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.Ivory
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper
import com.fukuiteams.app.ui.theme.White
import java.time.LocalDate

private val WEEKDAYS = listOf("月", "火", "水", "木", "金", "土", "日")

/** 今日の日付を「2026年9月28日(月)」の形式で。 */
fun todayLabel(): String {
    val d = LocalDate.now()
    return "${d.year}年${d.monthValue}月${d.dayOfMonth}日(${WEEKDAYS[d.dayOfWeek.value - 1]})"
}

/**
 * 各画面の一番上に置く、新聞の題字風ヘッダー。
 * section は「一面」「試合面」など、edition は「ブローウィンズ版」など(省略可)。
 */
@Composable
fun MastheadTopBar(
    section: String,
    edition: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ivory)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.align(Alignment.CenterStart)) { navigationIcon() }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "ふくスポ",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    letterSpacing = 2.sp,
                    color = Ink
                )
                Text(
                    "FUKUI MATCHDAY TIMES",
                    fontSize = 8.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }
            Row(modifier = Modifier.align(Alignment.CenterEnd), content = actions)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Ink))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(todayLabel(), fontSize = 10.sp, color = Ink)
            Text(listOfNotNull(section, edition).joinToString("・"), fontSize = 10.sp, color = Ink)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Ink))
    }
}

/** 黒地(または赤地)に白抜きの小見出し。「試合結果」「ニュース」など。 */
@Composable
fun SectionLabel(text: String, red: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(if (red) NewsRed else Ink)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = Ivory,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 2.sp
    )
}

/** 新聞の見出し(明朝・極太)。 */
@Composable
fun Headline(text: String, fontSize: Int = 24, modifier: Modifier = Modifier, color: Color = Ink) {
    Text(
        text,
        modifier = modifier,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Black,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.2).sp,
        color = color
    )
}

/** 二重罫線。紙面の区切りに使う。 */
@Composable
fun DoubleRule(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Ink))
        Spacer(modifier = Modifier.height(2.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Ink))
    }
}

/** 細い罫線。 */
@Composable
fun ThinRule(modifier: Modifier = Modifier, color: Color = Ink) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(color))
}

/**
 * チーム切り替えボタンの列。各ボタンの上端にチームカラーの帯、選択中はチームカラーで塗りつぶし。
 * showAll = true のときは先頭に「すべて」(黒)を付ける。選択中のチームの色の細い帯を下に引く。
 */
@Composable
fun TeamSelectorRow(
    selectedTeam: Team?,
    onSelect: (Team?) -> Unit,
    showAll: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 横スクロールせずに全ボタンが1画面に収まるよう、幅を分け合って横一列に並べる
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showAll) {
                TeamSelectorChip("すべて", Ink, selectedTeam == null, Modifier.weight(0.75f)) { onSelect(null) }
            }
            Team.values().forEach { team ->
                TeamSelectorChip(team.shortLabel(), team.color, selectedTeam == team, Modifier.weight(team.chipWeight())) {
                    onSelect(team)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(selectedTeam?.color ?: Ink)
        )
    }
}

/** ボタン用の短い表示名。 */
fun Team.shortLabel(): String = when (this) {
    Team.BLOWINDS -> "ブローウィンズ"
    Team.RAC -> "丸岡RUCK"
    Team.UNITED -> "ユナイテッド"
}

// 名前の長さに合わせたボタン幅の配分
private fun Team.chipWeight(): Float = when (this) {
    Team.BLOWINDS -> 1.35f
    Team.RAC -> 1f
    Team.UNITED -> 1.15f
}

@Composable
private fun TeamSelectorChip(
    label: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .border(1.dp, if (selected) color else Ink)
            .background(if (selected) color else Paper)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(color))
        Text(
            label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 7.dp),
            color = if (selected) White else Ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * 試合結果から新聞の見出しを作る。例:「岐阜に競り勝つ」「新潟に惜敗」。
 * バスケ(ブローウィンズ)は点差が大きいので、競技ごとに基準を変える。
 */
fun resultHeadline(game: Game, myScore: Int?, opponentScore: Int?, outcome: GameOutcome?): String {
    val opp = game.opponent
    if (outcome == null) return "${opp}戦"
    if (myScore == null || opponentScore == null) {
        return when (outcome) {
            GameOutcome.WIN -> "${opp}に勝利"
            GameOutcome.LOSE -> "${opp}に敗れる"
            GameOutcome.DRAW -> "${opp}と引き分け"
        }
    }
    val diff = kotlin.math.abs(myScore - opponentScore)
    val basketball = game.team == Team.BLOWINDS
    val close = if (basketball) diff <= 3 else diff <= 1
    val big = if (basketball) diff >= 15 else diff >= 3
    return when (outcome) {
        GameOutcome.WIN -> when {
            close -> "${opp}に競り勝つ"
            big -> "${opp}に完勝"
            else -> "${opp}を下す"
        }
        GameOutcome.LOSE -> when {
            close -> "${opp}に惜敗"
            big -> "${opp}に完敗"
            else -> "${opp}に敗れる"
        }
        GameOutcome.DRAW -> if (myScore == 0) "${opp}とスコアレスドロー" else "${opp}と痛み分け"
    }
}

/** 勝敗を「○90-88」「●80-82」「△1-1」の形に。 */
fun resultMark(outcome: GameOutcome?): String = when (outcome) {
    GameOutcome.WIN -> "○"
    GameOutcome.LOSE -> "●"
    GameOutcome.DRAW -> "△"
    null -> ""
}


/** HOME/AWAY の表示。HOMEは赤地に白抜き、AWAYは紺の枠と文字で、ひと目で区別できるようにする。 */
@androidx.compose.runtime.Composable
fun HomeAwayTag(isHome: Boolean, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val away = androidx.compose.ui.graphics.Color(0xFF2B4C7E)
    androidx.compose.material3.Text(
        if (isHome) "HOME" else "AWAY",
        modifier = modifier
            .then(androidx.compose.ui.Modifier.border(1.dp, if (isHome) com.fukuiteams.app.ui.theme.NewsRed else away))
            .background(if (isHome) com.fukuiteams.app.ui.theme.NewsRed else com.fukuiteams.app.ui.theme.Paper)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = if (isHome) com.fukuiteams.app.ui.theme.White else away,
        fontSize = 10.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        softWrap = false
    )
}
