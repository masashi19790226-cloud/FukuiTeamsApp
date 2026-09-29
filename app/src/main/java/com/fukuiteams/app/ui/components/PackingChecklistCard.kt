package com.fukuiteams.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import com.fukuiteams.app.data.PackingItem
import com.fukuiteams.app.data.checkAllPackingItems
import com.fukuiteams.app.data.checkedPackingItems
import com.fukuiteams.app.data.isToday
import com.fukuiteams.app.data.packingDataStore
import com.fukuiteams.app.data.packingItemsFor
import com.fukuiteams.app.data.resetAllPackingRecords
import com.fukuiteams.app.data.setPackingItemChecked
import com.fukuiteams.app.data.uncheckAllPackingItems
import com.fukuiteams.app.model.Game
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.LineGray
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper
import kotlinx.coroutines.launch

/**
 * 試合当日の持ち物チェック。チェック状態は試合ごとに端末へ保存され、アプリを閉じても残る。
 * 「すべてチェック」「すべて解除」はこの試合だけ、「リセット」は全試合の記録を消す(確認あり)。
 * 項目が多いので、試合当日は開いた状態、それ以外は閉じた状態で表示する(見出しをタップで開閉)。
 */
@Composable
fun PackingChecklistCard(game: Game) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by context.packingDataStore.data.collectAsState<Preferences, Preferences?>(initial = null)
    val items = remember(game.id, game.team) { packingItemsFor(game) }
    // 分類ごとにまとめる(並び順は一覧の順のまま)
    val groups = remember(items) { items.groupBy { it.group } }
    val checked = checkedPackingItems(game.id, prefs)
    val doneCount = items.count { it.id in checked }
    val allDone = doneCount == items.size
    var expanded by remember(game.id) { mutableStateOf(game.isToday()) }
    var confirmReset by remember { mutableStateOf(false) }

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
                .clickable { expanded = !expanded },
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
        Text(
            "チェックはこの試合ごとに保存され、アプリを閉じても消えません。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        Column {
            groups.forEach { (group, groupItems) ->
                if (group.isNotBlank() && groups.size > 1) {
                    val groupDone = groupItems.count { it.id in checked }
                    Text(
                        "$group($groupDone/${groupItems.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ink,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }
                groupItems.forEach { item ->
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { scope.launch { checkAllPackingItems(context, game.id, items.map { it.id }) } },
                modifier = Modifier.weight(1f)
            ) { Text("すべてチェック", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(
                onClick = { scope.launch { uncheckAllPackingItems(context, game.id) } },
                modifier = Modifier.weight(1f)
            ) { Text("すべて解除", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.weight(0.8f)
            ) { Text("リセット", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("持ち物チェックをリセットしますか?") },
            text = { Text("すべての試合の持ち物チェックの記録を消します。観戦記録や写真は消えません。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { resetAllPackingRecords(context) }
                }) { Text("リセット") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("やめる") }
            }
        )
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
