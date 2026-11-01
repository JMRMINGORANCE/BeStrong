package com.bestrong.mobile.ui.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.SessionRating
import com.bestrong.core.stats.formatValue
import com.bestrong.core.util.formatDuration
import com.bestrong.designsystem.BeStrongPalette
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.exerciseLabel
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.theme.LocalScreenColor
import com.bestrong.mobile.ui.theme.statusColors
import kotlin.random.Random

/**
 * Resumen de fin de entreno a pantalla completa (propuesta 4): trofeo, cifras clave, récords destacados,
 * comparación con la última vez, valoración y acciones. Con récords hay confeti y vibración.
 */
@Composable
fun WorkoutSummaryDialog(
    summary: FinishedSummary,
    onSaveRoutine: () -> Unit,
    onDone: () -> Unit,
    onShare: () -> Unit = {},
    onRate: (SessionRating) -> Unit = {},
    onUpdateRoutine: () -> Unit = {},
    onShareText: () -> Unit = {},
    dumbbellTotal: Boolean = false,
) {
    val w = summary.workout
    val unit = LocalWeightUnit.current
    val hasRecords = summary.records.isNotEmpty()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { if (hasRecords) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    // Un toque fuera sin querer no se lleva la valoración (Atrás y «Hecho» sí cierran)
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        val amber = MaterialTheme.colorScheme.secondary
        Box(
            Modifier.fillMaxSize().background(LocalScreenColor.current)
                .background(Brush.radialGradient(listOf(amber.copy(alpha = 0.22f), Color.Transparent), center = Offset(540f, 260f), radius = 900f)),
        ) {
            if (hasRecords) Confetti(Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Trophy()
                Text(
                    stringResource(if (hasRecords) R.string.rd_summary_title_records else R.string.workout_completed),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    listOfNotNull(
                        w.name,
                        formatDuration(w.durationSeconds()),
                        pluralStringResource(R.plurals.rd_summary_records, summary.records.size, summary.records.size).takeIf { hasRecords },
                    ).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                KpiRow(summary)
                summary.weeklyGoalReached?.let { goal ->
                    Text(pluralStringResource(R.plurals.weekly_goal_reached, goal, goal), fontWeight = FontWeight.Bold, color = statusColors.good)
                }
                summary.achievements.forEach { id ->
                    com.bestrong.mobile.ui.components.achievementLabelRes(id)?.let { res ->
                        Text("🏅 " + stringResource(R.string.achievement_unlocked, stringResource(res)), fontWeight = FontWeight.Bold, color = statusColors.good)
                    }
                }
                if (hasRecords) RecordsCard(summary)
                VolumeComparison(summary)
                // El detalle de siempre, en una tarjeta: músculos, comparaciones, desequilibrios y la próxima vez
                DetailsCard(summary, dumbbellTotal)
                if (w.notes.isNotBlank()) Text(w.notes, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                Column(Modifier.fillMaxWidth()) { SessionRatingPicker(onRate) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        w.routineId == null -> FilledTonalButton(onClick = onSaveRoutine, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.save_as_routine), textAlign = TextAlign.Center)
                        }
                        summary.routineToUpdate != null -> FilledTonalButton(onClick = onUpdateRoutine, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.update_routine, summary.routineToUpdate.name), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Button(onClick = onShare, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Icon(Icons.Default.Share, null)
                        Text(stringResource(R.string.share_workout), Modifier.padding(start = 8.dp), maxLines = 2)
                    }
                }
                TextButton(onClick = onShareText) { Text(stringResource(R.string.share_as_text)) }
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Trophy() {
    val primary = MaterialTheme.colorScheme.primary
    val amber = MaterialTheme.colorScheme.secondary
    val pulse = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pulse.animateTo(1f, tween(600)) }
    Box(
        Modifier.padding(top = 12.dp).size(104.dp * pulse.value).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFFE2B0), amber, primary))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.EmojiEvents, null, Modifier.size(56.dp * pulse.value), tint = Color(0xFF3A1A00))
    }
}

