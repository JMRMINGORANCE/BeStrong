package com.bestrong.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestrong.mobile.data.AccentColor
import com.bestrong.mobile.data.VisualStyle
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.theme.accentSwatch
import com.bestrong.mobile.ui.theme.styleSwatch
import com.bestrong.mobile.ui.theme.styleTitleFont

/** Nombre del estilo para la interfaz. */
@Composable
fun VisualStyle.label(): String = stringResource(
    when (this) {
        VisualStyle.CARBONO -> R.string.style_carbono
        VisualStyle.TITANIO_BLANCO -> R.string.style_titanio_blanco
    },
)

/** Una frase que lo describe. */
@Composable
fun VisualStyle.description(): String = stringResource(
    when (this) {
        VisualStyle.CARBONO -> R.string.style_carbono_desc
        VisualStyle.TITANIO_BLANCO -> R.string.style_titanio_blanco_desc
    },
)

/**
 * Tarjeta de un estilo con una miniatura de la app pintada con sus colores y su letra (independiente del tema
 * actual, para comparar): título, una serie con su peso y el botón con el acento.
 */
@Composable
fun StylePreviewCard(style: VisualStyle, selected: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier, accent: AccentColor = AccentColor.ORIGINAL) {
    val sw = styleSwatch(style, accent)
    val font = styleTitleFont(style)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onPick)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Miniatura: fondo, título, tarjeta con una serie y botón
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(sw.background).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Press banca", color = sw.text, fontFamily = font, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Clip)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(sw.card).padding(8.dp)) {
                Text("82,5 × 8", color = sw.text, fontFamily = font, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(4) { i -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i < 2) sw.accent else sw.muted.copy(alpha = 0.35f))) }
                }
            }
            Box(Modifier.fillMaxWidth().height(22.dp).clip(CircleShape).background(sw.accent))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                style.label(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Text(style.description(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, minLines = 3)
    }
}

/** Los estilos en filas de [columns] tarjetas. */
@Composable
fun StyleGrid(selected: VisualStyle, onPick: (VisualStyle) -> Unit, modifier: Modifier = Modifier, columns: Int = 2, accent: AccentColor = AccentColor.ORIGINAL) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VisualStyle.entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { s -> StylePreviewCard(s, s == selected, { onPick(s) }, Modifier.weight(1f), accent) }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Ancho mínimo de cada tarjeta para decidir cuántas caben por fila. */
val STYLE_CARD_MIN_WIDTH = 150.dp

/** Nombre del color de acento para la interfaz. */
@Composable
fun AccentColor.label(): String = stringResource(
    when (this) {
        AccentColor.ORIGINAL -> R.string.propuesta3_accent_original
        AccentColor.AZUL -> R.string.propuesta3_accent_blue
        AccentColor.VERDE -> R.string.propuesta3_accent_green
        AccentColor.MORADO -> R.string.propuesta3_accent_purple
        AccentColor.ROSA -> R.string.propuesta3_accent_pink
    },
)

/**
 * Color de la app: un círculo por acento pintado sobre el estilo actual (el primero es el del estilo) y su nombre
 * debajo. Al tocar uno la app cambia al momento.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(style: VisualStyle, selected: AccentColor, onPick: (AccentColor) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AccentColor.entries.forEach { a ->
            val isSelected = a == selected
            val name = a.label()
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPick(a) })
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(accentSwatch(style, a)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = Color.White)
                }
                Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}
