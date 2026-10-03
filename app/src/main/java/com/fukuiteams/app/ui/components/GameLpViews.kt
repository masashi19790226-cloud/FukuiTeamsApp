package com.fukuiteams.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fukuiteams.app.data.GameLp
import com.fukuiteams.app.data.openLabel
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft

/**
 * ブローウィンズ公式の「試合情報」ページから読み取った、開場時刻・当日のスケジュール・イベント。
 * 一面の「本日の試合」と、試合詳細で使う。スケジュールとイベントは見出しを押すと開閉する。
 * 最後に公式ページへのリンクを付ける(読み取れなかった内容や、グルメ・会場マップは公式ページで見る)。
 */
@Composable
fun GameLpSection(lp: GameLp, startExpanded: Boolean) {
    val context = LocalContext.current
    var showSchedule by remember(lp.url) { mutableStateOf(startExpanded) }
    var showEvents by remember(lp.url) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // シルバー会員の先行入場の時刻(無ければ一般の開場時刻)
        lp.openLabel?.let { label ->
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
        if (lp.timeline.isNotEmpty()) {
            ToggleHeading("当日のスケジュール(公式)", showSchedule) { showSchedule = !showSchedule }
            if (showSchedule) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lp.timeline.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                item.time,
                                modifier = Modifier.width(76.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                            Text(item.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Ink)
                        }
                    }
                    Text("※内容・時間は変更になる場合があります", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                }
            }
        }
        if (lp.events.isNotEmpty()) {
            ToggleHeading("イベント(${lp.events.size}件)", showEvents) { showEvents = !showEvents }
            if (showEvents) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lp.events.forEach { ev ->
                        Column {
                            Text("・${ev.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink)
                            if (ev.text.isNotBlank()) {
                                Text(ev.text, style = MaterialTheme.typography.bodySmall, color = InkSoft, maxLines = 3)
                            }
                        }
                    }
                }
            }
        }
        Text(
            "公式の試合情報ページを開く(グルメ・会場マップ・アクセスなど) ›",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Accent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { openLpPage(context, lp.url) }
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun ToggleHeading(label: String, open: Boolean, onClick: () -> Unit) {
    Text(
        "${if (open) "▲" else "▼"} $label",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        color = Ink,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    )
}

private fun openLpPage(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        // ブラウザが見つからないなどで開けないときは何もしない(アプリは落とさない)
    }
}
