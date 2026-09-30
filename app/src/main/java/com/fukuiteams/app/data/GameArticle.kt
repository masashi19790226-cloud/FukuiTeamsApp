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
 * 「10/3(土)、セーレン・ドリームアリーナで行われた金沢戦。福井ブローウィンズは85-80で勝利。
 *   最後の3ポイントで会場が揺れた!」(コメントは記事の続きとしてそのまま載せる)
 */
fun resultLead(game: Game, result: RemoteGameResult?, outcome: GameOutcome?, comment: String): String {
    val venue = game.venue.takeIf { it.isNotBlank() && !it.contains("調整中") }
    val first = if (venue != null) "${game.shortDate()}、${venue}で行われた${game.opponent}戦。"
    else "${game.shortDate()}の${game.opponent}戦。"
    val score = result?.let { "${it.myScore}-${it.opponentScore}" }.orEmpty()
    val second = when {
        score.isNotEmpty() -> "${game.team.displayName}は$score${outcomeVerb(outcome)}。"
        outcome != null -> "${game.team.displayName}は${outcomeVerb(outcome).removePrefix("で")}。"
        else -> ""
    }
    // コメントは引用の形にせず、記事の続きの文としてそのまま載せる(文末に句点が無ければ付ける)
    val body = comment.trim().replace("\n", "").let { c ->
        when {
            c.isEmpty() -> ""
            c.last() in "。!?!?」)" -> c
            else -> "$c。"
        }
    }
    return first + second + body
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
