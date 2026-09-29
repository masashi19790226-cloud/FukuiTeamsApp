package com.fukuiteams.app.data

import com.fukuiteams.app.model.Game
import java.util.Calendar

/**
 * MockDataのdateLabel("2026/9/26"のような年/月/日形式)とtimeLabel("14:05"、
 * または「時間未定」)から、その試合の開始時刻をミリ秒(エポック時間)に変換する。
 * GameDetailScreenのカレンダー登録機能と、通知のスケジューリングの両方で使う。
 * 時刻が未定の試合は 0 を返す(呼び出し側で無視する)。
 */
fun Game.startEpochMillis(): Long {
    val timeParts = timeLabel.split(":")
    val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: return 0L
    val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: return 0L

    val dateParts = dateLabel.split("/")
    val year = dateParts.getOrNull(0)?.toIntOrNull() ?: return 0L
    val month = (dateParts.getOrNull(1)?.toIntOrNull() ?: return 0L) - 1
    val day = dateParts.getOrNull(2)?.toIntOrNull() ?: return 0L

    val cal = Calendar.getInstance()
    cal.set(year, month, day, hour, minute, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * 現在時刻を、Gameのsortkeyと同じ "yyyyMMdd-HHmm" 形式の文字列にしたもの。
 * 文字列同士の比較で「終わった試合かどうか」を判定するのに使う。
 */
fun currentSortKey(): String {
    val cal = Calendar.getInstance()
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    val hh = cal.get(Calendar.HOUR_OF_DAY)
    val mm = cal.get(Calendar.MINUTE)
    return "%04d%02d%02d-%02d%02d".format(y, m, d, hh, mm)
}

/**
 * この試合がまだ始まっていない(=一覧に表示すべき)かどうか。
 * 開始時刻が「時間未定」の試合は時刻で比べられないので、試合当日の終わりまでは「これから」とみなす
 * (以前は当日の0時で「過去の試合」に移っていた)。
 */
fun Game.isUpcoming(): Boolean {
    val now = currentSortKey()
    val hasTime = Regex("""\d{1,2}:\d{2}""").matches(timeLabel.trim())
    return if (hasTime) sortKey >= now else sortKey.take(8) >= now.take(8)
}


/** 今日(日本時間)の試合か。 */
fun Game.isToday(): Boolean {
    val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"))
    val parts = dateLabel.split("/").mapNotNull { it.trim().toIntOrNull() }
    return parts.size == 3 && parts[0] == today.year && parts[1] == today.monthValue && parts[2] == today.dayOfMonth
}
