package com.fukuiteams.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.FeatureStory
import com.fukuiteams.app.data.LeagueStandings
import com.fukuiteams.app.data.RemoteGameResult
import com.fukuiteams.app.data.daysUntil
import com.fukuiteams.app.data.headToHead
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.theme.Ink

// 特集で、写真の代わりに出す絵(すべてアプリの中で描く。Web版の graphics.js と同じ考え方)
// ・選手カード:写真の無い選手の記事。チームカラーに斜めの帯と斜体の背番号
// ・記事の中身の図:直近の結果(○●とスコア)・順位表・試合までの日数と対戦成績
// ・競技のコート・ピッチの線画:図の背景に薄く敷く

/** 選手の名前などから背番号を取り出す(「#23 山田」「元#5 山田」など。無ければ空) */
fun numberFromCaption(text: String?): String =
    Regex("""#\s*([0-9０-９]{1,3})""").find(text.orEmpty())?.groupValues?.get(1)
        ?.map { c -> if (c in '０'..'９') ('0' + (c - '０')) else c }?.joinToString("") ?: ""

/** 写真説明の最後の「(相手チーム名)」。相手チームの選手なら、そのチーム名。福井側の選手なら null */
private fun opponentFromCaption(text: String?): String? =
    Regex("""[(（]([^)）]+)[)）]\s*$""").find(text.orEmpty())?.groupValues?.get(1)

/** 背番号の数字のフォント(太い斜体) */
private val NumberFont = FontFamily.SansSerif

/**
 * 選手カード(特集の大きい絵)。左上に「#背番号」、後ろに薄く大きな背番号、下に名前(名字と名前の2行)とひとこと。
 * caption は「#23 山田 太郎」「#7 佐藤 花子(相手チーム)」の形(背番号が無ければ「#−」)。
 */
@Composable
fun FeaturePlayerCard(story: FeatureStory, modifier: Modifier = Modifier) {
    val caption = story.photoCaption.orEmpty()
    val number = numberFromCaption(caption)
    val opponent = opponentFromCaption(caption)
    val base = if (opponent != null) Color(0xFF5B6470) else (story.team?.color ?: Ink)
    val name = caption.replace(Regex("""^\s*元?#\s*\S+\s*"""), "").replace(Regex("""[(（][^)）]+[)）]\s*$"""), "").trim()
    val parts = name.split(' ', '　').filter { it.isNotBlank() }
    val longest = parts.maxOfOrNull { it.length } ?: 0
    val nameSize = when {
        longest >= 9 -> 12
        longest >= 6 -> 14
        longest >= 5 -> 16
        else -> 19
    }
    val sub = when {
        opponent != null -> "相手:$opponent"
        story.bigNumber != null -> "${story.kicker}・${story.bigNumber}${story.bigUnit.orEmpty()}"
        else -> story.kicker
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
            .background(Brush.linearGradient(listOf(base, lerp(base, Color.Black, 0.32f))))
    ) {
        Band()
        if (number.isNotEmpty()) {
            // 後ろの大きな背番号(薄く)
            Text(
                number,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-14).dp),
                color = Color.White.copy(alpha = 0.14f),
                fontFamily = NumberFont,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontSize = 110.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(
            if (number.isNotEmpty()) "#$number" else "#−",
            modifier = Modifier.padding(start = 10.dp, top = 8.dp),
            color = Color.White,
            fontFamily = NumberFont,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            fontSize = 36.sp,
            lineHeight = 38.sp,
            maxLines = 1,
            softWrap = false
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 10.dp, end = 8.dp, bottom = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                if (parts.size >= 2) parts.first() + "\n" + parts.drop(1).joinToString("") else name,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = nameSize.sp,
                lineHeight = (nameSize * 1.15).sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.6f)))
            Text(sub, color = Color.White.copy(alpha = 0.92f), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 斜めの帯(選手カード・図の背景) */
@Composable
private fun Band() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 24.dp)
                .fillMaxWidth()
                .height(40.dp)
                .graphicsLayer(rotationZ = -18f, scaleX = 1.8f)
                .background(Color.White.copy(alpha = 0.12f))
        )
    }
}

/** 記事の中身の図を作れる記事か(写真の無いときに、代わりの写真ではなく図を出す記事) */
fun hasFeatureDataGraphic(story: FeatureStory): Boolean =
    story.team != null && (story.kicker == "順位" || story.kicker in RECENT_KICKERS || (story.kicker in COUNTDOWN_KICKERS && story.gameId != null))

private val RECENT_KICKERS = setOf("数字で見る", "今季のベストゲーム", "しびれた一戦", "シーズンの行方")
private val COUNTDOWN_KICKERS = setOf("決戦まで", "相手を知る", "要注意人物")

/**
 * 記事の中身を絵にしたもの(直近の結果・順位・決戦まで)。作れなければ false を返して何も出さない。
 * 背景にはチームの競技のコート・ピッチの線画を薄く敷く。
 */
