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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.Routine
import com.bestrong.mobile.share.RoutineShare
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatTime
import com.bestrong.mobile.ui.theme.FigureFont
import com.bestrong.mobile.ui.theme.enfoque

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenWorkout: () -> Unit,
    onEditRoutine: (String?) -> Unit,
    /** Búsqueda universal (lupa del saludo). */
    onOpenSearch: () -> Unit = {},
    /** Avatar de la cabecera: abre Tú. */
    onOpenProfile: () -> Unit = {},
) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val lastByRoutine by vm.lastByRoutine.collectAsStateWithLifecycle()
    val gymNames by vm.gymNames.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val suggested by vm.suggestedRoutine.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    // Se guarda el id (no el programa) para que la preparación sobreviva al giro
    var setupProgramId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var showPrograms by rememberSaveable { mutableStateOf(false) }
    OpenBuilderAfterWelcome(vm) { showPrograms = true }
    // Fuera de la rejilla: al volver de «Programas» siguen el desplazamiento, la búsqueda y las carpetas plegadas
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    // Buscar rutina (sale con muchas): mientras se busca, se ven todas las que coinciden con sus carpetas abiertas
    var routineQuery by rememberSaveable { mutableStateOf("") }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    // Después del estado de la rejilla: al volver de preparar un programa sigue igual que estaba
    setupProgramId?.let { id -> vm.periodizedPrograms.firstOrNull { it.id == id } }?.let { p ->
        ProgramSetupScreen(p, vm::suggestedTrainingMax, onConfirm = { tms -> vm.activateProgram(p, tms); setupProgramId = null; showPrograms = false }, onDismiss = { setupProgramId = null })
        return
    }
    UndoRoutineDelete(vm, snackbar)
    UndoDiscard(vm, snackbar, onOpenWorkout)
    ForgottenWorkoutDialog(vm)
    // Programas: pantalla completa para crear uno a medida (o elegir un clásico)
    if (showPrograms) {
        ProgramBuilderScreen(
            vm = vm,
            // El creador sigue debajo: «Atrás» en la preparación vuelve a elegir programa
            onPickPeriodized = { setupProgramId = it.id },
            onPickClassic = { vm.addProgram(it); showPrograms = false },
            onDismiss = { showPrograms = false },
        )
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        // Rejilla adaptable: 1 columna en el Fold plegado, 2-3 desplegado
        val shownRoutines = remember(routines, routineQuery) { filterRoutines(routines, routineQuery) }
        val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            state = gridState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Solo tres cosas: lo que toca hoy (con los accesos rápidos, discretos), la semana y las rutinas
            item(span = full) {
                Greeting(onOpenProfile) {
                    GymChip(vm)
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, stringResource(R.string.srch_open)) }
                }
            }
            active?.let { w -> item(span = full) { ActiveWorkoutCard(w, vm.rest.collectAsStateWithLifecycle().value, onOpenWorkout) } }
            if (active == null) todayItems(vm, TodayData(today, suggested, history, lastByRoutine), onOpenWorkout)
            item(span = full) {
                QuickActions(
                    // Si arriba ya hay una tarjeta destacada (entreno abierto, lo que toca), el entreno libre no compite con ella
                    highlightEmpty = active == null && today == null && suggested == null,
                    onEmpty = { vm.startEmpty(onOpenWorkout) },
                    onWatch = { vm.startOnWatch(null); Unit }.takeIf { active != null || (today == null && suggested == null) },
                    onPrograms = { showPrograms = true },
                )
            }
            // La semana en una sola tarjeta: días, racha, volumen y cardio (los músculos están en Perfil)
            item(span = full) { WeekStrip(week, canChangeGoal = today == null, onGoalChange = vm::setWeeklyGoal) { CardioWeekLine(vm) } }
            item(span = full) {
                Column {
                    RoutinesHeader(hasRoutines = routines.any { !it.archived }, onSearch = onOpenSearch, onNew = { onEditRoutine(null) })
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
                val last = lastByRoutine[routine.id]
                var sharing by remember { mutableStateOf(false) }
                if (sharing) ShareRoutineDialog(routine) { sharing = false }
                val actions = RoutineActions(
                    onEdit = { onEditRoutine(routine.id) },
                    onStartWatch = { vm.startOnWatch(routine) },
                    onDuplicate = { vm.duplicate(routine) },
                    onArchive = { vm.setArchived(routine, !routine.archived) },
                    onShare = { sharing = true },
                    onDelete = { vm.delete(routine) },
                    onMoveUp = { vm.move(routine, -1); Unit }.takeIf { routineQuery.isBlank() },
                    onMoveDown = { vm.move(routine, 1); Unit }.takeIf { routineQuery.isBlank() },
                )
                RoutineCard(
                    routine = routine,
                    lastDays = last?.let { daysSince(it.startedAtEpochMs) },
                    gymNames = gymNames,
                    onStart = { vm.startRoutine(routine, onOpenWorkout) },
                    onEdit = actions.onEdit,
                    menu = { close -> RoutineMenuItems(routine, actions, close) },
                )
            }
        }
    }
}

