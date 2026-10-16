package com.bestrong.mobile.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.Routine
import com.bestrong.core.training.BodyFocus
import com.bestrong.core.training.Goal
import com.bestrong.core.training.InjuryZone
import com.bestrong.core.training.IntervalPlan
import com.bestrong.core.training.Peaking
import com.bestrong.core.training.RoutineTime
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.theme.statusColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen(
    vm: PlanningViewModel,
    onBack: () -> Unit,
    onEditProgram: (String?) -> Unit,
    onTimer: (IntervalPlan) -> Unit,
    onGuided: (warmup: Boolean, focus: BodyFocus) -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.planning)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { CalendarCard(vm, ui) }
            item { FatigueCard(vm, ui) }
            item { GeneratorCard(vm) }
            item { ProgramsCard(vm, ui, onEditProgram) }
            item { PeakCard(vm, ui) }
            item { CalisthenicsCard(vm, ui) }
            item { TimersCard(onTimer) }
            item { GuidedCard(onGuided) }
            item { InjuriesCard(vm, ui) }
        }
    }
}

@Composable
private fun PlanCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title)
            content()
        }
    }
}

// --- Calendario ---------------------------------------------------------------------------------

@Composable
private fun CalendarCard(vm: PlanningViewModel, ui: PlanningUi) {
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    val weekStart = ui.weekStart.plusWeeks(weekOffset.toLong())
    val plan = ui.state.planFor(weekStart)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var cellWidth by remember { mutableIntStateOf(1) }
    PlanCard(stringResource(R.string.cal_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { weekOffset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.action_back)) }
            Text(
                stringResource(R.string.cal_week_of, formatShortDate(weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { weekOffset++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.action_next)) }
        }
        Text(stringResource(R.string.cal_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().onSizeChanged { cellWidth = (it.width / 7).coerceAtLeast(1) }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEach { day ->
                val routine = plan.routineFor(day)?.let { id -> ui.routines.firstOrNull { it.id == id } }
                var menu by remember { mutableStateOf(false) }
                var dragX by remember { mutableFloatStateOf(0f) }
                val minutes = routine?.let { RoutineTime.estimateMinutes(it) }
                val over = routine?.let { RoutineTime.overBy(it, ui.state.minutesPerSession) } ?: 0
                Box(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (routine != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(day, weekStart, routine?.id) {
                                detectDragGesturesAfterLongPress(
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragX += amount.x
                                    },
                                    onDragEnd = {
                                        val steps = (dragX / cellWidth).roundToInt()
                                        val target = (day.ordinal + steps).coerceIn(0, 6)
                                        dragX = 0f
                                        if (target != day.ordinal && routine != null) vm.move(weekStart, day, DayOfWeek.entries[target])
                                    },
                                    onDragCancel = { dragX = 0f },
                                )
                            }
                            .clickable { menu = true }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(day.getDisplayName(TextStyle.SHORT, locale), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text(
                            routine?.name ?: stringResource(R.string.cal_rest),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        minutes?.let { Text(stringResource(R.string.cal_minutes, it), style = MaterialTheme.typography.labelSmall) }
                        if (over > 0) {
                            val overLabel = stringResource(R.string.cal_over)
                            Text("⚠", color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { contentDescription = overLabel })
                        }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.cal_rest)) }, onClick = { menu = false; vm.assign(weekStart, day, null) })
                        routine?.let { r -> AddToCalendarItem(r, weekStart.plusDays(day.ordinal.toLong()), vm.usualTime, minutes ?: DEFAULT_SESSION_MIN) { menu = false } }
                        // Las archivadas no se ofrecen (las ya asignadas se siguen viendo en el calendario)
                        ui.routines.filterNot { it.archived }.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.name + " · " + stringResource(R.string.cal_minutes, RoutineTime.estimateMinutes(r))) },
                                onClick = { menu = false; vm.assign(weekStart, day, r.id) },
                            )
                        }
                    }
                }
            }
        }
        // Avisos de tiempo: rutinas que no caben en los minutos disponibles
        DayOfWeek.entries.mapNotNull { d -> plan.routineFor(d)?.let { id -> ui.routines.firstOrNull { it.id == id } } }.distinctBy { it.id }
            .forEach { r ->
                val over = RoutineTime.overBy(r, ui.state.minutesPerSession)
                if (over > 0) Text(r.name + ": " + stringResource(R.string.cal_too_long, over), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.gen_minutes), style = MaterialTheme.typography.labelMedium)
            listOf(30, 45, 60, 75, 90).forEach { m ->
                FilterChip(selected = ui.state.minutesPerSession == m, onClick = { vm.setMinutesPerSession(m) }, label = { Text("$m") })
            }
            DuplicateWeekButton { weeks -> vm.duplicateWeek(weekStart, weeks) }
            OutlinedButton(onClick = { vm.exportWeekToCalendar(weekStart) }) { Text(stringResource(R.string.cal_export_ics)) }
        }
    }
}

