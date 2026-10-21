package com.fukuiteams.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
fun FukuiTeamsAppTheme(content: @Composable () -> Unit) {
    // 着せ替え(メニューの「着せ替え」)を読み込む。選んでいる着せ替えの色で画面全体を作る
    val context = LocalContext.current
    remember { AppSkin.load(context); 0 }
    val palette = AppSkin.palette
    val colorScheme = remember(palette) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = White,
            background = palette.ivory,
            onBackground = palette.ink,
            surface = palette.paper,
            onSurface = palette.ink,
            surfaceVariant = palette.dividerGray,
            onSurfaceVariant = palette.inkSoft,
            outline = palette.lineGray
        )
    }
    // スマホの一番上の帯(時計・電池の表示)も、チームの色の着せ替えのときは同じ色にする。
    // 標準に戻したときは、もとの色に戻す
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        val original = remember(window) { window?.statusBarColor }
        SideEffect {
            if (window != null) {
                window.statusBarColor = if (palette.coloredBars) palette.topBar.toArgb() else (original ?: window.statusBarColor)
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
