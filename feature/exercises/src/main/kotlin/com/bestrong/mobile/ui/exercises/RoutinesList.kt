package com.bestrong.mobile.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.Routine
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.theme.FigureFont
import com.bestrong.mobile.ui.theme.KickerStyle

/**
 * Biblioteca → Rutinas: todas las rutinas agrupadas por carpeta (las sueltas primero). Tocar una la abre en el
 * editor; «Nueva rutina» crea otra.
 */
@Composable
fun RoutinesList(routines: List<Routine>, onEditRoutine: (String?) -> Unit, modifier: Modifier = Modifier) {
    val byFolder = routines.groupBy { it.folder?.takeIf { f -> f.isNotBlank() } }.toList().sortedBy { (folder, _) -> folder != null }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
    ) {
        if (routines.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.enf_lib_no_routines),
                    Modifier.padding(vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        byFolder.forEach { (folder, list) ->
            // Solo se rotula la carpeta cuando hay carpetas (si todas van sueltas, sobra el título)
            if (folder != null || byFolder.size > 1) {
                item(key = "folder-$folder") {
                    Text(
                        (folder ?: stringResource(R.string.enf_lib_no_folder)).uppercase(),
                        Modifier.padding(top = 16.dp, bottom = 4.dp),
                        style = KickerStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list, key = { it.id }) { r -> RoutineRow(r) { onEditRoutine(r.id) } }
        }
        item {
            OutlinedButton(onClick = { onEditRoutine(null) }, modifier = Modifier.padding(top = 16.dp)) {
                Icon(Icons.Default.Add, null)
                Text(stringResource(R.string.enf_lib_new_routine), Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** Una rutina: su emoji (o una pesa) en un cuadrado, el nombre y cuántos ejercicios tiene. */
@Composable
private fun RoutineRow(r: Routine, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                val emoji = r.emoji
                if (emoji != null) Text(emoji) else Icon(Icons.Default.FitnessCenter, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.exercise_count, r.exercises.size, r.exercises.size),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FigureFont,
                    color = scheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
        }
        HorizontalDivider(color = scheme.outlineVariant)
    }
}