// --- Fatiga y mesociclo --------------------------------------------------------------------------

@Composable
private fun FatigueCard(vm: PlanningViewModel, ui: PlanningUi) {
    PlanCard(stringResource(R.string.fatigue_title)) {
        val f = ui.fatigue
        if (f == null || f.signals == 0) {
            Text(stringResource(R.string.fatigue_ok), color = statusColors.good)
        } else {
            val parts = buildList {
                if (f.stalledLifts.isNotEmpty()) add(stringResource(R.string.fatigue_stalled, f.stalledLifts.joinToString { vm.exerciseName(it) }))
                f.avgRpe?.takeIf { it >= 9.0 }?.let { add(stringResource(R.string.fatigue_rpe, "%.1f".format(it))) }
                if ((f.avgEnergy ?: 5.0) <= 2.0) add(stringResource(R.string.fatigue_energy))
                if ((f.avgPain ?: 1.0) >= 3.5) add(stringResource(R.string.fatigue_pain))
            }
            Text(stringResource(R.string.fatigue_signals, f.signals, parts.joinToString()))
            if (f.deloadRecommended) Text(stringResource(R.string.fatigue_deload), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        }
        val meso = ui.state.mesocycle
        if (meso == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 4, 5).forEach { w -> AssistChip(onClick = { vm.startMesocycle(w) }, label = { Text(stringResource(R.string.meso_start, w)) }) }
            }
        } else {
            Text(stringResource(R.string.meso_week, ui.mesocycleWeek ?: 1, meso.length), fontWeight = FontWeight.SemiBold)
            if (ui.deloadWeek) Text(stringResource(R.string.meso_deload_week), color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = vm::stopMesocycle) { Text(stringResource(R.string.meso_stop)) }
        }
    }
}

// --- Generador ------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneratorCard(vm: PlanningViewModel) {
    val equipment by vm.gymEquipment.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableIntStateOf(3) }
    var goal by rememberSaveable { mutableStateOf(Goal.HYPERTROPHY) }
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    var preview by remember { mutableStateOf<List<Routine>>(emptyList()) }
    PlanCard(stringResource(R.string.gen_title)) {
        Text(stringResource(R.string.gen_days), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (2..6).forEach { d -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text("$d") }) }
        }
        Text(stringResource(R.string.gen_goal), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Goal.STRENGTH to R.string.goal_strength, Goal.HYPERTROPHY to R.string.goal_hypertrophy, Goal.ENDURANCE to R.string.goal_endurance).forEach { (g, l) ->
                FilterChip(selected = goal == g, onClick = { goal = g }, label = { Text(stringResource(l)) })
            }
        }
        Text(stringResource(R.string.gen_minutes), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 45, 60, 90).forEach { m -> FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m") }) }
        }
        Button(onClick = { preview = vm.generate(days, equipment, goal, minutes) }) { Text(stringResource(R.string.gen_generate)) }
        preview.forEach { r ->
            Text(stringResource(R.string.gen_preview, r.name, r.exercises.size, vm.estimateMinutes(r)), style = MaterialTheme.typography.bodySmall)
        }
        if (preview.isNotEmpty()) {
            OutlinedButton(onClick = { vm.saveGenerated(preview); preview = emptyList() }) { Text(stringResource(R.string.gen_save)) }
        }
    }
}

// --- Programas propios ----------------------------------------------------------------------------

