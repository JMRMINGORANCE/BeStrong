package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.Equipment
import com.bestrong.core.training.GymProfile
import com.bestrong.core.util.WeightUnit
import com.bestrong.mobile.data.UserSettings
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.label

private val STANDARD_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)

/** Discos de un gimnasio en libras (45, 35, 25…), guardados en kg. */
private val STANDARD_PLATES_LB = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5).map(WeightUnit.LB::toKg)

/** Selección del gimnasio activo y acceso a su edición. */
@Composable
fun GymProfilesSection(settings: UserSettings, onSelect: (String) -> Unit, onEdit: (GymProfile) -> Unit, onAdd: () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        SectionHeader(stringResource(R.string.gyms), Modifier.padding(horizontal = 16.dp))
        Text(
            stringResource(R.string.gyms_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        settings.gyms.forEach { g ->
            val selected = g.id == settings.activeGym.id
            ListItem(
                headlineContent = { Text(g.name) },
                supportingContent = { Text(stringResource(R.string.gym_summary, g.equipment.size, g.platesKg.size)) },
                leadingContent = { RadioButton(selected = selected, onClick = null) },
                trailingContent = { TextButton(onClick = { onEdit(g) }) { Text(stringResource(R.string.action_edit)) } },
                modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { onSelect(g.id) },
            )
        }
        TextButton(onClick = onAdd, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.gym_add)) }
    }
}

/** Editor de un gimnasio: material, barra, discos y mancuernas disponibles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GymEditorDialog(gym: GymProfile, canDelete: Boolean, onSave: (GymProfile) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val unit = LocalWeightUnit.current
    var name by remember { mutableStateOf(gym.name) }
    // Borrar un gimnasio se lleva su material y lo quita de sus rutinas: se confirma antes
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.routine_delete_q, gym.name)) },
            text = { Text(stringResource(R.string.gym_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    var equipment by remember { mutableStateOf(gym.equipment) }
    // Textos de partida: si no se tocan se guarda el valor exacto (el texto va redondeado y, en lb, convertido)
    val initial = remember {
        listOf(
            unit.format(gym.barKg),
            unit.format(gym.collarKg),
            unit.format(gym.dumbbellsKg.minOrNull() ?: 2.0),
            unit.format(gym.dumbbellsKg.maxOrNull() ?: 30.0),
            unit.format(gym.dumbbellsKg.sorted().zipWithNext { a, b -> b - a }.minOrNull() ?: 2.0),
        )
    }
    var bar by remember { mutableStateOf(initial[0]) }
    var collars by remember { mutableStateOf(initial[1]) }
    var plates by remember { mutableStateOf(gym.platesKg.toSet()) }
    var anyDumbbell by remember { mutableStateOf(gym.dumbbellsKg.isEmpty()) }
    var dbFrom by remember { mutableStateOf(initial[2]) }
    var dbTo by remember { mutableStateOf(initial[3]) }
    var dbStep by remember { mutableStateOf(initial[4]) }
    // Los de la unidad del usuario y los que ya tuviera el gimnasio
    val plateChoices = remember(unit) { ((if (unit == WeightUnit.LB) STANDARD_PLATES_LB else STANDARD_PLATES) + gym.platesKg).distinct().sortedDescending() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gym_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.gym_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.gym_equipment), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Equipment.entries.forEach { e ->
                        FilterChip(selected = e in equipment, onClick = { equipment = if (e in equipment) equipment - e else equipment + e }, label = { Text(e.label()) })
                    }
                }
                OutlinedTextField(
                    bar, { bar = it },
                    label = { Text(stringResource(R.string.gym_bar, unit.label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    collars, { collars = it },
                    label = { Text(stringResource(R.string.gym_collars, unit.label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.gym_plates), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    plateChoices.forEach { p ->
                        FilterChip(selected = p in plates, onClick = { plates = if (p in plates) plates - p else plates + p }, label = { Text(unit.format(p)) })
                    }
                }
                Row(Modifier.toggleable(value = anyDumbbell, role = androidx.compose.ui.semantics.Role.Switch) { anyDumbbell = it }, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.gym_any_dumbbell), Modifier.weight(1f))
                    Switch(anyDumbbell, null)
                }
                if (!anyDumbbell) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val kb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        OutlinedTextField(dbFrom, { dbFrom = it }, label = { Text(stringResource(R.string.gym_db_from)) }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f))
                        OutlinedTextField(dbTo, { dbTo = it }, label = { Text(stringResource(R.string.gym_db_to)) }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f))
                        OutlinedTextField(dbStep, { dbStep = it }, label = { Text(stringResource(R.string.gym_db_step)) }, singleLine = true, keyboardOptions = kb, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dumbbellsUntouched = listOf(dbFrom, dbTo, dbStep) == initial.subList(2, 5) && gym.dumbbellsKg.isNotEmpty()
                val dumbbells = if (anyDumbbell) {
                    emptyList()
                } else if (dumbbellsUntouched) {
                    gym.dumbbellsKg
                } else {
                    val from = unit.parseToKg(dbFrom) ?: 2.0
                    val to = unit.parseToKg(dbTo) ?: 30.0
                    val step = (unit.parseToKg(dbStep) ?: 2.0).coerceAtLeast(0.5)
                    generateSequence(from) { it + step }.takeWhile { it <= to + 1e-6 }.take(200).toList()
                }
                onSave(
                    gym.copy(
                        name = name.trim().ifBlank { gym.name },
                        equipment = equipment,
                        barKg = if (bar == initial[0]) gym.barKg else unit.parseToKg(bar) ?: gym.barKg,
                        collarKg = if (collars == initial[1]) {
                            gym.collarKg
                        } else if (collars.isBlank()) {
                            0.0
                        } else {
                            unit.parseToKg(collars)?.coerceIn(0.0, MAX_COLLARS_KG) ?: gym.collarKg
                        },
                        platesKg = plates.sortedDescending().ifEmpty { gym.platesKg },
                        dumbbellsKg = dumbbells,
                    ),
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (canDelete) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.action_delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** Límite razonable para los dos cierres (evita un «50» tecleado por error). */
private const val MAX_COLLARS_KG = 10.0
