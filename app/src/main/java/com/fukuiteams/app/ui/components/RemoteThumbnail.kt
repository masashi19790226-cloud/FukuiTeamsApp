package com.fukuiteams.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.Ivory
import com.fukuiteams.app.ui.theme.LineGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * ネット上の小さな画像(ニュースのサムネイル・選手の顔写真)を表示する。
 * ・画面に出た行の分だけ読み込む(LazyColumn の中で使うので、見えていない行は読み込まない)
 * ・一度読んだ画像は、アプリを開いている間はメモリに置いて使い回す(スマホには保存しない)
 * ・読み込めなかったときは、何も出さない(枠も出さない)
 */
private object ThumbnailCache {
    // 最大およそ 8MB 分の画像を覚えておく
    val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    // タップで拡大したときの大きい画像(数枚分だけ覚えておく。およそ 16MB まで)
    val largeCache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    // 透明な余白を切り取った画像(一面の特集で使う。およそ 8MB まで)
    val trimmed = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    // 読み込みに失敗したURL(同じものを何度も読みに行かない)
    val failed: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())
}

/** 画像をダウンロードし、短い辺が minSide px 以上残る範囲で縮小して読み込む。 */
private fun downloadAndDecode(url: String, minSide: Int): ImageBitmap? {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8_000
    c.readTimeout = 8_000
    // ふつうのスマホのブラウザと同じ名乗り方にする(独自の名乗りだと画像を返さないサイトがあるため)。
    // 画像のサイト自身から開いたことにする(Referer)。直リンクを断るサイト対策
    c.setRequestProperty(
        "User-Agent",
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    )
    runCatching { URL(url).let { u -> c.setRequestProperty("Referer", "${u.protocol}://${u.host}/") } }
    c.instanceFollowRedirects = true
    val bytes = c.inputStream.use { it.readBytes() }
    c.disconnect()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= minSide && bounds.outHeight / (sample * 2) >= minSide) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}

/**
 * 透明な部分(切り抜き写真の周りなど)を外側から切り取る。透明な部分が無い画像はそのまま返す。
 * 一面の特集で、写真の周りに余白が出ないようにするため。
 */
private fun trimTransparentEdges(img: ImageBitmap): ImageBitmap {
    val bmp = img.asAndroidBitmap()
    if (!bmp.hasAlpha()) return img
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return img
    val px = IntArray(w * h)
    try {
        bmp.getPixels(px, 0, w, 0, 0, w, h)
    } catch (e: Exception) {
        return img
    }
    var top = h
    var bottom = -1
    var left = w
    var right = -1
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            // ほぼ透明(不透明度 16/255 以下)な点は余白とみなす
            if ((px[row + x] ushr 24) > 16) {
                if (y < top) top = y
                if (y > bottom) bottom = y
                if (x < left) left = x
                if (x > right) right = x
            }
        }
    }
    if (bottom < 0) return img
    if (left == 0 && top == 0 && right == w - 1 && bottom == h - 1) return img
    return android.graphics.Bitmap.createBitmap(bmp, left, top, right - left + 1, bottom - top + 1).asImageBitmap()
}

private suspend fun loadThumbnail(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    ThumbnailCache.cache.get(url)?.let { return@withContext it }
    if (url in ThumbnailCache.failed) return@withContext null
    // 読めなければ、縮小版ではない元の画像(WordPress の「-150x150」を取ったもの)も試す
    for (candidate in thumbnailCandidates(url)) {
        val bitmap = try {
            // 大きな画像は縮小して読み込む(選手の顔写真を大きめに出してもぼやけないよう、320px 程度は残す)
            downloadAndDecode(candidate, 320)
        } catch (e: Exception) {
            null
        }
        if (bitmap != null) {
            ThumbnailCache.cache.put(url, bitmap)
            return@withContext bitmap
        }
    }
    ThumbnailCache.failed.add(url)
    null
}

/** 一覧の小さい画像の候補(よい順):そのままのURL → 縮小版でない元の画像 → 300x300 の縮小版 */
private fun thumbnailCandidates(url: String): List<String> {
    val list = mutableListOf(url)
    if (url.contains("/wp-content/uploads/")) {
        val m = Regex("""-\d+x\d+(\.\w+)(\?.*)?$""").find(url)
        if (m != null) {
            list += url.replace(m.value, m.groupValues[1])
            list += url.replace(m.value, "-300x300" + m.groupValues[1])
        }
    }
    return list.distinct()
}

/**
 * 拡大表示で読みに行く画像の候補(よい順)。
 * WordPress のサイト(丸岡RUCK公式など)は「〜-150x150.jpg」のような縮小版を一覧に使っているので、
 * 「-150x150」を取った元の大きい画像を先に試し、無ければ一覧と同じ画像にする。
 */
private fun largeImageCandidates(url: String): List<String> {
    val original = if (url.contains("/wp-content/uploads/")) {
        url.replace(Regex("""-\d+x\d+(\.\w+)(\?.*)?$"""), "$1")
    } else {
        url
    }
    return listOf(original, url).distinct()
}

