package com.fukuiteams.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fukuiteams.app.model.Team
import org.json.JSONObject

/**
 * 以前チームにいた選手(「あの選手は今」)。GitHub Actions(scripts/alumni.py)が standings.json の "ALUMNI" に入れる。
 * 今は丸岡RUCKだけ(公式サイトの退団・移籍のお知らせと、女子Fリーグ公式の各クラブの選手名簿から)。
 */
data class AlumniPlayer(
    val team: Team,
    val name: String,
    /** チームにいたときの背番号 */
    val number: String,
    /** 退団・移籍のお知らせの日付(2024-03-25) */
    val left: String,
    /** 「退団」か「移籍」 */
    val kind: String,
    /** お知らせに書かれた移籍先(無ければ空) */
    val dest: String,
    /** お知らせのページ */
    val link: String,
    /** 今の所属(女子Fリーグのクラブにいれば、そのクラブ。分からなければ空) */
    val now: String,
    val nowNumber: String,
    val nowUrl: String,
    /** 今季の得点(リーグの得点ランキングに載っていれば) */
    val goals: Int?,
    /** その選手の写真(退団・移籍のお知らせ・今いるクラブ・日本代表のお知らせから。分からなければ空) */
    val photo: String = "",
    /** 退団のお知らせが無い元選手の説明(例「2021年に日本代表候補に選ばれた元選手」。ふつうは空) */
    val note: String = ""
)

object Alumni {
    var players by mutableStateOf<List<AlumniPlayer>>(emptyList())
        private set

    /** standings.json の "ALUMNI" を読む(StandingsRepository から呼ぶ) */
    fun update(o: JSONObject?) {
        val arr = o?.optJSONArray("players") ?: return
        players = (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            val team = Team.values().firstOrNull { it.name == p.optString("team") } ?: return@mapNotNull null
            AlumniPlayer(
                team = team,
                name = p.optString("name"),
                number = p.optString("number"),
                left = p.optString("left"),
                kind = p.optString("kind"),
                dest = p.optString("dest"),
                link = p.optString("link"),
                now = p.optString("now"),
                nowNumber = p.optString("now_number"),
                nowUrl = p.optString("now_url"),
                goals = if (p.has("goals") && !p.isNull("goals")) p.optInt("goals") else null,
                photo = p.optString("photo"),
                note = p.optString("note")
            )
        }.filter { it.name.isNotBlank() }
    }
}
