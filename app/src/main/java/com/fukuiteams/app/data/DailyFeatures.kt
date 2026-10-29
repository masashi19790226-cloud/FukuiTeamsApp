package com.fukuiteams.app.data

import com.fukuiteams.app.model.shortName
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
    val openInvites: Boolean = false,
    /** 押したときに掲示板タブを開く(bbsPostNo があればその投稿を開く) */
    val openBbs: Boolean = false,
    val bbsPostNo: Int? = null,
    /**
     * 記事に合う写真が無いので、そのチームの選手の写真を代わりに付けたときの、その選手の名前(例「#13 川島 聖那」)。
     * 記事の写真そのものなら null。
     */
    val photoCaption: String? = null,
    /**
     * 記事の写真が読み込めなかったときに順に試す、代わりの選手の写真(URL と選手名)。
     * ニュースの画像は直リンクを断られて読めないことがあるため、必ず何かの写真を出せるようにする。
     */
    val photoAlternates: List<Pair<String, String>> = emptyList()
)

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

private fun Game.localDate(): LocalDate? = runCatching {
    LocalDate.of(sortKey.substring(0, 4).toInt(), sortKey.substring(4, 6).toInt(), sortKey.substring(6, 8).toInt())
}.getOrNull()

private fun Game.shortLabel(): String = "${dateLabel.split("/").drop(1).joinToString("/")}(${dayOfWeek})"

/** 今季の始まり(この日以降の試合を今季として数える) */
private fun seasonStart(team: Team, today: LocalDate): LocalDate = when (team) {
    Team.BLOWINDS -> LocalDate.of(if (today.monthValue >= 8) today.year else today.year - 1, 8, 1)
    Team.RAC -> LocalDate.of(if (today.monthValue >= 5) today.year else today.year - 1, 5, 1)
    Team.UNITED -> LocalDate.of(today.year, 3, 1)
    // リーグHは秋から翌年の春
    Team.THUNDER -> LocalDate.of(if (today.monthValue >= 8) today.year else today.year - 1, 8, 1)
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
    onSiteIds: Set<String> = emptySet(),
    /** 掲示板の話題(メニューでオフ・読めなかったときは null) */
    bbsBuzz: BbsBuzz? = null,
    /** 記事の選び方を決める数。null なら日付(日替わり)。開くたびに変えると、開くたびに記事が入れかわる */
    seedOverride: Int? = null,
    /** 前に見た記事(featureStoryKey)。見ていない記事がじゅうぶんあれば、見た記事は出さない */
    seen: Set<String> = emptySet(),
    /** 写真が無くても、代わりの写真を付けない記事(画面のほうで図や選手カードにする記事) */
    keepWithoutPhoto: (FeatureStory) -> Boolean = { false }
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
        out += opponentWatch(team, games, previews, players, standings)
        out += remaining(team, games, today)
        out += scorerRace(team, standings)
        out += biggestComeback(team, games, results, today)
        out += onSiteRecord(team, games, results, onSiteIds)
        out += tallest(team, birthdays)
        out += localPlayers(team, birthdays)
        out += alumniStory(team, today)
        out += historyStory(team, today)
        // 過去の記録(data/history.json)から
        out += pastMatchStory(team, today)
        out += legendStory(team, today)
        out += milestoneStory(team)
        out += recordStory(team, today)
        out += nationalStory(team, today)
    }
    out += topNews(teams, news, today)
    out += newsDigest(teams, news, today)
    out += openInvites(teams, invites)
    out += weekAhead(teams, games)
    out += birthdayMonth(teams, birthdays, today)
    val bbs = bbsTalk(teams, bbsBuzz)
    // 掲示板の話題(書き込みが多いときだけ作る)と、直近のトピック(最大 TOPIC_SLOTS 本)は毎回必ず入れる
    // (新しいトピックが出れば、日付が変わらなくても入れ替わる)。
    // それ以外の記事は、同じ種類(小見出し)は1日1本だけ(どのチーム・どの記事にするかは日替わり)にして日替わりに並べる。
    // 並びは「日替わりの記事 → トピック → 日替わりの記事 → トピック → …」と交互にし、合計 MAX_STORIES_PER_DAY 本まで
    val seed = seedOverride ?: today.toEpochDay().toInt()
    // 前に見た記事を外す(見ていない記事の種類が MIN_UNSEEN_KINDS より少なくなったら、見た記事も出す=ひと回りしたらはじめから)
    var pool: List<FeatureStory> = out
    if (seen.isNotEmpty()) {
        val unseen = out.filterNot { featureStoryKey(it) in seen }
        if (unseen.filter { it.kicker != TOPIC_KICKER }.map { it.kicker }.distinct().size >= MIN_UNSEEN_KINDS) pool = unseen
    }
    val topics = bbs + pool.filter { it.kicker == TOPIC_KICKER }
    val onePerKind = pool.filter { it.kicker != TOPIC_KICKER }.groupBy { it.kicker }.values.map { group ->
        group[(seed.absoluteValue + group.size) % group.size]
    }.sortedBy { ((it.kicker.hashCode() + seed * 7919).absoluteValue) % 1000 }
    // 過去の話(過去の実績・あの選手は今・今日は何の日・あの日の一戦・歴代の選手・日本代表の歩み)は、毎日 PAST_SLOTS 本を必ず入れる。
    // (ほかの記事だけで本数がいっぱいになると、読み込みが進むたびに過去の話が押し出されて、一瞬出て消えていたため)
    val past = onePerKind.filter { it.kicker in PAST_KICKERS }.take(PAST_SLOTS)
    val others = onePerKind.filterNot { it.kicker in PAST_KICKERS }
    val result = mutableListOf<FeatureStory>()
    var i = 0
    var j = 0
    val limit = MAX_STORIES_PER_DAY - past.size
    while (result.size < limit && (i < others.size || j < topics.size)) {
        if (i < others.size) result += others[i++]
        if (result.size < limit && j < topics.size) result += topics[j++]
    }
    // 過去の話は2本目・5本目あたりに入れる(先頭は今の話題)
    past.forEachIndexed { k, story -> result.add(minOf(1 + k * 3, result.size), story) }
    return withFallbackPhotos(result, photoPool(players, birthdays), seed, keepWithoutPhoto)
}

