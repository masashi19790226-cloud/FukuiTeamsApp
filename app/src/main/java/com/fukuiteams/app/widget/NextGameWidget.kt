package com.fukuiteams.app.widget

import com.fukuiteams.app.data.SpecialDaysRepository
import com.fukuiteams.app.data.specialDay
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
import com.fukuiteams.app.model.Team
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ホーム画面のウィジェット「次の試合」。
 * 3チームそれぞれの次の試合(日付・相手・会場・HOME/AWAY)を1行ずつ出し、その試合向けの招待があれば「招待あり」を出す。
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

    /** ウィジェットの1行分(チームごと)の部品。 */
    private class Row(
        val team: Team, val label: String,
        val bar: Int, val teamLabel: Int, val date: Int, val homeAway: Int, val opponent: Int, val invite: Int
    )

    companion object {
        private val ROWS = listOf(
            Row(Team.BLOWINDS, "ブローウィンズ", R.id.bar_b, R.id.team_b, R.id.date_b, R.id.homeaway_b, R.id.opp_b, R.id.invite_b),
            Row(Team.RAC, "丸岡RUCK", R.id.bar_r, R.id.team_r, R.id.date_r, R.id.homeaway_r, R.id.opp_r, R.id.invite_r),
            Row(Team.UNITED, "ユナイテッド", R.id.bar_u, R.id.team_u, R.id.date_u, R.id.homeaway_u, R.id.opp_u, R.id.invite_u)
        )

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
            val specialDays = SpecialDaysRepository.refresh()
            val invites = (InvitationAlertsRepository.fetch() as? AlertsResult.Success)?.items
                ?.filterNot { it.isLikelyClosed() } ?: emptyList()

            val views = RemoteViews(context.packageName, R.layout.widget_next_game)
            // 3チームそれぞれの次の試合を1行ずつ
            ROWS.forEach { row ->
                val next: Game? = games
                    .filter { it.team == row.team && it.isUpcoming() && !results.containsKey(it.id) }
                    .minByOrNull { it.sortKey }
                views.setInt(row.bar, "setBackgroundColor", row.team.color.toArgb())
                views.setTextViewText(row.teamLabel, row.label)
                views.setTextColor(row.teamLabel, row.team.color.toArgb())
                if (next == null) {
                    views.setTextViewText(row.date, "次の試合は未発表")
                    views.setTextViewText(row.opponent, "")
                    views.setViewVisibility(row.homeAway, View.GONE)
                    views.setViewVisibility(row.invite, View.GONE)
                } else {
                    val md = next.dateLabel.split("/").drop(1).joinToString("/")
                    views.setTextViewText(row.date, "$md(${next.dayOfWeek}) ${next.timeLabel}")
                    views.setTextViewText(row.opponent, "vs ${next.opponent}・${next.venue}")
                    views.setViewVisibility(row.homeAway, View.VISIBLE)
                    views.setTextViewText(row.homeAway, if (next.isHome) "HOME" else "AWAY")
                    // HOME は赤、AWAY は紺の塗りつぶしに白抜き文字(小さくても見分けやすいように)
                    views.setInt(
                        row.homeAway, "setBackgroundResource",
                        if (next.isHome) R.drawable.widget_home_bg else R.drawable.widget_away_bg
                    )
                    views.setTextColor(row.homeAway, Color.WHITE)
                    // 右端の印:コラボ企画など特別な日は「★コラボ」、招待があれば「招待あり」
                    val marks = listOfNotNull(
                        if (next.specialDay(specialDays) != null) "★コラボ" else null,
                        if (next.hasMatchingInvite(invites)) "招待あり" else null
                    )
                    views.setTextViewText(row.invite, marks.joinToString("・"))
                    views.setViewVisibility(row.invite, if (marks.isEmpty()) View.GONE else View.VISIBLE)
                }
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
