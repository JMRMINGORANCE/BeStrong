package com.bestrong.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.bestrong.core.util.WeightUnit
import com.bestrong.designsystem.BeStrongPalette

/** Acento del estilo elegido en el móvil (rojo en Carbono, naranja en Titanio Blanco). */
val Brand: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary

/** «Enfoque»: el color significa algo. Verde = hecho, ámbar = récord, el acento = acción. */
val Done = Color(0xFF00D26A)
val Record = Color(0xFFFFB547)
val HeartRed = BeStrongPalette.Heart

/** Cifras (peso, reps, tiempos): tabulares para que no bailen al girar la corona. */
val FigureStyle = androidx.compose.ui.text.TextStyle(
    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
    fontFeatureSettings = "tnum",
)

/** Etiqueta pequeña en mayúsculas con aire («PRESS BANCA · 3/5»). */
val KickerStyle = androidx.compose.ui.text.TextStyle(
    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
    fontSize = 10.sp,
    letterSpacing = 1.5.sp,
)

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
        "TITANIO_BLANCO" -> scheme(0xFFD9480F, 0xFFFFFFFF, 0xFF6B2A0A, 0xFFFFDBCA, 0xFFFFB547, 0xFF2A1A00, 0xFF222326)
        // Carbono (también si llega un nombre desconocido)
        else -> scheme(0xFFE10600, 0xFFFFFFFF, 0xFF4A0703, 0xFFFFDAD5, 0xFFFFB547, 0xFF2A1A00, 0xFF151517)
    }
}

/** Material 3 para Wear OS con los colores del estilo elegido en el móvil. */
@Composable
fun BeStrongWearTheme(style: String = "CARBONO", content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = wearColors(style), content = content)
}
