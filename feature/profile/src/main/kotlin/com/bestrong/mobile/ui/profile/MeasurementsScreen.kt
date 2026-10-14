package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.stats.BodyWeightTrend
import com.bestrong.core.util.WeightUnit
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.LineChart
import com.bestrong.mobile.ui.components.LocalBodyUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.formatShortDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementsScreen(vm: ProfileViewModel, onBack: () -> Unit) {
    val unit = LocalBodyUnit.current
    val list by vm.measurements.collectAsStateWithLifecycle()
    val relative by vm.relativeStrength.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    // Resto de medidas: una gráfica por cada una con al menos dos datos
    val others: List<Pair<String, (BodyMeasurement) -> Double?>> = listOf(
        stringResource(R.string.f_fat) to { m: BodyMeasurement -> m.bodyFatPct },
        stringResource(R.string.f_waist) to { m: BodyMeasurement -> m.waistCm },
        stringResource(R.string.f_chest) to { m: BodyMeasurement -> m.chestCm },
        stringResource(R.string.f_arm) to { m: BodyMeasurement -> m.armCm },
        stringResource(R.string.f_thigh) to { m: BodyMeasurement -> m.thighCm },
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.measurements)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = { ShareProgressButton(vm, list, unit) },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, stringResource(R.string.add_measurement)) } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val weights = list.filter { it.weightKg != null }.sortedBy { it.dateEpochMs }
            if (weights.size >= 2) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            SectionHeader(stringResource(R.string.body_weight_unit, unit.label))
                            LineChart(weights.map { ChartPoint(formatShortDate(it.dateEpochMs), it.weightKg!!) }, valueFormatter = { unit.format(it) })
                            WeightTrendLine(weights.map { it.dateEpochMs to it.weightKg!! }, unit)
                            WeightGoalLine(vm, weights.map { it.dateEpochMs to it.weightKg!! }, unit)
                        }
                    }
                }
            }
            // Siempre visible: sirve precisamente para empezar a pesarse con regularidad
            item { Card { Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) { WeighInReminder(vm) } } }
            others.forEach { (label, value) ->
                val points = list.filter { value(it) != null }.sortedBy { it.dateEpochMs }
                if (points.size >= 2) {
                    item(key = "chart-$label") {
                        Card {
                            Column(Modifier.padding(16.dp)) {
                                SectionHeader(label)
                                LineChart(points.map { ChartPoint(formatShortDate(it.dateEpochMs), value(it)!!) }, valueFormatter = { formatWeight(it) })
                            }
                        }
                    }
                }
            }
            relative?.let { (lift, data) ->
                item(key = "relative") {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            SectionHeader(stringResource(R.string.relative_strength, ExerciseLibrary.byId(lift)?.displayName() ?: lift))
                            Text(stringResource(R.string.relative_strength_hint), style = MaterialTheme.typography.bodySmall)
                            LineChart(data.map { (d, bw, rm) -> ChartPoint(formatShortDate(d), rm / bw) }, valueFormatter = { "%.2f".format(it) })
                        }
                    }
                }
            }
            item(key = "photos") { ProgressPhotosSection(vm) }
            items(list, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(formatDate(m.dateEpochMs)) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    m.weightKg?.let { stringResource(R.string.m_weight, unit.format(it, withUnit = true)) },
                                    m.bodyFatPct?.let { stringResource(R.string.m_fat, formatWeight(it)) },
                                    m.waistCm?.let { stringResource(R.string.m_waist, formatWeight(it)) },
                                    m.chestCm?.let { stringResource(R.string.m_chest, formatWeight(it)) },
                                    m.armCm?.let { stringResource(R.string.m_arm, formatWeight(it)) },
                                    m.thighCm?.let { stringResource(R.string.m_thigh, formatWeight(it)) },
                                ).joinToString(" · "),
                            )
                        },
                        trailingContent = { IconButton(onClick = { vm.deleteMeasurement(m.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) } },
                    )
                }
            }
        }
    }
    if (adding) {
        // Lo escrito sobrevive al giro; al cerrar el diálogo se descarta
        val fields = rememberSaveable(saver = listSaver<List<MutableState<String>>, String>({ l -> l.map { it.value } }, { l -> l.map { mutableStateOf(it) } })) {
            List(6) { mutableStateOf("") }
        }
        val labels = listOf(
            stringResource(R.string.f_weight, unit.label),
            stringResource(R.string.f_fat),
            stringResource(R.string.f_waist),
            stringResource(R.string.f_chest),
            stringResource(R.string.f_arm),
            stringResource(R.string.f_thigh),
        )

        // 0 o negativo no es una medida: cuenta como vacío
        fun v(i: Int) = fields[i].value.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.new_measurement)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    labels.forEachIndexed { i, l ->
                        OutlinedTextField(
                            fields[i].value,
                            { fields[i].value = it },
                            label = { Text(l) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
            },
            confirmButton = {
                Button(enabled = fields.indices.any { v(it) != null }, onClick = {
                    vm.saveMeasurement(BodyMeasurement(dateEpochMs = System.currentTimeMillis(), weightKg = v(0)?.let(unit::toKg), bodyFatPct = v(1), waistCm = v(2), chestCm = v(3), armCm = v(4), thighCm = v(5)))
                    adding = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Media de 7 días y cambio frente a la semana anterior: el peso de un día suelto sube y baja con el agua y la comida. */
@Composable
private fun WeightTrendLine(points: List<Pair<Long, Double>>, unit: WeightUnit) {
    val t = remember(points) { BodyWeightTrend.of(points, System.currentTimeMillis()) } ?: return
    val average = unit.format(t.averageKg, withUnit = true)
    val change = t.changeKg?.let { c -> (if (c >= 0) "+" else "−") + unit.format(kotlin.math.abs(c), withUnit = true) }
    Text(
        if (change != null) stringResource(R.string.weight_trend_change, average, change) else stringResource(R.string.weight_trend, average),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Peso objetivo: cuánto falta y cuándo llegarías al ritmo actual (para bajar o subir). */
@Composable
private fun WeightGoalLine(vm: ProfileViewModel, points: List<Pair<Long, Double>>, unit: WeightUnit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val goal = settings.bodyWeightGoalKg
    var editing by rememberSaveable { mutableStateOf(false) }
    if (editing) WeightGoalDialog(vm, goal, points, unit) { editing = false }
    val start = settings.bodyWeightGoalStartKg
    val progress = remember(points, goal, start) { goal?.let { com.bestrong.core.stats.BodyWeightGoal.progress(points, it, start) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val text = when {
            goal == null || progress == null -> stringResource(R.string.weight_goal_none)
            progress.reached -> stringResource(R.string.weight_goal_reached, unit.format(goal, withUnit = true))
            progress.etaEpochMs != null -> stringResource(R.string.weight_goal_eta, unit.format(progress.remainingKg, withUnit = true), unit.format(goal, withUnit = true), formatDate(progress.etaEpochMs!!))
            else -> stringResource(R.string.weight_goal_left, unit.format(progress.remainingKg, withUnit = true), unit.format(goal, withUnit = true))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { editing = true }) { Text(stringResource(if (goal == null) R.string.goal_set else R.string.action_edit)) }
    }
}

/** Recordatorio semanal de pesarse: el día elegido a las 8 (mejor siempre en las mismas condiciones). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WeighInReminder(vm: ProfileViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
    Column(Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.weigh_in_reminder, com.bestrong.mobile.data.WEIGH_IN_HOUR), style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.material3.FilterChip(
                selected = settings.weighInDay == null,
                onClick = { vm.updateSettings { it.copy(weighInDay = null) } },
                label = { Text(stringResource(R.string.weigh_in_off)) },
            )
            java.time.DayOfWeek.entries.forEach { d ->
                androidx.compose.material3.FilterChip(
                    selected = settings.weighInDay == d.value,
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        vm.updateSettings { it.copy(weighInDay = d.value) }
                    },
                    label = { Text(d.getDisplayName(java.time.format.TextStyle.SHORT, locale)) },
                )
            }
        }
    }
}

/** Poner, cambiar o quitar el peso objetivo (guarda también el peso de partida, que decide si es bajar o subir). */
@Composable
private fun WeightGoalDialog(vm: ProfileViewModel, goal: Double?, points: List<Pair<Long, Double>>, unit: WeightUnit, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(goal?.let { unit.format(it) }.orEmpty()) }
    val kg = unit.parseToKg(text)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = { onClose() },
        title = { Text(stringResource(R.string.weight_goal_title)) },
        text = {
            OutlinedTextField(
                text,
                { t -> text = t.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                label = { Text(unit.label) },
                singleLine = true,
                isError = text.isNotEmpty() && kg == null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            )
        },
        // Se guarda también el peso de ahora: decide si el objetivo es bajar o subir
        confirmButton = {
            TextButton(onClick = {
                vm.updateSettings {
                    // Guardar sin cambiar el objetivo conserva el peso de partida
                    val start = if (kg == it.bodyWeightGoalKg) it.bodyWeightGoalStartKg ?: points.lastOrNull()?.second else points.lastOrNull()?.second
                    it.copy(bodyWeightGoalKg = kg, bodyWeightGoalStartKg = start)
                }; onClose()
            }, enabled = kg != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (goal != null) TextButton(onClick = { vm.updateSettings { it.copy(bodyWeightGoalKg = null, bodyWeightGoalStartKg = null) }; onClose() }) { Text(stringResource(R.string.goal_remove)) }
                TextButton(onClick = { onClose() }) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/**
 * Comparte el progreso corporal en texto (para el entrenador o un amigo): media de 7 días y cambio,
 * objetivo de peso con lo que falta, y el cambio de cada medida desde la primera.
 */
@Composable
private fun ShareProgressButton(vm: ProfileViewModel, list: List<BodyMeasurement>, unit: WeightUnit) {
    if (list.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val title = stringResource(R.string.share_progress_title)
    val weights = list.filter { it.weightKg != null }.sortedBy { it.dateEpochMs }.map { it.dateEpochMs to it.weightKg!! }
    val trend = remember(weights) { BodyWeightTrend.of(weights, System.currentTimeMillis()) }
    val goal = settings.bodyWeightGoalKg
    val progress = remember(weights, goal) { goal?.let { com.bestrong.core.stats.BodyWeightGoal.progress(weights, it, settings.bodyWeightGoalStartKg) } }
    val labels = listOf(
        stringResource(R.string.f_waist) to { m: BodyMeasurement -> m.waistCm },
        stringResource(R.string.f_chest) to { m: BodyMeasurement -> m.chestCm },
        stringResource(R.string.f_arm) to { m: BodyMeasurement -> m.armCm },
        stringResource(R.string.f_thigh) to { m: BodyMeasurement -> m.thighCm },
    )
    val lines = buildList {
        trend?.let { t ->
            val change = t.changeKg?.let { c -> (if (c >= 0) "+" else "−") + unit.format(kotlin.math.abs(c), withUnit = true) }
            add(if (change != null) stringResource(R.string.weight_trend_change, unit.format(t.averageKg, withUnit = true), change) else stringResource(R.string.weight_trend, unit.format(t.averageKg, withUnit = true)))
        }
        if (goal != null && progress != null) {
            add(
                if (progress.reached) {
                    stringResource(R.string.weight_goal_reached, unit.format(goal, withUnit = true))
                } else {
                    stringResource(R.string.weight_goal_left, unit.format(progress.remainingKg, withUnit = true), unit.format(goal, withUnit = true))
                },
            )
        }
        addAll(measurementChanges(list, labels))
    }
    IconButton(
        onClick = {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, (listOf(title) + lines).joinToString("\n"))
            context.startActivity(android.content.Intent.createChooser(send, null))
        },
    ) { Icon(Icons.Default.Share, stringResource(R.string.share_progress)) }
}

/** «Cintura (cm): 84 (−2)»: cada medida, la última y su cambio desde la primera. */
private fun measurementChanges(list: List<BodyMeasurement>, labels: List<Pair<String, (BodyMeasurement) -> Double?>>): List<String> {
    val sorted = list.sortedBy { it.dateEpochMs }
    return labels.mapNotNull { (label, value) ->
        val first = sorted.firstNotNullOfOrNull(value)
        val last = sorted.lastOrNull { value(it) != null }?.let(value)
        if (first == null || last == null || first == last) return@mapNotNull null
        val diff = last - first
        // La etiqueta ya lleva la unidad («Cintura (cm)»)
        "$label: ${formatWeight(last)} (" + (if (diff >= 0) "+" else "−") + formatWeight(kotlin.math.abs(diff)) + ")"
    }
}