/** 見ていない記事の種類がこれより少なくなったら、見た記事も出す */
private const val MIN_UNSEEN_KINDS = 3

/** 記事を見分けるための文字列(「前に見た記事」の記録に使う。Web版の storyKey と同じ) */
fun featureStoryKey(s: FeatureStory): String = "${s.kicker}|${s.team?.name ?: ""}|${s.headline}"

/** 記事に写真が無いときに代わりに使う、選手の写真の候補 */
private data class PhotoCandidate(val team: Team?, val url: String, val label: String)

/**
 * 代わりの写真の候補。ブローウィンズは選手タブの顔写真(Bリーグ公式)、
 * 4チームとも誕生日のデータにある写真(各チームの公式サイト・女子Fリーグ公式)を使う。次の対戦相手の選手は使わない。
 */
private fun photoPool(players: Map<String, TeamPlayers>, birthdays: List<PlayerBirthday>): List<PhotoCandidate> {
    val list = mutableListOf<PhotoCandidate>()
    Team.values().forEach { team ->
        players[team.name]?.players.orEmpty().forEach { p ->
            if (p.photo.isNotBlank()) list += PhotoCandidate(team, p.photo, p.head())
        }
    }
    birthdays.forEach { b ->
        if (b.photo.isNotBlank() && b.team != null) list += PhotoCandidate(b.team, b.photo, b.label)
    }
    // 得点の表などにある選手の写真も候補にする(アプリ全体で使う選手の写真の入れ物から)
    TeamPhotos.own.forEach { (team, photos) ->
        photos.forEach { list += PhotoCandidate(team, it.url, it.label) }
    }
    // 公式サイトのお知らせの写真(試合・イベント)も混ぜる(選手の顔写真ばかりにならないように)。
    // 選手の写真とお知らせの写真が交互に並ぶようにする
    val scenes = Team.values().flatMap { t -> TeamPhotos.scenes[t].orEmpty().map { PhotoCandidate(t, it.url, it.label) } }
    val players = list.distinctBy { it.url }
    val mixed = mutableListOf<PhotoCandidate>()
    for (i in 0 until maxOf(players.size, scenes.size)) {
        players.getOrNull(i)?.let { mixed += it }
        scenes.getOrNull(i)?.let { mixed += it }
    }
    return mixed.distinctBy { it.url }
}

/**
 * 写真の無い記事に、その記事のチームの選手の写真を付ける(4チームまとめての記事は、どのチームの選手でもよい)。
 * どの選手にするかは日替わりで、同じ日の記事どうしで同じ写真が重ならないようにする。
 * 候補が無いとき(読み込み前など)は写真なしのまま(画面では写真の代わりに色の帯を出す)。
 */
private fun withFallbackPhotos(
    stories: List<FeatureStory>,
    pool: List<PhotoCandidate>,
    seed: Int,
    keepWithoutPhoto: (FeatureStory) -> Boolean = { false }
): List<FeatureStory> {
    if (pool.isEmpty()) return stories
    val used = stories.map { it.photo }.filter { it.isNotBlank() }.toMutableSet()
    return stories.map { story ->
        // 写真の無い記事のうち、画面のほうで図や選手カードにする記事には、代わりの写真を付けない
        if (story.photo.isBlank() && keepWithoutPhoto(story)) return@map story
        val candidates = pool.filter { story.team == null || it.team == story.team }
        if (candidates.isEmpty()) return@map story
        // 日替わりで選手を選び、そこから順に並べる(同じ日の記事どうしで同じ写真が重ならないよう、まだ使っていない写真を先に)
        val start = Math.floorMod(story.headline.hashCode() + seed * 31, candidates.size)
        val rotated = candidates.drop(start) + candidates.take(start)
        val ordered = rotated.filter { it.url !in used } + rotated.filter { it.url in used }
        val alternates = ordered.filter { it.url != story.photo }.take(MAX_PHOTO_ALTERNATES)
        if (story.photo.isNotBlank()) {
            // 記事の写真はそのまま。読めなかったときの代わりだけ付ける
            // (相手チームの選手の写真の記事は、まず相手の別の選手の写真を試し、それも読めなければ福井側の選手の写真)
            return@map story.copy(
                photoAlternates = (story.photoAlternates + alternates.map { it.url to it.label })
                    .distinctBy { it.first }.take(MAX_PHOTO_ALTERNATES + story.photoAlternates.size)
            )
        }
        val pick = alternates.firstOrNull() ?: return@map story
        used += pick.url
        story.copy(
            photo = pick.url,
            photoCaption = pick.label,
            photoAlternates = alternates.drop(1).map { it.url to it.label }
        )
    }
}

/** 写真が読めなかったときに試す代わりの写真の数 */
// (チームの公式サイトが止まって写真がまとめて読めないときもあるので、多めに用意する)
private const val MAX_PHOTO_ALTERNATES = 8

/** 直近のトピックの記事の小見出し(この記事は毎回必ず入れる) */
private const val TOPIC_KICKER = "トピック"

/** 特集に必ず入れる直近のトピックの本数 */
private const val TOPIC_SLOTS = 2

/** 掲示板の話題を特集に出す、24時間の書き込み数の下限 */
private const val BBS_MIN_POSTS = 5

/** 一面の特集として1日に出す記事の最大本数 */
private const val MAX_STORIES_PER_DAY = 6

/** 過去の話の記事の小見出し(毎日 PAST_SLOTS 本は必ず入れる) */
private val PAST_KICKERS = setOf("過去の実績", "あの選手は今", "今日は何の日", "あの日の一戦", "歴代の選手", "日本代表の歩み")

