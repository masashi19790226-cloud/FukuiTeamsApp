package com.fukuiteams.app.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.Preferences
import com.fukuiteams.app.data.APP_AUTHOR
import com.fukuiteams.app.data.GameOutcome
import com.fukuiteams.app.data.GamePhotos
import com.fukuiteams.app.data.GameResultsRepository
import com.fukuiteams.app.data.GamesRepository
import com.fukuiteams.app.data.PlayersRepository
import com.fukuiteams.app.data.RemoteGameResult
import com.fukuiteams.app.data.buildResultContext
import com.fukuiteams.app.data.commentHeadline
import com.fukuiteams.app.data.gameLogDataStore
import com.fukuiteams.app.data.recordedComment
import com.fukuiteams.app.data.resultLead
import com.fukuiteams.app.data.shareText
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.Ivory
import com.fukuiteams.app.ui.theme.NewsRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 試合結果の「号外」画像(スポーツ新聞の号外風の1枚画像)を作って、LINE・X などへ共有する。
 * 見出し・リード文は一面の「速報」と同じもの(コメントを書いていればその言葉が大見出し)。
 * 写真を登録していれば1枚目を載せ、ブローウィンズはその試合の得点トップの選手も載せる。
 */

/** 号外画像に載せる内容。 */
private data class ExtraContent(
    val team: Team,
    val headline: String,
    val subHeadline: String?,
    val myScore: Int,
    val oppScore: Int,
    val opponent: String,
    val outcome: GameOutcome,
    val isHome: Boolean,
    val dateLine: String,
    val venue: String,
    val lead: String,
    val star: String?,
    val photo: File?
)

/** 「号外画像を作る」ボタン。押すと画像を作り、確認画面(共有・Xに投稿)を開く。 */
@Composable
fun ExtraEditionButton(game: Game, result: RemoteGameResult, outcome: GameOutcome, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by context.gameLogDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
    var busy by remember { mutableStateOf(false) }
    var made by remember(game.id) { mutableStateOf<Pair<Bitmap, File>?>(null) }

    OutlinedButton(
        onClick = {
            if (!busy) {
            busy = true
            scope.launch {
                try {
                    val comment = recordedComment(game.id, prefs)
                    val content = buildContent(context, game, result, outcome, comment)
                    val bitmap = withContext(Dispatchers.Default) { renderExtraEdition(content) }
                    val file = withContext(Dispatchers.IO) { saveForShare(context, game.id, bitmap) }
                    made = bitmap to file
                } catch (e: Exception) {
                    Toast.makeText(context, "号外画像を作れませんでした", Toast.LENGTH_SHORT).show()
                } finally {
                    busy = false
                }
            }
            }
        },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(if (busy) "号外を作成中…" else "号外画像を作る", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }

    made?.let { (bitmap, file) ->
        val text = shareText(game, result, outcome, recordedComment(game.id, prefs))
        Dialog(onDismissRequest = { made = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Ink)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "号外画像",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height),
                    contentScale = ContentScale.Fit
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { shareImage(context, file, text, "com.twitter.android") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NewsRed)
                    ) { Text("Xに投稿", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { shareImage(context, file, text, null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NewsRed)
                    ) { Text("共有(LINEなど)", fontWeight = FontWeight.Bold, maxLines = 1) }
                }
                OutlinedButton(onClick = { made = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("閉じる", color = Ivory)
                }
            }
        }
    }
}

