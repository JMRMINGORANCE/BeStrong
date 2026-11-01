package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.training.PlateCalculator
import com.bestrong.core.util.formatWeight
import com.bestrong.designsystem.BeStrongPalette
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.Panel
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.theme.statusColors

/**
 * Pulso en directo del reloj con su evolución durante el entreno (Fold desplegado, «centro de mando»).
 * Las muestras se guardan solo mientras la pantalla está abierta.
 */
@Composable
internal fun HeartRateCard(workoutId: String, metrics: LiveMetrics?, modifier: Modifier = Modifier) {
    val samples = remember(workoutId) { mutableStateListOf<Int>() }
    LaunchedEffect(metrics?.timestampEpochMs) {
        metrics?.heartRate?.let { hr ->
            samples += hr
            if (samples.size > MAX_HR_SAMPLES) samples.removeAt(0)
        }
    }
    val m = metrics ?: return
    val hr = m.heartRate ?: return
    val trend = samples.takeLast(4).let { l -> if (l.size >= 2) l.last() - l.first() else 0 }
    val trendText = stringResource(
        when {
            trend > 2 -> R.string.rd_hr_rising
            trend < -2 -> R.string.rd_hr_falling
            else -> R.string.rd_hr_steady
        },
    )
    Panel(modifier, contentPadding = 16.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("❤ $hr", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = BeStrongPalette.Heart)
            Text(" ppm · $trendText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                listOfNotNull(m.avgHeartRate?.let { "Ø $it" }, m.calories?.let { stringResource(R.string.kcal_value, it) }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (samples.size >= 2) {
            val description = stringResource(R.string.rd_hr_chart, samples.min(), samples.max())
            Sparkline(samples.map { it.toFloat() }, BeStrongPalette.Heart, Modifier.fillMaxWidth().height(64.dp).clearAndSetSemantics { contentDescription = description })
        }
    }
}

/**
 * Discos por lado y 1RM estimado de la serie que toca, lado a lado: lo que se mira al cargar la barra.
 */
@Composable
internal fun UpcomingLoadCards(
    w: Workout,
    vm: ActiveWorkoutViewModel,
    bests: Map<String, Pair<Double, Double>>,
    barKgFor: (String) -> Double?,
    modifier: Modifier = Modifier,
) {
    val (i, set) = WorkoutEditor.upcomingSet(w) ?: return
    val we = w.exercises[i]
    if (we.exercise.tracking != TrackingType.WEIGHT_REPS || set.weightKg <= 0) return
    val unit = LocalWeightUnit.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (we.exercise.equipment == Equipment.BARBELL || we.exercise.equipment == Equipment.SMITH) {
            val bar = barKgFor(we.exercise.id)?.let { unit.fromKg(it) } ?: PlateCalculator.defaultBar(unit)
            val load = PlateCalculator.calculate(unit.fromKg(set.weightKg), unit, bar)
            Panel(Modifier.weight(1f), contentPadding = 14.dp) {
                Text(stringResource(R.string.rd_plates_title), fontWeight = FontWeight.Bold)
                val text = if (load.perSide.isEmpty()) stringResource(R.string.rd_plates_empty) else load.perSide.joinToString(" + ") { formatWeight(it) } + " ${unit.label}"
                BarbellDrawing(load.perSide, Modifier.fillMaxWidth().height(72.dp).clearAndSetSemantics { contentDescription = text })
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val best = bests[we.exercise.id]?.first ?: 0.0
        val points = remember(we.exercise.id, w.finishedAtEpochMs) {
            vm.historyFor(we.exercise.id, limit = 8).reversed().mapNotNull { (_, e) -> e.completedSets.maxOfOrNull { it.estimatedOneRepMax }?.toFloat() }
        }
        // Sin marcas anteriores no hay nada que enseñar
        if (best > 0 || points.size >= 2) {
            Panel(Modifier.weight(1f), contentPadding = 14.dp) {
                Text(stringResource(R.string.rd_e1rm_title), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(we.exercise.displayName(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (best > 0) Text(unit.format(best, withUnit = true), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                if (points.size >= 2) Sparkline(points, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth().height(36.dp).clearAndSetSemantics { })
                com.bestrong.core.stats.OneRepMax.repsToBeat(best, set.weightKg)?.let { reps ->
                    Text(
                        statusColors.goodMark + pluralStringResource(R.plurals.rd_pr_if_reps, reps, reps),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColors.good,
                    )
                }
            }
        }
    }
}

/** Línea simple normalizada al alto disponible. */
@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v - min) / span * (size.height - 6.dp.toPx()) - 3.dp.toPx()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Un lado de la barra con los discos a escala (colores de competición). */
@Composable
private fun BarbellDrawing(perSide: List<Double>, modifier: Modifier) {
    val barColor = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val cy = size.height / 2
        val sleeve = 6.dp.toPx()
        drawRect(barColor, Offset(0f, cy - sleeve / 2), Size(size.width, sleeve))
        // Tope de la barra
        drawRoundRect(barColor, Offset(size.width * 0.18f, cy - 10.dp.toPx()), Size(6.dp.toPx(), 20.dp.toPx()), CornerRadius(2.dp.toPx()))
        var x = size.width * 0.18f + 9.dp.toPx()
        val heaviest = perSide.maxOrNull() ?: return@Canvas
        perSide.forEach { p ->
            val h = size.height * (0.35f + 0.65f * (p / heaviest).toFloat().coerceIn(0f, 1f))
            val wPx = 9.dp.toPx()
            drawRoundRect(plateColor(p), Offset(x, cy - h / 2), Size(wPx, h), CornerRadius(2.dp.toPx()))
            x += wPx + 2.dp.toPx()
        }
    }
}

/** Colores habituales de los discos olímpicos (kg); en libras se aproxima por tamaño. */
private fun plateColor(p: Double): Color = when {
    p >= 25.0 -> Color(0xFFE5484D)
    p >= 20.0 -> Color(0xFF3B82F6)
    p >= 15.0 -> Color(0xFFFFC53D)
    p >= 10.0 -> Color(0xFF22C55E)
    p >= 5.0 -> Color(0xFFE6E8EB)
    p >= 2.5 -> Color(0xFF6B7280)
    else -> Color(0xFFB4BAC5)
}

private const val MAX_HR_SAMPLES = 90
