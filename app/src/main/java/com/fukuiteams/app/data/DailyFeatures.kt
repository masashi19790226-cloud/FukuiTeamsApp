package com.fukuiteams.app.data

import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.absoluteValue

/**
 * 一面の「特集」(日替わりの読み物)。試合が無い日も一面が毎日変わるよう、
 * 取得済みのデータ(日程・結果・展望・選手の成績・順位・ニュース・招待)から記事を作る。
 * どの記事も、取得できた数字だけで書く(数字を作らない)。
 */
data class FeatureStory(
    /** 記事の種類を表す小見出し(例「今日の注目選手」「数字で見る」) */
    val kicker: String,
    val team: Team?,
    val headline: String,
    val body: String,
    /** 顔写真・ニュースの画像(無ければ空) */
    val photo: String = "",
    /** 大きく出す数字と単位(例「3」「日」)。無ければ null */
    val bigNumber: String? = null,
    val bigUnit: String? = null,
    /** 押したときに開く試合(ID)・ページ(URL)。どちらも無ければ押せない */
    val gameId: String? = null,
    val url: String? = null,
    /** 押したときにトピックの招待を開く */
    val openInvites: Boolean = false
)

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

private fun Game.localDate(): LocalDate? = runCatching {
    LocalDate.of(sortKey.substring(0, 4).toInt(), sortKey.substring(4, 6).toInt(), sortKey.substring(6, 8).toInt())
}.getOrNull()

private fun Game.shortLabel(): String = "${dateLabel.split("/").drop(1).joinToString("/")}(${dayOfWeek})"

private fun teamShort(team: Team): String = when (team) {
    Team.BLOWINDS -> "ブローウィンズ"
    Team.RAC -> "丸岡RUCK"
    Team.UNITED -> "ユナイテッド"
}

/** 今季の始まり(この日以降の試合を今季として数える) */
private fun seasonStart(team: Team, today: LocalDate): LocalDate = when (team) {
    Team.BLOWINDS -> LocalDate.of(if (today.monthValue >= 8) today.year else today.year - 1, 8, 1)
    Team.RAC -> LocalDate.of(if (today.monthValue >= 5) today.year else today.year - 1, 5, 1)
    Team.UNITED -> LocalDate.of(today.year, 3, 1)
}

/**
 * 最新の結果が「速報」として新しいか(試合の当日・翌日)。古ければ一面トップは特集にする。
 */
fun isFreshResult(game: Game): Boolean {
    val d = game.localDate() ?: return true
    val today = LocalDate.now(JST)
    return !d.isBefore(today.minusDays(1))
}

/** その日の特集の候補をすべて作る(並び順は日替わり)。teams にないチームの記事は作らない。 */
fun buildFeatureStories(
    teams: List<Team>,
    games: List<Game>,
    results: Map<String, RemoteGameResult>,
    previews: Map<String, GamePreview>,
    players: Map<String, TeamPlayers>,
    standings: Map<String, LeagueStandings>,
    news: List<RemoteInvitationAlert>,
    invites: List<RemoteInvitationAlert>,
    birthdays: List<PlayerBirthday> = emptyList(),
    /** 自分が「現地観戦」と記録した試合のID */
    onSiteIds: Set<String> = emptySet()
): List<FeatureStory> {
    val today = LocalDate.now(JST)
    val out = mutableListOf<FeatureStory>()
    teams.forEach { team ->
        out += countdown(team, games, results, previews)
        out += numbers(team, games, results, today)
        out += spotlight(team, players, standings, today)
        out += standing(team, standings, games)
        out += onThisDay(team, games, results, today)
        out += teamLeaders(team, players)
        out += sharpshooter(team, players)
        out += lastGameHeroes(team, players, games)
        out += ironman(team, players)
        out += opponentWatch(team, games, previews)
        out += remaining(team, games, today)
        out += scorerRace(team, standings)
        out += biggestComeback(team, games, results, today)
        out += onSiteRecord(team, games, results, onSiteIds)
    }
    out += topNews(teams, news, today)
    out += newsDigest(teams, news, today)
    out += openInvites(teams, invites)
    out += weekAhead(teams, games)
    out += birthdayMonth(teams, birthdays, today)
    // 日替わりで並べ替える(同じ日は何度開いても同じ順。日が変わると先頭が変わる)
    val seed = today.toEpochDay().toInt()
    return out.sortedBy { ((it.headline.hashCode() + seed * 7919).absoluteValue) % 1000 }
}

// ---------- 次の試合までのカウントダウン ----------

