package com.fukuiteams.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_prefs")

object NotificationPrefsKeys {
    fun teamKey(teamId: String) = booleanPreferencesKey("team_$teamId")
    fun kindKey(kindId: String) = booleanPreferencesKey("kind_$kindId")
    val LAST_SEEN_INVITE_AT = stringPreferencesKey("last_seen_invite_at")
    val LAST_SEEN_NEWS_AT = stringPreferencesKey("last_seen_news_at")
    /** 選手の誕生日の通知を最後に出した日(同じ日に何度も通知しないため) */
    val LAST_BIRTHDAY_DATE = stringPreferencesKey("last_birthday_date")
}