/** 画像に載せる見出し・記事などをそろえる(連勝などの記事の材料と、得点トップの選手は通信して取る)。 */
private suspend fun buildContent(
    context: Context,
    game: Game,
    result: RemoteGameResult,
    outcome: GameOutcome,
    comment: String
): ExtraContent {
    val results = GameResultsRepository.fetch().ifEmpty { mapOf(game.id to result) }
    val ctx = buildResultContext(game, GamesRepository.games, results)
    val resultLine = resultHeadline(game, result.myScore, result.opponentScore, outcome, ctx)
    val commentTitle = commentHeadline(comment)
    val date = game.sortKey.take(8)
    val dateLine = runCatching {
        "${date.substring(0, 4)}年${date.substring(4, 6).toInt()}月${date.substring(6, 8).toInt()}日(${game.dayOfWeek})"
    }.getOrDefault(game.dateLabel)
    return ExtraContent(
        team = game.team,
        headline = commentTitle ?: resultLine,
        subHeadline = if (commentTitle != null) "${game.team.displayName} $resultLine" else null,
        myScore = result.myScore,
        oppScore = result.opponentScore,
        opponent = game.opponent,
        outcome = outcome,
        isHome = game.isHome,
        dateLine = dateLine,
        venue = game.venue.takeIf { it.isNotBlank() && !it.contains("調整中") } ?: "",
        lead = resultLead(game, result, outcome, comment, ctx),
        star = topPerformer(game),
        photo = GamePhotos.list(context, game.id).firstOrNull()
    )
}

/** ブローウィンズの試合なら、その試合の得点トップの選手(選手ページの試合ごとの成績から)。無ければ null。 */
private suspend fun topPerformer(game: Game): String? {
    if (game.team != Team.BLOWINDS) return null
    val date = game.sortKey.take(8).let { "${it.take(4)}-${it.substring(4, 6)}-${it.substring(6, 8)}" }
    val players = PlayersRepository.fetch()[Team.BLOWINDS.name]?.players ?: return null
    val best = players.mapNotNull { p ->
        val g = p.gameLog.firstOrNull { it.date == date } ?: return@mapNotNull null
        val pts = g.points?.toIntOrNull() ?: return@mapNotNull null
        Triple(p, g, pts)
    }.maxByOrNull { it.third } ?: return null
    val (p, g, pts) = best
    if (pts <= 0) return null
    val extra = listOfNotNull(
        g.rebounds?.takeIf { (it.toIntOrNull() ?: 0) >= 5 }?.let { "${it}リバウンド" },
        g.assists?.takeIf { (it.toIntOrNull() ?: 0) >= 5 }?.let { "${it}アシスト" }
    )
    return "#${p.number} ${p.name} ${pts}点" + if (extra.isNotEmpty()) "・" + extra.joinToString("・") else ""
}

// ---------------- 画像を描く ----------------

private const val W = 1080
private const val H = 1350
private const val MARGIN = 48f

private val PAPER = 0xFFF6F1E4.toInt()
private val INK = 0xFF141414.toInt()
private val INK_SOFT = 0xFF5A544C.toInt()
private val RED = 0xFFC8102E.toInt()
private val WHITE = 0xFFFFFFFF.toInt()

private fun paint(size: Float, color: Int, bold: Boolean = false, serif: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    this.color = color
    typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
}

/** 文字を幅に収まるよう1文字ずつ折り返す(句読点・閉じかっこは行の頭に来ないよう前の行に付ける)。 */
private fun wrap(text: String, p: Paint, maxWidth: Float): List<String> {
    val lines = mutableListOf<String>()
    text.split("\n").forEach { para ->
        var line = StringBuilder()
        para.forEach { ch ->
            val next = line.toString() + ch
            if (p.measureText(next) > maxWidth && line.isNotEmpty() && ch !in "、。，．」』）)！？!?ー") {
                lines += line.toString()
                line = StringBuilder().append(ch)
            } else {
                line.append(ch)
            }
        }
        if (line.isNotEmpty()) lines += line.toString()
    }
    return lines
}

