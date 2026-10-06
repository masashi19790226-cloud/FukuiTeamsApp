package com.fukuiteams.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.White
import kotlinx.coroutines.launch

/**
 * 画面の右下に出す「一番上へ移動」「一番下へ移動」の小さいボタン(縦に2つ)。
 * 各タブの Scaffold の floatingActionButton に置いて使う。
 * ・それ以上動かせない向きのボタンは薄く表示し、押しても何もしない
 * ・画面に収まっていてスクロールできないときは、ボタン自体を出さない
 */
@Composable
fun ScrollJumpButtons(state: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    ScrollJumpButtonColumn(
        canUp = state.canScrollBackward,
        canDown = state.canScrollForward,
        onTop = { scope.launch { state.animateScrollToItem(0) } },
        onBottom = {
            scope.launch {
                val last = state.layoutInfo.totalItemsCount - 1
                if (last >= 0) {
                    state.animateScrollToItem(last)
                    // 最後の行が画面より縦に長いときも、いちばん下まで届くようにする(端で自動的に止まる)
                    state.scrollBy(100_000f)
                }
            }
        },
        modifier = modifier
    )
}

/** verticalScroll(ScrollState) を使っている画面用。 */
@Composable
fun ScrollJumpButtons(state: ScrollState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    ScrollJumpButtonColumn(
        canUp = state.canScrollBackward,
        canDown = state.canScrollForward,
        onTop = { scope.launch { state.animateScrollTo(0) } },
        onBottom = { scope.launch { state.animateScrollTo(state.maxValue) } },
        modifier = modifier
    )
}

@Composable
private fun ScrollJumpButtonColumn(
    canUp: Boolean,
    canDown: Boolean,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!canUp && !canDown) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        JumpButton(Icons.Filled.VerticalAlignTop, "一番上へ移動", canUp, onTop)
        JumpButton(Icons.Filled.VerticalAlignBottom, "一番下へ移動", canDown, onBottom)
    }
}

/** 小さい丸ボタン1つ。押せないときは薄く表示する。 */
@Composable
fun JumpButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
        containerColor = White,
        contentColor = Ink,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp, pressedElevation = 6.dp)
    ) {
        Icon(icon, contentDescription = description)
    }
}

/** 右下のボタンに最後の行が隠れないよう、一覧の下に空ける余白。 */
val ScrollJumpBottomPadding = 112.dp
