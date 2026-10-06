package com.fukuiteams.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 掲示板タブの設定と既読位置。この端末の DataStore「bbs_prefs」に保存し、外部には送信しない。
 */
val Context.bbsDataStore: DataStore<Preferences> by preferencesDataStore(name = "bbs_prefs")

/** タイムラインの並び。 */
enum class BbsSort(val label: String) {
    NEW("新着順"),
    LIKES("いいね順"),
    IMAGES("画像あり")
}

data class BbsSettings(
    /** 表示件数(15・30・50・100)。 */
    val displayCount: Int = 30,
    val sort: BbsSort = BbsSort.NEW,
    /** true:最新が上 / false:最新が下 */
    val newestTop: Boolean = true,
    /** 本文の文字サイズ(14・16・18sp)。 */
    val fontSize: Int = 16,
    /** タブを開いている間、5分ごとに読み直す。 */
    val autoRefresh: Boolean = true,
    val ngWords: List<String> = emptyList(),
    /** NGユーザー(userKey → 名前)。 */
    val ngUsers: Map<String, String> = emptyMap(),
    /** 前回までに読み込んだ一番新しい投稿の番号(既読位置)。 */
    val lastReadNo: Int = 0,
    /** キーワード通知:オンのとき、登録した言葉を含む新しい投稿をスマホに通知する。 */
    val notifyEnabled: Boolean = false,
    val notifyWords: List<String> = emptyList()
)

object BbsPrefs {
    val DISPLAY_COUNTS = listOf(15, 30, 50, 100)
    val FONT_SIZES = listOf(14 to "小", 16 to "中", 18 to "大")

    private val KEY_COUNT = intPreferencesKey("display_count")
    private val KEY_SORT = stringPreferencesKey("sort")
    private val KEY_NEWEST_TOP = booleanPreferencesKey("newest_top")
    private val KEY_FONT = intPreferencesKey("font_size")
    private val KEY_AUTO = booleanPreferencesKey("auto_refresh")
    private val KEY_NG_WORDS = stringSetPreferencesKey("ng_words")
    // 「userKey<TAB>名前」の形で入れる
    private val KEY_NG_USERS = stringSetPreferencesKey("ng_users")
    private val KEY_LAST_READ = intPreferencesKey("last_read_no")
    private val KEY_NOTIFY_ON = booleanPreferencesKey("notify_enabled")
    private val KEY_NOTIFY_WORDS = stringSetPreferencesKey("notify_words")
    // キーワード通知で、どの投稿まで確認したか(0 は「まだ確認していない」)
    private val KEY_NOTIFIED_NO = intPreferencesKey("notified_no")

    /** キーワード通知の言葉の候補(設定画面で押すと追加できる) */
    val SUGGESTED_NOTIFY_WORDS = listOf("チケット", "譲", "招待", "余って")

    fun flow(context: Context): Flow<BbsSettings> = context.bbsDataStore.data.map { p ->
        BbsSettings(
            displayCount = p[KEY_COUNT]?.takeIf { it in DISPLAY_COUNTS } ?: 30,
            sort = BbsSort.values().firstOrNull { it.name == p[KEY_SORT] } ?: BbsSort.NEW,
            newestTop = p[KEY_NEWEST_TOP] ?: true,
            fontSize = p[KEY_FONT]?.takeIf { size -> FONT_SIZES.any { it.first == size } } ?: 16,
            autoRefresh = p[KEY_AUTO] ?: true,
            ngWords = (p[KEY_NG_WORDS] ?: emptySet()).filter { it.isNotBlank() }.sorted(),
            ngUsers = (p[KEY_NG_USERS] ?: emptySet()).mapNotNull { entry ->
                val parts = entry.split('\t', limit = 2)
                if (parts[0].isBlank()) null else parts[0] to parts.getOrElse(1) { "" }
            }.toMap(),
            lastReadNo = p[KEY_LAST_READ] ?: 0,
            notifyEnabled = p[KEY_NOTIFY_ON] ?: false,
            notifyWords = (p[KEY_NOTIFY_WORDS] ?: emptySet()).filter { it.isNotBlank() }.sorted()
        )
    }

    suspend fun load(context: Context): BbsSettings = flow(context).first()

