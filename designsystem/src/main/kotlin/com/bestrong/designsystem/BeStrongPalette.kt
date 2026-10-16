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
    val Background = Color(0xFF0E0F13)

    /** Superficies oscuras escalonadas (negro azulado): las tarjetas se separan del fondo sin bordes. */
    val SurfaceLowest = Color(0xFF0A0B0E)
    val SurfaceLow = Color(0xFF13151A)
    val SurfaceMid = Color(0xFF181B21)
    val SurfaceHigh = Color(0xFF1F232A)
    val SurfaceHighest = Color(0xFF282D35)
    val OutlineDark = Color(0xFF3A404B)
    val OnSurfaceVariantDark = Color(0xFFB4BAC5)

    /** Superficies claras cálidas (papel). */
    val LightBackground = Color(0xFFF7F4F0)
    val LightSurfaceLow = Color(0xFFFFFFFF)
    val LightSurfaceMid = Color(0xFFF2EEE9)
    val LightSurfaceHigh = Color(0xFFECE7E1)
    val LightSurfaceHighest = Color(0xFFE5DFD8)

    /** Acento secundario (ámbar) para los degradados de las tarjetas destacadas. */
    val Amber = Color(0xFFFFB547)
    val AmberDark = Color(0xFF8A5A00)
    val Ember = Color(0xFFE5484D)

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
