package com.bestrong.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFFF6B1A)
private val OrangeDark = Color(0xFFFFB68F)

private val Dark = darkColorScheme(
    primary = OrangeDark,
    onPrimary = Color(0xFF552000),
    primaryContainer = Color(0xFF7A3000),
    onPrimaryContainer = Color(0xFFFFDBCA),
    tertiary = Color(0xFF7DD99A),
    tertiaryContainer = Color(0xFF1E4D2B),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
)

private val Light = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCA),
    onPrimaryContainer = Color(0xFF341000),
    tertiary = Color(0xFF2E7D45),
    tertiaryContainer = Color(0xFFC8F0D2),
)

/** Colores de marca fijos (sin color dinámico) para que la app sea reconocible. */
@Composable
fun BeStrongTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
