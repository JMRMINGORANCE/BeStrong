package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.training.GymProfile
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit

/** Barras habituales (kg): olímpica, de mujer, EZ, hexagonal y técnica. */
private val BAR_CHOICES_KG = listOf(20.0, 15.0, 10.0, 25.0, 7.5)

/**
 * Barra de este ejercicio (curl con barra EZ, peso muerto con hexagonal…): la usan los discos por lado,
 * la calculadora y el calentamiento. «La del gimnasio» vuelve a la del perfil activo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseBarDialog(vm: ActiveWorkoutViewModel, exerciseId: String, onDismiss: () -> Unit) {
    val unit = LocalWeightUnit.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val own = settings.exerciseBarKg[exerciseId]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exercise_bar)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.exercise_bar_body), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = own == null,
                        onClick = { vm.setExerciseBar(exerciseId, null) },
                        label = { Text(stringResource(R.string.exercise_bar_gym, unit.format(unit.toKg(GymProfile.barIn(unit, settings.activeGym.barKg)), withUnit = true))) },
                    )
                    BAR_CHOICES_KG.forEach { kg ->
                        FilterChip(selected = own == kg, onClick = { vm.setExerciseBar(exerciseId, kg) }, label = { Text(unit.format(unit.toKg(GymProfile.barIn(unit, kg)), withUnit = true)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.got_it)) } },
    )
}

/**
 * Rango de repeticiones del ejercicio (doble progresión): se sube una rep por serie hasta el tope y entonces
 * peso, volviendo al mínimo. «Sin rango» deja la progresión de siempre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepRangeDialog(vm: ActiveWorkoutViewModel, exerciseId: String, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val current = settings.exerciseRepRange[exerciseId]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rep_range)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.rep_range_body), style = MaterialTheme.typography.bodySmall)
                com.bestrong.mobile.ui.components.RepRangeChips(current) { vm.setRepRange(exerciseId, it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.got_it)) } },
    )
}
