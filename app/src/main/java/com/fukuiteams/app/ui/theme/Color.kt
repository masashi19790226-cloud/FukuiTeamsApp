package com.fukuiteams.app.ui.theme

import androidx.compose.ui.graphics.Color

// ベース(スポーツ新聞風:生成りの紙・墨・赤)。
// 着せ替え(AppSkin.kt)で色が変わるので、どれも「いま選んでいる着せ替えの色」を返す。
// 標準の着せ替えの色は、これまでと同じ(紙面 F3F0E8・カード FBF9F4・墨 111111・補足 55504A・罫線 B9B2A4・淡い地 E6E0D3・赤 C8102E)
val Ivory: Color get() = AppSkin.palette.ivory             // 紙面の地色
val Paper: Color get() = AppSkin.palette.paper             // カード・囲み記事の地色(紙面より少し明るい)
val Ink: Color get() = AppSkin.palette.ink                 // 墨
val InkSoft: Color get() = AppSkin.palette.inkSoft         // 本文の補足
val LineGray: Color get() = AppSkin.palette.lineGray       // 罫線(細)
val DividerGray: Color get() = AppSkin.palette.dividerGray // 淡い地
val White = Color(0xFFFFFFFF)

// 新聞の赤(速報・勝ち・強調)。着せ替えでチームの色にしたときは、そのチームの色
val NewsRed: Color get() = AppSkin.palette.accent

// アプリ全体のアクセント(無料招待・CTA用)。新聞の赤にそろえる
val Accent: Color get() = NewsRed

// 上の帯・下のメニュー(着せ替えでチームの色にしたときは、帯をチームの色で塗る)
val TopBarColor: Color get() = AppSkin.palette.topBar
val NavBarColor: Color get() = AppSkin.palette.navBar
val OnBarColor: Color get() = AppSkin.palette.onBar
val OnBarSoftColor: Color get() = AppSkin.palette.onBarSoft
val NavSelectedIconColor: Color get() = AppSkin.palette.navSelectedIcon
val BarWarnColor: Color get() = AppSkin.palette.barWarn
// AWAYの札の色
val AwayColor: Color get() = AppSkin.palette.away

// チームカラー(ワイヤーフレームで確定した実際のチームカラーに準拠)。着せ替えでは変わらない
val TeamBlowinds = Color(0xFF0F3460) // 福井ブローウィンズ:濃紺
val TeamUnited = Color(0xFF2541B2)   // 福井ユナイテッド:鮮やかな青
val TeamRac = Color(0xFFC23A1F)      // 福井丸岡ラック:赤とオレンジの中間色(濃いめ)
// 福井永平寺ブルーサンダー(ハンドボール):空色寄りの青(ブローウィンズの濃紺・ユナイテッドの青と見分けられる色)
val TeamThunder = Color(0xFF0089C8)
