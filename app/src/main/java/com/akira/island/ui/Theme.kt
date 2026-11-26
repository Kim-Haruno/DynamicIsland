package com.akira.island.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

private val androidDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainerHigh = Color(0xFF2B2930),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
)

object Ui {
    private var scheme by mutableStateOf(androidDarkColorScheme)

    internal fun applyColorScheme(colorScheme: ColorScheme) {
        scheme = colorScheme
    }

    val Bg: Color get() = scheme.background
    val Card: Color get() = scheme.surfaceContainerHigh
    val IconBg: Color get() = scheme.surfaceVariant
    val Primary: Color get() = scheme.primary
    val OnPrimary: Color get() = scheme.onPrimary
    val Text: Color get() = scheme.onSurface
    val Sub: Color get() = scheme.onSurfaceVariant
    val Divider: Color get() = scheme.outlineVariant
    val Nav: Color get() = scheme.surfaceContainerLow
    val Pink: Color get() = scheme.tertiary
    val Outline: Color get() = scheme.outline
}

object Tile {
    private val scheme: ColorScheme get() = UiColorScheme.current

    val Blue: Color get() = scheme.primary
    val Green: Color get() = scheme.secondary
    val Orange: Color get() = scheme.tertiary
    val Red: Color get() = scheme.error
    val Purple: Color get() = scheme.primary
    val Pink: Color get() = scheme.tertiary
    val Teal: Color get() = scheme.secondary
    val Indigo: Color get() = scheme.primary
    val Yellow: Color get() = scheme.tertiary
    val Gray: Color get() = scheme.outline
}

private object UiColorScheme {
    val current: ColorScheme
        get() = activeColorScheme

    var activeColorScheme by mutableStateOf(androidDarkColorScheme)
        private set

    fun update(colorScheme: ColorScheme) {
        activeColorScheme = colorScheme
    }
}

@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    SideEffect {
        Ui.applyColorScheme(androidDarkColorScheme)
        UiColorScheme.update(androidDarkColorScheme)
    }

    MaterialTheme(
        colorScheme = androidDarkColorScheme,
        typography = InterTypography,
        content = content,
    )
}
