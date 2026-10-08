package com.bestrong.mobile.ui.workout

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatVolume
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.ui.FoldPosture
import com.bestrong.mobile.ui.components.RestState
import com.bestrong.mobile.ui.components.RestTimerBar
import com.bestrong.mobile.ui.components.StatTile
import com.bestrong.mobile.ui.components.rememberNow
import com.bestrong.mobile.ui.rememberFoldPosture

/**
 * Entrenamiento en curso.
 * - Fold plegado: una columna, temporizador de descanso abajo.
 * - Fold desplegado: panel lateral con resumen y descanso grande + lista de ejercicios.
 * - Modo mesa: resumen y descanso en la mitad superior, series en la inferior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    vm: ActiveWorkoutViewModel,
    onAddExercises: () -> Unit,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val workout by vm.workout.collectAsStateWithLifecycle()
    val previous by vm.previousSets.collectAsStateWithLifecycle()
    val rest by vm.rest.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    KeepScreenOn(settings.keepScreenOn)

    summary?.let { s ->
        WorkoutSummaryDialog(
            summary = s,
            onSaveRoutine = { vm.saveSummaryAsRoutine(); vm.dismissSummary(); onFinished() },
            onDone = { vm.dismissSummary(); onFinished() },
        )
        return
    }
    val w = workout ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay ningún entrenamiento en curso") }
        return
    }

    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val posture = rememberFoldPosture()
    val expanded = widthClass != WindowWidthSizeClass.COMPACT && posture != FoldPosture.TABLETOP

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Minimizar") } },
                title = {
                    Column(Modifier.padding(end = 8.dp)) {
                        TextButton(onClick = { renaming = true }, contentPadding = PaddingValues(0.dp)) {
                            Text(w.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        }
                    }
                },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    Button(onClick = { confirmFinish = true }) { Text("Terminar") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Descartar entrenamiento") }, onClick = { menu = false; confirmDiscard = true })
                    }
                },
            )
        },
        bottomBar = {
            if (!expanded && posture != FoldPosture.TABLETOP) rest?.let { RestTimerBar(it, vm::adjustRest, vm::skipRest) }
        },
    ) { padding ->
        val content = Modifier.padding(padding).fillMaxSize()
        when {
            posture == FoldPosture.TABLETOP -> Column(content) {
                // Mitad superior: lo que se mira desde el banco
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    rest?.let { RestTimerBar(it, vm::adjustRest, vm::skipRest, large = true) }
                        ?: SummaryPanel(w, previous, Modifier.padding(16.dp))
                }
                HorizontalDivider()
                ExerciseList(w, previous, vm, onAddExercises, Modifier.weight(1f))
            }
            expanded -> Row(content) {
                Column(
                    Modifier.weight(0.38f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    rest?.let { RestTimerBar(it, vm::adjustRest, vm::skipRest, large = true) }
                    SummaryPanel(w, previous)
                }
                ExerciseList(w, previous, vm, onAddExercises, Modifier.weight(0.62f))
            }
            else -> Column(content) {
                StatsRow(w, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                ExerciseList(w, previous, vm, onAddExercises, Modifier.weight(1f))
            }
        }
    }

    if (confirmFinish) {
        val pending = w.totalSetCount - w.completedSetCount
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("¿Terminar entrenamiento?") },
            text = {
                Text(
                    if (w.completedSetCount == 0) "No has completado ninguna serie: el entrenamiento se descartará."
                    else if (pending > 0) "Hay $pending series sin completar que no se guardarán."
                    else "Se guardará en tu historial.",
                )
            },
            confirmButton = { Button(onClick = { confirmFinish = false; vm.finish(); if (w.completedSetCount == 0) onFinished() }) { Text("Terminar") } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Seguir") } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("¿Descartar entrenamiento?") },
            text = { Text("Se perderán todas las series registradas.") },
            confirmButton = { Button(onClick = { confirmDiscard = false; vm.discard(); onFinished() }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancelar") } },
        )
    }
    if (renaming) {
        var name by remember { mutableStateOf(w.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Nombre del entrenamiento") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { Button(onClick = { vm.rename(name); renaming = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(enabled) {
        if (enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
private fun StatsRow(w: Workout, modifier: Modifier = Modifier) {
    val now = rememberNow()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Duración", formatDuration(w.durationSeconds(now)), Modifier.weight(1f))
        StatTile("Volumen", "${formatVolume(w.totalVolume)} kg", Modifier.weight(1f))
        StatTile("Series", "${w.completedSetCount}/${w.totalSetCount}", Modifier.weight(1f))
    }
}

@Composable
private fun SummaryPanel(w: Workout, previous: (String) -> List<WorkoutSet>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatsRow(w)
        val muscles = WorkoutStats.setsPerMuscle(listOf(w.copy(finishedAtEpochMs = 1)), 0)
        if (muscles.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Músculos trabajados", fontWeight = FontWeight.SemiBold)
                    muscles.entries.take(6).forEach { (m, sets) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(m.label, Modifier.weight(1f))
                            Text("${formatWeight(sets)} series")
                        }
                    }
                }
            }
        }
        // Siguiente serie pendiente, visible de un vistazo
        w.exercises.firstOrNull { we -> we.sets.any { !it.completed } }?.let { we ->
            val set = we.sets.first { !it.completed }
            val prev = previous(we.exercise.id).getOrNull(we.sets.indexOf(set))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Siguiente", style = MaterialTheme.typography.labelMedium)
                    Text(we.exercise.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(describeSet(set, we.exercise.tracking), style = MaterialTheme.typography.headlineMedium)
                    prev?.let { Text("Anterior: ${describeSet(it, we.exercise.tracking)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun ExerciseList(
    w: Workout,
    previous: (String) -> List<WorkoutSet>,
    vm: ActiveWorkoutViewModel,
    onAddExercises: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(w.exercises, key = { _, we -> we.id }) { index, we ->
            ExerciseCard(
                we = we,
                index = index,
                count = w.exercises.size,
                previous = previous(we.exercise.id),
                vm = vm,
            )
        }
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddExercises, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Text("Añadir ejercicios", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    we: WorkoutExercise,
    index: Int,
    count: Int,
    previous: List<WorkoutSet>,
    vm: ActiveWorkoutViewModel,
) {
    var menu by remember { mutableStateOf(false) }
    var editRest by remember { mutableStateOf(false) }
    var editNotes by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(we.exercise.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(
                    "${we.exercise.primaryMuscle.label} · ${we.exercise.equipment.label} · Descanso ${formatDuration(we.restSeconds.toLong())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones del ejercicio") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Tiempo de descanso") }, onClick = { menu = false; editRest = true })
                    DropdownMenuItem(text = { Text("Notas") }, onClick = { menu = false; editNotes = true })
                    if (index > 0) DropdownMenuItem(text = { Text("Subir") }, onClick = { menu = false; vm.moveExercise(index, index - 1) })
                    if (index < count - 1) DropdownMenuItem(text = { Text("Bajar") }, onClick = { menu = false; vm.moveExercise(index, index + 1) })
                    DropdownMenuItem(text = { Text("Quitar ejercicio") }, onClick = { menu = false; vm.removeExercise(we.id) })
                }
            }
        }
        if (we.notes.isNotBlank()) {
            Text(we.notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Spacer(Modifier.height(4.dp))
        SetHeader(we.exercise.tracking)
        var normalIndex = 0
        we.sets.forEachIndexed { i, set ->
            val displayIndex = if (set.type == com.bestrong.core.model.SetType.NORMAL) normalIndex++ else -1
            androidx.compose.runtime.key(set.id) {
                SetRow(
                    index = displayIndex,
                    set = set,
                    previous = previous.getOrNull(i),
                    tracking = we.exercise.tracking,
                    onWeight = { vm.updateSet(we.id, set.id, weight = it) },
                    onReps = { vm.updateSet(we.id, set.id, reps = it) },
                    onDuration = { vm.updateSet(we.id, set.id, duration = it) },
                    onToggle = { vm.toggleSet(we.id, set.id) },
                    onCycleType = { vm.cycleSetType(we.id, set.id) },
                    onDelete = { vm.removeSet(we.id, set.id) },
                )
            }
        }
        OutlinedButton(
            onClick = { vm.addSet(we.id) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) { Text("+ Añadir serie") }
    }
    HorizontalDivider()

    if (editRest) {
        val options = listOf(0, 30, 45, 60, 90, 120, 150, 180, 240, 300)
        AlertDialog(
            onDismissRequest = { editRest = false },
            title = { Text("Descanso") },
            text = {
                Column {
                    options.forEach { s ->
                        ListItem(
                            headlineContent = { Text(if (s == 0) "Sin descanso" else formatDuration(s.toLong())) },
                            modifier = Modifier.clickableNoRipple { vm.setRestSeconds(we.id, s); editRest = false },
                            trailingContent = { if (s == we.restSeconds) Text("✓") },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editRest = false }) { Text("Cerrar") } },
        )
    }
    if (editNotes) {
        var notes by remember { mutableStateOf(we.notes) }
        AlertDialog(
            onDismissRequest = { editNotes = false },
            title = { Text("Notas") },
            text = { OutlinedTextField(notes, { notes = it }, minLines = 3) },
            confirmButton = { Button(onClick = { vm.setExerciseNotes(we.id, notes); editNotes = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editNotes = false }) { Text("Cancelar") } },
        )
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
fun WorkoutSummaryDialog(summary: FinishedSummary, onSaveRoutine: () -> Unit, onDone: () -> Unit) {
    val w = summary.workout
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Default.EmojiEvents, null) },
        title = { Text("¡Entrenamiento completado!") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Duración", formatDuration(w.durationSeconds()), Modifier.weight(1f))
                    StatTile("Volumen", "${formatVolume(w.totalVolume)} kg", Modifier.weight(1f))
                }
                Text("${w.completedSetCount} series · ${w.exercises.size} ejercicios")
                if (summary.records.isNotEmpty()) {
                    Text("🏆 Nuevos récords", fontWeight = FontWeight.Bold)
                    summary.records.forEach { pr ->
                        Text("${pr.exerciseName}: ${pr.type.label} ${formatWeight(pr.value)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text("Listo") } },
        dismissButton = if (w.routineId == null) ({ TextButton(onClick = onSaveRoutine) { Text("Guardar como rutina") } }) else null,
    )
}
