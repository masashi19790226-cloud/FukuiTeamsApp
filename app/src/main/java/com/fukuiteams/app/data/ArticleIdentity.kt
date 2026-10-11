package com.fukuiteams.app.data

import java.net.URI

/** Stable grouping key for syndicated articles. Similar prefixes alone never merge stories. */
fun articleDuplicateKey(teamId: String, title: String, link: String, id: String): String {
    val canonicalUrl = runCatching {
        val uri = URI(link.trim())
        val host = uri.host?.lowercase() ?: ""
        val path = (uri.path ?: "").trimEnd('/')
        val query = uri.rawQuery.orEmpty().split('&')
            .filter { part ->
                val name = part.substringBefore('=').lowercase()
                part.isNotBlank() && !name.startsWith("utm_") && name !in setOf("fbclid", "gclid", "ref", "source")
            }
            .sorted()
            .joinToString("&")
        if (host.isBlank() || path.isBlank()) "" else "$host$path${if (query.isBlank()) "" else "?$query"}"
    }.getOrDefault("")
    if (canonicalUrl.isNotBlank()) return "url:$teamId:$canonicalUrl"

    val normalizedTitle = title.lowercase()
        .replace(Regex("[\\s\\u3000、。・「」『』【】()（）!！?？….,:：]"), "")
    return if (normalizedTitle.isBlank()) "id:$teamId:$id" else "title:$teamId:$normalizedTitle"
}
