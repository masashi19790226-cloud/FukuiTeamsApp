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

/** 制作者(追い風はBOOZ)が作った応援プレイリスト(Spotify。公式のものではない)。無いチームは null */
/** 制作者(追い風はBOOZ)が作った、試合の待ち時間に遊べるクイズのWebアプリ「ふくスポLab」(Web版の teams.js の FUKUSPO_LAB_URL) */
const val FUKUSPO_LAB_URL = "https://fukuspo-lab.pages.dev/"

val Team.fanPlaylistUrl: String?
    get() = when (this) {
        Team.BLOWINDS -> "https://open.spotify.com/playlist/5Pfdndtu2Czn7TvM9tDvf9"
        else -> null
    }
