package com.fukuiteams.app.data

import com.fukuiteams.app.model.Team

/**
 * 各チームの過去の実績・歩み(一面の特集「過去の実績」で使う)。
 * 公式・公的な資料で確かめられたものだけを、出典のURLと一緒に手で入れている。
 * - 丸岡RUCK      : JFA 全日本女子フットサル選手権大会の歴代優勝チーム一覧・チーム紹介、Wikipedia
 * - ユナイテッド  : Wikipedia(福井ユナイテッドFC)
 * - ブローウィンズ: Wikipedia(福井ブローウィンズ)
 * - ブルーサンダー: Wikipedia(福井永平寺ブルーサンダー)
 * 新しい実績を足すときは、出典を確かめてからここに書き足す。
 */
data class HistoryItem(
    val team: Team,
    /** 年(例 2017) */
    val year: Int,
    /** 見出し(例「全日本女子フットサル選手権で初優勝」) */
    val title: String,
    /** 本文(1〜2文) */
    val detail: String,
    /** 出典の名前とURL */
    val source: String,
    val url: String
)

private const val JFA_WF_HISTORY = "https://www.jfa.jp/match/womens_futsal_2025/history.html"
private const val JFA_WF_TEAM_2026 = "https://www.jfa.jp/match/womens_futsal_2026/team_detail/08.html"
private const val WIKI_RUCK = "https://ja.wikipedia.org/wiki/%E7%A6%8F%E4%BA%95%E4%B8%B8%E5%B2%A1RUCK"
private const val WIKI_UNITED = "https://ja.wikipedia.org/wiki/%E7%A6%8F%E4%BA%95%E3%83%A6%E3%83%8A%E3%82%A4%E3%83%86%E3%83%83%E3%83%89FC"
private const val WIKI_BLOWINDS = "https://ja.wikipedia.org/wiki/%E7%A6%8F%E4%BA%95%E3%83%96%E3%83%AD%E3%83%BC%E3%82%A6%E3%82%A3%E3%83%B3%E3%82%BA"
private const val WIKI_THUNDER = "https://ja.wikipedia.org/wiki/%E7%A6%8F%E4%BA%95%E6%B0%B8%E5%B9%B3%E5%AF%BA%E3%83%96%E3%83%AB%E3%83%BC%E3%82%B5%E3%83%B3%E3%83%80%E3%83%BC"

