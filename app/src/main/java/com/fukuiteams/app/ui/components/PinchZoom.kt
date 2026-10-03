package com.fukuiteams.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fukuiteams.app.ui.theme.Ink
import com.fukuiteams.app.ui.theme.Paper
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 画面の表示の大きさ(二本指で広げる・つまむで変える)。
 * 文字だけでなく余白や枠も一緒に大きく・小さくなり、画面の幅に合わせて並び直す(横にはみ出さない)。
 * 選んだ大きさはスマホに保存し、次にアプリを開いたときも同じ大きさで表示する。
 */
object UiScale {
    const val MIN = 0.85f
    const val MAX = 1.5f
    private const val PREFS = "ui_prefs"
    private const val KEY = "ui_scale"

    var scale by mutableFloatStateOf(1f)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        scale = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY, 1f).coerceIn(MIN, MAX)
    }

    fun set(context: Context, value: Float) {
        scale = value.coerceIn(MIN, MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY, scale).apply()
    }

    fun reset(context: Context) = set(context, 1f)

    /** 「120%」のような表示 */
    fun percentLabel(): String = "${(scale * 100).roundToInt()}%"
}

/**
 * アプリ全体を包み、二本指の操作で表示の大きさを変える。
 * 指1本の操作(スクロール・タップ)は今までどおり中の画面に渡し、指2本のときだけ大きさの変更に使う。
 */
@Composable
fun PinchZoomContainer(content: @Composable () -> Unit) {
    val context = LocalContext.current
    remember { UiScale.load(context); 0 }
    val base = LocalDensity.current
    // 大きさを変えている間と、その少し後だけ「表示 120%」の札を出す
    var showLabelTick by remember { mutableIntStateOf(0) }
    var showLabel by remember { mutableStateOf(false) }
    LaunchedEffect(showLabelTick) {
        if (showLabelTick > 0) {
            showLabel = true
            delay(1_200)
            showLabel = false
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                UiScale.set(context, UiScale.scale * zoom)
                                showLabelTick++
                            }
                            // 指2本のときは、中の画面のスクロールやタップにしない
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * UiScale.scale, base.fontScale)
        ) {
            content()
        }
        if (showLabel) {
            Text(
                "表示 ${UiScale.percentLabel()}",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .background(Ink)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Paper,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