/** 一面の特集に必ず入れる過去の話の本数 */
private const val PAST_SLOTS = 2

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
        // 前回の対戦(アプリの記録と過去の記録をまとめて、いちばん新しい試合)
        val last = it.lines().first()
        val mark = when (last.outcome) {
            GameOutcome.WIN -> "勝利"
            GameOutcome.LOSE -> "敗戦"
            GameOutcome.DRAW -> "引き分け"
        }
        // 今季の試合は「9/26(土)」、過去の記録の試合は年も付けて「2025/10/4(土)」
        val when_ = last.game?.shortLabel() ?: last.dateLabel
        "${next.opponent}とはこれまで${it.wins}勝${it.losses}敗" + (if (it.draws > 0) "${it.draws}分" else "") +
            "。前回(${when_})は${last.myScore}-${last.opponentScore}" + (if (last.pk.isNotBlank()) "(PK${last.pk})" else "") + "で$mark。"
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
    val name = team.shortName

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
                // 写真の選手の名前(一面の写真には必ず名前を出す)
                photoCaption = head,
                bigNumber = p.points,
                bigUnit = "点"
            )
        )
    }
    // 丸岡RUCK・ユナイテッド・ブルーサンダーは、チーム内の得点の表から
    val st = standings[team.name]?.scorers ?: return emptyList()
    val word = teamWordOf(team)
    val mine = st.rows.filter { it.team.contains(word) && it.goals > 0 }
    if (mine.isEmpty()) return emptyList()
    val p = mine[day.absoluteValue % mine.size]
    val head = if (p.number.isNotBlank()) "#${p.number} ${p.name}" else p.name
    val body = if (team == Team.RAC) {
        "今季${p.goals}得点で、リーグの得点ランキング${p.rank}位" +
            (p.shots?.let { "(シュート${it}本)" } ?: "") + "。" +
            (p.games?.let { "${it}試合に出場。" } ?: "")
    } else if (team == Team.THUNDER) {
        "今季のリーグHで${p.goals}得点(チーム内${p.rank}位)" +
            (p.shots?.takeIf { it > 0 }?.let { "。シュート${it}本で成功率${(p.goals * 100 / it)}%" } ?: "") + "。" +
            (p.games?.let { "${it}試合でメンバー入り。" } ?: "")
    } else {
        "今季の北信越リーグで${p.goals}得点(チーム内${p.rank}位)。" +
            listOfNotNull(p.starts?.let { "先発${it}試合" }, p.bench?.let { "ベンチ入り${it}試合" }).joinToString("・")
                .let { if (it.isNotBlank()) "$it。" else "" }
    }
    return listOf(
        FeatureStory(
            kicker = "今日の注目選手",
            team = team,
            headline = if (team == Team.THUNDER) "$head、今季${p.goals}得点" else "$head、今季${p.goals}ゴール",
            body = body,
            photo = p.photo,
            // 写真の選手の名前(一面の写真には必ず名前を出す)
            photoCaption = head,
            bigNumber = "${p.goals}",
            bigUnit = "得点"
        )
    )
}

/** 得点の表・順位表で、そのチームの行を見分けるための文字 */
private fun teamWordOf(team: Team): String = when (team) {
    Team.RAC -> "丸岡"
    Team.THUNDER -> "ブルーサンダー"
    else -> "ユナイテッド"
}

// ---------- 順位(4チーム。ブローウィンズはBリーグ公式の地区の順位表) ----------

