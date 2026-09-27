package com.fukuiteams.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.BitmapFactory
import com.fukuiteams.app.data.GameOutcome
import com.fukuiteams.app.data.GamePhotos
import com.fukuiteams.app.data.RemoteGameResult
import com.fukuiteams.app.data.WatchMethod
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.Ivory
import com.fukuiteams.app.ui.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 新聞の写真説明(キャプション)を試合の情報から自動で作る。
 * 例:「岐阜に90－88で競り勝ったブローウィンズの一戦＝9月26日、OKBぎふ清流アリーナ(現地で撮影)」
 */
fun photoCaption(game: Game, result: RemoteGameResult?, outcome: GameOutcome?, method: WatchMethod?): String {
    val team = game.team.shortLabel()
    val opp = game.opponent
    val score = result?.let { "${it.myScore}－${it.opponentScore}で" } ?: ""
    val basketball = game.team == Team.BLOWINDS
    val diff = result?.let { kotlin.math.abs(it.myScore - it.opponentScore) }
    val close = diff != null && (if (basketball) diff <= 3 else diff <= 1)
    val big = diff != null && (if (basketball) diff >= 15 else diff >= 3)
    val body = when (outcome) {
        GameOutcome.WIN -> when {
            close -> "${opp}に${score}競り勝った${team}の一戦"
            big -> "${opp}に${score}完勝した${team}の一戦"
            else -> "${opp}を${score}下した${team}の一戦"
        }
        GameOutcome.LOSE -> when {
            close -> "${opp}に${score}惜敗した${team}の一戦"
            big -> "${opp}に${score}完敗した${team}の一戦"
            else -> "${opp}に${score}敗れた${team}の一戦"
        }
        GameOutcome.DRAW -> "${opp}と${score}引き分けた${team}の一戦"
        null -> "${team}対${opp}の一戦"
    }
    val parts = game.dateLabel.split("/")
    val date = if (parts.size == 3) "${parts[1]}月${parts[2]}日" else game.dateLabel
    val credit = if (method == WatchMethod.ON_SITE) "(現地で撮影)" else ""
    return "${body}＝${date}、${game.venue}$credit"
}

/** 写真ファイルを裏で読み込んで表示用の画像にする。 */
@Composable
fun rememberPhoto(file: File?): ImageBitmap? {
    val state by produceState<ImageBitmap?>(initialValue = null, file?.path) {
        value = file?.let {
            withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(it.path)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    return state
}

/** 紙面の写真1枚+その下に「▲」から始まる写真説明。 */
@Composable
fun NewspaperPhoto(
    file: File,
    caption: String?,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val image = rememberPhoto(file)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .border(1.dp, Ink)
                .background(DividerGray)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = caption,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        if (caption != null) {
            Text("▲$caption", fontSize = 11.sp, lineHeight = 15.sp, color = Ink)
        }
    }
}

/**
 * 試合の写真を紙面風に組む。1枚目を大きく(自動キャプション付き)、2枚目以降は2枚ずつ横に並べる。
 * タップで拡大表示。
 */
@Composable
fun GamePhotoSpread(game: Game, photos: List<File>, leadCaption: String) {
    if (photos.isEmpty()) return
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NewspaperPhoto(photos[0], leadCaption, 220.dp, onClick = { viewerIndex = 0 })
        photos.drop(1).chunked(2).forEachIndexed { rowIndex, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { i, file ->
                    val index = 1 + rowIndex * 2 + i
                    NewspaperPhoto(
                        file,
                        "同試合の一場面(${index + 1})",
                        120.dp,
                        modifier = Modifier.weight(1f),
                        onClick = { viewerIndex = index }
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
    viewerIndex?.let { start ->
        PhotoViewerDialog(
            photos = photos,
            startIndex = start,
            captionFor = { i -> if (i == 0) leadCaption else "同試合の一場面(${i + 1})" },
            onDismiss = { viewerIndex = null }
        )
    }
}

/** 写真を画面いっぱいに表示。「前へ」「次へ」で切り替え、外側タップで閉じる。 */
@Composable
fun PhotoViewerDialog(
    photos: List<File>,
    startIndex: Int,
    captionFor: (Int) -> String,
    onDismiss: () -> Unit
) {
    var index by remember { mutableStateOf(startIndex.coerceIn(0, photos.lastIndex)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val image = rememberPhoto(photos[index])
            Box(modifier = Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                if (image != null) {
                    Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            Text("▲${captionFor(index)}", color = Ivory, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (index > 0) index-- }, enabled = index > 0) { Text("‹ 前へ", color = Ivory) }
                Text("${index + 1} / ${photos.size}", color = Ivory, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { if (index < photos.lastIndex) index++ }, enabled = index < photos.lastIndex) { Text("次へ ›", color = Ivory) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("閉じる", color = Ivory) }
        }
    }
}

/**
 * 過去の試合の記録欄に置く写真の登録列。「＋追加」でギャラリーから選び、タップで拡大、長押しで削除。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoStrip(game: Game, caption: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = GamePhotos.version
    val photos = remember(game.id, version) { GamePhotos.list(context, game.id) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var adding by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            adding = true
            scope.launch {
                GamePhotos.add(context, game.id, uris)
                adding = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(photos, key = { it.name }) { file ->
                val thumb = rememberPhoto(GamePhotos.thumbOf(file).takeIf { it.exists() } ?: file)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .border(1.dp, Ink)
                        .background(DividerGray)
                        .combinedClickable(
                            onClick = { viewerIndex = photos.indexOf(file) },
                            onLongClick = { deleteTarget = file }
                        )
                ) {
                    if (thumb != null) {
                        Image(bitmap = thumb, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .size(72.dp)
                        .border(1.dp, InkSoft)
                        .background(Paper)
                        .clickable(enabled = !adding) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(if (adding) "…" else "＋", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(if (adding) "保存中" else "写真追加", fontSize = 10.sp, color = Ink)
                }
            }
        }
        Text(
            if (photos.isEmpty()) "観戦の写真を登録すると、紙面に写真説明付きで載ります"
            else "タップで拡大・長押しで削除",
            fontSize = 10.sp,
            color = InkSoft
        )
    }

    viewerIndex?.let { start ->
        PhotoViewerDialog(
            photos = photos,
            startIndex = start,
            captionFor = { i -> if (i == 0) caption else "同試合の一場面(${i + 1})" },
            onDismiss = { viewerIndex = null }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("この写真を削除しますか?") },
            text = { Text("アプリ内に保存した写真だけが消えます。ギャラリーの元の写真は残ります。") },
            confirmButton = {
                TextButton(onClick = {
                    GamePhotos.delete(target)
                    deleteTarget = null
                }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("やめる") } }
        )
    }
}
