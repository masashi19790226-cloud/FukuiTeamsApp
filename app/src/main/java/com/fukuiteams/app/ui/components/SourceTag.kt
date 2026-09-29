package com.fukuiteams.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.ui.theme.Ink

/** 記事の引用元(「福井新聞」「ブローウィンズ公式」など)を枠付きの札で出す。空なら何も出さない。 */
@Composable
fun SourceTag(label: String, modifier: Modifier = Modifier) {
    if (label.isBlank()) return
    Text(
        label,
        modifier = modifier
            .border(1.dp, Ink)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = Ink,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        softWrap = false
    )
}
