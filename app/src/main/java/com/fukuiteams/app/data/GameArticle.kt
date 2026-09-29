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
 *   観戦メモより:『最後の3ポイントで会場が揺れた』」
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
    val memo = comment.trim().takeIf { it.isNotEmpty() }?.let { "観戦メモより:「$it」" }.orEmpty()
    return first + second + memo
}

/** 投稿用のハッシュタグ(チーム名から空白や記号を除いたもの)。 */
private fun Game.hashtag(): String = "#" + team.displayName.replace(Regex("[\\s・]"), "")

/**
 * X / Instagram などへ投稿する文章。Xの文字数制限(日本語はおよそ140字)に収まるよう、コメントは短く切る。
 */
fun shareText(game: Game, result: RemoteGameResult?, outcome: GameOutcome?, comment: String): String {
    val mark = when (outcome) {
        GameOutcome.WIN -> "WIN"
        GameOutcome.LOSE -> "LOSE"
        GameOutcome.DRAW -> "DRAW"
        null -> ""
    }
    val score = result?.let { " ${it.myScore}-${it.opponentScore}" }.orEmpty()
    val head = "【${game.team.displayName}】${game.shortDate()} vs ${game.opponent}$score $mark".trim()
    val tags = "${game.hashtag()} #ふくスポ"
    val room = 130 - head.length - tags.length
    val memo = comment.trim().let { if (it.length > room && room > 1) it.take(room - 1) + "…" else it }
    return listOf(head, memo, tags).filter { it.isNotBlank() }.joinToString("\n")
}
