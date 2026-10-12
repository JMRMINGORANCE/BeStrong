package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bestrong.core.training.GymProfile
import com.bestrong.core.training.PlateCalculator
import com.bestrong.core.util.WeightUnit
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit

/** Qué discos poner en cada lado de la barra para un peso objetivo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlateCalculatorDialog(initialKg: Double, gym: GymProfile? = null, onDismiss: () -> Unit) {
    val unit = LocalWeightUnit.current
    var text by remember { mutableStateOf(unit.format(initialKg).takeIf { initialKg > 0 } ?: "") }
    // La barra y los discos del gimnasio activo van primero
    val gymBar = gym?.let { unit.fromKg(it.barKg) }
    val bars = (listOfNotNull(gymBar) + if (unit == WeightUnit.KG) listOf(20.0, 15.0, 10.0) else listOf(45.0, 35.0, 25.0)).distinct()
    var bar by remember { mutableDoubleStateOf(bars.first()) }
    val target = text.replace(',', '.').toDoubleOrNull() ?: 0.0
    val plates = gym?.platesKg?.map { unit.fromKg(it) }?.takeIf { unit == WeightUnit.KG && it.isNotEmpty() } ?: PlateCalculator.defaultPlates(unit)
    val load = PlateCalculator.calculate(target, unit, bar, plates)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plates_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    text,
                    { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                    label = { Text(stringResource(R.string.plates_target, unit.label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(stringResource(R.string.plates_bar), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    bars.forEach { b -> FilterChip(selected = bar == b, onClick = { bar = b }, label = { Text("${formatWeight(b)} ${unit.label}") }) }
                }
                Text(stringResource(R.string.plates_per_side), style = MaterialTheme.typography.labelLarge)
                if (load.perSide.isEmpty()) Text(stringResource(R.string.plates_only_bar))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    load.perSide.forEach { p -> SuggestionChip(onClick = {}, label = { Text(formatWeight(p), fontWeight = FontWeight.Bold) }) }
                }
                Text(stringResource(R.string.plates_total, formatWeight(load.achieved), unit.label), fontWeight = FontWeight.SemiBold)
                if (load.remainder > 0) {
                    Text(
                        stringResource(R.string.plates_remainder, formatWeight(load.remainder), unit.label),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
