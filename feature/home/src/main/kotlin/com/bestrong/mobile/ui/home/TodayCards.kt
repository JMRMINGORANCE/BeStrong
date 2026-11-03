package com.bestrong.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestrong.core.model.Routine
import com.bestrong.core.model.RoutineExercise
import com.bestrong.core.model.Workout
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.ProgramSession
import com.bestrong.core.util.WeightUnit
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.RestState
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.rememberNow
import com.bestrong.mobile.ui.components.spokenAs
import com.bestrong.mobile.ui.theme.FigureFont
import com.bestrong.mobile.ui.theme.KickerStyle
import com.bestrong.mobile.ui.theme.enfoque

/** Tarjeta sobria de «Enfoque»: superficie, borde fino y esquinas suaves; sin degradados. */
@Composable
internal fun EnfoqueCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content,
    )
}

/** Etiqueta pequeña en mayúsculas encima de un título. */
@Composable
internal fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = KickerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Minutos que se tarda, más o menos, en una rutina (series × 40 s + descansos). */
internal fun Routine.estimatedMinutes(): Int = (WorkoutEditor.estimatedRemainingSeconds(toWorkout(0)) + 59) / 60

/** «Hoy toca»: la sesión del programa activo con sus cargas. */
@Composable
fun TodayCard(session: ProgramSession, onStart: () -> Unit, onStopProgram: () -> Unit, onStartWatch: (() -> Unit)? = null, lastDays: Int? = null) {
    val label = if (session.program.weeksPerCycle > 1) {
        stringResource(R.string.today_cycle_week, session.cycle, session.week, session.program.weeksPerCycle)
    } else {
        stringResource(R.string.today_session_n, session.completedSessions + 1)
    }
    TodayRoutineCard(
        kicker = stringResource(R.string.today_title) + " · " + label,
        routine = session.routine,
        lastDays = lastDays,
        onStart = onStart,
        onStartWatch = onStartWatch,
        menu = { close -> DropdownMenuItem(text = { Text(stringResource(R.string.program_stop)) }, onClick = { close(); onStopProgram() }) },
    )
}

/** Sin programa: sugerencia de la rutina que toca (la que lleva más tiempo sin hacerse). */
@Composable
fun SuggestedRoutineCard(routine: Routine, onStart: () -> Unit, onStartWatch: (() -> Unit)? = null, lastDays: Int? = null) {
    TodayRoutineCard(stringResource(R.string.today_suggested), routine, lastDays, onStart, onStartWatch)
}

