package com.bestrong.mobile.ui.workout

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import com.bestrong.core.library.ExerciseInfo
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.SessionRating
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.stats.formatValue
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.training.ProgressionSuggestion
import com.bestrong.core.training.SuggestionKind
import com.bestrong.core.training.Tempo
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.share.WorkoutShareCard
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.FoldPosture
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.RestTimerBar
import com.bestrong.mobile.ui.components.StatTile
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.exerciseLabel
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.components.reasonText
import com.bestrong.mobile.ui.components.rememberNow
import com.bestrong.mobile.ui.components.spokenAs
import com.bestrong.mobile.ui.components.uiLanguage
import com.bestrong.mobile.ui.help.TipCard
import com.bestrong.mobile.ui.rememberFoldPosture
import com.bestrong.mobile.ui.theme.statusColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    /** Tras descartar: a Inicio, donde se ofrece deshacerlo. */
    onDiscarded: () -> Unit = onFinished,
) {
    val workout by vm.workout.collectAsStateWithLifecycle()
    val previous by vm.previousSets.collectAsStateWithLifecycle()
    val rest by vm.rest.collectAsStateWithLifecycle()
    val restEndedAt by vm.restEndedAt.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val deadline by vm.deadline.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val metrics by vm.watchMetrics.collectAsStateWithLifecycle()
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var editingNotes by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val dumbbellTotal = vm.settings.collectAsStateWithLifecycle().value.dumbbellTotal

    KeepScreenOn(settings.keepScreenOn)
    LaunchedEffect(Unit) { vm.focusExercise.collect { listState.animateScrollToItem(it) } }

    val context = LocalContext.current
    val unitForShare = LocalWeightUnit.current
    summary?.let { s ->
        WorkoutSummaryDialog(
            onShare = { WorkoutShareCard.share(context, s.workout, s.records, unitForShare) },
            onRate = vm::rateWorkout,
            summary = s,
            onSaveRoutine = { vm.saveSummaryAsRoutine(); vm.dismissSummary(); onFinished() },
            onUpdateRoutine = { vm.updateRoutineFromSummary(); vm.dismissSummary(); onFinished() },
            onDone = { vm.dismissSummary(); onFinished() },
            onShareText = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, vm.shareText(s.workout))
                context.startActivity(android.content.Intent.createChooser(send, null))
            },
            dumbbellTotal = dumbbellTotal,
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
    val undoCopy = stringResource(R.string.undo_copied_sets)
    val undoComplete = stringResource(R.string.undo_completed_sets)
    val undoTrim = stringResource(R.string.undo_trimmed_sets)
    val undoRpeLoad = stringResource(R.string.undo_rpe_loads)
    val undoGym = stringResource(R.string.undo_gym_loads)
    val goalMsg = stringResource(R.string.goal_reached_live)
    val undoLabel = stringResource(R.string.action_undo)
    val applyLabel = stringResource(R.string.action_apply)
    val rpeDown = stringResource(R.string.rpe_suggest_down)
    val rpeUp = stringResource(R.string.rpe_suggest_up)
    val appContext = LocalContext.current
    val conflictMsg = stringResource(R.string.sync_conflict)
    LaunchedEffect(Unit) {
        vm.syncConflicts.collect { list ->
            val c = list.first()
            snackbar.showSnackbar(conflictMsg.format(c.setIndex + 1, c.exerciseName, list.size), duration = SnackbarDuration.Long)
        }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is ActiveWorkoutViewModel.WorkoutEvent.Undoable -> {
                    val msg = when (e.kind) {
                        ActiveWorkoutViewModel.UndoKind.SET -> undoSet
                        ActiveWorkoutViewModel.UndoKind.EXERCISE -> undoExercise
                        ActiveWorkoutViewModel.UndoKind.COPY -> undoCopy
                        ActiveWorkoutViewModel.UndoKind.COMPLETE -> undoComplete
                        ActiveWorkoutViewModel.UndoKind.TRIM -> undoTrim
                        ActiveWorkoutViewModel.UndoKind.RPE_LOAD -> undoRpeLoad
                        ActiveWorkoutViewModel.UndoKind.GYM -> undoGym
                    }
                    if (snackbar.showSnackbar(msg, undoLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                        vm.undo(e)
                    } else {
                        // Lo que venía detrás (objetivo conseguido) solo si no se ha deshecho
                        (e.followUp as? ActiveWorkoutViewModel.WorkoutEvent.GoalReached)?.let {
                            snackbar.showSnackbar(goalMsg.format(it.exercise.displayName(appContext), it.goal), duration = SnackbarDuration.Short)
                        }
                    }
                }
                is ActiveWorkoutViewModel.WorkoutEvent.GoalReached -> {
                    snackbar.showSnackbar(goalMsg.format(e.exercise.displayName(appContext), e.goal), duration = SnackbarDuration.Short)
                }
                is ActiveWorkoutViewModel.WorkoutEvent.RpeSuggestion -> {
                    val template = if (e.adjustment.direction < 0) rpeDown else rpeUp
                    // Con «suma de las dos mancuernas», el total del par (como las filas)
                    val pair = if (vm.settings.value.dumbbellTotal && e.exercise.equipment == Equipment.DUMBBELL) 2 else 1
                    // Cuánto se mueven las que quedan (cada una lo mismo: una pirámide sigue siéndolo)
                    val delta = kotlin.math.abs(e.adjustment.newWeightKg - e.adjustment.fromWeightKg) * pair
                    val msg = template.format(e.exercise.displayName(appContext), unitForShare.format(delta, withUnit = true))
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

    // Bloqueo de toques (bolsillo, sudor): solo se quita con una pulsación larga
    var touchLocked by rememberSaveable { mutableStateOf(false) }
    if (touchLocked) TouchLockOverlay { touchLocked = false }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.workout_minimize)) } },
                title = {
                    // El nombre, en grande y del color del texto (tocarlo lo cambia)
                    TextButton(
                        onClick = { renaming = true },
                        contentPadding = PaddingValues(0.dp),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    ) {
                        Text(w.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    val openGyms = gymSwitch(vm)
                    IconButton(onClick = vm::togglePause) {
                        if (w.isPaused) {
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.workout_resume))
                        } else {
                            Icon(Icons.Default.Pause, stringResource(R.string.workout_pause))
                        }
                    }
                    // Botón compacto: con el relleno por defecto el nombre del entreno se cortaba
                    if (!oneHanded) {
                        Button(onClick = { confirmFinish = true }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(stringResource(R.string.workout_finish), maxLines = 1)
                        }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.workout_notes)) }, onClick = { menu = false; editingNotes = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.touch_lock)) }, onClick = { menu = false; touchLocked = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.gym_switch_title)) }, onClick = { menu = false; openGyms() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.workout_discard)) }, onClick = { menu = false; confirmDiscard = true })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                // Entra y sale deslizándose (la lista no salta al marcar una serie o saltar el descanso)
                var shownRest by remember { mutableStateOf(rest) }
                if (rest != null) shownRest = rest
                androidx.compose.animation.AnimatedVisibility(
                    visible = rest != null && !expanded && posture != FoldPosture.TABLETOP,
                    enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
                ) {
                    // La siguiente serie solo si el descanso es de quien se ve (el de la pareja no lleva la tuya)
                    shownRest?.let { RestTimerBar(it, vm::adjustRest, vm::skipRest, next = nextSetLabel(w, dumbbellTotal).takeIf { _ -> it.workoutId == null || it.workoutId == w.id }) }
                }
                // Modo una mano: lo importante abajo, al alcance del pulgar
                if (oneHanded) OneHandedBar(w, vm, onFinish = { confirmFinish = true })
            }
        },
    ) { padding ->
        // Con el teclado abierto la lista se encoge y deja a la vista la serie que se escribe (la app va de borde a borde)
        val content = Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()
        val eta = EtaInputs(rest?.endsAtEpochMs, settings.warmupRestSeconds, deadline, vm::setDeadline, vm::fitToDeadline)
        when {
            posture == FoldPosture.TABLETOP -> Column(content) {
                // En pausa también se avisa en modo mesa y desplegado (antes solo plegado)
                if (w.isPaused) PausedBanner(vm::togglePause)
                RestOverrunBanner(restEndedAt, vm::dismissOverrun)
                // Mitad superior: lo que se mira desde el banco
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    rest?.let { RestTimerBar(it, vm::adjustRest, vm::skipRest, large = true, giant = true, next = nextSetLabel(w, dumbbellTotal).takeIf { _ -> it.workoutId == null || it.workoutId == w.id }) }
                        ?: SummaryPanel(w, previous, liveMetrics, Modifier.padding(16.dp), eta, dumbbellTotal)
                }
                HorizontalDivider()
                listContent(Modifier.weight(1f))
            }
            expanded -> Row(content) {
                Column(
                    Modifier.weight(0.38f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (w.isPaused) PausedBanner(vm::togglePause)
                    rest?.let { RestTimerBar(it, vm::adjustRest, vm::skipRest, large = true, next = nextSetLabel(w, dumbbellTotal).takeIf { _ -> it.workoutId == null || it.workoutId == w.id }) }
                    if (settings.partnerName.isNotBlank()) PartnerSwitch(vm, settings.partnerName)
                    RestOverrunBanner(restEndedAt, vm::dismissOverrun)
                    if (settings.mediaControls) MediaControls(settings.musicApp)
                    SummaryPanel(w, previous, liveMetrics, eta = eta, dumbbellTotal = dumbbellTotal)
                }
                listContent(Modifier.weight(0.62f))
            }
            else -> Column(content) {
                if (settings.partnerName.isNotBlank()) PartnerSwitch(vm, settings.partnerName)
                if (w.isPaused) PausedBanner(vm::togglePause)
                RestOverrunBanner(restEndedAt, vm::dismissOverrun)
                if (settings.mediaControls) MediaControls(settings.musicApp)
                StatsRow(w, liveMetrics, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), eta)
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
                Column {
                    Text(
                        when {
                            w.completedSetCount == 0 -> stringResource(R.string.workout_finish_empty)
                            pending > 0 -> pluralStringResource(R.plurals.workout_finish_pending, pending, pending)
                            else -> stringResource(R.string.workout_finish_ok)
                        },
                    )
                    // Cuáles son: lo normal es una serie hecha sin marcar; tocar lleva a ella
                    if (pending > 0 && w.completedSetCount > 0) {
                        PendingExercises(w) { i ->
                            confirmFinish = false
                            scope.launch { listState.animateScrollToItem(i) }
                        }
                    }
                }
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
            confirmButton = { Button(onClick = { confirmDiscard = false; vm.discard(); onDiscarded() }) { Text(stringResource(R.string.action_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (renaming) {
        var name by rememberSaveable { mutableStateOf(w.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.workout_name)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { Button(onClick = { vm.rename(name); renaming = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (editingNotes) {
        var notes by rememberSaveable { mutableStateOf(w.notes) }
        AlertDialog(
            onDismissRequest = { editingNotes = false },
            title = { Text(stringResource(R.string.workout_notes)) },
            text = { OutlinedTextField(notes, { notes = it }, minLines = 3, placeholder = { Text(stringResource(R.string.workout_notes_hint)) }) },
            confirmButton = { Button(onClick = { vm.setWorkoutNotes(notes); editingNotes = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editingNotes = false }) { Text(stringResource(R.string.action_cancel)) } },
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

/** «⚠ «Casa» no tiene Mancuernas · Sustituir»: el gimnasio activo no tiene el material del ejercicio. */
@Composable
private fun MissingEquipmentLine(vm: ActiveWorkoutViewModel, we: WorkoutExercise, onSubstitute: () -> Unit) {
    val gym = vm.settings.collectAsStateWithLifecycle().value.activeGym
    val equipment = we.exercise.equipment
    // Sin material o ya hecho: no hace falta avisar
    // Correr, bici, comba… («otro»/cardio) no dependen del material del gimnasio
    val needsNothing = equipment == Equipment.BODYWEIGHT || equipment == Equipment.OTHER || we.exercise.tracking == TrackingType.CARDIO
    if (needsNothing || equipment in gym.equipment || we.sets.all { it.completed }) return
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.gym_missing_equipment, gym.name, equipment.label()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSubstitute) { Text(stringResource(R.string.substitute_title)) }
    }
}

/**
 * «Rango 8–12 · 10/12 → al llegar a 12 en todas, +2,5 kg»: el rango de la doble progresión con lo que
 * llevas en la siguiente serie. Tocarlo abre el selector de rango.
 */
@Composable
private fun RepRangeLine(vm: ActiveWorkoutViewModel, we: WorkoutExercise) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val range = settings.repRangeFor(we.exercise.id)?.takeIf { we.exercise.tracking == TrackingType.WEIGHT_REPS } ?: return
    val unit = LocalWeightUnit.current
    var editing by remember { mutableStateOf(false) }
    if (editing) RepRangeDialog(vm, we.exercise.id) { editing = false }
    val current = we.sets.firstOrNull { !it.completed && it.type != SetType.WARMUP }?.reps
    val factor = if (we.exercise.equipment == Equipment.DUMBBELL && settings.dumbbellTotal) 2 else 1
    Text(
        stringResource(
            R.string.rep_range_line,
            range.first, range.last,
            current?.let { "$it/${range.last}" } ?: "✓",
            // Salto exacto (1,25 no «1,3») y, con «suma de las dos mancuernas», como en las filas
            unit.formatStep(settings.stepKgFor(we.exercise.id) * factor),
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            .clickable(onClickLabel = stringResource(R.string.rep_range)) { editing = true }.padding(horizontal = 12.dp),
    )
}

/** «🏆 6 reps con 100 kg = récord»: lo que hace falta en esta serie para superar tu mejor 1RM estimado. */
@Composable
private fun RecordHint(set: WorkoutSet, bestOneRepMaxKg: Double, weightFactor: Int) {
    val reps = com.bestrong.core.stats.OneRepMax.repsToBeat(bestOneRepMaxKg, set.weightKg) ?: return
    val unit = LocalWeightUnit.current
    Text(
        pluralStringResource(R.plurals.record_hint, reps, reps, unit.format(set.weightKg * weightFactor, withUnit = true)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
    )
}

@Composable
private fun StatsRow(w: Workout, metrics: LiveMetrics?, modifier: Modifier = Modifier, eta: EtaInputs = EtaInputs()) {
    val now = rememberNow()
    val unit = LocalWeightUnit.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.stat_duration), formatDuration(w.durationSeconds(now)), Modifier.weight(1f))
            StatTile(stringResource(R.string.stat_volume), unit.formatVolume(w.totalVolume), Modifier.weight(1f))
            StatTile(stringResource(R.string.stat_sets), "${w.completedSetCount}/${w.totalSetCount}", Modifier.weight(1f), spokenValue = stringResource(R.string.achievements_count, w.completedSetCount, w.totalSetCount))
            metrics?.heartRate?.let { StatTile(stringResource(R.string.stat_watch_hr), "$it", Modifier.weight(0.8f)) }
        }
        // Barra de progreso del entreno (series hechas sobre el total), animada
        if (w.totalSetCount > 0) {
            val progress by androidx.compose.animation.core.animateFloatAsState(w.completedSetCount.toFloat() / w.totalSetCount, label = "progress")
            Box(
                Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(androidx.compose.foundation.shape.CircleShape).background(com.bestrong.mobile.ui.components.brandGradient()))
            }
        }
        // Tiempo aproximado para terminar (series pendientes + descansos) y a qué hora
        val remaining = WorkoutEditor.estimatedRemainingSeconds(w, nowEpochMs = now, warmupRestSeconds = eta.warmupRestSeconds, restEndsAtEpochMs = eta.restEndsAtEpochMs)
        if (remaining > 0) {
            // «Ajustar» solo si de verdad hay algo que recortar
            val deadline = eta.deadlineEpochMs
            val canFit = deadline != null && now + remaining * 1000L > deadline &&
                WorkoutEditor.trimToFit(w, ((deadline - now) / 1000).toInt().coerceAtLeast(0)) {
                    WorkoutEditor.estimatedRemainingSeconds(it, nowEpochMs = now, warmupRestSeconds = eta.warmupRestSeconds, restEndsAtEpochMs = eta.restEndsAtEpochMs)
                } != w
            RemainingTimeLine(remaining, now, eta, canFit)
        }
    }
}

@Composable
private fun SummaryPanel(w: Workout, previous: (String) -> List<WorkoutSet>, metrics: LiveMetrics?, modifier: Modifier = Modifier, eta: EtaInputs = EtaInputs(), dumbbellTotal: Boolean = false) {
    val unit = LocalWeightUnit.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatsRow(w, metrics, eta = eta)
        metrics?.let { m ->
            Text(
                stringResource(R.string.watch_metrics, m.heartRate?.toString() ?: "--", m.avgHeartRate?.toString() ?: "--", m.calories?.toString() ?: "--"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val muscles = WorkoutStats.setsPerMuscle(listOf(w.copy(finishedAtEpochMs = 1)), 0)
        if (muscles.isNotEmpty()) {
            com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
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
        // Índice del entreno: cada ejercicio con sus series hechas (el panel izquierdo del Fold desplegado no se queda vacío)
        if (w.exercises.size > 1) {
            com.bestrong.mobile.ui.components.Panel(contentPadding = 12.dp) {
                Text(stringResource(R.string.workout_outline), fontWeight = FontWeight.SemiBold)
                w.exercises.forEach { we ->
                    val done = we.sets.count { it.completed }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.bestrong.mobile.ui.components.ExerciseThumb(we.exercise, 36.dp, Modifier.padding(end = 10.dp), enlargeOnTap = false)
                        Text(we.exercise.displayName(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "$done/${we.sets.size}",
                            fontWeight = FontWeight.Bold,
                            color = if (done == we.sets.size) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // Siguiente serie pendiente, visible de un vistazo
        // La que toca de verdad (superseries, fuera de orden), con su «anterior» del mismo tipo y el peso del par
        WorkoutEditor.upcomingSet(w)?.let { (i, set) ->
            val we = w.exercises[i]
            val prev = WorkoutEditor.previousMatch(we.sets, we.sets.indexOf(set), previous(we.exercise.id))
            val factor = pairFactor(we.exercise, dumbbellTotal)
            com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                Column(Modifier.padding(12.dp)) {
                    // Es la serie que toca ahora (la primera sin hacer), no la de después
                    Text(stringResource(R.string.current_set_label), style = MaterialTheme.typography.labelMedium)
                    Text(we.exercise.displayName(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(describeSet(set, we.exercise, unit, factor), style = MaterialTheme.typography.headlineMedium)
                    prev?.let { Text(stringResource(R.string.previous_value, describeSet(it, we.exercise, unit, factor)), color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    // Los ejercicios terminados se pliegan (menos el último hecho: su RPE y «añadir serie» siguen a mano)
    var reopened by rememberSaveable { mutableStateOf(setOf<String>()) }
    val lastDoneId = w.exercises.maxByOrNull { we -> we.sets.maxOfOrNull { it.completedAtEpochMs ?: 0L } ?: 0L }?.id
    LazyColumn(modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        // Entreno vacío: en vez de un botón pequeño, cómo se registra un entreno (sobre todo la primera vez)
        if (w.exercises.isEmpty()) {
            item {
                com.bestrong.mobile.ui.components.EmptyState(
                    Icons.Default.FitnessCenter,
                    stringResource(R.string.workout_empty_title),
                    stringResource(R.string.workout_empty_body),
                    Modifier.fillMaxWidth().heightIn(min = 420.dp),
                    action = stringResource(R.string.add_exercises) to onAddExercises,
                )
            }
            return@LazyColumn
        }
        itemsIndexed(w.exercises, key = { _, we -> we.id }) { index, we ->
            val groupIndex = we.supersetId?.let { groups.indexOf(it) }
            val folded = we.supersetId == null && we.sets.isNotEmpty() && we.sets.all { it.completed } && we.id != lastDoneId && we.id !in reopened
            // Al saltar, mover o plegar, la tarjeta se desliza a su sitio en vez de aparecer de golpe (no se pierde de vista)
            androidx.compose.foundation.layout.Box(Modifier.animateItem()) {
                if (folded) DoneExerciseRow(we) { reopened = reopened + we.id } else ExerciseItem(w, we, index, groupIndex, previous, suggestions, vm)
            }
        }
        item {
            Button(onClick = onAddExercises, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Icon(Icons.Default.Add, null)
                Text(stringResource(R.string.add_exercises), Modifier.padding(start = 8.dp))
            }
        }
        item { TipCard("swipe_sets", R.string.tip_swipe, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
    }
}

@Composable
private fun ExerciseItem(
    w: Workout,
    we: WorkoutExercise,
    index: Int,
    groupIndex: Int?,
    previous: (String) -> List<WorkoutSet>,
    suggestions: Map<String, ProgressionSuggestion>,
    vm: ActiveWorkoutViewModel,
) {
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

@OptIn(ExperimentalLayoutApi::class)
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
    var editNotes by rememberSaveable { mutableStateOf(false) }
    var editPinned by rememberSaveable { mutableStateOf(false) }
    var editIncrement by remember { mutableStateOf(false) }
    val stepKg = vm.settings.collectAsStateWithLifecycle().value.stepKgFor(we.exercise.id)
    val pinnedNote = vm.pinnedNotes.collectAsStateWithLifecycle().value[we.exercise.id].orEmpty()
    var editTempo by rememberSaveable { mutableStateOf(false) }
    var substituting by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var editBar by remember { mutableStateOf(false) }
    var editRange by remember { mutableStateOf(false) }
    if (editRange) RepRangeDialog(vm, we.exercise.id) { editRange = false }
    if (editBar) ExerciseBarDialog(vm, we.exercise.id) { editBar = false }
    if (showHistory) {
        val factor = if (we.exercise.equipment == Equipment.DUMBBELL && vm.settings.collectAsStateWithLifecycle().value.dumbbellTotal) 2 else 1
        ExerciseHistorySheet(vm, we.exercise, factor) { showHistory = false }
    }
    if (substituting) SubstituteDialog(vm, we.id) { substituting = false }
    var showInfo by remember { mutableStateOf(false) }
    var showPlates by remember { mutableStateOf(false) }
    // Arrastrar para reordenar: pulsación larga en el nombre y mover arriba/abajo
    var dragY by remember { mutableFloatStateOf(0f) }
    var cardHeight by remember { mutableIntStateOf(1) }
    val dragHint = stringResource(R.string.drag_to_reorder)

    // Cada ejercicio en su bloque redondeado (las superseries, juntas y con su barra de color)
    Row(
        Modifier.fillMaxWidth()
            // El alto incluye el margen: arrastrar N tarjetas mueve N puestos
            .onSizeChanged { cardHeight = it.height.coerceAtLeast(1) }
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = if (nextInSuperset) 0.dp else 6.dp)
            .height(IntrinsicSize.Min)
            .graphicsLayer { translationY = dragY }
            .zIndex(if (dragY != 0f) 1f else 0f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // Barra lateral de color para las superseries
        Box(Modifier.width(4.dp).fillMaxHeight().background(supersetColor ?: Color.Transparent))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                com.bestrong.mobile.ui.components.ExerciseThumb(we.exercise, 54.dp, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    // El color solo en la barra lateral: el ámbar o el turquesa como texto no se leen sobre fondo claro
                    supersetLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
                    Text(
                        we.exercise.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .semantics { heading(); stateDescription = dragHint }
                            .pointerInput(index, count) {
                                detectDragGesturesAfterLongPress(
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragY += amount.y
                                    },
                                    onDragEnd = {
                                        val steps = (dragY / cardHeight).roundToInt()
                                        val target = (index + steps).coerceIn(0, count - 1)
                                        dragY = 0f
                                        if (target != index) vm.moveExercise(index, target)
                                    },
                                    onDragCancel = { dragY = 0f },
                                )
                            },
                    )
                    Text(
                        stringResource(R.string.exercise_subtitle_rest, we.exercise.primaryMuscle.label(), we.exercise.equipment.label(), formatDuration(we.restSeconds.toLong())) +
                            lastDoneSuffix(previous),
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
                            if (we.exercise.equipment == Equipment.BARBELL) DropdownMenuItem(text = { Text(stringResource(R.string.exercise_bar)) }, onClick = { menu = false; editBar = true })
                        }
                        if (index < count - 1) DropdownMenuItem(text = { Text(stringResource(R.string.superset_with_next)) }, onClick = { menu = false; vm.linkWithNext(we.id) })
                        if (we.supersetId != null) DropdownMenuItem(text = { Text(stringResource(R.string.superset_remove)) }, onClick = { menu = false; vm.unlinkSuperset(we.id) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.rest_time)) }, onClick = { menu = false; editRest = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.notes)) }, onClick = { menu = false; editNotes = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.pinned_note)) }, onClick = { menu = false; editPinned = true })
                        if (we.exercise.tracking == TrackingType.WEIGHT_REPS) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.weight_increment)) }, onClick = { menu = false; editIncrement = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.rep_range)) }, onClick = { menu = false; editRange = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.add_drop_set)) }, onClick = { menu = false; vm.addDropSet(we.id) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.add_back_off)) }, onClick = { menu = false; vm.addBackOff(we.id) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.tempo)) }, onClick = { menu = false; editTempo = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.exercise_history)) }, onClick = { menu = false; showHistory = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.substitute_title)) }, onClick = { menu = false; substituting = true })
                        val completable = WorkoutEditor.completableCount(we)
                        if (completable > 0) DropdownMenuItem(text = { Text(stringResource(R.string.complete_remaining, completable)) }, onClick = { menu = false; vm.completeRemaining(we.id) })
                        if (index < count - 1) DropdownMenuItem(text = { Text(stringResource(R.string.skip_exercise)) }, onClick = { menu = false; vm.skipExercise(we.id) })
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
            // Barra: discos por lado de la siguiente serie, sin abrir la calculadora
            if (we.exercise.equipment == Equipment.BARBELL && we.exercise.tracking == TrackingType.WEIGHT_REPS) {
                val next = we.sets.firstOrNull { !it.completed }?.weightKg ?: 0.0
                val gym = vm.settings.collectAsStateWithLifecycle().value.gymFor(we.exercise.id)
                if (next > 0) {
                    val load = remember(next, gym, unit) { gym.plateLoad(next, unit) }
                    // Por debajo de la barra no hay «solo la barra» que valga: se avisa en otro color
                    val belowBar = next < gym.loadedBarKg - 0.01
                    Text(
                        if (belowBar) {
                            stringResource(R.string.plates_below_bar, unit.format(gym.loadedBarKg, withUnit = true))
                        } else if (load.perSide.isEmpty()) {
                            stringResource(R.string.plates_inline_bar)
                        } else {
                            stringResource(R.string.plates_inline, load.perSide.joinToString(" + ") { formatWeight(it) }) + if (load.remainder > 0.01) " ≈" else ""
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (belowBar) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        // Alineado con el nombre del ejercicio (a la derecha de su imagen)
                        modifier = Modifier.padding(start = 78.dp, end = 12.dp).minimumInteractiveComponentSize().clickable { showPlates = true },
                    )
                }
            }
            // Mancuernas: si el peso no existe en el gimnasio activo, las más cercanas (por debajo y por encima)
            if (we.exercise.equipment == Equipment.DUMBBELL && we.exercise.tracking == TrackingType.WEIGHT_REPS) {
                val next = we.sets.firstOrNull { !it.completed }?.weightKg ?: 0.0
                val prefsNow = vm.settings.collectAsStateWithLifecycle().value
                val available = prefsNow.activeGym.dumbbellsKg
                // Con «suma de las dos» se muestran como en la fila (el peso se guarda por mancuerna)
                val factor = if (prefsNow.dumbbellTotal) 2 else 1
                if (next > 0 && available.isNotEmpty() && available.none { kotlin.math.abs(it - next) < 0.01 }) {
                    val near = listOfNotNull(available.filter { it < next }.maxOrNull(), available.filter { it > next }.minOrNull())
                    Text(
                        stringResource(R.string.dumbbells_nearest, near.joinToString(" · ") { unit.format(it * factor, withUnit = true) }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            MissingEquipmentLine(vm, we) { substituting = true }
            TargetRpeLine(vm, we)
            RepRangeLine(vm, we)
            PlannedRepsLine(we)
            // Nota fija del ejercicio (se guarda para siempre, no solo en este entreno)
            if (pinnedNote.isNotBlank()) {
                Text(
                    "📌 $pinnedNote",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 12.dp).minimumInteractiveComponentSize().clickable { editPinned = true }.spokenAs(pinnedNote),
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
                    // Con «suma de las dos mancuernas», igual que en las filas
                    val chipFactor = if (we.exercise.equipment == Equipment.DUMBBELL && vm.settings.collectAsStateWithLifecycle().value.dumbbellTotal) 2 else 1
                    AssistChip(
                        onClick = { vm.applySuggestion(we.id, s) },
                        enabled = differs,
                        label = { Text(stringResource(if (differs) R.string.suggestion_apply else R.string.suggestion_applied, icon, describeSet(first, we.exercise, unit, chipFactor), s.reasonText()), maxLines = 2) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val prefs by vm.settings.collectAsStateWithLifecycle()
            val dumbbell = we.exercise.equipment == Equipment.DUMBBELL && we.exercise.tracking == TrackingType.WEIGHT_REPS
            val weightFactor = if (dumbbell && prefs.dumbbellTotal) 2 else 1
            SetHeader(we.exercise.tracking, dumbbell, prefs.dumbbellTotal)
            var normalIndex = 0
            // Mejor 1RM estimado y peso máximo hasta hoy (sin historial no hay récord que batir)
            val bestBefore = vm.bests.collectAsStateWithLifecycle().value[we.exercise.id]?.takeIf { we.exercise.tracking == TrackingType.WEIGHT_REPS }
            // Para la pista «N reps = récord»: lo que ya batiste hoy también cuenta
            val doneToday = vm.workout.collectAsStateWithLifecycle().value?.exercises.orEmpty().filter { it.exercise.id == we.exercise.id }.flatMap { it.completedSets }
            val bestNow = bestBefore?.let { b -> maxOf(b.first, doneToday.filter { it.countsForRecords }.maxOfOrNull { it.estimatedOneRepMax } ?: 0.0) }
            val firstPendingId = we.sets.firstOrNull { !it.completed && it.countsForRecords }?.id
            val pausedAt = vm.workout.collectAsStateWithLifecycle().value?.pausedAtEpochMs
            we.sets.forEachIndexed { i, set ->
                val displayIndex = if (set.type == SetType.NORMAL) normalIndex++ else -1
                key(set.id) {
                    // En pausa, el cronómetro del cardio se queda parado donde se paró
                    androidx.compose.runtime.CompositionLocalProvider(LocalPausedAt provides pausedAt) {
                        SetRow(
                            index = displayIndex,
                            set = set,
                            previous = WorkoutEditor.previousMatch(we.sets, i, previous),
                            tracking = we.exercise.tracking,
                            pace = com.bestrong.core.util.PaceStyle.of(we.exercise.id),
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
                            onStartTimer = { vm.startCardio(we.id, set.id) },
                            onNote = { vm.updateSetFields(we.id, set.id) { s -> s.copy(note = it) } },
                            onAssisted = { vm.updateSetFields(we.id, set.id) { s -> s.copy(assisted = it) } },
                            followingCount = WorkoutEditor.followingCount(we, set.id),
                            onCopyFollowing = { vm.copyToFollowing(we.id, set.id) },
                            onDuplicate = { vm.duplicateSet(we.id, set.id) },
                            onStepWeight = { vm.stepWeight(we.id, set.id, it) },
                            onStepReps = { vm.stepReps(we.id, set.id, it) },
                            weightFactor = weightFactor,
                            stepKg = stepKg,
                            isRecord = bestBefore != null && set.completed && set.countsForRecords &&
                                (set.estimatedOneRepMax > bestBefore.first + 1e-6 || set.weightKg > bestBefore.second + 1e-6),
                        )
                    }
                    // En la siguiente serie: cuántas reps con ese peso serían récord
                    if (set.id == firstPendingId && bestNow != null) RecordHint(set, bestNow, weightFactor)
                }
            }
            OutlinedButton(
                onClick = { vm.addSet(we.id) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) { Text(stringResource(R.string.add_set)) }
        }
    }

    if (editRest) {
        val options = listOf(0, 30, 45, 60, 90, 120, 150, 180, 240, 300)
        AlertDialog(
            onDismissRequest = { editRest = false },
            title = { Text(stringResource(R.string.rest)) },
            text = {
                // Opciones excluyentes: TalkBack dice cuál está elegida y «3 de 10»
                Column(Modifier.selectableGroup()) {
                    options.forEach { s ->
                        ListItem(
                            headlineContent = { Text(if (s == 0) stringResource(R.string.no_rest) else formatDuration(s.toLong())) },
                            modifier = Modifier.selectable(selected = s == we.restSeconds, role = Role.RadioButton) { vm.setRestSeconds(we.id, s); editRest = false },
                            trailingContent = { if (s == we.restSeconds) Icon(Icons.Default.Check, null) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editRest = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    if (editTempo) {
        var tempo by rememberSaveable { mutableStateOf(we.tempo.orEmpty()) }
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
        var notes by rememberSaveable { mutableStateOf(we.notes) }
        AlertDialog(
            onDismissRequest = { editNotes = false },
            title = { Text(stringResource(R.string.notes)) },
            text = { OutlinedTextField(notes, { notes = it }, minLines = 3) },
            confirmButton = { Button(onClick = { vm.setExerciseNotes(we.id, notes); editNotes = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editNotes = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (editIncrement) {
        val custom = vm.settings.collectAsStateWithLifecycle().value.exerciseIncrementKg[we.exercise.id]
        AlertDialog(
            onDismissRequest = { editIncrement = false },
            title = { Text(stringResource(R.string.weight_increment)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.weight_increment_hint), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = custom == null,
                            onClick = { vm.setIncrement(we.exercise.id, null); editIncrement = false },
                            label = { Text(stringResource(R.string.weight_increment_default, unit.formatStep(unit.stepKg()))) },
                        )
                        unit.incrementChoices().forEach { value ->
                            val kg = unit.toKg(value)
                            FilterChip(
                                selected = custom != null && kotlin.math.abs(custom - kg) < 1e-6,
                                onClick = { vm.setIncrement(we.exercise.id, kg); editIncrement = false },
                                label = { Text(unit.formatStep(kg)) },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editIncrement = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    if (editPinned) {
        var note by rememberSaveable { mutableStateOf(pinnedNote) }
        AlertDialog(
            onDismissRequest = { editPinned = false },
            title = { Text(stringResource(R.string.pinned_note)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.pinned_note_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(note, { note = it }, minLines = 2)
                }
            },
            confirmButton = { Button(onClick = { vm.savePinnedNote(we.exercise.id, note); editPinned = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editPinned = false }) { Text(stringResource(R.string.action_cancel)) } },
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
        // «Usar este peso» lo pone en la siguiente serie pendiente (y en las siguientes iguales)
        val pending = we.sets.firstOrNull { !it.completed }
        PlateCalculatorDialog(
            initialKg = target,
            gym = vm.settings.value.gymFor(we.exercise.id),
            onApply = pending?.let { set -> { kg: Double -> vm.updateSet(we.id, set.id, weightKg = kg) } },
            onDismiss = { showPlates = false },
        )
    }
}

/** «🎯 RPE objetivo 8 en las series efectivas · ≈ 72,5 kg para 8 reps» (el peso, con historial; tocarlo lo aplica a las series pendientes, cada una con sus reps). */
@Composable
private fun TargetRpeLine(vm: ActiveWorkoutViewModel, we: com.bestrong.core.model.WorkoutExercise) {
    val rpe = we.targetRpe?.takeIf { we.exercise.tracking == com.bestrong.core.model.TrackingType.WEIGHT_REPS } ?: return
    val unit = LocalWeightUnit.current
    val e1rms by vm.recentOneRepMax.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val e1rm = e1rms[we.exercise.id]
    val load = remember(we.sets, rpe, e1rm, settings) { targetRpeLoad(we, e1rm, settings) }
    // Solo se puede aplicar si queda alguna serie normal por hacer
    val pending = we.sets.any { !it.completed && it.type == SetType.NORMAL }
    // Con «suma de las dos mancuernas», igual que en las filas
    val factor = if (we.exercise.equipment == Equipment.DUMBBELL && settings.dumbbellTotal) 2 else 1
    val hint = load?.let { (kg, reps) -> " · " + stringResource(R.string.target_rpe_hint, unit.format(kg * factor, withUnit = true), reps) }.orEmpty()
    Text(
        // Como el RPE de las series («@8.5»)
        stringResource(R.string.target_rpe_line, com.bestrong.core.util.formatRpe(rpe)) + hint,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        // Tocar pone ese peso en las series pendientes
        modifier = Modifier.padding(horizontal = 12.dp)
            .then(if (load != null && pending) Modifier.minimumInteractiveComponentSize().clickable(onClickLabel = stringResource(R.string.plates_use_weight)) { vm.applyRpeLoads(we.id) } else Modifier),
    )
}

/** «Plan de la rutina: 5 reps» (o «5-8» si cambian por serie): lo que «la próxima vez» compara con lo hecho. */
@Composable
private fun PlannedRepsLine(we: com.bestrong.core.model.WorkoutExercise) {
    val planned = we.sets.filter { it.type != SetType.WARMUP }.mapNotNull { it.targetReps }
    if (planned.isEmpty() || we.exercise.tracking != com.bestrong.core.model.TrackingType.WEIGHT_REPS) return
    val text = if (planned.min() == planned.max()) "${planned.min()}" else "${planned.min()}-${planned.max()}"
    Text(
        stringResource(R.string.planned_reps_line, text),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

/** Diálogo para cambiar de gimnasio a mitad de entreno (de hotel, de casa…); devuelve cómo abrirlo. */
@Composable
private fun gymSwitch(vm: ActiveWorkoutViewModel): () -> Unit {
    var open by remember { mutableStateOf(false) }
    if (open) {
        val settings by vm.settings.collectAsStateWithLifecycle()
        com.bestrong.mobile.ui.components.GymPickerDialog(settings.gyms, settings.activeGym.id, vm::setActiveGym) { open = false }
    }
    return { open = true }
}

/** «Press banca: RPE 8.5 de media (objetivo 8)»: más duro en rojo, en el objetivo en verde. */
@Composable
private fun RpeVsTargetLines(w: com.bestrong.core.model.Workout) {
    val items = remember(w) { com.bestrong.core.stats.RpeTarget.vsTarget(w) }
    if (items.isEmpty()) return
    Text(stringResource(R.string.summary_rpe_title), fontWeight = FontWeight.Bold)
    items.forEach { r ->
        Text(
            stringResource(
                R.string.summary_rpe_line,
                ExerciseLibrary.byId(r.exerciseId)?.displayName() ?: r.exerciseName,
                com.bestrong.core.util.formatRpe(Math.round(r.averageRpe * 10) / 10.0),
                com.bestrong.core.util.formatRpe(r.targetRpe),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = when (r.direction) {
                1 -> MaterialTheme.colorScheme.error
                0 -> statusColors.good
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** «Frente a la última vez»: volumen con peso, reps sin peso y tiempo en los de tiempo (como el historial y el reloj). */
@Composable
private fun SummaryComparisons(summary: FinishedSummary) {
    val unit = LocalWeightUnit.current
    val kinds = summary.workout.exercises.associate { it.exercise.id to com.bestrong.core.util.ComparisonKind.of(it.exercise.tracking) }
    val shown = summary.comparisons.filter { kinds[it.exerciseId] != com.bestrong.core.util.ComparisonKind.NONE }
    if (shown.isEmpty()) return
    Text(stringResource(R.string.summary_vs_last), fontWeight = FontWeight.Bold)
    shown.take(5).forEach { c ->
        val (up, text) = when (kinds[c.exerciseId]) {
            com.bestrong.core.util.ComparisonKind.REPS -> (c.repsDelta > 0) to stringResource(R.string.history_vs_previous_reps_short, com.bestrong.core.util.signedCount(c.repsDelta))
            com.bestrong.core.util.ComparisonKind.TIME -> (c.durationDelta > 0) to com.bestrong.core.util.signedDuration(c.durationDelta)
            com.bestrong.core.util.ComparisonKind.DISTANCE -> com.bestrong.core.util.distanceComparison(c).let { (d, t) -> (d > 0) to t }
            else -> (c.best1rmDelta > 0.01 || (c.best1rmDelta >= -0.01 && c.volumeDelta > 0)) to
                (if (c.volumeDelta >= 0) "+" else "−") + unit.formatVolume(kotlin.math.abs(c.volumeDelta))
        }
        Text(
            stringResource(R.string.summary_vs_line, ExerciseLibrary.byId(c.exerciseId)?.displayName() ?: c.exerciseName, text),
            style = MaterialTheme.typography.bodySmall,
            color = if (up) statusColors.good else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun WorkoutSummaryDialog(
    summary: FinishedSummary,
    onSaveRoutine: () -> Unit,
    onDone: () -> Unit,
    onShare: () -> Unit = {},
    onRate: (SessionRating) -> Unit = {},
    onUpdateRoutine: () -> Unit = {},
    onShareText: () -> Unit = {},
    dumbbellTotal: Boolean = false,
) {
    val w = summary.workout
    val unit = LocalWeightUnit.current
    AlertDialog(
        onDismissRequest = onDone,
        // Un toque fuera sin querer no se lleva la valoración ni «Guardar como rutina» (Atrás y «Hecho» sí cierran)
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Default.EmojiEvents, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.workout_completed), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) },
        text = {
            // Con récords, comparaciones y próxima vez puede no caber: se desplaza
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Los datos con el color normal del texto (el diálogo los atenuaría)
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Un tono más que el diálogo (si no, las tarjetas se funden con él); sin peso, las series en vez de «0 kg»
                        val tile = MaterialTheme.colorScheme.surfaceContainerHighest
                        StatTile(stringResource(R.string.stat_duration), formatDuration(w.durationSeconds()), Modifier.weight(1f), container = tile)
                        if (w.totalVolume > 0) {
                            StatTile(stringResource(R.string.stat_volume), unit.formatVolume(w.totalVolume), Modifier.weight(1f), container = tile)
                        } else {
                            StatTile(stringResource(R.string.stat_sets), "${w.completedSetCount}", Modifier.weight(1f), container = tile)
                        }
                    }
                }
                // «1 serie · 1 ejercicio»: con plurales
                Text(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.history_sets_done, w.completedSetCount, w.completedSetCount) + " · " +
                        androidx.compose.ui.res.pluralStringResource(R.plurals.exercise_count, w.exercises.size, w.exercises.size),
                )
                val kcal = w.calories?.let { c -> (if (w.caloriesEstimated) "≈" else "") + stringResource(R.string.kcal_value, c) }
                listOfNotNull(w.avgHeartRate?.let { stringResource(R.string.summary_hr, it, w.maxHeartRate?.toString() ?: "--") }, kcal)
                    .takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · ")) }
                if (w.notes.isNotBlank()) Text(w.notes, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                // Cuánto descansaste de verdad frente a lo previsto
                remember(w) { com.bestrong.core.stats.RestStats.summary(w) }?.let { r ->
                    Text(
                        stringResource(R.string.rest_real_avg, formatDuration(r.realSeconds.toLong()), formatDuration(r.plannedSeconds.toLong())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                summary.weeklyGoalReached?.let { goal ->
                    Text(pluralStringResource(R.plurals.weekly_goal_reached, goal, goal), fontWeight = FontWeight.Bold, color = statusColors.good)
                }
                // Los récords arriba, antes de la valoración: es lo primero que se quiere ver
                if (summary.records.isNotEmpty()) {
                    Text(stringResource(R.string.new_records), fontWeight = FontWeight.Bold)
                    summary.records.forEach { pr ->
                        // Con «suma de las dos mancuernas», el peso y el 1RM del par (como en las series y el historial)
                        val f = if (pr.type == com.bestrong.core.stats.RecordType.HEAVIEST_WEIGHT || pr.type == com.bestrong.core.stats.RecordType.BEST_ONE_REP_MAX) com.bestrong.mobile.ui.components.pairFactor(pr.exerciseId) else 1
                        Text(stringResource(R.string.record_line, pr.exerciseLabel(), pr.type.label(), pr.type.formatValue(pr.value * f, unit)), style = MaterialTheme.typography.bodySmall)
                    }
                }
                // Frente a la última vez que hiciste cada ejercicio
                SummaryComparisons(summary)
                RpeVsTargetLines(w)
                summary.imbalances.forEach { (id, ratio) ->
                    Text(
                        stringResource(R.string.summary_imbalance, ExerciseLibrary.byId(id)?.displayName() ?: id, (ratio * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // Doble progresión: qué peso usar la próxima vez
                if (summary.nextTime.isNotEmpty()) {
                    Text(stringResource(R.string.summary_next_time), fontWeight = FontWeight.Bold)
                    summary.nextTime.take(6).forEach { n ->
                        val arrow = when {
                            n.direction > 0 -> "↑"
                            n.direction < 0 -> "↓"
                            else -> "="
                        }
                        Text(
                            stringResource(R.string.summary_next_line, arrow, ExerciseLibrary.byId(n.exerciseId)?.displayName() ?: n.exerciseName, unit.format(n.weightKg * pairFactor(w.exercises.firstOrNull { it.exercise.id == n.exerciseId }?.exercise, dumbbellTotal), withUnit = true), n.reps),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (n.direction > 0) statusColors.good else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Por qué se mantiene alguno: le falta el RPE de la serie pesada
                    if (summary.nextTime.take(6).any { it.needsRpe }) {
                        Text(stringResource(R.string.summary_next_needs_rpe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SessionRatingPicker(onRate)
                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null)
                    Text(stringResource(R.string.share_workout), Modifier.padding(start = 8.dp))
                }
                // Series y RPE en texto, para mandárselo al entrenador o al grupo (sin ir al historial)
                TextButton(onClick = onShareText, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_as_text)) }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text(stringResource(R.string.action_done)) } },
        dismissButton = when {
            w.routineId == null -> ({ TextButton(onClick = onSaveRoutine) { Text(stringResource(R.string.save_as_routine)) } })
            // El entreno cambió la rutina (ejercicios, series, descansos): se ofrece guardar esos cambios en ella
            summary.routineToUpdate != null -> ({ TextButton(onClick = onUpdateRoutine) { Text(stringResource(R.string.update_routine, summary.routineToUpdate.name)) } })
            else -> null
        },
    )
}

/** «Press banca · 80 kg × 8»: la siguiente serie por hacer, para la barra de descanso. */
@Composable
private fun nextSetLabel(w: com.bestrong.core.model.Workout, dumbbellTotal: Boolean): String? {
    val unit = LocalWeightUnit.current
    val next = WorkoutEditor.upcomingSet(w)
    val ex = next?.let { (i, _) -> w.exercises.getOrNull(i)?.exercise }
    // Con «suma de las dos mancuernas», el peso del par (como en la fila de la serie)
    val factor = if (dumbbellTotal && ex?.equipment == Equipment.DUMBBELL) 2 else 1
    return if (next == null || ex == null) null else ex.displayName() + " · " + describeSet(next.second, ex, unit, factor)
}

/** « · hace 12 d»: cuánto hace de la última vez (tras un parón se empieza más ligero); vacío si fue hoy o no consta. */
@Composable
private fun lastDoneSuffix(previous: List<WorkoutSet>): String {
    val days = remember(previous) {
        previous.firstNotNullOfOrNull { it.completedAtEpochMs }?.let { ms ->
            val zone = java.time.ZoneId.systemDefault()
            java.time.temporal.ChronoUnit.DAYS.between(java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(), java.time.LocalDate.now(zone))
        }
    }
    return if (days != null && days > 0) " · " + stringResource(R.string.muscle_days_ago, days.toInt()) else ""
}

/** Ejercicios con series sin marcar («Press banca · 2/3»), para ir a ellos antes de terminar. */
@Composable
private fun PendingExercises(w: Workout, onJump: (Int) -> Unit) {
    w.exercises.withIndex().filter { (_, we) -> we.sets.any { !it.completed } }.take(MAX_PENDING_SHOWN).forEach { (i, we) ->
        TextButton(onClick = { onJump(i) }) {
            Text("${we.exercise.displayName()} · ${we.sets.count { it.completed }}/${we.sets.size}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private const val MAX_PENDING_SHOWN = 5

/** Ejercicio terminado, plegado en una línea: imagen, nombre, series hechas y la mejor; tocar lo despliega. */
@Composable
private fun DoneExerciseRow(we: WorkoutExercise, onOpen: () -> Unit) {
    val unit = LocalWeightUnit.current
    val best = we.completedSets.maxByOrNull { it.estimatedOneRepMax }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClickLabel = stringResource(R.string.exercise_show_sets), onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        com.bestrong.mobile.ui.components.ExerciseThumb(we.exercise, 40.dp, enlargeOnTap = false)
        Column(Modifier.weight(1f)) {
            Text(we.exercise.displayName(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            best?.let { Text(describeSet(it, we.exercise, unit, com.bestrong.mobile.ui.components.pairFactor(we.exercise)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        com.bestrong.mobile.ui.components.MetaPill("✓ ${we.sets.size}/${we.sets.size}", Modifier.spokenAs(stringResource(R.string.achievements_count, we.sets.size, we.sets.size)))
    }
}

/** 2 con «suma de las dos mancuernas» en un ejercicio con mancuernas (como en las filas); si no, 1. */
private fun pairFactor(exercise: com.bestrong.core.model.Exercise?, dumbbellTotal: Boolean): Int =
    if (dumbbellTotal && exercise?.equipment == Equipment.DUMBBELL) 2 else 1
