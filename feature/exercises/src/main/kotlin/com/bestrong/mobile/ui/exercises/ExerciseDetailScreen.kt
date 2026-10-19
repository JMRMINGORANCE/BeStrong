package com.bestrong.mobile.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.SetType
import com.bestrong.core.stats.RecordType
import com.bestrong.core.util.formatVolume
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.BarChart
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.LineChart
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.components.reasonText
import com.bestrong.mobile.ui.components.spokenAs
import com.bestrong.mobile.ui.workout.describeSet
import com.bestrong.mobile.ui.workout.setRemark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(vm: ExercisesViewModel, exerciseId: String, onBack: (() -> Unit)?) {
    val unit = LocalWeightUnit.current
    var includeVariants by rememberSaveable(exerciseId) { mutableStateOf(false) }
    val flow = remember(exerciseId, includeVariants) { vm.detail(exerciseId, includeVariants) }
    val state by flow.collectAsStateWithLifecycle()
    val s = state ?: return
    var metric by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (editing) {
        ExerciseFormDialog(initial = s.exercise, onDismiss = { editing = false }, onSave = { vm.updateCustom(it); editing = false })
    }
    if (confirmDelete) {
        val used = remember { vm.routinesUsing(s.exercise.id) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_exercise_q, s.exercise.name)) },
            text = { Text(if (used > 0) stringResource(R.string.delete_exercise_used, used) else stringResource(R.string.delete_exercise_body)) },
            confirmButton = { Button(onClick = { confirmDelete = false; vm.deleteCustom(s.exercise.id); onBack?.invoke() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(s.exercise.displayName()) },
            navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } } },
            actions = {
                if (s.recentSessions.isNotEmpty()) ExportExerciseButton(vm, s.exercise)
                val hidden by vm.hidden.collectAsStateWithLifecycle()
                val isHidden = s.exercise.id in hidden
                IconButton(onClick = { vm.toggleHidden(s.exercise.id) }) {
                    Icon(
                        if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        stringResource(if (isHidden) R.string.exercise_unhide else R.string.exercise_hide),
                    )
                }
                if (s.exercise.isCustom) {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, stringResource(R.string.edit_exercise)) }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete_exercise))
                    }
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ExerciseMediaSection(vm, s.exercise) { ExerciseAbout(s.exercise) } }
            if (com.bestrong.core.stats.ExerciseGoals.supports(s.exercise.tracking)) item { ExerciseGoalCard(vm, s) }
            if (s.exercise.tracking == com.bestrong.core.model.TrackingType.WEIGHT_REPS) item { RepRangeCard(vm, s.exercise.id) }
            if (s.variants.size > 1) {
                item {
                    // Variantes enlazadas: historial y récords juntos o por separado
                    val names = s.variants.filter { it.id != s.exercise.id }.map { it.displayName() }.joinToString()
                    val desc = stringResource(R.string.include_variants_desc, names)
                    FilterChip(
                        selected = includeVariants,
                        onClick = { includeVariants = !includeVariants },
                        label = { Text(stringResource(R.string.include_variants, s.variants.size - 1)) },
                        modifier = Modifier.semantics { stateDescription = desc },
                    )
                }
            }
            if (s.progress.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.no_data_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.no_data_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@LazyColumn
            }
            item {
                Text(
                    "${s.exercise.primaryMuscle.label()} · ${s.exercise.equipment.label()}" +
                        (s.exercise.secondaryMuscles.takeIf { it.isNotEmpty() }?.map { it.label() }?.joinToString()?.let { stringResource(R.string.secondary_muscles, it) } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Cardio, distancia y tiempo: su gráfica (distancia o tiempo); con peso y reps, la de 1RM y volumen
            val timed = s.exercise.tracking in TIMED_TRACKING
            if (timed) item { CardioProgressChart(s.progress, timeOnly = s.exercise.tracking == com.bestrong.core.model.TrackingType.DURATION) }
            if (!timed) {
                item {
                    val labels = listOf(
                        stringResource(R.string.metric_1rm),
                        stringResource(R.string.metric_max_weight),
                        stringResource(R.string.metric_volume),
                        stringResource(R.string.metric_reps),
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        labels.forEachIndexed { i, l ->
                            SegmentedButton(selected = metric == i, onClick = { metric = i }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) { Text(l) }
                        }
                    }
                }
            }
            if (!timed) {
                item {
                    val points = s.progress.takeLast(30).map { p ->
                        ChartPoint(
                            formatShortDate(p.dateEpochMs),
                            when (metric) { 0 -> p.bestOneRepMax; 1 -> p.heaviestWeight; 2 -> p.volume; else -> p.bestReps.toDouble() },
                        )
                    }
                    // Peso en tu unidad, volumen con su unidad y las reps tal cual
                    val formatter: (Double) -> String = when (metric) {
                        2 -> { v -> unit.formatVolume(v) }
                        3 -> { v -> v.toInt().toString() }
                        else -> { v -> unit.format(v) }
                    }
                    // «¿Voy a más?» de un vistazo: la diferencia entre el primer y el último punto
                    ChartChange(points, if (metric < 2) { v -> unit.format(v, withUnit = true) } else formatter)
                    LineChart(points, Modifier.fillMaxWidth().padding(vertical = 8.dp), valueFormatter = formatter)
                }
            }
            if (s.rpeTrend.size >= RPE_MIN_SESSIONS) item { RpeTrendCard(s.rpeTrend) }
            if (s.exercise.tracking == com.bestrong.core.model.TrackingType.WEIGHT_REPS) item { PlateauHint(vm, exerciseId, s.progress) }
            item { SectionHeader(stringResource(R.string.personal_records)) }
            item {
                com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecordType.entries.forEach { type ->
                            val pr = s.records[type] ?: return@forEach
                            Row(Modifier.fillMaxWidth()) {
                                Text(type.label(), Modifier.weight(1f))
                                Text(
                                    when (type) {
                                        RecordType.MOST_REPS -> stringResource(R.string.n_reps, pr.value.toInt())
                                        RecordType.BEST_SET_VOLUME -> unit.formatVolume(pr.value)
                                        else -> unit.format(pr.value, withUnit = true)
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
            s.nextTarget?.let { t ->
                item {
                    val work = t.sets.firstOrNull { it.type != SetType.WARMUP } ?: t.sets.first()
                    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SectionHeader(stringResource(R.string.next_target))
                            Text(
                                stringResource(R.string.next_target_line, t.sets.count { it.type != SetType.WARMUP }, describeSet(work, s.exercise, unit, vm.weightFactor(s.exercise))),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(t.reasonText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (s.exercise.tracking == com.bestrong.core.model.TrackingType.WEIGHT_REPS) item { RpeLoadCard(vm, s.exercise, s.recentOneRepMax) }
            s.cardioBests?.let { b -> item { CardioBestsCard(s.exercise, b) } }
            if (s.strengthCurve.size >= 2) {
                item {
                    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                        Column(Modifier.padding(16.dp)) {
                            SectionHeader(stringResource(R.string.strength_curve))
                            Text(stringResource(R.string.strength_curve_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val repsLabel = stringResource(R.string.reps_short)
                            BarChart(
                                s.strengthCurve.map { (reps, kg) -> ChartPoint("$reps", unit.fromKg(kg)) },
                                describe = { "${it.label} $repsLabel: ${unit.format(unit.toKg(it.value), withUnit = true)}" },
                            )
                            // Mejores marcas por repeticiones (1RM, 3RM, 5RM…), las que se suelen buscar
                            // N RM = lo más pesado movido al menos N veces (100 kg × 6 también vale como 5RM)
                            val repMaxes = REP_MAXES.mapNotNull { r -> s.strengthCurve.filterKeys { it >= r }.values.maxOrNull()?.let { r to it } }
                            if (repMaxes.isNotEmpty()) {
                                Text(
                                    repMaxes.map { (r, kg) -> stringResource(R.string.rep_max, r, unit.format(kg, withUnit = true)) }.joinToString("  ·  "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.nav_history)) }
            items(s.recentSessions, key = { it.id }) { w ->
                com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                    Column(Modifier.padding(12.dp)) {
                        Text(w.name, fontWeight = FontWeight.SemiBold)
                        Text(formatDate(w.startedAtEpochMs), style = MaterialTheme.typography.bodySmall)
                        // Notas de ese día para el ejercicio («hombro cargado», «agarre más abierto»…)
                        s.sessionNotes[w.id]?.let { Text("✎ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                        w.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.sets }.forEachIndexed { i, set ->
                            Text("${i + 1}. ${describeSet(set, s.exercise, unit, vm.weightFactor(s.exercise))}" + if (set.estimatedOneRepMax > 0) stringResource(R.string.set_line_1rm, unit.format(set.estimatedOneRepMax)) else "")
                            setRemark(set)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

private val REP_MAXES = listOf(1, 3, 5, 8, 10, 12)

/** Exporta a CSV el historial de este ejercicio (para una hoja de cálculo o el entrenador). */
@Composable
private fun ExportExerciseButton(vm: ExercisesViewModel, exercise: com.bestrong.core.model.Exercise) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val done = stringResource(R.string.export_done)
    val failed = stringResource(R.string.export_failed)
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            vm.exportExerciseCsv(uri, exercise.id) { ok -> android.widget.Toast.makeText(context, if (ok) done else failed, android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
    // Nombre en el idioma de la app y sin tildes («Elevación lateral» → elevacion-lateral)
    val plain = java.text.Normalizer.normalize(exercise.displayName(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    val fileName = "bestrong-" + plain.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { exercise.id } + ".csv"
    IconButton(onClick = { launcher.launch(fileName) }) { Icon(Icons.Default.FileDownload, stringResource(R.string.export_exercise_csv)) }
}

private const val RPE_MIN_SESSIONS = 3

/** RPE medio de las últimas sesiones y aviso si cuesta más sin progresar (fatiga). */
@Composable
private fun RpeTrendCard(trend: List<Triple<Long, Double, Double>>) {
    val rising = remember(trend) { com.bestrong.core.stats.Strength.effortRising(trend) }
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.rpe_trend_title), fontWeight = FontWeight.SemiBold)
            LineChart(
                trend.takeLast(20).map { (date, rpe, _) -> ChartPoint(formatShortDate(date), rpe) },
                Modifier.fillMaxWidth(),
                height = 120.dp,
                valueFormatter = { "%.1f".format(it) },
            )
            if (rising) Text(stringResource(R.string.rpe_trend_rising), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Rango de repeticiones del ejercicio sin tener que estar entrenando (lo usa la progresión). */
@Composable
private fun RepRangeCard(vm: ExercisesViewModel, exerciseId: String) {
    val ranges by vm.repRanges.collectAsStateWithLifecycle()
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.rep_range), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.rep_range_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.bestrong.mobile.ui.components.RepRangeChips(ranges[exerciseId]) { vm.setRepRange(exerciseId, it) }
        }
    }
}

/** Reps y RPE de la tabla de cargas: fuerza, hipertrofia y resistencia, de 7 (3 en recámara) al fallo. */
private val RPE_TABLE_REPS = listOf(3, 5, 8, 10, 12)
private val RPE_TABLE_RPES = listOf(7.0, 8.0, 9.0, 10.0)

/**
 * «Cargas por RPE»: qué peso usar para N reps a un RPE, con el 1RM estimado reciente (el mismo del entreno,
 * con las reps en recámara del RPE). Redondeado al material del gimnasio o al incremento del ejercicio.
 */
@Composable
private fun RpeLoadCard(vm: ExercisesViewModel, exercise: com.bestrong.core.model.Exercise, oneRepMaxKg: Double?) {
    val e1rm = oneRepMaxKg ?: return
    val unit = LocalWeightUnit.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Mancuernas como en las filas («suma de las dos» si está activado)
    val factor = if (settings.dumbbellTotal && exercise.equipment == com.bestrong.core.model.Equipment.DUMBBELL) 2 else 1
    fun load(reps: Int, rpe: Double): String = unit.format(vm.roundLoad(exercise, com.bestrong.core.stats.RpeLoad.weightFor(e1rm, reps, rpe), settings) * factor)
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(stringResource(R.string.rpe_table_title))
            Text(stringResource(R.string.rpe_table_hint, unit.format(e1rm * factor, withUnit = true)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reps_short), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                RPE_TABLE_RPES.forEach { Text("@" + it.toString().removeSuffix(".0"), Modifier.weight(1f), fontWeight = FontWeight.Bold) }
            }
            RPE_TABLE_REPS.forEach { reps ->
                Row(Modifier.fillMaxWidth()) {
                    Text("$reps", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    RPE_TABLE_RPES.forEach { Text(load(reps, it), Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** «Mejores marcas» del cardio: distancia y tiempo más largos y mejor ritmo (en su medida: /km, /500 m o km/h). */
@Composable
private fun CardioBestsCard(exercise: com.bestrong.core.model.Exercise, b: com.bestrong.core.stats.CardioBests) {
    val style = com.bestrong.core.util.PaceStyle.of(exercise.id)
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(stringResource(R.string.cardio_bests))
            b.longestDistance?.let { Text(stringResource(R.string.cardio_best_distance, com.bestrong.mobile.ui.workout.cardioText(it.durationSec, it.distanceM, pace = style))) }
            b.longestTime?.takeIf { it !== b.longestDistance }?.let { Text(stringResource(R.string.cardio_best_time, com.bestrong.mobile.ui.workout.cardioText(it.durationSec, it.distanceM, pace = style))) }
            b.bestPace?.let { set ->
                com.bestrong.core.util.paceText(set.durationSec, set.distanceM, style)?.let { Text(stringResource(R.string.cardio_best_pace, it), fontWeight = FontWeight.Bold) }
            }
            // Lo acumulado: últimos 7 y 30 días
            listOf(R.string.cardio_total_week to b.week, R.string.cardio_total_month to b.month).filter { (_, t) -> t.second > 0 || t.first > 0 }.forEach { (label, t) ->
                Text(
                    stringResource(label, com.bestrong.mobile.ui.workout.cardioText(t.second, t.first, withPace = false)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Cardio: distancia (km) o tiempo (min) de cada sesión, en vez del 1RM y el volumen que no tienen sentido aquí. */
@Composable
private fun CardioProgressChart(progress: List<com.bestrong.core.stats.ExerciseProgressPoint>, timeOnly: Boolean = false) {
    var chosenTime by rememberSaveable { mutableStateOf(false) }
    val byTime = timeOnly || chosenTime
    Column {
        val labels = listOf(stringResource(R.string.metric_distance), stringResource(R.string.metric_time))
        if (!timeOnly) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, l ->
                    SegmentedButton(selected = byTime == (i == 1), onClick = { chosenTime = i == 1 }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) { Text(l) }
                }
            }
        }
        val points = progress.takeLast(30).map { p -> ChartPoint(formatShortDate(p.dateEpochMs), if (byTime) p.durationSec / 60.0 else p.distanceM / 1000) }
        LineChart(points, Modifier.fillMaxWidth().padding(vertical = 8.dp), valueFormatter = { "%.1f".format(it) + if (byTime) " min" else " km" })
    }
}

/** Ejercicios que se siguen por distancia o tiempo (no por peso y reps). */
private val TIMED_TRACKING = setOf(com.bestrong.core.model.TrackingType.CARDIO, com.bestrong.core.model.TrackingType.DISTANCE, com.bestrong.core.model.TrackingType.DURATION)

/**
 * «4 sesiones sin mejorar tu 1RM estimado»: con una idea para salir del estancamiento y un botón para probar otro
 * rango de reps (el de fuerza si ya se iba a 8-12, si no el de hipertrofia).
 */
@Composable
private fun PlateauHint(vm: ExercisesViewModel, exerciseId: String, progress: List<com.bestrong.core.stats.ExerciseProgressPoint>) {
    val stalled = remember(progress) { com.bestrong.core.stats.WorkoutStats.sessionsSinceBest(progress) }
    // Tras probar otro rango, el aviso espera unas sesiones para darle tiempo
    val snoozed by vm.plateauSnoozed.collectAsStateWithLifecycle()
    val resting = snoozed[exerciseId]?.let { progress.size < it + PLATEAU_SESSIONS } == true
    if (stalled < PLATEAU_SESSIONS || resting) return
    val ranges by vm.repRanges.collectAsStateWithLifecycle()
    // Siempre otro estímulo: de los rangos altos a uno más pesado y de los bajos (o sin rango, o uno antiguo como
    // «4-6») a hipertrofia
    val next = when (ranges[exerciseId]) {
        "12-20" -> HYPERTROPHY_RANGE
        "10-15" -> "6-10"
        HYPERTROPHY_RANGE, "6-10" -> STRENGTH_RANGE
        else -> HYPERTROPHY_RANGE
    }
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.plateau_hint, stalled), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { vm.snoozePlateau(exerciseId, progress.size) }) { Text(stringResource(R.string.plateau_not_now)) }
            TextButton(onClick = { vm.tryRangeForPlateau(exerciseId, next, progress.size) }) { Text(stringResource(R.string.plateau_try_range, next.replace("-", "–"))) }
        }
    }
}

private const val HYPERTROPHY_RANGE = "8-12"

// Los mismos que los chips de rango (RepRangeChips), para que se vean elegidos
private const val STRENGTH_RANGE = "3-5"

/** Sesiones sin récord de 1RM estimado a partir de las que se habla de estancamiento. */
private const val PLATEAU_SESSIONS = 4

/** Qué trabaja (principal y secundarios), con qué material y cómo se hace, arriba de la ficha. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExerciseAbout(exercise: com.bestrong.core.model.Exercise) {
    val howTo = com.bestrong.core.library.ExerciseInfo.instructions(exercise.id, com.bestrong.mobile.ui.components.uiLanguage())
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // El músculo principal, destacado
        Text(
            exercise.primaryMuscle.label(),
            Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 12.dp, vertical = 5.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        exercise.secondaryMuscles.forEach { com.bestrong.mobile.ui.components.MetaPill(it.label()) }
        com.bestrong.mobile.ui.components.MetaPill("🏋️ " + exercise.equipment.label(), Modifier.spokenAs(exercise.equipment.label()))
    }
    howTo?.let {
        com.bestrong.mobile.ui.components.Panel(Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.exercise_how_title), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** «+7,5 kg desde el 3 mar»: cuánto ha cambiado la métrica en lo que abarca la gráfica (en verde si sube). */
@Composable
private fun ChartChange(points: List<ChartPoint>, format: (Double) -> String) {
    if (points.size < 2) return
    val d = points.last().value - points.first().value
    val sign = when {
        d > 0 -> "+"
        d < 0 -> "−"
        else -> "="
    }
    val text = stringResource(R.string.chart_change_since, if (d == 0.0) sign else sign + format(kotlin.math.abs(d)), points.first().label)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (d > 0) com.bestrong.mobile.ui.theme.statusColors.good else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
