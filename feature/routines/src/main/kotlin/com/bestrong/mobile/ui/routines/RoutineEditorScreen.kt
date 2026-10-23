package com.bestrong.mobile.ui.routines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    val routine by vm.routine.collectAsStateWithLifecycle()
    val tempoVersion by vm.tempoVersion.collectAsStateWithLifecycle()
    // Leído aquí para que el título y el botón de borrar cambien si el borrador pasa a ser rutina nueva
    val isNew by vm.isNewState.collectAsStateWithLifecycle()
    CloseIfMissing(vm, onClose)
    var confirmLeave by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var confirmDelete by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var restFor by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    // Atrás con cambios sin guardar: se pregunta antes de perderlos
    val leave = { if (vm.hasChanges) confirmLeave = true else onClose() }
    BackHandler(onBack = leave)
    EditorDialogs(
        vm = vm,
        routineName = routine.name,
        restFor = restFor,
        currentRest = routine.exercises.firstOrNull { it.id == restFor }?.restSeconds,
        exerciseCount = routine.exercises.size,
        confirmLeave = confirmLeave,
        confirmDelete = confirmDelete,
        onClose = onClose,
        onDismissLeave = { confirmLeave = false },
        onDismissDelete = { confirmDelete = false },
        onDismissRest = { restFor = null },
    )
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(vm) {
        vm.removed.collect { message ->
            // Uno nuevo sustituye al anterior (solo se puede deshacer el último)
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(context.getString(message), context.getString(R.string.action_undo), duration = androidx.compose.material3.SnackbarDuration.Short)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.undoRemove()
        }
    }
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.routine_new else R.string.routine_edit)) },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_cancel)) } },
                actions = {
                    if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.routine_delete)) }
                    TextButton(onClick = { vm.save(onClose) }) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
        // Siempre a mano, aunque la rutina sea larga
        bottomBar = {
            Button(
                onClick = onAddExercises,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp),
            ) {
                Icon(Icons.Default.Add, null)
                Text(stringResource(R.string.add_exercises), Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
        },
    ) { padding ->
        var moreOptions by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        // El teclado también cuenta como borde (con barra inferior, el Scaffold no lo suma): así las series de
        // abajo pueden subir por encima de él
        LazyColumn(
            Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // En el Fold desplegado, el editor no se estira de lado a lado
            val content = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)
            item {
                Column(content, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        routine.name,
                        vm::rename,
                        label = { Text(stringResource(R.string.routine_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        routine.notes,
                        vm::setNotes,
                        label = { Text(stringResource(R.string.notes)) },
                        keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Lo que se toca poco (emoji, carpeta, gimnasio y los ajustes para toda la rutina), plegado
                    val moreState = stringResource(if (moreOptions) R.string.folder_expanded else R.string.folder_collapsed)
                    com.bestrong.mobile.ui.components.Panel(
                        onClick = { moreOptions = !moreOptions },
                        contentPadding = 14.dp,
                        modifier = Modifier.semantics { stateDescription = moreState },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.routine_more_options), Modifier.weight(1f).padding(start = 12.dp), fontWeight = FontWeight.Bold)
                            Icon(if (moreOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                        }
                    }
                    if (moreOptions) {
                        EmojiPicker(routine.emoji, vm::setEmoji)
                        FolderField(routine.folder.orEmpty(), vm.folders, vm::setFolder)
                        RoutineGymRow(vm, routine)
                        RoutineRangeRow(vm, routine)
                        RoutineRpeRow(vm, routine)
                        RoutineTempoRow(vm, routine)
                    }
                    if (routine.exercises.isNotEmpty()) {
                        com.bestrong.mobile.ui.components.SectionHeader(
                            androidx.compose.ui.res.pluralStringResource(R.plurals.exercise_count, routine.exercises.size, routine.exercises.size),
                            large = true,
                        )
                    }
                }
            }
            if (routine.exercises.isEmpty()) {
                item {
                    com.bestrong.mobile.ui.components.EmptyState(
                        Icons.Default.FitnessCenter,
                        stringResource(R.string.routine_empty_title),
                        stringResource(R.string.routine_empty_body),
                        Modifier.heightIn(min = 360.dp),
                    )
                }
            }
            itemsIndexed(routine.exercises, key = { _, re -> re.id }) { index, re ->
                RoutineExerciseCard(vm, routine, index, re, content, tempoVersion) { restFor = it }
            }
            if (routine.exercises.isNotEmpty()) item { RoutineMusclesCard(routine, content) }
        }
    }
}