private fun countdown(
    team: Team,
    games: List<Game>,
    results: Map<String, RemoteGameResult>,
    previews: Map<String, GamePreview>
): List<FeatureStory> {
    val next = games.filter { it.team == team && it.isUpcoming() }.minByOrNull { it.sortKey } ?: return emptyList()
    val days = next.daysUntil() ?: return emptyList()
    if (days < 1 || days > 30) return emptyList()
    val h2h = headToHead(next, games, results)
    val h2hLine = h2h?.let {
        val last = it.meetings.first()
        val mark = when (last.second.outcome()) {
            GameOutcome.WIN -> "勝利"
            GameOutcome.LOSE -> "敗戦"
            GameOutcome.DRAW -> "引き分け"
        }
        "${next.opponent}とはこれまで${it.wins}勝${it.losses}敗" + (if (it.draws > 0) "${it.draws}分" else "") +
            "。前回(${last.first.shortLabel()})は${last.second.myScore}-${last.second.opponentScore}で$mark。"
    } ?: "${next.opponent}とは今季(記録上)初めての対戦。"
    val summary = previews[next.id]?.summary?.takeIf { it.isNotBlank() }
    val where = if (next.isHome) "ホーム・${next.venue}" else "敵地・${next.venue}"
    return listOf(
        FeatureStory(
            kicker = "決戦まで",
            team = team,
            headline = "${next.opponent}戦まであと${days}日",
            body = "${next.shortLabel()} ${next.timeLabel}、$where。$h2hLine" + (summary?.let { " $it" } ?: ""),
            bigNumber = "$days",
            bigUnit = "日",
            gameId = next.id
        )
    )
}

// ---------- 数字で見る(今季の成績から) ----------

private fun numbers(team: Team, games: List<Game>, results: Map<String, RemoteGameResult>, today: LocalDate): List<FeatureStory> {
    val start = seasonStart(team, today)
    val played = games
        .filter { it.team == team && (it.localDate()?.let { d -> !d.isBefore(start) } == true) && results.containsKey(it.id) }
        .sortedBy { it.sortKey }
    if (played.size < 2) return emptyList()
    val rs = played.map { it to results.getValue(it.id) }
    val win = rs.count { it.second.outcome() == GameOutcome.WIN }
    val lose = rs.count { it.second.outcome() == GameOutcome.LOSE }
    val draw = rs.count { it.second.outcome() == GameOutcome.DRAW }
    val record = "${win}勝${lose}敗" + if (draw > 0) "${draw}分" else ""
    val stories = mutableListOf<FeatureStory>()
    val name = teamShort(team)

    // 得点・失点の平均
    val avgFor = rs.map { it.second.myScore }.average()
    val avgAgainst = rs.map { it.second.opponentScore }.average()
    val unit = "点"
    stories += FeatureStory(
        kicker = "数字で見る",
        team = team,
        headline = "${name}、1試合平均${"%.1f".format(avgFor)}$unit",
        body = "今季${played.size}試合で$record。1試合あたり${"%.1f".format(avgFor)}${unit}を取り、${"%.1f".format(avgAgainst)}${unit}を失っている" +
            (if (avgFor >= avgAgainst) "(得失点差は1試合あたり+${"%.1f".format(avgFor - avgAgainst)})。" else "(1試合あたり${"%.1f".format(avgAgainst - avgFor)}の負け越し)。"),
        bigNumber = "%.1f".format(avgFor),
        bigUnit = unit
    )

    // ホーム・アウェイ別
    val home = rs.filter { it.first.isHome }
    val away = rs.filterNot { it.first.isHome }
    if (home.isNotEmpty() && away.isNotEmpty()) {
        fun rec(l: List<Pair<Game, RemoteGameResult>>) =
            "${l.count { it.second.outcome() == GameOutcome.WIN }}勝${l.count { it.second.outcome() == GameOutcome.LOSE }}敗" +
                l.count { it.second.outcome() == GameOutcome.DRAW }.let { if (it > 0) "${it}分" else "" }
        val homeWins = home.count { it.second.outcome() == GameOutcome.WIN }
        val awayWins = away.count { it.second.outcome() == GameOutcome.WIN }
        val strongHome = homeWins * away.size >= awayWins * home.size
        stories += FeatureStory(
            kicker = "数字で見る",
            team = team,
            headline = if (strongHome) "${name}、ホームで${rec(home)}" else "${name}、敵地で${rec(away)}",
            body = "今季はホームで${rec(home)}、アウェイで${rec(away)}。" +
                (if (strongHome) "ホームの声援が力になっている。" else "アウェイでの勝負強さが光る。") +
                "通算は$record。"
        )
    }

    // 最大得点差の勝利
    rs.filter { it.second.outcome() == GameOutcome.WIN }
        .maxByOrNull { it.second.myScore - it.second.opponentScore }
        ?.let { (g, r) ->
            stories += FeatureStory(
                kicker = "今季のベストゲーム",
                team = team,
                headline = "${g.opponent}に${r.myScore}-${r.opponentScore}",
                body = "今季いちばん大差で勝ったのは${g.shortLabel()}の${g.opponent}戦(${if (g.isHome) "ホーム" else "アウェイ"})。" +
                    "${r.myScore - r.opponentScore}点差の快勝だった。今季は$record。",
                bigNumber = "+${r.myScore - r.opponentScore}",
                bigUnit = "点差",
                gameId = g.id
            )
        }

    // 連勝・連敗(最新の試合から数える)
    val last = rs.last()
    var streak = 0
    for (i in rs.indices.reversed()) {
        if (rs[i].second.outcome() == last.second.outcome()) streak++ else break
    }
    if (streak >= 2 && last.second.outcome() != GameOutcome.DRAW) {
        val isWin = last.second.outcome() == GameOutcome.WIN
        stories += FeatureStory(
            kicker = "数字で見る",
            team = team,
            headline = if (isWin) "${name}、${streak}連勝中" else "${name}、連敗を${streak}で止めたい",
            body = if (isWin) "${last.first.shortLabel()}の${last.first.opponent}戦まで${streak}連勝。次の試合で連勝を伸ばせるか。今季は$record。"
            else "${last.first.shortLabel()}の${last.first.opponent}戦まで${streak}連敗。次の試合で流れを変えたい。今季は$record。",
            bigNumber = "$streak",
            bigUnit = if (isWin) "連勝" else "連敗"
        )
    }
    return stories
}

