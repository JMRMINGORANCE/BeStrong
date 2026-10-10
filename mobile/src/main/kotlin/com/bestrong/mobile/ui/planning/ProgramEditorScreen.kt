package com.bestrong.mobile.ui.planning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.training.LoadScheme
import com.bestrong.core.training.PercentSet
import com.bestrong.core.training.PeriodizedItem
import com.bestrong.core.training.PeriodizedProgram
import com.bestrong.core.training.PeriodizedPrograms
import com.bestrong.core.training.ProgramDay
import com.bestrong.core.util.newId
import com.bestrong.mobile.R
import com.bestrong.mobile.ui.components.displayName

enum class SchemeKind { LINEAR, FIXED, FIVE_THREE_ONE }

/** Borrador editable de un ejercicio del programa. */
data class ItemDraft(
    val exerciseId: String,
    val kind: SchemeKind = SchemeKind.LINEAR,
    val sets: Int = 3,
    val reps: Int = 8,
    val percent: Int = 75,
    val incrementKg: Double = 2.5,
    val rest: Int = 120,
)

data class DayDraft(val name: String, val items: List<ItemDraft> = emptyList())

private val LOWER = setOf(MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES)

/** Convierte el borrador en programa: 5/3/1 → ciclo de 4 semanas; TM +2,5 kg (torso) o +5 kg (pierna) por ciclo. */
fun buildProgram(id: String, name: String, days: List<DayDraft>): PeriodizedProgram {
    val five31 = PeriodizedPrograms.byId("wendler_531_bbb")!!.days.first().items.first().scheme
    val programDays = days.map { d ->
        ProgramDay(
            d.name,
            d.name,
            d.items.map { it ->
                val scheme: LoadScheme = when (it.kind) {
                    SchemeKind.LINEAR -> LoadScheme.Linear(it.sets, it.reps, it.incrementKg, 0.0)
                    SchemeKind.FIXED -> LoadScheme.Fixed(List(it.sets) { _ -> PercentSet(it.percent / 100.0, it.reps) })
                    SchemeKind.FIVE_THREE_ONE -> five31
                }
                PeriodizedItem(it.exerciseId, scheme, it.rest)
            },
        )
    }
    val percentLifts = days.flatMap { it.items }.filter { it.kind != SchemeKind.LINEAR }.map { it.exerciseId }.distinct()
    return PeriodizedProgram(
        id = id,
        nameEs = name,
        nameEn = name,
        descriptionEs = "",
        descriptionEn = "",
        days = programDays,
        weeksPerCycle = if (days.any { d -> d.items.any { it.kind == SchemeKind.FIVE_THREE_ONE } }) 4 else 1,
        tmIncrementKg = percentLifts.associateWith { id -> if (ExerciseLibrary.byId(id)?.primaryMuscle in LOWER) 5.0 else 2.5 },
        mainLifts = percentLifts,
        custom = true,
    )
}

