package com.bestrong.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Formato de fecha localizado al idioma del sistema
private fun dateFormatter() = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
private fun shortDateFormatter() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

fun formatDate(epochMs: Long): String =
    dateFormatter().format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())).replaceFirstChar { it.uppercase() }

/** Hora (HH:mm) y fecha corta, p. ej. «18:42 · 9 oct». */
fun formatTime(epochMs: Long): String =
    DateTimeFormatter.ofPattern("HH:mm · d MMM", Locale.getDefault()).format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

/** Solo la hora, con el formato de 12/24 h que tenga el sistema («18:42» o «6:42 PM»). */
fun formatClock(epochMs: Long, is24Hour: Boolean): String {
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm")
    return DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
}

fun formatShortDate(epochMs: Long): String =
    shortDateFormatter().format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

/**
 * Cabecera de sección: título limpio, sin adornos, y una acción opcional a la derecha («+ Nueva», «Ver todo»).
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, large: Boolean = false, action: (@Composable () -> Unit)? = null) {
    // Grande entre bloques de la pantalla; dentro de una tarjeta, más discreto
    Row(modifier.fillMaxWidth().padding(top = if (large) 16.dp else 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

/** Título grande de cada pestaña: un antetítulo pequeño encima (la fecha, un resumen) y las acciones a la derecha. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, overline: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            overline?.let {
                Text(it.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Contenedor limpio: esquinas grandes y un color de superficie suave, sin bordes ni sombras. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    /** Qué hace tocarlo (TalkBack: «toca dos veces para…»). */
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val base = modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
    Column(
        (if (onClick != null) base.clickable(onClickLabel = onClickLabel, onClick = onClick) else base).padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * Acceso rápido: icono en un círculo de color y la etiqueta debajo (o al lado con [horizontal], para letra grande);
 * [filled] para el principal.
 */
@Composable
fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, horizontal: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val base = modifier.clip(MaterialTheme.shapes.large)
        .background(if (filled) brandGradient() else androidx.compose.ui.graphics.SolidColor(scheme.surfaceContainer))
        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
    val bubble: @Composable () -> Unit = {
        Box(
            Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (filled) scheme.surfaceContainerLowest.copy(alpha = 0.25f) else scheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(icon, null, tint = if (filled) scheme.onPrimary else scheme.primary)
        }
    }
    val text: @Composable (Modifier) -> Unit = { m ->
        Text(
            label,
            m,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (filled) scheme.onPrimary else scheme.onSurface,
            textAlign = if (horizontal) TextAlign.Start else TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (horizontal) {
        Row(base.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            bubble()
            text(Modifier.padding(start = 10.dp).weight(1f))
        }
    } else {
        Column(
            base.padding(horizontal = 6.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            bubble()
            text(Modifier)
        }
    }
}

/** Dato pequeño en una píldora («6 ejercicios», «≈ 45 min»). */
@Composable
fun MetaPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { androidx.compose.material3.Icon(it, null, Modifier.size(14.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Degradado de marca (acento → ámbar) para lo que tiene que destacar. */
@Composable
fun brandGradient(): Brush = Brush.linearGradient(
    listOf(MaterialTheme.colorScheme.primary, lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, 0.7f)),
)

/**
 * Tarjeta destacada: fondo con degradado de marca y texto oscuro encima. Para lo que importa ahora mismo (el entreno
 * en curso, «Hoy toca»).
 */
@Composable
fun HeroCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    val base = modifier.fillMaxWidth().clip(shape).background(brandGradient())
    Column(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onPrimary) {
            content()
        }
    }
}

/** Dato grande con su etiqueta: número muy visible sobre un fondo con un toque del acento. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(MaterialTheme.shapes.medium)
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.primary, 0.08f),
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ),
            ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Con letra grande la etiqueta encoge antes de cortarse («Duraci…»)
            AutoSizeLabel(label)
        }
    }
}

/** Etiqueta de una línea que reduce la letra para caber entera (con letra del sistema grande). */
@Composable
fun AutoSizeLabel(label: String, modifier: Modifier = Modifier, textAlign: androidx.compose.ui.text.style.TextAlign? = null) {
    val style = MaterialTheme.typography.labelSmall
    androidx.compose.foundation.text.BasicText(
        label,
        modifier = modifier,
        style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = textAlign ?: style.textAlign),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = style.fontSize),
    )
}

/**
 * Pantalla vacía como las de la bienvenida: la ilustración animada (con [orbit] alrededor, si se dan), título grande y
 * explicación.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    orbit: List<ImageVector> = emptyList(),
    /** Botón para salir del vacío («Empezar a entrenar»). */
    action: Pair<String, () -> Unit>? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            com.bestrong.mobile.ui.help.BrandIllustration(icon, orbit, MaterialTheme.colorScheme.primary, Modifier.size(200.dp), animated = false)
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            action?.let { (label, onClick) ->
                androidx.compose.material3.Button(onClick = onClick, modifier = Modifier.padding(top = 8.dp).height(52.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Buscador en píldora, sin borde: el mismo en todas las pantallas. */
@Composable
fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.TextField(
        value = value,
        onValueChange = onValueChange,
        leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, null) },
        // Con texto, una X para borrarlo; sin texto, lo que se pida (p. ej. el micrófono)
        trailingIcon = if (value.isNotEmpty()) {
            {
                androidx.compose.material3.IconButton(onClick = { onValueChange("") }) {
                    androidx.compose.material3.Icon(Icons.Default.Close, androidx.compose.ui.res.stringResource(com.bestrong.mobile.shared.R.string.routine_search_clear))
                }
            }
        } else {
            trailing
        },
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = androidx.compose.foundation.shape.CircleShape,
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
