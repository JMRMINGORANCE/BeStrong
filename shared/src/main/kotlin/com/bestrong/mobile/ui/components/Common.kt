package com.bestrong.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * Cabecera de sección: una barra corta del color de acento y el título en mayúsculas pequeñas, como las secciones de
 * un marcador.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
        )
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 10.dp).semantics { heading() },
        )
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
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Pantalla vacía: el icono dentro de un halo con el degradado de marca, título fuerte y explicación. */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(112.dp).clip(CircleShape).background(
                    Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), Color.Transparent)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(brandGradient()), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