@Composable
private fun SmallNumberField(label: String, value: String, modifier: Modifier, decimal: Boolean = false, onChange: (String) -> Unit) {
    // Texto local: así "82," no se convierte en "82" mientras se escribe
    // Un 0 se ve como pista, no como texto: así se escribe «8» y no «08» sin tener que borrar antes
    var text by remember { mutableStateOf(value.takeUnless { it == "0" }.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            val f = new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)
            // Un solo separador: «82,,5» no se lee y se guardaban 0 kg
            text = if (f.count { it == '.' || it == ',' } > 1) text else f
            onChange(text)
        },
        label = { Text(label) },
        placeholder = { Text("0") },
        singleLine = true,
        // «Siguiente» del teclado pasa de kg a reps (y a la serie de abajo) sin tocar la pantalla
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
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
    exerciseCount: Int,
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
                var toAll by remember(id) { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        REST_OPTIONS.forEach { s ->
                            FilterChip(
                                selected = s == current,
                                onClick = { if (toAll) vm.setRestForAll(s) else vm.setRest(id, s); onDismissRest() },
                                label = { Text(formatDuration(s.toLong())) },
                            )
                        }
                    }
                    // Con varios ejercicios, poner el mismo a todos de una vez
                    if (exerciseCount > 1) {
                        FilterChip(selected = toAll, onClick = { toAll = !toAll }, label = { Text(stringResource(R.string.routine_rest_apply_all)) })
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
private fun ExerciseExtras(notes: String, tempo: String, showTempo: Boolean, onNotes: (String) -> Unit, onTempo: (String) -> Unit, resetKey: Int = 0) {
    // Se vuelve a leer al poner el tempo a toda la rutina ([resetKey]); si no, se respeta lo que se escribe
    var tempoText by remember(resetKey) { mutableStateOf(tempo) }
    val tempoValid = tempoText.isBlank() || Tempo.parse(tempoText) != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            notes,
            onNotes,
            label = { Text(stringResource(R.string.notes)) },
            keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(2f),
        )
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

/** Gimnasio de la rutina (solo con más de un perfil): al empezarla pasa a ser el activo. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RoutineGymRow(vm: RoutineEditorViewModel, routine: com.bestrong.core.model.Routine) {
    val gyms = vm.gyms
    if (gyms.size < 2) return
    // Un gimnasio que ya no existe (borrado, restaurado con otros ids) cuenta como ninguno
    val gymId = routine.gymId?.takeIf { id -> gyms.any { it.id == id } }
    Text(stringResource(R.string.routine_gym), style = MaterialTheme.typography.labelLarge)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = gymId == null, onClick = { vm.setGym(null) }, label = { Text(stringResource(R.string.routine_gym_any)) })
        gyms.forEach { g -> FilterChip(selected = gymId == g.id, onClick = { vm.setGym(g.id) }, label = { Text(g.name) }) }
    }
}

/** Mismo RPE objetivo para todos los ejercicios de peso × reps (p. ej. una semana de descarga a RPE 7). */
@Composable
private fun RoutineRpeRow(vm: RoutineEditorViewModel, routine: com.bestrong.core.model.Routine) {
    val weighted = routine.exercises.filter { it.exercise.tracking == TrackingType.WEIGHT_REPS }
    if (weighted.size < 2) return
    // Con objetivos distintos no se marca ningún chip; todos sin objetivo marca «—»
    val distinct = weighted.map { it.targetRpe }.distinct()
    val common = if (distinct.size == 1) distinct.single() else MIXED_RPE
    Text(stringResource(R.string.target_rpe_all), style = MaterialTheme.typography.labelLarge)
    com.bestrong.mobile.ui.components.TargetRpeChips(TrackingType.WEIGHT_REPS, common) { vm.setTargetRpeForAll(it) }
}

/** Tempos habituales: controlado, bajada en 3 s, con pausa abajo (hipertrofia) y excéntrico lento. */
private val ROUTINE_TEMPOS = listOf("2-0-1-0", "3-0-1-0", "3-1-1-0", "4-0-1-0")

/** Mismo tempo para toda la rutina (p. ej. un día de hipertrofia a 3-1-1-0); «—» lo quita. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RoutineTempoRow(vm: RoutineEditorViewModel, routine: com.bestrong.core.model.Routine) {
    val eligible = routine.exercises.filter { it.exercise.tracking != TrackingType.CARDIO }
    if (eligible.size < 2) return
    // Comparando normalizados («3/1/1/0» es el chip 3-1-1-0)
    val distinct = eligible.map { re -> re.tempo?.let { Tempo.parse(it)?.toString() } }.distinct()
    val common = distinct.singleOrNull().takeIf { distinct.size == 1 }
    Text(stringResource(R.string.tempo_all), style = MaterialTheme.typography.labelLarge)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = distinct.size == 1 && common == null, onClick = { vm.setTempoForAll(null) }, label = { Text("—") })
        for (t in ROUTINE_TEMPOS) {
            val normalized = Tempo.parse(t)?.toString()
            FilterChip(selected = common != null && common == normalized, onClick = { vm.setTempoForAll(t) }, label = { Text(t) })
        }
    }
}

/** RPE imposible: objetivos distintos en la rutina, no coincide con ningún chip (ni con «—»). */
private const val MIXED_RPE = -1.0

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

/** La rutina ya no existe (borrada desde otro sitio, enlace antiguo): se cierra en vez de editar una vacía. */
@Composable
private fun CloseIfMissing(vm: RoutineEditorViewModel, onClose: () -> Unit) {
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onClose() }
    // Borrador recuperado de una rutina que se borró: se avisa de que al guardar será una nueva
    val recovered by vm.recoveredAsNew.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val text = stringResource(R.string.routine_recovered_as_new)
    LaunchedEffect(recovered) {
        if (recovered && !vm.recoveredNoticeShown) {
            vm.recoveredNoticeShown = true
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

/** Un ejercicio de la rutina: cabecera con sus acciones, superserie, extras plegables y sus series. */
@Composable
private fun RoutineExerciseCard(
    vm: RoutineEditorViewModel,
    routine: com.bestrong.core.model.Routine,
    index: Int,
    re: com.bestrong.core.model.RoutineExercise,
    content: Modifier,
    tempoVersion: Int,
    onEditRest: (String) -> Unit,
) {
    var extras by androidx.compose.runtime.saveable.rememberSaveable(re.id, tempoVersion) { mutableStateOf(re.notes.isNotBlank() || !re.tempo.isNullOrBlank() || re.targetRpe != null) }
    com.bestrong.mobile.ui.components.Panel(content, contentPadding = 0.dp) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.bestrong.mobile.ui.components.ExerciseThumb(re.exercise, 48.dp, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(re.exercise.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        stringResource(R.string.rest_value, formatDuration(re.restSeconds.toLong())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.minimumInteractiveComponentSize().clickable(onClickLabel = stringResource(R.string.rest)) { onEditRest(re.id) },
                    )
                }
                // Subir, bajar y duplicar en un menú: con cuatro botones al lado, el nombre se partía palabra a palabra
                var reMenu by remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { reMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.exercise_options, re.exercise.displayName())) }
                    androidx.compose.material3.DropdownMenu(expanded = reMenu, onDismissRequest = { reMenu = false }) {
                        if (index > 0) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.action_move_up)) }, onClick = { reMenu = false; vm.moveExercise(index, index - 1) })
                        }
                        if (index < routine.exercises.lastIndex) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.action_move_down)) }, onClick = { reMenu = false; vm.moveExercise(index, index + 1) })
                        }
                        androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { reMenu = false; vm.duplicateExercise(re.id) })
                    }
                }
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
            // Notas, tempo y RPE: plegados salvo que ya tengan algo
            TextButton(onClick = { extras = !extras }) {
                Text(stringResource(R.string.routine_exercise_extras))
                Icon(if (extras) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
            }
            if (extras) {
                ExerciseExtras(
                    notes = re.notes,
                    tempo = re.tempo.orEmpty(),
                    showTempo = re.exercise.tracking != TrackingType.CARDIO,
                    onNotes = { vm.setExerciseNotes(re.id, it) },
                    onTempo = { vm.setTempo(re.id, it) },
                    resetKey = tempoVersion,
                )
                com.bestrong.mobile.ui.components.TargetRpeChips(re.exercise.tracking, re.targetRpe) { vm.setTargetRpe(re.id, it) }
            }
            re.sets.forEachIndexed { i, set ->
                key(re.id, i, re.sets.size) { RoutineSetRow(vm, re, i, set) }
            }
            OutlinedButton(onClick = { vm.addSet(re.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_set)) }
        }
    }
}

