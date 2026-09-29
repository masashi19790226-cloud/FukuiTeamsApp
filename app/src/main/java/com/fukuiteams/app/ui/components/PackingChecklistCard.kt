package com.fukuiteams.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import com.fukuiteams.app.data.PackingItem
import com.fukuiteams.app.data.addPackingItem
import com.fukuiteams.app.data.checkAllPackingItems
import com.fukuiteams.app.data.checkedPackingItems
import com.fukuiteams.app.data.hiddenPackingCount
import com.fukuiteams.app.data.isToday
import com.fukuiteams.app.data.packingDataStore
import com.fukuiteams.app.data.packingItemsFor
import com.fukuiteams.app.data.removePackingItem
import com.fukuiteams.app.data.restoreDefaultPackingItems
import com.fukuiteams.app.data.setPackingItemChecked
import com.fukuiteams.app.data.uncheckAllPackingItems
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper
import kotlinx.coroutines.launch

/**
 * 試合当日の持ち物チェック。チェック状態は試合ごとに端末へ保存され、アプリを閉じても残る。
 * 「すべてチェック」「すべて解除」は、いま開いている試合だけに効く。
 * 項目が多いので、試合当日は開いた状態、それ以外は閉じた状態で表示する(見出しをタップで開閉)。
 * 「編集」で持ち物の追加・削除ができる(チームごとに保存され、そのチームの全試合に反映)。
 */
@Composable
fun PackingChecklistCard(game: Game) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by context.packingDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
    val items = packingItemsFor(game, prefs)
    // 分類ごとにまとめる(並び順は一覧の順のまま)
    val groups = items.groupBy { it.group }
    val checked = checkedPackingItems(game.id, prefs)
    val doneCount = items.count { it.id in checked }
    val allDone = items.isNotEmpty() && doneCount == items.size
    val hiddenCount = hiddenPackingCount(game.team, prefs)
    var expanded by remember(game.id) { mutableStateOf(game.isToday()) }
    var editing by remember(game.id) { mutableStateOf(false) }
    var newLabel by remember(game.id) { mutableStateOf("") }

    fun addItem() {
        val label = newLabel.trim()
        if (label.isEmpty()) return
        newLabel = ""
        scope.launch { addPackingItem(context, game.team, label) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink)
            .background(Paper)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    expanded = !expanded
                    if (!expanded) editing = false
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionLabel("持ち物チェック", red = !allDone)
            Text(
                if (allDone) "準備OK" else "$doneCount / ${items.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (allDone) Ink else NewsRed
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (expanded) "閉じる ▲" else "開く ▼",
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft
            )
        }
        if (!expanded) {
            Text(
                "タップで持ち物の一覧を開きます。チェックは試合ごとに保存されます。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (editing) "持ち物を追加・削除できます。変更は${game.team.displayName}の全試合に反映されます。"
                else "チェックはこの試合ごとに保存され、アプリを閉じても消えません。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (editing) "完了" else "編集",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Accent,
                modifier = Modifier
                    .clickable { editing = !editing }
                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp)
            )
        }
        Column {
            groups.forEach { (group, groupItems) ->
                if (group.isNotBlank() && groups.size > 1) {
                    val groupDone = groupItems.count { it.id in checked }
                    Text(
                        if (editing) group else "$group($groupDone/${groupItems.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ink,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }
                groupItems.forEach { item ->
                    if (editing) {
                        EditRow(item) { scope.launch { removePackingItem(context, game.team, item.id) } }
                    } else {
                        PackingRow(
                            item = item,
                            isChecked = item.id in checked,
                            onToggle = { value ->
                                scope.launch { setPackingItemChecked(context, game.id, item.id, value) }
                            }
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                Text(
                    "持ち物がありません。「編集」から追加できます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it.replace("\n", "") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("例:うちわ") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addItem() })
                )
                ChecklistButton("追加", Modifier) { addItem() }
            }
            if (hiddenCount > 0) {
                Text(
                    "削除した最初からの持ち物(${hiddenCount}件)を元に戻す",
                    style = MaterialTheme.typography.labelLarge,
                    color = Accent,
                    modifier = Modifier
                        .clickable { scope.launch { restoreDefaultPackingItems(context, game.team) } }
                        .padding(vertical = 6.dp)
                )
            }
        } else {
            // ボタンの文字が切れないよう、左右の余白を小さくしている
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChecklistButton("すべてチェック", Modifier.weight(1f)) {
                    scope.launch { checkAllPackingItems(context, game.id, items.map { it.id }) }
                }
                ChecklistButton("すべて解除", Modifier.weight(1f)) {
                    scope.launch { uncheckAllPackingItems(context, game.id) }
                }
            }
        }
    }
}

/** 持ち物チェックの下のボタン。文字が1行に収まるよう、内側の余白を小さくしている。 */
@Composable
private fun ChecklistButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, maxLines = 1, softWrap = false, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** 持ち物1行。行全体をタップしてもチェックが切り替わる。 */
@Composable
private fun PackingRow(item: PackingItem, isChecked: Boolean, onToggle: (Boolean) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!isChecked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { value -> onToggle(value) },
                colors = CheckboxDefaults.colors(checkedColor = Ink, uncheckedColor = InkSoft)
            )
            Text(
                item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isChecked) InkSoft else Ink,
                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
            )
        }
        ThinRule(color = LineGray)
    }
}

/** 編集中の持ち物1行。右の「削除」で一覧から消す。 */
@Composable
private fun EditRow(item: PackingItem, onRemove: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            Text(
                "削除",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = NewsRed,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
        ThinRule(color = LineGray)
    }
}
