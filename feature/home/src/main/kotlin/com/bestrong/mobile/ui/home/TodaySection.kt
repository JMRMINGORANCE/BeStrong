package com.bestrong.mobile.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Routine
import com.bestrong.core.training.PeriodizedProgram
import com.bestrong.core.training.ProgramSession
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.components.uiLanguage
import java.time.DayOfWeek
import java.time.format.TextStyle

/** «Hoy toca»: la sesión del programa activo con sus cargas. */
@Composable
fun TodayCard(session: ProgramSession, onStart: () -> Unit, onStopProgram: () -> Unit) {
    val unit = LocalWeightUnit.current
    var menu by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.today_title), style = MaterialTheme.typography.labelLarge)
                    Text(session.routine.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val label = if (session.program.weeksPerCycle > 1) {
                        stringResource(R.string.today_cycle_week, session.cycle, session.week, session.program.weeksPerCycle)
                    } else {
                        stringResource(R.string.today_session_n, session.completedSessions + 1)
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.program_stop)) }, onClick = { menu = false; onStopProgram() })
                    }
                }
            }
            session.routine.exercises.forEach { re ->
                val weights = re.sets.map { it.weightKg }.distinct()
                val reps = re.sets.map { it.reps }.distinct()
                val load = when {
                    weights.all { it == 0.0 } -> "${re.sets.size} × ${reps.joinToString("/")}"
                    weights.size == 1 && reps.size == 1 -> "${re.sets.size} × ${reps[0]} · ${unit.format(weights[0], withUnit = true)}"
                    else -> re.sets.joinToString("  ") { "${unit.format(it.weightKg)}×${it.reps}" } + " ${unit.label}"
                }
                Row {
                    Text(re.exercise.displayName(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(load, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                if (re.notes.isNotBlank()) Text(re.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Default.PlayArrow, null)
                Text(stringResource(R.string.today_start), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Sin programa: sugerencia de la rutina que toca (la que lleva más tiempo sin hacerse). */
@Composable
fun SuggestedRoutineCard(routine: Routine, onStart: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_suggested), style = MaterialTheme.typography.labelLarge)
                Text(routine.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onStart) { Text(stringResource(R.string.routine_start)) }
        }
    }
}

/** Semana: días entrenados (L–D), sesiones frente al objetivo y adherencia. */
@Composable
fun WeekStrip(week: WeekProgress, canChangeGoal: Boolean, onGoalChange: (Int) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.week_sessions, week.trainedDays.size, week.goal),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (canChangeGoal) {
                    val lessGoal = stringResource(R.string.week_goal_less)
                    val moreGoal = stringResource(R.string.week_goal_more)
                    TextButton(onClick = { onGoalChange(week.goal - 1) }, enabled = week.goal > 1, modifier = Modifier.semantics { contentDescription = lessGoal }) { Text("−") }
                    TextButton(onClick = { onGoalChange(week.goal + 1) }, enabled = week.goal < 7, modifier = Modifier.semantics { contentDescription = moreGoal }) { Text("+") }
                }
            }
            LinearProgressIndicator(progress = { (week.trainedDays.size.toFloat() / week.goal).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { d ->
                    val done = d in week.trainedDays
                    val name = d.getDisplayName(TextStyle.FULL, locale)
                    val state = stringResource(if (done) R.string.day_trained else R.string.day_not_trained)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .then(if (done) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                            .then(if (d == week.today) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                            .semantics { contentDescription = "$name: $state" },
                    ) {
                        Text(
                            d.getDisplayName(TextStyle.NARROW, locale),
                            color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            week.adherence?.let {
                Text(stringResource(R.string.week_adherence, (it * 100).toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Guarda un mapa de textos (id → valor escrito) en el estado de la pantalla. */
private val TextMapSaver = listSaver<SnapshotStateMap<String, String>, String>(
    save = { m -> m.flatMap { (k, v) -> listOf(k, v) } },
    restore = { l -> mutableStateMapOf<String, String>().apply { l.chunked(2).forEach { (k, v) -> put(k, v) } } },
)

/**
 * Preparar un programa con progresión: training max de los ejercicios principales.
 * Pantalla completa (no diálogo): con el teclado abierto en la pantalla exterior del Fold
 * un diálogo con cuatro campos no cabe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramSetupScreen(program: PeriodizedProgram, suggested: (String) -> Double?, onConfirm: (Map<String, Double>) -> Unit, onDismiss: () -> Unit) {
    val unit = LocalWeightUnit.current
    val values = rememberSaveable(program.id, saver = TextMapSaver) {
        mutableStateMapOf<String, String>().apply { program.mainLifts.forEach { id -> put(id, suggested(id)?.let { unit.format(it) }.orEmpty()) } }
    }

    // Todos los básicos con un TM válido: si falta uno, el programa no podría calcular sus cargas
    fun tmOf(id: String) = unit.parseToKg(values[id].orEmpty())?.takeIf { it > 0 }
    val lifts = program.mainLifts.filter { ExerciseLibrary.byId(it) != null }
    val valid = lifts.all { tmOf(it) != null }
    val lang = uiLanguage()
    BackHandler(onBack = onDismiss)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(program.name(lang), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        bottomBar = {
            Button(
                onClick = { onConfirm(lifts.associateWith { tmOf(it)!! }) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
            ) { Text(stringResource(R.string.program_start)) }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(program.description(lang), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.program_tm_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!valid) Text(stringResource(R.string.program_tm_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            program.mainLifts.forEach { id ->
                val ex = ExerciseLibrary.byId(id) ?: return@forEach
                OutlinedTextField(
                    value = values[id].orEmpty(),
                    onValueChange = { values[id] = it },
                    label = { Text(stringResource(R.string.program_tm_label, ex.displayName(), unit.label)) },
                    singleLine = true,
                    isError = tmOf(id) == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** «Te saltaste «Push» del martes»: hacerla hoy, pasarla a mañana o dejarlo estar. */
@Composable
fun MissedSessionCard(vm: HomeViewModel, onOpenWorkout: () -> Unit) {
    val missed by vm.missedSession.collectAsStateWithLifecycle()
    val tips = com.bestrong.mobile.ui.help.LocalTips.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val (day, routine, canMove) = missed ?: return
    // Una vez atendida (hecha hoy o descartada) no vuelve a salir esa semana
    val tipId = "missed:" + java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) + ":" + day.value
    if (tipId in tips.dismissed) return
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.missed_session, routine.displayName, day.getDisplayName(java.time.format.TextStyle.FULL, locale)), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = { tips.onDismiss(tipId) }) { Text(stringResource(R.string.missed_skip)) }
                if (canMove) {
                    androidx.compose.material3.TextButton(onClick = { vm.moveMissedToTomorrow(day) }) { Text(stringResource(R.string.missed_tomorrow)) }
                }
                androidx.compose.material3.TextButton(onClick = { tips.onDismiss(tipId); vm.startRoutine(routine, onOpenWorkout) }) { Text(stringResource(R.string.missed_today)) }
            }
        }
    }
}

/** «No haces «Pierna B» desde hace 75 días. ¿Archivarla?»: una a una, y se puede descartar. */
@Composable
fun StaleRoutineCard(vm: HomeViewModel) {
    val stale by vm.staleRoutines.collectAsStateWithLifecycle()
    val tips = com.bestrong.mobile.ui.help.LocalTips.current
    val (routine, days) = stale.firstOrNull { "stale:${it.first.id}" !in tips.dismissed } ?: return
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.stale_routine, routine.displayName, days), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = { tips.onDismiss("stale:${routine.id}") }) { Text(stringResource(R.string.stale_keep)) }
                androidx.compose.material3.TextButton(onClick = { vm.setArchived(routine, true) }) { Text(stringResource(R.string.routine_archive)) }
            }
        }
    }
}