/** 号外画像(縦1350×横1080)を描く。 */
private fun renderExtraEdition(c: ExtraContent): Bitmap {
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.drawColor(PAPER)
    val teamColor = c.team.color.toArgb()
    val right = W - MARGIN
    val width = right - MARGIN

    // 題字:赤地に白の「号外」と、紙名「ふくスポ」・日付
    cv.drawRect(MARGIN, 40f, MARGIN + 250f, 190f, Paint().apply { color = RED })
    val gogai = paint(108f, WHITE, bold = true, serif = true)
    val gw = gogai.measureText("号外")
    cv.drawText("号外", MARGIN + (250f - gw) / 2, 155f, gogai)
    cv.drawText("ふくスポ", MARGIN + 280f, 112f, paint(66f, INK, bold = true, serif = true))
    cv.drawText(c.dateLine, MARGIN + 284f, 170f, paint(34f, INK_SOFT))
    val tagText = if (c.isHome) "HOME" else "AWAY"
    val tagPaint = paint(32f, if (c.isHome) WHITE else INK, bold = true)
    val tw = tagPaint.measureText(tagText) + 28f
    val tagRect = RectF(right - tw, 120f, right, 168f)
    if (c.isHome) {
        cv.drawRect(tagRect, Paint().apply { color = RED })
    } else {
        cv.drawRect(tagRect, Paint().apply { color = INK; style = Paint.Style.STROKE; strokeWidth = 3f })
    }
    cv.drawText(tagText, tagRect.left + 14f, 156f, tagPaint)
    // 二重線
    cv.drawRect(MARGIN, 210f, right, 218f, Paint().apply { color = INK })
    cv.drawRect(MARGIN, 224f, right, 227f, Paint().apply { color = INK })

    // チーム名(チームの色)
    var y = 250f
    cv.drawRect(MARGIN, y, MARGIN + 12f, y + 52f, Paint().apply { color = teamColor })
    cv.drawText(c.team.displayName, MARGIN + 28f, y + 44f, paint(44f, teamColor, bold = true))
    y += 72f

    // 大見出し(長いときは文字を小さくして最大3行)
    var size = 104f
    var hp = paint(size, INK, bold = true, serif = true)
    var lines = wrap(c.headline, hp, width)
    while (lines.size > 3 && size > 64f) {
        size -= 8f
        hp = paint(size, INK, bold = true, serif = true)
        lines = wrap(c.headline, hp, width)
    }
    lines.take(3).forEach { line ->
        y += size * 1.1f
        cv.drawText(line, MARGIN, y, hp)
    }
    c.subHeadline?.let { sub ->
        val sp = paint(42f, teamColor, bold = true)
        wrap(sub, sp, width).take(2).forEach { line ->
            y += 54f
            cv.drawText(line, MARGIN, y, sp)
        }
    }
    y += 30f

    // スコアボックス
    val boxTop = y
    val boxBottom = y + 190f
    cv.drawRect(MARGIN, boxTop, right, boxBottom, Paint().apply { color = WHITE })
    cv.drawRect(MARGIN, boxTop, right, boxBottom, Paint().apply { color = INK; style = Paint.Style.STROKE; strokeWidth = 5f })
    val label = paint(40f, INK, bold = true)
    cv.drawText("福井", MARGIN + 30f, boxTop + 85f, label)
    val resultWord = when (c.outcome) {
        GameOutcome.WIN -> "WIN"
        GameOutcome.LOSE -> "LOSE"
        GameOutcome.DRAW -> "DRAW"
    }
    cv.drawText(resultWord, MARGIN + 30f, boxTop + 140f, paint(32f, if (c.outcome == GameOutcome.WIN) RED else INK_SOFT, bold = true))
    val scorePaint = paint(140f, INK, bold = true, serif = true)
    val myPaint = paint(140f, if (c.outcome == GameOutcome.WIN) RED else INK, bold = true, serif = true)
    val scoreText = "${c.myScore}"
    val dash = " - "
    val oppText = "${c.oppScore}"
    val total = myPaint.measureText(scoreText) + scorePaint.measureText(dash) + scorePaint.measureText(oppText)
    var sx = (W - total) / 2f
    val sy = boxTop + 145f
    cv.drawText(scoreText, sx, sy, myPaint); sx += myPaint.measureText(scoreText)
    cv.drawText(dash, sx, sy, scorePaint); sx += scorePaint.measureText(dash)
    cv.drawText(oppText, sx, sy, scorePaint)
    val oppPaint = paint(36f, INK, bold = true)
    val oppLines = wrap(c.opponent, oppPaint, 200f).take(2)
    oppLines.forEachIndexed { i, line ->
        cv.drawText(line, right - 30f - oppPaint.measureText(line), boxTop + 85f + i * 46f, oppPaint)
    }
    y = boxBottom + 24f

    // 写真(登録していれば)
    val footerTop = H - 120f
    c.photo?.let { file ->
        val src = runCatching { decodeSampled(file, 1200) }.getOrNull() ?: return@let
        val photoH = 330f
        val dst = RectF(MARGIN, y, right, y + photoH)
        cv.drawBitmap(src, centerCrop(src, dst), dst, Paint(Paint.FILTER_BITMAP_FLAG))
        y += photoH + 20f
    }

    // 活躍した選手
    c.star?.let { star ->
        val sp = paint(36f, RED, bold = true)
        wrap("★活躍 $star", sp, width).take(2).forEach { line ->
            y += 46f
            cv.drawText(line, MARGIN, y, sp)
        }
        y += 10f
    }

    // リード文(入るだけ)
    val lp = paint(33f, INK)
    for (line in wrap(c.lead, lp, width)) {
        if (y + 46f > footerTop - 10f) break
        y += 46f
        cv.drawText(line, MARGIN, y, lp)
    }

    // 下の欄:会場と、アプリ名・作者
    cv.drawRect(MARGIN, footerTop, right, footerTop + 3f, Paint().apply { color = INK })
    val fp = paint(28f, INK_SOFT)
    if (c.venue.isNotBlank()) {
        wrap("会場:${c.venue}", fp, width).take(1).forEach { line -> cv.drawText(line, MARGIN, footerTop + 44f, fp) }
    }
    val credit = "ふくスポ(作:$APP_AUTHOR)"
    cv.drawText(credit, right - fp.measureText(credit), footerTop + 88f, fp)
    return bmp
}

