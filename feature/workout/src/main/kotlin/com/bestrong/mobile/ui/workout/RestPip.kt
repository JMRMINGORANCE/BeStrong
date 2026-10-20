package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.rememberNow
import com.bestrong.mobile.workout.RestTimerController

/** Ventana flotante (picture-in-picture): cuenta atrás del descanso y siguiente serie. */
@Composable
fun RestPip(repository: BeStrongRepository, restTimer: RestTimerController, unit: com.bestrong.core.util.WeightUnit, dumbbellTotal: Boolean) {
    val workout by repository.activeWorkout.collectAsStateWithLifecycle()
    val rest by restTimer.rest.collectAsStateWithLifecycle()
    val now = rememberNow(500)
    val w = workout
    Box(Modifier.fillMaxSize().background(com.bestrong.mobile.ui.theme.LocalScreenColor.current).padding(8.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val r = rest
            if (r != null) {
                val remaining = ((r.endsAtEpochMs - now) / 1000).coerceAtLeast(0)
                Text(formatDuration(remaining), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(progress = { if (r.totalSeconds > 0) remaining.toFloat() / r.totalSeconds else 0f })
            } else if (w != null) {
                Text(formatDuration(w.durationSeconds(now)), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            w?.let { wk ->
                // Fuera del árbol de la app (sin su unidad): en tu unidad y con mancuernas como total, como la notificación
                WorkoutEditor.nextPendingSet(wk)?.let { (i, s) ->
                    val we = wk.exercises[i]
                    Text(
                        stringResource(R.string.pip_next, we.exercise.displayName() + " · " + describeSet(s, we.exercise, unit, if (dumbbellTotal && we.exercise.equipment == com.bestrong.core.model.Equipment.DUMBBELL) 2 else 1)),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
