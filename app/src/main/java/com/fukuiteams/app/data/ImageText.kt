package com.fukuiteams.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * 画像(スクリーンショット・ポスターなど)の中の文字を読む。
 * Google の ML Kit(日本語の文字認識)を使い、読み取りはスマホの中だけで行う(画像をどこかへ送ることはない)。
 * 読み取り用のデータは Google Play 開発者サービスが用意する(アプリを入れたときに自動でダウンロード。
 * まだ届いていないときは、少し待ってからもう一度読み込む)。
 */

/** 画像の中の文字(行ごとに改行)。読めなければ、理由を書いた例外 */
suspend fun recognizeImageText(context: Context, uri: Uri): String {
    val image = withContext(Dispatchers.IO) {
        try {
            InputImage.fromFilePath(context.applicationContext, uri)
        } catch (e: Exception) {
            throw IllegalStateException("画像を開けませんでした")
        }
    }
    val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    return try {
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result.text) }
                .addOnFailureListener { e ->
                    val message = if (e is MlKitException && e.errorCode == MlKitException.UNAVAILABLE) {
                        "文字を読み取るためのデータを準備しています。少し待ってから、もう一度読み込んでください"
                    } else {
                        "画像の文字を読み取れませんでした"
                    }
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(message))
                }
        }
    } finally {
        recognizer.close()
    }
}

/** 画面に出す用の、小さくした画像(長い辺が maxSize ほど)。読めなければ null */
suspend fun loadPreviewBitmap(context: Context, uri: Uri, maxSize: Int = 900): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.applicationContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}
