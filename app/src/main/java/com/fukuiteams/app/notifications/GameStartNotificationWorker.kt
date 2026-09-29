package com.fukuiteams.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fukuiteams.app.data.GamesRepository
import com.fukuiteams.app.data.NotificationPrefsKeys
import com.fukuiteams.app.data.notificationDataStore
import com.fukuiteams.app.data.startEpochMillis
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val LEAD_TIME_MINUTES = 60L
private const val KEY_TITLE = "title"
private const val KEY_TEXT = "text"
private const val KEY_NOTIFICATION_ID = "notification_id"
private const val KEY_GAME_ID = "game_id"

class GameStartNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val text = inputData.getString(KEY_TEXT) ?: ""
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, 0)
        val gameId = inputData.getString(KEY_GAME_ID)
        showAlertNotification(applicationContext, notificationId, title, text, gameId?.let { "game_detail/$it" })
        return Result.success()
    }
}

/**
 * 設定でONになっているチームの、まだ始まっていない試合について
 * 「開始のLEAD_TIME_MINUTES分前」に通知が出るよう予約し直す。
 * 通知設定の変更時や、アプリ起動時に呼び出す想定。
 * 開始時刻が公式サイトにまだ掲載されていない試合(startEpochMillis()が0)はスキップする。
 */
suspend fun rescheduleGameStartNotifications(context: Context) {
    val prefs = context.notificationDataStore.data.first()
    val gamestartEnabled = prefs[NotificationPrefsKeys.kindKey("gamestart")] ?: true
    val now = System.currentTimeMillis()
    val workManager = WorkManager.getInstance(context)

    // 「試合開始前」をOFFにしたときは、すでに予約してある通知もすべて取り消す
    if (!gamestartEnabled) {
        GamesRepository.refresh(context).forEach { game -> workManager.cancelUniqueWork("gamestart_${game.id}") }
        return
    }

    GamesRepository.refresh(context).forEach { game ->
        val teamOn = prefs[NotificationPrefsKeys.teamKey(game.team.name)] ?: true
        val startMillis = game.startEpochMillis()
        val triggerAt = startMillis - LEAD_TIME_MINUTES * 60_000

        val uniqueName = "gamestart_${game.id}"
        if (!teamOn || startMillis == 0L || triggerAt <= now) {
            workManager.cancelUniqueWork(uniqueName)
            return@forEach
        }

        val delay = triggerAt - now
        val data = workDataOf(
            KEY_TITLE to "まもなく試合開始:${game.team.displayName}",
            KEY_TEXT to "${game.dateLabel}(${game.dayOfWeek})${game.timeLabel} vs ${game.opponent}・${game.venue}",
            // 4000〜4999 の番号にする(hashCode は負になることがあるので floorMod で必ず 0〜999 にする。
            // 以前は 3001〜3999 になることがあり、ニュースの通知(3000番台)を上書きしてしまうことがあった)
            KEY_NOTIFICATION_ID to (4000 + Math.floorMod(game.id.hashCode(), 1000)),
            KEY_GAME_ID to game.id
        )
        val request = OneTimeWorkRequestBuilder<GameStartNotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
    }
}
