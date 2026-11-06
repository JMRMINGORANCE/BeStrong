package com.bestrong.mobile.data

/** Tipo de letra: la del sistema, Atkinson Hyperlegible (baja visión) u OpenDyslexic (dislexia). */
enum class AppFont { SYSTEM, LEGIBLE, DYSLEXIA }

/**
 * Estilo visual de toda la app (y del reloj): colores, letra de los títulos y forma de las esquinas. Se elige entre
 * Carbono (oscuro, negro mate y rojo racing) y Titanio Blanco (claro, titanio y naranja internacional).
 */
enum class VisualStyle(val dark: Boolean) {
    CARBONO(true),
    TITANIO_BLANCO(false),
}

/**
 * Color de acento sobre el estilo elegido (botones, acción en curso, selección). [ORIGINAL] deja el del estilo (rojo en
 * Carbono, naranja en Titanio Blanco); los demás lo cambian manteniendo el resto de la paleta.
 */
enum class AccentColor { ORIGINAL, AZUL, VERDE, MORADO, ROSA }

/** Opciones de apariencia y accesibilidad (Perfil → Apariencia). */
data class Appearance(
    val amoled: Boolean = false,
    val highContrast: Boolean = false,
    val colorblind: Boolean = false,
    val font: AppFont = AppFont.SYSTEM,
    val style: VisualStyle = VisualStyle.CARBONO,
    val accent: AccentColor = AccentColor.ORIGINAL,
)
