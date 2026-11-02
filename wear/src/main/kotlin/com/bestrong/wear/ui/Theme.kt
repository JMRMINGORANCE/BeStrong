package com.bestrong.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.bestrong.core.util.WeightUnit
import com.bestrong.designsystem.BeStrongPalette

/** Acento del estilo elegido en el móvil (rojo en Carbono, naranja en Titanio Blanco). */
val Brand: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary
val Done = BeStrongPalette.Success
val HeartRed = BeStrongPalette.Heart

/** Unidad de peso elegida en el móvil. */
val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.KG }

/**
 * Colores del reloj para cada estilo del móvil. El fondo sigue negro (pantalla OLED, batería) también en los estilos
 * claros: del estilo se toman el acento y los contenedores.
 */
private fun wearColors(style: String): ColorScheme {
    fun scheme(primary: Long, onPrimary: Long, container: Long, onContainer: Long, secondary: Long, onSecondary: Long, surface: Long) = ColorScheme(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(container),
        onPrimaryContainer = Color(onContainer),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        tertiary = Done,
        onTertiary = Color.Black,
        surfaceContainer = Color(surface),
    )
    return when (style) {
        "TITANIO_BLANCO" -> scheme(0xFFFF6A2B, 0xFF2A0E00, 0xFF6B2A0A, 0xFFFFDBCA, 0xFFD9D8D3, 0xFF1D1F22, 0xFF222326)
        // Carbono (también si llega un nombre desconocido)
        else -> scheme(0xFFFF2A1F, 0xFFFFFFFF, 0xFF5A0B07, 0xFFFFDAD5, 0xFFFFD400, 0xFF231D00, 0xFF1A1A1D)
    }
}

/** Material 3 para Wear OS con los colores del estilo elegido en el móvil. */
@Composable
fun BeStrongWearTheme(style: String = "CARBONO", content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = wearColors(style), content = content)
}