/** Volumen (o series), series y pulso medio con kcal: tres cifras de un vistazo. */
@Composable
private fun KpiRow(summary: FinishedSummary) {
    val w = summary.workout
    val unit = LocalWeightUnit.current
    val volumeDelta = summary.comparisons.sumOf { it.volumeDelta }
    val previousVolume = comparedVolume(summary) - volumeDelta
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (w.totalVolume > 0) {
            Kpi(
                unit.formatVolume(w.totalVolume),
                stringResource(R.string.stat_volume),
                previousVolume.takeIf { it > 0 }?.let { pct(volumeDelta / previousVolume) },
                volumeDelta >= 0,
                Modifier.weight(1f),
            )
        }
        Kpi("${w.completedSetCount}", stringResource(R.string.stat_sets), null, true, Modifier.weight(1f))
        w.avgHeartRate?.let { hr ->
            val kcal = w.calories?.let { c -> (if (w.caloriesEstimated) "≈" else "") + stringResource(R.string.kcal_value, c) }
            Kpi("❤ $hr", stringResource(R.string.rd_summary_avg_hr), kcal, null, Modifier.weight(1f))
        } ?: Kpi(formatDuration(w.durationSeconds()), stringResource(R.string.stat_duration), null, true, Modifier.weight(1f))
    }
}

private fun pct(fraction: Double): String = (if (fraction >= 0) "▲ " else "▼ ") + "%d %%".format(kotlin.math.abs(fraction * 100).toInt())

/** Volumen de hoy de los ejercicios que se pueden comparar con la vez anterior. */
private fun comparedVolume(summary: FinishedSummary): Double {
    val ids = summary.comparisons.map { it.exerciseId }.toSet()
    return summary.workout.exercises.filter { it.exercise.id in ids }.sumOf { we -> we.completedSets.sumOf { it.weightKg * it.reps } }
}

@Composable
private fun Kpi(value: String, label: String, sub: String?, up: Boolean?, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
            .clearAndSetSemantics { contentDescription = listOfNotNull(label, value, sub).joinToString(", ") }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        sub?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = when (up) {
                    true -> statusColors.good
                    false -> statusColors.bad
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** Récords del día en una tarjeta dorada. */
@Composable
private fun RecordsCard(summary: FinishedSummary) {
    val unit = LocalWeightUnit.current
    val amber = MaterialTheme.colorScheme.secondary
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(amber.copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceContainer)))
            .border(1.dp, amber.copy(alpha = 0.35f), MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.new_records), fontWeight = FontWeight.Black, color = amber, modifier = Modifier.weight(1f))
            Text(
                "${summary.records.size} PR",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.clip(CircleShape).background(amber).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        summary.records.forEach { pr ->
            // Con «suma de las dos mancuernas», el peso y el 1RM del par (como en las series y el historial)
            val f = if (pr.type == com.bestrong.core.stats.RecordType.HEAVIEST_WEIGHT || pr.type == com.bestrong.core.stats.RecordType.BEST_ONE_REP_MAX) com.bestrong.mobile.ui.components.pairFactor(pr.exerciseId) else 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pr.exerciseLabel(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pr.type.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(pr.type.formatValue(pr.value * f, unit), fontWeight = FontWeight.Black)
            }
        }
    }
}

/** Barras «Hoy» frente a «La última vez» con el volumen de los ejercicios repetidos. */
@Composable
private fun VolumeComparison(summary: FinishedSummary) {
    val today = comparedVolume(summary)
    val before = today - summary.comparisons.sumOf { it.volumeDelta }
    if (today <= 0 || before <= 0) return
    val unit = LocalWeightUnit.current
    val max = maxOf(today, before)
    com.bestrong.mobile.ui.components.Panel(contentPadding = 16.dp) {
        Text(stringResource(R.string.summary_vs_last), fontWeight = FontWeight.Bold)
        Bar(stringResource(R.string.day_today), unit.formatVolume(today), (today / max).toFloat(), com.bestrong.mobile.ui.components.brandGradient())
        Bar(stringResource(R.string.rd_summary_last_time), unit.formatVolume(before), (before / max).toFloat(), androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest))
    }
}

