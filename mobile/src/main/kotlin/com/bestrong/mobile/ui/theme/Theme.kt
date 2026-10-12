package com.bestrong.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.bestrong.designsystem.BeStrongPalette as P
import com.bestrong.mobile.R
import com.bestrong.mobile.data.Accent
import com.bestrong.mobile.data.AppFont
import com.bestrong.mobile.data.Appearance
import com.bestrong.mobile.data.ThemeMode

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
val LocalAppearance = staticCompositionLocalOf { Appearance() }

/** Atajo para los colores de estado del tema actual. */
val statusColors: StatusColors
    @Composable @ReadOnlyComposable
    get() = LocalStatusColors.current

private fun brandDark(accent: Accent): ColorScheme = if (accent == Accent.ORANGE) {
    darkColorScheme(
        primary = P.OrangeLight,
        onPrimary = P.OnOrangeLight,
        primaryContainer = P.OrangeContainerDark,
        onPrimaryContainer = P.OrangeContainerLight,
        tertiary = P.Success,
        tertiaryContainer = P.SuccessContainerDark,
        background = P.Background,
        surface = P.Background,
    )
} else {
    darkColorScheme(
        primary = lerp(accent.seed, Color.White, 0.35f),
        onPrimary = lerp(accent.seed, Color.Black, 0.7f),
        primaryContainer = lerp(accent.seed, Color.Black, 0.45f),
        onPrimaryContainer = lerp(accent.seed, Color.White, 0.8f),
        tertiary = P.Success,
        tertiaryContainer = P.SuccessContainerDark,
        background = P.Background,
        surface = P.Background,
    )
}

private fun brandLight(accent: Accent): ColorScheme = if (accent == Accent.ORANGE) {
    lightColorScheme(
        primary = P.Orange,
        onPrimary = Color.White,
        primaryContainer = P.OrangeContainerLight,
        onPrimaryContainer = P.OnOrangeContainerLight,
        tertiary = P.SuccessDark,
        tertiaryContainer = P.SuccessContainerLight,
    )
} else {
    lightColorScheme(
        primary = lerp(accent.seed, Color.Black, 0.1f),
        onPrimary = Color.White,
        primaryContainer = lerp(accent.seed, Color.White, 0.8f),
        onPrimaryContainer = lerp(accent.seed, Color.Black, 0.75f),
        tertiary = P.SuccessDark,
        tertiaryContainer = P.SuccessContainerLight,
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
    CompositionLocalProvider(
        LocalAppearance provides appearance,
        LocalStatusColors provides if (appearance.colorblind) ColorblindStatus else NormalStatus,
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography().withFont(family), content = content)
    }
}
