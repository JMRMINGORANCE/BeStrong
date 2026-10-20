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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.alpha
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
import java.time.DayOfWeek
import java.time.format.TextStyle

/** «Hoy toca»: la sesión del programa activo con sus cargas. */
@Composable
fun TodayCard(session: ProgramSession, onStart: () -> Unit, onStopProgram: () -> Unit) {
    val unit = LocalWeightUnit.current
    var menu by remember { mutableStateOf(false) }
    com.bestrong.mobile.ui.components.HeroCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.today_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                    Text(session.routine.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            com.bestrong.mobile.ui.components.ExerciseThumbStrip(session.routine.exercises.map { it.exercise }, 44.dp, modifier = Modifier.padding(vertical = 8.dp))
            // Los primeros con su carga (la tira de arriba ya los enseña todos): el botón de empezar sigue a la vista
            session.routine.exercises.take(TODAY_SHOWN).forEach { re ->
                val weights = re.sets.map { it.weightKg }.distinct()
                val reps = re.sets.map { it.reps }.distinct()
                val load = when {
                    weights.all { it == 0.0 } -> "${re.sets.size} × ${reps.joinToString("/")}"
                    weights.size == 1 && reps.size == 1 -> "${re.sets.size} × ${reps[0]} · ${unit.format(weights[0], withUnit = true)}"
                    else -> re.sets.joinToString("  ") { "${unit.format(it.weightKg)}×${it.reps}" } + " ${unit.label}"
                }
                Row {
                    Text(re.exercise.displayName(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Con series distintas la carga es larga: no deja sin sitio al nombre (pasa a dos líneas)
                    Text(
                        load,
                        Modifier.padding(start = 8.dp).weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        maxLines = 2,
                    )
                }
                if (re.notes.isNotBlank()) Text(re.notes, style = MaterialTheme.typography.labelSmall)
            }
            (session.routine.exercises.size - TODAY_SHOWN).takeIf { it > 0 }?.let { n ->
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.share_more_exercises, n, n), style = MaterialTheme.typography.labelMedium, modifier = Modifier.alpha(0.8f))
            }
            HeroButton(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Default.PlayArrow, null)
                Text(stringResource(R.string.today_start), Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Sin programa: sugerencia de la rutina que toca (la que lleva más tiempo sin hacerse). */
@Composable
fun SuggestedRoutineCard(routine: Routine, onStart: () -> Unit) {
    com.bestrong.mobile.ui.components.HeroCard {
        Text(stringResource(R.string.today_suggested), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
        Text(routine.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        // Cuántos ejercicios y cuánto se tarda, para decidir de un vistazo
        val minutes = remember(routine) { (com.bestrong.core.session.WorkoutEditor.estimatedRemainingSeconds(routine.toWorkout(0)) + 59) / 60 }
        Text(
            listOfNotNull(
                androidx.compose.ui.res.pluralStringResource(R.plurals.exercise_count, routine.exercises.size, routine.exercises.size),
                stringResource(R.string.routine_estimated, minutes).takeIf { minutes > 0 },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        com.bestrong.mobile.ui.components.ExerciseThumbStrip(routine.exercises.map { it.exercise }, 44.dp, modifier = Modifier.padding(vertical = 6.dp))
        HeroButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null)
            Text(stringResource(R.string.routine_start), Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
        }
    }
}

/** Botón sobre el degradado de marca: oscuro (o blanco en el tema claro) para que destaque. */
@Composable
fun HeroButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        // Mínimo, no fijo: con letra grande o «Lancer la routine» el texto pasa a dos líneas sin cortarse
        modifier = modifier.heightIn(min = 52.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        content = content,
    )
}

/** Semana: anillo con las sesiones frente al objetivo y los días entrenados (L–D); tocarla cambia el objetivo. */
@Composable
fun WeekStrip(week: WeekProgress, canChangeGoal: Boolean, onGoalChange: (Int) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by remember { mutableStateOf(false) }
    com.bestrong.mobile.ui.components.Panel(onClick = if (canChangeGoal) ({ editing = true }) else null, onClickLabel = stringResource(R.string.week_goal_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(58.dp)) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { (week.trainedDays.size.toFloat() / week.goal).coerceAtMost(1f) },
                    modifier = Modifier.size(58.dp),
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                Text("${week.trainedDays.size}/${week.goal}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(stringResource(R.string.week_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(
                    stringResource(R.string.week_sessions, week.trainedDays.size, week.goal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                week.adherence?.let {
                    Text(stringResource(R.string.week_adherence, (it * 100).toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Racha de semanas seguidas, como motivación
            if (week.streak >= 2) {
                com.bestrong.mobile.ui.components.MetaPill(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.week_streak, week.streak, week.streak),
                    Modifier.padding(end = 8.dp),
                )
            }
            if (canChangeGoal) Icon(Icons.Default.Edit, stringResource(R.string.week_goal_title), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { d -> DayCircle(d, week, locale) }
        }
    }
    if (editing) WeekGoalDialog(week.goal, onPick = { onGoalChange(it); editing = false }) { editing = false }
}

/** Objetivo de sesiones por semana: de 1 a 7. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WeekGoalDialog(goal: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.week_goal_title)) },
        text = {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in 1..7) {
                    androidx.compose.material3.FilterChip(selected = n == goal, onClick = { onPick(n) }, label = { Text("$n", fontWeight = FontWeight.Bold) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
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
    val lang = com.bestrong.mobile.ui.components.appLanguage()
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

/**
 * «Hace un año: Press banca 70×8 → 85×8 (+21 %)»; tocar una línea abre la ficha del ejercicio (su progreso
 * del año). Se puede descartar (vuelve a los 30 días).
 */
@Composable
fun YearAgoCard(vm: HomeViewModel, onOpenExercise: (String) -> Unit = {}) {
    val items by vm.yearAgo.collectAsStateWithLifecycle()
    val tips = com.bestrong.mobile.ui.help.LocalTips.current
    val unit = com.bestrong.mobile.ui.components.LocalWeightUnit.current
    // Descartada, no vuelve en 30 días: la ventana de ±7 días repite las mismas comparaciones muchos días seguidos
    val today = java.time.LocalDate.now()
    val tipId = "year_ago:$today"
    if (items.isEmpty() || yearAgoDismissedRecently(tips.dismissed, today)) return
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.year_ago_title), fontWeight = FontWeight.Bold)
            items.forEach { c ->
                val pct = kotlin.math.round(c.change * 100).toInt()
                Text(
                    stringResource(
                        R.string.year_ago_line,
                        c.exercise.displayName(),
                        unit.format(c.thenWeightKg) + "×" + c.thenReps,
                        unit.format(c.nowWeightKg) + "×" + c.nowReps + " " + unit.label,
                        (if (pct >= 0) "+" else "") + pct,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clickable(onClickLabel = c.exercise.displayName()) { onOpenExercise(c.exercise.id) }.padding(vertical = 4.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = { tips.onDismiss(tipId) }) { Text(stringResource(R.string.got_it)) }
            }
        }
    }
}

/** Días que la tarjeta «Hace un año» no vuelve tras descartarla. */
private const val YEAR_AGO_SNOOZE_DAYS = 30L

/** Si se descartó «Hace un año» (clave «year_ago:fecha») en los últimos [YEAR_AGO_SNOOZE_DAYS] días. */
private fun yearAgoDismissedRecently(dismissed: Set<String>, today: java.time.LocalDate): Boolean = dismissed.any { key ->
    // «year_ago:2026-10-14»; las de la versión anterior eran por mes («year_ago:2026-10»): cuentan desde el día 1
    val date = key.removePrefix("year_ago:").takeIf { it != key }?.let {
        runCatching { java.time.LocalDate.parse(it) }.getOrNull() ?: runCatching { java.time.YearMonth.parse(it).atDay(1) }.getOrNull()
    }
    date != null && !date.isBefore(today.minusDays(YEAR_AGO_SNOOZE_DAYS))
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
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
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
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
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
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
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
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
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
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.intake_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.intake_protein, intake.proteinG, range?.let { "${it.first}–${it.last}" } ?: "?"))
            range?.let { LinearProgressIndicator(progress = { (intake.proteinG.toFloat() / it.first).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
            Text(stringResource(R.string.intake_water, intake.waterMl))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Mitad y mitad: en francés («+20 g de protéines») el segundo no queda aplastado
                FilledTonalButton(onClick = { vm.addIntake(proteinG = 20) }, Modifier.weight(1f)) { Text(stringResource(R.string.intake_add_protein), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                FilledTonalButton(onClick = { vm.addIntake(waterMl = 250) }, Modifier.weight(1f)) { Text(stringResource(R.string.intake_add_water), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
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

/** Objetivos de km de cardio por semana que se pueden elegir. */
private val CARDIO_GOALS_KM = listOf(5.0, 10.0, 20.0, 30.0, 50.0)

/** Objetivos en minutos por semana: la mitad, lo recomendado por la OMS (150) y el doble. */
private val CARDIO_GOALS_MIN = listOf(75, 150, 300)

/** «12,3 de 20 km» o «95 de 150 min» con su barra, o lo hecho si no hay objetivo. */
@Composable
private fun CardioWeekProgress(week: HomeViewModel.CardioWeek) {
    val km = week.meters / 1000
    val minutes = (week.seconds / 60).toInt()
    val goalKm = week.goalKm
    val goalMin = week.goalMin
    val (text, progress) = when {
        goalMin != null -> stringResource(R.string.cardio_week_goal_min, minutes, goalMin) to minutes.toFloat() / goalMin
        goalKm != null -> stringResource(R.string.cardio_week_goal, com.bestrong.core.util.formatKmLike(km, goalKm), com.bestrong.core.util.formatGoalKm(goalKm)) to (km / goalKm).toFloat()
        else -> stringResource(R.string.cardio_week, "%.1f".format(km)) to null
    }
    Text(text, fontWeight = FontWeight.Bold)
    progress?.let { p -> androidx.compose.material3.LinearProgressIndicator(progress = { p.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
    if (week.seconds > 0 && goalMin == null) Text(com.bestrong.core.util.formatDuration(week.seconds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (week.streak >= 2) Text(stringResource(R.string.cardio_streak, week.streak), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    cardioPace(week)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** «Faltan 4,3 km · 2,2 km al día» hasta el domingo (hoy incluido); null sin objetivo o ya cumplido. */
@Composable
private fun cardioPace(week: HomeViewModel.CardioWeek): String? {
    val daysLeft = 8 - java.time.LocalDate.now().dayOfWeek.value
    val pace = com.bestrong.core.stats.CardioPace.of(week.goalKm, week.goalMin, week.meters, week.seconds, daysLeft) ?: return null
    return if (pace.inMinutes) {
        stringResource(R.string.cardio_pace_min, kotlin.math.ceil(pace.left).toInt(), kotlin.math.ceil(pace.perDay).toInt())
    } else {
        stringResource(R.string.cardio_pace_km, "%.1f".format(maxOf(pace.left, 0.1)), "%.1f".format(maxOf(pace.perDay, 0.1)))
    }
}

/** Objetivo a medida: el número y luego km o min (un número que no vale no cambia nada). */
@Composable
private fun CustomCardioGoal(vm: HomeViewModel, current: HomeViewModel.CardioWeek, onDone: () -> Unit) {
    // Empieza con el objetivo de ahora, para retocarlo
    var text by remember { mutableStateOf(current.goalMin?.toString() ?: current.goalKm?.let { com.bestrong.core.util.formatGoalKm(it) }.orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    text = v.filter { it.isDigit() || it == ',' || it == '.' }.take(7)
                    invalid = false
                },
                isError = invalid,
                singleLine = true,
                // Como etiqueta del campo: TalkBack dice qué se escribe, no solo «cuadro de edición»
                label = { Text(stringResource(R.string.cardio_goal_custom)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(onClick = { if (vm.setCustomCardioGoal(text, inMinutes = false)) onDone() else invalid = true }) { Text("km") }
            androidx.compose.material3.TextButton(onClick = { if (vm.setCustomCardioGoal(text, inMinutes = true)) onDone() else invalid = true }) { Text("min") }
        }
    }
}

/**
 * «Cardio esta semana: 12,3 / 20 km» (solo si hay cardio esta semana o un objetivo puesto); tocarla deja poner o
 * quitar el objetivo.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CardioWeekCard(vm: HomeViewModel) {
    val week by vm.cardioWeek.collectAsStateWithLifecycle()
    if (!week.worthShowing) return
    var editing by remember { mutableStateOf(false) }
    com.bestrong.mobile.ui.components.Panel(onClick = { editing = true }, contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardioWeekProgress(week)
        }
    }
    if (editing) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.cardio_goal_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        androidx.compose.material3.FilterChip(selected = week.goalKm == null && week.goalMin == null, onClick = { vm.setWeeklyCardioGoal(null, null); editing = false }, label = { Text("—") })
                        CARDIO_GOALS_KM.forEach { g ->
                            androidx.compose.material3.FilterChip(selected = week.goalKm == g, onClick = { vm.setWeeklyCardioGoal(g, null); editing = false }, label = { Text("${g.toInt()} km") })
                        }
                        // En minutos, para el cardio sin distancia (elíptica, bici estática, HIIT)
                        CARDIO_GOALS_MIN.forEach { g ->
                            androidx.compose.material3.FilterChip(selected = week.goalMin == g, onClick = { vm.setWeeklyCardioGoal(null, g); editing = false }, label = { Text("$g min") })
                        }
                    }
                    CustomCardioGoal(vm, week) { editing = false }
                    CardioNudgeSwitch(vm)
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

/** «Aviso el jueves si voy por detrás» (lo que falta y a qué ritmo). */
@Composable
private fun CardioNudgeSwitch(vm: HomeViewModel) {
    val on by vm.cardioNudge.collectAsStateWithLifecycle()
    val day by vm.cardioNudgeDay.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.cardio_nudge_setting), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.Switch(checked = on, onCheckedChange = vm::setCardioNudge)
    }
    // Qué día (por la tarde) llega el aviso
    if (on) {
        val locale = java.util.Locale.getDefault()
        // Abreviados («lun», «Mon»): con una letra se repiten (M, T, S); bajan de línea si no caben
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            java.time.DayOfWeek.entries.forEach { d ->
                androidx.compose.material3.FilterChip(
                    selected = d.value == day,
                    onClick = { vm.setCardioNudgeDay(d.value) },
                    label = { Text(d.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, locale)) },
                )
            }
        }
    }
}

/** Un día de la semana: relleno si se entrenó, con aro si es hoy o tiene algo planificado. */
@Composable
private fun DayCircle(d: DayOfWeek, week: WeekProgress, locale: java.util.Locale) {
    val done = d in week.trainedDays
    val planned = d in week.plannedDays && !done
    val name = d.getDisplayName(TextStyle.FULL, locale)
    val state = stringResource(
        when {
            done -> R.string.day_trained
            planned -> R.string.day_planned
            else -> R.string.day_not_trained
        },
    )
    val todayLabel = stringResource(R.string.day_today)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .background(
                if (done) com.bestrong.mobile.ui.components.brandGradient() else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest),
                CircleShape,
            )
            .then(
                when {
                    d == week.today -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    // Planificado y aún sin hacer: un aro más suave
                    planned -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), CircleShape)
                    else -> Modifier
                },
            )
            // «Hoy» también en voz alta: a la vista solo lo distingue el grosor del aro
            .semantics { contentDescription = "$name: $state" + if (d == week.today) ", $todayLabel" else "" },
    ) {
        Text(
            d.getDisplayName(TextStyle.NARROW, locale),
            color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Ejercicios de la sesión de hoy que se listan con su carga en la tarjeta. */
private const val TODAY_SHOWN = 4