    suspend fun setDisplayCount(context: Context, count: Int) {
        context.bbsDataStore.edit { it[KEY_COUNT] = count }
    }

    suspend fun setSort(context: Context, sort: BbsSort) {
        context.bbsDataStore.edit { it[KEY_SORT] = sort.name }
    }

    suspend fun setNewestTop(context: Context, newestTop: Boolean) {
        context.bbsDataStore.edit { it[KEY_NEWEST_TOP] = newestTop }
    }

    suspend fun setFontSize(context: Context, size: Int) {
        context.bbsDataStore.edit { it[KEY_FONT] = size }
    }

    suspend fun setAutoRefresh(context: Context, on: Boolean) {
        context.bbsDataStore.edit { it[KEY_AUTO] = on }
    }

    suspend fun addNgWord(context: Context, word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        context.bbsDataStore.edit { it[KEY_NG_WORDS] = (it[KEY_NG_WORDS] ?: emptySet()) + w }
    }

    suspend fun removeNgWord(context: Context, word: String) {
        context.bbsDataStore.edit { it[KEY_NG_WORDS] = (it[KEY_NG_WORDS] ?: emptySet()) - word }
    }

    suspend fun addNgUser(context: Context, userKey: String, name: String) {
        context.bbsDataStore.edit { p ->
            val rest = (p[KEY_NG_USERS] ?: emptySet()).filterNot { it.split('\t', limit = 2)[0] == userKey }.toSet()
            p[KEY_NG_USERS] = rest + "$userKey\t${name.replace('\t', ' ')}"
        }
    }

    suspend fun removeNgUser(context: Context, userKey: String) {
        context.bbsDataStore.edit { p ->
            p[KEY_NG_USERS] = (p[KEY_NG_USERS] ?: emptySet()).filterNot { it.split('\t', limit = 2)[0] == userKey }.toSet()
        }
    }

    /**
     * キーワード通知のオン・オフ。オンにしたときは確認位置を0に戻し、
     * 次の確認ではそれまでの投稿をまとめて通知せず、位置だけ記録する。
     */
    suspend fun setNotifyEnabled(context: Context, on: Boolean) {
        context.bbsDataStore.edit {
            it[KEY_NOTIFY_ON] = on
            if (on) it[KEY_NOTIFIED_NO] = 0
        }
    }

    suspend fun addNotifyWord(context: Context, word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        context.bbsDataStore.edit { it[KEY_NOTIFY_WORDS] = (it[KEY_NOTIFY_WORDS] ?: emptySet()) + w }
    }

    suspend fun removeNotifyWord(context: Context, word: String) {
        context.bbsDataStore.edit { it[KEY_NOTIFY_WORDS] = (it[KEY_NOTIFY_WORDS] ?: emptySet()) - word }
    }

    /** キーワード通知で、どの投稿まで確認したか(0 は未確認)。 */
    suspend fun notifiedNo(context: Context): Int = context.bbsDataStore.data.first()[KEY_NOTIFIED_NO] ?: 0

    suspend fun setNotifiedNo(context: Context, no: Int) {
        context.bbsDataStore.edit { it[KEY_NOTIFIED_NO] = no }
    }

    /** 既読位置を進める(今より小さい番号では戻さない)。 */
    suspend fun markRead(context: Context, no: Int) {
        context.bbsDataStore.edit { p ->
            if (no > (p[KEY_LAST_READ] ?: 0)) p[KEY_LAST_READ] = no
        }
    }
}

/** NGワード・NGユーザーに当たる投稿か。 */
fun BbsPost.isNg(settings: BbsSettings): Boolean {
    if (userKey in settings.ngUsers) return true
    if (settings.ngWords.isEmpty()) return false
    val text = (name + "\n" + body).lowercase()
    return settings.ngWords.any { it.isNotBlank() && text.contains(it.lowercase()) }
}

/** キーワード通知の言葉のうち、この投稿(名前・本文)に含まれる最初のもの。無ければ null。 */
fun BbsPost.matchedNotifyWord(words: List<String>): String? {
    if (words.isEmpty()) return null
    val text = (name + "\n" + body).lowercase()
    return words.firstOrNull { it.isNotBlank() && text.contains(it.lowercase()) }
}