private fun standing(team: Team, standings: Map<String, LeagueStandings>, games: List<Game>): List<FeatureStory> {
    if (team == Team.BLOWINDS) return blowindsStanding(standings, games)
    val league = standings[team.name] ?: return emptyList()
    val part = league.final?.takeIf { it.rows.isNotEmpty() } ?: league.regular ?: return emptyList()
    val word = teamWordOf(team)
    val me = part.rows.firstOrNull { it.team.contains(word) } ?: return emptyList()
    val top = part.rows.minByOrNull { it.rank } ?: return emptyList()
    val next = games.filter { it.team == team && it.isUpcoming() }.minByOrNull { it.sortKey }
    val gap = top.points - me.points
    val headline = if (me.rank == 1) "${team.shortName}、首位を走る" else "${team.shortName}、現在${me.rank}位"
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

/** ブローウィンズの地区の順位(Bリーグ公式の順位表から)。まだ1試合もしていなければ作らない */
private fun blowindsStanding(standings: Map<String, LeagueStandings>, games: List<Game>): List<FeatureStory> {
    val league = standings[Team.BLOWINDS.name] ?: return emptyList()
    val b = league.bleague ?: return emptyList()
    val own = b.own ?: return emptyList()
    val me = b.me ?: return emptyList()
    if (me.win + me.lose == 0) return emptyList()
    val team = Team.BLOWINDS
    val next = games.filter { it.team == team && it.isUpcoming() }.minByOrNull { it.sortKey }
    val headline = if (me.rank == 1) "${team.shortName}、${own.label}の首位" else "${team.shortName}、${own.label}${me.rank}位"
    val gap = if (me.rank == 1) {
        own.rows.filter { it.rank > 1 }.minByOrNull { it.rank }
            ?.let { "2位の${it.shortName}とはゲーム差${it.gamesBehind.ifBlank { "0" }}。" }.orEmpty()
    } else {
        own.rows.minByOrNull { it.rank }?.let { "首位の${it.shortName}とはゲーム差${me.gamesBehind.ifBlank { "0" }}。" }.orEmpty()
    }
    fun wl(t: String): String? = Regex("""(\d+)\s*-\s*(\d+)""").find(t)?.let { "${it.groupValues[1]}勝${it.groupValues[2]}敗" }
    val homeAway = listOfNotNull(wl(me.home)?.let { "ホーム$it" }, wl(me.away)?.let { "アウェー$it" }).joinToString("・")
    val body = "${league.league} ${own.label}で${me.win}勝${me.lose}敗(勝率${me.pct})。" + gap +
        (me.streakLabel.takeIf { it.isNotBlank() }?.let { "いま$it。" } ?: "") +
        (if (homeAway.isNotBlank()) "$homeAway。" else "") +
        (next?.let { "次は${it.shortLabel()}の${it.opponent}戦。" } ?: "")
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
    }.maxByOrNull { it.sortKey } ?: return onThisDayFromHistory(team, today)
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

/**
 * 「今日は何の日」を過去の記録(data/history.json。ユナイテッド・ブルーサンダー)から。
 * 何年か前の今日に試合があれば、いちばん近い年の試合を出す(押すと公式の結果のページを開く)
 */
private fun onThisDayFromHistory(team: Team, today: LocalDate): List<FeatureStory> {
    val hit = HistoryRepository.matchesFor(team).filter { m ->
        val d = m.localDate ?: return@filter false
        !m.isExhibition && d.year < today.year && d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth
    }.maxByOrNull { it.date } ?: return emptyList()
    val years = today.year - (hit.localDate?.year ?: today.year)
    val verb = when (hit.outcome) {
        GameOutcome.WIN -> "に勝利"
        GameOutcome.LOSE -> "に敗れた"
        GameOutcome.DRAW -> "と引き分けた"
    }
    val where = when (hit.homeAway) {
        "H" -> "ホームの"
        "A" -> "アウェイの"
        else -> ""
    }
    val scorers = hit.scorers.takeIf { it.isNotEmpty() }?.joinToString("・") { (n, g) -> if (g > 1) "$n(${g}点)" else n }
    return listOf(
        FeatureStory(
            kicker = "今日は何の日",
            team = team,
            headline = "${years}年前の今日、${hit.opponent}$verb",
            body = "${hit.dateLabel}、${hit.title.ifBlank { hit.competition }}。" +
                "$where${hit.opponent}戦は${hit.myScore}-${hit.opponentScore}" + (if (hit.pk.isNotBlank()) "(PK${hit.pk})" else "") + "。" +
                (scorers?.let { "得点は$it。" } ?: "") +
                (hit.visitors?.let { "観客は${"%,d".format(it)}人。" } ?: ""),
            bigNumber = "$years",
            bigUnit = "年前",
            url = hit.url.ifBlank { null }
        )
    )
}

/**
 * 「あの日の一戦」:過去のシーズンの、今ごろ(前後7日)の試合。無ければ過去の大勝から日替わりで1つ。
 * 過去の記録(data/history.json)のうち、今季より前の試合だけを使う
 */
private fun pastMatchStory(team: Team, today: LocalDate): List<FeatureStory> {
    val start = seasonStart(team, today)
    val past = HistoryRepository.matchesFor(team).filter { m ->
        val d = m.localDate ?: return@filter false
        !m.isExhibition && d.isBefore(start)
    }
    if (past.isEmpty()) return emptyList()
    val seed = today.toEpochDay().toInt() + team.ordinal * 31
    // 今ごろの試合(年は違っても、月日が前後7日以内)
    fun gap(m: HistoryMatch): Int {
        val d = m.localDate ?: return 999
        val a = d.withYear(today.year)
        return kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(a, today).toInt())
    }
    val near = past.filter { gap(it) in 1..7 }
    // 大勝:バスケットボール(ブローウィンズ)は15点差以上、ほかは3点差以上
    val bigWins = past.filter { it.myScore - it.opponentScore >= (if (team == Team.BLOWINDS) 15 else 3) }
    val pool = near.ifEmpty { bigWins }.ifEmpty { past.filter { it.outcome == GameOutcome.WIN } }
    if (pool.isEmpty()) return emptyList()
    val m = pool[Math.floorMod(seed, pool.size)]
    val years = today.year - (m.localDate?.year ?: today.year)
    val verb = when (m.outcome) {
        GameOutcome.WIN -> "に勝利"
        GameOutcome.LOSE -> "に敗れた"
        GameOutcome.DRAW -> "と引き分けた"
    }
    val where = when (m.homeAway) {
        "H" -> "ホームで"
        "A" -> "アウェイで"
        else -> ""
    }
    val scorers = m.scorers.takeIf { it.isNotEmpty() }?.joinToString("・") { (n, g) -> if (g > 1) "$n(${g}点)" else n }
    val lead = if (near.isNotEmpty()) "${years}年前の今ごろ" else "${m.season}シーズン"
    return listOf(
        FeatureStory(
            kicker = "あの日の一戦",
            team = team,
            headline = "$lead、${m.opponent}$verb ${m.myScore}-${m.opponentScore}",
            body = "${m.dateLabel}、${m.title.ifBlank { m.competition }}。$where${m.opponent}と対戦し、" +
                "${m.myScore}-${m.opponentScore}" + (if (m.pk.isNotBlank()) "(PK${m.pk})" else "") + "。" +
                (scorers?.let { "得点は$it。" } ?: "") +
                (m.visitors?.let { "観客は${"%,d".format(it)}人。" } ?: "") +
                "下のメニューの「チーム」で、ほかの試合も振り返れます。",
            bigNumber = "${m.myScore}-${m.opponentScore}",
            url = m.url.ifBlank { null }
        )
    )
}

/**
 * 「節目まであと」:今の選手のうち、通算の出場・得点の節目(100試合など)や歴代最多得点が近い選手。
 * 過去の記録(歴代の選手)の通算から。丸岡RUCKは出場選手の記録があるときだけ出場も数える
 */
private fun milestoneStory(team: Team): List<FeatureStory> {
    // 節目の数え方は upcomingMilestones(HistoryRecords.kt。節目の通知と同じ)
    val pick = upcomingMilestones(team).minByOrNull { it.left } ?: return emptyList()
    val photo = TeamPhotos.findByName(TeamPhotos.forTeam(team), pick.player.name)?.url ?: pick.player.photo.takeIf { it.isNotBlank() }
    return listOf(
        FeatureStory(
            photo = photo.orEmpty(),
            photoCaption = photo?.let { pick.player.name },
            kicker = "節目まであと",
            team = team,
            headline = pick.headline,
            body = pick.body + "下のメニューの「選手」の「歴代の選手」で、ほかの選手の記録も見られます。",
            bigNumber = "${pick.left}",
            bigUnit = pick.unit
        )
    )
}

/**
 * 「記録集」:最近(2週間以内)の試合で、チームの1試合の最多得点や、個人の1試合の最多得点(歴代1位)が出たとき。
 * 過去の記録(data/history.json)から数える。同じ数字の試合が前にもあれば「タイ記録」
 */