/** La tarjeta de lo que toca: título, ejercicios con su carga y, abajo, Empezar y el reloj. */
@Composable
private fun TodayRoutineCard(
    kicker: String,
    routine: Routine,
    lastDays: Int?,
    onStart: () -> Unit,
    onStartWatch: (() -> Unit)?,
    menu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    EnfoqueCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(kicker, Modifier.weight(1f))
            menu?.let { items -> CardMenu(items) }
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
            Text(routine.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            val minutes = remember(routine) { routine.estimatedMinutes() }
            if (minutes > 0) Text("≈ $minutes min", fontFamily = FigureFont, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val unit = LocalWeightUnit.current
        routine.exercises.take(TODAY_SHOWN).forEach { re -> ExerciseLoadRow(re, unit) }
        MoreAndLast(routine.exercises.size - TODAY_SHOWN, lastDays)
        StartRow(onStart, onStartWatch, enabled = routine.exercises.isNotEmpty(), modifier = Modifier.padding(top = 12.dp)) {
            Icon(Icons.Default.PlayArrow, null)
            Text(stringResource(R.string.enf_home_start), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CardMenu(items: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { items { open = false } }
    }
}

/** Un ejercicio de lo que toca: nombre y series × reps a la izquierda, la carga a la derecha. */
@Composable
private fun ExerciseLoadRow(re: RoutineExercise, unit: WeightUnit) {
    // Con «suma de las dos mancuernas», el total del par (lo mismo que saldrá al empezar)
    val f = com.bestrong.mobile.ui.components.pairFactor(re.exercise)
    val weights = re.sets.map { it.weightKg }.distinct()
    val reps = re.sets.map { it.reps }.distinct()
    val scheme = "${re.sets.size} × ${reps.joinToString("/")}"
    val load = when {
        weights.all { it == 0.0 } -> ""
        weights.size == 1 -> unit.format(weights[0] * f, withUnit = true)
        else -> unit.format(weights.min() * f) + "–" + unit.format(weights.max() * f, withUnit = true)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(re.exercise.displayName(), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(" $scheme", Modifier.weight(1f), fontFamily = FigureFont, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        if (load.isNotEmpty()) Text(load, Modifier.padding(start = 8.dp), fontFamily = FigureFont, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, maxLines = 1)
    }
}

/** «+2 ejercicios más · última vez hace 4 d». */
@Composable
private fun MoreAndLast(more: Int, lastDays: Int?) {
    val parts = listOfNotNull(
        more.takeIf { it > 0 }?.let { n -> pluralStringResource(R.plurals.share_more_exercises, n, n) },
        lastDays?.let { d -> stringResource(R.string.enf_home_last_time, daysAgo(d)) },
    )
    if (parts.isEmpty()) return
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(parts.joinToString(" · "), Modifier.padding(top = 8.dp), fontFamily = FigureFont, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** «hoy» o «hace N d». */
@Composable
internal fun daysAgo(days: Int): String = if (days <= 0) stringResource(R.string.day_today) else stringResource(R.string.enf_home_days_ago, days)

/** «Empezar» grande en el color de acción y, al lado, el botón cuadrado del reloj. */
@Composable
internal fun StartRow(
    onStart: () -> Unit,
    onStartWatch: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onStart,
            enabled = enabled,
            // Mínimo, no fijo: con letra grande el texto pasa a dos líneas sin cortarse
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = enfoque.action, contentColor = enfoque.onAction),
            content = content,
        )
        onStartWatch?.let { watch ->
            androidx.compose.material3.FilledIconButton(
                onClick = watch,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Icon(Icons.Default.Watch, stringResource(R.string.rd_home_start_watch)) }
        }
    }
}

/** Hoy ya se entrenó: «✓ Torso A hecho» y lo siguiente que toca (se puede empezar igual). */
@Composable
internal fun DoneTodayCard(done: Workout, next: Routine?, onStartNext: () -> Unit) {
    EnfoqueCard {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(enfoque.done.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Check, null, tint = enfoque.done, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.enf_home_done_today, done.name), Modifier.padding(start = 6.dp), color = enfoque.done, fontFamily = FigureFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
        next?.let { r ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.enf_home_next, r.displayName), Modifier.weight(1f), fontFamily = FigureFont, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                androidx.compose.material3.TextButton(onClick = onStartNext) { Text(stringResource(R.string.enf_home_start), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** El entreno abierto, lo primero de todo: «Continuar» vuelve a él. */
@Composable
internal fun ActiveWorkoutCard(w: Workout, rest: RestState?, onOpenWorkout: () -> Unit) {
    val now = rememberNow()
    EnfoqueCard(onClick = onOpenWorkout) {
        Kicker(stringResource(R.string.enf_home_in_progress))
        Text(w.name, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.home_in_progress_detail, formatDuration(w.durationSeconds(now)), w.completedSetCount, w.totalSetCount),
            fontFamily = FigureFont,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Descanso en marcha: se ve sin abrir el entreno
        rest?.let { r ->
            val left = ((r.endsAtEpochMs - now + 999) / 1000).coerceAtLeast(0)
            if (left > 0) stringResource(R.string.rest_title, formatDuration(left)).let { t -> Text(t, Modifier.spokenAs(t), fontFamily = FigureFont, fontWeight = FontWeight.Bold) }
        }
        androidx.compose.material3.LinearProgressIndicator(
            progress = { if (w.totalSetCount == 0) 0f else w.completedSetCount.toFloat() / w.totalSetCount },
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(4.dp).clip(CircleShape),
            color = enfoque.done,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
        StartRow(onOpenWorkout, onStartWatch = null) {
            Icon(Icons.Default.PlayArrow, null)
            Text(stringResource(R.string.action_continue), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** Ejercicios de la sesión de hoy que se listan con su carga en la tarjeta. */
private const val TODAY_SHOWN = 4
