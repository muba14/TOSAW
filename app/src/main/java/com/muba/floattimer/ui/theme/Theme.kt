package com.muba.floattimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

object Palette {
    val Bg = Color(0xFF0B0C10)
    val Surface = Color(0xFF15171D)
    val Surface2 = Color(0xFF1E2129)
    val Line = Color(0xFF2A2E39)
    val Text = Color(0xFFF3F4F8)
    val TextDim = Color(0xFF8B91A0)
    val Danger = Color(0xFFFF4D5E)
}

/** Seçilen rengin üstünde okunacak yazı rengi. */
fun Color.onColor(): Color = if (luminance() > 0.55f) Color(0xFF111216) else Color.White

@Composable
fun FloatTimerTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = accent.onColor(),
        secondary = accent,
        secondaryContainer = accent.copy(alpha = 0.22f),
        onSecondaryContainer = Palette.Text,
        background = Palette.Bg,
        onBackground = Palette.Text,
        surface = Palette.Surface,
        onSurface = Palette.Text,
        surfaceVariant = Palette.Surface2,
        onSurfaceVariant = Palette.TextDim,
        surfaceContainer = Palette.Surface,
        outline = Palette.Line,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
