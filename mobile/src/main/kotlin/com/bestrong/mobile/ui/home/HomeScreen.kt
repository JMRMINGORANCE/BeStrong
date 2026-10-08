package com.bestrong.mobile.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.Routine
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.rememberNow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenWorkout: () -> Unit,
    onEditRoutine: (String?) -> Unit,
) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("BeStrong", fontWeight = FontWeight.Black) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Rejilla adaptable: 1 columna en el Fold plegado, 2-3 desplegado
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            active?.let { w ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val now = rememberNow()
                    Card(
                        onClick = onOpenWorkout,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Entrenamiento en curso", style = MaterialTheme.typography.labelLarge)
                                Text(w.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("${formatDuration(w.durationSeconds(now))} · ${w.completedSetCount}/${w.totalSetCount} series")
                            }
                            Icon(Icons.Default.PlayArrow, "Continuar")
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Empezar") }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.startEmpty(onOpenWorkout) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null)
                        Text("Entrenamiento vacío", Modifier.padding(start = 8.dp))
                    }
                    FilledTonalButton(onClick = { vm.startOnWatch(null) }) {
                        Icon(Icons.Default.Watch, null)
                        Text("En el reloj", Modifier.padding(start = 8.dp))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Mis rutinas (${routines.size})", Modifier.weight(1f))
                    OutlinedButton(onClick = { onEditRoutine(null) }) { Text("+ Nueva rutina") }
                }
            }
            if (routines.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Crea tu primera rutina (p. ej. Push / Pull / Pierna) o guarda un entrenamiento como rutina al terminarlo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(routines, key = { it.id }) { routine ->
                val last = history.firstOrNull { it.routineId == routine.id }
                RoutineCard(
                    routine = routine,
                    lastDone = last?.let { formatDate(it.startedAtEpochMs) },
                    onStart = { vm.startRoutine(routine, onOpenWorkout) },
                    onStartWatch = { vm.startOnWatch(routine) },
                    onEdit = { onEditRoutine(routine.id) },
                    onDuplicate = { vm.duplicate(routine) },
                    onDelete = { vm.delete(routine) },
                    onMoveUp = { vm.move(routine, -1) },
                    onMoveDown = { vm.move(routine, 1) },
                )
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    lastDone: String?,
    onStart: () -> Unit,
    onStartWatch: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones de la rutina") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Empezar en el reloj") }, onClick = { menu = false; onStartWatch() })
                        DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Subir") }, onClick = { menu = false; onMoveUp() })
                        DropdownMenuItem(text = { Text("Bajar") }, onClick = { menu = false; onMoveDown() })
                        DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Text(
                routine.exercises.joinToString { it.exercise.name }.ifBlank { "Sin ejercicios" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            lastDone?.let { Text("Última vez: $it", style = MaterialTheme.typography.labelSmall) }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), enabled = routine.exercises.isNotEmpty()) {
                Text("Empezar rutina")
            }
        }
    }
}