private fun recordStory(team: Team, today: LocalDate): List<FeatureStory> {
    val cutoff = today.minusDays(14).toString()
    val matches = HistoryRepository.matchesFor(team).filterNot { it.isExhibition }
    if (matches.isEmpty()) return emptyList()
    val unit = if (team == Team.BLOWINDS) "点" else "得点"
    // 個人の1試合の最多得点(試合ごとの記録が無いチームは得点者から)
    val highs = singleGameHighs(team, top = 50) { it.points }.ifEmpty { singleGameGoalsFromScorers(team, top = 50) }
    val topHigh = highs.firstOrNull()
    if (topHigh != null && topHigh.date >= cutoff && topHigh.value >= (if (team == Team.BLOWINDS) 20 else 3)) {
        val tie = highs.any { it.value == topHigh.value && it !== topHigh && it.date < topHigh.date }
        val photo = TeamPhotos.findByName(TeamPhotos.forTeam(team), topHigh.name)?.url
        return listOf(
            FeatureStory(
                photo = photo.orEmpty(),
                photoCaption = photo?.let { topHigh.name },
                kicker = "記録集",
                team = team,
                headline = "${topHigh.name}、1試合${topHigh.value}${unit}は歴代最多" + (if (tie) "タイ" else ""),
                body = "${topHigh.dateLabel}の${topHigh.opponent}戦(${topHigh.scoreLabel})で${topHigh.value}$unit。" +
                    "過去の記録(公式の試合ごとの記録)で数えた、${team.shortName}の1試合の個人最多" + (if (tie) "に並びました。" else "です。") +
                    "下のメニューの「チーム」の「記録集」で、ほかの記録も見られます。",
                bigNumber = "${topHigh.value}",
                bigUnit = unit
            )
        )
    }
    // チームの1試合の最多得点
    val best = matches.maxOf { it.myScore }
    val hit = matches.filter { it.myScore == best }.maxByOrNull { it.date }
    if (hit != null && hit.date >= cutoff && best > 0) {
        val tie = matches.count { it.myScore == best } > 1
        return listOf(
            FeatureStory(
                kicker = "記録集",
                team = team,
                headline = "${hit.opponent}戦の${best}${unit}、チームの1試合最多" + (if (tie) "タイ" else ""),
                body = "${hit.dateLabel}、${hit.title.ifBlank { hit.competition }}。${hit.opponent}に${hit.myScore}-${hit.opponentScore}。" +
                    "過去の記録で数えた、${team.shortName}の公式戦の1試合の最多得点" + (if (tie) "に並びました。" else "です。") +
                    "下のメニューの「チーム」の「記録集」で、ほかの記録も見られます。",
                bigNumber = "$best",
                bigUnit = unit,
                url = hit.url.ifBlank { null }
            )
        )
    }
    return emptyList()
}

/** 「歴代の選手」:これまでにプレーした選手(元選手を優先)を日替わりで1人。出場・得点の記録から */
private fun legendStory(team: Team, today: LocalDate): List<FeatureStory> {
    // バスケットボール(ブローウィンズ)は得点が多いので、10試合以上出た選手だけ
    val players = HistoryRepository.data[team]?.players.orEmpty()
        .filter {
            when (team) {
                // 丸岡RUCKは、ゴールランキングからのときは出場試合数が分からないので得点で選ぶ
                Team.RAC -> if (HistoryRepository.data[team]?.playersFromLineup == true) it.apps >= 10 || it.goals >= 5 else it.goals >= 5
                Team.BLOWINDS -> it.apps >= 10
                else -> it.apps >= 10 || it.goals >= 5
            }
        }
    if (players.isEmpty()) return emptyList()
    val former = players.filter { it.now == false }
    val pool = former.ifEmpty { players }
    val p = pool[Math.floorMod(today.toEpochDay().toInt() * 13 + team.ordinal, pool.size)]
    val head = (if (p.number.isNotBlank()) "#${p.number} " else "") + p.name
    val record = when (team) {
        Team.UNITED, Team.BLOWINDS -> "${p.apps}試合(先発${p.starts})に出場し${p.goals}得点"
        Team.RAC -> if (HistoryRepository.data[team]?.playersFromLineup == true)
            "${p.apps}試合(先発${p.starts})に出場し${p.goals}得点"
        else
            "通算${p.goals}得点(公式のゴールランキングから)"
        else -> "${p.apps}試合で${p.goals}得点"
    }
    // 写真:いまのチームの選手の写真 → 過去の記録の写真(ブローウィンズ)。名前を添える
    val key = p.name.replace(Regex("[\\s　]"), "")
    val photo = TeamPhotos.findByName(TeamPhotos.forTeam(team), p.name)?.url
        ?: p.photo.takeIf { it.isNotBlank() }
        // 丸岡RUCKの元選手は「あの選手は今」の写真
        ?: Alumni.players.firstOrNull { it.team == team && it.photo.isNotBlank() && it.name.replace(Regex("[\\s　]"), "") == key }?.photo
    val status = when (p.now) {
        true -> "いまもチームでプレーしています。"
        false -> "いまの選手名簿にはいない元選手です。"
        null -> ""
    }
    return listOf(
        FeatureStory(
            photo = photo.orEmpty(),
            photoCaption = photo?.let { p.name },
            kicker = "歴代の選手",
            team = team,
            headline = "$head、${p.seasonsLabel}で${p.goals}得点",
            body = "${p.seasonsLabel}(${p.seasons.size}シーズン)に${team.shortName}で$record。$status" +
                "下のメニューの「選手」の「歴代の選手」で、ほかの選手も見られます。",
            bigNumber = "${p.goals}",
            bigUnit = "得点"
        )
    )
}

/** 「日本代表の歩み」:日本代表・代表候補に選ばれたときのお知らせ(丸岡RUCK)を日替わりで1つ */
private fun nationalStory(team: Team, today: LocalDate): List<FeatureStory> {
    val list = HistoryRepository.data[team]?.national.orEmpty().filter { it.players.isNotEmpty() }
    if (list.isEmpty()) return emptyList()
    val n = list[Math.floorMod(today.toEpochDay().toInt() * 5 + team.ordinal, list.size)]
    val year = n.date.take(4)
    val names = n.players.joinToString("・")
    // 写真:記事の中の画像を最優先(本文の選手の写真 → お知らせの一番上の画像)。無いときだけ元選手・いまの選手の写真。名前を添える
    val photo = nationalPhotos(team, n).first.firstOrNull()?.let { it.url to it.label }
    return listOf(
        FeatureStory(
            photo = photo?.first.orEmpty(),
            photoCaption = photo?.second,
            kicker = if (n.kind.contains("日本")) "日本代表の歩み" else "代表選出の歩み",
            team = team,
            headline = "${year}年、${names}が${n.kind}に",
            body = "${n.date.replace("-", "/")}、${team.shortName}から${names}が${n.kind}に選ばれました(${n.title})。" +
                "これまでに${list.size}回、代表・代表候補の選出がありました。押すと当時のお知らせが開きます。",
            bigNumber = "${n.players.size}",
            bigUnit = "人",
            url = n.link.ifBlank { null }
        )
    )
}

