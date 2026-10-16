package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
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
    // Medida que se está corrigiendo (null = nueva)
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    // Resto de medidas: una gráfica por cada una con al menos dos datos
    val others: List<Pair<String, (BodyMeasurement) -> Double?>> = listOf(
        stringResource(R.string.f_fat) to { m: BodyMeasurement -> m.bodyFatPct },
        stringResource(R.string.f_waist) to { m: BodyMeasurement -> m.waistCm },
        stringResource(R.string.f_chest) to { m: BodyMeasurement -> m.chestCm },
        stringResource(R.string.f_arm) to { m: BodyMeasurement -> m.armCm },
        stringResource(R.string.f_thigh) to { m: BodyMeasurement -> m.thighCm },
    )
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    UndoMeasurementDelete(vm, snackbar)
    // Cambio de peso frente a la medida anterior con peso (la lista va de la más reciente a la más antigua)
    val deltas = remember(list) { weightDeltas(list) }
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.measurements)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    ImportMeasurementsButton(vm)
                    ExportMeasurementsButton(vm, list)
                    ShareProgressButton(vm, list, unit)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingId = null
                adding = true
            }) { Icon(Icons.Default.Add, stringResource(R.string.add_measurement)) }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val weights = list.filter { it.weightKg != null }.sortedBy { it.dateEpochMs }
            if (weights.size >= 2) {
                item {
                    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                        Column(Modifier.padding(16.dp)) {
                            SectionHeader(stringResource(R.string.body_weight_unit, unit.label))
                            LineChart(weights.map { ChartPoint(formatShortDate(it.dateEpochMs), it.weightKg!!) }, valueFormatter = { unit.format(it) })
                            WeightTrendLine(weights.map { it.dateEpochMs to it.weightKg!! }, unit)
                            WeightGoalLine(vm, weights.map { it.dateEpochMs to it.weightKg!! }, unit)
                        }
                    }
                }
            }
            item { BodyCompositionCard(vm, list) }
            // Siempre visible: sirve precisamente para empezar a pesarse con regularidad
            item { Card { Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) { WeighInReminder(vm) } } }
            others.forEach { (label, value) ->
                val points = list.filter { value(it) != null }.sortedBy { it.dateEpochMs }
                if (points.size >= 2) {
                    item(key = "chart-$label") {
                        com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
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
                    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
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
                MeasurementItem(m, deltas[m.id], unit, onEdit = {
                    editingId = m.id
                    adding = true
                }) { vm.deleteMeasurement(m.id) }
            }
        }
    }
    // Al corregir una, se espera a tenerla cargada (tras cerrar Android la app, la lista llega después)
    val editing = list.firstOrNull { it.id == editingId }
    if (adding && (editingId == null || editing != null)) {
        MeasurementDialog(
            initial = editing,
            unit = unit,
            onSave = { m ->
                vm.saveMeasurement(m)
                adding = false
                editingId = null
            },
            onDismiss = {
                adding = false
                editingId = null
            },
        )
    }
}

/** Una medida de la lista con el cambio de peso; tocarla deja corregirla (mismo día y misma medida). */
@Composable
private fun MeasurementItem(m: BodyMeasurement, delta: Double?, unit: WeightUnit, onEdit: () -> Unit, onDelete: () -> Unit) {
    // Sin cambio visible (menos de lo que se enseña), nada: ni «+0» ni «−0»
    val change = delta?.takeIf { unit.format(kotlin.math.abs(it)) != "0" }?.let { d -> "  (" + (if (d >= 0) "+" else "−") + unit.format(kotlin.math.abs(d), withUnit = true) + ")" }.orEmpty()
    val parts = listOfNotNull(
        m.weightKg?.let { stringResource(R.string.m_weight, unit.format(it, withUnit = true)) + if (m.weightFromHealth) " (Health Connect)" else "" },
        m.bodyFatPct?.let { stringResource(R.string.m_fat, formatWeight(it)) },
        m.waistCm?.let { stringResource(R.string.m_waist, formatWeight(it)) },
        m.chestCm?.let { stringResource(R.string.m_chest, formatWeight(it)) },
        m.armCm?.let { stringResource(R.string.m_arm, formatWeight(it)) },
        m.thighCm?.let { stringResource(R.string.m_thigh, formatWeight(it)) },
    )
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        ListItem(
            headlineContent = { Text(formatDate(m.dateEpochMs) + change) },
            supportingContent = { Text(parts.joinToString(" · ")) },
            trailingContent = { IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) } },
        )
    }
}

