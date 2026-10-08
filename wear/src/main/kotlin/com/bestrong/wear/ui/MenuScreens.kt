package com.bestrong.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
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
import com.bestrong.core.util.formatDuration
import com.bestrong.wear.R
import com.bestrong.wear.WearContainer
import com.bestrong.wear.session.WorkoutSession

@Composable
fun MenuScreen(session: WorkoutSession, onBack: () -> Unit, onAddExercise: () -> Unit, onDiscarded: () -> Unit) {
    val workout by session.workout.collectAsStateWithLifecycle()
    val index by session.exerciseIndex.collectAsStateWithLifecycle()
    val repCounting by session.repCounting.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val listState = rememberScalingLazyListState()
    var confirmDiscard by remember { mutableStateOf(false) }
    val w = workout ?: return

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
                Button(onClick = { session.addSet(); onBack() }, label = { Text(stringResource(R.string.add_set)) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
            }
            item {
                Button(onClick = onAddExercise, label = { Text(stringResource(R.string.add_exercise)) }, colors = ButtonDefaults.filledTonalButtonColors(), modifier = Modifier.fillMaxWidth())
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
