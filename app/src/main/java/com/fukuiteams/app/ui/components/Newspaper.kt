package com.fukuiteams.app.ui.components

import com.fukuiteams.app.model.shortName
import androidx.compose.foundation.background
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.fukuiteams.app.data.DataStatusRepository
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
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
import com.fukuiteams.app.data.pick
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

/**
 * 各画面の一番上に置く細い帯。現在の日時と、データの最終更新日時(GitHubの自動更新が最後に動いた時刻)を出す。
 * 左に戻るボタン、右に更新ボタンなどを置ける。section・edition は今は表示しない(呼び出し元との互換のため残している)。
 */
@Composable
fun MastheadTopBar(
    section: String,
    edition: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    // 帯には「現在の日時」と「データの最終更新日時」だけを出す(画面名・通知ボタンは出さない)。
    // section・edition は、これまでの呼び出し元をそのまま使えるよう引数として残してある
    val now by produceState(initialValue = java.time.ZonedDateTime.now(JST)) {
        // 分が変わるたびに表示を更新する
        while (true) {
            value = java.time.ZonedDateTime.now(JST)
            kotlinx.coroutines.delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    LaunchedEffect(Unit) { DataStatusRepository.refreshIfStale() }
    val status = DataStatusRepository.latest
    val updated = status?.updatedAt?.atZone(JST)
    // 3時間より前なら、自動更新が止まっているかもしれないので赤字にする
    val stale = updated != null && java.time.Duration.between(updated, now).toMinutes() > 180
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ivory)
            .padding(horizontal = 8.dp)
    ) {
        // 左右のボタンの残りの幅に、現在の日時と最終更新日時を同じ形(9/30(水) 22:45)で1行に並べる。
        // 入りきらない画面(左右にボタンがある画面など)では、1行に収まるまで文字を小さくする
        var fontSizeSp by remember { mutableStateOf(13f) }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationIcon()
            Text(
                buildAnnotatedString {
                    append(shortDateTime(now))
                    append("  |  最終更新 ")
                    if (updated != null) {
                        if (stale) {
                            withStyle(SpanStyle(color = NewsRed, fontWeight = FontWeight.Bold)) { append(shortDateTime(updated)) }
                        } else {
                            append(shortDateTime(updated))
                        }
                    } else {
                        append("確認中")
                    }
                },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 4.dp),
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { result ->
                    if (result.hasVisualOverflow && fontSizeSp > 9f) fontSizeSp -= 0.5f
                }
            )
            Row(content = actions)
        }
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Ink))
    }
}

private val JST: java.time.ZoneId = java.time.ZoneId.of("Asia/Tokyo")

/** 帯に出す日時の形「9/30(水) 22:45」。現在の日時と最終更新日時で同じ形にそろえる。 */
private fun shortDateTime(t: java.time.ZonedDateTime): String =
    "%d/%d(%s) %02d:%02d".format(t.monthValue, t.dayOfMonth, WEEKDAYS[t.dayOfWeek.value - 1], t.hour, t.minute)

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
fun Team.shortLabel(): String = shortName

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
 * 試合結果から新聞の見出しを作る。例:「岐阜に競り勝つ」「新潟に惜敗」「金沢下し3連勝」「浦安に雪辱」。
 * バスケ(ブローウィンズ)は点差が大きいので、競技ごとに基準を変える。
 * ctx(連勝・連敗・前回対戦)があれば、それを優先して見出しにする。
 * 同じ意味の言い回しを数通り用意し、試合ごとに決まったものを使う(同じ試合は開くたびに同じ見出し)。
 */
