package com.bestrong.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TextButtonDefaults
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.SetType
import com.bestrong.core.training.PlateCalculator
import com.bestrong.core.training.Tempo
import com.bestrong.wear.R
import com.bestrong.wear.session.WorkoutSession

/**
 * Progreso del ejercicio actual en el reloj: la última vez, mejor 1RM estimado con su
 * evolución, discos por lado para la serie actual y guía de tempo con vibraciones.
 */
@Composable
fun ExerciseInfoScreen(session: WorkoutSession) {
    val workout by session.workout.collectAsStateWithLifecycle()
    val index by session.exerciseIndex.collectAsStateWithLifecycle()
    val tempoActive by session.tempoActive.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val we = workout?.exercises?.getOrNull(index) ?: return
    val progress = remember(we.exercise.id, workout?.id) { session.progressFor(we.exercise.id).takeLast(12) }
    val previous = remember(we.exercise.id) { session.previousFor(we.exercise.id).filter { it.type != SetType.WARMUP } }
    val set = session.currentSet()
    val listState = rememberScalingLazyListState()
    val brand = MaterialTheme.colorScheme.primary

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text(we.exercise.displayName(), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) } }
            item {
                val text = if (previous.isEmpty()) {
                    stringResource(R.string.info_first_time)
                } else {
                    stringResource(R.string.info_last_time, previous.joinToString("  ") { "${unit.format(it.weightKg)}×${it.reps}" })
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            progress.maxOfOrNull { it.bestOneRepMax }?.takeIf { it > 0 }?.let { best ->
                item {
                    Text(
                        stringResource(R.string.info_best_1rm, unit.format(best, withUnit = true)),
                        style = MaterialTheme.typography.titleMedium,
                        color = brand,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (progress.size >= 2) {
                item { CrownChart(progress.map { it.dateEpochMs to it.bestOneRepMax }, brand) }
            }
            // Últimos 3 pesos como accesos rápidos para la serie actual
            val recent = session.recentWeights(we.exercise.id)
            if (recent.isNotEmpty() && set != null) {
                item { ListHeader { Text(stringResource(R.string.quick_weights)) } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        recent.forEach { kg ->
                            val label = unit.format(kg)
                            TextButton(
                                onClick = { session.setCurrentWeight(kg) },
                                colors = if (kg == set.weightKg) TextButtonDefaults.filledTextButtonColors() else TextButtonDefaults.filledTonalTextButtonColors(),
                                modifier = Modifier.describedAs(stringResource(R.string.a11y_set_weight, unit.format(kg, withUnit = true))),
                            ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            if (we.exercise.equipment == Equipment.BARBELL && set != null && set.weightKg > 0) {
                item {
                    val load = PlateCalculator.calculate(unit.fromKg(set.weightKg), unit)
                    val plates = load.perSide.joinToString(" · ") { unit.format(unit.toKg(it)) }.ifEmpty { "—" }
                    Text(
                        stringResource(R.string.info_plates, plates, unit.format(unit.toKg(load.barWeight))),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Tempo.parse(we.tempo)?.let { tempo ->
                item {
                    SwitchButton(
                        checked = tempoActive,
                        onCheckedChange = { session.toggleTempo() },
                        label = { Text(stringResource(R.string.info_tempo, tempo.toString())) },
                        secondaryLabel = { Text(stringResource(R.string.info_tut, tempo.timeUnderTension(set?.reps ?: 10))) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Gráfica del 1RM estimado que se recorre con la corona: toca la gráfica para tomar el control
 * y gira para ver cada sesión (fecha y valor).
 */
@Composable
private fun CrownChart(points: List<Pair<Long, Double>>, color: Color) {
    val unit = LocalWeightUnit.current
    var selected by remember(points.size) { mutableIntStateOf(points.lastIndex) }
    var accumulated by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    val values = points.map { it.second }
    val (date, value) = points[selected]
    val label = stringResource(R.string.chart_point, java.time.Instant.ofEpochMilli(date).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(), unit.format(value, withUnit = true))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp)
                .onRotaryScrollEvent { e ->
                    accumulated += e.verticalScrollPixels
                    val steps = (accumulated / 40f).toInt()
                    if (steps != 0) {
                        accumulated -= steps * 40f
                        selected = (selected + steps).coerceIn(0, points.lastIndex)
                    }
                    true
                }
                .focusRequester(focus)
                .focusable()
                .clickable { focus.requestFocus() }
                .semantics { contentDescription = label },
        ) {
            val min = values.min()
            val range = (values.max() - min).takeIf { it > 0 } ?: 1.0
            fun y(v: Double) = size.height - (size.height * ((v - min) / range)).toFloat()
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = size.width * i / (values.size - 1)
                if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
            val sx = size.width * selected / (values.size - 1)
            drawLine(color.copy(alpha = 0.4f), Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(color, 5.dp.toPx(), Offset(sx, y(values[selected])))
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(stringResource(R.string.chart_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
