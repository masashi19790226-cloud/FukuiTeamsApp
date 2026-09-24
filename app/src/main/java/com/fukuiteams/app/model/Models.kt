package com.fukuiteams.app.model

import androidx.compose.ui.graphics.Color
import com.fukuiteams.app.ui.theme.TeamBlowinds
import com.fukuiteams.app.ui.theme.TeamRac
import com.fukuiteams.app.ui.theme.TeamUnited

enum class Team(val displayName: String, val initial: String, val color: Color) {
    BLOWINDS("福井ブローウィンズ", "B", TeamBlowinds),
    RAC("福井丸岡ラック", "R", TeamRac),
    UNITED("福井ユナイテッド", "U", TeamUnited)
}

data class NewsItem(
    val team: Team,
    val source: String,
    val postedAgo: String,
    val headline: String
)

data class Game(
    val id: String,
    val team: Team,
    val opponent: String,
    val dateLabel: String,
    val dayOfWeek: String,
    val timeLabel: String,
    val venue: String,
    val hasInvitation: Boolean,
    val ticketStatus: String,
    val ticketSaleStart: String
)

enum class InvitationSource(val label: String) {
    OFFICIAL("公式発信"),
    AD("広告経由"),
    PERSONAL("個人譲渡")
}

enum class InvitationStatus {
    OPEN, CLOSED_NO_RESULT, RESULT_LINKED
}

data class InvitationEvent(
    val id: String,
    val team: Team,
    val source: InvitationSource,
    val title: String,
    val fromWho: String,
    val detectedAt: String,
    val relatedGameLabel: String,
    val deadlineLabel: String,
    val status: InvitationStatus,
    val winners: String? = null,
    val applicants: String? = null,
    val competitionRate: String? = null
)