/** 拡大表示用。元の画像をできるだけそのままの細かさで読み込む(大きすぎる画像だけ 1000px 程度に縮小)。 */
private suspend fun loadLargeImage(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    ThumbnailCache.largeCache.get(url)?.let { return@withContext it }
    for (candidate in largeImageCandidates(url)) {
        val bitmap = try {
            downloadAndDecode(candidate, 1000)
        } catch (e: Exception) {
            null
        }
        if (bitmap != null) {
            ThumbnailCache.largeCache.put(url, bitmap)
            return@withContext bitmap
        }
    }
    null
}

/**
 * size:正方形の一辺。width・height を渡すと縦長などにできる。
 * alignTop:切り抜くときに上側を残す(選手の顔写真は顔が上にあるため)
 * zoomCaption:null 以外を渡すと、タップで画像を大きく表示する(渡した文字は拡大画面の下に出す)
 * fillWidth:true なら幅いっぱい(高さは height)にする(一面の特集のニュース画像など)
 * trimTransparent:true なら、透明な余白(切り抜き写真の周りなど)を切り取ってから表示する
 * backgroundColor:写真の透明な部分の後ろに見える色。framed:false なら枠線を付けない
 */
@Composable
fun RemoteThumbnail(
    url: String,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier,
    width: Dp = size,
    height: Dp = size,
    alignTop: Boolean = false,
    zoomCaption: String? = null,
    fillWidth: Boolean = false,
    trimTransparent: Boolean = false,
    backgroundColor: Color = DividerGray,
    framed: Boolean = true
) {
    if (url.isBlank()) return
    // URLが変わったら(並べ替えで同じ位置に別の選手が来た・データが新しくなった など)、
    // 前の画像を引き継がず、そのURLの画像を読み直す。
    // (以前は前の画像が残ったままになり、名前と写真が食い違うことがあった)
    var image by remember(url) { mutableStateOf(ThumbnailCache.cache.get(url)) }
    LaunchedEffect(url) {
        if (image == null) image = loadThumbnail(url)
    }
    val loaded = image ?: return
    // 透明な余白を切り取る指定なら、切り取った画像を使う(切り取るまでは元の画像)
    var trimmed by remember(url, trimTransparent) {
        mutableStateOf(if (trimTransparent) ThumbnailCache.trimmed.get(url) else null)
    }
    if (trimTransparent) {
        LaunchedEffect(url, loaded) {
            if (trimmed == null) {
                trimmed = withContext(Dispatchers.Default) { trimTransparentEdges(loaded) }
                    .also { ThumbnailCache.trimmed.put(url, it) }
            }
        }
    }
    val img = trimmed ?: loaded
    var zoomed by remember(url) { mutableStateOf(false) }
    val sizeModifier = if (fillWidth) Modifier.fillMaxWidth().height(height) else Modifier.size(width, height)
    Box(
        modifier = modifier
            .then(sizeModifier)
            .then(if (framed) Modifier.border(1.dp, LineGray) else Modifier)
            .background(backgroundColor)
            .then(if (zoomCaption != null) Modifier.clickable { zoomed = true } else Modifier)
    ) {
        Image(
            bitmap = img,
            contentDescription = null,
            modifier = sizeModifier,
            contentScale = ContentScale.Crop,
            alignment = if (alignTop) Alignment.TopCenter else Alignment.Center
        )
    }
    if (zoomed && zoomCaption != null) {
        RemoteImageViewerDialog(url = url, preview = img, caption = zoomCaption, onDismiss = { zoomed = false })
    }
}

/**
 * ネット上の画像を画面いっぱいに大きく表示する。
 * 大きい画像を読み込むまでは、一覧で使っている小さい画像を代わりに出す。画像か外側をタップで閉じる。
 */
@Composable
private fun RemoteImageViewerDialog(url: String, preview: ImageBitmap, caption: String, onDismiss: () -> Unit) {
    // URLごとに読み直す(別の画像の拡大画像が残らないように)
    var large by remember(url) { mutableStateOf(ThumbnailCache.largeCache.get(url)) }
    LaunchedEffect(url) {
        if (large == null) large = loadLargeImage(url)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 一覧の小さい写真と同じ地の色(DividerGray)の上に、写真の縦横比のまま表示する。
            // 背景が透明な写真(選手の切り抜き写真など)も、拡大したときに背景の色が変わらない
            val shown = large ?: preview
            val ratio = if (shown.height > 0) shown.width.toFloat() / shown.height else 1f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = shown,
                    contentDescription = caption,
                    modifier = Modifier
                        .aspectRatio(ratio.coerceIn(0.2f, 5f))
                        .background(DividerGray),
                    contentScale = ContentScale.Fit
                )
            }
            if (caption.isNotBlank()) {
                Text(caption, color = Ivory, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("閉じる", color = Ivory) }
        }
    }
}