fun resultHeadline(
    game: Game,
    myScore: Int?,
    opponentScore: Int?,
    outcome: GameOutcome?,
    ctx: com.fukuiteams.app.data.ResultContext? = null
): String {
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
    val huge = if (basketball) diff >= 25 else diff >= 5
    val prev = ctx?.prevMeetingResult?.let {
        when {
            it.myScore > it.opponentScore -> GameOutcome.WIN
            it.myScore < it.opponentScore -> GameOutcome.LOSE
            else -> GameOutcome.DRAW
        }
    }
    // 前日など(2日以内)にも同じ相手と戦っていれば、その結果
    val b2b = ctx?.backToBackResult?.let {
        when {
            it.myScore > it.opponentScore -> GameOutcome.WIN
            it.myScore < it.opponentScore -> GameOutcome.LOSE
            else -> GameOutcome.DRAW
        }
    }
    val streak = ctx?.streak ?: 0
    val broken = ctx?.brokenStreak ?: 0
    val seasonGames = (ctx?.seasonWin ?: 0) + (ctx?.seasonLose ?: 0) + (ctx?.seasonDraw ?: 0)
    // ホーム・アウェイで言い回しを変える(「ホームで」「敵地で」)
    val at = if (game.isHome) "ホームで" else "敵地で"
    val sum = myScore + opponentScore
    return when (outcome) {
        GameOutcome.WIN -> when {
            streak >= 3 -> game.pick(listOf("${opp}下し${streak}連勝", "${streak}連勝 ${opp}を撃破", "${opp}破り${streak}連勝", "止まらない${streak}連勝"))
            broken >= 2 -> game.pick(listOf("連敗${broken}で止める", "${opp}に勝ち連敗脱出", "連敗ストップ ${opp}下す"))
            ctx?.seasonOpener == true -> game.pick(listOf("開幕戦で${opp}下す", "開幕白星 ${opp}に勝利", "白星発進 ${opp}破る"))
            ctx?.homeOpener == true -> game.pick(listOf("ホーム開幕戦で白星", "ホーム初戦 ${opp}下す"))
            ctx?.seasonWin == 1 && seasonGames >= 2 -> game.pick(listOf("待望の今季初勝利", "${opp}下し今季初白星"))
            b2b == GameOutcome.WIN -> game.pick(listOf("${opp}に連勝", "${opp}との連戦に連勝"))
            b2b == GameOutcome.LOSE -> game.pick(listOf("${opp}に前日の雪辱", "${opp}にやり返す"))
            streak == 2 -> game.pick(listOf("${opp}下し連勝", "${opp}破り2連勝"))
            !basketball && opponentScore == 0 -> game.pick(listOf("${opp}に完封勝ち", "${opp}を零封", "無失点で${opp}下す"))
            prev == GameOutcome.LOSE -> game.pick(listOf("${opp}に雪辱", "${opp}にリベンジ", "${opp}に借り返す"))
            basketball && myScore >= 100 -> game.pick(listOf("${myScore}点の猛攻で${opp}下す", "大台${myScore}点 ${opp}に勝利"))
            !basketball && myScore >= 4 -> game.pick(listOf("${myScore}得点で${opp}に大勝", "ゴールラッシュ ${opp}下す"))
            basketball && opponentScore <= 65 -> game.pick(listOf("堅守で${opp}に勝利", "${opp}を${opponentScore}点に封じる"))
            close -> game.pick(listOf("${opp}に競り勝つ", "${opp}との接戦制す", "${opp}との激闘制す", "${at}${opp}に辛勝", "${diff}点差守り${opp}に勝利"))
            huge -> game.pick(listOf("${opp}に圧勝", "${opp}を圧倒", "${at}${opp}に大勝"))
            big -> game.pick(listOf("${opp}に完勝", "${opp}に快勝", "${at}${opp}に快勝", "${opp}寄せ付けず"))
            else -> game.pick(listOf("${opp}を下す", "${opp}から白星", "${opp}に勝利", "${at}${opp}下す", "${opp}破る"))
        }
        GameOutcome.LOSE -> when {
            streak >= 3 -> game.pick(listOf("${opp}に敗れ${streak}連敗", "${streak}連敗 ${opp}に屈す", "連敗${streak}に伸びる"))
            broken >= 3 -> game.pick(listOf("連勝${broken}でストップ", "${opp}に敗れ連勝止まる"))
            ctx?.seasonOpener == true -> game.pick(listOf("開幕戦 ${opp}に黒星", "黒星発進 ${opp}に敗れる"))
            ctx?.homeOpener == true -> game.pick(listOf("ホーム開幕戦飾れず", "ホーム初戦 ${opp}に敗れる"))
            ctx?.seasonLose == 1 && (ctx?.seasonWin ?: 0) >= 2 -> game.pick(listOf("今季初黒星", "${opp}に敗れ今季初黒星"))
            b2b == GameOutcome.LOSE -> game.pick(listOf("${opp}に連敗", "${opp}との連戦に連敗"))
            b2b == GameOutcome.WIN -> game.pick(listOf("${opp}に連勝ならず", "${opp}に雪辱許す"))
            streak == 2 -> game.pick(listOf("${opp}に敗れ連敗", "2連敗 ${opp}に屈す"))
            !basketball && myScore == 0 -> game.pick(listOf("${opp}に零封負け", "${opp}の前に無得点", "ゴール遠く${opp}に敗戦"))
            prev == GameOutcome.WIN -> game.pick(listOf("${opp}に雪辱許す", "${opp}に借り返される"))
            basketball && myScore < 65 -> game.pick(listOf("${myScore}点と攻撃振るわず", "${opp}の守りに苦戦"))
            close -> game.pick(listOf("${opp}に惜敗", "${opp}に競り負け", "${opp}に一歩及ばず", "${diff}点差に泣く", "${at}${opp}に惜敗"))
            huge -> game.pick(listOf("${opp}に大敗", "${opp}に完敗", "${at}${opp}に大差負け"))
            big -> game.pick(listOf("${opp}に完敗", "${opp}に力負け", "${at}${opp}に完敗"))
            else -> game.pick(listOf("${opp}に敗れる", "${opp}に屈す", "${opp}から白星奪えず", "${at}${opp}に黒星", "${opp}に及ばず"))
        }
        GameOutcome.DRAW -> when {
            ctx?.seasonOpener == true -> "開幕戦は${opp}とドロー"
            myScore == 0 -> game.pick(listOf("${opp}とスコアレスドロー", "${opp}と0-0の引き分け"))
            !basketball && myScore >= 3 -> game.pick(listOf("${opp}と打ち合いドロー", "${sum}得点の乱戦 ${opp}とドロー"))
            !basketball -> game.pick(listOf("${opp}と痛み分け", "${opp}とドロー", "${opp}と引き分け", "${opp}と勝ち点1分け合う", "${at}${opp}とドロー"))
            else -> game.pick(listOf("${opp}と痛み分け", "${opp}と引き分け"))
        }
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
fun HomeAwayTag(
    isHome: Boolean,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    // 文字の大きさ(一面の「次の試合」では大きめにして目立たせる)
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp
) {
    val away = androidx.compose.ui.graphics.Color(0xFF2B4C7E)
    androidx.compose.material3.Text(
        if (isHome) "HOME" else "AWAY",
        modifier = modifier
            .then(androidx.compose.ui.Modifier.border(1.dp, if (isHome) com.fukuiteams.app.ui.theme.NewsRed else away))
            .background(if (isHome) com.fukuiteams.app.ui.theme.NewsRed else com.fukuiteams.app.ui.theme.Paper)
            .padding(horizontal = (fontSize.value / 2).dp, vertical = 1.dp),
        color = if (isHome) com.fukuiteams.app.ui.theme.White else away,
        fontSize = fontSize,
        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        softWrap = false
    )
}