/** «Esta semana vas corto de: Espalda 4 de 10 series». */
@Composable
fun LaggingMusclesCard(vm: HomeViewModel, onOpenMuscle: (com.bestrong.core.model.MuscleGroup) -> Unit = {}) {
    val lagging by vm.laggingMuscles.collectAsStateWithLifecycle()
    if (lagging.isEmpty()) return
    Card {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(stringResource(R.string.lagging_muscles_title), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            // Cada músculo abre la biblioteca con sus ejercicios
            lagging.forEach { (muscle, sets) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = stringResource(R.string.lagging_muscle_open)) { onOpenMuscle(muscle) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.lagging_muscle_line, muscle.label(), "%.1f".format(sets).removeSuffix(",0").removeSuffix(".0"), vm.weeklySetsTarget),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Cómo llegas hoy: sueño y pulso en reposo (Health Connect) y la última valoración. */
@Composable
fun ReadinessCard(vm: HomeViewModel) {
    val r by vm.readiness.collectAsStateWithLifecycle()
    val connected by vm.healthConnected.collectAsStateWithLifecycle()
    val report = r ?: return
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.readiness_title), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("${report.score}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { report.score / 100f }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(
                    when (report.level) {
                        com.bestrong.core.training.ReadinessReport.Level.HIGH -> R.string.readiness_high
                        com.bestrong.core.training.ReadinessReport.Level.NORMAL -> R.string.readiness_normal
                        com.bestrong.core.training.ReadinessReport.Level.LOW -> R.string.readiness_low
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (connected) {
                Text(
                    stringResource(R.string.readiness_detail, report.sleepHours?.let { "%.1f".format(it) } ?: "--", report.restingHr?.toString() ?: "--", report.baselineRestingHr?.toString() ?: "--"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(stringResource(R.string.readiness_connect), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Proteína del día (objetivo según tu peso) y agua, con botones rápidos. */
@Composable
fun IntakeCard(vm: HomeViewModel) {
    val intake by vm.intake.collectAsStateWithLifecycle()
    val bw by vm.bodyWeightKg.collectAsStateWithLifecycle()
    val range = bw?.takeIf { it > 0 }?.let { com.bestrong.core.training.Nutrition.proteinRangeGrams(it) }?.takeIf { it.first > 0 }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.intake_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.intake_protein, intake.proteinG, range?.let { "${it.first}–${it.last}" } ?: "?"))
            range?.let { LinearProgressIndicator(progress = { (intake.proteinG.toFloat() / it.first).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
            Text(stringResource(R.string.intake_water, intake.waterMl))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.addIntake(proteinG = 20) }) { Text(stringResource(R.string.intake_add_protein)) }
                FilledTonalButton(onClick = { vm.addIntake(waterMl = 250) }) { Text(stringResource(R.string.intake_add_water)) }
            }
            BodyWeightQuickEntry(bw) { vm.logBodyWeight(it) }
        }
    }
}

/** Último peso corporal y botón para apuntar el de hoy sin ir a Medidas. */
@Composable
private fun BodyWeightQuickEntry(lastKg: Double?, onSave: (Double) -> Unit) {
    val unit = com.bestrong.mobile.ui.components.LocalBodyUnit.current
    var editing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            lastKg?.let { stringResource(R.string.body_weight_last, unit.format(it, withUnit = true)) } ?: stringResource(R.string.body_weight_none),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.TextButton(onClick = { editing = true }) { Text(stringResource(R.string.body_weight_log)) }
    }
    if (editing) {
        var text by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(lastKg?.let { unit.format(it) }.orEmpty()) }
        val kg = unit.parseToKg(text)?.takeIf { it in 20.0..400.0 }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.body_weight_log)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    text,
                    { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                    label = { Text(unit.label) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { kg?.let(onSave); editing = false }, enabled = kg != null) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
