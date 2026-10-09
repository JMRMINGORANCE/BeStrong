package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.Workout
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.R
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.workout.RestTimerController
import kotlinx.coroutines.delay

/**
 * Modo una mano (pantalla exterior del Fold): completar la siguiente serie y terminar,
 * abajo y en grande, al alcance del pulgar.
 */
@Composable
fun OneHandedBar(w: Workout, vm: ActiveWorkoutViewModel, onFinish: () -> Unit) {
    val next = WorkoutEditor.nextPendingSet(w)
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (next != null) {
                val (index, set) = next
                val we = w.exercises[index]
                val setNumber = we.sets.indexOfFirst { it.id == set.id } + 1
                Button(
                    onClick = { vm.toggleSet(we.id, set.id) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Default.Check, null)
                    Text(
                        stringResource(R.string.one_hand_complete, we.exercise.displayName(), setNumber),
                        Modifier.padding(start = 8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OutlinedButton(onClick = onFinish, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.workout_finish)) }
        }
    }
}

/** Aviso de entreno en pausa (el cronómetro no avanza). */
@Composable
fun PausedBanner(onResume: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = onResume)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.workout_paused), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Icon(Icons.Default.PlayArrow, null)
            Text(stringResource(R.string.workout_resume), Modifier.padding(start = 4.dp))
        }
    }
}

/** Aviso de descanso excesivo: aparece cuando ha pasado más de un minuto desde que acabó el descanso. */
@Composable
fun RestOverrunBanner(endedAt: Long?, onDismiss: () -> Unit) {
    if (endedAt == null) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsed = ((now - endedAt) / 1000).coerceAtLeast(0)
    if (elapsed * 1000 < RestTimerController.OVERRUN_ALERT_MS) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rest_overrun, formatDuration(elapsed)), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.rest_overrun_action), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Cambiar de ejercicio por otro equivalente (mismo músculo) sin perder la progresión. */
@Composable
fun SubstituteDialog(vm: ActiveWorkoutViewModel, weId: String, onDismiss: () -> Unit) {
    val alternatives = remember(weId) { vm.alternatives(weId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.substitute_title)) },
        text = {
            Column {
                Text(stringResource(R.string.substitute_body), style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(alternatives, key = { it.id }) { ex ->
                        ListItem(
                            headlineContent = { Text(ex.displayName()) },
                            supportingContent = { Text(ex.equipment.label()) },
                            modifier = Modifier.clickable {
                                vm.substitute(weId, ex.id)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
