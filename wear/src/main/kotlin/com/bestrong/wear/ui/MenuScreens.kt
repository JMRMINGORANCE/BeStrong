package com.bestrong.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.Workout
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.util.formatDuration
import com.bestrong.wear.R
import com.bestrong.wear.WearContainer
import com.bestrong.wear.session.StemAction
import com.bestrong.wear.session.WorkoutSession

@Composable
fun MenuScreen(
    session: WorkoutSession,
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    onInfo: () -> Unit,
    onDiscarded: () -> Unit,
    onCalibrate: () -> Unit = {},
) {
    val workout by session.workout.collectAsStateWithLifecycle()
    val index by session.exerciseIndex.collectAsStateWithLifecycle()
    val repCounting by session.repCounting.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val listState = rememberScalingLazyListState()
    var confirmDiscard by remember { mutableStateOf(false) }
    val lowPower by session.lowPower.collectAsStateWithLifecycle()
    var maxHr by remember { mutableIntStateOf(session.maxHeartRate) }
    val voice = rememberVoiceInput(stringResource(R.string.voice_prompt)) { text -> if (session.applySpoken(text)) onBack() }
    val w = workout ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasWeight = w.exercises.getOrNull(index)?.sets?.any { it.weightKg > 0 } == true
    val warnIfNot: (Boolean) -> Unit = { added -> warnIfNotLighter(context, added, hasWeight) }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = session::finish) { Text(stringResource(R.string.finish)) }
        },
    ) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text(w.name, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            item {
                Text(
                    stringResource(R.string.sets_volume, w.completedSetCount, unit.formatVolume(w.totalVolume)),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
            // Lo que queda (con el descanso en marcha) y a qué hora se acabaría
            item { RemainingEta(session, w) }
            item { ListHeader { Text(stringResource(R.string.exercises)) } }
            itemsIndexed(w.exercises, key = { _, we -> we.id }) { i, we ->
                val done = we.sets.count { it.completed }
                Button(
                    onClick = { session.selectExercise(i); onBack() },
                    label = { Text(we.exercise.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(stringResource(R.string.done_of_sets, done, we.sets.size) + if (i == index) stringResource(R.string.current_suffix) else "") },
                    colors = if (done == we.sets.size) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = onInfo,
                    label = { Text(stringResource(R.string.menu_progress)) },
                    secondaryLabel = { Text(stringResource(R.string.menu_progress_sub)) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            voice?.let { launch ->
                item {
                    Button(onClick = launch, label = { Text(stringResource(R.string.voice_set)) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                Button(onClick = { session.addWarmup(); onBack() }, label = { Text(stringResource(R.string.menu_warmup)) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
            }
            // Drop set y series de descarga, como en el móvil (solo ejercicios con peso)
            if (w.exercises.getOrNull(index)?.exercise?.tracking == com.bestrong.core.model.TrackingType.WEIGHT_REPS) {
                item {
                    Button(onClick = { warnIfNot(session.addDropSet()); onBack() }, label = { Text(stringResource(R.string.menu_drop_set)) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
                }
                item {
                    Button(onClick = { warnIfNot(session.addBackOff()); onBack() }, label = { Text(stringResource(R.string.menu_back_off)) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                Button(onClick = { session.addSet(); onBack() }, label = { Text(stringResource(R.string.add_set)) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
            }
            setCorrections(w, index, session, onBack)
            item {
                Button(onClick = onAddExercise, label = { Text(stringResource(R.string.add_exercise)) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
            }
            item {
                val stem by session.stemAction.collectAsStateWithLifecycle()
                Button(
                    onClick = session::cycleStemAction,
                    label = { Text(stringResource(R.string.stem_button)) },
                    secondaryLabel = {
                        Text(
                            stringResource(
                                when (stem) {
                                    StemAction.COMPLETE_SET -> R.string.stem_complete
                                    StemAction.SKIP_REST -> R.string.stem_skip_rest
                                    StemAction.NEXT_EXERCISE -> R.string.stem_next
                                },
                            ),
                        )
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (session.repCounter.isAvailable) {
                item {
                    SwitchButton(
                        checked = repCounting,
                        onCheckedChange = session::setRepCounting,
                        label = { Text(stringResource(R.string.count_reps)) },
                        secondaryLabel = { Text(stringResource(R.string.with_accelerometer)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (session.repCounter.isAvailable) {
                item {
                    Button(onClick = onCalibrate, label = { Text(stringResource(R.string.calibrate)) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                Button(
                    onClick = { maxHr = session.cycleMaxHeartRate() },
                    label = { Text(stringResource(R.string.max_hr)) },
                    secondaryLabel = { Text(stringResource(R.string.max_hr_value, maxHr)) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SwitchButton(
                    checked = lowPower,
                    onCheckedChange = session::setLowPower,
                    label = { Text(stringResource(R.string.low_power)) },
                    secondaryLabel = { Text(stringResource(R.string.low_power_sub)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = { if (confirmDiscard) { session.discard(); onDiscarded() } else confirmDiscard = true },
                    label = { Text(stringResource(if (confirmDiscard) R.string.tap_again_discard else R.string.discard_workout)) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** «≈ 25 min · fin 18:45»: lo que queda, con el descanso en marcha. */
@Composable
private fun RemainingEta(session: WorkoutSession, w: Workout) {
    val rest by session.rest.collectAsStateWithLifecycle()
    val settings by session.settings.collectAsStateWithLifecycle()
    // Se recalcula cada 30 s mientras el menú sigue abierto
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val remaining = WorkoutEditor.estimatedRemainingSeconds(w, nowEpochMs = now, warmupRestSeconds = settings.warmupRestSeconds, restEndsAtEpochMs = rest?.endsAtEpochMs)
    if (remaining <= 0) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), if (android.text.format.DateFormat.is24HourFormat(context)) "Hm" else "hm")
    val ends = java.time.format.DateTimeFormatter.ofPattern(pattern)
        .format(java.time.Instant.ofEpochMilli(now + remaining * 1000L).atZone(java.time.ZoneId.systemDefault()))
    Text(stringResource(R.string.remaining_eta, (remaining + 59) / 60, ends), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
}

/** Selector de ejercicios en el reloj: recientes primero, luego por grupo muscular. */
@Composable
fun ExercisePickerScreen(container: WearContainer, onPick: (Exercise) -> Unit) {
    val history by container.store.history.collectAsStateWithLifecycle()
    val routines by container.store.routines.collectAsStateWithLifecycle()
    var muscle by remember { mutableStateOf<MuscleGroup?>(null) }
    val listState = rememberScalingLazyListState()
    // Ejercicios personalizados del móvil llegan dentro del historial/rutinas
    val known = (
        ExerciseLibrary.all +
            routines.flatMap { r -> r.exercises.map { it.exercise } } +
            history.flatMap { w -> w.exercises.map { it.exercise } }
        ).distinctBy { it.id }
    val recent = history.flatMap { w -> w.exercises.map { it.exercise } }.distinctBy { it.id }.take(6)

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
            val m = muscle
            if (m == null) {
                if (recent.isNotEmpty()) {
                    item { ListHeader { Text(stringResource(R.string.recent)) } }
                    items(recent, key = { "r-" + it.id }) { ex ->
                        Button(onClick = { onPick(ex) }, label = { Text(ex.displayName(), maxLines = 2) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
                    }
                }
                item { ListHeader { Text(stringResource(R.string.muscle_group)) } }
                items(MuscleGroup.entries.filter { g -> known.any { it.primaryMuscle == g } }, key = { it.name }) { g ->
                    Button(onClick = { muscle = g }, label = { Text(g.label()) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
                }
            } else {
                item {
                    Button(onClick = { muscle = null }, label = { Text(stringResource(R.string.back_to, m.label())) }, colors = ButtonDefaults.outlinedButtonColors(), modifier = Modifier.fillMaxWidth())
                }
                items(known.filter { it.primaryMuscle == m }.sortedBy { it.id }, key = { it.id }) { ex ->
                    Button(
                        onClick = { onPick(ex) },
                        label = { Text(ex.displayName(), maxLines = 2) },
                        secondaryLabel = { Text(ex.equipment.label()) },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryScreen(session: WorkoutSession, onDone: () -> Unit) {
    val summary by session.summary.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val s = summary ?: return
    val w = s.workout
    val listState = rememberScalingLazyListState()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = { EdgeButton(onClick = onDone) { Text(stringResource(R.string.done)) } },
    ) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text(stringResource(R.string.completed), color = Brand) } }
            item { Text(w.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
            item { Text(stringResource(R.string.summary_duration, formatDuration(w.durationSeconds()))) }
            item { Text(stringResource(R.string.summary_volume, unit.formatVolume(w.totalVolume), w.completedSetCount)) }
            w.avgHeartRate?.let { item { Text(stringResource(R.string.summary_hr, it, w.maxHeartRate?.toString() ?: "--"), color = HeartRed) } }
            w.calories?.let { item { Text(stringResource(R.string.summary_kcal, it)) } }
            if (s.comparisons.isNotEmpty()) {
                item { ListHeader { Text(stringResource(R.string.vs_last)) } }
                items(s.comparisons.take(5)) { c ->
                    val name = ExerciseLibrary.byId(c.exerciseId)?.displayName() ?: c.exerciseName
                    Text(
                        stringResource(R.string.vs_line, name, (if (c.volumeDelta >= 0) "+" else "−") + unit.formatVolume(kotlin.math.abs(c.volumeDelta))),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (c.volumeDelta >= 0) Done else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (s.records.isNotEmpty()) {
                item { ListHeader { Text(stringResource(R.string.n_records, s.records.size)) } }
                items(s.records) { pr ->
                    Text(
                        "${pr.exerciseName}: ${unit.format(pr.value)}",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item { Text(stringResource(R.string.syncs_with_phone), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** Correcciones: quitar una serie pendiente, el RPE de la última serie y deshacer la última marcada. */
private fun ScalingLazyListScope.setCorrections(w: Workout, index: Int, session: WorkoutSession, onBack: () -> Unit) {
    if (w.exercises.getOrNull(index)?.sets?.any { !it.completed } == true) {
        item {
            Button(onClick = { if (session.removePendingSet()) onBack() }, label = { Text(stringResource(R.string.remove_set)) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
        }
    }
    // RPE de la última serie normal del ejercicio: cada toque pasa al siguiente (6…10 y «—»)
    w.exercises.getOrNull(index)?.takeIf { it.exercise.tracking == com.bestrong.core.model.TrackingType.WEIGHT_REPS }
        ?.sets?.lastOrNull { it.completed && it.type == com.bestrong.core.model.SetType.NORMAL }?.let { last ->
            item {
                Button(
                    onClick = { session.cycleLastSetRpe() },
                    label = { Text(stringResource(R.string.last_set_rpe, last.rpe?.toString()?.removeSuffix(".0") ?: "—")) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    if (w.completedSetCount > 0) {
        item {
            Button(onClick = { if (session.undoLastSet()) onBack() }, label = { Text(stringResource(R.string.undo_last_set)) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Si no se pudo bajar más el peso, se dice (si no, el menú se cerraría sin hacer nada). */
private fun warnIfNotLighter(context: android.content.Context, added: Boolean, hasWeight: Boolean) {
    // Sin peso apuntado todavía no hay de dónde bajar: se dice eso
    if (!added) android.widget.Toast.makeText(context, if (hasWeight) R.string.cannot_go_lighter else R.string.enter_weight_first, android.widget.Toast.LENGTH_SHORT).show()
}
