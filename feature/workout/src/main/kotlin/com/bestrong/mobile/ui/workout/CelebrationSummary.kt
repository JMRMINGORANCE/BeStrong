package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.exerciseLabel
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.theme.FigureFont
import com.bestrong.mobile.ui.theme.KickerStyle
import com.bestrong.mobile.ui.theme.LocalScreenColor
import com.bestrong.mobile.ui.theme.enfoque
import com.bestrong.mobile.ui.theme.statusColors

/**
 * Resumen de fin de entreno a pantalla completa («Enfoque»): «ENTRENO HECHO», las tres cifras (tiempo, volumen,
 * series), los récords en ámbar, la carga de la próxima vez, las etiquetas y Compartir / Listo. Sin adornos: con
 * récords solo vibra.
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
    /** Etiquetas del diario («dormí-mal»…); vacío si no se usan. */
    onTags: ((List<String>) -> Unit)? = null,
    tagSuggestions: List<String> = emptyList(),
) {
    val w = summary.workout
    val hasRecords = summary.records.isNotEmpty()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { if (hasRecords) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    // Un toque fuera sin querer no se lleva la valoración (Atrás y «Listo» sí cierran)
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(LocalScreenColor.current).statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryHeader(summary)
                KpiRow(summary)
                GoalsAndAchievements(summary)
                if (hasRecords) RecordsCard(summary)
                NextTimeCard(summary, dumbbellTotal)
                // Etiquetas para el diario (Historial → Diario)
                onTags?.let { set ->
                    SummaryCard {
                        Text(stringResource(R.string.diary_tags).uppercase(), style = KickerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        com.bestrong.mobile.ui.components.TagEditor(w.tags, tagSuggestions + com.bestrong.mobile.ui.components.defaultTagSuggestions(), set)
                    }
                }
                VolumeComparison(summary)
                // El detalle de siempre: músculos, comparaciones, desequilibrios y RPE
                DetailsCard(summary)
                if (w.notes.isNotBlank()) Text(w.notes, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                Column(Modifier.fillMaxWidth()) { SessionRatingPicker(onRate) }
                RoutineButton(summary, onSaveRoutine, onUpdateRoutine)
                TextButton(onClick = onShareText, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.share_as_text)) }
            }
            SummaryActions(onShare, onDone)
        }
    }
}

/** «ENTRENO HECHO», el nombre en grande y «martes 3 nov · ♥ 131 de media». */
@Composable
private fun SummaryHeader(summary: FinishedSummary) {
    val w = summary.workout
    val date = remember(w.startedAtEpochMs) {
        java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM", java.util.Locale.getDefault())
            .format(java.time.Instant.ofEpochMilli(w.startedAtEpochMs).atZone(java.time.ZoneId.systemDefault()))
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.enf_wo_finished).uppercase(), style = KickerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            w.name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).semantics { heading() },
        )
        Text(
            listOfNotNull(date, w.avgHeartRate?.let { stringResource(R.string.enf_wo_avg_hr, it) }).joinToString(" · "),
            fontFamily = FigureFont,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun GoalsAndAchievements(summary: FinishedSummary) {
    summary.weeklyGoalReached?.let { goal ->
        Text(pluralStringResource(R.plurals.weekly_goal_reached, goal, goal), fontWeight = FontWeight.Bold, color = enfoque.done)
    }
    summary.achievements.forEach { id ->
        com.bestrong.mobile.ui.components.achievementLabelRes(id)?.let { res ->
            Text("🏅 " + stringResource(R.string.achievement_unlocked, stringResource(res)), fontWeight = FontWeight.Bold, color = enfoque.record)
        }
    }
}

/** Tarjeta lisa del resumen: superficie con borde fino. */
@Composable
private fun SummaryCard(modifier: Modifier = Modifier, borderColor: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, borderColor ?: MaterialTheme.colorScheme.outlineVariant, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Guardar el entreno libre como rutina, o actualizar la rutina con lo que se hizo. */
@Composable
private fun RoutineButton(summary: FinishedSummary, onSaveRoutine: () -> Unit, onUpdateRoutine: () -> Unit) {
    when {
        summary.workout.routineId == null -> FilledTonalButton(onClick = onSaveRoutine, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.save_as_routine), textAlign = TextAlign.Center)
        }
        summary.routineToUpdate != null -> FilledTonalButton(onClick = onUpdateRoutine, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.update_routine, summary.routineToUpdate.name), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Abajo, siempre a la vista: Compartir y Listo. */
@Composable
private fun SummaryActions(onShare: () -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(
            onClick = onShare,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Icon(Icons.Default.Share, null)
            Text(stringResource(R.string.share_workout), Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onDone,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = enfoque.action, contentColor = enfoque.onAction),
        ) { Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold) }
    }
}