// ---------- 今日の注目選手 ----------

private fun spotlight(
    team: Team,
    players: Map<String, TeamPlayers>,
    standings: Map<String, LeagueStandings>,
    today: LocalDate
): List<FeatureStory> {
    val day = today.toEpochDay().toInt()
    if (team == Team.BLOWINDS) {
        val list = players[Team.BLOWINDS.name]?.players.orEmpty()
            .filter { (it.games?.toIntOrNull() ?: 0) > 0 && it.points != null }
            .sortedBy { it.number.toIntOrNull() ?: 999 }
        if (list.isEmpty()) return emptyList()
        val p = list[day.absoluteValue % list.size]
        val recent = p.gameLog.take(3).mapNotNull { it.points?.toIntOrNull() }
        val trend = if (recent.size >= 2) {
            val avg = recent.average()
            val season = p.points?.toDoubleOrNull() ?: avg
            when {
                avg >= season + 3 -> "直近${recent.size}試合は平均${"%.1f".format(avg)}点と好調"
                avg <= season - 3 -> "直近${recent.size}試合は平均${"%.1f".format(avg)}点。ここから巻き返したい"
                else -> "直近${recent.size}試合も平均${"%.1f".format(avg)}点と安定"
            }
        } else null
        val last = p.gameLog.firstOrNull()
        val lastLine = last?.let { g ->
            val d = runCatching { LocalDate.parse(g.date) }.getOrNull()
            val date = d?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: g.date
            "前の試合($date ${g.opponent}戦)は" + listOfNotNull(
                g.points?.let { "${it}点" },
                g.rebounds?.let { "${it}リバウンド" },
                g.assists?.let { "${it}アシスト" }
            ).joinToString("・") + "。"
        } ?: ""
        val head = if (p.number.isNotBlank()) "#${p.number} ${p.name}" else p.name
        return listOf(
            FeatureStory(
                kicker = "今日の注目選手",
                team = team,
                headline = trend?.let { "$head、${it.substringBefore("。")}" } ?: "$head、今季平均${p.points}点",
                body = "今季${p.games}試合で平均${p.points}点・${p.rebounds ?: "-"}リバウンド・${p.assists ?: "-"}アシスト" +
                    (p.minutesPerGame?.let { "(出場${it})" } ?: "") + "。$lastLine" +
                    (trend?.let { "$it。" } ?: ""),
                photo = p.photo,
                bigNumber = p.points,
                bigUnit = "点"
            )
        )
    }
    // 丸岡RUCK・ユナイテッドは、チーム内の得点の表から
    val st = standings[team.name]?.scorers ?: return emptyList()
    val word = if (team == Team.RAC) "丸岡" else "ユナイテッド"
    val mine = st.rows.filter { it.team.contains(word) && it.goals > 0 }
    if (mine.isEmpty()) return emptyList()
    val p = mine[day.absoluteValue % mine.size]
    val head = if (p.number.isNotBlank()) "#${p.number} ${p.name}" else p.name
    val body = if (team == Team.RAC) {
        "今季${p.goals}得点で、リーグの得点ランキング${p.rank}位" +
            (p.shots?.let { "(シュート${it}本)" } ?: "") + "。" +
            (p.games?.let { "${it}試合に出場。" } ?: "")
    } else {
        "今季の北信越リーグで${p.goals}得点(チーム内${p.rank}位)。" +
            listOfNotNull(p.starts?.let { "先発${it}試合" }, p.bench?.let { "ベンチ入り${it}試合" }).joinToString("・")
                .let { if (it.isNotBlank()) "$it。" else "" }
    }
    return listOf(
        FeatureStory(
            kicker = "今日の注目選手",
            team = team,
            headline = "$head、今季${p.goals}ゴール",
            body = body,
            photo = p.photo,
            bigNumber = "${p.goals}",
            bigUnit = "得点"
        )
    )
}

