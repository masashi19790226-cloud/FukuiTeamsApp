package com.fukuiteams.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.ui.theme.Accent
import com.fukuiteams.app.ui.theme.White

/** 試合に付ける「行く予定」の札(チェックインした試合) */
@Composable
fun GoingBadge(modifier: Modifier = Modifier) {
    Text(
        "行く予定",
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = White,
        maxLines = 1,
        modifier = modifier.background(Accent).padding(horizontal = 5.dp, vertical = 1.dp)
    )
}
