package com.fukuiteams.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.fukuiteams.app.MainActivity
import com.fukuiteams.app.R
import com.fukuiteams.app.data.AlertsResult
import com.fukuiteams.app.data.GameResultsRepository
import com.fukuiteams.app.data.GamesRepository
import com.fukuiteams.app.data.InvitationAlertsRepository
import com.fukuiteams.app.data.hasMatchingInvite
import com.fukuiteams.app.data.isLikelyClosed
import com.fukuiteams.app.data.isUpcoming
import com.fukuiteams.app.model.Game
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ホーム画面のウィジェット「次の試合」。
 * 3チームの中で一番近い試合の日付・相手・HOME/AWAY・会場と、その試合向けの招待があれば「招待あり」を出す。
 * 更新のきっかけ: ウィジェットの定期更新(約1時間)・アプリの一面を開いたとき・招待の定期チェック。
 */
class NextGameWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** アプリ側から「ウィジェットも最新にして」と頼むときに呼ぶ。 */
        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, NextGameWidget::class.java))
            if (ids.isEmpty()) return
            scope.launch { runCatching { render(appContext, manager, ids) } }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val games = GamesRepository.loadForWidget(context)
            val results = GameResultsRepository.fetch()
            val next: Game? = games.filter { it.isUpcoming() && !results.containsKey(it.id) }.minByOrNull { it.sortKey }
            val invites = (InvitationAlertsRepository.fetch() as? AlertsResult.Success)?.items
                ?.filterNot { it.isLikelyClosed() } ?: emptyList()

            val views = RemoteViews(context.packageName, R.layout.widget_next_game)
            if (next == null) {
                views.setTextViewText(R.id.widget_team, "")
                views.setTextViewText(R.id.widget_date, "予定はまだ発表されていません")
                views.setTextViewText(R.id.widget_opponent, "")
                views.setTextViewText(R.id.widget_venue, "")
                views.setViewVisibility(R.id.widget_homeaway, View.GONE)
                views.setViewVisibility(R.id.widget_invite, View.GONE)
            } else {
                val md = next.dateLabel.split("/").drop(1).joinToString("/")
                views.setTextViewText(R.id.widget_team, next.team.displayName)
                views.setTextColor(R.id.widget_team, next.team.color.toArgb())
                views.setTextViewText(R.id.widget_date, "$md(${next.dayOfWeek}) ${next.timeLabel}")
                views.setTextViewText(R.id.widget_opponent, "vs ${next.opponent}")
                views.setTextViewText(R.id.widget_venue, next.venue)
                views.setViewVisibility(R.id.widget_homeaway, View.VISIBLE)
                views.setTextViewText(R.id.widget_homeaway, if (next.isHome) "HOME" else "AWAY")
                if (next.isHome) {
                    views.setInt(R.id.widget_homeaway, "setBackgroundResource", R.drawable.widget_home_bg)
                    views.setTextColor(R.id.widget_homeaway, Color.WHITE)
                } else {
                    views.setInt(R.id.widget_homeaway, "setBackgroundResource", R.drawable.widget_away_bg)
                    views.setTextColor(R.id.widget_homeaway, Color.parseColor("#2B4C7E"))
                }
                views.setViewVisibility(
                    R.id.widget_invite,
                    if (next.hasMatchingInvite(invites)) View.VISIBLE else View.GONE
                )
            }
            // タップでアプリを開く
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
