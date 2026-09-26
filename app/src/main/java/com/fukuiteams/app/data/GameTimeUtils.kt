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
