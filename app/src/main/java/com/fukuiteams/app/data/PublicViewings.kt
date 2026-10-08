package com.fukuiteams.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

/**
 * パブリックビューイング(PV・観戦会)。主にアウェイ戦の日に、お店や自治体などで試合を中継して応援する催し。
 * ・手で登録した分:GitHub の scripts/public_viewings.json(Xなどで見つけたものを書き足す。push すればアプリの作り直しは不要)
 * ・自動で見つけた分:ニュース(公式サイト・Googleアラート・Bingニュース・自治体のページ)のうち、見出しにPVの言葉があるもの
 * 試合の日付(手で登録した分は日付、自動の分は見出しに書かれた日付)で試合と結び付け、
 * 一面の「次の試合」・試合タブの一覧と詳細・トピックに出す。
 */
private const val PUBLIC_VIEWINGS_URL =
    "https://raw.githubusercontent.com/masashi19790226-cloud/FukuiTeamsApp/main/scripts/public_viewings.json"

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

/** 見出しに含まれていればPVの記事とみなす言葉 */
val PUBLIC_VIEWING_RE = Regex("パブリックビューイング|パブリック・ビューイング|パブリックビューング|観戦会|応援会|ＰＶ|(?<![A-Za-z])PV(?![A-Za-z])")

data class PublicViewing(
    val team: Team,
    /** 開催日(=試合の日)。分からなければ null */
    val date: LocalDate?,
    /** 時間(例「18:00〜」)。分からなければ空 */
    val time: String,
    /** 会場(例「〇〇ビル1階 △△」)。分からなければ空 */
    val place: String,
    val address: String,
    /** 参加費・申込みの要否など */
    val note: String,
    /** 告知の出どころのURL(Xのポスト・お知らせのページ) */
    val url: String,
    /** 告知の出どころ(例「X(@xxxx)」「ブローウィンズ公式」) */
    val source: String,
    /** どの試合のPVか(相手の名前。分からなければ空) */
    val opponent: String,
    /** 自動で見つけたもの(見出しだけで、場所などは記事を開いて確かめる) */
    val auto: Boolean,
    /** 自動で見つけたものの見出し(手で登録した分は空) */
    val title: String = ""
) {
    /** 「10/17(土)」の形。日付が分からなければ空 */
    fun dateLabel(): String {
        val d = date ?: return ""
        val week = listOf("月", "火", "水", "木", "金", "土", "日")[d.dayOfWeek.value - 1]
        return "${d.monthValue}/${d.dayOfMonth}($week)"
    }

    /** 終わったか(開催日の翌日以降)。日付が分からないものは終わっていない扱い */
    fun isPast(today: LocalDate = LocalDate.now(JST)): Boolean = date != null && date.isBefore(today)

    /** 一覧に出す1行(例「10/17(土) 18:00〜 〇〇ビル1階 △△」)。自動の分は見出し */
    val summary: String
        get() = if (auto) title
        else listOf(dateLabel(), time, place).filter { it.isNotBlank() }.joinToString(" ")
}

object PublicViewingsRepository {
    /** 手で登録した分 */
    private var manual by mutableStateOf<List<PublicViewing>>(emptyList())

    /** ニュースから自動で見つけた分 */
    private var fromNews by mutableStateOf<List<PublicViewing>>(emptyList())

    /** 手で登録した分を一度でも読み込んだか */
    var loaded by mutableStateOf(false)
        private set

    /** すべてのPV(手で登録した分が先。同じURLの自動の分は除く) */
    val all: List<PublicViewing>
        get() {
            val urls = manual.map { it.url }.filter { it.isNotBlank() }.toSet()
            return manual + fromNews.filter { it.url.isBlank() || it.url !in urls }
        }

    /** これからのPV(終わっていないもの)。日付の近い順(日付が分からないものは後ろ) */
    fun upcoming(team: Team? = null): List<PublicViewing> =
        all.filter { (team == null || it.team == team) && !it.isPast() }
            .sortedWith(compareBy<PublicViewing> { it.date == null }.thenBy { it.date })

    /** scripts/public_viewings.json を読み直す。失敗したら今の一覧のまま。 */
    suspend fun refresh(): List<PublicViewing> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(PUBLIC_VIEWINGS_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val array = JSONObject(text).optJSONArray("items")
            manual = (0 until (array?.length() ?: 0)).mapNotNull { i ->
                val o = array?.optJSONObject(i) ?: return@mapNotNull null
                val team = Team.values().find { it.name == o.optString("team") } ?: return@mapNotNull null
                PublicViewing(
                    team = team,
                    date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull(),
                    time = o.optString("time"),
                    place = o.optString("place"),
                    address = o.optString("address"),
                    note = o.optString("note"),
                    url = o.optString("url"),
                    source = o.optString("source"),
                    opponent = o.optString("opponent"),
                    auto = false
                )
            }
            loaded = true
        } catch (e: Exception) {
            // 読めなければ今の一覧のまま
        }
        all
    }

    /**
     * ニュースの中から、見出しにPVの言葉がある記事をPVとして加える(公式サイト・アラート・Bingニュース・自治体のページ)。
     * 日付は見出し・抜粋に書かれた月日(書かれていなければ null)。
     */
    fun updateFromNews(news: List<RemoteInvitationAlert>) {
        fromNews = news.filter { PUBLIC_VIEWING_RE.containsMatchIn(it.title) }
            .mapNotNull { n ->
                val team = Team.values().find { it.name == n.teamId } ?: return@mapNotNull null
                PublicViewing(
                    team = team,
                    date = n.mentionedDates().minOrNull(),
                    time = "",
                    place = "",
                    address = "",
                    note = "",
                    url = n.link,
                    source = n.sourceLabel(),
                    opponent = "",
                    auto = true,
                    title = n.title
                )
            }
            .distinctBy { it.url.ifBlank { it.title } }
    }
}

/** この試合のPV(同じチームで、日付が試合の日と同じもの。日付の分からない自動の分は、見出しに相手の名前があるもの) */
fun Game.publicViewings(): List<PublicViewing> {
    val parts = dateLabel.split("/").mapNotNull { it.trim().toIntOrNull() }
    val gameDate = if (parts.size >= 3) runCatching { LocalDate.of(parts[0], parts[1], parts[2]) }.getOrNull() else null
    return PublicViewingsRepository.all.filter { pv ->
        pv.team == team && when {
            pv.date != null -> pv.date == gameDate
            else -> opponent.isNotBlank() && (pv.title.contains(opponent) || pv.opponent == opponent)
        }
    }
}

/** Xで、このチームのパブリックビューイングの最新の投稿を探すURL */
fun publicViewingXSearchUrl(team: Team): String {
    val word = when (team) {
        Team.BLOWINDS -> "ブローウィンズ"
        Team.RAC -> "丸岡RUCK"
        Team.UNITED -> "福井ユナイテッド"
    }
    val q = java.net.URLEncoder.encode("$word (パブリックビューイング OR PV OR 観戦会)", "UTF-8")
    return "https://x.com/search?q=$q&f=live"
}
