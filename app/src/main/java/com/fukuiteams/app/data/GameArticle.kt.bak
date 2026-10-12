package com.fukuiteams.app.data

import com.fukuiteams.app.model.Game

/**
 * 試合結果と、自分で書いたコメント(観戦メモ)から文章を組み立てる。
 * ・一面「速報」のリード文(記事の書き出し)
 * ・X / Instagram などへ投稿する文章
 * 数字は取得した結果(または自分で記録した勝敗)だけを使い、作らない。
 */

private fun Game.shortDate(): String = "${dateLabel.split("/").drop(1).joinToString("/")}($dayOfWeek)"

private fun outcomeVerb(outcome: GameOutcome?): String = when (outcome) {
    GameOutcome.WIN -> "で勝利"
    GameOutcome.LOSE -> "で敗れた"
    GameOutcome.DRAW -> "で引き分けた"
    null -> ""
}

/**
 * 一面「速報」のリード文。例:
 * 「10/3(土)、ホームのセーレン・ドリームアリーナで行われた金沢戦。福井ブローウィンズは85-80で勝利した。
 *   これで3連勝。今季成績は5勝2敗。最後の3ポイントで会場が揺れた!」
 * ・書き出しの言い回しは数通りから試合ごとに決まったものを使う
 * ・ctx があれば、連勝・連敗、前回対戦との関係、今季の成績を書き添える(取得した結果から数えたものだけ)
 * ・コメントは記事の続きとしてそのまま載せる
 */
