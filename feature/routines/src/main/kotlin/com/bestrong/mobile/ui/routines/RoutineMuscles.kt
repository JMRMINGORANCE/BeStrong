package com.bestrong.mobile.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.Routine
import com.bestrong.core.stats.MuscleTargets
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.label

/**
 * Series por músculo de la rutina mientras se edita («Pecho 9 · Espalda 6»). Más de lo que se
 * recomienda a la semana en una sola sesión se marca en rojo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineMusclesCard(routine: Routine, modifier: Modifier = Modifier) {
    val perMuscle = remember(routine) { WorkoutStats.routineSetsPerMuscle(routine) }
    if (perMuscle.isEmpty()) return
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.routine_muscles_title), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                perMuscle.forEach { (muscle, sets) ->
                    val tooMuch = sets > MuscleTargets.MAX_WEEKLY_SETS
                    // Solo informativo: no es un botón
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            muscle.label() + " " + "%.1f".format(sets).removeSuffix(",0").removeSuffix(".0"),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (tooMuch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Text(stringResource(R.string.routine_muscles_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