@Composable
private fun Bar(label: String, value: String, fraction: Float, brush: Brush) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { contentDescription = "$label: $value" }) {
        Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f).height(10.dp)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(brush))
        }
        Text(value, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** Todo el detalle del resumen clásico. */
@Composable
private fun DetailsCard(summary: FinishedSummary, dumbbellTotal: Boolean) {
    val w = summary.workout
    val unit = LocalWeightUnit.current
    com.bestrong.mobile.ui.components.Panel(contentPadding = 16.dp) {
        Text(
            pluralStringResource(R.plurals.history_sets_done, w.completedSetCount, w.completedSetCount) + " · " +
                pluralStringResource(R.plurals.exercise_count, w.exercises.size, w.exercises.size),
            fontWeight = FontWeight.Bold,
        )
        w.avgHeartRate?.let { Text(stringResource(R.string.summary_hr, it, w.maxHeartRate?.toString() ?: "--"), style = MaterialTheme.typography.bodySmall) }
        remember(w) { com.bestrong.core.stats.RestStats.summary(w) }?.let { r ->
            Text(
                stringResource(R.string.rest_real_avg, formatDuration(r.realSeconds.toLong()), formatDuration(r.plannedSeconds.toLong())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        com.bestrong.mobile.ui.components.WorkoutMuscleChips(w)
        SummaryComparisons(summary)
        RpeVsTargetLines(w)
        summary.imbalances.forEach { (id, ratio) ->
            Text(
                stringResource(R.string.summary_imbalance, ExerciseLibrary.byId(id)?.displayName() ?: id, (ratio * 100).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        // Doble progresión: qué peso usar la próxima vez
        if (summary.nextTime.isNotEmpty()) {
            Text(stringResource(R.string.summary_next_time), fontWeight = FontWeight.Bold)
            summary.nextTime.take(6).forEach { n ->
                val arrow = when {
                    n.direction > 0 -> "↑"
                    n.direction < 0 -> "↓"
                    else -> "="
                }
                val ex = w.exercises.firstOrNull { it.exercise.id == n.exerciseId }?.exercise
                val f = if (dumbbellTotal && ex?.equipment == Equipment.DUMBBELL) 2 else 1
                Text(
                    stringResource(R.string.summary_next_line, arrow, ExerciseLibrary.byId(n.exerciseId)?.displayName() ?: n.exerciseName, unit.format(n.weightKg * f, withUnit = true), n.reps),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (n.direction > 0) statusColors.good else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summary.nextTime.take(6).any { it.needsRpe }) {
                Text(stringResource(R.string.summary_next_needs_rpe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Confeti que cae una vez (solo con récords); decorativo, sin semántica. */
@Composable
private fun Confetti(modifier: Modifier) {
    val colors = listOf(BeStrongPalette.Orange, BeStrongPalette.Amber, BeStrongPalette.Success, MaterialTheme.colorScheme.primary)
    val pieces = remember {
        val r = Random(7)
        List(CONFETTI_PIECES) { Triple(r.nextFloat(), r.nextFloat() * 0.6f, r.nextFloat()) }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(3200, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(modifier.clearAndSetSemantics { }) {
        pieces.forEachIndexed { i, (x, delay, spin) ->
            val t = ((progress.value - delay * 0.4f) / 0.75f).coerceIn(0f, 1f)
            if (t <= 0f) return@forEachIndexed
            val px = x * size.width + kotlin.math.sin((t * 6 + i) * 1.3f) * 18.dp.toPx()
            val py = -20f + t * size.height * 0.9f
            rotate(spin * 360f + t * 540f, Offset(px, py)) {
                drawRect(colors[i % colors.size].copy(alpha = 1f - t * 0.6f), Offset(px - 4.dp.toPx(), py - 7.dp.toPx()), Size(8.dp.toPx(), 14.dp.toPx()))
            }
        }
    }
}

private const val CONFETTI_PIECES = 46
