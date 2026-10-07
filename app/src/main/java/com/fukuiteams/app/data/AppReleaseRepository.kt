package com.fukuiteams.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 配布ページ(GitHub Releases)で公開している最新版。メニューの「最新版の入手」で使う。
 * 公開は GitHub Actions の「Run workflow」で「Releasesへ公開する」にチェックしたときだけなので、
 * 自分でビルドした版のほうが新しいこともある。
 */
const val RELEASES_PAGE_URL = "https://github.com/masashi19790226-cloud/FukuiTeamsApp/releases/latest"
private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/masashi19790226-cloud/FukuiTeamsApp/releases/latest"

/** version は「2.152」の形(先頭の v は除く)。 */
data class AppRelease(val version: String, val publishedAt: java.time.Instant?)

object AppReleaseRepository {
    /** 読めない・まだ公開していないときは null */
    suspend fun fetchLatest(): AppRelease? = withContext(Dispatchers.IO) {
        try {
            val c = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) {
                c.disconnect()
                return@withContext null
            }
            val text = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val o = JSONObject(text)
            val tag = o.optString("tag_name").removePrefix("v").trim()
            if (tag.isBlank()) return@withContext null
            val published = o.optString("published_at").takeIf { it.isNotBlank() }?.let {
                runCatching { java.time.Instant.parse(it) }.getOrNull()
            }
            AppRelease(tag, published)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 「2.152」と「2.150」のような版を比べる(a が新しければ正の数)。数字にできない部分は0として扱う。
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }
}
