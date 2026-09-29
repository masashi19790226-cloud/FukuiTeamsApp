package com.fukuiteams.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.model.Team

/**
 * 試合当日の持ち物チェック。
 * チェック状態は試合ごとに、この端末のDataStore(packing_checklist)へ保存する。
 * 持ち物の一覧そのもの(追加・削除)はチームごとに同じDataStoreへ保存する。
 * アプリを閉じても消えない。観戦記録(game_log)とは別の保存先なので、既存の記録には影響しない。
 */
val Context.packingDataStore: DataStore<Preferences> by preferencesDataStore(name = "packing_checklist")

/** 持ち物1つ。group は画面で見出しにする分類(空なら見出しなし)。 */
data class PackingItem(val id: String, val label: String, val group: String = "")

// ブローウィンズ以外の試合の持ち物
private val BASE_ITEMS = listOf(
    PackingItem("ticket", "チケット"),
    PackingItem("phone", "スマートフォン"),
    PackingItem("wallet", "財布"),
    PackingItem("battery", "モバイルバッテリー"),
    PackingItem("goods", "応援グッズ"),
    PackingItem("towel", "タオル"),
    PackingItem("drink", "飲み物"),
    PackingItem("binoculars", "双眼鏡"),
    PackingItem("camera", "カメラ")
)

private const val G_MUST = "必需品"
private const val G_CHEER = "応援"
private const val G_SEAT = "観戦グッズ"
private const val G_FOOD = "飲み物・食べ物"
private const val G_BODY = "身の回り"
private const val G_PLAY = "あそび・その他"

/**
 * ブローウィンズ(バスケ)の試合の持ち物。自宅の「バスケ荷物」メモをもとにしている。
 * 以前の一覧と同じ物は同じIDにして、保存済みのチェックが引き継がれるようにしている
 * (ケータイ=phone、サイフ=wallet、モバイルバッテリー=battery、タオル=towel など)。
 */
private val BLOWINDS_ITEMS = listOf(
    PackingItem("ticket", "チケット", G_MUST),
    PackingItem("member_card", "メンバーカード", G_MUST),
    PackingItem("phone", "ケータイ", G_MUST),
    PackingItem("wallet", "サイフ", G_MUST),
    PackingItem("battery", "モバイルバッテリー", G_MUST),
    PackingItem("bag", "カバン", G_MUST),
    PackingItem("eco_bag", "エコバッグ", G_MUST),

    PackingItem("uniform", "ユニフォーム", G_CHEER),
    PackingItem("hairband", "BOOZヘアバンド", G_CHEER),
    PackingItem("booz_sunglasses", "BOOZサングラス", G_CHEER),
    PackingItem("megaphone", "メガホン", G_CHEER),
    PackingItem("penlight", "ペンライト", G_CHEER),
    PackingItem("towel", "タオル", G_CHEER),

    PackingItem("chair", "イス", G_SEAT),
    PackingItem("cushion", "ザブトン", G_SEAT),
    PackingItem("dech_blanket", "デッチブランケット", G_SEAT),
    PackingItem("jacket", "上着", G_SEAT),
    PackingItem("basket_book", "バスケの本", G_SEAT),
    PackingItem("clear_file", "クリアファイル", G_SEAT),

    PackingItem("water_bottle", "水筒", G_FOOD),
    PackingItem("cup", "コップ", G_FOOD),
    PackingItem("straw", "ストロー", G_FOOD),
    PackingItem("snack", "おかし", G_FOOD),
    PackingItem("food", "たべもの", G_FOOD),
    PackingItem("zip_bag", "ジップロック", G_FOOD),
    PackingItem("trash_bag", "ゴミ袋", G_FOOD),

    PackingItem("glasses", "メガネ", G_BODY),
    PackingItem("contacts", "ワンデーコンタクト", G_BODY),
    PackingItem("eye_drops", "目薬", G_BODY),
    PackingItem("umbrella", "カサ", G_BODY),

    PackingItem("game_console", "スイッチ・DS", G_PLAY),
    PackingItem("basket", "カゴ", G_PLAY)
)

/**
 * チームごとの最初の持ち物の一覧(アプリに組み込み)。
 * ブローウィンズはバスケ観戦用の一覧。ユナイテッドの試合は屋外のグラウンドで行われるため、雨具を加える。
 */