fun resultLead(
    game: Game,
    result: RemoteGameResult?,
    outcome: GameOutcome?,
    comment: String,
    ctx: ResultContext? = null,
    // その日の試合開始ごろの天気(ユナイテッドの試合だけ渡す)
    weather: MatchWeather? = null
): String {
    val venue = game.venue.takeIf { it.isNotBlank() && !it.contains("調整中") }
    val where = if (game.isHome) "ホームの" else "敵地・"
    val opener = when {
        ctx?.seasonOpener == true -> "今季の開幕戦。"
        ctx?.homeOpener == true -> "今季のホーム開幕戦。"
        else -> ""
    }
    val first = if (venue != null) {
        game.pick(
            listOf(
                "${game.shortDate()}、${where}${venue}で行われた${game.opponent}戦。",
                "${game.shortDate()}、${venue}での${game.opponent}戦。",
                "${game.shortDate()}の${game.opponent}戦(${venue})。",
                "${venue}に${game.opponent}を迎えた${game.shortDate()}の一戦。".takeIf { game.isHome }
                    ?: "${game.shortDate()}、敵地${venue}に乗り込んでの${game.opponent}戦。",
                "${game.shortDate()}、${game.team.displayName}は${venue}で${game.opponent}と対戦した。"
            ),
            salt = 1
        )
    } else {
        "${game.shortDate()}の${game.opponent}戦。"
    }
    val score = result?.let { "${it.myScore}-${it.opponentScore}" }.orEmpty()
    // 2文目で主語を繰り返さないよう、1文目にチーム名を書いたときは「福井は」にする
    val team = if (first.contains(game.team.displayName)) "福井" else game.team.displayName
    val second = when {
        score.isNotEmpty() && outcome == GameOutcome.WIN -> game.pick(
            listOf(
                "${team}は${score}で勝利した。",
                "${team}が${score}で${game.opponent}を下した。",
                "${team}は${score}で白星を挙げた。",
                "${team}が${score}で勝ち切った。",
                "${team}は${score}で${game.opponent}を破った。"
            ),
            salt = 2
        )
        score.isNotEmpty() && outcome == GameOutcome.LOSE -> game.pick(
            listOf(
                "${team}は${score}で敗れた。",
                "${team}は${score}で${game.opponent}に屈した。",
                "${team}は${score}で黒星を喫した。",
                "${team}は${score}で${game.opponent}に及ばなかった。",
                "${team}は${score}で白星を逃した。"
            ),
            salt = 2
        )
        score.isNotEmpty() && outcome == GameOutcome.DRAW -> game.pick(
            listOf("${team}は${score}で引き分けた。", "${team}は${score}のドロー。", "${score}で両者譲らず、引き分けに終わった。"),
            salt = 2
        )
        score.isNotEmpty() -> "${team}は$score${outcomeVerb(outcome)}。"
        outcome != null -> "${team}は${outcomeVerb(outcome).removePrefix("で")}。"
        else -> ""
    }

    // 試合の中身(天気・点差・得点の多さ。取得した情報から分かることだけ)
    val flow = StringBuilder()
    weather?.takeIf { it.summary != "−" }?.let { w ->
        val hour = game.timeLabel.substringBefore(":").toIntOrNull()
        val rain = w.rainChance?.let { "、降水確率${it}%" }.orEmpty()
        flow.append(
            game.pick(
                listOf(
                    "試合開始${hour?.let { "の${it}時" } ?: ""}ごろの${w.placeLabel}は${w.summary}、気温${w.temperature}℃${rain}。",
                    "${w.summary}の${w.placeLabel}、気温${w.temperature}℃の中でのキックオフとなった。"
                ),
                salt = 6
            )
        )
    }
    if (result != null && outcome != null) {
        val diff = kotlin.math.abs(result.myScore - result.opponentScore)
        val sum = result.myScore + result.opponentScore
        val basketball = game.team == com.fukuiteams.app.model.Team.BLOWINDS
        val unit = if (basketball) "点" else "点"
        when {
            basketball && sum >= 180 -> flow.append("両チーム合わせて${sum}点の打ち合いとなった。")
            basketball && sum <= 130 -> flow.append("両チームとも${maxOf(result.myScore, result.opponentScore)}点以下のロースコアの展開。")
            !basketball && sum >= 6 -> flow.append("両チーム合わせて${sum}ゴールが生まれた。")
        }
        when {
            outcome == GameOutcome.WIN && diff <= (if (basketball) 3 else 1) ->
                flow.append(game.pick(listOf("${diff}${unit}差の接戦を制した。", "僅差の勝負を制した。"), salt = 3))
            outcome == GameOutcome.LOSE && diff <= (if (basketball) 3 else 1) ->
                flow.append(game.pick(listOf("あと${diff}${unit}が届かなかった。", "わずか${diff}${unit}差の惜しい敗戦。"), salt = 3))
            outcome == GameOutcome.WIN && diff >= (if (basketball) 20 else 4) ->
                flow.append("${diff}${unit}差をつける快勝だった。")
            outcome == GameOutcome.LOSE && diff >= (if (basketball) 20 else 4) ->
                flow.append("${diff}${unit}差をつけられる完敗だった。")
            !basketball && outcome == GameOutcome.WIN && result.opponentScore == 0 ->
                flow.append("守っては相手を無得点に抑えた。")
        }
    }

    // 前後関係(取得した結果から数えたものだけ)
    val extra = StringBuilder()
    if (ctx != null && outcome != null) {
        val streak = ctx.streak
        val broken = ctx.brokenStreak
        when {
            streak != null && outcome == GameOutcome.WIN -> extra.append(game.pick(listOf("これで${streak}連勝。", "連勝を${streak}に伸ばした。"), salt = 4))
            streak != null && outcome == GameOutcome.LOSE -> extra.append(game.pick(listOf("これで${streak}連敗となった。", "連敗は${streak}に伸びた。"), salt = 4))
            streak != null && outcome == GameOutcome.DRAW -> extra.append("${streak}試合続けての引き分け。")
            broken != null && outcome == GameOutcome.WIN -> extra.append("連敗を${broken}で止めた。")
            broken != null && outcome == GameOutcome.LOSE -> extra.append("連勝は${broken}で止まった。")
        }
        val b2b = ctx.backToBackResult
        val pm = ctx.prevMeeting
        val pr = ctx.prevMeetingResult
        if (b2b != null) {
            val bo = b2b.outcome()
            extra.append(
                when {
                    bo == GameOutcome.WIN && outcome == GameOutcome.WIN -> "前の試合に続く勝利で、${game.opponent}との連戦を2連勝で終えた。"
                    bo == GameOutcome.LOSE && outcome == GameOutcome.WIN -> "前の試合(${b2b.myScore}-${b2b.opponentScore})の雪辱を果たし、${game.opponent}との連戦を1勝1敗とした。"
                    bo == GameOutcome.WIN && outcome == GameOutcome.LOSE -> "前の試合は勝っていたが、${game.opponent}との連戦は1勝1敗となった。"
                    bo == GameOutcome.LOSE && outcome == GameOutcome.LOSE -> "前の試合に続いて敗れ、${game.opponent}との連戦は2連敗となった。"
                    else -> "前の試合は${b2b.myScore}-${b2b.opponentScore}だった。"
                }
            )
        } else if (pm != null && pr != null) {
            val pd = pm.dateLabel.split("/").drop(1).joinToString("/")
            val po = pr.outcome()
            val mark = resultMark(po) + "${pr.myScore}-${pr.opponentScore}"
            extra.append(
                when {
                    po == GameOutcome.LOSE && outcome == GameOutcome.WIN -> "${pd}の前回対戦(${mark})の雪辱を果たした。"
                    po == GameOutcome.WIN && outcome == GameOutcome.WIN -> "${pd}の前回対戦(${mark})に続いて${game.opponent}に勝った。"
                    po == GameOutcome.WIN && outcome == GameOutcome.LOSE -> "${pd}の前回対戦(${mark})では勝っていた。"
                    po == GameOutcome.LOSE && outcome == GameOutcome.LOSE -> "${pd}の前回対戦(${mark})に続いて${game.opponent}に敗れた。"
                    else -> "${pd}の前回対戦は${mark}だった。"
                }
            )
        }
        val w = ctx.seasonWin
        val l = ctx.seasonLose
        val d = ctx.seasonDraw
        if (w != null && l != null && w + l + (d ?: 0) >= 2) {
            extra.append("今季成績は${w}勝${l}敗" + (if ((d ?: 0) > 0) "${d}分" else "") + "。")
        }
        // ホーム(アウェイ)での成績。3試合以上あるときだけ
        val vw = ctx.venueWin
        val vl = ctx.venueLose
        val vd = ctx.venueDraw ?: 0
        if (vw != null && vl != null && vw + vl + vd >= 3) {
            extra.append("${if (game.isHome) "ホーム" else "アウェイ"}では${vw}勝${vl}敗" + (if (vd > 0) "${vd}分" else "") + "。")
        }
    }

    // 次の試合の予告
    val nextLine = ctx?.nextGame?.let { n ->
        val nd = "${n.dateLabel.split("/").drop(1).joinToString("/")}(${n.dayOfWeek})"
        game.pick(
            listOf(
                "次戦は${nd}、${if (n.isHome) "ホーム" else "アウェイ"}で${n.opponent}と対戦する。",
                "次は${nd}に${n.opponent}と戦う。"
            ),
            salt = 5
        )
    }.orEmpty()

    // コメントは引用の形にせず、記事の続きの文としてそのまま載せる(文末に句点が無ければ付ける)
    val body = comment.trim().replace("\n", "").let { c ->
        when {
            c.isEmpty() -> ""
            c.last() in "。!?!?」)" -> c
            else -> "$c。"
        }
    }
    return opener + first + second + flow + extra + body + nextLine
}

