package com.bestrong.mobile.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.Routine
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.share.RoutineShare
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.formatTime
import com.bestrong.mobile.ui.components.rememberNow
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
    // Programas: pantalla completa para crear uno a medida (o elegir un clásico)
    if (showPrograms) {
        ProgramBuilderScreen(
            vm = vm,
            onPickPeriodized = { setupProgramId = it.id; showPrograms = false },
            onPickClassic = { vm.addProgram(it); showPrograms = false },
            onDismiss = { showPrograms = false },
        )
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        // Rejilla adaptable: 1 columna en el Fold plegado, 2-3 desplegado
        var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
        // Buscar rutina (sale con muchas): mientras se busca, se ven todas las que coinciden con sus carpetas abiertas
        var routineQuery by rememberSaveable { mutableStateOf("") }
        val shownRoutines = remember(routines, routineQuery) { filterRoutines(routines, routineQuery) }
        val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Saludo; 2. lo que toca ahora; 3. accesos rápidos; 4. la semana; 5. rutinas; 6. el resto
            item(span = full) { Greeting { GymChip(vm) } }
            active?.let { w -> item(span = full) { ActiveWorkoutHero(w, onOpenWorkout) } }
            if (active == null) {
                today?.let { s ->
                    item(span = full) { TodayCard(s, onStart = { vm.startProgramSession(s, onOpenWorkout) }, onStopProgram = vm::stopProgram) }
                } ?: suggested?.let { r ->
                    item(span = full) { SuggestedRoutineCard(r, onStart = { vm.startRoutine(r, onOpenWorkout) }) }
                }
            }
            item(span = full) {
                QuickActions(
                    onEmpty = { vm.startEmpty(onOpenWorkout) },
                    repeatName = history.firstOrNull { it.isFinished }?.name,
                    onRepeat = { vm.repeatLast(onOpenWorkout) },
                    onWatch = { vm.startOnWatch(null) },
                    onPrograms = { showPrograms = true },
                    onPlan = onOpenPlanning,
                )
            }
            item(span = full) { WeekStrip(week, canChangeGoal = today == null, onGoalChange = vm::setWeeklyGoal) }
            item(span = full) {
                Column {
                    SectionHeader(stringResource(R.string.home_my_routines, routines.count { !it.archived }), large = true) {
                        TextButton(onClick = { onEditRoutine(null) }) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.routine_new_short), Modifier.padding(start = 4.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                    RoutineSearchField(routines.size, routineQuery, nothingShown = shownRoutines.isEmpty()) { routineQuery = it }
                }
            }
            if (routines.none { !it.archived }) item(span = full) { NoRoutines(onPrograms = { showPrograms = true }, onNew = { onEditRoutine(null) }) }
            // Buscando, todo abierto (también las archivadas, cuya clave en el conjunto significa «abierta»), y
            // tocar una cabecera no cambia las carpetas de verdad
            routineItems(
                shownRoutines,
                foldersFor(routineQuery, collapsed),
                onToggleFolder = { f -> collapsed = toggledFolder(routineQuery, collapsed, f) },
            ) { routine ->
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
            // Avisos y datos del día, al final para no empujar lo principal
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

/** El entreno abierto, lo primero de todo: tocarlo vuelve a él. */
@Composable
private fun ActiveWorkoutHero(w: com.bestrong.core.model.Workout, onOpenWorkout: () -> Unit) {
    val now = rememberNow()
    com.bestrong.mobile.ui.components.HeroCard(onClick = onOpenWorkout) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_in_progress), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                Text(w.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.home_in_progress_detail, formatDuration(w.durationSeconds(now)), w.completedSetCount, w.totalSetCount))
            }
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, stringResource(R.string.action_continue), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
        }
        androidx.compose.material3.LinearProgressIndicator(
            progress = { if (w.totalSetCount == 0) 0f else w.completedSetCount.toFloat() / w.totalSetCount },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            trackColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.3f),
        )
    }
}

/**
 * Los accesos de siempre en una fila de botones cuadrados (el entreno libre, destacado y más ancho) y, debajo,
 * repetir el último entreno.
 */