// ---------- 順位(丸岡RUCK・ユナイテッド) ----------

private fun standing(team: Team, standings: Map<String, LeagueStandings>, games: List<Game>): List<FeatureStory> {
    if (team == Team.BLOWINDS) return emptyList()
    val league = standings[team.name] ?: return emptyList()
    val part = league.final?.takeIf { it.rows.isNotEmpty() } ?: league.regular ?: return emptyList()
    val word = if (team == Team.RAC) "丸岡" else "ユナイテッド"
    val me = part.rows.firstOrNull { it.team.contains(word) } ?: return emptyList()
    val top = part.rows.minByOrNull { it.rank } ?: return emptyList()
    val next = games.filter { it.team == team && it.isUpcoming() }.minByOrNull { it.sortKey }
    val gap = top.points - me.points
    val headline = if (me.rank == 1) "${teamShort(team)}、首位を走る" else "${teamShort(team)}、現在${me.rank}位"
    val body = (if (me.rank == 1) {
        val second = part.rows.filter { it.rank > 1 }.minByOrNull { it.rank }
        "${part.label}で勝点${me.points}の首位。" + (second?.let { "2位の${it.team}とは勝点差${me.points - it.points}。" } ?: "")
    } else {
        "${part.label}で勝点${me.points}の${me.rank}位。首位の${top.team}とは勝点差$gap。"
    }) + (next?.let { "次は${it.shortLabel()}の${it.opponent}戦。" } ?: "")
    return listOf(
        FeatureStory(
            kicker = "順位",
            team = team,
            headline = headline,
            body = body,
            bigNumber = "${me.rank}",
            bigUnit = "位",
            url = league.sourceUrl.takeIf { it.isNotBlank() }
        )
    )
}

// ---------- 今日は何の日(過去の同じ日の試合) ----------

private fun onThisDay(team: Team, games: List<Game>, results: Map<String, RemoteGameResult>, today: LocalDate): List<FeatureStory> {
    val hit = games.filter { g ->
        val d = g.localDate() ?: return@filter false
        g.team == team && d.year < today.year && d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth &&
            results.containsKey(g.id)
    }.maxByOrNull { it.sortKey } ?: return emptyList()
    val r = results.getValue(hit.id)
    val years = today.year - (hit.localDate()?.year ?: today.year)
    val verb = when (r.outcome()) {
        GameOutcome.WIN -> "に勝利"
        GameOutcome.LOSE -> "に敗れた"
        GameOutcome.DRAW -> "と引き分けた"
    }
    return listOf(
        FeatureStory(
            kicker = "今日は何の日",
            team = team,
            headline = "${years}年前の今日、${hit.opponent}$verb",
            body = "${hit.dateLabel}(${hit.dayOfWeek})、${if (hit.isHome) "ホーム" else "アウェイ"}の${hit.opponent}戦は${r.myScore}-${r.opponentScore}。",
            bigNumber = "$years",
            bigUnit = "年前",
            gameId = hit.id
        )
    )
}

// ---------- ニュース・招待 ----------

private fun topNews(teams: List<Team>, news: List<RemoteInvitationAlert>, today: LocalDate): List<FeatureStory> {
    val border = today.minusDays(3).atStartOfDay(JST).toInstant()
    val item = news
        .filter { n -> teams.any { it.name == n.teamId } && (n.eventInstant()?.isAfter(border) == true) }
        .maxByOrNull { it.eventInstant() ?: java.time.Instant.EPOCH } ?: return emptyList()
    val team = Team.values().firstOrNull { it.name == item.teamId }
    return listOf(
        FeatureStory(
            kicker = "ニュース",
            team = team,
            headline = item.title,
            body = listOf(item.sourceLabel(), item.timeLabel()).filter { it.isNotBlank() }.joinToString("・") + "。押すと記事を開きます。",
            photo = item.image,
            url = item.link
        )
    )
}

