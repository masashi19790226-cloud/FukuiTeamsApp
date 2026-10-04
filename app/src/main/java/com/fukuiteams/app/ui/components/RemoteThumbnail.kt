package com.fukuiteams.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fukuiteams.app.ui.theme.DividerGray
import com.fukuiteams.app.ui.theme.LineGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * ネット上の小さな画像(ニュースのサムネイル)を表示する。
 * ・画面に出た行の分だけ読み込む(LazyColumn の中で使うので、見えていない行は読み込まない)
 * ・一度読んだ画像は、アプリを開いている間はメモリに置いて使い回す(スマホには保存しない)
 * ・読み込めなかったときは、何も出さない(枠も出さない)
 */
private object ThumbnailCache {
    // 最大およそ 8MB 分の画像を覚えておく
    val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    // 読み込みに失敗したURL(同じものを何度も読みに行かない)
    val failed: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())
}

private suspend fun loadThumbnail(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    ThumbnailCache.cache.get(url)?.let { return@withContext it }
    if (url in ThumbnailCache.failed) return@withContext null
    try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 8_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) FukuiSpo")
        val bytes = c.inputStream.use { it.readBytes() }
        c.disconnect()
        // 大きな画像は縮小して読み込む(サムネイルなので 200px 程度で十分)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 200 && bounds.outHeight / (sample * 2) >= 200) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()
        if (bitmap != null) ThumbnailCache.cache.put(url, bitmap) else ThumbnailCache.failed.add(url)
        bitmap
    } catch (e: Exception) {
        ThumbnailCache.failed.add(url)
        null
    }
}

/**
 * size:正方形の一辺。width・height を渡すと縦長などにできる。
 * alignTop:切り抜くときに上側を残す(選手の顔写真は顔が上にあるため)
 */
@Composable
fun RemoteThumbnail(
    url: String,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier,
    width: Dp = size,
    height: Dp = size,
    alignTop: Boolean = false
) {
    if (url.isBlank()) return
    val image by produceState<ImageBitmap?>(initialValue = ThumbnailCache.cache.get(url), url) {
        if (value == null) value = loadThumbnail(url)
    }
    val img = image ?: return
    Box(modifier = modifier.size(width, height).border(1.dp, LineGray).background(DividerGray)) {
        Image(
            bitmap = img,
            contentDescription = null,
            modifier = Modifier.size(width, height),
            contentScale = ContentScale.Crop,
            alignment = if (alignTop) Alignment.TopCenter else Alignment.Center
        )
    }
}
