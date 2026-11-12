package com.fukuiteams.app.data

import com.fukuiteams.app.model.Team
import java.text.Normalizer

/**
 * 選手コール(会場で選手を呼ぶときのニックネーム)。ブローウィンズの選手コール(2026-27シーズン)から。Web版の data/calls.js と同じ。
 * 変わったときは、この一覧を直す(背番号・名前の一部・コール)。
 * 背番号が同じで名前の一部が合うときだけ使う(背番号の付け替えで別の選手にならないように)。
 */
data class PlayerCall(val number: String, val name: String, val call: String)
data class StaffCall(val role: String, val name: String, val call: String)
data class TeamCall(val label: String, val call: String)
data class TeamCalls(
    val label: String,
    val players: List<PlayerCall>,
    val staff: List<StaffCall>,
    val team: List<TeamCall>
)

val PLAYER_CALLS: Map<Team, TeamCalls> = mapOf(
    Team.BLOWINDS to TeamCalls(
        label = "選手コール 2026-27",
        players = listOf(
            PlayerCall("1", "藤永", "アキ"),
            PlayerCall("3", "塚本", "トモ"),
            PlayerCall("8", "岡本", "エイチ"),
            PlayerCall("10", "オーギル", "ギル"),
            PlayerCall("11", "内藤", "エイシン"),
            PlayerCall("12", "西野", "ヨウ"),
            PlayerCall("13", "川島", "セナ"),
            PlayerCall("14", "リスティッチ", "ドゥさん"),
            PlayerCall("15", "ブラウン", "CB(シービー)"),
            PlayerCall("17", "常田", "ツネ"),
            PlayerCall("21", "満田", "ジョー"),
            PlayerCall("41", "ウィリアムズ", "Devin(デヴィン)"),
            PlayerCall("73", "田渡", "しゅーてぃー"),
            PlayerCall("93", "高橋", "いくみ")
        ),
        staff = listOf(StaffCall("HC", "中堀", "じゅんき")),
        team = listOf(
            TeamCall("オフェンスコール", "レッツゴー ブローウィンズ!(パンパンパパパン)"),
            TeamCall("ディフェンスコール", "ディーフェンス!(パンパン)")
        )
    )
)

private fun callNorm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFKC).replace(Regex("[\\s・･]"), "")

/** その選手のコール(無ければ空) */
fun playerCallOf(team: Team, number: String, name: String): String {
    val n = callNorm(number).removePrefix("#")
    return PLAYER_CALLS[team]?.players
        ?.firstOrNull { it.number == n && callNorm(name).contains(callNorm(it.name)) }
        ?.call.orEmpty()
}
