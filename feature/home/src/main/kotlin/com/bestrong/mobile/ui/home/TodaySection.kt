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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
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
import com.bestrong.mobile.ui.components.spokenAs
import java.time.DayOfWeek
import java.time.format.TextStyle

/** «Hoy toca»: la sesión del programa activo con sus cargas. */
@Composable
fun TodayCard(session: ProgramSession, onStart: () -> Unit, onStopProgram: () -> Unit, onStartWatch: (() -> Unit)? = null) {
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
                    // Cuánto dura más o menos, para decidir de un vistazo
                    val minutes = remember(session.routine) { (com.bestrong.core.session.WorkoutEditor.estimatedRemainingSeconds(session.routine.toWorkout(0)) + 59) / 60 }
                    Text(
                        listOfNotNull(label, stringResource(R.string.routine_estimated, minutes).takeIf { minutes > 0 }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                // Con «suma de las dos mancuernas», el total del par (lo mismo que saldrá al empezar)
                val f = com.bestrong.mobile.ui.components.pairFactor(re.exercise)
                val weights = re.sets.map { it.weightKg }.distinct()
                val reps = re.sets.map { it.reps }.distinct()
                val load = when {
                    weights.all { it == 0.0 } -> "${re.sets.size} × ${reps.joinToString("/")}"
                    weights.size == 1 && reps.size == 1 -> "${re.sets.size} × ${reps[0]} · ${unit.format(weights[0] * f, withUnit = true)}"
                    else -> re.sets.joinToString("  ") { "${unit.format(it.weightKg * f)}×${it.reps}" } + " ${unit.label}"
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
            StartRow(onStart, onStartWatch, Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.PlayArrow, null)
                Text(stringResource(R.string.today_start), Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Sin programa: sugerencia de la rutina que toca (la que lleva más tiempo sin hacerse). */
@Composable
fun SuggestedRoutineCard(routine: Routine, onStart: () -> Unit, onStartWatch: (() -> Unit)? = null) {
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
        StartRow(onStart, onStartWatch) {
            Icon(Icons.Default.PlayArrow, null)
            Text(stringResource(R.string.routine_start), Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
        }
    }
}

/** «Empezar» a lo ancho y, al lado, empezar en el reloj (propuesta 2). */
@Composable
private fun StartRow(onStart: () -> Unit, onStartWatch: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        HeroButton(onClick = onStart, modifier = Modifier.weight(1f), content = content)
        onStartWatch?.let { watch ->
            androidx.compose.material3.FilledIconButton(
                onClick = watch,
                modifier = Modifier.size(52.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.35f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Icon(Icons.Default.Watch, stringResource(R.string.rd_home_start_watch)) }
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

/**
 * La semana en una sola tarjeta: anillo con las sesiones frente al objetivo (tocarla lo cambia), los días entrenados
 * (L–D), la racha y el volumen frente a la semana pasada y, si hay cardio o un objetivo de cardio, su línea.
 */
@Composable
fun WeekStrip(week: WeekProgress, canChangeGoal: Boolean, onGoalChange: (Int) -> Unit, cardio: @Composable () -> Unit = {}) {
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
            if (canChangeGoal) Icon(Icons.Default.Edit, stringResource(R.string.week_goal_title), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { d -> DayCircle(d, week, locale) }
        }
        WeekStats(week)
        cardio()
    }
    if (editing) WeekGoalDialog(week.goal, onPick = { onGoalChange(it); editing = false }) { editing = false }
}

/** Racha de semanas y volumen de la semana (con la diferencia frente a la pasada), lado a lado. */
@Composable
private fun WeekStats(week: WeekProgress) {
    val unit = LocalWeightUnit.current
    val streakText = androidx.compose.ui.res.pluralStringResource(R.plurals.rd_home_streak_weeks, week.streak, week.streak)
    val volumeLabel = stringResource(R.string.rd_home_week_volume)
    val volume = unit.formatVolume(week.volumeKg)
    val delta = if (week.lastWeekVolumeKg > 0) (week.volumeKg - week.lastWeekVolumeKg) / week.lastWeekVolumeKg else null
    val colors = com.bestrong.mobile.ui.theme.statusColors
    val deltaText = delta?.let { d ->
        (if (d >= 0) colors.goodMark.ifEmpty { "▲ " } else colors.badMark.ifEmpty { "▼ " }) +
            stringResource(R.string.rd_home_vs_last_week, kotlin.math.abs(d * 100).toInt())
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = streakText }) {
            Text(stringResource(R.string.rd_home_streak), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("🔥 ${week.streak}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = listOfNotNull(volumeLabel, volume, deltaText).joinToString(", ") }) {
            Text(volumeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(volume, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1)
            deltaText?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = if ((delta ?: 0.0) >= 0) colors.good else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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

/** Objetivos de km de cardio por semana que se pueden elegir. */
private val CARDIO_GOALS_KM = listOf(5.0, 10.0, 20.0, 30.0, 50.0)

/** Objetivos en minutos por semana: la mitad, lo recomendado por la OMS (150) y el doble. */
private val CARDIO_GOALS_MIN = listOf(75, 150, 300)

/** «12,3 de 20 km» o «95 de 150 min» con su barra (o lo hecho si no hay objetivo) y lo que falta al día. */
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
    Text("🏃 $text", Modifier.spokenAs(text), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    progress?.let { p -> LinearProgressIndicator(progress = { p.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
    val extra = listOfNotNull(
        stringResource(R.string.cardio_streak, week.streak).takeIf { week.streak >= 2 },
        cardioPace(week),
    )
    if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
 * Línea de cardio de la tarjeta de la semana: «12,3 / 20 km» (solo si hay cardio esta semana o un objetivo puesto);
 * tocarla deja poner o quitar el objetivo y el aviso.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CardioWeekLine(vm: HomeViewModel) {
    val week by vm.cardioWeek.collectAsStateWithLifecycle()
    if (!week.worthShowing) return
    var editing by remember { mutableStateOf(false) }
    // Con objetivo llega el aviso de cardio: sin permiso de notificaciones no llegaría nunca
    val askNotifications = com.bestrong.mobile.ui.components.rememberNotificationPermission()
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = stringResource(R.string.cardio_goal_title)) { editing = true }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CardioWeekProgress(week)
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
                            androidx.compose.material3.FilterChip(selected = week.goalKm == g, onClick = { vm.setWeeklyCardioGoal(g, null); editing = false; askNotifications() }, label = { Text("${g.toInt()} km") })
                        }
                        // En minutos, para el cardio sin distancia (elíptica, bici estática, HIIT)
                        CARDIO_GOALS_MIN.forEach { g ->
                            androidx.compose.material3.FilterChip(selected = week.goalMin == g, onClick = { vm.setWeeklyCardioGoal(null, g); editing = false; askNotifications() }, label = { Text("$g min") })
                        }
                    }
                    CustomCardioGoal(vm, week) {
                        editing = false
                        askNotifications()
                    }
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
    val askNotifications = com.bestrong.mobile.ui.components.rememberNotificationPermission()
    // Toda la fila es el interruptor: TalkBack lo lee con su nombre
    Row(
        Modifier.toggleable(value = on, role = androidx.compose.ui.semantics.Role.Switch) {
            vm.setCardioNudge(it)
            if (it) askNotifications()
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.cardio_nudge_setting), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.Switch(checked = on, onCheckedChange = null)
    }
    // Qué día (por la tarde) llega el aviso
    if (on) {
        val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
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

/** Un día de la semana: relleno si se entrenó, con aro si es hoy. */
@Composable
private fun DayCircle(d: DayOfWeek, week: WeekProgress, locale: java.util.Locale) {
    val done = d in week.trainedDays
    val name = d.getDisplayName(TextStyle.FULL, locale)
    val state = stringResource(if (done) R.string.day_trained else R.string.day_not_trained)
    val todayLabel = stringResource(R.string.day_today)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .background(
                if (done) com.bestrong.mobile.ui.components.brandGradient() else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest),
                CircleShape,
            )
            .then(if (d == week.today) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
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
