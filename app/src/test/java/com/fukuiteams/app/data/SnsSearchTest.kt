package com.fukuiteams.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnsSearchTest {
    private val team = SnsTeamSearchTerms(
        displayName = "福井ブローウィンズ",
        identifiers = listOf("福井ブローウィンズ", "ブローウィンズ", "#福井ブローウィンズ"),
        purposeTerms = mapOf(
            SnsSearchPurpose.LATEST to listOf("お知らせ", "発表"),
            SnsSearchPurpose.GAME to listOf("試合結果", "ホームゲーム"),
            SnsSearchPurpose.TICKET to listOf("チケット譲渡", "チケット求む")
        )
    )

    @Test
    fun xUrlUsesSelectedPurposesEncodesFreeTextAndSortsLatestFirst() {
        val url = buildSnsSearchUrl(
            SnsSearchPlatform.X,
            listOf(team),
            setOf(SnsSearchPurpose.TICKET, SnsSearchPurpose.LATEST),
            "#選手 12番"
        )
        assertTrue(url.startsWith("https://x.com/search?q="))
        assertTrue(url.endsWith("&f=live"))
        assertTrue(url.contains("%E3%83%96%E3%83%AD%E3%83%BC%E3%82%A6%E3%82%A3%E3%83%B3%E3%82%BA"))
        assertTrue(url.contains("%23%E9%81%B8%E6%89%8B%2012%E7%95%AA"))
        assertTrue(url.contains("%E3%83%81%E3%82%B1%E3%83%83%E3%83%88%E8%AD%B2%E6%B8%A1"))
        assertTrue(url.contains("%E3%81%8A%E7%9F%A5%E3%82%89%E3%81%9B"))
    }

    @Test
    fun noPurposeAndNoFreeTextStillSearchesTeam() {
        val url = buildSnsSearchUrl(SnsSearchPlatform.X, listOf(team), emptySet(), "")
        assertTrue(url.contains("%E7%A6%8F%E4%BA%95%E3%83%96%E3%83%AD%E3%83%BC%E3%82%A6%E3%82%A3%E3%83%B3%E3%82%BA"))
        assertFalse(url.contains("q=&"))
    }

    @Test
    fun instagramUrlDoesNotPretendToPassSearchTerms() {
        val url = buildSnsSearchUrl(
            SnsSearchPlatform.INSTAGRAM,
            listOf(team),
            setOf(SnsSearchPurpose.TICKET, SnsSearchPurpose.LATEST),
            "自由語"
        )
        assertTrue(url == "https://www.instagram.com/")
        assertFalse(url.contains("?q="))
    }

    @Test
    fun instagramPhraseUsesTeamAndOnlySelectedNewsPurpose() {
        val phrase = buildInstagramSearchTerms(listOf(team), setOf(SnsSearchPurpose.LATEST), "")
        assertTrue(phrase.startsWith("福井ブローウィンズ"))
        assertTrue(phrase.contains("お知らせ"))
        assertTrue(phrase.contains("発表"))
        assertFalse(phrase.contains("チケット"))
    }

    @Test
    fun instagramPhraseSupportsGameTicketMultiplePurposesAndFreeText() {
        val game = buildInstagramSearchTerms(listOf(team), setOf(SnsSearchPurpose.GAME), "")
        val ticket = buildInstagramSearchTerms(listOf(team), setOf(SnsSearchPurpose.TICKET), "")
        val combined = buildInstagramSearchTerms(
            listOf(team), setOf(SnsSearchPurpose.GAME, SnsSearchPurpose.TICKET), "#12番"
        )
        assertTrue(game.contains("試合結果"))
        assertFalse(game.contains("チケット"))
        assertTrue(ticket.contains("チケット譲渡"))
        assertFalse(ticket.contains("試合結果"))
        assertTrue(combined.contains("試合結果"))
        assertTrue(combined.contains("チケット譲渡"))
        assertTrue(combined.endsWith("#12番"))
    }

    @Test
    fun instagramPhraseWithoutPurposeStillIncludesTeamAndFreeText() {
        val phrase = buildInstagramSearchTerms(listOf(team), emptySet(), "12番")
        assertTrue(phrase.contains("福井ブローウィンズ"))
        assertTrue(phrase.endsWith("12番"))
        assertFalse(phrase.contains("お知らせ"))
    }

    @Test
    fun teamChangeProducesDifferentSearchTarget() {
        val otherTeam = team.copy(displayName = "福井ユナイテッド", identifiers = listOf("福井ユナイテッド", "#福井ユナイテッド"))
        val blowindsUrl = buildSnsSearchUrl(SnsSearchPlatform.X, listOf(team), emptySet(), "")
        val unitedUrl = buildSnsSearchUrl(SnsSearchPlatform.X, listOf(otherTeam), emptySet(), "")
        assertFalse(blowindsUrl == unitedUrl)
    }
}