/**
 * 日本代表に選ばれた選手の写真(1人分)。無ければ null。
 * そのお知らせの写真を最優先し、無いときだけ 元選手の写真(あの選手は今) → いまの選手の写真 で補う。
 * ほかの代表選出のお知らせの写真は、同じ写真の使い回しになるので使わない
 */
fun nationalPhoto(team: Team, n: NationalSelection, name: String): String? {
    n.photos[name]?.takeIf { it.isNotBlank() }?.let { return it }
    // 結婚などで名字が変わった選手は、今の名前で探す
    val now = NameChanges.current(team, name)
    val key = now.replace(Regex("[\\s　]"), "")
    Alumni.players.firstOrNull { it.team == team && it.name.replace(Regex("[\\s　]"), "") == key && it.photo.isNotBlank() }
        ?.let { return it.photo }
    return TeamPhotos.findByName(TeamPhotos.forTeam(team), now)?.url?.takeIf { it.isNotBlank() }
}

/**
 * 代表選出のお知らせ1件に出す写真(名前付き)。記事の中の画像を最優先する:
 * 1. 本文に選ばれた選手全員の写真があれば、1人ずつ
 * 2. 無ければ、お知らせの一番上の画像(アイキャッチ。選手の写真が並んだ画像)を1枚、選手全員の名前を付けて
 * 3. それも無いときだけ、1人ずつ 元選手の写真 → いまの選手の写真 で補う
 * 返り値の articleImage は、2 のとき true(横長の画像なので、出す側で大きさを変える)
 */
fun nationalPhotos(team: Team, n: NationalSelection): Pair<List<PhotoRef>, Boolean> {
    if (n.players.isNotEmpty() && n.players.all { !n.photos[it].isNullOrBlank() }) {
        return n.players.map { PhotoRef(n.photos.getValue(it), it) } to false
    }
    if (n.image.isNotBlank()) return listOf(PhotoRef(n.image, n.players.joinToString("・"))) to true
    return n.players.mapNotNull { name -> nationalPhoto(team, n, name)?.let { PhotoRef(it, name) } } to false
}

// ---------- ニュース・招待 ----------

/** 直近3日のトピック(ニュース)を新しい順に最大 TOPIC_SLOTS 本。同じ記事(見出しの頭が同じ)は1本にまとめる */
private fun topNews(teams: List<Team>, news: List<RemoteInvitationAlert>, today: LocalDate): List<FeatureStory> {
    val border = today.minusDays(3).atStartOfDay(JST).toInstant()
    val items = news
        .filter { n -> n.title.isNotBlank() && teams.any { it.name == n.teamId } && (n.eventInstant()?.isAfter(border) == true) }
        .sortedByDescending { it.eventInstant() ?: java.time.Instant.EPOCH }
        .distinctBy { it.title.replace(Regex("[\\s　]"), "").take(15) }
        .take(TOPIC_SLOTS)
    return items.map { item ->
        FeatureStory(
            kicker = TOPIC_KICKER,
            team = Team.values().firstOrNull { it.name == item.teamId },
            headline = item.title,
            body = listOf(item.sourceLabel(), item.timeLabel()).filter { it.isNotBlank() }.joinToString("・") + "。押すと記事を開きます。",
            photo = item.image,
            url = item.link
        )
    }
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
            // 写真の選手の名前(一面の写真には必ず名前を出す)
            photoCaption = pts.head(),
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
                // 写真の選手の名前(一面の写真には必ず名前を出す)
                photoCaption = p.head(),
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
                // 写真の選手の名前(一面の写真には必ず名前を出す)
                photoCaption = p.head(),
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
            // 写真の選手の名前(一面の写真には必ず名前を出す)
            photoCaption = doubles.first().first.head(),
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
                // 写真の選手の名前(一面の写真には必ず名前を出す)
                photoCaption = p.head(),
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
            photo = p.photo,
            // 写真の選手の名前(一面の写真には必ず名前を出す)
            photoCaption = p.head()
        )
    )
}

/** 次の相手の要注意選手と近況(展望のデータから) */
/**
 * 次の対戦相手の選手の写真(URL と「#23 渡邉 雄大(堺リエゾン)」のような名前)。
 * - ブローウィンズ : 選手タブの相手の全選手(Bリーグ公式)
 * - 丸岡RUCK・ブルーサンダー: 得点の表の相手チームの選手(写真があるものだけ)
 * - ユナイテッド   : 相手の選手の写真は無いので空
 * 得点・成績が上の選手から並べる
 */
private fun opponentPhotos(
    team: Team,
    next: Game,
    players: Map<String, TeamPlayers>,
    standings: Map<String, LeagueStandings>
): List<Pair<String, String>> {
    val opp = next.opponent
    return when (team) {
        Team.BLOWINDS -> players[BLOWINDS_OPP_KEY]?.takeIf { it.gameId.isBlank() || it.gameId == next.id }?.players.orEmpty()
            .filter { it.photo.isNotBlank() }
            .sortedByDescending { it.points?.toDoubleOrNull() ?: -1.0 }
            .map { it.photo to "${it.head()}($opp)" }
        Team.RAC, Team.THUNDER -> standings[team.name]?.scorers?.rows.orEmpty()
            .filter { r -> r.photo.isNotBlank() && (r.team.contains(opp) || opp.contains(r.team)) }
            .sortedWith(compareByDescending<ScorerRow> { it.goals }.thenBy { it.rank })
            .map { r -> r.photo to ((if (r.number.isNotBlank()) "#${r.number} " else "") + "${r.name}($opp)") }
        else -> emptyList()
    }
}

