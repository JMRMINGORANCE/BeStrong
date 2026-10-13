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
import com.bestrong.core.training.PlateLoad
import com.bestrong.core.util.WeightUnit
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit

/** Qué discos poner en cada lado de la barra para un peso objetivo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlateCalculatorDialog(initialKg: Double, gym: GymProfile? = null, onApply: ((Double) -> Unit)? = null, onDismiss: () -> Unit) {
    val unit = LocalWeightUnit.current
    var text by remember { mutableStateOf(unit.format(initialKg).takeIf { initialKg > 0 } ?: "") }
    // Lo que ponen los discos tocados, exacto (el texto redondea y en libras dejaría de cuadrar)
    var exact by remember { mutableStateOf<Double?>(null) }
    val bars = barsFor(gym, unit)
    var bar by remember { mutableDoubleStateOf(bars.first()) }
    val target = exact?.takeIf { formatWeight(it) == text } ?: text.replace(',', '.').toDoubleOrNull() ?: 0.0
    val plates = platesFor(gym, unit)
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
                PlateChips(load, plates, bar) {
                    exact = it
                    text = formatWeight(it)
                }
                PlateTotals(load, unit)
            }
        },
        confirmButton = {
            if (onApply != null && target > 0) {
                TextButton(onClick = { onApply(unit.toKg(load.achieved)); onDismiss() }) { Text(stringResource(R.string.plates_use_weight)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = if (onApply != null) ({ TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }) else null,
    )
}

/** Discos por lado (tocar uno lo quita) y discos para añadir: cargar la barra al revés. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlateChips(load: PlateLoad, plates: List<Double>, bar: Double, onTarget: (Double) -> Unit) {
    // Tocar un disco lo quita de cada lado; abajo se añaden (cargar la barra al revés)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        load.perSide.forEach { p ->
            SuggestionChip(onClick = { onTarget((load.achieved - 2 * p).coerceAtLeast(bar)) }, label = { Text(formatWeight(p), fontWeight = FontWeight.Bold) })
        }
    }
    Text(stringResource(R.string.plates_add_per_side), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        plates.sortedDescending().forEach { p ->
            SuggestionChip(onClick = { onTarget(maxOf(load.achieved, bar) + 2 * p) }, label = { Text("+" + formatWeight(p)) })
        }
    }
}

/** Peso conseguido y lo que falta si los discos no cuadran. */
@Composable
private fun PlateTotals(load: PlateLoad, unit: WeightUnit) {
    Text(stringResource(R.string.plates_total, formatWeight(load.achieved), unit.label), fontWeight = FontWeight.SemiBold)
    if (load.remainder > 0) {
        Text(
            stringResource(R.string.plates_remainder, formatWeight(load.remainder), unit.label),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Barras a elegir: la del gimnasio activo primero (en libras, redondeada a la estándar). */
private fun barsFor(gym: GymProfile?, unit: WeightUnit): List<Double> {
    // En libras, el peso real de la barra (20 kg = 44 lb) redondeado a media libra: lo que se apunta debe ser exacto
    val gymBar = gym?.let { if (unit == WeightUnit.KG) it.barKg else Math.round(unit.fromKg(it.barKg) * 2) / 2.0 }
    return (listOfNotNull(gymBar) + if (unit == WeightUnit.KG) listOf(20.0, 15.0, 10.0) else listOf(45.0, 35.0, 25.0)).distinct()
}

/** Discos del gimnasio (en kg); en libras, los estándar. */
private fun platesFor(gym: GymProfile?, unit: WeightUnit): List<Double> =
    gym?.platesKg?.map { unit.fromKg(it) }?.takeIf { unit == WeightUnit.KG && it.isNotEmpty() } ?: PlateCalculator.defaultPlates(unit)
