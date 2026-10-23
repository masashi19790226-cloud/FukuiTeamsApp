package com.fukuiteams.app.data

import com.fukuiteams.app.model.Team

/**
 * 結婚などで名字が変わった選手(前の名前 → 今の名前)。scripts/name_changes.py と同じ内容。
 * 代表選出のお知らせなど、当時の名前で載っている記録を今の選手と結びつけるのに使う。
 */
object NameChanges {
    private val CHANGES: Map<Team, Map<String, String>> = mapOf(
        Team.RAC to mapOf(
            "北川夏奈" to "宮本夏奈",
            "北嶋笑" to "林笑"
        )
    )

    private fun key(name: String) = name.replace(Regex("[\\s　・]"), "")

    /** 前の名前なら今の名前(空白なし)、そうでなければそのまま */
    fun current(team: Team, name: String): String = CHANGES[team]?.get(key(name)) ?: name

    /** 今の名前に対する前の名前(空白なし) */
    fun former(team: Team, name: String): List<String> =
        CHANGES[team].orEmpty().filterValues { it == key(name) }.keys.toList()
}
