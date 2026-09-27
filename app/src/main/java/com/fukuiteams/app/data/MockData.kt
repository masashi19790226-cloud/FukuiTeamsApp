package com.fukuiteams.app.data

import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team

/**
 * 【重要】現在、試合日程の本体は GitHub 上の data/games.json。アプリはそちらを読み込む(GamesRepository)。
 * このファイルは、初回起動で通信できないときだけ使う予備データ。日程の追加・変更は games.json で行う。
 *
 * 福井ブローウィンズの試合は、公式サイト(fukuiblowinds.com/schedule/list/)の
 * 2026年9月〜2027年4月の実際の日程を反映している。
 * 福井ユナイテッドは fukuiunited.co.jp/team/schedule.php、
 * 福井丸岡RUCKは ruck-fukui.com/schedules-results の実際の日程・結果を反映している
 * (どちらも今後、新しい試合が発表され次第、公式サイトを見て追記していく想定)。
 * 1月以降のブローウィンズの試合は開始時刻・会場が「時間未定」「調整中」のまま公式サイトに掲載されている。
 * ニュースは NewsAlertsRepository 経由でGoogleアラートの結果をその都度取得するため、
 * ここには静的なニュースデータは持たない。
 */
object MockData {

    val upcomingGames: List<Game> = listOf(
        // 2026年9月
        Game("bw01", Team.BLOWINDS, "岐阜", "2026/9/26", "土", "14:05", "OKBぎふ清流アリーナ", "販売中", "販売中", isHome = false, sortKey = "20260926-1405", resultPageUrl = "https://www.fukuiblowinds.com/game/?YMD=20260926&KEY=507167&DOUBLEHEADERFLAG=false&TAB=R"),
        Game("bw02", Team.BLOWINDS, "岐阜", "2026/9/27", "日", "14:05", "OKBぎふ清流アリーナ", "販売中", "販売中", isHome = false, sortKey = "20260927-1405"),
        // 2026年10月
        Game("bw03", Team.BLOWINDS, "金沢", "2026/10/3", "土", "15:05", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20261003-1505"),
        Game("bw04", Team.BLOWINDS, "金沢", "2026/10/4", "日", "14:05", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20261004-1405"),
        Game("bw05", Team.BLOWINDS, "横浜EX", "2026/10/10", "土", "17:35", "横浜武道館", "販売中", "販売中", isHome = false, sortKey = "20261010-1735"),
        Game("bw06", Team.BLOWINDS, "横浜EX", "2026/10/11", "日", "15:05", "横浜武道館", "販売中", "販売中", isHome = false, sortKey = "20261011-1505"),
        Game("bw07", Team.BLOWINDS, "山形", "2026/10/14", "水", "19:05", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20261014-1905"),
        Game("bw08", Team.BLOWINDS, "新潟", "2026/10/17", "土", "14:05", "アオーレ長岡", "販売中", "販売中", isHome = false, sortKey = "20261017-1405"),
        Game("bw09", Team.BLOWINDS, "新潟", "2026/10/18", "日", "14:05", "アオーレ長岡", "販売中", "販売中", isHome = false, sortKey = "20261018-1405"),
        Game("bw10", Team.BLOWINDS, "埼玉", "2026/10/24", "土", "15:05", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20261024-1505"),
        Game("bw11", Team.BLOWINDS, "埼玉", "2026/10/25", "日", "14:05", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20261025-1405"),
        Game("bw12", Team.BLOWINDS, "岐阜", "2026/10/31", "土", "14:05", "飛騨高山ビッグアリーナ", "販売中", "販売中", isHome = false, sortKey = "20261031-1405"),
        // 2026年11月
        Game("bw13", Team.BLOWINDS, "岐阜", "2026/11/1", "日", "14:05", "飛騨高山ビッグアリーナ", "販売中", "販売中", isHome = false, sortKey = "20261101-1405"),
        Game("bw14", Team.BLOWINDS, "鹿児島", "2026/11/4", "水", "19:05", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20261104-1905"),
        Game("bw15", Team.BLOWINDS, "岡山", "2026/11/7", "土", "15:05", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20261107-1505"),
        Game("bw16", Team.BLOWINDS, "岡山", "2026/11/8", "日", "14:05", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20261108-1405"),
        Game("bw17", Team.BLOWINDS, "新潟", "2026/11/21", "土", "15:05", "敦賀市総合運動公園体育館", "販売中", "販売中", isHome = true, sortKey = "20261121-1505"),
        Game("bw18", Team.BLOWINDS, "新潟", "2026/11/22", "日", "14:05", "敦賀市総合運動公園体育館", "販売中", "販売中", isHome = true, sortKey = "20261122-1405"),
        Game("bw19", Team.BLOWINDS, "静岡", "2026/11/28", "土", "14:05", "このはなアリーナ", "販売中", "販売中", isHome = false, sortKey = "20261128-1405"),
        Game("bw20", Team.BLOWINDS, "静岡", "2026/11/29", "日", "14:05", "このはなアリーナ", "販売中", "販売中", isHome = false, sortKey = "20261129-1405"),
        // 2026年12月:公式サイトのエラーで未取得
        // 2026年12月
        Game("bw20b", Team.BLOWINDS, "横浜EX", "2026/12/5", "土", "15:05", "越前市アイシンスポーツアリーナ", "販売中", "販売中", isHome = true, sortKey = "20261205-1505"),
        Game("bw20c", Team.BLOWINDS, "横浜EX", "2026/12/6", "日", "14:05", "越前市アイシンスポーツアリーナ", "販売中", "販売中", isHome = true, sortKey = "20261206-1405"),
        Game("bw20d", Team.BLOWINDS, "山形", "2026/12/9", "水", "19:05", "山形県総合運動公園", "販売中", "販売中", isHome = false, sortKey = "20261209-1905"),
        Game("bw20e", Team.BLOWINDS, "東京U", "2026/12/14", "月", "19:05", "国立代々木競技場 第二体育館", "販売中", "販売中", isHome = false, sortKey = "20261214-1905"),
        Game("bw20f", Team.BLOWINDS, "東京U", "2026/12/15", "火", "19:05", "国立代々木競技場 第二体育館", "販売中", "販売中", isHome = false, sortKey = "20261215-1905"),
        // 2027年1月(開始時刻は公式サイトにまだ掲載されていない)
        Game("bw21", Team.BLOWINDS, "鹿児島", "2027/1/20", "水", "時間未定", "鹿児島県総合体育センター体育館", "販売中", "販売中", isHome = false, sortKey = "20270120-0000"),
        Game("bw22", Team.BLOWINDS, "新潟", "2027/1/23", "土", "時間未定", "アオーレ長岡", "販売中", "販売中", isHome = false, sortKey = "20270123-0000"),
        Game("bw23", Team.BLOWINDS, "新潟", "2027/1/24", "日", "時間未定", "アオーレ長岡", "販売中", "販売中", isHome = false, sortKey = "20270124-0000"),
        Game("bw24", Team.BLOWINDS, "横浜EX", "2027/1/30", "土", "時間未定", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20270130-0000"),
        Game("bw25", Team.BLOWINDS, "横浜EX", "2027/1/31", "日", "時間未定", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20270131-0000"),
        // 2027年2月
        Game("bw26", Team.BLOWINDS, "八王子", "2027/2/3", "水", "時間未定", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20270203-0000"),
        Game("bw27", Team.BLOWINDS, "金沢", "2027/2/6", "土", "時間未定", "いしかわ総合スポーツセンター", "販売中", "販売中", isHome = false, sortKey = "20270206-0000"),
        Game("bw28", Team.BLOWINDS, "金沢", "2027/2/7", "日", "時間未定", "いしかわ総合スポーツセンター", "販売中", "販売中", isHome = false, sortKey = "20270207-0000"),
        Game("bw29", Team.BLOWINDS, "東京Z", "2027/2/13", "土", "時間未定", "調整中", "販売中", "販売中", isHome = false, sortKey = "20270213-0000"),
        Game("bw30", Team.BLOWINDS, "東京Z", "2027/2/14", "日", "時間未定", "調整中", "販売中", "販売中", isHome = false, sortKey = "20270214-0000"),
        Game("bw31", Team.BLOWINDS, "岐阜", "2027/2/20", "土", "時間未定", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20270220-0000"),
        Game("bw32", Team.BLOWINDS, "岐阜", "2027/2/21", "日", "時間未定", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20270221-0000"),
        Game("bw33", Team.BLOWINDS, "熊本", "2027/2/24", "水", "時間未定", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20270224-0000"),
        Game("bw34", Team.BLOWINDS, "FE名古屋", "2027/2/27", "土", "時間未定", "クロコくんホール", "販売中", "販売中", isHome = false, sortKey = "20270227-0000"),
        Game("bw35", Team.BLOWINDS, "FE名古屋", "2027/2/28", "日", "時間未定", "クロコくんホール", "販売中", "販売中", isHome = false, sortKey = "20270228-0000"),
        // 2027年3月
        Game("bw36", Team.BLOWINDS, "八王子", "2027/3/3", "水", "時間未定", "エスフォルタアリーナ八王子", "販売中", "販売中", isHome = false, sortKey = "20270303-0000"),
        Game("bw37", Team.BLOWINDS, "金沢", "2027/3/5", "金", "時間未定", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20270305-0000"),
        Game("bw38", Team.BLOWINDS, "金沢", "2027/3/6", "土", "時間未定", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20270306-0000"),
        Game("bw39", Team.BLOWINDS, "青森", "2027/3/13", "土", "時間未定", "マエダアリーナ", "販売中", "販売中", isHome = false, sortKey = "20270313-0000"),
        Game("bw40", Team.BLOWINDS, "青森", "2027/3/14", "日", "時間未定", "マエダアリーナ", "販売中", "販売中", isHome = false, sortKey = "20270314-0000"),
        Game("bw41", Team.BLOWINDS, "熊本", "2027/3/17", "水", "時間未定", "熊本県立総合体育館", "販売中", "販売中", isHome = false, sortKey = "20270317-0000"),
        Game("bw42", Team.BLOWINDS, "立川", "2027/3/20", "土", "時間未定", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20270320-0000"),
        Game("bw43", Team.BLOWINDS, "立川", "2027/3/21", "日", "時間未定", "セーレン・ドリームアリーナ", "販売中", "販売中", isHome = true, sortKey = "20270321-0000"),
        Game("bw44", Team.BLOWINDS, "横浜EX", "2027/3/27", "土", "時間未定", "横浜BUNTAI", "販売中", "販売中", isHome = false, sortKey = "20270327-0000"),
        Game("bw45", Team.BLOWINDS, "横浜EX", "2027/3/28", "日", "時間未定", "横浜BUNTAI", "販売中", "販売中", isHome = false, sortKey = "20270328-0000"),
        // 2027年4月
        Game("bw46", Team.BLOWINDS, "新潟", "2027/4/3", "土", "時間未定", "調整中", "販売中", "販売中", isHome = true, sortKey = "20270403-0000"),
        Game("bw47", Team.BLOWINDS, "新潟", "2027/4/4", "日", "時間未定", "調整中", "販売中", "販売中", isHome = true, sortKey = "20270404-0000"),
        Game("bw48", Team.BLOWINDS, "越谷", "2027/4/10", "土", "時間未定", "調整中", "販売中", "販売中", isHome = true, sortKey = "20270410-0000"),
        Game("bw49", Team.BLOWINDS, "越谷", "2027/4/11", "日", "時間未定", "調整中", "販売中", "販売中", isHome = true, sortKey = "20270411-0000"),
        Game("bw50", Team.BLOWINDS, "愛媛", "2027/4/24", "土", "時間未定", "調整中", "販売中", "販売中", isHome = true, sortKey = "20270424-0000"),
        Game("bw51", Team.BLOWINDS, "愛媛", "2027/4/25", "日", "時間未定", "調整中", "販売中", "販売中", isHome = true, sortKey = "20270425-0000"),

        // 福井ユナイテッド(fukuiunited.co.jp/team/schedule.php より、2026年9〜10月分)
        Game("un01", Team.UNITED, "新潟医療福祉大学FC", "2026/9/27", "日", "11:00", "新潟聖籠スポーツセンターアルビレッジEピッチ", "情報なし", "-", isHome = false, sortKey = "20260927-1100"),
        Game("un02", Team.UNITED, "富山新庄クラブ", "2026/10/4", "日", "13:30", "岩瀬スポーツ公園サッカー・ラグビー場", "情報なし", "-", isHome = false, sortKey = "20261004-1330"),

        // 福井丸岡RUCK(ruck-fukui.com/schedules-results より、2026年メットライフ生命日本女子フットサルリーグ)
        // 結果(スコア)も同ページに掲載されていたため、data/results.json 側にも反映済み。
        Game("rc01", Team.RAC, "フウガドールすみだレディース", "2026/6/14", "日", "14:00", "セーレン・ドリームアリーナ", "終了", "-", isHome = true, sortKey = "20260614-1400", resultPageUrl = "https://w-fleague.jp/score/result.html?gid=104694"),
        Game("rc02", Team.RAC, "アニージャ湘南", "2026/6/28", "日", "14:00", "セーレン・ドリームアリーナ", "終了", "-", isHome = true, sortKey = "20260628-1400", resultPageUrl = "https://w-fleague.jp/score/result.html?gid=104704"),
        Game("rc03", Team.RAC, "立川アスレティックFCレディース", "2026/7/5", "日", "16:00", "サイデン化学アリーナさいたま", "終了", "-", isHome = false, sortKey = "20260705-1600", resultPageUrl = "https://w-fleague.jp/score/result.html?gid=104709"),
        Game("rc04", Team.RAC, "アルコ神戸", "2026/7/19", "日", "14:00", "セーレン・ドリームアリーナ", "終了", "-", isHome = true, sortKey = "20260719-1400", resultPageUrl = "https://w-fleague.jp/score/result.html?gid=104714"),
        Game("rc05", Team.RAC, "バルドラール浦安ラス・ボニータス", "2026/7/25", "土", "11:00", "北海きたえーる", "終了", "-", isHome = false, sortKey = "20260725-1100", resultPageUrl = "https://w-fleague.jp/score/result.html?gid=104715"),
        Game("rc06", Team.RAC, "SWHレディース西宮", "2026/8/2", "日", "13:30", "ニューライフアリーナ龍ケ崎", "終了", "-", isHome = false, sortKey = "20260802-1330", resultPageUrl = "https://w-fleague.jp/score/result.html?gid=104723"),
        Game("rc07", Team.RAC, "ミネルバ宇部", "2026/8/23", "日", "14:00", "セーレン・ドリームアリーナ", "終了", "-", isHome = true, sortKey = "20260823-1400", resultPageUrl = "https://w-fleague.jp/score/result.html?gid=104729"),
        Game("rc08", Team.RAC, "流経大メニーナ龍ケ崎", "2026/9/5", "土", "13:30", "神戸市立中央体育館", "終了", "-", isHome = false, sortKey = "20260905-1330", resultPageUrl = "https://w-fleague.jp/score/result.html?gid=104731"),
        Game("rc09", Team.RAC, "さいたまサイコロ", "2026/9/12", "土", "15:00", "セーレン・ドリームアリーナ", "終了", "-", isHome = true, sortKey = "20260912-1500", resultPageUrl = "https://w-fleague.jp/score/result.html?gid=104737"),
        Game("rc10", Team.RAC, "エスポラーダ北海道イルネーヴェ", "2026/9/19", "土", "13:30", "西宮市立中央体育館", "終了", "-", isHome = false, sortKey = "20260919-1330", resultPageUrl = "https://w-fleague.jp/score/result.html?gid=104741")
    )
}
