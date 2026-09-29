package com.fukuiteams.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * 無料招待ごとの「応募済み」「応募不要」の記録。この端末だけに保存する(invite_status)。
 * 記録が無い招待は、本文に「応募不要」「申込不要」などが書かれていれば「応募不要(自動判定)」とする。
 */
val Context.inviteStatusDataStore: DataStore<Preferences> by preferencesDataStore(name = "invite_status")

enum class InviteStatus { APPLIED, NOT_NEEDED, NONE }

/** 画面に出す状態。autoDetected は「応募不要」を本文から自動で判定したとき true。 */
data class InviteStatusView(val status: InviteStatus, val autoDetected: Boolean)

// 応募がいらないことを示す書き方
private val NOT_NEEDED_WORDS = listOf(
    "応募不要", "申込不要", "申し込み不要", "申込み不要", "事前申込不要", "事前申し込み不要",
    "予約不要", "応募は不要", "申込は不要", "申し込みは不要", "どなたでも入場", "入場無料"
)

private fun RemoteInvitationAlert.statusKey() = stringPreferencesKey("invite_${id.ifBlank { link }}")

fun RemoteInvitationAlert.looksNoApplicationNeeded(): Boolean {
    val text = "$title $snippet"
    return NOT_NEEDED_WORDS.any { text.contains(it) }
}

/** 記録を優先し、記録が無ければ本文から判定する。 */
fun RemoteInvitationAlert.inviteStatus(prefs: Preferences?): InviteStatusView {
    val saved = InviteStatus.values().find { it.name == prefs?.get(statusKey()) }
    return when {
        saved != null -> InviteStatusView(saved, autoDetected = false)
        looksNoApplicationNeeded() -> InviteStatusView(InviteStatus.NOT_NEEDED, autoDetected = true)
        else -> InviteStatusView(InviteStatus.NONE, autoDetected = false)
    }
}

/** 状態を記録する。NONE を記録すると、自動判定の「応募不要」も外れる。 */
suspend fun saveInviteStatus(context: Context, alert: RemoteInvitationAlert, status: InviteStatus) {
    context.inviteStatusDataStore.edit { it[alert.statusKey()] = status.name }
}
