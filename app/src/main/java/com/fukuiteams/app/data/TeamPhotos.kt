package com.fukuiteams.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import java.time.LocalDate
import java.time.ZoneId

/** 選手の写真1枚(URL と「#13 川島 聖那」のような名前) */
data class PhotoRef(val url: String, val label: String)

/**
 * アプリのあちこちに出す選手の写真の入れ物(ニュース・一面・試合の詳細・特集で共通に使う)。
 * - 各チームの選手 : 選手タブの成績(ブローウィンズ)と、誕生日のデータ(4チームの公式サイトの選手紹介)の写真
 * - 次の対戦相手   : ブローウィンズは選手タブの相手の全選手、丸岡RUCK・ブルーサンダーは得点の表の相手チームの選手
 * - 試合・イベントの写真: 各チームの公式サイトのお知らせに付いている写真と、丸岡RUCK公式サイトの試合結果の記事の写真
 *   (選手の顔写真だけにならないよう、混ぜて使う)
 * データを読み込んだとき(DataRefresher・各画面)に update / updateNews で入れ直す。
 */
object TeamPhotos {
    /** チーム → 選手の写真 */
    var own by mutableStateOf<Map<Team, List<PhotoRef>>>(emptyMap())
        private set

    /** チーム → 公式サイトのお知らせの写真(試合・イベントなど。名前の代わりにお知らせの見出し) */
    private var newsScenes by mutableStateOf<Map<Team, List<PhotoRef>>>(emptyMap())

    /** チーム → 公式サイトの試合結果の記事の写真(丸岡RUCKなど。standings.json の "PHOTOS") */
    private var gallery by mutableStateOf<Map<Team, List<PhotoRef>>>(emptyMap())

    /** チーム → 試合・イベントの写真(試合結果の記事の写真とお知らせの写真を交互に混ぜたもの) */
    val scenes: Map<Team, List<PhotoRef>>
        get() = Team.values().associateWith { t ->
            val a = gallery[t].orEmpty()
            val b = newsScenes[t].orEmpty()
            val out = mutableListOf<PhotoRef>()
            for (i in 0 until maxOf(a.size, b.size)) {
                a.getOrNull(i)?.let { out += it }
                b.getOrNull(i)?.let { out += it }
            }
            out.distinctBy { it.url }
        }.filterValues { it.isNotEmpty() }

    /** 試合結果の記事の写真を入れ直す(StandingsRepository が standings.json を読んだとき) */
    fun updateGallery(map: Map<Team, List<PhotoRef>>) {
        gallery = map
    }

    /** 「チーム|相手チーム名」→ 相手の選手の写真(得点・成績の上の選手から) */
    private var opponents by mutableStateOf<Map<String, List<PhotoRef>>>(emptyMap())

    /** ブローウィンズの次の相手の選手(試合ID と写真) */
    private var blowindsOpp by mutableStateOf<Pair<String, List<PhotoRef>>?>(null)

    private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

    private fun label(number: String, name: String): String = (if (number.isNotBlank()) "#$number " else "") + name

    fun update(players: Map<String, TeamPlayers>, standings: Map<String, LeagueStandings>, birthdays: List<PlayerBirthday>) {
        val mine = mutableMapOf<Team, MutableList<PhotoRef>>()
        Team.values().forEach { team ->
            players[team.name]?.players.orEmpty().forEach { p ->
                if (p.photo.isNotBlank()) mine.getOrPut(team) { mutableListOf() } += PhotoRef(p.photo, label(p.number, p.name))
            }
        }
        birthdays.forEach { b ->
            val t = b.team ?: return@forEach
            if (b.photo.isNotBlank()) mine.getOrPut(t) { mutableListOf() } += PhotoRef(b.photo, b.label)
        }
        // 得点の表に写真がある自チームの選手(丸岡RUCK・ユナイテッド・ブルーサンダー)
        val opp = mutableMapOf<String, MutableList<PhotoRef>>()
        listOf(Team.RAC, Team.UNITED, Team.THUNDER).forEach { team ->
            val word = when (team) {
                Team.RAC -> "丸岡"
                Team.UNITED -> "福井ユナイテッド"
                else -> "ブルーサンダー"
            }
            standings[team.name]?.scorers?.rows.orEmpty()
                .filter { it.photo.isNotBlank() }
                .sortedWith(compareByDescending<ScorerRow> { it.goals }.thenBy { it.rank })
                .forEach { r ->
                    val ref = PhotoRef(r.photo, label(r.number, r.name))
                    if (r.team.contains(word)) mine.getOrPut(team) { mutableListOf() } += ref
                    else opp.getOrPut(team.name + "|" + r.team) { mutableListOf() } += ref
                }
        }
        own = mine.mapValues { (_, v) -> v.distinctBy { it.url } }
        opponents = opp.mapValues { (_, v) -> v.distinctBy { it.url } }
        blowindsOpp = players[BLOWINDS_OPP_KEY]?.let { r ->
            r.gameId to r.players.filter { it.photo.isNotBlank() }
                .sortedByDescending { it.points?.toDoubleOrNull() ?: -1.0 }
                .map { PhotoRef(it.photo, label(it.number, it.name)) }
        }
    }

