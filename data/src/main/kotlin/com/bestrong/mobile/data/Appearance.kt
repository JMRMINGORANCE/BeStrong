package com.bestrong.mobile.data

/** Modo claro/oscuro. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Color de acento (ARGB); DYNAMIC usa el del fondo de pantalla (Android 12+). */
enum class Accent(val seedArgb: Long) {
    ORANGE(0xFFFF6B1A),
    BLUE(0xFF2F7DF6),
    GREEN(0xFF2E9D5B),
    PURPLE(0xFF8E5BE8),
    PINK(0xFFE5487D),
    DYNAMIC(0xFFFF6B1A),
}

/** Tipo de letra: la del sistema, Atkinson Hyperlegible (baja visión) u OpenDyslexic (dislexia). */
enum class AppFont { SYSTEM, LEGIBLE, DYSLEXIA }

/** Opciones de apariencia y accesibilidad (Perfil → Apariencia). */
data class Appearance(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.ORANGE,
    val amoled: Boolean = false,
    val highContrast: Boolean = false,
    val colorblind: Boolean = false,
    val font: AppFont = AppFont.SYSTEM,
)