private fun openInvites(teams: List<Team>, invites: List<RemoteInvitationAlert>): List<FeatureStory> {
    val open = invites.filter { i -> teams.any { it.name == i.teamId } && !i.isLikelyClosed() }
    if (open.isEmpty()) return emptyList()
    val first = open.first()
    return listOf(
        FeatureStory(
            kicker = "無料招待",
            team = if (teams.size == 1) teams.first() else null,
            headline = "無料招待、募集中が${open.size}件",
            body = "「${first.title}」" + (if (open.size > 1) "ほか${open.size - 1}件" else "") + "。応募は締切にご注意を。押すとトピックを開きます。",
            bigNumber = "${open.size}",
            bigUnit = "件",
            openInvites = true
        )
    )
}

// ================= ここから追加の特集 =================

private fun bwPlayers(players: Map<String, TeamPlayers>) =
    players[Team.BLOWINDS.name]?.players.orEmpty().filter { (it.games?.toIntOrNull() ?: 0) > 0 }

private fun PlayerStats.head(): String = if (number.isNotBlank()) "#$number $name" else name

/** チームのリーダー(平均得点・リバウンド・アシストの各1位)。ブローウィンズのみ */
private fun teamLeaders(team: Team, players: Map<String, TeamPlayers>): List<FeatureStory> {
    if (team != Team.BLOWINDS) return emptyList()
    val list = bwPlayers(players)
    if (list.size < 3) return emptyList()
    fun top(v: (PlayerStats) -> String?) = list.maxByOrNull { v(it)?.toDoubleOrNull() ?: -1.0 }
    val pts = top { it.points } ?: return emptyList()
    val reb = top { it.rebounds }
    val ast = top { it.assists }
    val ranking = list.sortedByDescending { it.points?.toDoubleOrNull() ?: -1.0 }.take(3)
    return listOf(
        FeatureStory(
            kicker = "チームの柱",
            team = team,
            headline = "得点王は${pts.head()}、平均${pts.points}点",
            body = "今季のチーム内の得点ランキングは、" +
                ranking.mapIndexed { i, p -> "${i + 1}位 ${p.name}(${p.points}点)" }.joinToString("、") + "。" +
                (reb?.let { "リバウンドは${it.name}(平均${it.rebounds})、" } ?: "") +
                (ast?.let { "アシストは${it.name}(平均${it.assists})がチーム1位。" } ?: ""),
            photo = pts.photo,
            bigNumber = pts.points,
            bigUnit = "点"
        )
    )
}

/** シュートの名手(3ポイント・フリースローの成功率。試投数が少ない選手は除く)。ブローウィンズのみ */
private fun sharpshooter(team: Team, players: Map<String, TeamPlayers>): List<FeatureStory> {
    if (team != Team.BLOWINDS) return emptyList()
    val list = bwPlayers(players)
    val out = mutableListOf<FeatureStory>()
    fun pct(s: String?) = s?.removeSuffix("%")?.toDoubleOrNull()
    list.filter { (it.threesAttempted?.toIntOrNull() ?: 0) >= 10 }
        .maxByOrNull { pct(it.threePct) ?: -1.0 }
        ?.let { p ->
            out += FeatureStory(
                kicker = "シュートの名手",
                team = team,
                headline = "3ポイントの名手は${p.head()}",
                body = "今季の3ポイント成功率は${p.threePct}(${p.threesMade}/${p.threesAttempted})で、チーム1位(10本以上打った選手の中で)。" +
                    "外からの一撃に注目したい。",
                photo = p.photo,
                bigNumber = p.threePct?.removeSuffix("%"),
                bigUnit = "%"
            )
        }
    list.filter { (it.freeThrowsAttempted?.toIntOrNull() ?: 0) >= 10 }
        .maxByOrNull { pct(it.freeThrowPct) ?: -1.0 }
        ?.let { p ->
            out += FeatureStory(
                kicker = "シュートの名手",
                team = team,
                headline = "フリースローは${p.name}におまかせ",
                body = "今季のフリースロー成功率は${p.freeThrowPct}(${p.freeThrowsMade}/${p.freeThrowsAttempted})でチーム1位(10本以上打った選手の中で)。" +
                    "接戦の終盤で頼りになる。",
                photo = p.photo,
                bigNumber = p.freeThrowPct?.removeSuffix("%"),
                bigUnit = "%"
            )
        }
    return out
}

