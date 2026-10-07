package com.fukuiteams.app.notifications

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fukuiteams.app.data.AlertsResult
import com.fukuiteams.app.data.BirthdaysRepository
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

        // OFFの種類も「ここまで見た」だけは進めておく。
        // (進めないと、あとでONに戻したときに、OFFの間の情報がまとめて通知されてしまう)
        checkAndNotify(
            result = InvitationAlertsRepository.fetch(),
            lastSeenKey = NotificationPrefsKeys.LAST_SEEN_INVITE_AT,
            notificationTitlePrefix = "新しい無料招待",
            notificationIdBase = 2000,
            openRoute = "radar/INVITE",
            teamEnabled = ::teamEnabled,
            notify = inviteEnabled
        )
        checkAndNotify(
            result = NewsAlertsRepository.fetch(),
            lastSeenKey = NotificationPrefsKeys.LAST_SEEN_NEWS_AT,
            notificationTitlePrefix = "新しいニュース",
            notificationIdBase = 3000,
            openRoute = "radar/ALL",
            teamEnabled = ::teamEnabled,
            notify = newsEnabled
        )

        // 選手の誕生日(その日の朝8時以降に1回だけ。設定でオフにできる)
        runCatching { notifyBirthdays(::teamEnabled, prefs[NotificationPrefsKeys.kindKey("birthday")] ?: true) }

        // 試合開始前の通知も予約し直す(アプリを開かなくても、あとから決まった開始時刻や日程の変更に合わせる)
        runCatching { rescheduleGameStartNotifications(applicationContext) }

        // ホーム画面のウィジェット(次の試合・招待あり)も定期的に最新にする
        com.fukuiteams.app.widget.NextGameWidget.requestUpdate(applicationContext)

        return Result.success()
    }

    private suspend fun notifyBirthdays(teamEnabled: (String) -> Boolean, enabled: Boolean) {
        if (!enabled) return
        val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Tokyo"))
        if (now.hour < 8) return
        val today = now.toLocalDate()
        val last = applicationContext.notificationDataStore.data.first()[NotificationPrefsKeys.LAST_BIRTHDAY_DATE]
        if (last == today.toString()) return
        val all = BirthdaysRepository.fetch()
        if (all.isEmpty()) return  // 読み込めなかったときは、次の回にもう一度
        val todays = all.filter { it.isBirthdayOn(today) && (it.team == null || teamEnabled(it.team.name)) }
        todays.take(3).forEachIndexed { index, p ->
            showAlertNotification(
                applicationContext,
                4800 + index,
                "今日は${p.team?.displayName ?: ""}の${p.label}選手の誕生日",
                "${p.ageOn(today)}歳の誕生日です。おめでとうございます!",
                "home"
            )
        }
        applicationContext.notificationDataStore.edit { it[NotificationPrefsKeys.LAST_BIRTHDAY_DATE] = today.toString() }
    }

    private suspend fun checkAndNotify(
        result: AlertsResult,
        lastSeenKey: androidx.datastore.preferences.core.Preferences.Key<String>,
        notificationTitlePrefix: String,
        notificationIdBase: Int,
        openRoute: String,
        teamEnabled: (String) -> Boolean,
        notify: Boolean
    ) {
        if (result !is AlertsResult.Success || result.items.isEmpty()) return

        val lastSeenAt = applicationContext.notificationDataStore.data.first()[lastSeenKey] ?: ""
        val latest = result.items.maxOf { it.detectedAt }
        // 入れた直後・入れ直した直後の1回目は、これまでの情報をまとめて通知しないよう「ここまで見た」だけ記録する
        if (lastSeenAt.isEmpty()) {
            applicationContext.notificationDataStore.edit { it[lastSeenKey] = latest }
            return
        }
        val newItems = if (notify) {
            result.items.filter { it.detectedAt > lastSeenAt && teamEnabled(it.teamId) }
        } else {
            emptyList()
        }

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