/** Doble progresión: qué carga usar la próxima vez (▲ sube, = mantiene, ▼ baja). */
@Composable
private fun NextTimeCard(summary: FinishedSummary, dumbbellTotal: Boolean) {
    if (summary.nextTime.isEmpty()) return
    val unit = LocalWeightUnit.current
    val w = summary.workout
    SummaryCard {
        Text(stringResource(R.string.summary_next_time).uppercase(), style = KickerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        summary.nextTime.take(6).forEach { n ->
            val arrow = when {
                n.direction > 0 -> "▲"
                n.direction < 0 -> "▼"
                else -> "="
            }
            val ex = w.exercises.firstOrNull { it.exercise.id == n.exerciseId }?.exercise
            val f = if (dumbbellTotal && ex?.equipment == Equipment.DUMBBELL) 2 else 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ExerciseLibrary.byId(n.exerciseId)?.displayName() ?: n.exerciseName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$arrow ${unit.format(n.weightKg * f, withUnit = true)} × ${n.reps}",
                    fontFamily = FigureFont,
                    fontWeight = FontWeight.SemiBold,
                    color = if (n.direction > 0) enfoque.done else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (summary.nextTime.take(6).any { it.needsRpe }) {
            Text(stringResource(R.string.summary_next_needs_rpe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Tiempo, volumen y series: tres cifras de un vistazo (el volumen con su cambio frente a la última vez). */
@Composable
private fun KpiRow(summary: FinishedSummary) {
    val w = summary.workout
    val unit = LocalWeightUnit.current
    val volumeDelta = summary.comparisons.sumOf { it.volumeDelta }
    val previousVolume = comparedVolume(summary) - volumeDelta
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Kpi(formatDuration(w.durationSeconds()), stringResource(R.string.enf_wo_time), null, null, Modifier.weight(1f))
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
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainer).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clearAndSetSemantics { contentDescription = listOfNotNull(label, value, sub).joinToString(", ") }
            .padding(10.dp),
    ) {
        Text(label.uppercase(), style = KickerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontFamily = FigureFont, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        sub?.let {
            Text(
                it,
                fontFamily = FigureFont,
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

/** Récords del día: borde y título en ámbar, el número en una píldora y cada marca con su cifra. */
@Composable
private fun RecordsCard(summary: FinishedSummary) {
    val unit = LocalWeightUnit.current
    val record = enfoque.record
    SummaryCard(borderColor = record) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.enf_wo_records), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = record, modifier = Modifier.weight(1f))
            Text(
                "${summary.records.size}",
                fontFamily = FigureFont,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = enfoque.onRecord,
                modifier = Modifier.clip(CircleShape).background(record).padding(horizontal = 9.dp, vertical = 2.dp),
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
                Text(pr.type.formatValue(pr.value * f, unit), fontFamily = FigureFont, fontWeight = FontWeight.Bold)
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
        Bar(stringResource(R.string.day_today), unit.formatVolume(today), (today / max).toFloat(), androidx.compose.ui.graphics.SolidColor(enfoque.action))
        Bar(stringResource(R.string.rd_summary_last_time), unit.formatVolume(before), (before / max).toFloat(), androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest))
        // Y cada ejercicio, en el mismo apartado (antes salía otro «Frente a la última vez» más abajo)
        SummaryComparisons(summary, withTitle = false)
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
private fun DetailsCard(summary: FinishedSummary) {
    val w = summary.workout
    val unit = LocalWeightUnit.current
    com.bestrong.mobile.ui.components.Panel(contentPadding = 16.dp) {
        // Las series ya van en las cifras de arriba: aquí solo cuántos ejercicios
        Text(pluralStringResource(R.plurals.exercise_count, w.exercises.size, w.exercises.size), fontWeight = FontWeight.Bold)
        w.avgHeartRate?.let { Text(stringResource(R.string.summary_hr, it, w.maxHeartRate?.toString() ?: "--"), style = MaterialTheme.typography.bodySmall) }
        remember(w) { com.bestrong.core.stats.RestStats.summary(w) }?.let { r ->
            Text(
                stringResource(R.string.rest_real_avg, formatDuration(r.realSeconds.toLong()), formatDuration(r.plannedSeconds.toLong())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        com.bestrong.mobile.ui.components.WorkoutMuscleChips(w)
        // Si no hubo barras de volumen (ejercicios sin peso), las comparaciones van aquí
        if (comparedVolume(summary) <= 0 || comparedVolume(summary) - summary.comparisons.sumOf { it.volumeDelta } <= 0) SummaryComparisons(summary)
        RpeVsTargetLines(w)
        summary.imbalances.forEach { (id, ratio) ->
            Text(
                stringResource(R.string.summary_imbalance, ExerciseLibrary.byId(id)?.displayName() ?: id, (ratio * 100).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
