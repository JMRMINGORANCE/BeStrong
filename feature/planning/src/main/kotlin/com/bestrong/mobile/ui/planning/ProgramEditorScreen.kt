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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.displayName
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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

/** Borrador en JSON para guardarlo en el estado de la pantalla (giro, Fold, proceso muerto). */
internal fun encodeDrafts(days: List<DayDraft>): String = buildJsonArray {
    days.forEach { d ->
        add(
            buildJsonObject {
                put("name", d.name)
                put(
                    "items",
                    buildJsonArray {
                        d.items.forEach { i ->
                            add(
                                buildJsonObject {
                                    put("ex", i.exerciseId)
                                    put("kind", i.kind.name)
                                    put("sets", i.sets)
                                    put("reps", i.reps)
                                    put("percent", i.percent)
                                    put("inc", i.incrementKg)
                                    put("rest", i.rest)
                                },
                            )
                        }
                    },
                )
            },
        )
    }
}.toString()

internal fun decodeDrafts(json: String): List<DayDraft> = runCatching {
    Json.parseToJsonElement(json).jsonArray.map { d ->
        val o = d.jsonObject
        DayDraft(
            o.getValue("name").jsonPrimitive.content,
            o.getValue("items").jsonArray.map { e ->
                val i = e.jsonObject
                ItemDraft(
                    exerciseId = i.getValue("ex").jsonPrimitive.content,
                    kind = SchemeKind.valueOf(i.getValue("kind").jsonPrimitive.content),
                    sets = i.getValue("sets").jsonPrimitive.int,
                    reps = i.getValue("reps").jsonPrimitive.int,
                    percent = i.getValue("percent").jsonPrimitive.int,
                    incrementKg = i.getValue("inc").jsonPrimitive.double,
                    rest = i.getValue("rest").jsonPrimitive.int,
                )
            },
        )
    }
}.getOrDefault(emptyList())

private val DraftsSaver = Saver<SnapshotStateList<DayDraft>, String>(
    save = { encodeDrafts(it) },
    restore = { mutableStateListOf<DayDraft>().apply { addAll(decodeDrafts(it)) } },
)

private fun draftsOf(p: PeriodizedProgram): List<DayDraft> = p.days.map { d ->
    DayDraft(
        d.nameEs,
        d.items.map { item ->
            when (val s = item.scheme) {
                is LoadScheme.Linear -> ItemDraft(item.exerciseId, SchemeKind.LINEAR, s.sets, s.reps, incrementKg = s.incrementKg, rest = item.restSeconds)
                is LoadScheme.Fixed -> ItemDraft(item.exerciseId, SchemeKind.FIXED, s.sets.size, s.sets.firstOrNull()?.reps ?: 5, ((s.sets.firstOrNull()?.percent ?: 0.75) * 100).roundToInt(), rest = item.restSeconds)
                is LoadScheme.Weekly -> ItemDraft(item.exerciseId, SchemeKind.FIVE_THREE_ONE, rest = item.restSeconds)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramEditorScreen(vm: PlanningViewModel, programId: String?, onClose: () -> Unit) {
    val existing = remember(programId) { programId?.let { id -> vm.ui.value.state.customPrograms.firstOrNull { it.id == id } } }
    // El borrador sobrevive al giro y al plegar/desplegar el Fold
    var name by rememberSaveable { mutableStateOf(existing?.nameEs ?: "") }
    val dayLabel = stringResource(R.string.prog_day_n, 1)
    val days = rememberSaveable(saver = DraftsSaver) { mutableStateListOf<DayDraft>().apply { addAll(existing?.let(::draftsOf) ?: listOf(DayDraft(dayLabel))) } }
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    val defaultDayName = stringResource(R.string.prog_day_n, days.size + 1)

    picking?.takeIf { it in days.indices }?.let { dayIndex ->
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
    val unit = LocalWeightUnit.current
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
                NumberBox(stringResource(R.string.prog_sets), item.sets, 1..12, Modifier.weight(1f)) { onChange(item.copy(sets = it)) }
                NumberBox(stringResource(R.string.prog_reps), item.reps, 1..50, Modifier.weight(1f)) { onChange(item.copy(reps = it)) }
                if (item.kind == SchemeKind.FIXED) {
                    NumberBox(stringResource(R.string.prog_percent), item.percent, 30..110, Modifier.weight(1f)) { onChange(item.copy(percent = it)) }
                } else {
                    // Incremento con decimales (1,25 / 2,5) y en la unidad elegida
                    ValueBox(
                        label = stringResource(R.string.prog_increment) + " (" + unit.label + ")",
                        value = item.incrementKg,
                        show = { unit.formatStep(it).removeSuffix(" " + unit.label) },
                        parse = { t -> unit.parseToKg(t)?.takeIf { it in 0.0..20.0 } },
                        decimal = true,
                        modifier = Modifier.weight(1f),
                    ) { onChange(item.copy(incrementKg = it)) }
                }
                NumberBox(stringResource(R.string.prog_rest), item.rest, 15..600, Modifier.weight(1f)) { onChange(item.copy(rest = it)) }
            }
        }
    }
}

@Composable
private fun NumberBox(label: String, value: Int, range: IntRange, modifier: Modifier, onChange: (Int) -> Unit) =
    ValueBox(label, value, show = { it.toString() }, parse = { t -> t.toIntOrNull()?.takeIf { it in range } }, decimal = false, modifier = modifier, onChange = onChange)

/**
 * Campo numérico que guarda el texto tal cual se escribe (para poder teclear «85» pasando por «8»)
 * y solo comunica el valor cuando es válido; al salir del campo vuelve al último válido.
 */
@Composable
private fun <T> ValueBox(label: String, value: T, show: (T) -> String, parse: (String) -> T?, decimal: Boolean, modifier: Modifier, onChange: (T) -> Unit) {
    var text by rememberSaveable { mutableStateOf(show(value)) }
    // Cambio desde fuera (otro esquema, otra unidad…): se refleja si no coincide con lo escrito
    LaunchedEffect(value) { if (parse(text) != value) text = show(value) }
    OutlinedTextField(
        text,
        { t ->
            text = (if (decimal) t.filter { it.isDigit() || it == ',' || it == '.' }.take(6) else t.filter(Char::isDigit).take(3))
            parse(text)?.let(onChange)
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        isError = parse(text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.onFocusChanged { if (!it.isFocused && parse(text) == null) text = show(value) },
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
