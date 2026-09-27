package com.fukuiteams.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 過去の試合に登録した写真。端末内(アプリ専用の保存領域)だけに保存し、外部には送らない。
 * 保存先: filesDir/photos/<試合ID>/<時刻>.jpg と、一覧用の縮小版 <時刻>_t.jpg
 */
object GamePhotos {

    /** 写真を追加・削除するたびに増える。画面はこれを見て読み込み直す。 */
    var version by mutableStateOf(0)
        private set

    private const val MAX_SIDE = 1600
    private const val THUMB_SIDE = 360

    private fun dir(context: Context, gameId: String) =
        File(context.filesDir, "photos/$gameId").apply { mkdirs() }

    /** 登録済みの写真(本体)を古い順に。 */
    fun list(context: Context, gameId: String): List<File> =
        File(context.filesDir, "photos/$gameId")
            .listFiles { f -> f.name.endsWith(".jpg") && !f.name.endsWith("_t.jpg") }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun thumbOf(photo: File): File = File(photo.parentFile, photo.name.removeSuffix(".jpg") + "_t.jpg")

    /** ギャラリーで選んだ写真を縮小して保存する。 */
    suspend fun add(context: Context, gameId: String, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val target = dir(context, gameId)
        uris.forEachIndexed { i, uri ->
            try {
                val bitmap = decodeScaled(context, uri, MAX_SIDE) ?: return@forEachIndexed
                val name = "${System.currentTimeMillis()}_$i"
                File(target, "$name.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                val thumb = scaleDown(bitmap, THUMB_SIDE)
                File(target, "${name}_t.jpg").outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            } catch (e: Exception) {
                // 読めない形式などはスキップ
            }
        }
        withContext(Dispatchers.Main) { version++ }
    }

    fun delete(photo: File) {
        photo.delete()
        thumbOf(photo).delete()
        version++
    }

    private fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        // スマホで撮った写真は向きの情報(EXIF)を持っているので、それに合わせて回転させる
        val rotation = try {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        val rotated = if (rotation != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
        } else decoded
        return scaleDown(rotated, maxSide)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longSide
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }
}
