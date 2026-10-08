package com.bestrong.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.bestrong.designsystem.BeStrongPalette as P

private val Dark = darkColorScheme(
    primary = P.OrangeLight,
    onPrimary = P.OnOrangeLight,
    primaryContainer = P.OrangeContainerDark,
    onPrimaryContainer = P.OrangeContainerLight,
    tertiary = P.Success,
    tertiaryContainer = P.SuccessContainerDark,
    background = P.Background,
    surface = P.Background,
)

private val Light = lightColorScheme(
    primary = P.Orange,
    onPrimary = Color.White,
    primaryContainer = P.OrangeContainerLight,
    onPrimaryContainer = P.OnOrangeContainerLight,
    tertiary = P.SuccessDark,
    tertiaryContainer = P.SuccessContainerLight,
)

/** Colores de marca fijos (sin color dinámico) para que la app sea reconocible. Paleta en :designsystem. */
@Composable
fun BeStrongTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
