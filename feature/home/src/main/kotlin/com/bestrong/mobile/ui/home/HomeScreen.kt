package com.bestrong.mobile.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.library.TrainingProgram
import com.bestrong.core.model.Routine
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.PeriodizedProgram
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.share.RoutineShare
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.formatTime
import com.bestrong.mobile.ui.components.rememberNow
import com.bestrong.mobile.ui.components.uiLanguage
import com.bestrong.mobile.ui.help.TipCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenWorkout: () -> Unit,
    onEditRoutine: (String?) -> Unit,
    onOpenPlanning: () -> Unit = {},
    /** Abre la biblioteca filtrada por ese músculo. */
    onOpenMuscle: (com.bestrong.core.model.MuscleGroup) -> Unit = {},
    /** Abre la ficha del ejercicio (su progreso). */
    onOpenExercise: (String) -> Unit = {},
) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val gymNames by vm.gymNames.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val suggested by vm.suggestedRoutine.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    // Se guarda el id (no el programa) para que la preparación sobreviva al giro
    var setupProgramId by rememberSaveable { mutableStateOf<String?>(null) }
    setupProgramId?.let { id -> vm.periodizedPrograms.firstOrNull { it.id == id } }?.let { p ->
        ProgramSetupScreen(p, vm::suggestedTrainingMax, onConfirm = { tms -> vm.activateProgram(p, tms); setupProgramId = null }, onDismiss = { setupProgramId = null })
        return
    }
    val snackbar = remember { SnackbarHostState() }
    var showPrograms by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    UndoRoutineDelete(vm, snackbar)
    UndoDiscard(vm, snackbar, onOpenWorkout)
    // ¿Se quedó un entreno abierto sin querer? Ofrecer terminarlo a la hora de la última serie
    var forgotten by remember { mutableStateOf(vm.forgottenWorkout()) }
    forgotten?.let { fw ->
        AlertDialog(
            onDismissRequest = { forgotten = null },
            title = { Text(stringResource(R.string.forgotten_title)) },
            text = { Text(stringResource(R.string.forgotten_body, fw.name, formatTime(fw.updatedAtEpochMs))) },
            confirmButton = { Button(onClick = { vm.finishForgotten(); forgotten = null }) { Text(stringResource(R.string.forgotten_finish)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.discardActive(); forgotten = null }) { Text(stringResource(R.string.action_discard)) }
                    TextButton(onClick = { forgotten = null }) { Text(stringResource(R.string.forgotten_continue)) }
                }
            },
        )
    }
    if (showPrograms) {
        ProgramsDialog(
            periodized = vm.periodizedPrograms,
            programs = vm.programs,
            onPickPeriodized = { setupProgramId = it.id; showPrograms = false },
            onPick = { vm.addProgram(it); showPrograms = false },
            onDismiss = { showPrograms = false },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("BeStrong", fontWeight = FontWeight.Black) }, actions = { GymChip(vm) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Rejilla adaptable: 1 columna en el Fold plegado, 2-3 desplegado
        var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
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
                                Text(stringResource(R.string.home_in_progress), style = MaterialTheme.typography.labelLarge)
                                Text(w.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.home_in_progress_detail, formatDuration(w.durationSeconds(now)), w.completedSetCount, w.totalSetCount))
                            }
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.action_continue))
                        }
                    }
                }
            }
            if (active == null) {
                today?.let { s ->
                    item(span = { GridItemSpan(maxLineSpan) }) { TodayCard(s, onStart = { vm.startProgramSession(s, onOpenWorkout) }, onStopProgram = vm::stopProgram) }
                } ?: suggested?.let { r ->
                    item(span = { GridItemSpan(maxLineSpan) }) { SuggestedRoutineCard(r, onStart = { vm.startRoutine(r, onOpenWorkout) }) }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { WeekStrip(week, canChangeGoal = today == null, onGoalChange = vm::setWeeklyGoal) }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.home_start)) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.startEmpty(onOpenWorkout) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null)
                        Text(stringResource(R.string.home_empty_workout), Modifier.padding(start = 8.dp))
                    }
                    history.firstOrNull { it.isFinished }?.let { last ->
                        FilledTonalButton(onClick = { vm.repeatLast(onOpenWorkout) }) {
                            Icon(Icons.Default.Replay, null)
                            Text(stringResource(R.string.home_repeat_last, last.name), Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    FilledTonalButton(onClick = { vm.startOnWatch(null) }) {
                        Icon(Icons.Default.Watch, null)
                        Text(stringResource(R.string.home_on_watch), Modifier.padding(start = 8.dp))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                // Título y acciones en líneas separadas: con letra grande los botones bajan de línea
                Column {
                    SectionHeader(stringResource(R.string.home_my_routines, routines.count { !it.archived }))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onEditRoutine(null) }) { Text(stringResource(R.string.home_new_routine)) }
                        TextButton(onClick = { showPrograms = true }) { Text(stringResource(R.string.home_programs)) }
                        TextButton(onClick = onOpenPlanning) { Text(stringResource(R.string.planning_open)) }
                    }
                }
            }
            if (routines.none { !it.archived }) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.home_no_routines),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(onClick = { showPrograms = true }) { Text(stringResource(R.string.home_browse_programs)) }
                    }
                }
            }
            routineItems(routines, collapsed, onToggleFolder = { f -> collapsed = if (f in collapsed) collapsed - f else collapsed + f }) { routine ->
                val last = history.firstOrNull { it.routineId == routine.id }
                RoutineCard(
                    routine = routine,
                    lastDone = last?.let { formatDate(it.startedAtEpochMs) },
                    gymNames = gymNames,
                    onStart = { vm.startRoutine(routine, onOpenWorkout) },
                    onStartWatch = { vm.startOnWatch(routine) },
                    onEdit = { onEditRoutine(routine.id) },
                    onDuplicate = { vm.duplicate(routine) },
                    onDelete = { vm.delete(routine) },
                    onMoveUp = { vm.move(routine, -1) },
                    onMoveDown = { vm.move(routine, 1) },
                    onArchive = { vm.setArchived(routine, !routine.archived) },
                )
            }
            // Preparación del día y proteína/agua (al final para no empujar «Empezar»)
            item { MissedSessionCard(vm, onOpenWorkout) }
            item { YearAgoCard(vm, onOpenExercise) }
            item { CardioWeekCard(vm) }
            item { StaleRoutineCard(vm) }
            item { LaggingMusclesCard(vm, onOpenMuscle) }
            item { ReadinessCard(vm) }
            item { IntakeCard(vm) }
            item { TipCard("home_reorder", R.string.tip_home) }
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
    onArchive: () -> Unit = {},
    /** Id → nombre de los gimnasios: se enseña el de la rutina, si tiene (y sigue existiendo). */
    gymNames: Map<String, String> = emptyMap(),
) {
    val gymName = routine.gymId?.let(gymNames::get)
    var menu by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    if (sharing) ShareRoutineDialog(routine) { sharing = false }
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(routine.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.routine_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; onEdit() })
                        // El reloj no recibe las archivadas: empezarla allí abriría un entreno vacío
                        if (!routine.archived) DropdownMenuItem(text = { Text(stringResource(R.string.routine_start_on_watch)) }, onClick = { menu = false; onStartWatch() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (routine.archived) R.string.routine_unarchive else R.string.routine_archive)) },
                            onClick = { menu = false; onArchive() },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_routine_link)) }, onClick = { menu = false; sharing = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_routine_pdf)) }, onClick = { menu = false; RoutineShare.sharePdf(context, listOf(routine), routine.name) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_move_up)) }, onClick = { menu = false; onMoveUp() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_move_down)) }, onClick = { menu = false; onMoveDown() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Text(
                routine.exercises.map { it.exercise.displayName() }.joinToString().ifBlank { stringResource(R.string.routine_no_exercises) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Cuánto se tarda, más o menos (series × 40 s + descansos), para elegir según el tiempo que haya
            val minutes = remember(routine) { (WorkoutEditor.estimatedRemainingSeconds(routine.toWorkout(0)) + 59) / 60 }
            val info = listOfNotNull(
                stringResource(R.string.routine_estimated, minutes).takeIf { minutes > 0 },
                lastDone?.let { stringResource(R.string.routine_last_done, it) },
                gymName?.let { "🏋️ $it" },
            )
            if (info.isNotEmpty()) Text(info.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), enabled = routine.exercises.isNotEmpty()) {
                Text(stringResource(R.string.routine_start))
            }
        }
    }
}