private fun opponentWatch(
    team: Team,
    games: List<Game>,
    previews: Map<String, GamePreview>,
    players: Map<String, TeamPlayers>,
    standings: Map<String, LeagueStandings>
): List<FeatureStory> {
    val next = games.filter { it.team == team && it.isUpcoming() }.minByOrNull { it.sortKey } ?: return emptyList()
    val pv = previews[next.id] ?: return emptyList()
    val out = mutableListOf<FeatureStory>()
    // 相手の選手の写真(「要注意人物」はその選手の写真、「相手を知る」は相手でいちばん成績のよい選手の写真)
    val oppPhotos = opponentPhotos(team, next, players, standings)
    fun photoFor(name: String?): Pair<String, String>? {
        if (name != null) {
            val key = playerNameKey(name)
            oppPhotos.firstOrNull { (_, label) -> playerNameKey(label.replace(Regex("^#\\S+\\s+"), "").substringBefore("(")) == key }
                ?.let { return it }
        }
        return oppPhotos.firstOrNull()
    }
    pv.keyPlayers.firstOrNull()?.let { k ->
        val ph = photoFor(k.name)
        out += FeatureStory(
            kicker = "要注意人物",
            team = team,
            headline = "${next.opponent}の${if (k.number.isNotBlank()) "#${k.number} " else ""}${k.name}に注意",
            body = "次の相手${next.opponent}(${next.shortLabel()})で警戒したいのは${k.name}" +
                (if (k.position.isNotBlank()) "(${k.position})" else "") + "。${k.stat}。" +
                (pv.keyPlayers.drop(1).takeIf { it.isNotEmpty() }?.let { rest -> "ほかに" + rest.joinToString("、") { it.name } + "も。" } ?: ""),
            gameId = next.id,
            photo = ph?.first.orEmpty(),
            photoCaption = ph?.second,
            photoAlternates = oppPhotos.filter { it != ph }.take(MAX_PHOTO_ALTERNATES)
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
        ).let { story ->
            // 相手の注目選手(いなければ相手でいちばん成績のよい選手)の写真を付ける
            val ph = photoFor(pv.keyPlayers.firstOrNull()?.name)
            if (ph == null) story else story.copy(
                photo = ph.first,
                photoCaption = ph.second,
                photoAlternates = oppPhotos.filter { it != ph }.take(MAX_PHOTO_ALTERNATES)
            )
        }
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
            headline = "${team.shortName}、今季の残りは${left.size}試合",
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
            // 写真の選手の名前(一面の写真には必ず名前を出す)
            photoCaption = if (ours.photo.isNotBlank()) ours.name else leader.name,
            bigNumber = "${leader.goals}",
            bigUnit = "得点"
        )
    )
}

/**
 * 「あの選手は今」:以前チームにいた選手の近況(日替わりで1人)。
 * 今もリーグのほかのクラブでプレーしている選手を先に選ぶ(今季の得点が分かれば添える)
 */
private fun alumniStory(team: Team, today: LocalDate): List<FeatureStory> {
    val list = Alumni.players.filter { it.team == team }
    if (list.isEmpty()) return emptyList()
    val active = list.filter { it.now.isNotBlank() }
    val pool = active.ifEmpty { list }
    val p = pool[Math.floorMod(today.toEpochDay().toInt(), pool.size)]
    val when_ = runCatching { LocalDate.parse(p.left) }.getOrNull()?.let { "${it.year}年${it.monthValue}月" } ?: "以前"
    val head = if (p.number.isNotBlank()) "元#${p.number} ${p.name}" else p.name
    val short = team.shortName
    // 写真はチームの試合・イベントの写真(今の選手の顔写真だと、その選手と見間違えるため)
    val scenes = TeamPhotos.rotate(TeamPhotos.scenesFor(team), "alumni-" + p.name)
    val story = if (p.now.isNotBlank()) {
        listOf(
            FeatureStory(
                kicker = "あの選手は今",
                team = team,
                headline = "$head、いまは${p.now}で" + (p.goals?.let { "今季${it}得点" } ?: "プレー"),
                body = "${when_}に${short}を${p.kind}した${p.name}は、いま${p.now}" +
                    (if (p.nowNumber.isNotBlank()) "(#${p.nowNumber})" else "") + "でプレーしています。" +
                    (p.goals?.let { "今季はここまで${it}得点(リーグの得点ランキングより)。" } ?: "") +
                    "${short}との対戦では古巣との一戦になります。",
                bigNumber = p.goals?.toString(),
                bigUnit = p.goals?.let { "得点" },
                url = p.nowUrl.ifBlank { p.link }.ifBlank { null }
            )
        )
    } else {
        listOf(
            FeatureStory(
                kicker = "あの選手は今",
                team = team,
                headline = if (p.left.isBlank()) "$head、${short}の元選手" else "$head、${when_}に${p.kind}",
                body = (if (p.left.isBlank()) "${p.name}は${short}の元選手。${p.note.ifBlank { "" }}${if (p.note.isNotBlank()) "。" else ""}"
                else "${when_}に${short}を${p.kind}した${p.name}" + (if (p.number.isNotBlank()) "(当時#${p.number})" else "") + "。") +
                    (if (p.dest.isNotBlank()) "移籍先は${p.dest}。" else "") +
                    "いまはリーグの選手名簿には載っていません。押すと当時のお知らせが開きます。",
                url = p.link.ifBlank { null }
            )
        )
    }
    // 退団・移籍のお知らせにその選手の写真があれば、それを使う(名前を添える)
    if (p.photo.isNotBlank()) {
        return story.map {
            it.copy(photo = p.photo, photoCaption = head, photoAlternates = scenes.take(MAX_PHOTO_ALTERNATES).map { s -> s.url to s.label })
        }
    }
    val first = scenes.firstOrNull() ?: return story
    return story.map {
        it.copy(photo = first.url, photoCaption = first.label, photoAlternates = scenes.drop(1).take(MAX_PHOTO_ALTERNATES).map { s -> s.url to s.label })
    }
}

