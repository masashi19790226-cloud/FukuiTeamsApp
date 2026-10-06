package com.fukuiteams.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fukuiteams.app.data.BbsPrefs
import com.fukuiteams.app.data.BbsRepository
import com.fukuiteams.app.data.isNg
import com.fukuiteams.app.data.matchedNotifyWord
import java.util.concurrent.TimeUnit

private const val UNIQUE_BBS_KEYWORD_WORK_NAME = "bbs_keyword_check"

/** 掲示板のキーワード通知の通知番号(ほかの通知と重ならないよう 4000 番台) */
private const val BBS_NOTIFICATION_ID_BASE = 4000

/** 1回の確認で個別に通知する最大件数(それ以上はまとめて1件で知らせる) */
private const val MAX_INDIVIDUAL_NOTIFICATIONS = 3

/**
 * 掲示板のキーワード通知。約15分おきにバックグラウンドで掲示板の最新の投稿を読み、
 * 前回の確認より新しい投稿のうち、登録した言葉(名前・本文)を含むものを通知する。
 * NGワード・NGユーザーに当たる投稿は通知しない。
 * オンにした直後の1回目は、それまでの投稿をまとめて通知しないよう、確認位置だけ記録する。
 */
class BbsKeywordWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = BbsPrefs.load(applicationContext)
        if (!settings.notifyEnabled) return Result.success()

        val lastNo = BbsPrefs.notifiedNo(applicationContext)
        val posts = try {
            BbsRepository.fetchNewPostsForCheck(afterNo = lastNo)
        } catch (e: Exception) {
            // 通信できないときは次の回にまかせる
            return Result.success()
        }
        if (posts.isEmpty()) return Result.success()
        val newest = posts.maxOf { it.no }

        if (lastNo <= 0) {
            BbsPrefs.setNotifiedNo(applicationContext, newest)
            return Result.success()
        }

        val hits = posts
            .filter { it.no > lastNo && !it.isNg(settings) }
            .mapNotNull { p -> p.matchedNotifyWord(settings.notifyWords)?.let { word -> p to word } }
            .sortedBy { it.first.no }

        if (hits.size > MAX_INDIVIDUAL_NOTIFICATIONS) {
            val words = hits.map { it.second }.distinct().joinToString("・") { "「$it」" }
            val latest = hits.last().first
            showAlertNotification(
                applicationContext,
                BBS_NOTIFICATION_ID_BASE,
                "掲示板:${words}を含む投稿が${hits.size}件",
                "最新 No.${latest.no} ${latest.name}:${preview(latest.body)}",
                openRoute = "bbs"
            )
        } else {
            hits.forEach { (post, word) ->
                showAlertNotification(
                    applicationContext,
                    BBS_NOTIFICATION_ID_BASE + 1 + (post.no % 500),
                    "掲示板:「$word」を含む投稿",
                    "No.${post.no} ${post.name}:${preview(post.body)}",
                    openRoute = "bbs"
                )
            }
        }

        if (newest > lastNo) BbsPrefs.setNotifiedNo(applicationContext, newest)
        return Result.success()
    }

    /** 通知に出す本文の先頭部分(改行は空白にして、長いものは省略) */
    private fun preview(body: String): String {
        val oneLine = body.replace(Regex("\\s+"), " ").trim()
        return if (oneLine.length > 120) oneLine.take(120) + "…" else oneLine
    }
}

/**
 * 掲示板のキーワード通知の定期確認を、オンなら予約し(すでに予約済みならそのまま)、オフなら取り消す。
 * Android の決まりで、定期的な確認は15分おきが最短(電池の状態などで少し遅れることがある)。
 */
fun scheduleBbsKeywordCheck(context: Context, enabled: Boolean) {
    val wm = WorkManager.getInstance(context)
    if (!enabled) {
        wm.cancelUniqueWork(UNIQUE_BBS_KEYWORD_WORK_NAME)
        return
    }
    val request = PeriodicWorkRequestBuilder<BbsKeywordWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    wm.enqueueUniquePeriodicWork(UNIQUE_BBS_KEYWORD_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}