/** 勝敗の記号(○●△) */
private fun resultMark(outcome: GameOutcome): String = when (outcome) {
    GameOutcome.WIN -> "○"
    GameOutcome.LOSE -> "●"
    GameOutcome.DRAW -> "△"
}

/**
 * コメントから一面「速報」の大見出しを作る。コメントが無ければ null(いつもの見出しを使う)。
 * ・最初の一文(。!?や改行までの部分)を使う
 * ・見出しらしく文末の「。」は取る(「!」は残す)
 * ・20字を超えるときは読点「、」の手前で区切り、区切れなければ18字+「…」
 * 例:「最後の3ポイントで会場が揺れた!ブラウン選手すごかった」→「最後の3ポイントで会場が揺れた!」
 */
fun commentHeadline(comment: String): String? {
    val first = comment.trim()
        .split(Regex("(?<=[。!?!?])|\n"))
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() } ?: return null
    var h = first.removeSuffix("。").trim()
    if (h.length > 20) {
        val cut = h.lastIndexOf('、', 20)
        h = if (cut >= 8) h.substring(0, cut) else h.take(18) + "…"
    }
    return h.ifBlank { null }
}

/** 投稿用のハッシュタグ。丸岡RUCKは公式の表記に合わせて「#福井丸岡RUCK」。 */
private fun Game.hashtag(): String = when (team) {
    com.fukuiteams.app.model.Team.BLOWINDS -> "#福井ブローウィンズ"
    com.fukuiteams.app.model.Team.RAC -> "#福井丸岡RUCK"
    com.fukuiteams.app.model.Team.UNITED -> "#福井ユナイテッド"
}

/** 投稿用の日付「2026年9月27日(土)」。 */
private fun Game.fullDate(): String {
    val p = dateLabel.split("/")
    return if (p.size == 3) "${p[0]}年${p[1]}月${p[2]}日($dayOfWeek)" else "$dateLabel($dayOfWeek)"
}

/**
 * X / Instagram などへ投稿する文章。Xの文字数制限(日本語はおよそ140字)に収まるよう、コメントは短く切る。
 */
fun shareText(game: Game, result: RemoteGameResult?, outcome: GameOutcome?, comment: String): String {
    val mark = when (outcome) {
        GameOutcome.WIN -> "勝利"
        GameOutcome.LOSE -> "敗戦"
        GameOutcome.DRAW -> "引き分け"
        null -> ""
    }
    // 1行目:日付とHOME/AWAY 2行目:スコア(結果が取れていれば「福井ブローウィンズ 85-80 金沢」)と勝敗
    val dateLine = "【試合結果】${game.fullDate()} ${if (game.isHome) "HOME" else "AWAY"}"
    val scoreLine = if (result != null) {
        "${game.team.displayName} ${result.myScore}-${result.opponentScore} ${game.opponent} $mark"
    } else {
        "${game.team.displayName} vs ${game.opponent} $mark"
    }.trim()
    val tags = game.hashtag()
    val room = 130 - dateLine.length - scoreLine.length - tags.length
    val memo = comment.trim().let { if (it.length > room && room > 1) it.take(room - 1) + "…" else it }
    return listOf(dateLine, scoreLine, memo, tags).filter { it.isNotBlank() }.joinToString("\n")
}
