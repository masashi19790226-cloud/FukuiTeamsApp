package com.fukuiteams.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.Ivory
import com.fukuiteams.app.ui.theme.Paper

/**
 * アプリのあちこちで使う、小さな共通の部品。
 * (以前は画面ごとに同じものを別々に書いていたので、ここにまとめた)
 */

/**
 * ブラウザなど、ほかのアプリでURLを開く。URLが空・開けるアプリが無いときは何もしない(アプリは落とさない)。
 */
fun openExternalUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** 試合の一覧の日付の下に出す HOME / AWAY の札(紙面風。HOME は黒地に白抜き、AWAY は黒枠) */
@Composable
fun HomeAwayLabel(isHome: Boolean) {
    HomeAwayTag(isHome = isHome, modifier = Modifier.padding(top = 2.dp))
}

/**
 * 絞り込み・並び替え・切り替えのボタン(紙面風の四角いボタン)。選んでいるものは黒地に白抜き。
 * 試合タブ・トピック・選手タブなど、どの画面でも同じ見た目にそろえる。
 */
@Composable
fun NewsChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 7.dp,
    onClick: () -> Unit
) {
    Text(
        label,
        modifier = modifier
            .border(1.dp, Ink)
            .background(if (selected) Ink else Paper)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        color = if (selected) Ivory else Ink,
        fontSize = fontSize,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false
    )
}