/**
 * Accesos rápidos discretos en una fila de botones pequeños: entreno libre (destacado solo si no hay nada que hacer
 * arriba), en el reloj y programas. Bajan de línea si no caben (letra grande).
 */
@Composable
private fun QuickActions(highlightEmpty: Boolean, onEmpty: () -> Unit, onWatch: (() -> Unit)?, onPrograms: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val small = Modifier.heightIn(min = 48.dp)
        val empty: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Text(stringResource(R.string.home_empty_workout), Modifier.padding(start = 6.dp), fontWeight = FontWeight.Bold)
        }
        if (highlightEmpty) Button(onClick = onEmpty, modifier = small, content = empty) else androidx.compose.material3.FilledTonalButton(onClick = onEmpty, modifier = small, content = empty)
        onWatch?.let { watch ->
            OutlinedButton(onClick = watch, modifier = small) {
                Icon(Icons.Default.Watch, null, Modifier.size(18.dp))
                Text(stringResource(R.string.home_on_watch), Modifier.padding(start = 6.dp))
            }
        }
        OutlinedButton(onClick = onPrograms, modifier = small) {
            Icon(Icons.Default.Star, null, Modifier.size(18.dp))
            Text(stringResource(R.string.home_programs), Modifier.padding(start = 6.dp))
        }
    }
}

/** Sin rutinas: la ilustración de la bienvenida y los dos caminos para empezar. */
@Composable
private fun NoRoutines(onPrograms: () -> Unit, onNew: () -> Unit) {
    com.bestrong.mobile.ui.components.Panel(contentPadding = 24.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 480.dp)) {
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
            Button(onClick = onPrograms, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Default.Star, null)
                Text(stringResource(R.string.home_browse_programs), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.home_new_routine)) }
        }
    }
}

/** Cabecera de la sección de rutinas: «Rutinas», y a la derecha Buscar y Nueva (sin rutinas ya está el botón grande). */
@Composable
private fun RoutinesHeader(hasRoutines: Boolean, onSearch: () -> Unit, onNew: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.enf_home_routines),
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onSearch) {
            Icon(Icons.Default.Search, null, Modifier.size(16.dp))
            Text(stringResource(R.string.enf_home_search), Modifier.padding(start = 4.dp), fontFamily = FigureFont, style = MaterialTheme.typography.labelMedium)
        }
        if (hasRoutines) {
            TextButton(onClick = onNew) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Text(stringResource(R.string.routine_new_short), Modifier.padding(start = 4.dp), fontFamily = FigureFont, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Una rutina en la lista: icono, nombre, «6 ejercicios · 55 min», hace cuánto y, a la derecha, empezar y su menú. */
@Composable
private fun RoutineCard(
    routine: Routine,
    lastDays: Int?,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
    /** Id → nombre de los gimnasios: se enseña el de la rutina, si tiene (y sigue existiendo). */
    gymNames: Map<String, String> = emptyMap(),
) {
    val gymName = routine.gymId?.let(gymNames::get)
    val minutes = remember(routine) { routine.estimatedMinutes() }
    val count = routine.exercises.size
    val meta = listOfNotNull(
        androidx.compose.ui.res.pluralStringResource(R.plurals.exercise_count, count, count),
        "$minutes min".takeIf { minutes > 0 },
        gymName,
    ).joinToString(" · ")
    // TalkBack: «toca dos veces para editar» (no un «activar» sin más)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClickLabel = stringResource(R.string.action_edit), onClick = onEdit).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(Icons.Default.FitnessCenter)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(routine.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, fontFamily = FigureFont, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        lastDays?.let { Text(daysAgo(it), fontFamily = FigureFont, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)) }
        if (routine.exercises.isNotEmpty()) {
            IconButton(onClick = onStart) { Icon(Icons.Default.PlayArrow, stringResource(R.string.routine_start), tint = enfoque.action) }
        }
        var open by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.routine_options)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
        }
    }
}