/** 「過去の実績」:チームの歩み・過去の成績(TeamHistory.kt。日替わりで1つ。出典のページを開ける) */
private fun historyStory(team: Team, today: LocalDate): List<FeatureStory> {
    val list = TEAM_HISTORY.filter { it.team == team }
    if (list.isEmpty()) return emptyList()
    val h = list[Math.floorMod(today.toEpochDay().toInt() * 7 + team.ordinal, list.size)]
    val ago = today.year - h.year
    return listOf(
        FeatureStory(
            kicker = "過去の実績",
            team = team,
            headline = "${h.whenLabel}、${h.title}",
            body = h.detail + (if (ago > 0) "(${ago}年前)" else "") + "\n出典:${h.source}",
            bigNumber = "${h.year}",
            bigUnit = "年",
            url = h.url
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
    // 「接戦」とみなす点差(バスケは5点、ハンドボールは2点、サッカー・フットサルは1点まで)
    val limit = when (team) {
        Team.BLOWINDS -> 5
        Team.THUNDER -> 2
        else -> 1
    }
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
            body = "あなたが現地で見た${team.shortName}の試合は${seen.size}試合。" +
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

/** この先7日間の試合(4チームまとめて) */
private fun weekAhead(teams: List<Team>, games: List<Game>): List<FeatureStory> {
    val week = games.filter { g -> g.team in teams && g.isUpcoming() && (g.daysUntil() ?: 99L) in 0L..7L }.sortedBy { it.sortKey }
    if (week.size < 2) return emptyList()
    return listOf(
        FeatureStory(
            kicker = "今週の試合",
            team = if (teams.size == 1) teams.first() else null,
            headline = "この1週間で${week.size}試合",
            body = week.joinToString("\n") { "・${it.shortLabel()} ${it.team.shortName} vs ${it.opponent}(${if (it.isHome) "H" else "A"})" },
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
            body = list.joinToString("、") { "${it.birthday.dayOfMonth}日 ${it.name}(${it.team?.let { t -> t.shortName } ?: ""})" } + "。",
            // これから誕生日を迎える選手(今月)の写真。もう全員過ぎていれば最初の選手
            photo = (list.firstOrNull { it.birthday.dayOfMonth >= today.dayOfMonth } ?: list.first()).photo,
            // 写真の選手の名前(一面の写真には必ず名前を出す)
            photoCaption = (list.firstOrNull { it.birthday.dayOfMonth >= today.dayOfMonth } ?: list.first()).label,
            bigNumber = "${list.size}",
            bigUnit = "人"
        )
    )
}

// ---------- 掲示板の話題 ----------

/**
 * ブローウィンズ掲示板の24時間の書き込み数・よく出た言葉・反応の多い投稿。
 * 書き込みが BBS_MIN_POSTS 件より少ない日や、ブローウィンズを選んでいないときは作らない。
 * 投稿の内容は短い抜粋だけにし、匿名掲示板からの自動集計であることを書き添える。
 */
private fun bbsTalk(teams: List<Team>, buzz: BbsBuzz?): List<FeatureStory> {
    if (buzz == null || Team.BLOWINDS !in teams || buzz.count24h < BBS_MIN_POSTS) return emptyList()
    val n = buzz.count24h
    val countText = if (buzz.atLeast) "${n}件以上" else "${n}件"
    val usual = buzz.usualPerDay
    val ratio = if (usual != null && usual >= 1.0) n / usual else null
    val hot = ratio != null && ratio >= 1.5 && n >= 10
    val lines = mutableListOf<String>()
    if (ratio != null) {
        lines += if (hot) "ふだん(1日およそ${usual!!.toInt().coerceAtLeast(1)}件)の%.1f倍の書き込みです。".format(ratio)
        else "ふだんは1日およそ${usual!!.toInt().coerceAtLeast(1)}件です。"
    }
    if (buzz.words.isNotEmpty()) lines += "よく出た言葉:" + buzz.words.joinToString("") { "「$it」" }
    buzz.topPost?.let { p ->
        val reaction = listOfNotNull(
            buzz.topReplies.takeIf { it > 0 }?.let { "返信${it}件" },
            p.likes.takeIf { it > 0 }?.let { "いいね$it" }
        ).joinToString("・")
        lines += "反応が多かった投稿($reaction):「${BbsBuzzRepository.excerpt(p)}」"
    }
    lines += "※匿名掲示板の書き込みから自動で数えています。押すと掲示板を開きます。"
    return listOf(
        FeatureStory(
            kicker = "掲示板",
            team = Team.BLOWINDS,
            headline = if (hot) "掲示板が盛り上がり中、24時間で${countText}の書き込み" else "掲示板、この24時間で${countText}の書き込み",
            body = lines.joinToString("\n"),
            bigNumber = "$n",
            bigUnit = if (buzz.atLeast) "件以上" else "件",
            openBbs = true,
            bbsPostNo = buzz.topPost?.no
        )
    )
}

// ---------- 身長・出身地(公式サイトの選手ページから) ----------

/** チームでいちばん背が高い選手と、チームの平均身長・いちばん小柄な選手(身長が分かる選手が3人以上のとき) */
private fun tallest(team: Team, birthdays: List<PlayerBirthday>): List<FeatureStory> {
    val list = birthdays.filter { it.team == team && it.heightCm != null }
    if (list.size < 3) return emptyList()
    val top = list.maxByOrNull { it.heightCm!! }!!
    val low = list.minByOrNull { it.heightCm!! }!!
    val avg = list.mapNotNull { it.heightCm }.average()
    val top3 = list.sortedByDescending { it.heightCm }.take(3)
    return listOf(
        FeatureStory(
            kicker = "高さ比べ",
            team = team,
            headline = "${team.shortName}でいちばん背が高いのは${top.name}選手、${top.height}cm",
            body = "身長が分かる${list.size}人の平均は%.1fcm。".format(avg) +
                "上位は" + top3.joinToString("、") { "${it.name}(${it.height}cm)" } + "。" +
                "いちばん小柄なのは${low.name}選手(${low.height}cm)です。\n※公式サイトの選手紹介より",
            photo = top.photo,
            // 写真の選手の名前(一面の写真には必ず名前を出す)
            photoCaption = top.label,
            bigNumber = top.height,
            bigUnit = "cm"
        )
    )
}

/** 福井県出身の選手 */
private fun localPlayers(team: Team, birthdays: List<PlayerBirthday>): List<FeatureStory> {
    val withHome = birthdays.filter { it.team == team && it.hometown.isNotBlank() }
    val local = withHome.filter { it.hometown.contains("福井") }
    if (local.isEmpty() || withHome.size < 3) return emptyList()
    return listOf(
        FeatureStory(
            kicker = "地元出身",
            team = team,
            headline = "${team.shortName}の福井県出身の選手は${local.size}人",
            body = local.joinToString("、") { it.label } + "。" +
                "出身地が分かる${withHome.size}人のうち${local.size}人が地元・福井の選手です。\n※公式サイトの選手紹介より",
            photo = local[Math.floorMod(LocalDate.now(JST).toEpochDay().toInt(), local.size)].photo,
            // 写真の選手の名前(一面の写真には必ず名前を出す)
            photoCaption = local[Math.floorMod(LocalDate.now(JST).toEpochDay().toInt(), local.size)].label,
            bigNumber = "${local.size}",
            bigUnit = "人"
        )
    )
}
