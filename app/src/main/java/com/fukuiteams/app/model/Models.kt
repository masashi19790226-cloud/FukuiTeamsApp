package com.fukuiteams.app.model

import androidx.compose.ui.graphics.Color
import com.fukuiteams.app.ui.theme.TeamBlowinds
import com.fukuiteams.app.ui.theme.TeamRac
import com.fukuiteams.app.ui.theme.TeamUnited

enum class Team(val displayName: String, val initial: String, val color: Color, val officialSiteUrl: String?) {
    BLOWINDS("福井ブローウィンズ", "B", TeamBlowinds, "https://www.fukuiblowinds.com/"),
    RAC("福井丸岡ラック", "R", TeamRac, "https://ruck-fukui.com/"),
    UNITED("福井ユナイテッド", "U", TeamUnited, "https://fukuiunited.co.jp/")
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
    val sortKey: String
)