/** Una serie de la plantilla: tipo (tocando el número), peso y reps, minutos o segundos según el ejercicio. */
@Composable
private fun RoutineSetRow(vm: RoutineEditorViewModel, re: com.bestrong.core.model.RoutineExercise, i: Int, set: com.bestrong.core.model.RoutineSet) {
    val unit = LocalWeightUnit.current
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Tocar el número cambia el tipo de serie (C calentamiento, D drop, F al fallo)
        Text(
            set.type.short.ifEmpty { "${i + 1}" },
            Modifier.minimumInteractiveComponentSize().clickable(onClickLabel = stringResource(R.string.set_type_change), role = Role.Button) { vm.cycleSetType(re.id, i) }
                .wrapContentSize(),
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
            val factor = vm.weightFactor(re.exercise)
            SmallNumberField(unit.label, unit.format(set.weightKg * factor), Modifier.weight(1f), decimal = true) { v ->
                // Vacío = 0 kg; un texto a medias que no se lee no borra el peso que había
                val kg = if (v.isEmpty()) 0.0 else unit.parseToKg(v)
                if (kg != null) vm.updateSet(re.id, i) { it.copy(weightKg = kg / factor) }
            }
            // De distancia, solo el peso (las reps no se usan; los metros salen de la última vez)
            if (re.exercise.tracking != TrackingType.DISTANCE) {
                SmallNumberField(stringResource(R.string.reps_short), set.reps.toString(), Modifier.weight(1f)) { v -> vm.updateSet(re.id, i) { it.copy(reps = v.toIntOrNull() ?: 0) } }
            }
        }
        IconButton(onClick = { vm.removeSet(re.id, i) }) { Icon(Icons.Default.Close, stringResource(R.string.remove_set), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
