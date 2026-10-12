package com.fukuiteams.app.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class SnsSearchPlatform { X, INSTAGRAM }

enum class SnsSearchPurpose(val label: String) {
    LATEST("最新の話題・公式発表"),
    GAME("試合・結果・観戦"),
    PLAYER("選手の話題"),
    TICKET("チケット・譲渡"),
    INVITE("無料招待・キャンペーン"),
    EVENT_GOODS("イベント・グッズ"),
    FAN("ファンの感想・その他")
}

data class SnsTeamSearchTerms(
    val displayName: String,
    val identifiers: List<String>,
    val purposeTerms: Map<SnsSearchPurpose, List<String>>
)

/** Build an external search URL without fetching or displaying SNS content in the app. */
fun buildSnsSearchUrl(
    platform: SnsSearchPlatform,
    teams: List<SnsTeamSearchTerms>,
    purposes: Set<SnsSearchPurpose>,
    freeText: String
): String {
    val teamIdentifiers = teams
        .flatMap { it.identifiers }
        .filter(String::isNotBlank)
        .distinct()
    val teamExpression = teamIdentifiers.joinToString(" OR ")
        .let { terms -> if (teamIdentifiers.size > 1) "($terms)" else terms }

    val purposeTerms = SnsSearchPurpose.entries
        .filter { it in purposes }
        .flatMap { purpose -> teams.flatMap { it.purposeTerms[purpose].orEmpty() } }
        .filter(String::isNotBlank)
        .distinct()
        .take(6)
    val purposeExpression = purposeTerms.joinToString(" OR ")
        .let { terms -> if (terms.contains(" OR ")) "($terms)" else terms }
    val words = when (platform) {
        SnsSearchPlatform.X -> listOf(teamExpression, purposeExpression, freeText.trim())
            .filter(String::isNotBlank)
            .joinToString(" ")
        // Instagram does not document a query-bearing app deep link. Do not place
        // terms on an unsupported URL and imply that the Instagram app will use them.
        SnsSearchPlatform.INSTAGRAM -> "https://www.instagram.com/"
    }
    if (words.isBlank()) return ""
    if (platform == SnsSearchPlatform.INSTAGRAM) return words
    val encoded = URLEncoder.encode(words, StandardCharsets.UTF_8.name()).replace("+", "%20")

    return when (platform) {
        SnsSearchPlatform.X -> "https://x.com/search?q=$encoded&f=live"
        SnsSearchPlatform.INSTAGRAM -> words
    }
}

/**
 * Terms to show and copy for a manual Instagram search. Instagram's official
 * help describes searching inside the app, but does not document a query URL
 * or app intent that accepts a search string.
 */
fun buildInstagramSearchTerms(
    teams: List<SnsTeamSearchTerms>,
    purposes: Set<SnsSearchPurpose>,
    freeText: String
): String {
    val teamNames = teams.map { it.displayName.trim() }.filter(String::isNotBlank).distinct()
    val purposeTerms = SnsSearchPurpose.entries
        .filter { it in purposes }
        .flatMap { purpose -> teams.flatMap { it.purposeTerms[purpose].orEmpty().take(2) } }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    return (teamNames + purposeTerms + freeText.trim().takeIf(String::isNotBlank).orEmpty())
        .filter(String::isNotBlank)
        .distinct()
        .joinToString(" ")
}