/** 前の試合のヒーロー(二桁得点の選手・ベンチから出て活躍した選手)。ブローウィンズのみ */
private fun lastGameHeroes(team: Team, players: Map<String, TeamPlayers>, games: List<Game>): List<FeatureStory> {
    if (team != Team.BLOWINDS) return emptyList()
    val list = bwPlayers(players)
    val lastDate = list.mapNotNull { it.gameLog.firstOrNull()?.date }.maxOrNull() ?: return emptyList()
    val rows = list.mapNotNull { p -> p.gameLog.firstOrNull { it.date == lastDate }?.let { p to it } }
    if (rows.isEmpty()) return emptyList()
    val opp = rows.first().second.opponent
    val d = runCatching { LocalDate.parse(lastDate) }.getOrNull()
    val dateText = d?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: lastDate
    val gameId = games.firstOrNull { g -> g.team == team && g.localDate()?.toString() == lastDate }?.id
    val out = mutableListOf<FeatureStory>()
    val doubles = rows.filter { (it.second.points?.toIntOrNull() ?: 0) >= 10 }
        .sortedByDescending { it.second.points?.toIntOrNull() ?: 0 }
    if (doubles.isNotEmpty()) {
        out += FeatureStory(
            kicker = "前の試合をふり返る",
            team = team,
            headline = "${opp}戦、${doubles.size}人が二桁得点",
            body = "$dateText の${opp}戦で二桁得点を挙げたのは、" +
                doubles.joinToString("、") { "${it.first.name}(${it.second.points}点)" } + "。",
            photo = doubles.first().first.photo,
            bigNumber = "${doubles.size}",
            bigUnit = "人",
            gameId = gameId
        )
    }
    rows.filter { !it.second.starter && (it.second.points?.toIntOrNull() ?: 0) >= 8 }
        .maxByOrNull { it.second.points?.toIntOrNull() ?: 0 }
        ?.let { (p, g) ->
            out += FeatureStory(
                kicker = "ベンチの切り札",
                team = team,
                headline = "途中出場の${p.name}が${g.points}点",
                body = "$dateText の${opp}戦、ベンチから出た${p.head()}が${g.minutes ?: "-"}の出場で${g.points}点" +
                    (g.fieldGoalsMade?.let { m -> g.fieldGoalsAttempted?.let { a -> "(FG $m/$a)" } } ?: "") + "。流れを変える働きを見せた。",
                photo = p.photo,
                bigNumber = g.points,
                bigUnit = "点",
                gameId = gameId
            )
        }
    return out
}

/** チームで一番長くコートに立っている選手。ブローウィンズのみ */
private fun ironman(team: Team, players: Map<String, TeamPlayers>): List<FeatureStory> {
    if (team != Team.BLOWINDS) return emptyList()
    fun sec(t: String?): Int? = t?.split(":")?.takeIf { it.size == 2 }?.let { (m, s) -> (m.toIntOrNull() ?: return null) * 60 + (s.toIntOrNull() ?: 0) }
    val p = bwPlayers(players).maxByOrNull { sec(it.minutesPerGame) ?: -1 } ?: return emptyList()
    if (sec(p.minutesPerGame) == null) return emptyList()
    return listOf(
        FeatureStory(
            kicker = "コートの主",
            team = team,
            headline = "出場時間トップは${p.head()}",
            body = "1試合平均${p.minutesPerGame}の出場はチームで最長。その間に平均${p.points ?: "-"}点・${p.rebounds ?: "-"}リバウンド・${p.assists ?: "-"}アシストを記録している。",
            photo = p.photo
        )
    )
}

/** 次の相手の要注意選手と近況(展望のデータから) */
private fun opponentWatch(team: Team, games: List<Game>, previews: Map<String, GamePreview>): List<FeatureStory> {
    val next = games.filter { it.team == team && it.isUpcoming() }.minByOrNull { it.sortKey } ?: return emptyList()
    val pv = previews[next.id] ?: return emptyList()
    val out = mutableListOf<FeatureStory>()
    pv.keyPlayers.firstOrNull()?.let { k ->
        out += FeatureStory(
            kicker = "要注意人物",
            team = team,
            headline = "${next.opponent}の${if (k.number.isNotBlank()) "#${k.number} " else ""}${k.name}に注意",
            body = "次の相手${next.opponent}(${next.shortLabel()})で警戒したいのは${k.name}" +
                (if (k.position.isNotBlank()) "(${k.position})" else "") + "。${k.stat}。" +
                (pv.keyPlayers.drop(1).takeIf { it.isNotEmpty() }?.let { rest -> "ほかに" + rest.joinToString("、") { it.name } + "も。" } ?: ""),
            gameId = next.id
        )
    }
    if (pv.opp.record.isNotBlank() || pv.opp.form.isNotBlank()) {
        out += FeatureStory(
            kicker = "相手を知る",
            team = team,
            headline = "次の相手${next.opponent}は${pv.opp.record.ifBlank { "好調?" }}",
            body = listOfNotNull(
                pv.opp.record.takeIf { it.isNotBlank() }?.let { "${next.opponent}の今季成績は$it" },
                pv.opp.rank.takeIf { it.isNotBlank() }?.let { "順位は$it" },
                pv.opp.form.takeIf { it.isNotBlank() }?.let { "直近は$it" }
            ).joinToString("、") + "。" +
                listOfNotNull(
                    pv.my.record.takeIf { it.isNotBlank() }?.let { "対する福井は$it" },
                    pv.my.form.takeIf { it.isNotBlank() }?.let { "直近$it" }
                ).joinToString("・").let { if (it.isNotBlank()) "$it。" else "" },
            gameId = next.id
        )
    }
    return out
}

