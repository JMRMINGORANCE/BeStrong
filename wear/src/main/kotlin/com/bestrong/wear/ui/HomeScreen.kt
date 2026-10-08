package com.bestrong.wear.ui

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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatVolume
import com.bestrong.wear.WearContainer
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(container: WearContainer, onStart: () -> Unit) {
    val routines by container.store.routines.collectAsStateWithLifecycle()
    val history by container.store.history.collectAsStateWithLifecycle()
    val active by container.session.workout.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val zone = ZoneId.systemDefault()
    val thisWeek = WorkoutStats.weeklySummary(history, 1, LocalDate.now(zone), zone).first().workouts
    val streak = WorkoutStats.weeklyStreak(history, LocalDate.now(zone), zone)

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text("BeStrong", color = Brand, style = MaterialTheme.typography.title2) } }
            item {
                Text(
                    "$thisWeek entrenos esta semana · 🔥 $streak",
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
            }
            active?.let { w ->
                item {
                    Chip(
                        onClick = onStart,
                        label = { Text("Continuar") },
                        secondaryLabel = { Text(w.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = ChipDefaults.gradientBackgroundChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (active == null) {
                item {
                    Chip(
                        onClick = { container.session.startEmpty(); onStart() },
                        label = { Text("Entrenamiento vacío") },
                        icon = { Text("＋") },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { ListHeader { Text("Rutinas") } }
                if (routines.isEmpty()) {
                    item {
                        Text(
                            "Crea rutinas en el móvil y aparecerán aquí",
                            style = MaterialTheme.typography.caption3,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                items(routines, key = { it.id }) { routine ->
                    Chip(
                        onClick = { container.session.startRoutine(routine); onStart() },
                        label = { Text(routine.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = { Text("${routine.exercises.size} ejercicios") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            history.firstOrNull()?.let { last ->
                item { ListHeader { Text("Último") } }
                item {
                    Chip(
                        onClick = {},
                        enabled = false,
                        label = { Text(last.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = { Text("${formatDuration(last.durationSeconds())} · ${formatVolume(last.totalVolume)} kg") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