val TEAM_HISTORY: List<HistoryItem> = listOf(
    // ---------- 丸岡RUCK ----------
    HistoryItem(Team.RAC, 1991, "丸岡RUCKレディースとして誕生", "チームの始まりは1991年。「ガラクタたちの集まり」という意味のRUCKの名で活動を始め、2016年に今の「福井丸岡RUCK」に名前を変えました。", "Wikipedia", WIKI_RUCK),
    HistoryItem(Team.RAC, 2012, "全日本女子フットサル選手権で3位", "第9回全日本女子フットサル選手権大会で3位に入りました(当時の名前は丸岡ラックレディース)。", "JFA 歴代優勝チーム一覧", JFA_WF_HISTORY),
    HistoryItem(Team.RAC, 2014, "全日本女子フットサル選手権で準優勝", "第11回全日本女子フットサル選手権大会で準優勝。この年は東海女子フットサルリーグでも優勝しています。", "JFA 歴代優勝チーム一覧・Wikipedia", JFA_WF_HISTORY),
    HistoryItem(Team.RAC, 2015, "全日本選手権で2年連続の準優勝", "第12回全日本女子フットサル選手権大会で2年続けて準優勝し、フェアプレー賞も受けました。東海女子フットサルリーグは2連覇。", "JFA 歴代優勝チーム一覧・Wikipedia", JFA_WF_HISTORY),
    HistoryItem(Team.RAC, 2016, "北信越女子リーグを5戦全勝で優勝", "この年から北信越女子フットサルリーグに移り、5戦全勝・57得点2失点で優勝。日本女子フットサルリーグのプレシーズン大会でも優勝しました。", "Wikipedia", WIKI_RUCK),
    HistoryItem(Team.RAC, 2017, "全日本女子フットサル選手権で初優勝", "第14回全日本女子フットサル選手権大会で初めて日本一に。フェアプレー賞もあわせて受けました。", "JFA 歴代優勝チーム一覧", JFA_WF_HISTORY),
    HistoryItem(Team.RAC, 2018, "全日本女子フットサル選手権で3位", "第15回全日本女子フットサル選手権大会で3位。この大会でもフェアプレー賞を受けました。", "JFA 歴代優勝チーム一覧", JFA_WF_HISTORY),
    HistoryItem(Team.RAC, 2018, "U-15の全国大会で5度目の優勝", "全日本女子ユース(U-15)フットサル選手権大会では、2012・2013・2016・2017・2018年と5度の優勝を飾っています。", "Wikipedia", WIKI_RUCK),
    HistoryItem(Team.RAC, 2025, "全日本女子フットサル選手権で3位", "第21回全日本女子フットサル選手権大会で3位に入りました。", "JFA 歴代優勝チーム一覧", JFA_WF_HISTORY),
    HistoryItem(Team.RAC, 2026, "全日本選手権に14年連続出場", "第22回全日本女子フットサル選手権大会に北信越代表として出場し、14年連続の全国大会出場となりました。", "JFA チーム紹介", JFA_WF_TEAM_2026),

    // ---------- ユナイテッド ----------
    HistoryItem(Team.UNITED, 2019, "福井ユナイテッドFCとして始動", "2018年に解散したサウルコス福井の後を受けて2019年に始動。この年、北信越リーグ1部で優勝し、全国地域チャンピオンズリーグでは4位に入りました。", "Wikipedia", WIKI_UNITED),
    HistoryItem(Team.UNITED, 2020, "北信越リーグ1部で優勝", "北信越フットボールリーグ1部で優勝(サウルコス時代から数えて4連覇)。", "Wikipedia", WIKI_UNITED),
    HistoryItem(Team.UNITED, 2021, "北信越リーグ1部で3連覇", "13勝1分2敗で北信越フットボールリーグ1部を制しました。", "Wikipedia", WIKI_UNITED),
    HistoryItem(Team.UNITED, 2023, "北信越リーグ1部の優勝を奪還", "北信越フットボールリーグ1部で優勝。全国地域チャンピオンズリーグの1次ラウンドは負けなし(1勝2分)でした。", "Wikipedia", WIKI_UNITED),
    HistoryItem(Team.UNITED, 2024, "14戦全勝で北信越リーグ1部を連覇", "クラブ史上初めての14戦全勝で北信越フットボールリーグ1部を連覇。全国地域チャンピオンズリーグでは決勝ラウンドで3位に入りました。", "Wikipedia", WIKI_UNITED),

    // ---------- ブローウィンズ ----------
    HistoryItem(Team.BLOWINDS, 2022, "チーム名「福井ブローウィンズ」が決まる", "2022年11月25日にチーム名が「福井ブローウィンズ」に決まりました。運営会社の福井バスケットボール株式会社は同じ年の5月に設立されています。", "Wikipedia", WIKI_BLOWINDS),
    HistoryItem(Team.BLOWINDS, 2023, "Bリーグ(B3)への入会が決まる", "2023年4月の最終審査に通り、4月20日にB3リーグへの入会が決まりました。初代ヘッドコーチは伊佐勉さんです。", "Wikipedia", WIKI_BLOWINDS),

    // ---------- ブルーサンダー ----------
    HistoryItem(Team.THUNDER, 1992, "前身の北陸電力ブルーサンダーがリーグに参戦", "前身の北陸電力ブルーサンダーは1992年からリーグで戦いました。チームのスローガン「不撓不屈」は今のチームにも受け継がれています。", "Wikipedia", WIKI_THUNDER),
    HistoryItem(Team.THUNDER, 2022, "新しいプロリーグへの参入が決まる", "2022年10月21日、2024年に始まる新しいプロリーグ(今のリーグH)への参入が決まりました。", "Wikipedia", WIKI_THUNDER),
    HistoryItem(Team.THUNDER, 2023, "福井永平寺ブルーサンダーが発足", "2023年4月25日に福井市で発足式を行い、地元の企業約50社の支えで新しいチームが動き出しました。前身の北陸電力ブルーサンダーは同じ年の3月に活動を終えています。", "Wikipedia", WIKI_THUNDER)
)