private fun draftsOf(p: PeriodizedProgram): List<DayDraft> = p.days.map { d ->
    DayDraft(
        d.nameEs,
        d.items.map { item ->
            when (val s = item.scheme) {
                is LoadScheme.Linear -> ItemDraft(item.exerciseId, SchemeKind.LINEAR, s.sets, s.reps, incrementKg = s.incrementKg, rest = item.restSeconds)
                is LoadScheme.Fixed -> ItemDraft(item.exerciseId, SchemeKind.FIXED, s.sets.size, s.sets.firstOrNull()?.reps ?: 5, ((s.sets.firstOrNull()?.percent ?: 0.75) * 100).toInt(), rest = item.restSeconds)
                is LoadScheme.Weekly -> ItemDraft(item.exerciseId, SchemeKind.FIVE_THREE_ONE, rest = item.restSeconds)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramEditorScreen(vm: PlanningViewModel, programId: String?, onClose: () -> Unit) {
    val existing = remember(programId) { programId?.let { id -> vm.ui.value.state.customPrograms.firstOrNull { it.id == id } } }
    var name by remember { mutableStateOf(existing?.nameEs ?: "") }
    val dayLabel = stringResource(R.string.prog_day_n, 1)
    val days = remember { mutableStateListOf<DayDraft>().apply { addAll(existing?.let(::draftsOf) ?: listOf(DayDraft(dayLabel))) } }
    var picking by remember { mutableStateOf<Int?>(null) }
    val defaultDayName = stringResource(R.string.prog_day_n, days.size + 1)

    picking?.let { dayIndex ->
        ExercisePickDialog(onPick = { id ->
            days[dayIndex] = days[dayIndex].copy(items = days[dayIndex].items + ItemDraft(id))
            picking = null
        }, onDismiss = { picking = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prog_create)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    TextButton(
                        enabled = name.isNotBlank() && days.any { it.items.isNotEmpty() },
                        onClick = {
                            vm.saveProgram(buildProgram(existing?.id ?: "custom:${newId()}", name.trim(), days.filter { it.items.isNotEmpty() }))
                            onClose()
                        },
                    ) { Text(stringResource(R.string.prog_save)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.prog_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            itemsIndexed(days) { dayIndex, day ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(day.name, { days[dayIndex] = day.copy(name = it) }, label = { Text(stringResource(R.string.prog_day_name)) }, singleLine = true, modifier = Modifier.weight(1f))
                            if (days.size > 1) IconButton(onClick = { days.removeAt(dayIndex) }) { Icon(Icons.Default.Close, stringResource(R.string.action_delete)) }
                        }
                        day.items.forEachIndexed { i, item ->
                            ItemEditor(
                                item = item,
                                name = ExerciseLibrary.byId(item.exerciseId)?.displayName() ?: item.exerciseId,
                                onChange = { updated -> days[dayIndex] = day.copy(items = day.items.toMutableList().also { it[i] = updated }) },
                                onRemove = { days[dayIndex] = day.copy(items = day.items.filterIndexed { j, _ -> j != i }) },
                            )
                        }
                        OutlinedButton(onClick = { picking = dayIndex }) { Text(stringResource(R.string.prog_add_exercise)) }
                    }
                }
            }
            item {
                Button(onClick = { days.add(DayDraft(defaultDayName)) }) { Text(stringResource(R.string.prog_days) + " +") }
            }
        }
    }
}

@Composable
private fun ItemEditor(item: ItemDraft, name: String, onChange: (ItemDraft) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, stringResource(R.string.action_delete)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(SchemeKind.LINEAR to R.string.scheme_linear, SchemeKind.FIXED to R.string.scheme_fixed, SchemeKind.FIVE_THREE_ONE to R.string.scheme_531).forEach { (k, l) ->
                FilterChip(selected = item.kind == k, onClick = { onChange(item.copy(kind = k)) }, label = { Text(stringResource(l), style = MaterialTheme.typography.labelSmall) })
            }
        }
        if (item.kind != SchemeKind.FIVE_THREE_ONE) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberBox(stringResource(R.string.prog_sets), item.sets, Modifier.weight(1f)) { onChange(item.copy(sets = it.coerceIn(1, 12))) }
                NumberBox(stringResource(R.string.prog_reps), item.reps, Modifier.weight(1f)) { onChange(item.copy(reps = it.coerceIn(1, 50))) }
                if (item.kind == SchemeKind.FIXED) {
                    NumberBox(stringResource(R.string.prog_percent), item.percent, Modifier.weight(1f)) { onChange(item.copy(percent = it.coerceIn(30, 110))) }
                } else {
                    NumberBox(stringResource(R.string.prog_increment), item.incrementKg.toInt(), Modifier.weight(1f)) { onChange(item.copy(incrementKg = it.toDouble().coerceIn(0.0, 20.0))) }
                }
                NumberBox(stringResource(R.string.prog_rest), item.rest, Modifier.weight(1f)) { onChange(item.copy(rest = it.coerceIn(15, 600))) }
            }
        }
    }
}

@Composable
private fun NumberBox(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text,
        { t ->
            text = t.filter(Char::isDigit).take(3)
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Selector sencillo de ejercicios de la biblioteca con búsqueda. */
@Composable
fun ExercisePickDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val lang = com.bestrong.mobile.ui.components.uiLanguage()
    val results = remember(query) { ExerciseLibrary.search(ExerciseLibrary.all, query, nameOf = { ExerciseLibrary.displayName(it, lang) }).take(60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prog_add_exercise)) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.prog_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.id }) { ex ->
                        ListItem(headlineContent = { Text(ex.displayName()) }, modifier = Modifier.clickable { onPick(ex.id) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
