package com.bestrong.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** Paleta de marca de BeStrong: la misma naranja en el móvil, el reloj, el widget y las imágenes compartidas. */
object BeStrongPalette {
    val Orange = Color(0xFFFF6B1A)
    val OrangeLight = Color(0xFFFFB68F)
    val OnOrangeLight = Color(0xFF552000)
    val OrangeContainerDark = Color(0xFF7A3000)
    val OrangeContainerLight = Color(0xFFFFDBCA)
    val OnOrangeContainerLight = Color(0xFF341000)

    /** Serie completada / éxito. */
    val Success = Color(0xFF7DD99A)
    val SuccessDark = Color(0xFF2E7D45)
    val SuccessContainerDark = Color(0xFF1E4D2B)
    val SuccessContainerLight = Color(0xFFC8F0D2)

    val Heart = Color(0xFFFF5252)
    val Background = Color(0xFF121212)

    /** Estado del volumen semanal por músculo (bajo / óptimo / alto). */
    val VolumeLow = Color(0xFFF5A623)
    val VolumeOptimal = Color(0xFF34A853)
    val VolumeHigh = Color(0xFFE5484D)

    /** Para código Android que trabaja con colores ARGB (Canvas, notificaciones). */
    val OrangeArgb: Int get() = Orange.toArgb()
}

/** Espaciados comunes. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
}
