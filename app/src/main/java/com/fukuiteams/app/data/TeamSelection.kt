package com.fukuiteams.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fukuiteams.app.model.Team

/**
 * 一面・試合・選手・トピックの各タブで共通の「選んでいるチーム」。
 * タブを切り替えても、最後に選んだチームのまま表示するために使う(アプリを閉じると最初の状態に戻る)。
 *
 * - current : 一面・トピックで使う。null は「すべて」
 * - lastTeam: 試合・選手で使う(この2つは「すべて」が無いので、「すべて」のときは直前に選んでいたチーム)
 */
object TeamSelection {
    var current by mutableStateOf<Team?>(null)
        private set

    var lastTeam by mutableStateOf(Team.BLOWINDS)
        private set

    /** チームボタンで選んだとき(null = すべて) */
    fun select(team: Team?) {
        current = team
        if (team != null) lastTeam = team
    }

    /** 一面などから特定の試合を開いたとき。一面の「すべて」はそのままにし、試合・選手タブだけそのチームにする */
    fun focus(team: Team) {
        lastTeam = team
        if (current != null) current = team
    }

    /** 試合・選手タブで最初に出すチーム */
    fun teamForSingle(): Team = current ?: lastTeam
}
