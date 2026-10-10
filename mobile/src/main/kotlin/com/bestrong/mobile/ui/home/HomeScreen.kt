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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.library.TrainingProgram
import com.bestrong.core.model.Routine
import com.bestrong.core.training.PeriodizedProgram
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.R
import com.bestrong.mobile.share.RoutineShare
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
) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val suggested by vm.suggestedRoutine.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    var setupProgram by remember { mutableStateOf<PeriodizedProgram?>(null) }
    setupProgram?.let { p ->
        ProgramSetupScreen(p, vm::suggestedTrainingMax, onConfirm = { tms -> vm.activateProgram(p, tms); setupProgram = null }, onDismiss = { setupProgram = null })
        return
    }
    val snackbar = remember { SnackbarHostState() }
    var showPrograms by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
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
            onPickPeriodized = { setupProgram = it; showPrograms = false },
            onPick = { vm.addProgram(it); showPrograms = false },
            onDismiss = { showPrograms = false },
        )
    }

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
                    FilledTonalButton(onClick = { vm.startOnWatch(null) }) {
                        Icon(Icons.Default.Watch, null)
                        Text(stringResource(R.string.home_on_watch), Modifier.padding(start = 8.dp))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                // Título y acciones en líneas separadas: con letra grande los botones bajan de línea
                Column {
                    SectionHeader(stringResource(R.string.home_my_routines, routines.size))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onEditRoutine(null) }) { Text(stringResource(R.string.home_new_routine)) }
                        TextButton(onClick = { showPrograms = true }) { Text(stringResource(R.string.home_programs)) }
                        TextButton(onClick = onOpenPlanning) { Text(stringResource(R.string.planning_open)) }
                    }
                }
            }
            if (routines.isEmpty()) {
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
            // Preparación del día y proteína/agua (al final para no empujar «Empezar»)
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
) {
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
                        DropdownMenuItem(text = { Text(stringResource(R.string.routine_start_on_watch)) }, onClick = { menu = false; onStartWatch() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { menu = false; onDuplicate() })
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
            lastDone?.let { Text(stringResource(R.string.routine_last_done, it), style = MaterialTheme.typography.labelSmall) }
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
