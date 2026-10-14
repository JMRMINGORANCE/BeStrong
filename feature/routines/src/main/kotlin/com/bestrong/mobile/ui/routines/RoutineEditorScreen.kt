package com.bestrong.mobile.ui.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.training.Tempo
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.displayName

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorScreen(vm: RoutineEditorViewModel, onAddExercises: () -> Unit, onClose: () -> Unit) {
    val unit = LocalWeightUnit.current
    val routine by vm.routine.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var restFor by remember { mutableStateOf<String?>(null) }
    // Atrás con cambios sin guardar: se pregunta antes de perderlos
    val leave = { if (vm.hasChanges) confirmLeave = true else onClose() }
    BackHandler(onBack = leave)
    EditorDialogs(
        vm = vm,
        routineName = routine.name,
        restFor = restFor,
        currentRest = routine.exercises.firstOrNull { it.id == restFor }?.restSeconds,
        confirmLeave = confirmLeave,
        confirmDelete = confirmDelete,
        onClose = onClose,
        onDismissLeave = { confirmLeave = false },
        onDismissDelete = { confirmDelete = false },
        onDismissRest = { restFor = null },
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isNew) R.string.routine_new else R.string.routine_edit)) },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_cancel)) } },
                actions = {
                    if (!vm.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.routine_delete)) }
                    TextButton(onClick = { vm.save(onClose) }) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // En el Fold desplegado, el editor no se estira de lado a lado
            val content = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)
            item {
                Column(content, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(routine.name, vm::rename, label = { Text(stringResource(R.string.routine_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(routine.notes, vm::setNotes, label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.fillMaxWidth())
                    EmojiPicker(routine.emoji, vm::setEmoji)
                    FolderField(routine.folder.orEmpty(), vm.folders, vm::setFolder)
                    RoutineRangeRow(vm, routine)
                }
            }
            itemsIndexed(routine.exercises, key = { _, re -> re.id }) { index, re ->
                Card(content) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(re.exercise.displayName(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    stringResource(R.string.rest_value, formatDuration(re.restSeconds.toLong())),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.clickable { restFor = re.id },
                                )
                            }
                            IconButton(onClick = { vm.moveExercise(index, index - 1) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.action_move_up)) }
                            IconButton(onClick = { vm.moveExercise(index, index + 1) }, enabled = index < routine.exercises.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.action_move_down)) }
                            IconButton(onClick = { vm.duplicateExercise(re.id) }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.duplicate_named, re.exercise.displayName())) }
                            IconButton(onClick = { vm.removeExercise(re.id) }) { Icon(Icons.Default.Close, stringResource(R.string.remove_named, re.exercise.displayName())) }
                        }
                        if (index < routine.exercises.lastIndex) {
                            val linked = re.supersetId != null && re.supersetId == routine.exercises[index + 1].supersetId
                            FilterChip(
                                selected = linked,
                                onClick = { vm.toggleSupersetWithNext(re.id) },
                                label = { Text(stringResource(if (linked) R.string.in_superset_with_next else R.string.superset_with_next)) },
                            )
                        }
                        ExerciseExtras(
                            notes = re.notes,
                            tempo = re.tempo.orEmpty(),
                            showTempo = re.exercise.tracking != TrackingType.CARDIO,
                            onNotes = { vm.setExerciseNotes(re.id, it) },
                            onTempo = { vm.setTempo(re.id, it) },
                        )
                        TargetRpeChips(re.exercise.tracking, re.targetRpe) { vm.setTargetRpe(re.id, it) }
                        re.sets.forEachIndexed { i, set ->
                            key(re.id, i, re.sets.size) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Tocar el número cambia el tipo de serie (C calentamiento, D drop, F al fallo)
                                    Text(
                                        set.type.short.ifEmpty { "${i + 1}" },
                                        Modifier.widthIn(min = 24.dp).clickable { vm.cycleSetType(re.id, i) }.padding(vertical = 8.dp),
                                        fontWeight = FontWeight.Bold,
                                        color = if (set.type == SetType.NORMAL) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.tertiary,
                                    )
                                    if (re.exercise.tracking == TrackingType.CARDIO) {
                                        SmallNumberField(stringResource(R.string.set_minutes), (set.durationSec / 60).toString(), Modifier.weight(1f)) { v ->
                                            vm.updateSet(re.id, i) { it.copy(durationSec = (v.toIntOrNull() ?: 0) * 60) }
                                        }
                                    } else if (re.exercise.tracking == TrackingType.DURATION) {
                                        SmallNumberField(stringResource(R.string.seconds_short), set.durationSec.toString(), Modifier.weight(1f)) { v -> vm.updateSet(re.id, i) { it.copy(durationSec = v.toIntOrNull() ?: 0) } }
                                    } else {
                                        SmallNumberField(unit.label, unit.format(set.weightKg), Modifier.weight(1f), decimal = true) { v ->
                                            vm.updateSet(re.id, i) { it.copy(weightKg = unit.parseToKg(v) ?: 0.0) }
                                        }
                                        SmallNumberField(stringResource(R.string.reps_short), set.reps.toString(), Modifier.weight(1f)) { v -> vm.updateSet(re.id, i) { it.copy(reps = v.toIntOrNull() ?: 0) } }
                                    }
                                    IconButton(onClick = { vm.removeSet(re.id, i) }) { Icon(Icons.Default.Close, stringResource(R.string.remove_set)) }
                                }
                            }
                        }
                        OutlinedButton(onClick = { vm.addSet(re.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_set)) }
                    }
                }
            }
            item { RoutineMusclesCard(routine, content) }
            item {
                Button(onClick = onAddExercises, modifier = content) {
                    Icon(Icons.Default.Add, null)
                    Text(stringResource(R.string.add_exercises), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SmallNumberField(label: String, value: String, modifier: Modifier, decimal: Boolean = false, onChange: (String) -> Unit) {
    // Texto local: así "82," no se convierte en "82" mientras se escribe
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)
            onChange(text)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

/** Diálogos del editor: salir con cambios, borrar la rutina y elegir el descanso. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorDialogs(
    vm: RoutineEditorViewModel,
    routineName: String,
    restFor: String?,
    currentRest: Int?,
    confirmLeave: Boolean,
    confirmDelete: Boolean,
    onClose: () -> Unit,
    onDismissLeave: () -> Unit,
    onDismissDelete: () -> Unit,
    onDismissRest: () -> Unit,
) {
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { onDismissLeave() },
            title = { Text(stringResource(R.string.routine_discard_q)) },
            text = { Text(stringResource(R.string.routine_discard_body)) },
            confirmButton = { Button(onClick = { onDismissLeave(); vm.save(onClose) }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { onDismissLeave(); onClose() }) { Text(stringResource(R.string.action_discard)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { onDismissDelete() },
            title = { Text(stringResource(R.string.routine_delete_q, routineName)) },
            text = { Text(stringResource(R.string.routine_delete_body)) },
            confirmButton = { Button(onClick = { onDismissDelete(); vm.delete(onClose) }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { onDismissDelete() }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    restFor?.let { id ->
        val current = currentRest
        AlertDialog(
            onDismissRequest = { onDismissRest() },
            title = { Text(stringResource(R.string.rest_time)) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    REST_OPTIONS.forEach { s ->
                        FilterChip(selected = s == current, onClick = { vm.setRest(id, s); onDismissRest() }, label = { Text(formatDuration(s.toLong())) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onDismissRest() }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

/** Carpeta de la rutina en Inicio: se escribe o se elige una existente. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderField(folder: String, existing: List<String>, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(folder) }
    // La rutina se carga después de pintar (arranque en frío): se recoge la carpeta cuando llega
    LaunchedEffect(folder) { if (text.trim() != folder) text = folder }
    OutlinedTextField(
        text,
        { text = it.take(40); onChange(text) },
        label = { Text(stringResource(R.string.routine_folder)) },
        placeholder = { Text(stringResource(R.string.routine_folder_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (existing.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            existing.forEach { f -> FilterChip(selected = f == text.trim(), onClick = { text = f; onChange(f) }, label = { Text(f) }) }
        }
    }
}

private val REST_OPTIONS = listOf(0, 30, 45, 60, 90, 120, 150, 180, 240, 300)

/** Notas y tempo del ejercicio dentro de la rutina (se copian al empezar el entreno). */
@Composable
private fun ExerciseExtras(notes: String, tempo: String, showTempo: Boolean, onNotes: (String) -> Unit, onTempo: (String) -> Unit) {
    var tempoText by remember { mutableStateOf(tempo) }
    val tempoValid = tempoText.isBlank() || Tempo.parse(tempoText) != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        OutlinedTextField(notes, onNotes, label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.weight(2f))
        if (showTempo) {
            OutlinedTextField(
                tempoText,
                { t ->
                    tempoText = t.take(9)
                    if (tempoText.isBlank() || Tempo.parse(tempoText) != null) onTempo(tempoText)
                },
                label = { Text(stringResource(R.string.tempo)) },
                placeholder = { Text("3-1-1-0") },
                isError = !tempoValid,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val ROUTINE_EMOJIS = listOf("💪", "🦵", "🏋️", "🔥", "🏃", "🧘", "⚡", "🎯", "🍑", "🤸", "🥊", "🚴", "🏊", "❤️", "🧠", "⭐")

/** Emoji opcional para reconocer la rutina en Inicio y en el reloj. */
@Composable
private fun EmojiPicker(selected: String?, onPick: (String?) -> Unit) {
    Text(stringResource(R.string.routine_emoji), style = MaterialTheme.typography.labelLarge)
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            FilterChip(selected = selected == null, onClick = { onPick(null) }, label = { Text(stringResource(R.string.emoji_none)) })
        }
        items(ROUTINE_EMOJIS.size) { i ->
            val e = ROUTINE_EMOJIS[i]
            FilterChip(selected = selected == e, onClick = { onPick(e) }, label = { Text(e) })
        }
    }
}

/** Mismo rango de repeticiones para toda la rutina (p. ej. un día de hipertrofia); solo si tiene ejercicios con peso. */
@Composable
private fun RoutineRangeRow(vm: RoutineEditorViewModel, routine: com.bestrong.core.model.Routine) {
    if (routine.exercises.none { it.exercise.tracking == TrackingType.WEIGHT_REPS }) return
    var common by remember(routine.exercises.map { it.exercise.id }) { mutableStateOf(vm.commonRange()) }
    Text(stringResource(R.string.rep_range_all), style = MaterialTheme.typography.labelLarge)
    // El rango es de cada ejercicio: también cambia en las demás rutinas que lo usan
    Text(stringResource(R.string.rep_range_all_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    com.bestrong.mobile.ui.components.RepRangeChips(common) { vm.setRangeForAll(it); common = it }
}

/** RPE objetivo de las series efectivas («—» = sin objetivo); solo en los ejercicios de peso × reps. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TargetRpeChips(tracking: TrackingType, current: Double?, onPick: (Double?) -> Unit) {
    if (tracking != TrackingType.WEIGHT_REPS) return
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.target_rpe), style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
        FilterChip(selected = current == null, onClick = { onPick(null) }, label = { Text("—") })
        for (r in TARGET_RPES) FilterChip(selected = current == r, onClick = { onPick(r) }, label = { Text(r.toString().removeSuffix(".0")) })
    }
}

private val TARGET_RPES = listOf(6.0, 7.0, 8.0, 8.5, 9.0, 10.0)
