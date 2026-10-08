package com.bestrong.mobile.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSource
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatVolume
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.ui.components.EmptyState
import com.bestrong.mobile.ui.components.StatTile
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.workout.describeSet

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel, onWorkoutStarted: () -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    val days by vm.trainingDays.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                Column(Modifier.fillMaxSize()) {
                    TopAppBar(title = { Text("Historial") })
                    if (history.isEmpty()) {
                        EmptyState(Icons.Default.History, "Sin entrenamientos todavía", "Cuando termines un entrenamiento aparecerá aquí.")
                        return@Column
                    }
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { CalendarMonth(days, Modifier.padding(bottom = 8.dp)) }
                        items(history, key = { it.id }) { w ->
                            WorkoutCard(w, Modifier.clickable { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, w.id) })
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val id = navigator.currentDestination?.contentKey
                val w = history.firstOrNull { it.id == id }
                if (w == null) {
                    EmptyState(Icons.Default.FitnessCenter, "Elige un entrenamiento", "Verás todas las series, récords y estadísticas.")
                } else {
                    WorkoutDetail(
                        w = w,
                        vm = vm,
                        onBack = if (navigator.canNavigateBack()) ({ navigator.navigateBack() }) else null,
                        onWorkoutStarted = onWorkoutStarted,
                    )
                }
            }
        },
    )
}

@Composable
private fun WorkoutCard(w: Workout, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (w.source == WorkoutSource.WATCH) Icon(Icons.Default.Watch, "Registrado en el reloj", Modifier.padding(start = 4.dp))
            }
            Text(formatDate(w.startedAtEpochMs), style = MaterialTheme.typography.bodySmall)
            Text(
                "${formatDuration(w.durationSeconds())} · ${formatVolume(w.totalVolume)} kg · ${w.completedSetCount} series",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            w.exercises.take(4).forEach { we ->
                Text("${we.sets.size} × ${we.exercise.name}", style = MaterialTheme.typography.bodySmall)
            }
            if (w.exercises.size > 4) Text("y ${w.exercises.size - 4} más…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDetail(w: Workout, vm: HistoryViewModel, onBack: (() -> Unit)?, onWorkoutStarted: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val records = remember(w.id) { vm.recordsIn(w) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(w.name) },
            navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } } },
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Repetir entrenamiento") }, onClick = { menu = false; vm.repeat(w, onWorkoutStarted) })
                        DropdownMenuItem(text = { Text("Guardar como rutina") }, onClick = { menu = false; vm.saveAsRoutine(w) })
                        DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; vm.delete(w.id); onBack?.invoke() })
                    }
                }
            },
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(formatDate(w.startedAtEpochMs), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Duración", formatDuration(w.durationSeconds()), Modifier.weight(1f))
                    StatTile("Volumen", "${formatVolume(w.totalVolume)} kg", Modifier.weight(1f))
                    StatTile("Series", "${w.completedSetCount}", Modifier.weight(1f))
                }
            }
            if (w.avgHeartRate != null || w.calories != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        w.avgHeartRate?.let { StatTile("FC media", "$it ppm", Modifier.weight(1f)) }
                        w.maxHeartRate?.let { StatTile("FC máx.", "$it ppm", Modifier.weight(1f)) }
                        w.calories?.let { StatTile("Calorías", "$it kcal", Modifier.weight(1f)) }
                    }
                }
            }
            if (records.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("🏆 ${records.size} récords", fontWeight = FontWeight.Bold)
                            records.forEach { Text("${it.exerciseName}: ${it.type.label} ${formatWeight(it.value)}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            if (w.notes.isNotBlank()) item { Text(w.notes) }
            items(w.exercises, key = { it.id }) { we ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(we.exercise.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        we.sets.forEachIndexed { i, set ->
                            Row {
                                Text(if (set.type.short.isEmpty()) "${i + 1}" else set.type.short, Modifier.padding(end = 12.dp), fontWeight = FontWeight.Bold)
                                Text(describeSet(set, we.exercise.tracking), Modifier.weight(1f))
                                if (set.estimatedOneRepMax > 0) Text("1RM ${formatWeight(set.estimatedOneRepMax)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
