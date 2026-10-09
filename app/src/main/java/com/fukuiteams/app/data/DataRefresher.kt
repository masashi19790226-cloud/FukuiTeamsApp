package com.fukuiteams.app.data

import android.content.Context
import com.fukuiteams.app.widget.NextGameWidget
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** 手動で更新したときに読み直した、すべての情報。 */
data class AllData(
    val results: Map<String, RemoteGameResult>,
    val invitations: AlertsResult,
    val news: AlertsResult,
    val previews: Map<String, GamePreview>,
    val status: DataStatus?,
    val players: Map<String, TeamPlayers>,
    val standings: Map<String, LeagueStandings>
)

/**
 * 画面の更新ボタン・下に引っ張る操作で、アプリのすべての情報をまとめて読み直す。
 * どの画面で更新しても、試合日程・結果・招待・ニュース・展望・選手・順位・試合情報ページ・特別な日・
 * 最終更新日時・ホーム画面のウィジェットがすべて新しくなる(読み込みは同時に進めるので、1つずつより早い)。
 */
object DataRefresher {
    suspend fun refreshAll(context: Context): AllData = coroutineScope {
        val appContext = context.applicationContext
        val games = async { GamesRepository.refresh(appContext) }
        val special = async { SpecialDaysRepository.refresh() }
        // パブリックビューイング(手で登録した分)
        val pv = async { PublicViewingsRepository.refresh() }
        val lp = async { GameLpRepository.fetch() }
        val results = async { GameResultsRepository.fetch() }
        val invitations = async { InvitationAlertsRepository.fetch() }
        val news = async { NewsAlertsRepository.fetch() }
        val previews = async { GamePreviewRepository.fetch() }
        val status = async { DataStatusRepository.fetch() }
        val players = async { PlayersRepository.fetch() }
        val standings = async { StandingsRepository.fetch() }
        games.await()
        // 「行く予定」にしていて始まった試合は、観戦方法を「現地観戦」として記録する
        runCatching { recordGoingAsOnSite(appContext, GamesRepository.games) }
        special.await()
        lp.await()
        pv.await()
        // ニュースの中のパブリックビューイングの記事も、試合と結び付けられるようにする
        val newsResult = news.await()
        (newsResult as? AlertsResult.Success)?.let { PublicViewingsRepository.updateFromNews(it.items) }
        // ホーム画面のウィジェットも最新にする
        NextGameWidget.requestUpdate(appContext)
        AllData(
            results = results.await(),
            invitations = invitations.await(),
            news = newsResult,
            previews = previews.await(),
            status = status.await(),
            players = players.await(),
            standings = standings.await()
        )
    }
}