@Composable
fun FeatureDataGraphic(
    story: FeatureStory,
    games: List<Game>,
    results: Map<String, RemoteGameResult>,
    standings: Map<String, LeagueStandings>,
    modifier: Modifier = Modifier
): Boolean {
    val team = story.team ?: return false
    val content: (@Composable ColumnScope.() -> Unit)? = when {
        story.kicker == "順位" -> standingLadder(team, standings)
        story.kicker in RECENT_KICKERS -> recentForm(team, games, results)
        story.kicker in COUNTDOWN_KICKERS && story.gameId != null -> countdown(story.gameId, games, results)
        else -> null
    }
    if (content == null) return false
    Box(modifier = modifier.background(team.color)) {
        SportLines(team, Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            content()
        }
    }
    return true
}

/** 図の見出し(白い文字に下線) */
@Composable
private fun GraphicTitle(text: String) {
    Column {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.55f)))
    }
}

private fun mark(r: RemoteGameResult): String = when {
    r.myScore > r.opponentScore -> "○"
    r.myScore < r.opponentScore -> "●"
    else -> "△"
}

private fun monthDay(g: Game): String = g.dateLabel.split("/").drop(1).joinToString("/")

/** 直近の結果(○●の並びと、最近4試合のスコア) */
private fun recentForm(team: Team, games: List<Game>, results: Map<String, RemoteGameResult>): (@Composable ColumnScope.() -> Unit)? {
    val played = games.filter { it.team == team && results.containsKey(it.id) }.sortedBy { it.sortKey }
    if (played.isEmpty()) return null
    val last = played.takeLast(6)
    return {
        GraphicTitle("直近の結果")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            last.forEach { g ->
                Text(mark(results.getValue(g.id)), color = Color.White, fontSize = 16.sp, lineHeight = 18.sp)
            }
        }
        last.takeLast(4).reversed().forEach { g ->
            val r = results.getValue(g.id)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(monthDay(g), color = Color.White.copy(alpha = 0.8f), fontSize = 10.5.sp, maxLines = 1, softWrap = false)
                Text("${r.myScore}-${r.opponentScore}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                Text(g.opponent, color = Color.White, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun teamWord(team: Team): String = when (team) {
    Team.RAC -> "丸岡"
    Team.THUNDER -> "ブルーサンダー"
    Team.UNITED -> "ユナイテッド"
    Team.BLOWINDS -> "ブローウィンズ"
}

private data class LadderRow(val rank: Int, val name: String, val value: String, val own: Boolean)

/** 順位(自分のチームのまわりの順位表) */
private fun standingLadder(team: Team, standings: Map<String, LeagueStandings>): (@Composable ColumnScope.() -> Unit)? {
    val league = standings[team.name] ?: return null
    val rows: List<LadderRow> = if (team == Team.BLOWINDS) {
        val b = league.bleague ?: return null
        val own = b.own ?: return null
        val me = b.me ?: return null
        own.rows.map { r -> LadderRow(r.rank, r.short.ifBlank { r.team }, "${r.win}-${r.lose}", r === me) }
    } else {
        val part = league.final?.takeIf { it.rows.isNotEmpty() } ?: league.regular ?: return null
        part.rows.map { r -> LadderRow(r.rank, r.team, "${r.points}", r.team.contains(teamWord(team))) }
    }.sortedBy { it.rank }
    val i = rows.indexOfFirst { it.own }
    if (i < 0) return null
    val max = 7
    val start = maxOf(0, minOf(i - 3, rows.size - max))
    val view = rows.drop(start).take(max)
    return {
        GraphicTitle(if (team == Team.BLOWINDS) "地区の順位(勝-敗)" else "順位(勝点)")
        view.forEach { r ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (r.own) Modifier.background(Color.White, RoundedCornerShape(2.dp)) else Modifier)
                    .padding(horizontal = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val c = if (r.own) Ink else Color.White
                Text("${r.rank}", color = c.copy(alpha = if (r.own) 1f else 0.85f), fontSize = 10.5.sp, modifier = Modifier.width(16.dp), maxLines = 1)
                Text(r.name, color = c, fontSize = 10.5.sp, fontWeight = if (r.own) FontWeight.ExtraBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(r.value, color = c, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** 決戦まで(あと何日と、相手との対戦成績) */
private fun countdown(gameId: String, games: List<Game>, results: Map<String, RemoteGameResult>): (@Composable ColumnScope.() -> Unit)? {
    val g = games.firstOrNull { it.id == gameId } ?: return null
    val days = g.daysUntil() ?: return null
    if (days < 0) return null
    val h2h = headToHead(g, games, results)
    return {
        GraphicTitle("${monthDay(g)} ${g.opponent}戦")
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text("あと", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 6.dp))
            Text("$days", color = Color.White, fontSize = 50.sp, lineHeight = 52.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
            Text("日", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.weight(1f))
        if (h2h != null && h2h.wins + h2h.losses + h2h.draws > 0) {
            Text(
                "対戦成績 ${h2h.wins}勝${h2h.losses}敗" + (if (h2h.draws > 0) "${h2h.draws}分" else ""),
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color.White, RoundedCornerShape(4.dp))
            ) {
                if (h2h.wins > 0) Box(Modifier.weight(h2h.wins.toFloat()).height(8.dp).background(Color.White))
                if (h2h.draws > 0) Box(Modifier.weight(h2h.draws.toFloat()).height(8.dp).background(Color.White.copy(alpha = 0.5f)))
                if (h2h.losses > 0) Box(Modifier.weight(h2h.losses.toFloat()).height(8.dp))
            }
        } else {
            Text("記録上は初対戦", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** チームの競技のコート・ピッチの線画(縦長 150×200 を基準に、枠に合わせて伸ばす)。背景に薄く敷く */
@Composable
fun SportLines(team: Team, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sx = size.width / 150f
        val sy = size.height / 200f
        val c = Color.White.copy(alpha = 0.22f)
        val w = 1.6f * minOf(sx, sy)
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, dashed: Boolean = false) =
            drawLine(c, p(x1, y1), p(x2, y2), strokeWidth = w, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5f * sx, 4f * sx)) else null)
        fun rect(x: Float, y: Float, rw: Float, rh: Float) = drawRect(c, p(x, y), Size(rw * sx, rh * sy), style = Stroke(w))
        fun circle(cx: Float, cy: Float, r: Float) = drawOval(c, p(cx - r, cy - r), Size(2 * r * sx, 2 * r * sy), style = Stroke(w))
        fun quad(points: List<Pair<Float, Float>>, dashed: Boolean = false) {
            // 3点ずつの2次曲線(始点・制御点・終点 … )
            val path = Path()
            path.moveTo(points[0].first * sx, points[0].second * sy)
            var k = 1
            while (k + 1 < points.size) {
                path.quadraticBezierTo(points[k].first * sx, points[k].second * sy, points[k + 1].first * sx, points[k + 1].second * sy)
                k += 2
            }
            drawPath(path, c, style = Stroke(w, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5f * sx, 4f * sx)) else null))
        }
        rect(8f, 8f, 134f, 184f)
        when (team) {
            Team.BLOWINDS -> { // バスケットボール(ハーフコート)
                drawArc(c, 0f, 180f, false, p(53f, -14f), Size(44f * sx, 44f * sy), style = Stroke(w))
                rect(53f, 122f, 44f, 70f)
                circle(75f, 122f, 22f)
                drawArc(c, 180f, 180f, false, p(18f, 102f), Size(114f * sx, 116f * sy), style = Stroke(w))
                line(18f, 160f, 18f, 192f); line(132f, 160f, 132f, 192f)
                circle(75f, 178f, 5f)
            }
            Team.THUNDER -> { // ハンドボール(6mラインと9mの点線)
                line(8f, 100f, 142f, 100f)
                quad(listOf(28f to 8f, 28f to 48f, 60f to 48f)); line(60f, 48f, 90f, 48f); quad(listOf(90f to 48f, 122f to 48f, 122f to 8f))
                quad(listOf(14f to 8f, 14f to 66f, 60f to 66f), true); line(60f, 66f, 90f, 66f, true); quad(listOf(90f to 66f, 136f to 66f, 136f to 8f), true)
                quad(listOf(28f to 192f, 28f to 152f, 60f to 152f)); line(60f, 152f, 90f, 152f); quad(listOf(90f to 152f, 122f to 152f, 122f to 192f))
                quad(listOf(14f to 192f, 14f to 134f, 60f to 134f), true); line(60f, 134f, 90f, 134f, true); quad(listOf(90f to 134f, 136f to 134f, 136f to 192f), true)
            }
            Team.RAC -> { // フットサル
                line(8f, 100f, 142f, 100f); circle(75f, 100f, 20f)
                quad(listOf(40f to 8f, 40f to 40f, 66f to 40f)); line(66f, 40f, 84f, 40f); quad(listOf(84f to 40f, 110f to 40f, 110f to 8f))
                quad(listOf(40f to 192f, 40f to 160f, 66f to 160f)); line(66f, 160f, 84f, 160f); quad(listOf(84f to 160f, 110f to 160f, 110f to 192f))
            }
            Team.UNITED -> { // サッカー
                line(8f, 100f, 142f, 100f); circle(75f, 100f, 20f)
                rect(38f, 8f, 74f, 30f); rect(58f, 8f, 34f, 11f)
                rect(38f, 162f, 74f, 30f); rect(58f, 181f, 34f, 11f)
            }
        }
    }
}
