package com.bestrong.mobile.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.TrackingType
import com.bestrong.core.stats.Strength
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.formatDate

/**
 * Objetivo propio del ejercicio («press banca a 100 kg» de 1RM, «15 dominadas», «plancha de 2 min»):
 * barra de progreso, lo que llevas y cuándo llegarías al ritmo actual. Al alcanzarlo, se celebra.
 */
@Composable
fun ExerciseGoalCard(vm: ExercisesViewModel, s: ExerciseDetailState) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val goal = goals[s.exercise.id]
    var editing by rememberSaveable { mutableStateOf(false) }
    val tracking = s.exercise.tracking
    val byWeight = tracking == TrackingType.WEIGHT_REPS
    // Mancuernas con «suma de las dos»: todo se ve y se escribe como el total (se guarda por mancuerna)
    val factor = if (byWeight) vm.weightFactor(s.exercise) else 1
    val show = goalFormatter(tracking, factor)
    if (editing) GoalEditor(vm, s, goal, factor) { editing = false }
    val best = s.goalPoints.maxOfOrNull { it.second } ?: 0.0
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.goal_title), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { editing = true }) { Text(stringResource(if (goal == null) R.string.goal_set else R.string.action_edit)) }
            }
            if (goal == null) {
                val hint = when (tracking) {
                    TrackingType.WEIGHT_REPS -> R.string.goal_hint
                    TrackingType.DURATION -> R.string.goal_hint_time
                    else -> R.string.goal_hint_reps
                }
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            LinearProgressIndicator(progress = { (best / goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            val eta = remember(s.goalPoints, goal) { Strength.predictDate(s.goalPoints, goal) }
            val line = when {
                best >= goal -> stringResource(R.string.goal_reached, show(goal))
                eta != null -> stringResource(R.string.goal_progress_now_eta, show(best), show(goal), formatDate(eta))
                else -> stringResource(R.string.goal_progress_now, show(best), show(goal))
            }
            Text(line, style = MaterialTheme.typography.bodyMedium, color = if (best >= goal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Cómo se escribe un valor del objetivo: peso (con el total de mancuernas), tiempo o reps. */
@Composable
private fun goalFormatter(tracking: TrackingType, factor: Int): (Double) -> String {
    val unit = LocalWeightUnit.current
    val repsLabel = stringResource(R.string.reps_short)
    return { v ->
        when (tracking) {
            TrackingType.WEIGHT_REPS -> unit.format(v * factor, withUnit = true)
            TrackingType.DURATION -> formatDuration(v.toLong())
            else -> "${v.toInt()} $repsLabel"
        }
    }
}

/** Diálogo para poner, cambiar o quitar el objetivo con la unidad adecuada a cada ejercicio. */
@Composable
private fun GoalEditor(vm: ExercisesViewModel, s: ExerciseDetailState, goal: Double?, factor: Int, onClose: () -> Unit) {
    val unit = LocalWeightUnit.current
    val byWeight = s.exercise.tracking == TrackingType.WEIGHT_REPS
    val initial = goal?.let { if (byWeight) unit.format(it * factor) else it.toInt().toString() }.orEmpty()
    GoalDialog(
        initial = initial,
        label = when (s.exercise.tracking) {
            TrackingType.WEIGHT_REPS -> stringResource(R.string.goal_field, unit.label)
            TrackingType.DURATION -> stringResource(R.string.goal_field_seconds)
            else -> stringResource(R.string.goal_field_reps)
        },
        // Sin tocar el texto se guarda el objetivo exacto: 100 kg visto en libras (220,5) no pasa a 100,017
        parse = { t ->
            if (goal != null && t == initial) {
                goal
            } else if (byWeight) {
                unit.parseToKg(t)?.let { it / factor }
            } else {
                t.toIntOrNull()?.toDouble()
            }
        },
        decimal = byWeight,
        hasGoal = goal != null,
        onSave = { v -> vm.setGoal(s.exercise.id, v); onClose() },
        onDismiss = onClose,
    )
}

/** [onSave] recibe el objetivo ya convertido por [parse] (null = quitarlo). Guardar solo con un número válido. */
@Composable
private fun GoalDialog(initial: String, label: String, parse: (String) -> Double?, decimal: Boolean, hasGoal: Boolean, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val value = parse(text)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_title)) },
        text = {
            OutlinedTextField(
                text,
                { t -> text = t.filter { it.isDigit() || (decimal && (it == ',' || it == '.')) }.take(6) },
                label = { Text(label) },
                singleLine = true,
                isError = text.isNotEmpty() && value == null,
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = value != null) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            Row {
                if (hasGoal) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.goal_remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
