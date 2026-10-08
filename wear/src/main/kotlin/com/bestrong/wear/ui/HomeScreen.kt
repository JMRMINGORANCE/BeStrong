package com.bestrong.wear.ui

import com.bestrong.wear.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.util.formatDuration
import com.bestrong.wear.WearContainer
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(container: WearContainer, onStart: () -> Unit) {
    val routines by container.store.routines.collectAsStateWithLifecycle()
    val history by container.store.history.collectAsStateWithLifecycle()
    val active by container.session.workout.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val listState = rememberScalingLazyListState()
    val zone = ZoneId.systemDefault()
    val thisWeek = WorkoutStats.weeklySummary(history, 1, LocalDate.now(zone), zone).first().workouts
    val streak = WorkoutStats.weeklyStreak(history, LocalDate.now(zone), zone)

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text("BeStrong", color = Brand, style = MaterialTheme.typography.titleLarge) } }
            item {
                Text(
                    stringResource(R.string.week_summary, thisWeek, streak),
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
                item {
                    Button(
                        onClick = { container.session.startEmpty(); onStart() },
                        label = { Text(stringResource(R.string.empty_workout)) },
                        icon = { Text("＋") },
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
                items(routines, key = { it.id }) { routine ->
                    Button(
                        onClick = { container.session.startRoutine(routine); onStart() },
                        label = { Text(routine.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = { Text(stringResource(R.string.n_exercises, routine.exercises.size)) },
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
