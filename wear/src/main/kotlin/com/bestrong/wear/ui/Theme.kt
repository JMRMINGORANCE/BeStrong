package com.bestrong.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.bestrong.core.util.WeightUnit

val Brand = Color(0xFFFF6B1A)
val Done = Color(0xFF7DD99A)
val HeartRed = Color(0xFFFF5252)

/** Unidad de peso elegida en el móvil. */
val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.KG }

private val BeStrongColors = ColorScheme(
    primary = Brand,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF7A3000),
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFFFFB68F),
    onSecondary = Color(0xFF552000),
    tertiary = Done,
    onTertiary = Color.Black,
)

/** Material 3 para Wear OS con los colores de la marca. */
@Composable
fun BeStrongWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BeStrongColors, content = content)
}
