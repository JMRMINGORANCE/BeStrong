package com.bestrong.mobile.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.CaloriesSource
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSource
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.RestStats
import com.bestrong.core.stats.RestSummary
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatVolume
import com.bestrong.mobile.share.DragShare
import com.bestrong.mobile.share.WorkoutShareCard
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.EmptyState
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.StatTile
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.workout.describeSet
import com.bestrong.mobile.ui.workout.setRemark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel, onWorkoutStarted: () -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    val days by vm.trainingDays.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val heatmap by vm.heatmap.collectAsStateWithLifecycle()
    val partnerWorkouts by vm.partnerHistory.collectAsStateWithLifecycle()
    val showPartner by vm.showPartner.collectAsStateWithLifecycle()
    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val view = LocalView.current
    // Material3 Adaptive 1.1: la navegación entre paneles es suspend (animada)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val deletedMsg = stringResource(R.string.undo_workout_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(deleted) {
        if (deleted != null) {
            if (snackbar.showSnackbar(deletedMsg, undoLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearDeleted()
        }
    }

    var logging by remember { mutableStateOf(false) }
    if (logging) {
        val routines by vm.routines.collectAsStateWithLifecycle()
        LogPastWorkoutDialog(
            routines = routines,
            onLog = { r, start, end ->
                logging = false
                vm.logPast(r, start, end) { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } }
            },
            onDismiss = { logging = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    Column(Modifier.fillMaxSize()) {
                        TopAppBar(
                            title = { Text(stringResource(R.string.nav_history)) },
                            actions = {
                                IconButton(onClick = { logging = true }) { Icon(Icons.Default.EditCalendar, stringResource(R.string.log_past_title)) }
                            },
                        )
                        if (history.isEmpty()) {
                            EmptyState(Icons.Default.History, stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body))
                            return@Column
                        }
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { vm.query.value = it },
                                    placeholder = { Text(stringResource(R.string.history_search)) },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (partnerWorkouts.isNotEmpty()) {
                                item {
                                    FilterChip(
                                        selected = showPartner,
                                        onClick = { vm.showPartner.value = !showPartner },
                                        label = { Text(stringResource(R.string.partner_history, partnerWorkouts.first().athlete.orEmpty())) },
                                    )
                                }
                            }
                            if (query.isBlank()) {
                                item { YearHeatmap(heatmap, Modifier.padding(vertical = 4.dp)) }
                                item { CalendarMonth(days, Modifier.padding(bottom = 8.dp), selectedDay) { vm.selectedDay.value = it } }
                            }
                            selectedDay?.let { day ->
                                item {
                                    // Filtro por el día tocado en el calendario; tocar el chip lo quita
                                    FilterChip(
                                        selected = true,
                                        onClick = { vm.selectedDay.value = null },
                                        label = { Text(stringResource(R.string.history_day_filter, formatShortDate(day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))) },
                                    )
                                }
                            }
                            items(filtered, key = { it.id }) { w ->
                                // Pulsación larga: arrastrar el entreno como texto a otra app (pantalla dividida)
                                val dragText = workoutText(w)
                                WorkoutCard(
                                    w,
                                    Modifier.combinedClickable(
                                        onLongClick = { DragShare.start(view, w.name, dragText) },
                                        onClick = { scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, w.id) } },
                                    ),
                                )
                            }
                        }
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    val id = (navigator.currentDestination?.contentKey as? String)
                    val w = history.firstOrNull { it.id == id }
                    if (w == null) {
                        EmptyState(Icons.Default.FitnessCenter, stringResource(R.string.history_pick_title), stringResource(R.string.history_pick_body))
                    } else {
                        WorkoutDetail(
                            w = w,
                            vm = vm,
                            onBack = if (navigator.canNavigateBack()) ({ scope.launch { navigator.navigateBack() } }) else null,
                            onWorkoutStarted = onWorkoutStarted,
                        )
                    }
                }
            },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun WorkoutCard(w: Workout, modifier: Modifier = Modifier) {
    val unit = LocalWeightUnit.current
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (w.source == WorkoutSource.WATCH) Icon(Icons.Default.Watch, stringResource(R.string.logged_on_watch), Modifier.padding(start = 4.dp))
            }
            Text(formatDate(w.startedAtEpochMs), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.history_card_detail, formatDuration(w.durationSeconds()), unit.formatVolume(w.totalVolume), w.completedSetCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            w.exercises.take(4).forEach { we ->
                Text(stringResource(R.string.sets_times_exercise, we.sets.size, we.exercise.displayName()), style = MaterialTheme.typography.bodySmall)
            }
            if (w.exercises.size > 4) Text(stringResource(R.string.and_n_more, w.exercises.size - 4), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDetail(w: Workout, vm: HistoryViewModel, onBack: (() -> Unit)?, onWorkoutStarted: () -> Unit) {
    val unit = LocalWeightUnit.current
    var menu by remember { mutableStateOf(false) }
    var editing by remember(w.id) { mutableStateOf(false) }
    var editInfo by remember { mutableStateOf(false) }
    var editSet by remember { mutableStateOf<Pair<String, String>?>(null) }
    val records by produceState(emptyList<PersonalRecord>(), w) { value = vm.recordsIn(w) }
    val comparisons by produceState(emptyMap<String, com.bestrong.core.stats.WorkoutStats.ExerciseComparison>(), w) { value = vm.comparisonsFor(w) }
    val restByExercise = remember(w) { RestStats.byExercise(w) }
    val routinesById by vm.routinesById.collectAsStateWithLifecycle()
    if (editInfo) {
        EditPastWorkoutInfoDialog(
            w.name, w.notes, w.startedAtEpochMs, w.durationSeconds() / 60,
            onSave = { n, t, range ->
                if (range != null) vm.retime(w.copy(name = n, notes = t), range.first, range.second) else vm.editWorkout(w) { it.copy(name = n, notes = t) }
                editInfo = false
            },
            onDismiss = { editInfo = false },
        )
    }
    editSet?.let { (weId, setId) ->
        val we = w.exercises.firstOrNull { it.id == weId }
        val set = we?.sets?.firstOrNull { it.id == setId }
        if (we == null || set == null) {
            editSet = null
        } else {
            EditPastSetDialog(
                set = set,
                tracking = we.exercise.tracking,
                onSave = { s -> vm.editWorkout(w) { WorkoutEditor.updateSet(it, weId, setId) { s } }; editSet = null },
                onDelete = { vm.editWorkout(w) { WorkoutEditor.removeSet(it, weId, setId) }; editSet = null },
                onDismiss = { editSet = null },
            )
        }
    }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(w.name) },
            navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } } },
            actions = {
                if (editing) {
                    IconButton(onClick = { editing = false }) { Icon(Icons.Default.Check, stringResource(R.string.history_edit_done)) }
                }
                Box {
                    IconButton(onClick = { WorkoutShareCard.share(context, w, records, unit) }) { Icon(Icons.Default.Share, stringResource(R.string.share_workout)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.history_edit)) }, onClick = { menu = false; editing = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.history_edit_info)) }, onClick = { menu = false; editInfo = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.repeat_workout)) }, onClick = { menu = false; vm.repeat(w, onWorkoutStarted) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.save_as_routine)) }, onClick = { menu = false; vm.saveAsRoutine(w) })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_as_text)) },
                            onClick = {
                                menu = false
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, vm.shareText(w))
                                context.startActivity(android.content.Intent.createChooser(send, null))
                            },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; vm.delete(w.id); onBack?.invoke() })
                    }
                }
            },
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(formatDate(w.startedAtEpochMs), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (editing) item { Text(stringResource(R.string.history_edit_hint), color = MaterialTheme.colorScheme.primary) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.stat_duration), formatDuration(w.durationSeconds()), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stat_volume), unit.formatVolume(w.totalVolume), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stat_sets), "${w.completedSetCount}", Modifier.weight(1f))
                }
            }
            if (w.avgHeartRate != null || w.calories != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        w.avgHeartRate?.let { StatTile(stringResource(R.string.stat_avg_hr), stringResource(R.string.bpm_value, it), Modifier.weight(1f)) }
                        w.maxHeartRate?.let { StatTile(stringResource(R.string.stat_max_hr), stringResource(R.string.bpm_value, it), Modifier.weight(1f)) }
                        w.calories?.let { StatTile(stringResource(R.string.stat_calories), (if (w.caloriesEstimated) "≈" else "") + stringResource(R.string.kcal_value, it), Modifier.weight(1f)) }
                    }
                }
                if (w.caloriesSource != null || w.activeCalories != null) {
                    item { caloriesSourceLine(w)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
            }
            if (records.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.n_records, records.size), fontWeight = FontWeight.Bold)
                            records.forEach { Text(stringResource(R.string.record_line, it.exerciseName, it.type.label(), unit.format(it.value)), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            item { RestSummaryLine(w) }
            if (w.notes.isNotBlank()) item { Text(w.notes) }
            items(w.exercises, key = { it.id }) { we ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(we.exercise.displayName(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        we.sets.forEachIndexed { i, set ->
                            Row(if (editing) Modifier.clickable { editSet = we.id to set.id }.padding(vertical = 8.dp) else Modifier) {
                                Text(if (set.type.short.isEmpty()) "${i + 1}" else set.type.short, Modifier.padding(end = 12.dp), fontWeight = FontWeight.Bold)
                                Text(describeSet(set, we.exercise.tracking, unit), Modifier.weight(1f))
                                if (set.estimatedOneRepMax > 0) Text(stringResource(R.string.one_rm_value, unit.format(set.estimatedOneRepMax)), style = MaterialTheme.typography.bodySmall)
                            }
                            // Nota de la serie y si fue con ayuda (se anotan en el entreno)
                            setRemark(set)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp)) }
                        }
                        restByExercise[we.id]?.let { r -> ExerciseRestLine(w, we, r, routinesById, vm) }
                        // Una vez por ejercicio (en su primer bloque) y solo con peso
                        val firstBlock = w.exercises.firstOrNull { it.exercise.id == we.exercise.id }?.id == we.id
                        if (firstBlock && we.exercise.tracking == com.bestrong.core.model.TrackingType.WEIGHT_REPS) comparisons[we.exercise.id]?.let { ComparisonLine(it) }
                        if (editing) TextButton(onClick = { vm.addPastSet(w, we.id) }) { Text(stringResource(R.string.add_set)) }
                    }
                }
            }
        }
    }
}

