package com.fukuiteams.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.data.PublicViewing
import com.fukuiteams.app.data.publicViewingXSearchUrl
import com.fukuiteams.app.model.Team
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.InkSoft
import com.fukuiteams.app.ui.theme.NewsRed
import com.fukuiteams.app.ui.theme.Paper
import com.fukuiteams.app.ui.theme.White

/** 試合の一覧などに付ける小さな札「PV」(その試合のパブリックビューイングがあるとき) */
@Composable
fun PublicViewingBadge(modifier: Modifier = Modifier) {
    Text(
        "PV",
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = White,
        maxLines = 1,
        modifier = modifier.background(NewsRed).padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

/**
 * パブリックビューイングの一覧(1件ずつ枠で囲む)。押すと告知の出どころ(Xのポスト・お知らせ)を開く。
 * showTeam:3チームまとめて出すときはチーム名も出す
 */
@Composable
fun PublicViewingList(items: List<PublicViewing>, showTeam: Boolean = false) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { pv ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (pv.auto) InkSoft else NewsRed)
                    .background(Paper)
                    .then(if (pv.url.isNotBlank()) Modifier.clickable { openPvUrl(context, pv.url) } else Modifier)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PublicViewingBadge()
                    if (showTeam) {
                        Text(pv.team.displayName, style = MaterialTheme.typography.labelMedium, color = pv.team.color, maxLines = 1)
                    }
                    if (pv.auto) {
                        Text("自動で見つけた記事", style = MaterialTheme.typography.labelSmall, color = InkSoft)
                    }
                }
                Text(pv.summary.ifBlank { "パブリックビューイング" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Ink)
                if (pv.auto && pv.dateLabel().isNotBlank()) {
                    Text("記事に書かれた日付:${pv.dateLabel()}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
                if (pv.opponent.isNotBlank()) {
                    Text("対象の試合:vs ${pv.opponent}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
                if (pv.address.isNotBlank()) {
                    Text("場所:${pv.address}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
                if (pv.note.isNotBlank()) {
                    Text(pv.note, style = MaterialTheme.typography.bodySmall, color = Ink)
                }
                val from = listOf(pv.source.takeIf { it.isNotBlank() }?.let { "出どころ:$it" } ?: "",
                    if (pv.url.isNotBlank()) "押すと告知を開きます" else "").filter { it.isNotBlank() }.joinToString("・")
                if (from.isNotBlank()) {
                    Text(from, style = MaterialTheme.typography.labelSmall, color = InkSoft)
                }
            }
        }
    }
}

/** Xでパブリックビューイングの最新の投稿を探すボタン(チームごと) */
@Composable
fun PublicViewingXSearch(teams: List<Team>) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Xだけの告知は自動で取れないため、ボタンでXを探せます。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        // チームごとに枠付きのボタン(押すとXの最新の投稿の検索結果が開く)
        teams.forEach { team ->
            val color = if (teams.size > 1) team.color else Ink
            OutlinedButton(
                onClick = { openPvUrl(context, publicViewingXSearchUrl(team)) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.5.dp, color),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = White, contentColor = color)
            ) {
                Text(
                    if (teams.size > 1) "Xで探す(${team.displayName})" else "XでPV情報を探す",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
}

private fun openPvUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
