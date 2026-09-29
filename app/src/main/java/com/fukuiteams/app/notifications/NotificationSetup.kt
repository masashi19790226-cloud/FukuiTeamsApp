package com.fukuiteams.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

const val ALERTS_CHANNEL_ID = "fukui_teams_alerts"

/** 通知を押したときに開く画面(画面遷移のルート)を MainActivity に渡すための名前 */
const val EXTRA_OPEN_ROUTE = "open_route"

fun ensureAlertsNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            ALERTS_CHANNEL_ID,
            "無料招待・ニュース・試合開始通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "無料招待の新着、ニュースの新着、試合開始前のお知らせをまとめて通知します"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

/**
 * 通知を出す。openRoute を渡すと、通知を押したときにアプリのその画面を開く
 * (例:"radar/INVITE" = トピックの招待、"game_detail/bw03" = その試合)。
 * 渡さないときはアプリの一面を開く。
 */
fun showAlertNotification(context: Context, notificationId: Int, title: String, text: String, openRoute: String? = null) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
    }
    val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(openAppIntent(context, notificationId, openRoute))
        .build()
    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

/** 通知を押したときにアプリを開くための PendingIntent。前の画面は閉じて、指定の画面から始める。 */
private fun openAppIntent(context: Context, requestCode: Int, openRoute: String?): PendingIntent {
    val intent = Intent(context, com.fukuiteams.app.MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        if (openRoute != null) putExtra(EXTRA_OPEN_ROUTE, openRoute)
    }
    return PendingIntent.getActivity(
        context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
