package com.fukuiteams.app.data

import com.fukuiteams.app.model.Team

/**
 * 各チームの過去の実績・歩み(一面の特集「過去の実績」で使う)。
 * 公式・公的な資料で確かめられたものだけを、出典のURLと一緒に手で入れている。
 * - 丸岡RUCK      : JFA 全日本女子フットサル選手権大会の歴代優勝チーム一覧・チーム紹介、Wikipedia
 * - ユナイテッド  : Wikipedia(福井ユナイテッドFC)
 * - ブローウィンズ: Wikipedia(福井ブローウィンズ)、Bリーグ公式のクラブページ(シーズンごとの成績)
 * - ブルーサンダー: Wikipedia(福井永平寺ブルーサンダー)、リーグH公式の星取表
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
private const val BLEAGUE_CLUB_BLOWINDS = "https://www.bleague.jp/club_detail/?TeamID=2891"
private const val ALLCONNECT_B3 = "https://all-connect.co.jp/?p=2089"
private const val ALLCONNECT_ARENA = "https://all-connect.co.jp/news/%E7%A6%8F%E4%BA%95%E3%82%A2%E3%83%AA%E3%83%BC%E3%83%8A%E3%81%8B%E3%82%89%E6%96%B0%E3%81%97%E3%81%84%E6%96%87%E5%8C%96%E3%81%AE%E9%86%B8%E6%88%90%E3%82%92%E3%80%82%E3%80%80%E3%80%80%E3%80%80%E3%80%80/"
private const val ALLCONNECT_ATTEND = "https://all-connect.co.jp/?p=3186"
private const val JFA_EMPEROR_2025 = "https://jfa.jp/match/emperorscup_2025/team_detail/05.html"
private const val JFA_EMPEROR_2026 = "https://jfa.jp/match/emperorscup_2026/team_detail/85.html"
private const val LEAGUEH_MATRIX = "https://leagueh.jp/rankings/matrix/"
private const val WIKI_THUNDER = "https://ja.wikipedia.org/wiki/%E7%A6%8F%E4%BA%95%E6%B0%B8%E5%B9%B3%E5%AF%BA%E3%83%96%E3%83%AB%E3%83%BC%E3%82%B5%E3%83%B3%E3%83%80%E3%83%BC"

val TEAM_HISTORY: List<HistoryItem> = listOf(
    // ---------- 丸岡RUCK ----------
    HistoryItem(Team.RAC, 1991, "丸岡RUCKレディースとして誕生", "チームの始まりは1991年。「ガラクタたちの集まり」という意味のRUCKの名で活動を始め、2016年に今の「福井丸岡RUCK」に名前を変えました。", "Wikipedia", WIKI_RUCK),
    HistoryItem(Team.RAC, 2012, "全日本女子フットサル選手権で3位", "第9回全日本女子フットサル選手権大会で3位に入りました(当時の名前は丸岡ラックレディース)。", "JFA 歴代優勝チーム一覧", JFA_WF_HISTORY),
    HistoryItem(Team.RAC, 2014, "全日本女子フットサル選手権で準優勝", "第11回全日本女子フットサル選手権大会で準優勝。この年は東海女子フットサルリーグでも優勝しています。", "JFA 歴代優勝チーム一覧・Wikipedia", JFA_WF_HISTORY),
    HistoryItem(Team.RAC, 2015, "全日本選手権で2年連続の準優勝", "第12回全日本女子フットサル選手権大会で2年続けて準優勝し、フェアプレー賞も受けました。東海女子フットサルリーグは2連覇。", "JFA 歴代優勝チーム一覧・Wikipedia", JFA_WF_HISTORY),
    HistoryItem(Team.RAC, 2016, "北信越女子リーグを5戦全勝で優勝", "この年から北信越女子フットサルリーグに移り、5戦全勝・57得点2失点で優勝。日本女子フットサルリーグのプレシーズン大会でも優勝しました。", "Wikipedia", WIKI_RUCK),
    HistoryItem(Team.RAC, 2017, "日本女子フットサルリーグに参加", "2017年に始まった日本女子フットサルリーグ(女子Fリーグ)に最初のシーズンから参加しました。この年は北信越女子フットサルリーグでも優勝しています。", "Wikipedia", WIKI_RUCK),
    HistoryItem(Team.RAC, 2017, "全日本女子フットサル選手権で初優勝", "第14回全日本女子フットサル選手権大会で初めて日本一に。フェアプレー賞もあわせて受けました。", "JFA 歴代優勝チーム一覧", JFA_WF_HISTORY),
    HistoryItem(Team.RAC, 2018, "全日本女子フットサル選手権で3位", "第15回全日本女子フットサル選手権大会で3位。この大会でもフェアプレー賞を受けました。", "JFA 歴代優勝チーム一覧", JFA_WF_HISTORY),
    HistoryItem(Team.RAC, 2018, "U-15の全国大会で5度目の優勝", "全日本女子ユース(U-15)フットサル選手権大会では、2012・2013・2016・2017・2018年と5度の優勝を飾っています。", "Wikipedia", WIKI_RUCK),
    HistoryItem(Team.RAC, 2025, "全日本女子フットサル選手権で3位", "第21回全日本女子フットサル選手権大会で3位に入りました。", "JFA 歴代優勝チーム一覧", JFA_WF_HISTORY),
    HistoryItem(Team.RAC, 2026, "全日本選手権に14年連続出場", "第22回全日本女子フットサル選手権大会に北信越代表として出場し、14年連続の全国大会出場となりました。", "JFA チーム紹介", JFA_WF_TEAM_2026),

    // ---------- ユナイテッド ----------
    HistoryItem(Team.UNITED, 2019, "福井ユナイテッドFCとして始動", "2018年に解散したサウルコス福井の後を受けて2019年に始動。この年、北信越リーグ1部で優勝し、全国地域チャンピオンズリーグでは4位に入りました。", "Wikipedia", WIKI_UNITED),
    HistoryItem(Team.UNITED, 2020, "北信越リーグ1部で優勝", "北信越フットボールリーグ1部で優勝(サウルコス時代から数えて4連覇)。", "Wikipedia", WIKI_UNITED),
    HistoryItem(Team.UNITED, 2021, "北信越リーグ1部で3連覇", "13勝1分2敗で北信越フットボールリーグ1部を制しました。", "Wikipedia", WIKI_UNITED),
    HistoryItem(Team.UNITED, 2022, "北信越リーグ1部は3位", "9勝1分4敗の3位で、サウルコス時代から続いていた連続優勝が止まりました。", "Wikipedia", WIKI_UNITED),
    HistoryItem(Team.UNITED, 2023, "北信越リーグ1部の優勝を奪還", "北信越フットボールリーグ1部で優勝。全国地域チャンピオンズリーグの1次ラウンドは負けなし(1勝2分)でした。", "Wikipedia", WIKI_UNITED),
    HistoryItem(Team.UNITED, 2024, "14戦全勝で北信越リーグ1部を連覇", "クラブ史上初めての14戦全勝で北信越フットボールリーグ1部を連覇。全国地域チャンピオンズリーグでは決勝ラウンドで3位に入りました。", "Wikipedia", WIKI_UNITED),
    HistoryItem(Team.UNITED, 2025, "天皇杯でJFLのHonda FCに勝利", "サウルコス時代から14年連続17度目の天皇杯。1回戦でJFLのHonda FCに1-0で勝ち、2回戦でJ2のヴァンフォーレ甲府に1-2で敗れました。", "JFA 天皇杯チーム紹介・福井ユナイテッド公式 試合結果", JFA_EMPEROR_2025),
    HistoryItem(Team.UNITED, 2026, "15年連続で天皇杯に出場", "福井県代表決定戦の決勝で坂井フェニックスに2-1で逆転勝ちし、15年連続18度目の天皇杯へ。1回戦でAC長野パルセイロに1-2で敗れました。", "JFA 天皇杯チーム紹介・福井ユナイテッド公式 試合結果", JFA_EMPEROR_2026),

    // ---------- ブローウィンズ ----------
    HistoryItem(Team.BLOWINDS, 2020, "福井にプロバスケットボールクラブをつくる動きが始まる", "2020年11月30日、県内の有志が「一般社団法人福井県プロバスケットボールクラブ」を設立し、12月10日に記者会見を開きました。最初のB3参入申請(2022-23シーズン)は審査に通りませんでした。", "Wikipedia", WIKI_BLOWINDS),
    HistoryItem(Team.BLOWINDS, 2022, "チーム名「福井ブローウィンズ」が決まる", "2022年11月25日にチーム名が「福井ブローウィンズ」に決まりました。運営会社の福井バスケットボール株式会社は同じ年の5月に設立されています。", "Wikipedia", WIKI_BLOWINDS),
    HistoryItem(Team.BLOWINDS, 2023, "Bリーグ(B3)への入会が決まる", "2023年4月の最終審査に通り、4月20日にB3リーグへの入会が決まりました。初代ヘッドコーチは伊佐勉さんです。", "Wikipedia", WIKI_BLOWINDS),
    HistoryItem(Team.BLOWINDS, 2023, "マスコット「BOOZ」が誕生", "Bリーグ1年目の2023年に、チームのマスコットキャラクター「BOOZ」が生まれました。", "Wikipedia", WIKI_BLOWINDS),
    HistoryItem(Team.BLOWINDS, 2023, "B3初年度で完全優勝、B2昇格", "Bリーグ1年目の2023-24シーズンは、B3レギュラーシーズンを46勝4敗(勝率.920)で優勝。B3で初めての31連勝を記録し、ホームゲームは負けなしでした。プレーオフも勝ち抜いて完全優勝し、次のシーズンからB2で戦いました。", "ALL CONNECT(2024年5月20日)", ALLCONNECT_B3),
    HistoryItem(Team.BLOWINDS, 2023, "B3初年度の観客数はリーグトップ", "2023-24シーズンのホームゲームの観客数は合わせて69,673人で、B3でいちばん多い数字でした。", "ALL CONNECT(2024年5月20日)", ALLCONNECT_B3),
    HistoryItem(Team.BLOWINDS, 2024, "B2初年度は32勝28敗でプレーオフへ", "2024-25シーズンはB2で32勝28敗。B2プレーオフに進みました(0勝2敗)。", "Bリーグ公式 クラブページ", BLEAGUE_CLUB_BLOWINDS),
    HistoryItem(Team.BLOWINDS, 2025, "新しい福井アリーナの計画が進む", "福井市の東公園に計画されている「福井アリーナ」は、2028年秋ごろの完成を見込み、完成後はブローウィンズの本拠地になる予定です(2025年10月の発表時点)。", "ALL CONNECT(2025年10月8日)", ALLCONNECT_ARENA),
    HistoryItem(Team.BLOWINDS, 2025, "クラブ史上最多の4,579人", "2025-26シーズンは、ホーム開幕戦(10月11日)の4,076人に続き、平日の11月2日に4,579人とクラブ史上最多の観客を集めました。11月19日時点のホームの平均入場者数は4,062人で、新しいトップリーグ「B.PREMIER」の参入基準の一つ(平均4,000人以上)に届きました。", "ALL CONNECT(2025年11月19日)", ALLCONNECT_ATTEND),
    HistoryItem(Team.BLOWINDS, 2025, "B2で35勝、2年続けてプレーオフへ", "2025-26シーズンはB2で35勝25敗。2年続けてB2プレーオフに進み、1勝しました(1勝2敗)。", "Bリーグ公式 クラブページ", BLEAGUE_CLUB_BLOWINDS),
    HistoryItem(Team.BLOWINDS, 2026, "新しいリーグ「B.ONE」で戦う", "Bリーグの新しい仕組みが始まった2026-27シーズンは、B.ONEで戦っています。", "Bリーグ公式 クラブページ", BLEAGUE_CLUB_BLOWINDS),

    // ---------- ブルーサンダー ----------
    HistoryItem(Team.THUNDER, 1992, "前身の北陸電力ブルーサンダーがリーグに参戦", "前身の北陸電力ブルーサンダーは1992年からリーグで戦いました。チームのスローガン「不撓不屈」は今のチームにも受け継がれています。", "Wikipedia", WIKI_THUNDER),
    HistoryItem(Team.THUNDER, 2022, "新しいプロリーグへの参入が決まる", "2022年10月21日、2024年に始まる新しいプロリーグ(今のリーグH)への参入が決まりました。", "Wikipedia", WIKI_THUNDER),
    HistoryItem(Team.THUNDER, 2022, "運営会社ができ、マスコットも発表", "2022年7月に運営会社「株式会社永平寺町ハンドボールまちづくり推進機構」が設立され、10月にはチームのマスコットも発表されました。", "Wikipedia", WIKI_THUNDER),
    HistoryItem(Team.THUNDER, 2024, "リーグHの初年度を戦う", "2024年9月に始まった新しいリーグ「リーグH」の最初のシーズン(2024-25)に参加し、リーグ戦は12勝14敗でした。", "リーグH公式 星取表", LEAGUEH_MATRIX),
    HistoryItem(Team.THUNDER, 2025, "リーグH2年目を戦う", "リーグHの2年目(2025-26シーズン)は、リーグ戦を8勝2分16敗で終えました。", "リーグH公式 星取表", LEAGUEH_MATRIX),
    HistoryItem(Team.THUNDER, 2023, "福井永平寺ブルーサンダーが発足", "2023年4月25日に福井市で発足式を行い、地元の企業約50社の支えで新しいチームが動き出しました。前身の北陸電力ブルーサンダーは同じ年の3月に活動を終えています。", "Wikipedia", WIKI_THUNDER)
)
