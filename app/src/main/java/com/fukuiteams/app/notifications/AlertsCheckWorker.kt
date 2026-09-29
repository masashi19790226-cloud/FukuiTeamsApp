package com.fukuiteams.app.notifications

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fukuiteams.app.data.AlertsResult
import com.fukuiteams.app.data.InvitationAlertsRepository
import com.fukuiteams.app.data.NewsAlertsRepository
import com.fukuiteams.app.data.NotificationPrefsKeys
import com.fukuiteams.app.data.notificationDataStore
import com.fukuiteams.app.model.Team
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val UNIQUE_PERIODIC_WORK_NAME = "alerts_periodic_check"

/**
 * 1時間おきにバックグラウンドで実行され、無料招待・ニュースそれぞれについて
 * 前回チェック時より新しい(detected_atが新しい)ものが無いか確認し、
 * 設定でONになっているチーム・種類だけ通知する。
 */
class AlertsCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.notificationDataStore.data.first()

        fun teamEnabled(teamId: String): Boolean {
            val team = Team.values().find { it.name == teamId } ?: return true
            return prefs[NotificationPrefsKeys.teamKey(team.name)] ?: true
        }

        val inviteEnabled = prefs[NotificationPrefsKeys.kindKey("invite")] ?: true
        val newsEnabled = prefs[NotificationPrefsKeys.kindKey("news")] ?: true

        if (inviteEnabled) {
            checkAndNotify(
                result = InvitationAlertsRepository.fetch(),
                lastSeenKey = NotificationPrefsKeys.LAST_SEEN_INVITE_AT,
                notificationTitlePrefix = "新しい無料招待",
                notificationIdBase = 2000,
                openRoute = "radar/INVITE",
                teamEnabled = ::teamEnabled
            )
        }
        if (newsEnabled) {
            checkAndNotify(
                result = NewsAlertsRepository.fetch(),
                lastSeenKey = NotificationPrefsKeys.LAST_SEEN_NEWS_AT,
                notificationTitlePrefix = "新しいニュース",
                notificationIdBase = 3000,
                openRoute = "radar/ALL",
                teamEnabled = ::teamEnabled
            )
        }

        // ホーム画面のウィジェット(次の試合・招待あり)も定期的に最新にする
        com.fukuiteams.app.widget.NextGameWidget.requestUpdate(applicationContext)

        return Result.success()
    }

    private suspend fun checkAndNotify(
        result: AlertsResult,
        lastSeenKey: androidx.datastore.preferences.core.Preferences.Key<String>,
        notificationTitlePrefix: String,
        notificationIdBase: Int,
        openRoute: String,
        teamEnabled: (String) -> Boolean
    ) {
        if (result !is AlertsResult.Success || result.items.isEmpty()) return

        val lastSeenAt = applicationContext.notificationDataStore.data.first()[lastSeenKey] ?: ""
        val latest = result.items.maxOf { it.detectedAt }
        // 入れた直後・入れ直した直後の1回目は、これまでの情報をまとめて通知しないよう「ここまで見た」だけ記録する
        if (lastSeenAt.isEmpty()) {
            applicationContext.notificationDataStore.edit { it[lastSeenKey] = latest }
            return
        }
        val newItems = result.items.filter { it.detectedAt > lastSeenAt && teamEnabled(it.teamId) }

        newItems.take(5).forEachIndexed { index, item ->
            val team = Team.values().find { it.name == item.teamId }
            showAlertNotification(
                applicationContext,
                notificationIdBase + index,
                "$notificationTitlePrefix:${team?.displayName ?: item.teamId}",
                item.title,
                openRoute
            )
        }

        if (latest > lastSeenAt) {
            applicationContext.notificationDataStore.edit { it[lastSeenKey] = latest }
        }
    }
}

fun schedulePeriodicAlertsCheck(context: Context) {
    val request = PeriodicWorkRequestBuilder<AlertsCheckWorker>(60, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        UNIQUE_PERIODIC_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}
