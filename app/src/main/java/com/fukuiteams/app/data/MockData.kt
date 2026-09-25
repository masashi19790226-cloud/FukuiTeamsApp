package com.fukuiteams.app.data

import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.InvitationEvent
import com.fukuiteams.app.model.InvitationSource
import com.fukuiteams.app.model.InvitationStatus
import com.fukuiteams.app.model.NewsItem
import com.fukuiteams.app.model.Team

/**
 * 開発中に画面を確認するためのダミーデータ。
 * 実装時は、ニュース収集・招待検知バックエンドから取得したデータに置き換える。
 */
object MockData {

    val news = listOf(
        NewsItem(Team.RAC, "公式サイト", "12分前", "次節メンバー発表、新加入選手が practice に初参加"),
        NewsItem(Team.BLOWINDS, "公式X", "38分前", "来週のホームゲーム、限定グッズ販売のお知らせ"),
        NewsItem(Team.UNITED, "Instagram", "2時間前", "アウェイ戦のハイライト動画を公開しました")
    )

    val upcomingGames = listOf(
        Game(
            id = "g1",
            team = Team.BLOWINDS,
            opponent = "信州ブレイブウォリアーズ",
            dateLabel = "9/28",
            dayOfWeek = "日",
            timeLabel = "14:00",
            venue = "福井県営体育館",
            hasInvitation = true,
            ticketStatus = "販売中",
            ticketSaleStart = "9/1 10:00"
        ),
        Game(
            id = "g2",
            team = Team.UNITED,
            opponent = "新潟医療福祉大学FC",
            dateLabel = "9/27",
            dayOfWeek = "土",
            timeLabel = "11:00",
            venue = "新潟聖籠スポーツセンター",
            hasInvitation = false,
            ticketStatus = "販売前",
            ticketSaleStart = "9/20 18:00"
        ),
        Game(
            id = "g3",
            team = Team.RAC,
            opponent = "金沢フットサルクラブ",
            dateLabel = "9/29",
            dayOfWeek = "月",
            timeLabel = "19:30",
            venue = "丸岡体育館",
            hasInvitation = false,
            ticketStatus = "販売中",
            ticketSaleStart = "9/15 10:00"
        )
    )

    val openInvitations = listOf(
        InvitationEvent(
            id = "i1",
            team = Team.BLOWINDS,
            source = InvitationSource.OFFICIAL,
            title = "9/28ホームゲーム ペア招待券プレゼント",
            fromWho = "@fukui_blowinds",
            detectedAt = "9/23 09:10",
            relatedGameLabel = "9/28 vs 信州ブレイブウォリアーズ",
            deadlineLabel = "あと18時間",
            status = InvitationStatus.OPEN
        ),
        InvitationEvent(
            id = "i2",
            team = Team.UNITED,
            source = InvitationSource.AD,
            title = "スポンサー企業タイアップ 観戦チケットプレゼント",
            fromWho = "井上金庫グループ(Instagram広告)",
            detectedAt = "9/22 20:40",
            relatedGameLabel = "9/27 vs 新潟医療福祉大学FC",
            deadlineLabel = "あと3日",
            status = InvitationStatus.OPEN
        ),
        InvitationEvent(
            id = "i3",
            team = Team.RAC,
            source = InvitationSource.PERSONAL,
            title = "急用のため観戦チケット1枚譲ります",
            fromWho = "Xユーザー投稿",
            detectedAt = "9/23 07:55",
            relatedGameLabel = "試合まで5日",
            deadlineLabel = "試合まで5日",
            status = InvitationStatus.OPEN
        )
    )

    val archivedInvitations = listOf(
        InvitationEvent(
            id = "i0",
            team = Team.BLOWINDS,
            source = InvitationSource.OFFICIAL,
            title = "開幕戦 ペア招待券プレゼント",
            fromWho = "@fukui_blowinds",
            detectedAt = "8/30 10:00",
            relatedGameLabel = "9/5 vs アルティーリ千葉",
            deadlineLabel = "締切済み",
            status = InvitationStatus.RESULT_LINKED,
            winners = "3組",
            applicants = "142件",
            competitionRate = "約47倍"
        ),
        InvitationEvent(
            id = "i0b",
            team = Team.UNITED,
            source = InvitationSource.AD,
            title = "地元ラジオ局タイアップ招待企画",
            fromWho = "FM福井(Instagram広告)",
            detectedAt = "8/20 12:00",
            relatedGameLabel = "8/24 vs JAPAN.S.C.",
            deadlineLabel = "終了・結果未反映",
            status = InvitationStatus.CLOSED_NO_RESULT
        )
    )
}
