package com.fukuiteams.app.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.fukuiteams.app.ui.theme.TeamBlowinds
import com.fukuiteams.app.ui.theme.TeamRac
import com.fukuiteams.app.ui.theme.TeamUnited
import com.fukuiteams.app.ui.theme.TeamThunder
import com.fukuiteams.app.ui.theme.TeamVercosta
import com.fukuiteams.app.ui.theme.TeamVercostaInk

enum class Team(val displayName: String, val initial: String, val color: Color, val officialSiteUrl: String?) {
    BLOWINDS("福井ブローウィンズ", "B", TeamBlowinds, "https://www.fukuiblowinds.com/"),
    RAC("福井丸岡ラック", "R", TeamRac, "https://ruck-fukui.com/"),
    UNITED("福井ユナイテッド", "U", TeamUnited, "https://fukuiunited.co.jp/"),
    // 男子ハンドボール・リーグH
    THUNDER("福井永平寺ブルーサンダー", "T", TeamThunder, "https://www.fukuieiheijihand.co.jp/"),
    // 男子ホッケー・ホッケー日本リーグ サムライリーグD1(2026年11月に追加)
    VERCOSTA("ヴェルコスタ福井", "V", TeamVercosta, "https://vercosta.com/")
}

/** 白い紙の上に、チームの色で文字を書くときの色(ヴェルコスタの黄色は読みにくいので濃い黄土色。ほかのチームはチームの色) */
val Team.inkColor: Color
    get() = if (this == Team.VERCOSTA) TeamVercostaInk else color

/** チームの色で塗った上に載せる文字の色(ヴェルコスタの黄色の上は黒。ほかのチームは白) */
val Team.onColor: Color
    get() = if (this == Team.VERCOSTA) Color(0xFF111111) else Color.White

/** 色で塗った上に載せる文字の色(明るい色は黒、それ以外は白)。チームが分からず色だけ渡されたところで使う */
fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF111111) else Color.White

/** チームの短い呼び方(例「ブローウィンズ」「丸岡RUCK」「ユナイテッド」)。画面・記事のどこでもこれを使う */
val Team.shortName: String
    get() = when (this) {
        Team.BLOWINDS -> "ブローウィンズ"
        Team.RAC -> "丸岡RUCK"
        Team.UNITED -> "ユナイテッド"
        Team.THUNDER -> "ブルーサンダー"
        Team.VERCOSTA -> "ヴェルコスタ"
    }

data class Game(
    val id: String,
    val team: Team,
    val opponent: String,
    val dateLabel: String,
    val dayOfWeek: String,
    val timeLabel: String,
    val venue: String,
    val ticketStatus: String,
    val ticketSaleStart: String,
    val isHome: Boolean = true,
    val sortKey: String,
    // 公式サイトの結果ページURL(分かっているものだけ)。開いて自分でスコアを確認する用。
    val resultPageUrl: String? = null,
    // 自分で追加した予定(Xの投稿などから。この端末の中だけ)
    val userAdded: Boolean = false,
    // 自分で追加した予定の元の投稿のURL(無ければ null)
    val sourceUrl: String? = null,
    // トップチーム以外の試合の区分(例「U-20」「サテライト」)。トップチームの試合は null
    val category: String? = null,
    // 中立の会場での試合か(ヴェルコスタのセントラル開催など。ホームの試合は false)
    val neutral: Boolean = false,
    // 「7位決定戦」「入替戦」など(レギュラーシーズンの試合は null)
    val stage: String? = null
)

/** ホーム・アウェイ・中立の言葉(中立は neutral の試合だけ)。例:sideWord("ホーム", "アウェイ") */
fun Game.sideWord(home: String, away: String, neutralWord: String = "中立"): String =
    if (isHome) home else if (neutral) neutralWord else away

/**
 * トップチームの公式の試合か(U-20・サテライトなどの試合と、自分で追加した予定は false)。
 * 一面・特集・ウィジェットは、この試合だけを使う。試合タブの一覧には全部並べる。
 */
fun Game.isMainTeamGame(): Boolean = category.isNullOrBlank() && !userAdded