private fun defaultItemsFor(team: Team): List<PackingItem> = when (team) {
    Team.BLOWINDS -> BLOWINDS_ITEMS
    Team.UNITED -> BASE_ITEMS + PackingItem("rain", "雨具(屋外の試合)")
    Team.RAC -> BASE_ITEMS
}

// ---- アプリ内での編集(チームごとに保存し、そのチームの全試合に反映) ----

/** 自分で追加した持ち物の見出し */
const val CUSTOM_GROUP = "追加した持ち物"

// 最初の一覧から外した項目のID
private fun hiddenKey(team: Team) = stringSetPreferencesKey("hidden_${team.name}")

// 自分で追加した項目。1行に「ID<タブ>名前」で、追加した順に並べて保存する
private fun customKey(team: Team) = stringPreferencesKey("custom_${team.name}")

private fun parseCustom(raw: String?, group: String): List<PackingItem> =
    raw.orEmpty().lines().mapNotNull { line ->
        val parts = line.split("\t", limit = 2)
        if (parts.size == 2 && parts[1].isNotBlank()) PackingItem(parts[0], parts[1], group) else null
    }

/**
 * この試合の持ち物の一覧。最初の一覧から外した項目を除き、自分で追加した項目を最後に加える。
 * ブローウィンズのように見出しで分けている一覧では、追加分に「追加した持ち物」の見出しを付ける。
 */
fun packingItemsFor(game: Game, prefs: Preferences?): List<PackingItem> {
    val defaults = defaultItemsFor(game.team)
    val hidden = prefs?.get(hiddenKey(game.team)) ?: emptySet()
    val group = if (defaults.any { it.group.isNotBlank() }) CUSTOM_GROUP else ""
    return defaults.filterNot { it.id in hidden } + parseCustom(prefs?.get(customKey(game.team)), group)
}

/** 最初の一覧から外している項目の数(「元に戻す」の表示用)。 */
fun hiddenPackingCount(team: Team, prefs: Preferences?): Int =
    (prefs?.get(hiddenKey(team)) ?: emptySet()).count { id -> defaultItemsFor(team).any { it.id == id } }

/** 持ち物を追加する。空の名前は追加しない。 */
suspend fun addPackingItem(context: Context, team: Team, label: String) {
    val clean = label.replace("\t", " ").replace("\n", " ").trim()
    if (clean.isEmpty()) return
    context.packingDataStore.edit { prefs ->
        val current = prefs[customKey(team)].orEmpty()
        val line = "custom_${System.currentTimeMillis()}\t$clean"
        prefs[customKey(team)] = if (current.isBlank()) line else "$current\n$line"
    }
}

/** 持ち物を一覧から消す。自分で追加した項目は削除、最初からある項目は非表示にする。 */
suspend fun removePackingItem(context: Context, team: Team, itemId: String) {
    context.packingDataStore.edit { prefs ->
        if (itemId.startsWith("custom_")) {
            val kept = prefs[customKey(team)].orEmpty().lines().filterNot { it.startsWith("$itemId\t") }
            prefs[customKey(team)] = kept.joinToString("\n")
        } else {
            prefs[hiddenKey(team)] = (prefs[hiddenKey(team)] ?: emptySet()) + itemId
        }
    }
}

/** 最初の一覧から外した項目を元に戻す(自分で追加した項目はそのまま)。 */
suspend fun restoreDefaultPackingItems(context: Context, team: Team) {
    context.packingDataStore.edit { it.remove(hiddenKey(team)) }
}

private fun checkedKey(gameId: String) = stringSetPreferencesKey("checked_$gameId")

/** 保存されているチェック済みの項目ID。未保存なら空。 */
fun checkedPackingItems(gameId: String, prefs: Preferences?): Set<String> =
    prefs?.get(checkedKey(gameId)) ?: emptySet()

suspend fun setPackingItemChecked(context: Context, gameId: String, itemId: String, checked: Boolean) {
    context.packingDataStore.edit { prefs ->
        val current = prefs[checkedKey(gameId)] ?: emptySet()
        prefs[checkedKey(gameId)] = if (checked) current + itemId else current - itemId
    }
}

/** この試合の全項目をチェック済みにする。 */
suspend fun checkAllPackingItems(context: Context, gameId: String, itemIds: List<String>) {
    context.packingDataStore.edit { it[checkedKey(gameId)] = itemIds.toSet() }
}

/** この試合のチェックをすべて外す。 */
suspend fun uncheckAllPackingItems(context: Context, gameId: String) {
    context.packingDataStore.edit { it[checkedKey(gameId)] = emptySet() }
}