/** Icono en un cuadrado redondeado, a la izquierda de cada fila de la lista. */
@Composable
private fun RowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** Las opciones de una rutina: editar, reloj, duplicar, archivar, compartir, PDF, mover y borrar. */
@Composable
private fun RoutineMenuItems(routine: Routine, actions: RoutineActions, close: () -> Unit) {
    val unit = com.bestrong.mobile.ui.components.LocalWeightUnit.current
    val context = LocalContext.current
    fun item(text: Int, action: () -> Unit): @Composable () -> Unit = { DropdownMenuItem(text = { Text(stringResource(text)) }, onClick = { close(); action() }) }
    item(R.string.action_edit, actions.onEdit)()
    // El reloj no recibe las archivadas: empezarla allí abriría un entreno vacío
    if (!routine.archived) item(R.string.routine_start_on_watch, actions.onStartWatch)()
    item(R.string.action_duplicate, actions.onDuplicate)()
    item(if (routine.archived) R.string.routine_unarchive else R.string.routine_archive, actions.onArchive)()
    item(R.string.share_routine_link, actions.onShare)()
    item(R.string.export_routine_pdf) { RoutineShare.sharePdf(context, listOf(routine), routine.name, unit) }()
    // Con una búsqueda activa no se reordena: el vecino real podría estar oculto
    actions.onMoveUp?.let { item(R.string.action_move_up, it)() }
    actions.onMoveDown?.let { item(R.string.action_move_down, it)() }
    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }, onClick = { close(); actions.onDelete() })
}

/** Lo que se puede hacer con una rutina desde su menú. */
private class RoutineActions(
    val onEdit: () -> Unit,
    val onStartWatch: () -> Unit,
    val onDuplicate: () -> Unit,
    val onArchive: () -> Unit,
    val onShare: () -> Unit,
    val onDelete: () -> Unit,
    val onMoveUp: (() -> Unit)?,
    val onMoveDown: (() -> Unit)?,
)

/** Lo que hace falta para la tarjeta de hoy. */
private class TodayData(
    val today: com.bestrong.core.training.ProgramSession?,
    val suggested: Routine?,
    val history: List<com.bestrong.core.model.Workout>,
    val lastByRoutine: Map<String, com.bestrong.core.model.Workout>,
)

/**
 * Sin entreno abierto: si hoy ya se entrenó, «✓ hecho» y lo siguiente; si no, lo que toca del programa o la rutina
 * sugerida, con sus cargas y Empezar.
 */
private fun LazyGridScope.todayItems(vm: HomeViewModel, d: TodayData, onOpenWorkout: () -> Unit) {
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    val doneToday = d.history.firstOrNull { it.isFinished && daysSince(it.startedAtEpochMs) == 0 }
    val lastDays = { r: Routine -> d.lastByRoutine[r.id]?.let { daysSince(it.startedAtEpochMs) } }
    val today = d.today
    val suggested = d.suggested
    when {
        doneToday != null -> item(span = full) {
            val next = today?.routine ?: suggested
            DoneTodayCard(doneToday, next) { today?.let { vm.startProgramSession(it, onOpenWorkout) } ?: next?.let { vm.startRoutine(it, onOpenWorkout) } }
        }
        today != null -> item(span = full) {
            TodayCard(today, onStart = { vm.startProgramSession(today, onOpenWorkout) }, onStopProgram = vm::stopProgram, onStartWatch = { vm.startOnWatch(today.routine) }, lastDays = lastDays(today.routine))
        }
        suggested != null -> item(span = full) {
            SuggestedRoutineCard(suggested, onStart = { vm.startRoutine(suggested, onOpenWorkout) }, onStartWatch = { vm.startOnWatch(suggested) }, lastDays = lastDays(suggested))
        }
    }
}

/** Días desde [epochMs] hasta hoy (0 = hoy). */
private fun daysSince(epochMs: Long): Int {
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    return java.time.temporal.ChronoUnit.DAYS.between(day, java.time.LocalDate.now(zone)).toInt()
}