/** 今季の残り試合 */
private fun remaining(team: Team, games: List<Game>, today: LocalDate): List<FeatureStory> {
    val start = seasonStart(team, today)
    val season = games.filter { it.team == team && (it.localDate()?.let { d -> !d.isBefore(start) } == true) }
    val left = season.filter { it.isUpcoming() }
    if (season.size < 5 || left.isEmpty()) return emptyList()
    val home = left.count { it.isHome }
    return listOf(
        FeatureStory(
            kicker = "シーズンの行方",
            team = team,
            headline = "${teamShort(team)}、今季の残りは${left.size}試合",
            body = "日程に載っている今季${season.size}試合のうち、残りは${left.size}試合(ホーム${home}・アウェイ${left.size - home})。" +
                "最後の試合は${left.maxByOrNull { it.sortKey }?.let { "${it.shortLabel()}の${it.opponent}戦" } ?: "未定"}。",
            bigNumber = "${left.size}",
            bigUnit = "試合"
        )
    )
}

/** 女子Fリーグの得点王争い(リーグ1位と丸岡RUCKの1位) */
private fun scorerRace(team: Team, standings: Map<String, LeagueStandings>): List<FeatureStory> {
    if (team != Team.RAC) return emptyList()
    val rows = standings[Team.RAC.name]?.scorers?.rows.orEmpty()
    val leader = rows.minByOrNull { it.rank } ?: return emptyList()
    val ours = rows.filter { it.team.contains("丸岡") }.minByOrNull { it.rank } ?: return emptyList()
    val body = if (leader.team.contains("丸岡")) {
        "リーグの得点ランキングで丸岡RUCKの${leader.name}が${leader.goals}得点でトップに立つ。" +
            (rows.filter { !it.team.contains("丸岡") }.minByOrNull { it.rank }?.let { "追うのは${it.team}の${it.name}(${it.goals}得点)。" } ?: "")
    } else {
        "リーグのトップは${leader.team}の${leader.name}で${leader.goals}得点。丸岡RUCKの最上位は${ours.name}の${ours.goals}得点(${ours.rank}位)で、トップとの差は${leader.goals - ours.goals}。"
    }
    return listOf(
        FeatureStory(
            kicker = "得点王争い",
            team = team,
            headline = "得点王争い、トップは${leader.name}の${leader.goals}点",
            body = body,
            photo = ours.photo.ifBlank { leader.photo },
            bigNumber = "${leader.goals}",
            bigUnit = "得点"
        )
    )
}

/** 今季いちばんの接戦の勝利 */
private fun biggestComeback(team: Team, games: List<Game>, results: Map<String, RemoteGameResult>, today: LocalDate): List<FeatureStory> {
    val start = seasonStart(team, today)
    val wins = games.filter { it.team == team && (it.localDate()?.let { d -> !d.isBefore(start) } == true) }
        .mapNotNull { g -> results[g.id]?.takeIf { it.outcome() == GameOutcome.WIN }?.let { g to it } }
    val close = wins.minByOrNull { it.second.myScore - it.second.opponentScore } ?: return emptyList()
    val diff = close.second.myScore - close.second.opponentScore
    val limit = if (team == Team.BLOWINDS) 5 else 1
    if (diff > limit || wins.size < 2) return emptyList()
    val (g, r) = close
    return listOf(
        FeatureStory(
            kicker = "しびれた一戦",
            team = team,
            headline = "${g.opponent}に${r.myScore}-${r.opponentScore}、${diff}点差で逃げ切り",
            body = "今季いちばんの接戦の勝利は${g.shortLabel()}の${g.opponent}戦(${if (g.isHome) "ホーム" else "アウェイ"})。最後まで目が離せない試合だった。",
            bigNumber = "$diff",
            bigUnit = "点差",
            gameId = g.id
        )
    )
}

