package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.Exercise
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatDate

/** Últimas sesiones del ejercicio sin salir del entreno: series, mejor 1RM estimado y la nota de ese día. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistorySheet(vm: ActiveWorkoutViewModel, exercise: Exercise, weightFactor: Int, onDismiss: () -> Unit) {
    val unit = LocalWeightUnit.current
    val sessions = remember(exercise.id) { vm.historyFor(exercise.id) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(exercise.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (sessions.isEmpty()) Text(stringResource(R.string.exercise_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions, key = { it.first.id }) { (w, we) ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(formatDate(w.startedAtEpochMs), fontWeight = FontWeight.SemiBold)
                        Text(
                            we.completedSets.joinToString(" · ") { describeSet(it, exercise, unit, weightFactor) },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        // El 1RM solo tiene sentido con peso (en dominadas lastradas saldría del lastre)
                        we.completedSets.maxOfOrNull { it.estimatedOneRepMax }?.takeIf { it > 0 && exercise.tracking == com.bestrong.core.model.TrackingType.WEIGHT_REPS }?.let {
                            Text(stringResource(R.string.one_rm_value, unit.format(it * weightFactor)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (we.notes.isNotBlank()) Text("✎ " + we.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}