/** QR y enlace de una rutina para pasarla a otro móvil con BeStrong. */
@Composable
private fun ShareRoutineDialog(routine: Routine, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val link = remember(routine) { RoutineShare.link(routine) }
    // Demasiado grande para que el otro móvil la abra: se dice aquí, no «enlace no válido» allí
    val fits = remember(routine) { com.bestrong.core.io.RoutineLink.fits(routine) }
    // Una rutina muy larga no cabe en un QR (fallaba al abrir): entonces solo el enlace
    val qr = remember(link) { if (fits) runCatching { RoutineShare.qr(link).asImageBitmap() }.getOrNull() else null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_routine_title, routine.name)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!fits) {
                    Text(stringResource(R.string.share_routine_too_big), style = MaterialTheme.typography.bodyMedium)
                } else if (qr != null) {
                    Image(qr, contentDescription = stringResource(R.string.share_routine_link), modifier = Modifier.size(240.dp))
                    Text(stringResource(R.string.share_routine_hint), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.share_qr_too_long), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(enabled = fits, onClick = { RoutineShare.shareLink(context, routine); onDismiss() }) { Text(stringResource(R.string.share_routine_send)) } },
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
private fun FolderHeader(name: String, count: Int, folded: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Layers, onToggle: () -> Unit) {
    val state = stringResource(if (folded) R.string.folder_collapsed else R.string.folder_expanded)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onToggle).padding(vertical = 10.dp, horizontal = 4.dp)
            .semantics { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text("$count", fontFamily = FigureFont, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            FolderHeader(stringResource(R.string.routines_archived), archived.size, !open, icon = Icons.Default.Archive) { onToggleFolder(ARCHIVE_KEY) }
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
    // Con un entreno en curso, el gimnasio se cambia desde el entreno (que ajusta los pesos pendientes)
    val active by vm.active.collectAsStateWithLifecycle()
    if (settings.gyms.size < 2 || active != null) return
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.widthIn(max = GYM_CHIP_MAX_WIDTH)) {
        // Con letra grande sobra el emoji: se deja sitio al nombre
        val big = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
        Text((if (big) "" else "🏋️ ") + settings.activeGym.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

/** Cabecera de Hoy: la fecha encima, «Hoy» grande, [actions] (gimnasio, buscar) y el avatar que abre Tú. */
@Composable
private fun Greeting(onOpenProfile: () -> Unit, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    val now = java.time.LocalDate.now()
    val date = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM", androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(now)
    com.bestrong.mobile.ui.components.EnfoqueHeader(kicker = date, title = stringResource(R.string.nav_today), onOpenProfile = onOpenProfile, actions = actions)
}

/** ¿Se quedó un entreno abierto sin querer? Ofrecer terminarlo a la hora de la última serie. */
@Composable
private fun ForgottenWorkoutDialog(vm: HomeViewModel) {
    // Del flujo, no de una lectura al abrir: en arranque en frío el entreno activo llega un poco después
    val active by vm.active.collectAsStateWithLifecycle()
    var handledId by rememberSaveable { mutableStateOf<String?>(null) }
    val forgotten = active?.takeIf { it.id != handledId && vm.forgottenWorkout() != null }
    forgotten?.let { fw ->
        val close = { handledId = fw.id }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.forgotten_title)) },
            text = { Text(stringResource(R.string.forgotten_body, fw.name, formatTime(fw.updatedAtEpochMs, android.text.format.DateFormat.is24HourFormat(LocalContext.current)))) },
            confirmButton = { Button(onClick = { vm.finishForgotten(); close() }) { Text(stringResource(R.string.forgotten_finish)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.discardActive(); close() }) { Text(stringResource(R.string.action_discard)) }
                    TextButton(onClick = { close() }) { Text(stringResource(R.string.forgotten_continue)) }
                }
            },
        )
    }
}

/** Recién terminada la bienvenida y sin rutinas: directo a «Crea tu programa» (con lo que se acaba de elegir). */
@Composable
private fun OpenBuilderAfterWelcome(vm: HomeViewModel, open: () -> Unit) {
    val pendingBuilder = vm.settings.collectAsStateWithLifecycle().value.pendingBuilder
    LaunchedEffect(pendingBuilder) {
        if (pendingBuilder) {
            if (vm.hasNoRoutines()) open()
            vm.clearPendingBuilder()
        }
    }
}
