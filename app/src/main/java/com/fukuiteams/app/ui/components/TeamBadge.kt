package com.fukuiteams.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.model.Team

/**
 * チームロゴの仮バッジ(頭文字+チームカラーの円)。
 * 実装時は各クラブから使用許諾を得た正式なエンブレム画像に差し替える。
 */
@Composable
fun TeamBadge(
    team: Team,
    size: Dp = 32.dp,
    fontSize: TextUnit = 13.sp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .background(team.color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = team.initial,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = fontSize
        )
    }
}
