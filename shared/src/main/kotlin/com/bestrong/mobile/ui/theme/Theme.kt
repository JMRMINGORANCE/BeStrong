package com.bestrong.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bestrong.designsystem.BeStrongPalette as P
import com.bestrong.mobile.data.AppFont
import com.bestrong.mobile.data.Appearance
import com.bestrong.mobile.shared.R

/**
 * Colores de estado (bien / regular / mal). En modo daltónico se usa la paleta Okabe-Ito
 * (azul / amarillo / bermellón), distinguible con deuteranopía y protanopía, y además
 * cada estado lleva su símbolo para no depender solo del color.
 */
data class StatusColors(val good: Color, val warn: Color, val bad: Color, val colorblind: Boolean) {
    val goodMark get() = if (colorblind) "▲ " else ""
    val badMark get() = if (colorblind) "▼ " else ""
    val warnMark get() = if (colorblind) "● " else ""
}

private val NormalStatus = StatusColors(P.VolumeOptimal, P.VolumeLow, P.VolumeHigh, colorblind = false)
private val ColorblindStatus = StatusColors(Color(0xFF0072B2), Color(0xFFE69F00), Color(0xFFD55E00), colorblind = true)

val LocalStatusColors = staticCompositionLocalOf { NormalStatus }

/**
 * El fondo real de las pantallas. En el tema, [ColorScheme.background] y [ColorScheme.surface] son transparentes para
 * que se vea el fondo de marca (color sólido con un resplandor arriba, como en la bienvenida) detrás de barras,
 * listas y Scaffold; lo que necesite un fondo opaco usa este.
 */
val LocalScreenColor = staticCompositionLocalOf { Color(0xFF0E0F13) }

/**
 * Fondo de marca de una pantalla: el color de fondo y, arriba, un resplandor del color [tint] que se desvanece
 * (naranja en Entrenar, ámbar en Historial, verde en Ejercicios…).
 */
@Composable
fun ScreenBackdrop(
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable () -> Unit,
) {
    val dark = LocalScreenColor.current.luminance() < 0.5f
    // Un toque de color arriba, sin teñir media pantalla (con más se veía turbio, marrón)
    val glow by androidx.compose.animation.animateColorAsState(tint.copy(alpha = if (dark) 0.16f else 0.10f), label = "glow")
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxSize()
            .background(LocalScreenColor.current)
            // Cola suave (sin cambio brusco de pendiente: se veía una línea a media pantalla)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to glow, 0.12f to glow.copy(alpha = glow.alpha * 0.5f), 0.24f to glow.copy(alpha = glow.alpha * 0.15f), 0.38f to Color.Transparent)),
    ) { content() }
}
val LocalAppearance = staticCompositionLocalOf { Appearance() }

/** Atajo para los colores de estado del tema actual. */
val statusColors: StatusColors
    @Composable @ReadOnlyComposable
    get() = LocalStatusColors.current

/**
 * Tipografía con carácter: títulos muy gruesos y apretados (como un marcador deportivo), cuerpo cómodo y etiquetas
 * con algo de aire.
 */
private fun beStrongTypography(): Typography {
    val base = Typography()
    fun TextStyle.heavy(weight: FontWeight, tracking: Float) = copy(fontWeight = weight, letterSpacing = tracking.sp)
    return base.copy(
        displayLarge = base.displayLarge.heavy(FontWeight.Black, -1.5f),
        displayMedium = base.displayMedium.heavy(FontWeight.Black, -1f),
        displaySmall = base.displaySmall.heavy(FontWeight.ExtraBold, -0.5f),
        headlineLarge = base.headlineLarge.heavy(FontWeight.ExtraBold, -0.5f),
        headlineMedium = base.headlineMedium.heavy(FontWeight.ExtraBold, -0.25f),
        headlineSmall = base.headlineSmall.heavy(FontWeight.Bold, 0f),
        // Títulos de las barras: grandes y gruesos, como los de la bienvenida
        titleLarge = base.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp).heavy(FontWeight.Black, -0.3f),
        titleMedium = base.titleMedium.heavy(FontWeight.SemiBold, 0.1f),
        titleSmall = base.titleSmall.heavy(FontWeight.SemiBold, 0.1f),
        labelLarge = base.labelLarge.heavy(FontWeight.SemiBold, 0.3f),
        labelMedium = base.labelMedium.heavy(FontWeight.Medium, 0.4f),
        labelSmall = base.labelSmall.heavy(FontWeight.Medium, 0.5f),
    )
}

private fun lerp(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/** Negro puro (pantallas AMOLED del Fold: ahorra batería y da más contraste). */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
)

/** Alto contraste: textos y bordes al máximo, sin grises suaves. */
private fun ColorScheme.highContrast(dark: Boolean): ColorScheme {
    val fg = if (dark) Color.White else Color.Black
    val bg = if (dark) Color.Black else Color.White
    return copy(
        background = bg,
        surface = bg,
        onBackground = fg,
        onSurface = fg,
        onSurfaceVariant = fg,
        outline = fg,
        outlineVariant = if (dark) Color(0xFFBDBDBD) else Color(0xFF424242),
        surfaceVariant = if (dark) Color(0xFF1A1A1A) else Color(0xFFEDEDED),
        primary = if (dark) lerp(primary, Color.White, 0.4f) else lerp(primary, Color.Black, 0.35f),
        onPrimary = bg,
    )
}

private val Legible = FontFamily(Font(R.font.atkinson_regular), Font(R.font.atkinson_bold, FontWeight.Bold))
private val Dyslexic = FontFamily(Font(R.font.opendyslexic_regular), Font(R.font.opendyslexic_bold, FontWeight.Bold))

private fun Typography.withFont(family: FontFamily?): Typography {
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
        titleMedium = titleMedium.f(),
        titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(),
        bodyMedium = bodyMedium.f(),
        bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(),
        labelMedium = labelMedium.f(),
        labelSmall = labelSmall.f(),
    )
}

/** Colores de marca (o el acento elegido), con modo AMOLED, alto contraste y letra accesible. Paleta en :designsystem. */
@Composable
fun BeStrongTheme(appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
    // El estilo elegido (Carbono o Titanio Blanco) trae su paleta, oscura o clara
    val dark = appearance.style.dark
    var scheme = styleScheme(appearance.style)
    if (dark && appearance.amoled) scheme = scheme.amoled()
    if (appearance.highContrast) scheme = scheme.highContrast(dark)
    val family = when (appearance.font) {
        AppFont.SYSTEM -> null
        AppFont.LEGIBLE -> Legible
        AppFont.DYSLEXIA -> Dyslexic
    }
    val screen = scheme.background
    CompositionLocalProvider(
        LocalAppearance provides appearance,
        LocalStatusColors provides if (appearance.colorblind) ColorblindStatus else NormalStatus,
        LocalScreenColor provides screen,
    ) {
        // Fondo y superficie transparentes: el fondo de marca de [ScreenBackdrop] se ve detrás de todo
        MaterialTheme(
            colorScheme = scheme.copy(background = Color.Transparent, surface = Color.Transparent),
            // La letra accesible (si se eligió) manda sobre la del estilo
            typography = beStrongTypography().withDisplayFont(if (family == null) styleDisplayFont(appearance.style) else null).withFont(family),
            shapes = styleShapes(appearance.style),
        ) {
            ScreenBackdrop { content() }
        }
    }
}
