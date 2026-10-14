package com.bestrong.mobile.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.Strength
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.formatDate

/**
 * Objetivo propio del ejercicio («press banca a 100 kg» de 1RM estimado): barra de progreso, lo que
 * falta y cuándo llegarías al ritmo actual. Al alcanzarlo, se celebra.
 */
@Composable
fun ExerciseGoalCard(vm: ExercisesViewModel, s: ExerciseDetailState) {
    val unit = LocalWeightUnit.current
    val goals by vm.goals.collectAsStateWithLifecycle()
    val goal = goals[s.exercise.id]
    var editing by rememberSaveable { mutableStateOf(false) }
    if (editing) GoalDialog(goal?.let { unit.format(it) }.orEmpty(), { text -> vm.setGoal(s.exercise.id, unit.parseToKg(text)?.takeIf { it > 0 }); editing = false }) { editing = false }
    val best = s.records[RecordType.BEST_ONE_REP_MAX]?.value ?: 0.0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.goal_title), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { editing = true }) { Text(stringResource(if (goal == null) R.string.goal_set else R.string.action_edit)) }
            }
            if (goal == null) {
                Text(stringResource(R.string.goal_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            LinearProgressIndicator(progress = { (best / goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            val eta = remember(s.progress, goal) { Strength.predictDate(s.progress.map { it.dateEpochMs to it.bestOneRepMax }, goal) }
            val line = when {
                best >= goal -> stringResource(R.string.goal_reached, unit.format(goal, withUnit = true))
                eta != null -> stringResource(R.string.goal_progress_eta, unit.format(best), unit.format(goal, withUnit = true), formatDate(eta))
                else -> stringResource(R.string.goal_progress, unit.format(best), unit.format(goal, withUnit = true))
            }
            Text(line, style = MaterialTheme.typography.bodyMedium, color = if (best >= goal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun GoalDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val unit = LocalWeightUnit.current
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_title)) },
        text = {
            OutlinedTextField(
                text,
                { t -> text = t.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                label = { Text(stringResource(R.string.goal_field, unit.label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) } },
        // Guardar vacío quita el objetivo
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