@Composable
private fun QuickActions(onEmpty: () -> Unit, repeatName: String?, onRepeat: () -> Unit, onWatch: () -> Unit, onPrograms: () -> Unit, onPlan: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
            val tile = Modifier.fillMaxHeight()
            com.bestrong.mobile.ui.components.QuickAction(Icons.Default.Add, stringResource(R.string.home_empty_workout), onEmpty, tile.weight(1.5f), filled = true)
            com.bestrong.mobile.ui.components.QuickAction(Icons.Default.Watch, stringResource(R.string.home_on_watch), onWatch, tile.weight(1f))
            com.bestrong.mobile.ui.components.QuickAction(Icons.Default.Star, stringResource(R.string.home_programs), onPrograms, tile.weight(1f))
            com.bestrong.mobile.ui.components.QuickAction(Icons.Default.CalendarMonth, stringResource(R.string.planning_open), onPlan, tile.weight(1f))
        }
        repeatName?.let { name ->
            com.bestrong.mobile.ui.components.Panel(onClick = onRepeat, contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Replay, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.home_repeat_last, name),
                        Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Sin rutinas: la ilustración de la bienvenida y los dos caminos para empezar. */
@Composable
private fun NoRoutines(onPrograms: () -> Unit, onNew: () -> Unit) {
    com.bestrong.mobile.ui.components.Panel(contentPadding = 24.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            com.bestrong.mobile.ui.help.BrandIllustration(
                Icons.Default.CalendarMonth,
                listOf(Icons.Default.Repeat, Icons.Default.Star, Icons.Default.Today),
                MaterialTheme.colorScheme.secondary,
                Modifier.size(140.dp),
                animated = false,
            )
            Text(
                stringResource(R.string.home_no_routines),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = onPrograms, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.home_browse_programs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.home_new_routine)) }
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
    com.bestrong.mobile.ui.components.Panel(onClick = onEdit) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(routine.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // Cuánto se tarda, más o menos (series × 40 s + descansos), para elegir según el tiempo que haya
                val minutes = remember(routine) { (WorkoutEditor.estimatedRemainingSeconds(routine.toWorkout(0)) + 59) / 60 }
                val count = routine.exercises.size
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.bestrong.mobile.ui.components.MetaPill(androidx.compose.ui.res.pluralStringResource(R.plurals.exercise_count, count, count))
                    if (minutes > 0) com.bestrong.mobile.ui.components.MetaPill(stringResource(R.string.routine_estimated, minutes))
                    gymName?.let { com.bestrong.mobile.ui.components.MetaPill("🏋️ $it") }
                }
            }
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
        // Las imágenes de sus ejercicios (animadas), para reconocerla de un vistazo, y el botón de empezar al lado
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (routine.exercises.isEmpty()) {
                    Text(stringResource(R.string.routine_no_exercises), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    com.bestrong.mobile.ui.components.ExerciseThumbStrip(routine.exercises.map { it.exercise }, 46.dp, max = 4)
                }
            }
            Button(onClick = onStart, enabled = routine.exercises.isNotEmpty(), modifier = Modifier.height(48.dp)) {
                Icon(Icons.Default.PlayArrow, null)
                Text(stringResource(R.string.routine_start), Modifier.padding(start = 4.dp), fontWeight = FontWeight.Bold)
            }
        }
        lastDone?.let {
            Text(stringResource(R.string.routine_last_done, it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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

/** Cabecera de una carpeta de rutinas: icono, nombre y cuántas tiene; tocarla la pliega o despliega. */
@Composable
private fun FolderHeader(name: String, count: Int, folded: Boolean, icon: String = "📁", onToggle: () -> Unit) {
    val state = stringResource(if (folded) R.string.folder_collapsed else R.string.folder_expanded)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onToggle).padding(vertical = 10.dp, horizontal = 4.dp)
            .semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(icon)
        }
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
        com.bestrong.mobile.ui.components.MetaPill("$count")
        Icon(
            if (folded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
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

/** Con muchas rutinas, una búsqueda por nombre o carpeta (sin acentos ni mayúsculas). */
@Composable
private fun RoutineSearchField(count: Int, query: String, nothingShown: Boolean, onChange: (String) -> Unit) {
    if (count < ROUTINE_SEARCH_MIN && query.isBlank()) return
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.routine_search)) },
        singleLine = true,
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, stringResource(R.string.routine_search_clear)) }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    if (query.isNotBlank() && nothingShown) Text(stringResource(R.string.routine_search_none), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

/**
 * Las rutinas cuyo nombre, carpeta o algún ejercicio (en español o en inglés) contienen lo buscado: «¿en qué rutina
 * estaba el hip thrust?». Todas si no se busca nada.
 */
private fun filterRoutines(routines: List<Routine>, query: String): List<Routine> {
    fun String.plain() = java.text.Normalizer.normalize(lowercase(), java.text.Normalizer.Form.NFD).replace(MARKS, "")
    val q = query.trim().plain()
    if (q.isEmpty()) return routines
    fun matches(e: com.bestrong.core.model.Exercise) = listOf(e.name, com.bestrong.core.library.ExerciseLibrary.displayName(e, "en")).any { it.plain().contains(q) }
    return routines.filter { r -> r.name.plain().contains(q) || r.folder?.plain()?.contains(q) == true || r.exercises.any { matches(it.exercise) } }
}

/** Carpetas plegadas (y archivo abierto) que se usan: buscando, todo a la vista. */
private fun foldersFor(query: String, collapsed: Set<String>): Set<String> = if (query.isBlank()) collapsed else setOf(ARCHIVE_KEY)

/** Plegar o desplegar [folder]; buscando no cambia nada (lo que se ve es todo abierto). */
private fun toggledFolder(query: String, collapsed: Set<String>, folder: String): Set<String> = when {
    query.isNotBlank() -> collapsed
    folder in collapsed -> collapsed - folder
    else -> collapsed + folder
}

private val MARKS = Regex("\\p{Mn}+")

/** A partir de cuántas rutinas sale el buscador. */
private const val ROUTINE_SEARCH_MIN = 8

/** «Buenos días» con la fecha encima, grande, al abrir la app; [actions] a la derecha (el gimnasio). */
@Composable
private fun Greeting(actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    val now = java.time.LocalDateTime.now()
    val greeting = when (now.hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..19 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }
    val date = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.getDefault()).format(now)
    com.bestrong.mobile.ui.components.ScreenTitle(stringResource(greeting), overline = date, actions = actions)
}