private const val NO_CHANGE_KG = 0.5

/** «Frente a la vez anterior: +250 kg de volumen · 1RM +2,5 kg», en verde si mejoró. */
@Composable
private fun ComparisonLine(c: com.bestrong.core.stats.WorkoutStats.ExerciseComparison) {
    val unit = LocalWeightUnit.current

    // Lo que no llega a medio kilo es «sin cambio» (±0); si no, con su signo
    fun signed(v: Double, text: String) = when {
        kotlin.math.abs(v) < NO_CHANGE_KG -> "±0"
        v > 0 -> "+$text"
        else -> "−$text"
    }
    val up = c.best1rmDelta > 0.01 || (c.best1rmDelta >= -0.01 && c.volumeDelta > 0)
    Text(
        stringResource(
            R.string.history_vs_previous,
            signed(c.volumeDelta, unit.formatVolume(kotlin.math.abs(c.volumeDelta))),
            signed(c.best1rmDelta, unit.format(kotlin.math.abs(c.best1rmDelta), withUnit = true)),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = if (up) com.bestrong.mobile.ui.theme.statusColors.good else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Descanso real medio frente al previsto (solo si las series tienen hora de marcado). */
@Composable
private fun RestSummaryLine(w: Workout) {
    val rest = remember(w) { RestStats.summary(w) } ?: return
    Text(
        stringResource(R.string.rest_real_avg, formatDuration(rest.realSeconds.toLong()), formatDuration(rest.plannedSeconds.toLong())),
        style = MaterialTheme.typography.bodySmall,
        color = if (rest.realSeconds > rest.plannedSeconds * OVERRUN_FACTOR) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Descanso real de un ejercicio y, si la rutina lo tiene distinto, botón para usarlo en ella. */
@Composable
private fun ExerciseRestLine(w: Workout, we: com.bestrong.core.model.WorkoutExercise, r: RestSummary, routines: Map<String, com.bestrong.core.model.Routine>, vm: HistoryViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.rest_real_exercise, formatDuration(r.realSeconds.toLong()), formatDuration(r.plannedSeconds.toLong())),
            style = MaterialTheme.typography.bodySmall,
            color = if (r.realSeconds > r.plannedSeconds * OVERRUN_FACTOR) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        // La misma aparición del ejercicio en la rutina (puede salir más de una vez)
        val occurrence = w.exercises.takeWhile { it.id != we.id }.count { it.exercise.id == we.exercise.id }
        val planned = w.routineId?.let { routines[it] }?.exercises?.filter { it.exercise.id == we.exercise.id }?.getOrNull(occurrence)?.restSeconds
        if (planned != null && planned != r.suggestedSeconds) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val done = stringResource(R.string.rest_use_real_done, formatDuration(r.suggestedSeconds.toLong()))
            val notFound = stringResource(R.string.routine_not_found)
            TextButton(
                onClick = {
                    vm.useRealRest(w, we.exercise.id, occurrence, r.suggestedSeconds) { ok ->
                        android.widget.Toast.makeText(context, if (ok) done else notFound, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text(stringResource(R.string.rest_use_real, formatDuration(r.suggestedSeconds.toLong())))
            }
        }
    }
}

/** Descansar más de un 50 % de lo previsto se resalta. */
private const val OVERRUN_FACTOR = 1.5

/** Texto del entreno para compartir o arrastrar: nombre, fecha y la mejor serie de cada ejercicio. */
@Composable
private fun workoutText(w: Workout): String {
    val unit = LocalWeightUnit.current
    val header = stringResource(R.string.share_workout_text, w.name, formatDate(w.startedAtEpochMs), w.completedSetCount)
    val lines = w.exercises.map { we ->
        val best = we.sets.filter { it.completed }.maxByOrNull { it.estimatedOneRepMax }
        "• " + we.exercise.displayName() + (best?.let { " — " + describeSet(it, we.exercise.tracking, unit) } ?: "")
    }
    return (listOf(header) + lines).joinToString("\n")
}

/** De dónde salen las calorías y cuántas son activas (sin el gasto en reposo). */
@Composable
private fun caloriesSourceLine(w: Workout): String? {
    val source = when (w.caloriesSource) {
        CaloriesSource.WATCH -> stringResource(R.string.calories_source_watch)
        CaloriesSource.HEART_RATE -> stringResource(R.string.calories_source_hr)
        CaloriesSource.ESTIMATE -> stringResource(R.string.calories_source_estimate)
        null -> null
    }
    val active = w.activeCalories?.let { stringResource(R.string.active_kcal, it) }
    return listOfNotNull(source, active).joinToString(" · ").ifEmpty { null }
}
