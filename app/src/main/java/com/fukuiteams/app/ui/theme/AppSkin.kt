package com.fukuiteams.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.fukuiteams.app.data.TeamSelection
import com.fukuiteams.app.model.Team

/**
 * 着せ替え(アプリ全体の色)。メニューの「着せ替え」で選ぶ。
 * - 標準     : これまでどおりの新聞風(生成りの紙・墨・赤)
 * - 自動     : 画面の上のチームのボタンで選んだチームの色に合わせて変わる(「すべて」のときは標準)。
 *              試合・選手のタブは「すべて」が無く、いつもどれか1チームを表示するので、表示しているチームの色にする
 * - 各チーム : そのチームの色で固定
 * 選んだものはスマホに保存し、次にアプリを開いたときも同じ着せ替えで表示する。
 * ただし、掲示板の画面はブローウィンズの色、メニュー(とそこから開く使い方・更新履歴)は標準で固定する(screenOverride)。
 *
 * Color.kt の Ivory・Paper・NewsRed などは、ここで決めた色を返す(画面のどこでも、着せ替えがすぐに反映される)。
 */
enum class SkinMode(val label: String, val team: Team?) {
    STANDARD("標準(新聞)", null),
    AUTO("選んだチームに合わせる", null),
    BLOWINDS("ブローウィンズ", Team.BLOWINDS),
    RAC("丸岡RUCK", Team.RAC),
    UNITED("ユナイテッド", Team.UNITED),
    THUNDER("ブルーサンダー", Team.THUNDER)
}

/** 着せ替え1つ分の色 */
data class SkinPalette(
    /** 紙面の地色 */
    val ivory: Color,
    /** カード・囲み記事の地色 */
    val paper: Color,
    /** 墨(文字) */
    val ink: Color,
    /** 本文の補足の文字 */
    val inkSoft: Color,
    /** 細い罫線 */
    val lineGray: Color,
    /** 淡い地 */
    val dividerGray: Color,
    /** 強調の色(標準は新聞の赤) */
    val accent: Color,
    /** 上の帯(日時・最終更新)の地色 */
    val topBar: Color,
    /** 下のメニューの地色 */
    val navBar: Color,
    /** 帯の上の文字・アイコン */
    val onBar: Color,
    /** 下のメニューの選んでいないタブの文字・アイコン */
    val onBarSoft: Color,
    /** 下のメニューの選んでいるタブのアイコン */
    val navSelectedIcon: Color,
    /** 帯の上の「最終更新が古い」ときの文字 */
    val barWarn: Color,
    /** AWAYの札の色(HOMEは強調の色) */
    val away: Color,
    /** 上の帯の地色がチームの色か(スマホの一番上の帯も同じ色にする) */
    val coloredBars: Boolean
)

object AppSkin {
    private const val PREFS = "ui_prefs"
    private const val KEY = "skin_mode"

    var mode by mutableStateOf(SkinMode.STANDARD)
        private set

    /** 画面ごとに色を固定するとき(掲示板=ブローウィンズ、メニュー=標準)。null なら選んだ着せ替えのまま。NavGraph で決める */
    var screenOverride by mutableStateOf<SkinMode?>(null)

    /** いまの画面が1チームだけを表示する画面(試合・選手)か。NavGraph で決める。
     *  このときの「選んだチームに合わせる」は、一面で「すべて」を選んでいても、その画面で表示しているチームの色にする */
    var singleTeamScreen by mutableStateOf(false)
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        mode = SkinMode.values().firstOrNull { it.name == saved } ?: SkinMode.STANDARD
    }

    fun set(context: Context, value: SkinMode) {
        mode = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value.name).apply()
    }

    /** いま使うチーム(標準のときは null) */
    val team: Team?
        get() = when (val m = screenOverride ?: mode) {
            SkinMode.STANDARD -> null
            SkinMode.AUTO -> if (singleTeamScreen) TeamSelection.teamForSingle() else TeamSelection.current
            else -> m.team
        }

    /** いまの色 */
    val palette: SkinPalette
        get() = team?.let { teamPalette(it) } ?: STANDARD_PALETTE

    private val STANDARD_PALETTE = SkinPalette(
        ivory = Color(0xFFF3F0E8),
        paper = Color(0xFFFBF9F4),
        ink = Color(0xFF111111),
        inkSoft = Color(0xFF55504A),
        lineGray = Color(0xFFB9B2A4),
        dividerGray = Color(0xFFE6E0D3),
        accent = Color(0xFFC8102E),
        topBar = Color(0xFFF3F0E8),
        navBar = Color(0xFFE6E0D3),
        onBar = Color(0xFF111111),
        onBarSoft = Color(0xFF55504A),
        navSelectedIcon = Color(0xFFC8102E),
        barWarn = Color(0xFFC8102E),
        away = Color(0xFF2B4C7E),
        coloredBars = false
    )

    private val paletteCache = mutableMapOf<Team, SkinPalette>()

    /** チームの色をもとにした着せ替え。紙の色はチームの色をうっすら混ぜ、帯と強調の色はチームの色にする */
    private fun teamPalette(team: Team): SkinPalette = paletteCache.getOrPut(team) {
        val c = team.color
        val s = STANDARD_PALETTE
        SkinPalette(
            ivory = lerp(s.ivory, c, 0.07f),
            paper = lerp(s.paper, c, 0.03f),
            ink = s.ink,
            inkSoft = s.inkSoft,
            lineGray = lerp(s.lineGray, c, 0.22f),
            dividerGray = lerp(s.dividerGray, c, 0.14f),
            accent = c,
            topBar = c,
            navBar = c,
            onBar = Color.White,
            onBarSoft = Color.White.copy(alpha = 0.72f),
            navSelectedIcon = Color.White,
            // チームの色の帯の上でも読める明るい黄色
            barWarn = Color(0xFFFFD54F),
            // HOMEの札がチームの色になるので、AWAYは見分けやすい灰色にする
            away = Color(0xFF55504A),
            coloredBars = true
        )
    }
}