@Composable
private fun ProgramsCard(vm: PlanningViewModel, ui: PlanningUi, onEdit: (String?) -> Unit) {
    PlanCard(stringResource(R.string.prog_title)) {
        if (ui.state.customPrograms.isEmpty()) Text(stringResource(R.string.prog_empty), style = MaterialTheme.typography.bodySmall)
        ui.state.customPrograms.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.nameEs, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { onEdit(p.id) }) { Text(stringResource(R.string.prog_edit)) }
                IconButton(onClick = { vm.deleteProgram(p.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) }
            }
        }
        Button(onClick = { onEdit(null) }) { Text(stringResource(R.string.prog_create)) }
    }
}

// --- Pico -------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeakCard(vm: PlanningViewModel, ui: PlanningUi) {
    val unit = LocalWeightUnit.current
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.now().plusWeeks(10).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.setMeetDate(LocalDate.ofEpochDay(it / 86_400_000L)) }
                    picking = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
    PlanCard(stringResource(R.string.peak_title)) {
        val meet = ui.state.meetEpochDay
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picking = true }) {
                Text(meet?.let { formatShortDate(LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) } ?: stringResource(R.string.peak_pick))
            }
            if (meet != null) TextButton(onClick = { vm.setMeetDate(null) }) { Text(stringResource(R.string.peak_clear)) }
        }
        if (ui.peak.isEmpty()) Text(stringResource(R.string.peak_none), style = MaterialTheme.typography.bodySmall)
        val lang = com.bestrong.mobile.ui.components.uiLanguage()
        ui.peak.forEach { w ->
            Text(stringResource(R.string.peak_week, w.weeksOut, w.focus(lang), w.sets, w.reps, (w.percent * 100).roundToInt()), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            val loads = ui.oneRepMaxes.entries.joinToString("  ") { (id, rm) -> vm.exerciseName(id) + " " + unit.format(Peaking.weight(w, rm)) }
            if (loads.isNotBlank()) Text(loads, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// --- Calistenia --------------------------------------------------------------------------------------

@Composable
private fun CalisthenicsCard(vm: PlanningViewModel, ui: PlanningUi) {
    PlanCard(stringResource(R.string.calis_title)) {
        ui.calisthenics.forEach { c ->
            val chain = stringResource(
                when (c.chain) {
                    "push" -> R.string.chain_push
                    "pull" -> R.string.chain_pull
                    "squat" -> R.string.chain_squat
                    else -> R.string.chain_core
                },
            )
            val line = if (c.currentId == null) {
                stringResource(R.string.calis_start, vm.exerciseName(c.nextId))
            } else {
                stringResource(R.string.calis_line, vm.exerciseName(c.currentId), vm.exerciseName(c.nextId))
            }
            Text("$chain: $line", style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.calis_rule), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- Temporizadores y sesiones guiadas ------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimersCard(onTimer: (IntervalPlan) -> Unit) {
    var work by rememberSaveable { mutableIntStateOf(40) }
    var rest by rememberSaveable { mutableIntStateOf(20) }
    var rounds by rememberSaveable { mutableIntStateOf(8) }
    var minutes by rememberSaveable { mutableIntStateOf(10) }
    val defaultStations = stringResource(R.string.timer_circuit_default)
    var stations by rememberSaveable { mutableStateOf(defaultStations) }
    PlanCard(stringResource(R.string.timers_title)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { onTimer(com.bestrong.core.training.IntervalTimer.TABATA) }) { Text(stringResource(R.string.timer_tabata)) }
            Button(onClick = { onTimer(IntervalPlan.Hiit(work, rest, rounds)) }) { Text(stringResource(R.string.timer_hiit, work, rest, rounds)) }
            Button(onClick = { onTimer(IntervalPlan.Emom(minutes)) }) { Text(stringResource(R.string.timer_emom, minutes)) }
            Button(onClick = { onTimer(IntervalPlan.Amrap(minutes)) }) { Text(stringResource(R.string.timer_amrap, minutes)) }
        }
        Stepper(stringResource(R.string.timer_work_s), work, 10, 180, 5) { work = it }
        Stepper(stringResource(R.string.timer_rest_s), rest, 0, 180, 5) { rest = it }
        Stepper(stringResource(R.string.timer_rounds), rounds, 1, 30, 1) { rounds = it }
        Stepper(stringResource(R.string.timer_minutes), minutes, 1, 60, 1) { minutes = it }
        // Circuito / clase: estaciones con el trabajo y descanso de arriba, tantas vueltas como rondas
        OutlinedTextField(stations, { stations = it }, label = { Text(stringResource(R.string.timer_circuit_stations)) }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val list = stations.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (list.isNotEmpty()) onTimer(IntervalPlan.Circuit(list, work, rest, rounds.coerceAtMost(10)))
        }) { Text(stringResource(R.string.timer_circuit)) }
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, modifier = Modifier.semantics { contentDescription = "$label −$step" }) { Text("−") }
        Text("$value", fontWeight = FontWeight.Bold)
        TextButton(onClick = { onChange((value + step).coerceAtMost(max)) }, modifier = Modifier.semantics { contentDescription = "$label +$step" }) { Text("+") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuidedCard(onGuided: (Boolean, BodyFocus) -> Unit) {
    PlanCard(stringResource(R.string.guided_title)) {
        val focuses = listOf(BodyFocus.UPPER to R.string.focus_upper, BodyFocus.LOWER to R.string.focus_lower, BodyFocus.FULL to R.string.focus_full)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            focuses.forEach { (f, l) -> OutlinedButton(onClick = { onGuided(true, f) }) { Text(stringResource(R.string.guided_warmup, stringResource(l))) } }
            focuses.forEach { (f, l) -> OutlinedButton(onClick = { onGuided(false, f) }) { Text(stringResource(R.string.guided_stretch, stringResource(l))) } }
        }
    }
}

// --- Lesiones y límite semanal ------------------------------------------------------------------------

@Composable
fun InjuryZone.label(): String = stringResource(
    when (this) {
        InjuryZone.SHOULDER -> R.string.zone_shoulder
        InjuryZone.ELBOW -> R.string.zone_elbow
        InjuryZone.WRIST -> R.string.zone_wrist
        InjuryZone.LOWER_BACK -> R.string.zone_lower_back
        InjuryZone.KNEE -> R.string.zone_knee
        InjuryZone.HIP -> R.string.zone_hip
        InjuryZone.NECK -> R.string.zone_neck
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InjuriesCard(vm: PlanningViewModel, ui: PlanningUi) {
    PlanCard(stringResource(R.string.injury_title)) {
        Text(stringResource(R.string.injury_hint), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InjuryZone.entries.forEach { z ->
                FilterChip(selected = z in ui.state.injuries, onClick = { vm.toggleInjury(z) }, label = { Text(z.label()) })
            }
        }
        Text(stringResource(R.string.cap_title), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = ui.state.weeklyCap == null, onClick = { vm.setWeeklyCap(null) }, label = { Text(stringResource(R.string.cap_off)) })
            listOf(0.025, 0.05, 0.10).forEach { c ->
                FilterChip(selected = ui.state.weeklyCap == c, onClick = { vm.setWeeklyCap(c) }, label = { Text(stringResource(R.string.cap_value, if (c < 0.05) java.text.NumberFormat.getNumberInstance().format(2.5) else "${(c * 100).roundToInt()}")) })
            }
        }
    }
}

private const val DEFAULT_SESSION_MIN = 60

/** Añade la sesión al calendario del móvil (Google Calendar, Samsung…) a la hora habitual de entrenar. */
@Composable
private fun AddToCalendarItem(routine: com.bestrong.core.model.Routine, date: java.time.LocalDate, time: java.time.LocalTime, minutes: Int, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val noApp = stringResource(R.string.no_calendar_app)
    DropdownMenuItem(
        text = { Text(stringResource(R.string.cal_add_to_calendar)) },
        onClick = {
            onDone()
            val begin = date.atTime(time).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val intent = android.content.Intent(android.content.Intent.ACTION_INSERT)
                .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                .putExtra(android.provider.CalendarContract.Events.TITLE, "🏋 " + routine.displayName)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, begin + minutes * 60_000L)
            // Sin app de calendario no hay nada que abrir: se avisa
            runCatching { context.startActivity(intent) }.onFailure { android.widget.Toast.makeText(context, noApp, android.widget.Toast.LENGTH_SHORT).show() }
        },
    )
}

/** «Repetir semana»: en la siguiente o en las 2, 4 u 8 siguientes (hasta el final del mesociclo). */
@Composable
private fun DuplicateWeekButton(onDuplicate: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.cal_duplicate)) }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(1, 2, 4, 8).forEach { n ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.plan_duplicate_weeks, n, n)) },
                    onClick = {
                        open = false
                        onDuplicate(n)
                    },
                )
            }
        }
    }
}
