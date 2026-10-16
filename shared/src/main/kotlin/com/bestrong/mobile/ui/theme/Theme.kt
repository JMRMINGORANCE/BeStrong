package com.bestrong.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestrong.designsystem.BeStrongPalette as P
import com.bestrong.mobile.data.Accent
import com.bestrong.mobile.data.AppFont
import com.bestrong.mobile.data.Appearance
import com.bestrong.mobile.data.ThemeMode
import com.bestrong.mobile.shared.R

/** Color semilla de cada acento (el modelo [Accent] vive en la capa de datos, sin Compose). */
private val Accent.seed: Color get() = Color(seedArgb)

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
    val glow by androidx.compose.animation.animateColorAsState(tint.copy(alpha = if (dark) 0.30f else 0.20f), label = "glow")
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxSize()
            .background(LocalScreenColor.current)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to glow, 0.45f to Color.Transparent)),
    ) { content() }
}
val LocalAppearance = staticCompositionLocalOf { Appearance() }

/** Atajo para los colores de estado del tema actual. */
val statusColors: StatusColors
    @Composable @ReadOnlyComposable
    get() = LocalStatusColors.current

/**
 * Oscuro de marca: fondo negro azulado con superficies escalonadas (las tarjetas flotan sin bordes), el acento
 * vivo y un secundario ámbar para los degradados.
 */
private fun brandDark(accent: Accent): ColorScheme {
    val orange = accent == Accent.ORANGE
    val primary = if (orange) Color(0xFFFF8A4C) else lerp(accent.seed, Color.White, 0.3f)
    return darkColorScheme(
        primary = primary,
        onPrimary = if (orange) Color(0xFF2A0E00) else lerp(accent.seed, Color.Black, 0.75f),
        primaryContainer = if (orange) Color(0xFF5C2408) else lerp(accent.seed, Color.Black, 0.55f),
        onPrimaryContainer = if (orange) Color(0xFFFFDBCA) else lerp(accent.seed, Color.White, 0.82f),
        secondary = P.Amber,
        onSecondary = Color(0xFF2B1A00),
        secondaryContainer = Color(0xFF3A2A12),
        onSecondaryContainer = Color(0xFFFFDDB0),
        tertiary = P.Success,
        onTertiary = Color(0xFF00391A),
        tertiaryContainer = P.SuccessContainerDark,
        onTertiaryContainer = Color(0xFFC8F0D2),
        background = P.Background,
        onBackground = Color(0xFFEDEFF3),
        surface = P.Background,
        onSurface = Color(0xFFEDEFF3),
        surfaceVariant = P.SurfaceHigh,
        onSurfaceVariant = P.OnSurfaceVariantDark,
        surfaceContainerLowest = P.SurfaceLowest,
        surfaceContainerLow = P.SurfaceLow,
        surfaceContainer = P.SurfaceMid,
        surfaceContainerHigh = P.SurfaceHigh,
        surfaceContainerHighest = P.SurfaceHighest,
        outline = P.OutlineDark,
        outlineVariant = Color(0xFF2A2F38),
        error = Color(0xFFFF7A7E),
    )
}

/** Claro de marca: papel cálido, tarjetas blancas y el mismo acento con más fuerza. */
private fun brandLight(accent: Accent): ColorScheme {
    val orange = accent == Accent.ORANGE
    return lightColorScheme(
        primary = if (orange) Color(0xFFE85A0C) else lerp(accent.seed, Color.Black, 0.12f),
        onPrimary = Color.White,
        primaryContainer = if (orange) P.OrangeContainerLight else lerp(accent.seed, Color.White, 0.8f),
        onPrimaryContainer = if (orange) P.OnOrangeContainerLight else lerp(accent.seed, Color.Black, 0.75f),
        secondary = P.AmberDark,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE6C2),
        onSecondaryContainer = Color(0xFF2B1A00),
        tertiary = P.SuccessDark,
        tertiaryContainer = P.SuccessContainerLight,
        background = P.LightBackground,
        onBackground = Color(0xFF1B1C1F),
        surface = P.LightBackground,
        onSurface = Color(0xFF1B1C1F),
        surfaceVariant = P.LightSurfaceHigh,
        onSurfaceVariant = Color(0xFF55595F),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = P.LightSurfaceLow,
        surfaceContainer = P.LightSurfaceMid,
        surfaceContainerHigh = P.LightSurfaceHigh,
        surfaceContainerHighest = P.LightSurfaceHighest,
        outline = Color(0xFFB9B2AA),
        outlineVariant = Color(0xFFDDD6CE),
    )
}

/** Esquinas generosas: tarjetas de 24 dp, diálogos de 32 y chips/botones redondeados. */
private val BeStrongShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

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
    val dark = when (appearance.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    var scheme = if (appearance.accent == Accent.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        brandDark(appearance.accent)
    } else {
        brandLight(appearance.accent)
    }
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
            typography = beStrongTypography().withFont(family),
            shapes = BeStrongShapes,
        ) {
            ScreenBackdrop { content() }
        }
    }
}