/** 自分が現地で見た試合の成績 */
private fun onSiteRecord(team: Team, games: List<Game>, results: Map<String, RemoteGameResult>, onSiteIds: Set<String>): List<FeatureStory> {
    val seen = games.filter { it.team == team && it.id in onSiteIds }.mapNotNull { g -> results[g.id]?.let { g to it } }
    if (seen.isEmpty()) return emptyList()
    val w = seen.count { it.second.outcome() == GameOutcome.WIN }
    val l = seen.count { it.second.outcome() == GameOutcome.LOSE }
    val d = seen.size - w - l
    val rate = if (w + l > 0) w * 100 / (w + l) else null
    return listOf(
        FeatureStory(
            kicker = "あなたの観戦記録",
            team = team,
            headline = "現地観戦は${w}勝${l}敗" + (if (d > 0) "${d}分" else "") + (rate?.let { "、勝率${it}%" } ?: ""),
            body = "あなたが現地で見た${teamShort(team)}の試合は${seen.size}試合。" +
                (if (rate != null && rate >= 60) "あなたが行くと勝つ「勝利の使者」かも。" else if (rate != null && rate < 40) "次の現地観戦で流れを変えよう。" else "次も会場から声援を。") +
                "最後に見たのは${seen.maxByOrNull { it.first.sortKey }!!.let { "${it.first.shortLabel()}の${it.first.opponent}戦" }}。",
            bigNumber = rate?.let { "$it" } ?: "${seen.size}",
            bigUnit = if (rate != null) "%" else "試合"
        )
    )
}

/** ニュースのまとめ(直近3日の見出し3本) */
private fun newsDigest(teams: List<Team>, news: List<RemoteInvitationAlert>, today: LocalDate): List<FeatureStory> {
    val border = today.minusDays(3).atStartOfDay(JST).toInstant()
    val items = news.filter { n -> teams.any { it.name == n.teamId } && (n.eventInstant()?.isAfter(border) == true) }
        .sortedByDescending { it.eventInstant() }
    if (items.size < 3) return emptyList()
    return listOf(
        FeatureStory(
            kicker = "ニュースまとめ",
            team = if (teams.size == 1) teams.first() else null,
            headline = "この3日間のニュースは${items.size}件",
            body = items.take(3).joinToString("\n") { "・" + it.title.take(40) } + "\n押すとトピックを開きます。",
            bigNumber = "${items.size}",
            bigUnit = "件",
            openInvites = true
        )
    )
}

/** この先7日間の試合(3チームまとめて) */
private fun weekAhead(teams: List<Team>, games: List<Game>): List<FeatureStory> {
    val week = games.filter { g -> g.team in teams && g.isUpcoming() && (g.daysUntil() ?: 99L) in 0L..7L }.sortedBy { it.sortKey }
    if (week.size < 2) return emptyList()
    return listOf(
        FeatureStory(
            kicker = "今週の試合",
            team = if (teams.size == 1) teams.first() else null,
            headline = "この1週間で${week.size}試合",
            body = week.joinToString("\n") { "・${it.shortLabel()} ${teamShort(it.team)} vs ${it.opponent}(${if (it.isHome) "H" else "A"})" },
            bigNumber = "${week.size}",
            bigUnit = "試合",
            gameId = week.first().id
        )
    )
}

/** 今月が誕生日の選手 */
private fun birthdayMonth(teams: List<Team>, birthdays: List<PlayerBirthday>, today: LocalDate): List<FeatureStory> {
    val list = birthdays.filter { it.team in teams && it.birthday.monthValue == today.monthValue }
        .sortedBy { it.birthday.dayOfMonth }
    if (list.isEmpty()) return emptyList()
    return listOf(
        FeatureStory(
            kicker = "${today.monthValue}月生まれ",
            team = if (teams.size == 1) teams.first() else null,
            headline = "${today.monthValue}月が誕生日の選手は${list.size}人",
            body = list.joinToString("、") { "${it.birthday.dayOfMonth}日 ${it.name}(${it.team?.let { t -> teamShort(t) } ?: ""})" } + "。",
            // これから誕生日を迎える選手(今月)の写真。もう全員過ぎていれば最初の選手
            photo = (list.firstOrNull { it.birthday.dayOfMonth >= today.dayOfMonth } ?: list.first()).photo,
            bigNumber = "${list.size}",
            bigUnit = "人"
        )
    )
}
