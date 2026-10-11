package com.fukuiteams.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArticleIdentityTest {
    @Test
    fun similarTitlePrefixesRemainSeparate() {
        val first = articleDuplicateKey("BLOWINDS", "福井が快勝、次節へ", "", "1")
        val second = articleDuplicateKey("BLOWINDS", "福井が快勝、次節への展望", "", "2")
        assertNotEquals(first, second)
    }

    @Test
    fun trackingParametersDoNotSplitSameUrl() {
        val first = articleDuplicateKey("BLOWINDS", "記事", "https://example.com/story/1?utm_source=feed", "1")
        val second = articleDuplicateKey("BLOWINDS", "記事", "https://example.com/story/1/", "2")
        assertEquals(first, second)
    }

    @Test
    fun meaningfulQueryIdentifiersRemainDistinct() {
        val first = articleDuplicateKey("BLOWINDS", "記事", "https://example.com/story?id=1", "1")
        val second = articleDuplicateKey("BLOWINDS", "記事", "https://example.com/story?id=2", "2")
        assertNotEquals(first, second)
    }

    @Test
    fun missingTitleAndLinkFallBackToItemId() {
        val first = articleDuplicateKey("BLOWINDS", "", "", "1")
        val second = articleDuplicateKey("BLOWINDS", "", "", "2")
        assertNotEquals(first, second)
    }
}
