package com.bestrong.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.bestrong.core.util.WeightUnit
import com.bestrong.designsystem.BeStrongPalette

val Brand = BeStrongPalette.Orange
val Done = BeStrongPalette.Success
val HeartRed = BeStrongPalette.Heart

/** Unidad de peso elegida en el móvil. */
val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.KG }

private val BeStrongColors = ColorScheme(
    primary = Brand,
    onPrimary = Color.Black,
    primaryContainer = BeStrongPalette.OrangeContainerDark,
    onPrimaryContainer = BeStrongPalette.OrangeContainerLight,
    secondary = BeStrongPalette.OrangeLight,
    onSecondary = BeStrongPalette.OnOrangeLight,
    tertiary = Done,
    onTertiary = Color.Black,
)

/** Material 3 para Wear OS con los colores de la marca. */
@Composable
fun BeStrongWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BeStrongColors, content = content)
}
