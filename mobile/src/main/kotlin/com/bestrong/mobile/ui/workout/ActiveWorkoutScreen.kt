package com.bestrong.mobile.ui.workout

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import com.bestrong.core.library.ExerciseInfo
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.training.ProgressionSuggestion
import com.bestrong.core.training.SuggestionKind
import com.bestrong.core.training.Tempo
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.R
import com.bestrong.mobile.share.WorkoutShareCard
import com.bestrong.mobile.ui.FoldPosture
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.RestTimerBar
import com.bestrong.mobile.ui.components.StatTile
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.components.reasonText
import com.bestrong.mobile.ui.components.rememberNow
import com.bestrong.mobile.ui.components.uiLanguage
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
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val metrics by vm.watchMetrics.collectAsStateWithLifecycle()
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    KeepScreenOn(settings.keepScreenOn)
    LaunchedEffect(Unit) { vm.focusExercise.collect { listState.animateScrollToItem(it) } }

    val context = LocalContext.current
    val unitForShare = LocalWeightUnit.current
    summary?.let { s ->
        WorkoutSummaryDialog(
            onShare = { WorkoutShareCard.share(context, s.workout, s.records, unitForShare) },
            summary = s,
            onSaveRoutine = { vm.saveSummaryAsRoutine(); vm.dismissSummary(); onFinished() },
            onDone = { vm.dismissSummary(); onFinished() },
        )
        return
    }
    val w = workout ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.workout_none)) }
        return
    }
    val liveMetrics = metrics?.takeIf { it.workoutId == w.id && System.currentTimeMillis() - it.timestampEpochMs < 30_000 }

    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val posture = rememberFoldPosture()
    val expanded = widthClass != WindowWidthSizeClass.COMPACT && posture != FoldPosture.TABLETOP
    // Avisos con acción: deshacer un borrado y ajustar por RPE
    val snackbar = remember { SnackbarHostState() }
    val undoSet = stringResource(R.string.undo_set_deleted)
    val undoExercise = stringResource(R.string.undo_exercise_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val applyLabel = stringResource(R.string.action_apply)
    val rpeDown = stringResource(R.string.rpe_suggest_down)
    val rpeUp = stringResource(R.string.rpe_suggest_up)
    val appContext = LocalContext.current
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is ActiveWorkoutViewModel.WorkoutEvent.Undoable -> {
                    val msg = if (e.kind == ActiveWorkoutViewModel.UndoKind.SET) undoSet else undoExercise
                    if (snackbar.showSnackbar(msg, undoLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undo()
                }
                is ActiveWorkoutViewModel.WorkoutEvent.RpeSuggestion -> {
                    val template = if (e.adjustment.direction < 0) rpeDown else rpeUp
                    val msg = template.format(e.exercise.displayName(appContext), unitForShare.format(e.adjustment.newWeightKg, withUnit = true))
                    if (snackbar.showSnackbar(msg, applyLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                        vm.applyRpeAdjustment(e.weId, e.adjustment)
                    }
                }
            }
        }
    }
    val oneHanded = settings.oneHanded && widthClass == WindowWidthSizeClass.COMPACT && posture != FoldPosture.TABLETOP

    val listContent: @Composable (Modifier) -> Unit = { m ->
        ExerciseList(w, previous, suggestions, vm, onAddExercises, listState, m)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.workout_minimize)) } },
                title = {
                    TextButton(onClick = { renaming = true }, contentPadding = PaddingValues(0.dp)) {
                        Text(w.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = vm::togglePause) {
                        if (w.isPaused) {
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.workout_resume))
                        } else {
                            Icon(Icons.Default.Pause, stringResource(R.string.workout_pause))
                        }
                    }
                    if (!oneHanded) Button(onClick = { confirmFinish = true }) { Text(stringResource(R.string.workout_finish)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.workout_discard)) }, onClick = { menu = false; confirmDiscard = true })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                if (!expanded && posture != FoldPosture.TABLETOP) rest?.let { RestTimerBar(it, vm::adjustRest, vm::skipRest) }
                // Modo una mano: lo importante abajo, al alcance del pulgar
                if (oneHanded) OneHandedBar(w, vm, onFinish = { confirmFinish = true })
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding).fillMaxSize()
        when {
            posture == FoldPosture.TABLETOP -> Column(content) {
                // Mitad superior: lo que se mira desde el banco
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    rest?.let { RestTimerBar(it, vm::adjustRest, vm::skipRest, large = true) }
                        ?: SummaryPanel(w, previous, liveMetrics, Modifier.padding(16.dp))
                }
                HorizontalDivider()
                listContent(Modifier.weight(1f))
            }
            expanded -> Row(content) {
                Column(
                    Modifier.weight(0.38f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    rest?.let { RestTimerBar(it, vm::adjustRest, vm::skipRest, large = true) }
                    SummaryPanel(w, previous, liveMetrics)
                }
                listContent(Modifier.weight(0.62f))
            }
            else -> Column(content) {
                if (w.isPaused) PausedBanner(vm::togglePause)
                StatsRow(w, liveMetrics, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                listContent(Modifier.weight(1f))
            }
        }
    }

    if (confirmFinish) {
        val pending = w.totalSetCount - w.completedSetCount
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.workout_finish_q)) },
            text = {
                Text(
                    when {
                        w.completedSetCount == 0 -> stringResource(R.string.workout_finish_empty)
                        pending > 0 -> stringResource(R.string.workout_finish_pending, pending)
                        else -> stringResource(R.string.workout_finish_ok)
                    },
                )
            },
            confirmButton = { Button(onClick = { confirmFinish = false; vm.finish(); if (w.completedSetCount == 0) onFinished() }) { Text(stringResource(R.string.workout_finish)) } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.action_keep_going)) } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.workout_discard_q)) },
            text = { Text(stringResource(R.string.workout_discard_body)) },
            confirmButton = { Button(onClick = { confirmDiscard = false; vm.discard(); onFinished() }) { Text(stringResource(R.string.action_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (renaming) {
        var name by remember { mutableStateOf(w.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.workout_name)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { Button(onClick = { vm.rename(name); renaming = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(enabled) {
        if (enabled) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
private fun StatsRow(w: Workout, metrics: LiveMetrics?, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val unit = LocalWeightUnit.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stringResource(R.string.stat_duration), formatDuration(w.durationSeconds(now)), Modifier.weight(1f))
        StatTile(stringResource(R.string.stat_volume), unit.formatVolume(w.totalVolume), Modifier.weight(1f))
        StatTile(stringResource(R.string.stat_sets), "${w.completedSetCount}/${w.totalSetCount}", Modifier.weight(1f))
        metrics?.heartRate?.let { StatTile(stringResource(R.string.stat_watch_hr), "$it", Modifier.weight(0.8f)) }
    }
}

@Composable
private fun SummaryPanel(w: Workout, previous: (String) -> List<WorkoutSet>, metrics: LiveMetrics?, modifier: Modifier = Modifier) {
    val unit = LocalWeightUnit.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatsRow(w, metrics)
        metrics?.let { m ->
            Text(
                stringResource(R.string.watch_metrics, m.heartRate?.toString() ?: "--", m.avgHeartRate?.toString() ?: "--", m.calories?.toString() ?: "--"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val muscles = WorkoutStats.setsPerMuscle(listOf(w.copy(finishedAtEpochMs = 1)), 0)
        if (muscles.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.muscles_worked), fontWeight = FontWeight.SemiBold)
                    muscles.entries.take(6).forEach { (m, sets) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(m.label(), Modifier.weight(1f))
                            Text(stringResource(R.string.n_sets, formatWeight(sets)))
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
                    Text(stringResource(R.string.next_up), style = MaterialTheme.typography.labelMedium)
                    Text(we.exercise.displayName(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(describeSet(set, we.exercise.tracking, unit), style = MaterialTheme.typography.headlineMedium)
                    prev?.let { Text(stringResource(R.string.previous_value, describeSet(it, we.exercise.tracking, unit)), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

/** Colores para distinguir superseries (A, B, C…). */
private val supersetColors = listOf(Color(0xFF7C4DFF), Color(0xFF00BFA5), Color(0xFFFFAB00), Color(0xFFFF4081))

@Composable
private fun ExerciseList(
    w: Workout,
    previous: (String) -> List<WorkoutSet>,
    suggestions: Map<String, ProgressionSuggestion>,
    vm: ActiveWorkoutViewModel,
    onAddExercises: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val groups = w.exercises.mapNotNull { it.supersetId }.distinct()
    LazyColumn(modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(w.exercises, key = { _, we -> we.id }) { index, we ->
            val groupIndex = we.supersetId?.let { groups.indexOf(it) }
            ExerciseCard(
                we = we,
                index = index,
                count = w.exercises.size,
                previous = previous(we.exercise.id),
                suggestion = suggestions[we.exercise.id],
                supersetLabel = groupIndex?.let { stringResource(R.string.superset_label, ('A' + it).toString()) },
                supersetColor = groupIndex?.let { supersetColors[it % supersetColors.size] },
                nextInSuperset = w.exercises.getOrNull(index + 1)?.supersetId?.let { it == we.supersetId } == true,
                vm = vm,
            )
        }
        item {
            Button(onClick = onAddExercises, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Icon(Icons.Default.Add, null)
                Text(stringResource(R.string.add_exercises), Modifier.padding(start = 8.dp))
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
    suggestion: ProgressionSuggestion?,
    supersetLabel: String?,
    supersetColor: Color?,
    nextInSuperset: Boolean,
    vm: ActiveWorkoutViewModel,
) {
    val unit = LocalWeightUnit.current
    var menu by remember { mutableStateOf(false) }
    var editRest by remember { mutableStateOf(false) }
    var editNotes by remember { mutableStateOf(false) }
    var editTempo by remember { mutableStateOf(false) }
    var substituting by remember { mutableStateOf(false) }
    if (substituting) SubstituteDialog(vm, we.id) { substituting = false }
    var showInfo by remember { mutableStateOf(false) }
    var showPlates by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Barra lateral de color para las superseries
        Box(Modifier.width(4.dp).fillMaxHeight().background(supersetColor ?: Color.Transparent))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    supersetLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = supersetColor ?: Color.Unspecified, fontWeight = FontWeight.Bold) }
                    Text(
                        we.exercise.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.exercise_subtitle_rest, we.exercise.primaryMuscle.label(), we.exercise.equipment.label(), formatDuration(we.restSeconds.toLong())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ExerciseInfo.instructions(we.exercise.id) != null) {
                    IconButton(onClick = { showInfo = true }) { Icon(Icons.Default.Info, stringResource(R.string.exercise_how_to, we.exercise.displayName())) }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.exercise_options, we.exercise.displayName())) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (we.exercise.tracking == TrackingType.WEIGHT_REPS) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.add_warmup)) }, onClick = { menu = false; vm.addWarmup(we.id) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.plates_title)) }, onClick = { menu = false; showPlates = true })
                        }
                        if (index < count - 1) DropdownMenuItem(text = { Text(stringResource(R.string.superset_with_next)) }, onClick = { menu = false; vm.linkWithNext(we.id) })
                        if (we.supersetId != null) DropdownMenuItem(text = { Text(stringResource(R.string.superset_remove)) }, onClick = { menu = false; vm.unlinkSuperset(we.id) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.rest_time)) }, onClick = { menu = false; editRest = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.notes)) }, onClick = { menu = false; editNotes = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.tempo)) }, onClick = { menu = false; editTempo = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.substitute_title)) }, onClick = { menu = false; substituting = true })
                        if (index > 0) DropdownMenuItem(text = { Text(stringResource(R.string.action_move_up)) }, onClick = { menu = false; vm.moveExercise(index, index - 1) })
                        if (index < count - 1) DropdownMenuItem(text = { Text(stringResource(R.string.action_move_down)) }, onClick = { menu = false; vm.moveExercise(index, index + 1) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove_exercise)) }, onClick = { menu = false; vm.removeExercise(we.id) })
                    }
                }
            }
            Tempo.parse(we.tempo)?.let { t ->
                val reps = we.sets.firstOrNull { it.type != SetType.WARMUP }?.reps ?: 10
                Text(
                    stringResource(R.string.tempo_line, t.toString(), t.timeUnderTension(reps)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            if (we.notes.isNotBlank()) {
                Text(we.notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
            }
            // Sugerencia de sobrecarga progresiva (solo si difiere de lo que hay)
            suggestion?.let { s ->
                val pending = we.sets.filter { !it.completed && it.type != SetType.WARMUP }
                val differs = pending.zip(s.sets).any { (a, b) -> a.weightKg != b.weightKg || a.reps != b.reps || a.durationSec != b.durationSec }
                if (pending.isNotEmpty()) {
                    val first = s.sets.first()
                    val icon = when (s.kind) {
                        SuggestionKind.INCREASE_WEIGHT -> "📈"
                        SuggestionKind.DELOAD -> "🔻"
                        else -> "💡"
                    }
                    AssistChip(
                        onClick = { vm.applySuggestion(we.id, s) },
                        enabled = differs,
                        label = { Text(stringResource(if (differs) R.string.suggestion_apply else R.string.suggestion_applied, icon, describeSet(first, we.exercise.tracking, unit), s.reasonText()), maxLines = 2) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            SetHeader(we.exercise.tracking)
            var normalIndex = 0
            we.sets.forEachIndexed { i, set ->
                val displayIndex = if (set.type == SetType.NORMAL) normalIndex++ else -1
                key(set.id) {
                    SetRow(
                        index = displayIndex,
                        set = set,
                        previous = previous.getOrNull(i),
                        tracking = we.exercise.tracking,
                        onWeightKg = { vm.updateSet(we.id, set.id, weightKg = it) },
                        onReps = { vm.updateSet(we.id, set.id, reps = it) },
                        onDuration = { vm.updateSet(we.id, set.id, duration = it) },
                        onToggle = { vm.toggleSet(we.id, set.id) },
                        onType = { vm.setType(we.id, set.id, it) },
                        onRpe = { vm.setRpe(we.id, set.id, it) },
                        onDelete = { vm.removeSet(we.id, set.id) },
                        unilateral = we.exercise.unilateral,
                        onRepsRight = { vm.updateSetFields(we.id, set.id) { s -> s.copy(repsRight = it) } },
                        onDistance = { vm.updateSetFields(we.id, set.id) { s -> s.copy(distanceM = it) } },
                        onNote = { vm.updateSetFields(we.id, set.id) { s -> s.copy(note = it) } },
                        onAssisted = { vm.updateSetFields(we.id, set.id) { s -> s.copy(assisted = it) } },
                    )
                }
            }
            OutlinedButton(
                onClick = { vm.addSet(we.id) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) { Text(stringResource(R.string.add_set)) }
        }
    }
    if (!nextInSuperset) HorizontalDivider()

    if (editRest) {
        val options = listOf(0, 30, 45, 60, 90, 120, 150, 180, 240, 300)
        AlertDialog(
            onDismissRequest = { editRest = false },
            title = { Text(stringResource(R.string.rest)) },
            text = {
                Column {
                    options.forEach { s ->
                        ListItem(
                            headlineContent = { Text(if (s == 0) stringResource(R.string.no_rest) else formatDuration(s.toLong())) },
                            modifier = Modifier.clickable { vm.setRestSeconds(we.id, s); editRest = false },
                            trailingContent = { if (s == we.restSeconds) Text("✓") },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editRest = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    if (editTempo) {
        var tempo by remember { mutableStateOf(we.tempo.orEmpty()) }
        val valid = tempo.isBlank() || Tempo.parse(tempo) != null
        AlertDialog(
            onDismissRequest = { editTempo = false },
            title = { Text(stringResource(R.string.tempo)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tempo_help), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        tempo,
                        { tempo = it },
                        singleLine = true,
                        isError = !valid,
                        placeholder = { Text("3-1-1-0") },
                        supportingText = { if (!valid) Text(stringResource(R.string.tempo_invalid)) },
                    )
                }
            },
            confirmButton = { Button(onClick = { vm.setTempo(we.id, tempo); editTempo = false }, enabled = valid) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editTempo = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (editNotes) {
        var notes by remember { mutableStateOf(we.notes) }
        AlertDialog(
            onDismissRequest = { editNotes = false },
            title = { Text(stringResource(R.string.notes)) },
            text = { OutlinedTextField(notes, { notes = it }, minLines = 3) },
            confirmButton = { Button(onClick = { vm.setExerciseNotes(we.id, notes); editNotes = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editNotes = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(we.exercise.displayName()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ExerciseInfo.instructions(we.exercise.id, uiLanguage()).orEmpty())
                    Text(
                        stringResource(R.string.primary_muscle, we.exercise.primaryMuscle.label()) +
                            (we.exercise.secondaryMuscles.takeIf { it.isNotEmpty() }?.map { it.label() }?.joinToString()?.let { stringResource(R.string.secondary_muscles, it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    if (showPlates) {
        val target = we.sets.firstOrNull { !it.completed }?.weightKg ?: we.sets.lastOrNull()?.weightKg ?: 0.0
        PlateCalculatorDialog(initialKg = target, gym = vm.settings.value.activeGym, onDismiss = { showPlates = false })
    }
}

@Composable
fun WorkoutSummaryDialog(summary: FinishedSummary, onSaveRoutine: () -> Unit, onDone: () -> Unit, onShare: () -> Unit = {}) {
    val w = summary.workout
    val unit = LocalWeightUnit.current
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Default.EmojiEvents, null) },
        title = { Text(stringResource(R.string.workout_completed)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.stat_duration), formatDuration(w.durationSeconds()), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stat_volume), unit.formatVolume(w.totalVolume), Modifier.weight(1f))
                }
                Text(stringResource(R.string.summary_sets_exercises, w.completedSetCount, w.exercises.size))
                w.avgHeartRate?.let { Text(stringResource(R.string.summary_hr, it, w.maxHeartRate?.toString() ?: "--") + (w.calories?.let { c -> stringResource(R.string.summary_kcal, c) } ?: "")) }
                if (summary.records.isNotEmpty()) {
                    Text(stringResource(R.string.new_records), fontWeight = FontWeight.Bold)
                    summary.records.forEach { pr ->
                        Text(stringResource(R.string.record_line, pr.exerciseName, pr.type.label(), unit.format(pr.value)), style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null)
                    Text(stringResource(R.string.share_workout), Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text(stringResource(R.string.action_done)) } },
        dismissButton = if (w.routineId == null) ({ TextButton(onClick = onSaveRoutine) { Text(stringResource(R.string.save_as_routine)) } }) else null,
    )
}
