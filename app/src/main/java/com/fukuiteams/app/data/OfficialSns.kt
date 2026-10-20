package com.fukuiteams.app.data

import com.fukuiteams.app.model.Team

/*
 * 各チームの公式SNS(丸岡RUCKのインスタグラム以外は、各チームの公式サイトに載っているもの)。
 * メニューの「公式サイト・SNS」と、試合タブの「Xの投稿から予定を追加」で使う。
 */

/** 公式Xのアカウント */
val Team.officialXUrl: String
    get() = when (this) {
        Team.BLOWINDS -> "https://x.com/fukui_blowinds"
        Team.RAC -> "https://x.com/RUCK_PR"
        Team.UNITED -> "https://x.com/fkiutd_official"
        Team.THUNDER -> "https://x.com/fu_ei_bluethun"
    }

/** 公式インスタグラムのアカウント(無いチームは null)。
 *  丸岡RUCKは公式サイトに載っていないが、@fukuimaruokaruck が公式(2026年10月にアプリの作者が確認) */
val Team.officialInstagramUrl: String?
    get() = when (this) {
        Team.BLOWINDS -> "https://www.instagram.com/fukui_blowinds/"
        Team.RAC -> "https://www.instagram.com/fukuimaruokaruck/"
        Team.UNITED -> "https://www.instagram.com/fukuiunited_official/"
        Team.THUNDER -> "https://www.instagram.com/fukui_eiheiji_bluethunder/"
    }
