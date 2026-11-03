package com.bestrong.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bestrong.mobile.shared.R

/*
 * Piezas de los Ajustes: grupos con título, filas con su icono de color, interruptores y opciones en chips. Todas las
 * pantallas de ajustes se montan con estas, así se ven y se tocan igual.
 */

/**
 * Icono en un cuadrado neutro («Enfoque»: el color se reserva para acción, hecho y récord). [tint] queda para quien
 * quiera marcar un icono que de verdad lo necesite; por defecto no se pinta.
 */
@Composable
fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Int = 34) {
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** Grupo de ajustes: título pequeño encima y las filas en una tarjeta, separadas por líneas finas. */
@Composable
fun SettingsGroup(title: String?, modifier: Modifier = Modifier, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(
                it.uppercase(),
                Modifier.padding(start = 4.dp).semantics { heading() },
                style = com.bestrong.mobile.ui.theme.KickerStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                .padding(vertical = 2.dp),
            content = content,
        )
        footer?.let {
            Text(it, Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Línea fina entre filas de un grupo (empieza donde el texto, como en los ajustes del sistema). */
@Composable
fun SettingsDivider(inset: Boolean = true) {
    HorizontalDivider(Modifier.padding(start = if (inset) 62.dp else 16.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** Fila base: icono (opcional), título, descripción y lo que vaya a la derecha. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    tint: Color = Color.Unspecified,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val base = modifier.fillMaxWidth().heightIn(min = 56.dp)
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon?.let { IconTile(it, tint) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/**
 * Fila que abre otra pantalla: con la flecha a la derecha, el valor actual en la letra de las cifras ([value],
 * p. ej. «2:30 · vibrar») y un aviso opcional ([badge], p. ej. «2 pendientes»).
 */
@Composable
fun SettingsNavRow(
    title: String,
    icon: ImageVector?,
    tint: Color,
    onClick: () -> Unit,
    summary: String? = null,
    badge: String? = null,
    value: String? = null,
) {
    SettingsRow(title, summary = summary, icon = icon, tint = tint, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            value?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = com.bestrong.mobile.ui.theme.FigureFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
            }
            badge?.let {
                Text(
                    it,
                    Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.error).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError,
                    maxLines = 1,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Ajuste con interruptor: toda la fila es pulsable y TalkBack la lee como «<título>, interruptor, activado» en lugar
 * de un interruptor sin nombre.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    summary: String? = null,
    icon: ImageVector? = null,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
) {
    SettingsRow(
        title,
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        summary = summary,
        icon = icon,
        tint = tint,
    ) {
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Elegir una opción entre pocas: título, explicación y chips debajo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SettingsChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    summary: String? = null,
    icon: ImageVector? = null,
    tint: Color = Color.Unspecified,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            icon?.let { IconTile(it, tint) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        FlowRow(
            Modifier.padding(start = if (icon != null) 48.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
            extra?.invoke()
        }
    }
}

/**
 * Pantalla de ajustes a pantalla completa: flecha para volver, título grande y el contenido en una lista con aire.
 * Atrás (gesto o botón) también la cierra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(title: String, onBack: () -> Unit, subtitle: String? = null, content: LazyListScope.() -> Unit) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        com.bestrong.mobile.ui.theme.ScreenBackdrop {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {},
                        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        Modifier.widthIn(max = 720.dp).fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                        content()
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}
