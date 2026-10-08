package com.bestrong.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val Brand = Color(0xFFFF6B1A)
val Done = Color(0xFF7DD99A)
val HeartRed = Color(0xFFFF5252)

@Composable
fun BeStrongWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(primary = Brand, primaryVariant = Color(0xFFB34700), secondary = Color(0xFFFFB68F), onPrimary = Color.Black),
        content = content,
    )
}

/** Unidad de peso elegida en el móvil. */
val LocalWeightUnit = androidx.compose.runtime.staticCompositionLocalOf { com.bestrong.core.util.WeightUnit.KG }