/** Programas predefinidos: al elegir uno se crean todas sus rutinas. */
@Composable
private fun ProgramsDialog(
    periodized: List<PeriodizedProgram>,
    programs: List<TrainingProgram>,
    onPickPeriodized: (PeriodizedProgram) -> Unit,
    onPick: (TrainingProgram) -> Unit,
    onDismiss: () -> Unit,
) {
    val lang = uiLanguage()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.programs_title)) },
        text = {
            LazyColumn(Modifier.testTag("programs_list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(stringResource(R.string.programs_periodized), style = MaterialTheme.typography.titleSmall) }
                items(periodized, key = { "p-" + it.id }) { p ->
                    Card(onClick = { onPickPeriodized(p) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(p.name(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.program_meta_periodized, p.daysPerWeek),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(p.description(lang), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { Text(stringResource(R.string.programs_templates), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                items(programs, key = { it.id }) { p ->
                    Card(onClick = { onPick(p) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(p.name(lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.program_meta, p.daysPerWeek, p.routines.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(p.description(lang), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** QR y enlace de una rutina para pasarla a otro móvil con BeStrong. */
@Composable
private fun ShareRoutineDialog(routine: Routine, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val link = remember(routine) { RoutineShare.link(routine) }
    val qr = remember(link) { RoutineShare.qr(link).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_routine_title, routine.name)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(qr, contentDescription = stringResource(R.string.share_routine_link), modifier = Modifier.size(240.dp))
                Text(stringResource(R.string.share_routine_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { RoutineShare.shareLink(context, routine); onDismiss() }) { Text(stringResource(R.string.share_routine_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Borrar una rutina se puede deshacer (como los entrenos, series y ejercicios). */
@Composable
private fun UndoRoutineDelete(vm: HomeViewModel, snackbar: SnackbarHostState) {
    val deletedRoutine by vm.deletedRoutine.collectAsStateWithLifecycle()
    val deletedMsg = deletedRoutine?.let { stringResource(R.string.undo_routine_deleted, it.name) }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(deletedRoutine) {
        if (deletedMsg != null) {
            // Si se gira la pantalla o se sale de Inicio, el ViewModel olvida el borrado a los pocos segundos
            val result = snackbar.showSnackbar(deletedMsg, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearDeleted()
        }
    }
}

/** Cabecera de una carpeta de rutinas; tocarla la pliega o despliega. */
@Composable
private fun FolderHeader(name: String, count: Int, folded: Boolean, icon: String = "📁", onToggle: () -> Unit) {
    val state = stringResource(if (folded) R.string.folder_collapsed else R.string.folder_expanded)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp)
            .semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (folded) "▸" else "▾", Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.primary)
        Text("$icon $name", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Rutinas sin carpeta primero; después cada carpeta con su cabecera (se puede plegar). */
private fun LazyGridScope.routineItems(
    routines: List<Routine>,
    collapsed: Set<String>,
    onToggleFolder: (String) -> Unit,
    card: @Composable (Routine) -> Unit,
) {
    val (archived, active) = routines.partition { it.archived }
    val byFolder = active.groupBy { it.folder?.trim()?.takeIf(String::isNotEmpty) }
    items(byFolder[null].orEmpty(), key = { it.id }) { card(it) }
    byFolder.keys.filterNotNull().sorted().forEach { folder ->
        val list = byFolder.getValue(folder)
        val folded = folder in collapsed
        item(key = "folder:$folder", span = { GridItemSpan(maxLineSpan) }) {
            FolderHeader(folder, list.size, folded) { onToggleFolder(folder) }
        }
        if (!folded) items(list, key = { it.id }) { card(it) }
    }
    // Archivadas al final, plegadas salvo que se abran (la clave en [collapsed] significa aquí «abierta»)
    if (archived.isNotEmpty()) {
        val open = ARCHIVE_KEY in collapsed
        item(key = "folder:$ARCHIVE_KEY", span = { GridItemSpan(maxLineSpan) }) {
            FolderHeader(stringResource(R.string.routines_archived), archived.size, !open, icon = "🗄") { onToggleFolder(ARCHIVE_KEY) }
        }
        if (open) items(archived, key = { it.id }) { card(it) }
    }
}

/** Clave de la sección de rutinas archivadas (no puede coincidir con una carpeta: lleva un carácter de control). */
private const val ARCHIVE_KEY = "\u0000archived"

/** Descartar un entreno por error se puede deshacer al volver a Inicio. */
@Composable
private fun UndoDiscard(vm: HomeViewModel, snackbar: SnackbarHostState, onOpenWorkout: () -> Unit) {
    val discarded by vm.discarded.collectAsStateWithLifecycle()
    val msg = discarded?.let { stringResource(R.string.undo_workout_discarded, it.name) }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(discarded) {
        if (msg != null && !vm.discardRecent()) vm.forgetDiscarded()
        if (msg != null && vm.discardRecent()) {
            val result = snackbar.showSnackbar(msg, undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) vm.restoreDiscarded(onOpenWorkout) else vm.forgetDiscarded()
        }
    }
}

/** Ancho máximo del gimnasio en la barra: un nombre largo no puede tapar el título. */
private val GYM_CHIP_MAX_WIDTH = 140.dp

/** El gimnasio activo arriba a la derecha (solo con más de uno): tocarlo lo cambia antes de empezar. */
@Composable
private fun GymChip(vm: HomeViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    if (settings.gyms.size < 2) return
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.widthIn(max = GYM_CHIP_MAX_WIDTH)) {
        Text("🏋️ " + settings.activeGym.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
    if (open) com.bestrong.mobile.ui.components.GymPickerDialog(settings.gyms, settings.activeGym.id, vm::setActiveGym) { open = false }
}