/** Trae medidas de un CSV (de una hoja de cálculo o del nutricionista). */
@Composable
private fun ImportMeasurementsButton(vm: ProfileViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val failed = stringResource(R.string.import_failed)
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            vm.importMeasurementsCsv(it) { n ->
                val text = n?.let { (added, completed, skipped) ->
                    context.getString(R.string.import_measurements_summary, added, completed) +
                        if (skipped > 0) " · " + context.getString(R.string.import_measurements_skipped, skipped) else ""
                } ?: failed
                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    IconButton(onClick = { launcher.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values")) }) {
        Icon(Icons.Default.FileUpload, stringResource(R.string.import_measurements))
    }
}

/** Guarda las medidas en un CSV (fecha y cada medida). */
@Composable
private fun ExportMeasurementsButton(vm: ProfileViewModel, list: List<BodyMeasurement>) {
    if (list.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val done = stringResource(R.string.export_measurements_done)
    val failed = stringResource(R.string.export_failed)
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportMeasurementsCsv(it) { ok -> android.widget.Toast.makeText(context, if (ok) done else failed, android.widget.Toast.LENGTH_SHORT).show() } }
    }
    IconButton(onClick = { launcher.launch("bestrong-medidas.csv") }) {
        Icon(Icons.Default.FileDownload, stringResource(R.string.export_measurements))
    }
}

/** Id → cambio de peso (kg) frente a la medida anterior que tenía peso; sin entrada si no hay con qué comparar. */
private fun weightDeltas(list: List<BodyMeasurement>): Map<String, Double> {
    val withWeight = list.filter { it.weightKg != null }.sortedByDescending { it.dateEpochMs }
    return withWeight.zipWithNext { cur, prev -> cur.id to (cur.weightKg!! - prev.weightKg!!) }.toMap()
}

/** «Medida borrada» con «Deshacer». */
@Composable
private fun UndoMeasurementDelete(vm: ProfileViewModel, snackbar: androidx.compose.material3.SnackbarHostState) {
    val deleted by vm.deletedMeasurement.collectAsStateWithLifecycle()
    val msg = stringResource(R.string.measurement_deleted)
    val undo = stringResource(R.string.action_undo)
    androidx.compose.runtime.LaunchedEffect(deleted) {
        if (deleted == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(msg, undo, duration = androidx.compose.material3.SnackbarDuration.Short)
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.undoDeleteMeasurement() else vm.clearDeletedMeasurement()
    }
}

/** Valores posibles de cada medida (peso en kg, grasa en %, perímetros en cm): fuera de ahí es un error al escribir. */
private val MEASURE_RANGES = listOf(20.0..400.0, 2.0..70.0, 30.0..250.0, 40.0..250.0, 10.0..100.0, 20.0..150.0)

/** Lo guardado si no se tocó ([kept], en kg para el peso: se enseña en la unidad elegida); si no, lo escrito. */
private fun fieldValue(text: String, index: Int, unit: WeightUnit, kept: Double?): Double? =
    if (kept != null) (if (index == 0) unit.fromKg(kept) else kept) else measureValue(text, index, unit)

/** El número escrito si es posible para esa medida (el peso se comprueba en kg); null si no lo es o está vacío. */
private fun measureValue(text: String, index: Int, unit: WeightUnit): Double? {
    val raw = text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    val check = if (index == 0) unit.toKg(raw) else raw
    return raw.takeIf { check in MEASURE_RANGES[index] }
}

/** Nueva medida o corregir [initial] (mismo id y fecha); un valor imposible se marca y no deja guardar. */
@Composable
private fun MeasurementDialog(initial: BodyMeasurement?, unit: WeightUnit, onSave: (BodyMeasurement) -> Unit, onDismiss: () -> Unit) {
    fun shown(v: Double?) = v?.let { "%.1f".format(it).removeSuffix(".0").removeSuffix(",0") }.orEmpty()
    // Lo escrito sobrevive al giro; al cerrar el diálogo se descarta
    val originals = listOf(initial?.weightKg, initial?.bodyFatPct, initial?.waistCm, initial?.chestCm, initial?.armCm, initial?.thighCm)
    val originalTexts = originals.mapIndexed { i, v -> shown(if (i == 0) v?.let(unit::fromKg) else v) }
    val fields = rememberSaveable(initial?.id, saver = listSaver<List<MutableState<String>>, String>({ l -> l.map { it.value } }, { l -> l.map { mutableStateOf(it) } })) {
        originalTexts.map { mutableStateOf(it) }
    }
    val labels = listOf(
        stringResource(R.string.f_weight, unit.label),
        stringResource(R.string.f_fat),
        stringResource(R.string.f_waist),
        stringResource(R.string.f_chest),
        stringResource(R.string.f_arm),
        stringResource(R.string.f_thigh),
    )

    // Sin tocar: el valor guardado tal cual (sin el redondeo de lo enseñado y aunque sea de antes de los límites)
    fun untouched(i: Int) = initial != null && fields[i].value == originalTexts[i]

    fun v(i: Int) = fieldValue(fields[i].value, i, unit, originals[i].takeIf { untouched(i) })

    // Vacío no es error; escrito y fuera de rango, sí
    fun wrong(i: Int) = fields[i].value.isNotBlank() && v(i) == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_measurement else R.string.edit_measurement)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                labels.forEachIndexed { i, l ->
                    OutlinedTextField(
                        fields[i].value,
                        { fields[i].value = it },
                        label = { Text(l) },
                        isError = wrong(i),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = fields.indices.any { v(it) != null } && fields.indices.none { wrong(it) }, onClick = {
                val base = initial ?: BodyMeasurement(dateEpochMs = System.currentTimeMillis())
                val weight = if (untouched(0)) initial?.weightKg else v(0)?.let(unit::toKg)
                onSave(base.copy(weightKg = weight, bodyFatPct = v(1), waistCm = v(2), chestCm = v(3), armCm = v(4), thighCm = v(5)))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
    // Y en el último mes (la tendencia que de verdad cuenta)
    remember(points) { BodyWeightTrend.monthChange(points, System.currentTimeMillis()) }?.takeIf { unit.format(kotlin.math.abs(it)) != "0" }?.let { c ->
        Text(
            stringResource(R.string.weight_month_change, (if (c >= 0) "+" else "−") + unit.format(kotlin.math.abs(c), withUnit = true)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Más de un 1 % a la semana: aviso para frenar (en una dirección u otra)
    val weekly = remember(points) { com.bestrong.core.stats.WeightRate.weeklyChange(points, System.currentTimeMillis()) }
    if (com.bestrong.core.stats.WeightRate.tooFast(t.averageKg, weekly)) {
        Text(
            stringResource(if ((weekly ?: 0.0) < 0) R.string.weight_rate_fast_loss else R.string.weight_rate_fast_gain),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
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
    if (progress != null && !progress.reached) GoalPaceLine(settings.bodyWeightGoalDateMs, progress.remainingKg, points.lastOrNull()?.second, unit)
}

/** Con fecha: «Para el 3 dic necesitas 0,4 kg por semana» y aviso si pasa del 1 % semanal. */
@Composable
private fun GoalPaceLine(dateMs: Long?, remainingKg: Double, currentKg: Double?, unit: WeightUnit) {
    dateMs ?: return
    val weekly = com.bestrong.core.stats.BodyWeightGoal.neededWeeklyKg(remainingKg, System.currentTimeMillis(), dateMs)
    val tooFast = weekly != null && currentKg != null && com.bestrong.core.stats.WeightRate.tooFast(currentKg, weekly)
    Text(
        if (weekly == null) {
            stringResource(R.string.weight_goal_date_passed, formatShortDate(dateMs))
        } else {
            stringResource(R.string.weight_goal_pace, formatShortDate(dateMs), unit.formatStep(weekly)) + if (tooFast) " " + stringResource(R.string.weight_goal_pace_fast) else ""
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (tooFast || weekly == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
        // Y una vez al mes, las medidas con cinta
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.measure_reminder), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(
                checked = settings.measureReminder,
                onCheckedChange = { on ->
                    if (on && android.os.Build.VERSION.SDK_INT >= 33) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    vm.updateSettings { it.copy(measureReminder = on) }
                },
            )
        }
    }
}

/** Poner, cambiar o quitar el peso objetivo (guarda también el peso de partida, que decide si es bajar o subir). */
@Composable
private fun WeightGoalDialog(vm: ProfileViewModel, goal: Double?, points: List<Pair<Long, Double>>, unit: WeightUnit, onClose: () -> Unit) {
    val currentDate = vm.settings.collectAsStateWithLifecycle().value.bodyWeightGoalDateMs
    var text by rememberSaveable { mutableStateOf(goal?.let { unit.format(it) }.orEmpty()) }
    // Plazo: KEEP_DATE = la fecha que ya había, 0 = sin fecha, N = N semanas desde hoy
    var weeks by rememberSaveable { mutableStateOf(if (currentDate != null) KEEP_DATE else 0) }
    val kg = unit.parseToKg(text)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = { onClose() },
        title = { Text(stringResource(R.string.weight_goal_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    text,
                    { t -> text = t.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                    label = { Text(unit.label) },
                    singleLine = true,
                    isError = text.isNotEmpty() && kg == null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
                // Fecha opcional: en cuántas semanas (desde hoy)
                Text(stringResource(R.string.weight_goal_by), style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    currentDate?.let { d ->
                        androidx.compose.material3.FilterChip(selected = weeks == KEEP_DATE, onClick = { weeks = KEEP_DATE }, label = { Text(formatShortDate(d)) })
                    }
                    androidx.compose.material3.FilterChip(selected = weeks == 0, onClick = { weeks = 0 }, label = { Text(stringResource(R.string.weight_goal_no_date)) })
                    for (n in GOAL_WEEKS) {
                        androidx.compose.material3.FilterChip(selected = weeks == n, onClick = { weeks = n }, label = { Text(stringResource(R.string.n_weeks_short, n)) })
                    }
                }
            }
        },
        // Se guarda también el peso de ahora: decide si el objetivo es bajar o subir
        confirmButton = {
            TextButton(onClick = {
                vm.updateSettings { saveWeightGoal(it, kg, weeks, points, unit) }; onClose()
            }, enabled = kg != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (goal != null) TextButton(onClick = { vm.updateSettings { it.copy(bodyWeightGoalKg = null, bodyWeightGoalStartKg = null, bodyWeightGoalDateMs = null) }; onClose() }) { Text(stringResource(R.string.goal_remove)) }
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
    val start = settings.bodyWeightGoalStartKg
    val progress = remember(weights, goal, start) { goal?.let { com.bestrong.core.stats.BodyWeightGoal.progress(weights, it, start) } }
    val labels = listOf(
        stringResource(R.string.f_fat) to { m: BodyMeasurement -> m.bodyFatPct },
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

/** Diferencia (en la unidad) por debajo de la cual se considera el mismo objetivo: el texto va con un decimal. */
private const val SAME_GOAL_TOLERANCE = 0.05

/** Cintura/altura y FFMI con las últimas medidas (hace falta la altura del perfil). */
@Composable
private fun BodyCompositionCard(vm: ProfileViewModel, list: List<BodyMeasurement>) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sorted = list.sortedByDescending { it.dateEpochMs }
    val waist = sorted.firstNotNullOfOrNull { it.waistCm }
    // Peso y grasa de la misma medición (mezclar fechas falsea el FFMI)
    val both = sorted.firstOrNull { it.weightKg != null && it.bodyFatPct != null }
    val weight = both?.weightKg
    val fat = both?.bodyFatPct
    if (waist == null && (weight == null || fat == null)) return
    val height = settings.heightCm
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(stringResource(R.string.body_comp_title))
            // La cintura objetivo no necesita la altura
            waist?.let { WaistGoalLine(vm, sorted.mapNotNull { m -> m.waistCm?.let { m.dateEpochMs to it } }.reversed(), settings.waistGoalCm) }
            if (height == null) {
                Text(stringResource(R.string.body_comp_need_height), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            waist?.let { com.bestrong.core.stats.BodyComposition.waistToHeight(it, height) }?.let { WaistToHeightLine(it) }
            if (weight != null && fat != null) {
                com.bestrong.core.stats.BodyComposition.ffmi(weight, fat, height)?.let { f ->
                    Text(stringResource(R.string.body_comp_ffmi, "%.1f".format(f)))
                }
            }
            Text(stringResource(if (settings.sex == com.bestrong.core.stats.Sex.FEMALE) R.string.body_comp_hint_female else R.string.body_comp_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** «Cintura/altura: 0,45 · saludable». */
@Composable
private fun WaistToHeightLine(ratio: Double) {
    val label = stringResource(
        when (com.bestrong.core.stats.BodyComposition.waistToHeightRange(ratio)) {
            com.bestrong.core.stats.BodyRange.LOW -> R.string.body_range_low
            com.bestrong.core.stats.BodyRange.HEALTHY -> R.string.body_range_healthy
            com.bestrong.core.stats.BodyRange.HIGH -> R.string.body_range_high
        },
    )
    Text(stringResource(R.string.body_comp_whtr, "%.2f".format(ratio), label))
}

/**
 * «Cintura objetivo: 80 cm · faltan 4 cm» (con fecha estimada si la tendencia de las medidas lo permite) y el
 * botón para fijarla o cambiarla. [points]: (fecha, cm) de la más antigua a la última.
 */
@Composable
private fun WaistGoalLine(vm: ProfileViewModel, points: List<Pair<Long, Double>>, goalCm: Double?) {
    var editing by remember { mutableStateOf(false) }
    val waistCm = points.last().second
    Row(verticalAlignment = Alignment.CenterVertically) {
        // La misma estimación que el peso objetivo, con la tendencia de las medidas de cintura
        val eta = remember(points, goalCm) { goalCm?.let { com.bestrong.core.stats.BodyWeightGoal.progress(points, it)?.etaEpochMs } }
        val text = when {
            goalCm == null -> stringResource(R.string.waist_goal_none)
            waistCm <= goalCm + WAIST_GOAL_TOLERANCE_CM -> stringResource(R.string.waist_goal_reached, formatWeight(goalCm))
            eta != null -> stringResource(R.string.weight_goal_eta, formatWeight(waistCm - goalCm) + " cm", formatWeight(goalCm) + " cm", formatDate(eta))
            else -> stringResource(R.string.waist_goal_left, formatWeight(goalCm), formatWeight(waistCm - goalCm))
        }
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { editing = true }) { Text(stringResource(R.string.goal_set)) }
    }
    if (editing) {
        var input by remember { mutableStateOf(goalCm?.let(::formatWeight) ?: "") }
        val parsed = input.replace(',', '.').toDoubleOrNull()?.takeIf { it in MIN_WAIST_GOAL_CM..MAX_WAIST_GOAL_CM }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.waist_goal_title)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.f_waist)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(enabled = parsed != null, onClick = {
                    vm.updateSettings { it.copy(waistGoalCm = parsed) }
                    editing = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.updateSettings { it.copy(waistGoalCm = null) }
                    editing = false
                }) { Text(stringResource(R.string.goal_remove)) }
            },
        )
    }
}

/** Margen (cm) para dar la cintura objetivo por conseguida: la cinta no da más precisión. */
private const val WAIST_GOAL_TOLERANCE_CM = 0.5
private const val MIN_WAIST_GOAL_CM = 40.0
private const val MAX_WAIST_GOAL_CM = 200.0

/** Plazos que se pueden elegir para el peso objetivo (semanas). */
private val GOAL_WEEKS = listOf(4, 8, 12, 16, 24)

/** Elección «la fecha que ya había» en el diálogo del peso objetivo. */
private const val KEEP_DATE = -1

/**
 * Ajustes con el peso objetivo guardado: sin cambiar el objetivo (salvo redondeos de la unidad) se conserva
 * el peso de partida (los antiguos toman el que se deduce al mostrarlos); el plazo es [weeks] ([KEEP_DATE] =
 * la fecha que había, que no vale si cambia el objetivo; 0 = sin fecha).
 */
private fun saveWeightGoal(s: com.bestrong.mobile.data.UserSettings, kg: Double?, weeks: Int, points: List<Pair<Long, Double>>, unit: WeightUnit): com.bestrong.mobile.data.UserSettings {
    val old = s.bodyWeightGoalKg
    val sameGoal = old != null && kg != null && kotlin.math.abs(kg - old) < unit.toKg(SAME_GOAL_TOLERANCE)
    val kept = if (sameGoal) {
        s.copy(bodyWeightGoalStartKg = s.bodyWeightGoalStartKg ?: com.bestrong.core.stats.BodyWeightGoal.inferStart(points))
    } else {
        s.copy(bodyWeightGoalKg = kg, bodyWeightGoalStartKg = points.lastOrNull()?.second)
    }
    val date = when {
        weeks == KEEP_DATE -> s.bodyWeightGoalDateMs.takeIf { sameGoal }
        weeks > 0 -> System.currentTimeMillis() + weeks * 7L * 86_400_000L
        else -> null
    }
    return kept.copy(bodyWeightGoalDateMs = date)
}
