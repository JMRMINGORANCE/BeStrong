package com.bestrong.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.util.formatDuration
import com.bestrong.wear.R
import com.bestrong.wear.WearContainer
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(container: WearContainer, onStart: () -> Unit, onCardio: () -> Unit = {}, onCreateRoutine: () -> Unit = {}) {
    val routines by container.store.routines.collectAsStateWithLifecycle()
    val history by container.store.history.collectAsStateWithLifecycle()
    val active by container.session.workout.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val listState = rememberScalingLazyListState()
    val zone = ZoneId.systemDefault()
    val thisWeek = WorkoutStats.weeklySummary(history, 1, LocalDate.now(zone), zone).first().workouts
    val streak = WorkoutStats.weeklyStreak(history, LocalDate.now(zone), zone)
    // La de hoy (como el tile «Hoy toca»), arriba del todo: sin girar la corona para buscarla
    val today = androidx.compose.runtime.remember(routines, history) { container.store.todayRoutine() }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text("BeStrong", color = Brand, style = MaterialTheme.typography.titleLarge) } }
            item {
                Text(
                    pluralStringResource(R.plurals.week_summary, thisWeek, thisWeek, streak),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
            active?.let { w ->
                item {
                    Button(
                        onClick = onStart,
                        label = { Text(stringResource(R.string.continue_)) },
                        secondaryLabel = { Text(w.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (active == null) {
                today?.let { r ->
                    item {
                        Button(
                            onClick = { container.session.startRoutine(r); onStart() },
                            label = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text(stringResource(R.string.tile_today)) },
                            icon = { Text("▶", Modifier.clearAndSetSemantics { }) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    Button(
                        onClick = { container.session.startEmpty(); onStart() },
                        colors = if (today != null) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
                        label = { Text(stringResource(R.string.empty_workout)) },
                        icon = { Text("＋", Modifier.clearAndSetSemantics { }) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Button(
                        onClick = onCardio,
                        label = { Text(stringResource(R.string.cardio)) },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { ListHeader { Text(stringResource(R.string.routines)) } }
                if (routines.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.create_routines_on_phone),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                item {
                    Button(
                        onClick = onCreateRoutine,
                        label = { Text(stringResource(R.string.create_routine)) },
                        colors = ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                items(routines, key = { it.id }) { routine ->
                    Button(
                        onClick = { container.session.startRoutine(routine); onStart() },
                        label = { Text(routine.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = {
                            // Ejercicios y tiempo aproximado (series × 40 s + descansos)
                            val minutes = remember(routine) { (WorkoutEditor.estimatedRemainingSeconds(routine.toWorkout(0)) + 59) / 60 }
                            val exercises = pluralStringResource(R.plurals.n_exercises, routine.exercises.size, routine.exercises.size)
                            Text(if (minutes > 0) stringResource(R.string.routine_minutes_suffix, exercises, minutes) else exercises)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            history.firstOrNull()?.let { last ->
                item { ListHeader { Text(stringResource(R.string.last)) } }
                item {
                    Button(
                        onClick = {},
                        enabled = false,
                        label = { Text(last.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = { Text("${formatDuration(last.durationSeconds())} · ${unit.formatVolume(last.totalVolume)}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
