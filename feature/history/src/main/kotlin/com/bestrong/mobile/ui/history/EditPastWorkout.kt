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
fun EditPastSetDialog(
    set: WorkoutSet,
    tracking: TrackingType,
    onSave: (WorkoutSet) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** 2 con «suma de las dos mancuernas»: se ve y se escribe el total del par, y se guarda el de una. */
    weightFactor: Int = 1,
) {
    val unit = LocalWeightUnit.current
    val initialWeight = remember { if (set.weightKg == 0.0) "" else unit.format(set.weightKg * weightFactor) }
    var weight by remember { mutableStateOf(initialWeight) }
    var reps by remember { mutableStateOf(set.reps.toString()) }
    var seconds by remember { mutableStateOf(set.durationSec.toString()) }
    var meters by remember { mutableStateOf(set.distanceM.toInt().toString()) }
    var minutes by remember { mutableStateOf((set.durationSec / 60).toString()) }
    var km by remember { mutableStateOf(if (set.distanceM > 0) "%.2f".format(set.distanceM / 1000) else "") }
    val hasWeight = tracking != TrackingType.DURATION && tracking != TrackingType.CARDIO
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
                    TrackingType.CARDIO -> {
                        OutlinedTextField(
                            minutes,
                            { minutes = it.filter(Char::isDigit).take(3) },
                            label = { Text(stringResource(R.string.set_minutes)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            km,
                            { km = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                            label = { Text(stringResource(R.string.set_km)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kg = when {
                    // Sin tocar el peso se guarda el exacto (el texto va redondeado a un decimal y, en lb, convertido)
                    weight == initialWeight -> set.weightKg
                    !hasWeight || weight.isBlank() -> 0.0
                    weight.startsWith("-") -> -(unit.parseToKg(weight.drop(1)) ?: 0.0) / weightFactor
                    else -> unit.parseToKg(weight)?.div(weightFactor) ?: set.weightKg
                }
                onSave(
                    set.copy(
                        weightKg = kg,
                        reps = reps.toIntOrNull() ?: set.reps,
                        durationSec = if (tracking == TrackingType.CARDIO) minutes.toIntOrNull()?.times(60) ?: set.durationSec else seconds.toIntOrNull() ?: set.durationSec,
                        distanceM = if (tracking == TrackingType.CARDIO) km.replace(',', '.').toDoubleOrNull()?.times(1000) ?: 0.0 else meters.toDoubleOrNull() ?: set.distanceM,
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

/** Nombre, notas, fecha, hora y duración de un entreno pasado. */
@Composable
fun EditPastWorkoutInfoDialog(
    name: String,
    notes: String,
    startEpochMs: Long,
    durationMin: Long,
    /** [range] es null si no se cambió la fecha, la hora ni la duración. */
    onSave: (name: String, notes: String, range: Pair<Long, Long>?) -> Unit,
    onDismiss: () -> Unit,
) {
    var n by remember { mutableStateOf(name) }
    var t by remember { mutableStateOf(notes) }
    val time = rememberWorkoutTimeState(startEpochMs, durationMin)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_edit_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text(stringResource(R.string.history_workout_name)) }, singleLine = true)
                OutlinedTextField(t, { t = it }, label = { Text(stringResource(R.string.history_workout_notes)) }, minLines = 2)
                WorkoutTimeFields(time)
            }
        },
        confirmButton = {
            val range = time.range
            TextButton(onClick = { onSave(n.trim().ifBlank { name }, t.trim(), range?.takeIf { time.changed }) }, enabled = range != null || !time.changed) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
