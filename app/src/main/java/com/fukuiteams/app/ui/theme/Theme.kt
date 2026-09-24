package com.fukuiteams.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = White,
    background = Ivory,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = DividerGray,
    onSurfaceVariant = InkSoft,
    outline = LineGray
)

@Composable
fun FukuiTeamsAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}
