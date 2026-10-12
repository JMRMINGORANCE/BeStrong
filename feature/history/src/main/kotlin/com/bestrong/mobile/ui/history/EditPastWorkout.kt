package com.bestrong.mobile.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.WorkoutSet
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit

/** Corrige una serie de un entreno ya guardado (peso, reps, segundos o metros) o la borra. */
@Composable
fun EditPastSetDialog(set: WorkoutSet, tracking: TrackingType, onSave: (WorkoutSet) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val unit = LocalWeightUnit.current
    var weight by remember { mutableStateOf(if (set.weightKg == 0.0) "" else unit.format(set.weightKg)) }
    var reps by remember { mutableStateOf(set.reps.toString()) }
    var seconds by remember { mutableStateOf(set.durationSec.toString()) }
    var meters by remember { mutableStateOf(set.distanceM.toInt().toString()) }
    val hasWeight = tracking != TrackingType.DURATION
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_edit_set)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasWeight) {
                    OutlinedTextField(
                        weight,
                        { weight = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '-' }.take(7) },
                        label = { Text(stringResource(R.string.set_weight_in, unit.label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                when (tracking) {
                    TrackingType.WEIGHT_REPS, TrackingType.REPS_ONLY -> OutlinedTextField(
                        reps,
                        { reps = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.set_reps)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    TrackingType.DURATION -> OutlinedTextField(
                        seconds,
                        { seconds = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.set_seconds)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    TrackingType.DISTANCE -> OutlinedTextField(
                        meters,
                        { meters = it.filter(Char::isDigit).take(6) },
                        label = { Text(stringResource(R.string.set_meters)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kg = when {
                    !hasWeight || weight.isBlank() -> 0.0
                    weight.startsWith("-") -> -(unit.parseToKg(weight.drop(1)) ?: 0.0)
                    else -> unit.parseToKg(weight) ?: set.weightKg
                }
                onSave(
                    set.copy(
                        weightKg = kg,
                        reps = reps.toIntOrNull() ?: set.reps,
                        durationSec = seconds.toIntOrNull() ?: set.durationSec,
                        distanceM = meters.toDoubleOrNull() ?: set.distanceM,
                    ),
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** Nombre y notas de un entreno pasado. */
@Composable
fun EditPastWorkoutInfoDialog(name: String, notes: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var n by remember { mutableStateOf(name) }
    var t by remember { mutableStateOf(notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_edit_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text(stringResource(R.string.history_workout_name)) }, singleLine = true)
                OutlinedTextField(t, { t = it }, label = { Text(stringResource(R.string.history_workout_notes)) }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(n.trim().ifBlank { name }, t.trim()) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
