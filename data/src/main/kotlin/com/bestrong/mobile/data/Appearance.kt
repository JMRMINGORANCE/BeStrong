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

/** Opciones de apariencia y accesibilidad (Perfil → Apariencia). */
data class Appearance(
    val amoled: Boolean = false,
    val highContrast: Boolean = false,
    val colorblind: Boolean = false,
    val font: AppFont = AppFont.SYSTEM,
    val style: VisualStyle = VisualStyle.CARBONO,
)
