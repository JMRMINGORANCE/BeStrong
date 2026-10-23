package com.bestrong.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.shared.R

/**
 * Series que ha recibido cada músculo en este entreno («Pecho 8 · Tríceps 5»): las mismas cuentas que la semana
 * (sin calentamientos ni cardio; los secundarios cuentan media). Nada si no hay series de fuerza.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutMuscleChips(w: Workout, modifier: Modifier = Modifier) {
    val perMuscle = remember(w) { WorkoutStats.setsPerMuscle(listOf(w), 0L).entries.sortedByDescending { it.value }.take(MAX_CHIPS) }
    if (perMuscle.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.routine_muscles_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            perMuscle.forEach { (muscle, sets) -> MetaPill(muscle.label() + " " + formatSets(sets)) }
        }
    }
}

/** 8 o 7,5 (sin «,0»), con el separador decimal del idioma. */
private fun formatSets(sets: Double): String = "%.1f".format(sets).removeSuffix(",0").removeSuffix(".0")

private const val MAX_CHIPS = 8
