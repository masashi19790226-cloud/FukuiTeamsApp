package com.fukuiteams.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * 「どうやって観戦したか」「試合結果のスコア」は、公式の自動取得手段が無いため
 * この端末のDataStoreに、ユーザー自身が入力した内容として保存する。
 * 外部には送信しない。
 */
val Context.gameLogDataStore: DataStore<Preferences> by preferencesDataStore(name = "game_log")

enum class WatchMethod(val label: String) {
    ON_SITE("現地観戦"),
    STREAMING("配信(バスケットLIVEなど)"),
    NOT_WATCHED("見ていない")
}

private fun watchMethodKey(gameId: String) = stringPreferencesKey("watch_method_$gameId")

suspend fun saveWatchMethod(context: Context, gameId: String, method: WatchMethod) {
    context.gameLogDataStore.edit { it[watchMethodKey(gameId)] = method.name }
}

suspend fun loadWatchMethod(context: Context, gameId: String): WatchMethod? {
    val value = context.gameLogDataStore.data.first()[watchMethodKey(gameId)] ?: return null
    return WatchMethod.values().find { it.name == value }
}

enum class GameOutcome(val label: String) {
    WIN("勝ち"),
    LOSE("負け"),
    DRAW("引き分け")
}

private fun outcomeKey(gameId: String) = stringPreferencesKey("outcome_$gameId")

suspend fun saveGameOutcome(context: Context, gameId: String, outcome: GameOutcome) {
    context.gameLogDataStore.edit { it[outcomeKey(gameId)] = outcome.name }
}

suspend fun loadGameOutcome(context: Context, gameId: String): GameOutcome? {
    val value = context.gameLogDataStore.data.first()[outcomeKey(gameId)] ?: return null
    return GameOutcome.values().find { it.name == value }
}