/** 写真を、描く枠の縦横比に合わせて中央を切り出す範囲。 */
private fun centerCrop(src: Bitmap, dst: RectF): Rect {
    val srcRatio = src.width.toFloat() / src.height
    val dstRatio = dst.width() / dst.height()
    return if (srcRatio > dstRatio) {
        val w = (src.height * dstRatio).toInt()
        val left = (src.width - w) / 2
        Rect(left, 0, left + w, src.height)
    } else {
        val h = (src.width / dstRatio).toInt()
        // 人物の顔が上の方にあることが多いので、少し上寄りを切り出す
        val top = ((src.height - h) * 0.35f).toInt()
        Rect(0, top, src.width, top + h)
    }
}

private fun decodeSampled(file: File, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** 共有用にアプリのキャッシュ(cache/share)へPNGで保存する。同じ試合の画像は上書き。 */
private fun saveForShare(context: Context, gameId: String, bitmap: Bitmap): File {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "gogai_$gameId.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

/** 画像と文章を共有する。packageName を渡すとそのアプリ(X など)を直接開く。無ければ共有先を選ぶ画面。 */
private fun shareImage(context: Context, file: File, text: String, packageName: String?) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        if (packageName != null) {
            context.startActivity(intent.setPackage(packageName))
        } else {
            context.startActivity(Intent.createChooser(intent, "号外を共有"))
        }
    } catch (e: Exception) {
        if (packageName != null) {
            // Xのアプリが無いときは、共有先を選ぶ画面にする
            try {
                context.startActivity(Intent.createChooser(intent.setPackage(null), "号外を共有"))
            } catch (e2: Exception) {
                Toast.makeText(context, "共有できませんでした", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "共有できませんでした", Toast.LENGTH_SHORT).show()
        }
    }
}