    // お知らせの写真のうち、どの記事にも付く共通の画像(サイトのロゴ・OGP画像など)と、小さすぎる画像(Bingの縮小画像)は使わない
    private val GENERIC_IMAGE = Regex("""ogp|logo|noimage|no_image|no-image|favicon|default|bing\.com/th""", RegexOption.IGNORE_CASE)

    /** ニュースから、各チームの公式サイトのお知らせの写真を集める(新しいお知らせから) */
    fun updateNews(items: List<RemoteInvitationAlert>) {
        val map = mutableMapOf<Team, MutableList<PhotoRef>>()
        items.sortedByDescending { it.published.ifBlank { it.detectedAt } }.forEach { n ->
            val team = Team.values().firstOrNull { it.name == n.teamId } ?: return@forEach
            if (n.image.isBlank() || GENERIC_IMAGE.containsMatchIn(n.image)) return@forEach
            val title = n.title.trim().let { if (it.length > 28) it.take(27) + "…" else it }
            map.getOrPut(team) { mutableListOf() } += PhotoRef(n.image, title)
        }
        newsScenes = map.mapValues { (_, v) -> v.distinctBy { it.url }.take(40) }
    }

    /** まだ読み込んでいなければ読み込む(トピック・試合の詳細など、一面を通らずに開いたとき用) */
    suspend fun ensureLoaded() {
        if (own.isNotEmpty()) return
        runCatching { update(PlayersRepository.fetch(), StandingsRepository.fetch(), BirthdaysRepository.fetch()) }
    }

    /** そのチームの選手の写真(null なら4チームすべて) */
    fun forTeam(team: Team?): List<PhotoRef> =
        if (team != null) own[team].orEmpty() else Team.values().flatMap { own[it].orEmpty() }

    /** 試合の相手チームの選手の写真(分からなければ空) */
    fun forOpponent(game: Game): List<PhotoRef> {
        if (game.team == Team.BLOWINDS) {
            val (gameId, list) = blowindsOpp ?: return emptyList()
            return if (gameId.isBlank() || gameId == game.id) list else emptyList()
        }
        val opp = game.opponent
        return opponents.entries
            .filter { (k, _) -> k.startsWith(game.team.name + "|") }
            .firstOrNull { (k, _) -> val name = k.substringAfter("|"); name.contains(opp) || opp.contains(name) }
            ?.value.orEmpty()
    }

    /**
     * 写真の並びを日替わりで回す(seed が同じなら同じ日は同じ並び)。
     * 同じ画面で記事ごとに違う選手が出るよう、seed には記事のIDなどを渡す
     */
    fun rotate(list: List<PhotoRef>, seed: String): List<PhotoRef> {
        if (list.size <= 1) return list
        val day = LocalDate.now(JST).toEpochDay().toInt()
        val start = Math.floorMod(seed.hashCode() + day * 31, list.size)
        return list.drop(start) + list.take(start)
    }

    /** そのチームの公式サイトのお知らせの写真(null なら4チームすべて) */
    fun scenesFor(team: Team?): List<PhotoRef> =
        if (team != null) scenes[team].orEmpty() else Team.values().flatMap { scenes[it].orEmpty() }

    /**
     * 選手の写真と、お知らせの写真(試合・イベント)を交互に混ぜて、日替わりで回したもの。
     * 同じ選手の顔写真ばかりにならないよう、一面の近況などで使う
     */
    fun mixed(team: Team?, seed: String): List<PhotoRef> {
        val a = rotate(forTeam(team), seed)
        val b = rotate(scenesFor(team), seed + "-scene")
        val out = mutableListOf<PhotoRef>()
        for (i in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(i)?.let { out += it }
            b.getOrNull(i)?.let { out += it }
        }
        return out.distinctBy { it.url }
    }

    /** 名前で写真を探す(空白・異体字の違いは無視) */
    fun findByName(list: List<PhotoRef>, name: String): PhotoRef? {
        val key = playerNameKey(name)
        return list.firstOrNull { playerNameKey(it.label.replace(Regex("^#\\S+\\s+"), "")) == key }
    }
}
