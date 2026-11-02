package com.bestrong.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bestrong.mobile.data.VisualStyle
import com.bestrong.mobile.shared.R

/**
 * Los estilos visuales que el usuario puede elegir (Ajustes → Apariencia, o al empezar):
 * - Carbono: fibra de carbono, negro mate y rojo racing (telemetría de F1).
 * - Titanio Blanco: titanio claro con naranja internacional.
 */
internal fun styleScheme(style: VisualStyle): ColorScheme = when (style) {
    VisualStyle.CARBONO -> darkColorScheme(
        primary = Color(0xFFFF2A1F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF5A0B07),
        onPrimaryContainer = Color(0xFFFFDAD5),
        secondary = Color(0xFFFFD400),
        onSecondary = Color(0xFF231D00),
        secondaryContainer = Color(0xFFFF2A1F),
        onSecondaryContainer = Color.White,
        tertiary = Color(0xFF00D26A),
        onTertiary = Color(0xFF00210F),
        tertiaryContainer = Color(0xFF0B3F24),
        onTertiaryContainer = Color(0xFFB8F5D0),
        background = Color(0xFF0B0B0C),
        onBackground = Color(0xFFF4F4F2),
        surface = Color(0xFF0B0B0C),
        onSurface = Color(0xFFF4F4F2),
        surfaceVariant = Color(0xFF1F1F22),
        onSurfaceVariant = Color(0xFFA6A7AB),
        surfaceContainerLowest = Color(0xFF060607),
        surfaceContainerLow = Color(0xFF121214),
        surfaceContainer = Color(0xFF17171A),
        surfaceContainerHigh = Color(0xFF1F1F22),
        surfaceContainerHighest = Color(0xFF29292D),
        outline = Color(0xFF3A3A3F),
        outlineVariant = Color(0xFF26262A),
        error = Color(0xFFFF6B6B),
    )
    VisualStyle.TITANIO_BLANCO -> lightColorScheme(
        primary = Color(0xFFD9480F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBCA),
        onPrimaryContainer = Color(0xFF3A1000),
        secondary = Color(0xFFFF7A3D),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD9480F),
        onSecondaryContainer = Color.White,
        tertiary = Color(0xFF2E7D45),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD3EEDC),
        onTertiaryContainer = Color(0xFF0D3A1B),
        background = Color(0xFFECEBE7),
        onBackground = Color(0xFF1D1F22),
        surface = Color(0xFFECEBE7),
        onSurface = Color(0xFF1D1F22),
        surfaceVariant = Color(0xFFE3E2DE),
        onSurfaceVariant = Color(0xFF55585D),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F3F0),
        surfaceContainer = Color(0xFFF9F9F7),
        surfaceContainerHigh = Color(0xFFE3E2DE),
        surfaceContainerHighest = Color(0xFFD9D8D3),
        outline = Color(0xFFB2B1AC),
        outlineVariant = Color(0xFFD3D2CD),
    )
}

/** Letra de los títulos y las cifras grandes de cada estilo (el cuerpo sigue con la del sistema, más legible). */
internal fun styleDisplayFont(style: VisualStyle): FontFamily = when (style) {
    VisualStyle.CARBONO -> FontFamily(Font(R.font.style_carbono_bold, FontWeight.Bold), Font(R.font.style_carbono_black, FontWeight.Black))
    VisualStyle.TITANIO_BLANCO -> FontFamily(Font(R.font.style_titanio_semibold, FontWeight.SemiBold), Font(R.font.style_titanio_extrabold, FontWeight.ExtraBold))
}

/** Pone la letra del estilo en los títulos (display, headline y title). */
internal fun Typography.withDisplayFont(family: FontFamily?): Typography {
    if (family == null) return this
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(),
        displayMedium = displayMedium.f(),
        displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(),
        headlineMedium = headlineMedium.f(),
        headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(),
    )
}

/** Esquinas: Carbono más secas (instrumento); Titanio Blanco algo más suaves. */
internal fun styleShapes(style: VisualStyle): Shapes = when (style) {
    VisualStyle.CARBONO -> shapes(6, 8, 10, 12, 16)
    VisualStyle.TITANIO_BLANCO -> shapes(8, 10, 14, 18, 24)
}

private fun shapes(xs: Int, s: Int, m: Int, l: Int, xl: Int) = Shapes(
    extraSmall = RoundedCornerShape(xs.dp),
    small = RoundedCornerShape(s.dp),
    medium = RoundedCornerShape(m.dp),
    large = RoundedCornerShape(l.dp),
    extraLarge = RoundedCornerShape(xl.dp),
)

/** Muestra de colores para el selector de estilo: fondo, tarjeta, acento y texto. */
data class StyleSwatch(val background: Color, val card: Color, val accent: Color, val text: Color, val muted: Color)

fun styleSwatch(style: VisualStyle): StyleSwatch {
    val s = styleScheme(style)
    return StyleSwatch(s.background, s.surfaceContainer, s.primary, s.onSurface, s.onSurfaceVariant)
}

/** La letra de títulos del estilo (para enseñar el nombre de cada uno con la suya en el selector). */
fun styleTitleFont(style: VisualStyle): FontFamily = styleDisplayFont(style)
